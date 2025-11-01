import os
import uuid
import networkx as nx
from collections import defaultdict
from celery import shared_task
from qdrant_client import models

from .vision_service import get_image_embedding, get_image_captions
from .qdrant_utils import (
    get_qdrant_client,
    IMAGE_COLLECTION_NAME,
    REPVEC_COLLECTION_NAME,
)
from .models import User, Photo_Caption, Caption, Photo_Tag, Tag

import time

from sentence_transformers import SentenceTransformer

_TEXT_MODEL_NAME = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
_text_model = None  # 전역 캐시

MAX_WAIT = 2.0  # 최대 2초 대기
WAIT_INTERVAL = 0.1

import torch
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"


def get_text_model():
    """Lazy-load text model inside worker"""
    global _text_model
    if _text_model is None:
        print("[INFO] Loading text model inside worker...")
        _text_model = SentenceTransformer(_TEXT_MODEL_NAME, device=DEVICE)
    return _text_model


@shared_task
def process_and_embed_photo(
    image_path, user_id, filename, photo_path_id, created_at, lat, lng
):
    try:
        client = get_qdrant_client()
        # 파일이 실제로 생길 때까지 잠시 대기
        waited = 0
        while not os.path.exists(image_path) and waited < MAX_WAIT:
            time.sleep(WAIT_INTERVAL)
            waited += WAIT_INTERVAL

        if not os.path.exists(image_path):
            print(
                f"[Celery Task Error] File not found even after waiting: {image_path}"
            )
            return

        embedding = get_image_embedding(image_path)

        if embedding is None:
            print(f"[Celery Task Error] Failed to create embedding for {filename}")
            if os.path.exists(image_path):
                os.remove(image_path)
            return

        photo_id = uuid.uuid4()
        point_to_upsert = models.PointStruct(
            id=str(photo_id),
            vector=embedding,
            payload={
                "user_id": user_id,
                "filename": filename,
                "photo_path_id": photo_path_id,
                "created_at": created_at,
                "lat": lat,
                "lng": lng,
                "isTagged": False,
            },
        )

        client.upsert(
            collection_name=IMAGE_COLLECTION_NAME, points=[point_to_upsert], wait=True
        )
        captions = get_image_captions(image_path)

        # asserts that user id has checked
        user = User.objects.get(id=user_id)

        for word, count in captions.items():
            caption, _ = Caption.objects.get_or_create(
                user=user,
                caption=word,
            )

            _ = Photo_Caption.objects.create(
                user=user,
                photo_id=photo_id,
                caption=caption,
                weight=count,
            )

        print(f"[Celery Task Success] Processed and upserted photo {filename}")

    except Exception as e:
        print(f"[Celery Task Exception] Error processing {filename}: {str(e)}")
    finally:
        if os.path.exists(image_path):
            os.remove(image_path)


@shared_task
def create_query_embedding(query):
    model = get_text_model()  # lazy-load
    return model.encode(query)


def recommend_photo_from_photo(user: User, photos: list[uuid.UUID]):
    client = get_qdrant_client()
    ALPHA = 0.5
    LIMIT = 20

    target_set = set(photos)

    all_photos, _, graph = retrieve_photo_caption_graph(user)

    candidates: set[uuid.UUID] = all_photos - target_set

    # evaluate weighted root pagerank
    rwr_scores = nx.pagerank(
        graph, personalization={node: 1 for node in photos}, weight="weight"
    )

    # evaluate Adamic/Adar score
    aa_scores = defaultdict(float)

    for u, _, score in nx.adamic_adar_index(
        graph, [(c, t) for c in candidates for t in target_set]
    ):
        aa_scores[u] += score

    def normalize(minv, maxv, v):
        if maxv == minv:
            return 0
        else:
            return (v - minv) / (maxv - minv)

    max_rwr = max(rwr_scores.values()) if rwr_scores else 0
    min_rwr = min(rwr_scores.values()) if rwr_scores else 0

    max_aa = max(aa_scores.values()) if aa_scores else 0
    min_aa = min(aa_scores.values()) if aa_scores else 0

    scores = {
        candidate: ALPHA * normalize(min_rwr, max_rwr, rwr_scores.get(candidate, 0))
        + (1 - ALPHA) * normalize(min_aa, max_aa, aa_scores.get(candidate, 0))
        for candidate in candidates
    }

    sorted_scores = sorted(scores.items(), key=lambda item: item[1], reverse=True)

    recommend_photos = list(map(lambda t: str(t[0]), sorted_scores[:LIMIT]))

    points = client.retrieve(
        collection_name=IMAGE_COLLECTION_NAME,
        ids=recommend_photos,
        with_payload=["photo_path_id"],
    )

    return [
        {"photo_id": point.id, "photo_path_id": point.payload["photo_path_id"]}
        for point in points
    ]


def tag_recommendation(user, photo_id):
    LIMIT = 10
    client = get_qdrant_client()

    retrieved_points = client.retrieve(
        collection_name=IMAGE_COLLECTION_NAME,
        ids=[str(photo_id)],
        with_vectors=True,
    )

    if not retrieved_points:
        raise ValueError(f"Image with id {photo_id} not found in collection.")

    image_vector = retrieved_points[0].vector

    user_filter = models.Filter(
        must=[
            models.FieldCondition(
                key="user_id",
                match=models.MatchValue(value=user.id),
            )
        ]
    )

    search_results = client.search(
        collection_name=REPVEC_COLLECTION_NAME,
        query_vector=image_vector,
        query_filter=user_filter,
        limit=LIMIT,
        with_payload=True,
    )

    tag_ids = list(dict.fromkeys(result.payload["tag_id"] for result in search_results))

    recommendations = []

    for tag_id in tag_ids:
        try:
            tag = Tag.objects.get(tag_id=tag_id)
            recommendations.append(tag)
        except Tag.DoesNotExist:
            continue

    return recommendations


# aggregates N similarity queries with Reciprocal Rank Fusion
def recommend_photo_from_tag(user: User, tag_id: uuid.UUID):
    client = get_qdrant_client()
    LIMIT = 40
    RRF_CONSTANT = 40

    rep_vectors = retrieve_all_rep_vectors_of_tag(user, tag_id)

    rrf_scores = defaultdict(float)
    photo_id_to_path_id = {}

    for rep_vector in rep_vectors:
        img_points = client.query_points(
            IMAGE_COLLECTION_NAME,
            query=rep_vector,
            with_payload=["photo_id", "photo_path_id"],
            limit=LIMIT,
        ).points

        for i, img_point in enumerate(img_points):
            photo_id = img_point.payload["photo_id"]
            photo_path_id = img_point.payload["photo_path_id"]

            rrf_scores[photo_id] = rrf_scores[photo_id] + 1 / (RRF_CONSTANT + i + 1)
            photo_id_to_path_id[photo_id] = photo_path_id

    rrf_sorted = sorted(
        rrf_scores.items(),
        key=lambda item: item[1],
        reverse=True,
    )

    tagged_photo_ids = (
        Photo_Tag.objects.filter(user=user)
        .filter(tag__tag_id=tag_id)
        .values_list("photo_id", flat=True)
    )

    recommendations = [
        {"photo_id": photo_id, "photo_path_id": photo_id_to_path_id[photo_id]}
        for (photo_id, _) in rrf_sorted
        if photo_id not in tagged_photo_ids
    ][:LIMIT]

    return recommendations

def retrieve_all_rep_vectors_of_tag(user: User, tag_id: uuid.UUID):
    client = get_qdrant_client()
    LIMIT = 32  # assert max num of rep vectors <= 32

    filters = models.Filter(
        must=[
            models.FieldCondition(
                key="user_id", match=models.MatchValue(value=user.id)
            ),
            models.FieldCondition(
                key="tag_id", match=models.MatchValue(value=str(tag_id))
            ),
        ]
    )

    rep_points, _ = client.scroll(
        REPVEC_COLLECTION_NAME,
        scroll_filter=filters,
        limit=LIMIT,
        with_vectors=True,
    )

    rep_vectors: list[list[float]] = [point.vector for point in rep_points]

    return rep_vectors


def retrieve_photo_caption_graph(user: User):
    graph = nx.Graph()

    photo_set = set()
    caption_set = set()

    for photo_caption in Photo_Caption.objects.filter(user=user):
        if photo_caption.photo_id not in photo_set:
            photo_set.add(photo_caption.photo_id)
            graph.add_node(photo_caption.photo_id, bipartite=0)

        if photo_caption.caption.caption_id not in caption_set:
            caption_set.add(photo_caption.caption.caption_id)
            graph.add_node(photo_caption.caption, bipartite=1)

        graph.add_edge(
            photo_caption.photo_id, photo_caption.caption, weight=photo_caption.weight
        )

    return photo_set, caption_set, graph

def retrieve_combined_graph(user: User, tag_edge_weight: float = 10.0):
    graph = nx.Graph()
    photo_set = set()
    
    # meta_set은 캡션 '객체'와 태그 '객체'를 모두 담습니다.
    meta_set = set() 

    for photo_caption in Photo_Caption.objects.filter(user=user):
        photo_id = photo_caption.photo_id
        caption_obj = photo_caption.caption

        if photo_id not in photo_set:
            photo_set.add(photo_id)
            graph.add_node(photo_id, bipartite=0)

        if caption_obj not in meta_set:
            meta_set.add(caption_obj)
            graph.add_node(caption_obj, bipartite=1)

        graph.add_edge(
            photo_id, caption_obj, weight=photo_caption.weight 
        ) 

    for photo_tag in Photo_Tag.objects.filter(user=user):
        photo_id = photo_tag.photo_id
        tag_obj = photo_tag.tag 

        if photo_id not in photo_set:
            photo_set.add(photo_id)
            graph.add_node(photo_id, bipartite=0)

        if tag_obj not in meta_set:
            meta_set.add(tag_obj)
            graph.add_node(tag_obj, bipartite=1)

        graph.add_edge(
            photo_id, tag_obj, weight=tag_edge_weight
        )

    return photo_set, meta_set, graph

def execute_hybrid_graph_search(
    user: User, 
    personalization_nodes: set, 
    semantic_scores: dict,
    tag_edge_weight: float = 10.0,
    alpha: float = 0.5,
    graph_weight: float = 0.6,
    semantic_weight: float = 0.4,
    limit: int = 20,
):
    
    # 1. 결합 그래프 생성
    all_photos, _, graph = retrieve_combined_graph(user, tag_edge_weight)
    
    # 2. 후보군 정의
    # 재시작 집합의 노드 중 '사진' 노드만 분리
    # (Tag, Caption 객체는 UUID가 아니므로 분리 가능)
    personalization_photos = {
        node for node in personalization_nodes if isinstance(node, uuid.UUID)
    }
    
    candidates: set[uuid.UUID] = all_photos - personalization_photos
    
    valid_personalization_nodes = {
        node for node in personalization_nodes if node in graph
    }

    if not valid_personalization_nodes:
        return []

    # 점수 계산 1: Personalized PageRank (RWR)
    rwr_scores = nx.pagerank(
        graph, 
        personalization=valid_personalization_nodes, 
        weight="weight"
    )

    # 4. 점수 계산 2: Adamic/Adar Index
    aa_scores = defaultdict(float)
    target_set = valid_personalization_nodes 
    
    try:
        for u, _, score in nx.adamic_adar_index(
            graph, [(c, t) for c in candidates for t in target_set if (c in graph and t in graph)]
        ):
            aa_scores[u] += score
    except Exception as e:
        print(f"[AdamicAdar Error] {e}")
        pass

    # 점수 정규화 및 결합
    def normalize(minv, maxv, v):
        if maxv == minv:
            return 0
        else:
            return (v - minv) / (maxv - minv)

    max_rwr = max(rwr_scores.values()) if rwr_scores else 0
    min_rwr = min(rwr_scores.values()) if rwr_scores else 0

    max_aa = max(aa_scores.values()) if aa_scores else 0
    min_aa = min(aa_scores.values()) if aa_scores else 0
    
    max_sem = max(semantic_scores.values()) if semantic_scores else 0
    min_sem = min(semantic_scores.values()) if semantic_scores else 0

    scores = {}
    for candidate in candidates:
        # 그래프 점수 계산
        norm_rwr = normalize(min_rwr, max_rwr, rwr_scores.get(candidate, 0))
        norm_aa = normalize(min_aa, max_aa, aa_scores.get(candidate, 0))
        graph_score = alpha * norm_rwr + (1 - alpha) * norm_aa

        # 시맨틱 점수 계산
        # (시맨틱 검색 결과에 없던 후보는 0점)
        norm_sem = normalize(min_sem, max_sem, semantic_scores.get(candidate, 0))
        
        # ✨ 최종 하이브리드 점수
        scores[candidate] = (graph_weight * graph_score) + (semantic_weight * norm_sem)

    sorted_scores = sorted(scores.items(), key=lambda item: item[1], reverse=True)

    recommend_photos = list(map(lambda t: str(t[0]), sorted_scores[:limit]))

    if not recommend_photos:
        return []

    # Qdrant에서 최종 정보 조회
    points = client.retrieve(
        collection_name=IMAGE_COLLECTION_NAME,
        ids=recommend_photos,
        with_payload=["photo_path_id"],
    )

    # 정렬 순서 유지를 위해 ID를 키로 하는 딕셔너리 생성
    points_dict = {point.id: point.payload["photo_path_id"] for point in points}

    # RWR/AA로 정렬된 순서대로 최종 결과 생성
    final_results = [
        {"photo_id": photo_id, "photo_path_id": points_dict[photo_id]}
        for photo_id in recommend_photos if photo_id in points_dict
    ]
    
    return final_results




def is_valid_uuid(uuid_to_test):
    try:
        uuid.UUID(str(uuid_to_test))
    except ValueError:
        return False
    return True
