import os
import uuid
import networkx as nx
from collections import defaultdict
from celery import shared_task
from qdrant_client import models
from django.conf import settings
from django.core.cache import cache

from .vision_service import get_image_embedding, get_image_captions
from .qdrant_utils import (
    get_qdrant_client,
    IMAGE_COLLECTION_NAME,
    REPVEC_COLLECTION_NAME,
)
from .models import User, Photo_Caption, Caption, Photo_Tag, Tag

import time
import torch
from sentence_transformers import SentenceTransformer

import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.cluster import KMeans
import math

SEARCH_SETTINGS = settings.HYBRID_SEARCH_SETTINGS

_TEXT_MODEL_NAME = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
_text_model = None  # 전역 캐시

MAX_WAIT = 2.0  # 최대 2초 대기
WAIT_INTERVAL = 0.1

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


# aggregates N similarity queries with Reciprocal Rank Fusion
def recommend_photo_from_tag(user: User, tag_id: uuid.UUID):
    client = get_qdrant_client()
    LIMIT = 40
    RRF_CONSTANT = 40

    rep_vectors = retrieve_all_rep_vectors_of_tag(user, tag_id)

    rrf_scores = defaultdict(float)
    photo_id_to_path_id = {}

    for rep_vector in rep_vectors:
        search_result = client.search(
            IMAGE_COLLECTION_NAME,
            query_vector=rep_vector,
            with_payload=["photo_path_id"],
            limit=LIMIT,
        )

        for i, img_point in enumerate(search_result):
            photo_id = img_point.id
            photo_path_id = img_point.payload["photo_path_id"]

            rrf_scores[photo_id] = rrf_scores[photo_id] + 1 / (RRF_CONSTANT + i + 1)
            photo_id_to_path_id[photo_id] = photo_path_id

    rrf_sorted = sorted(
        rrf_scores.items(),
        key=lambda item: item[1],
        reverse=True,
    )

    tagged_photo_ids = set(
        str(pid) for pid in Photo_Tag.objects.filter(user=user)
        .filter(tag_id=tag_id)
        .values_list("photo_id", flat=True)
    )

    recommendations = [
        {"photo_id": photo_id, "photo_path_id": photo_id_to_path_id[photo_id]}
        for (photo_id, _) in rrf_sorted
        if photo_id not in tagged_photo_ids
    ][:LIMIT]

    return recommendations


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
            
        caption_id = photo_caption.caption.caption_id
        if caption_id not in caption_set:
            caption_set.add(caption_id)
            graph.add_node(caption_id, bipartite=1)

        graph.add_edge(
            photo_caption.photo_id, caption_id, weight=photo_caption.weight
        )

    return photo_set, caption_set, graph

def retrieve_combined_graph(user: User, tag_edge_weight: float = 10.0):
    cache_key = f"user_{user.id}_combined_graph"
    
    cached_data = cache.get(cache_key)
    
    if cached_data:
        print(f"[INFO] User {user.id} graph loaded from CACHE")
        return cached_data['photo_set'], cached_data['meta_set'], cached_data['graph']

    print(f"[INFO] User {user.id} graph building from DB...")
    graph = nx.Graph()
    photo_set = set()
    meta_set = set()
    
    caption_relations = Photo_Caption.objects.filter(user=user).select_related('caption')
    tag_relations = Photo_Tag.objects.filter(user=user).select_related('tag')

    all_photo_ids = set()
    caption_doc_freq = defaultdict(set)
    
    for pc in caption_relations:
        all_photo_ids.add(pc.photo_id)
        caption_doc_freq[pc.caption].add(pc.photo_id)

    for pt in tag_relations:
        all_photo_ids.add(pt.photo_id)
    
    N = len(all_photo_ids)
    if N == 0:
        N = 1
    
    idf_scores = {}
    for caption_obj, photo_ids_set in caption_doc_freq.items():
        df = len(photo_ids_set)
        idf_scores[caption_obj] = math.log(N / (1 + df))
    
    for photo_caption in caption_relations:
        photo_id = photo_caption.photo_id
        caption_obj = photo_caption.caption

        if photo_id not in photo_set:
            photo_set.add(photo_id)
            graph.add_node(photo_id, bipartite=0)

        if caption_obj not in meta_set:
            meta_set.add(caption_obj)
            graph.add_node(caption_obj, bipartite=1)

        tf = photo_caption.weight
        
        idf = idf_scores.get(caption_obj, 0.0) 
        
        new_weight = tf * (1 + idf)

        graph.add_edge(
            photo_id, caption_obj, weight=new_weight 
        )
        
    for photo_tag in tag_relations:
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
        
    data_to_cache = {
        'photo_set': photo_set,
        'meta_set': meta_set,
        'graph': graph
    }
    
    cache.set(cache_key, data_to_cache, timeout=3600)
    
    print(f"[INFO] User {user.id} graph SAVED to cache")

    return photo_set, meta_set, graph

def execute_hybrid_graph_search(
    user: User, 
    personalization_nodes: set, 
    semantic_scores: dict,
    tag_edge_weight: float = SEARCH_SETTINGS["TAG_EDGE_WEIGHT"],
    alpha: float = SEARCH_SETTINGS["ALPHA_RWR_VS_AA"],
    graph_weight: float = SEARCH_SETTINGS["GRAPH_WEIGHT"],
    semantic_weight: float =SEARCH_SETTINGS["SEMANTIC_WEIGHT"],
    limit: int = SEARCH_SETTINGS["FINAL_RESULT_LIMIT"],
):
    client = get_qdrant_client()
    
    # 1. 결합 그래프 생성
    all_photos, _, graph = retrieve_combined_graph(user, tag_edge_weight)
    
    # 2. 후보군 정의
    # 재시작 집합의 노드 중 '사진' 노드만 분리
    # (Tag, Caption 객체는 UUID가 아니므로 분리 가능)
    candidates: set[uuid.UUID] = all_photos
    
    valid_personalization_nodes_set = {
        node for node in personalization_nodes if node in graph
    }

    if not valid_personalization_nodes_set:
        return []
    
    personalization_dict = {node: 1 for node in valid_personalization_nodes_set}

    # 점수 계산 1: Personalized PageRank (RWR)
    rwr_scores = nx.pagerank(
        graph, 
        personalization=personalization_dict, 
        weight="weight"
    )

    # 4. 점수 계산 2: Adamic/Adar Index
    aa_scores = defaultdict(float)
    target_set = valid_personalization_nodes_set 
    
    try:
        for u, _, score in nx.adamic_adar_index(
            graph, [(c, t) for c in candidates for t in target_set if (c in graph and t in graph)]
        ):
            aa_scores[u] += score
    except Exception as e:
        print(f"[AdamicAdar Error] {e}")
        pass

    def z_score_sigmoid_normalize(score_dict, candidates):
        # 1. 후보군에 해당하는 점수 리스트 생성
        scores_array = np.array([score_dict.get(c, 0.0) for c in candidates])
        
        # 2. Z-Score 계산
        mean = np.mean(scores_array)
        std = np.std(scores_array)
        
        if std == 0:
            # 모든 점수가 0이거나 동일함
            return {c: 0.5 for c in candidates} # Sigmoid(0) = 0.5

        z_scores = (scores_array - mean) / std
        
        # 3. Sigmoid 적용 (0.0 ~ 1.0 사이로 압축)
        #    numpy의 'vectorize'를 사용하지 않고 math.exp를 루프로 돌리는 것이
        #    오버헤드가 적어 더 빠릅니다.
        final_scores = {}
        for candidate, z in zip(candidates, z_scores):
            try:
                final_scores[candidate] = 1 / (1 + math.exp(-z))
            except OverflowError:
                # z가 너무 작으면 (e.g., -1000) exp(-z)가 오버플로우
                # z가 너무 크면 (e.g., +1000) exp(-z)가 0
                final_scores[candidate] = 0.0 if z < 0 else 1.0
                
        return final_scores

    # 3대 점수 모두 정규화
    norm_rwr_dict = z_score_sigmoid_normalize(rwr_scores, candidates)
    norm_aa_dict = z_score_sigmoid_normalize(aa_scores, candidates)
    norm_sem_dict = z_score_sigmoid_normalize(semantic_scores, candidates)
    
    scores = {}
    for candidate in candidates:
        # 그래프 점수 계산
        norm_rwr = norm_rwr_dict.get(candidate, 0.5)
        norm_aa = norm_aa_dict.get(candidate, 0.5)
        graph_score = alpha * norm_rwr + (1 - alpha) * norm_aa

        # 시맨틱 점수 계산
        # (시맨틱 검색 결과에 없던 후보는 0점 -> Z-Score 후에도 평균 이하 점수 -> Sigmoid 후 0.5 미만)
        norm_sem = norm_sem_dict.get(candidate, 0.5)
        
        # 최종 하이브리드 점수
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
    points_dict = {point.id: point.payload.get("photo_path_id") for point in points}

    # RWR/AA로 정렬된 순서대로 최종 결과 생성
    final_results = []
    for photo_id in recommend_photos:
        photo_path_id = points_dict.get(photo_id)
        if photo_path_id is not None:
            final_results.append(
                {"photo_id": photo_id, "photo_path_id": photo_path_id}
            )
    return final_results


def is_valid_uuid(uuid_to_test):
    try:
        uuid.UUID(str(uuid_to_test))
    except ValueError:
        return False
    return True


@shared_task
def compute_and_store_rep_vectors(user_id: int, tag_id: str):
    client = get_qdrant_client()
    K_CLUSTERS = 3           # 기본 코드의 k = 3
    OUTLIER_FRACTION = 0.05  # 기본 코드의 outlier_fraction = 0.05
    MIN_SAMPLES_FOR_ML = 10  # ML 모델을 돌리기 위한 최소 샘플 수 (기본 코드 기준)

    print(f"[Task Start] RepVec computation for User: {user_id}, Tag: {tag_id}")

    try:
        photo_tags = Photo_Tag.objects.filter(user_id=user_id, tag_id=tag_id)
        photo_ids = [str(pt.photo_id) for pt in photo_tags]

        delete_filter = models.Filter(
            must=[
                models.FieldCondition(key="user_id", match=models.MatchValue(value=user_id)),
                models.FieldCondition(key="tag_id", match=models.MatchValue(value=str(tag_id))) 
            ]
        )
        client.delete(
            collection_name=REPVEC_COLLECTION_NAME,
            points_selector=models.FilterSelector(filter=delete_filter),
            wait=True
        )
        print(f"[Task Info] Deleted old repvecs for Tag: {tag_id}.")

        if not photo_ids:
            print(f"[Task Info] No photos found for Tag: {tag_id}. RepVecs deleted. Task finished.")
            return

        points = client.retrieve(
            collection_name=IMAGE_COLLECTION_NAME,
            ids=photo_ids,
            with_vectors=True
        )
        
        selected_vecs = np.array([point.vector for point in points if point.vector])

        if len(selected_vecs) == 0:
            print(f"[Task Info] No vectors found in Qdrant for Tag: {tag_id}. Skipping.")
            return

        if len(selected_vecs) < MIN_SAMPLES_FOR_ML:
            final_representatives = selected_vecs
        else:
            iso_forest = IsolationForest(contamination=OUTLIER_FRACTION, random_state=42)
            preds = iso_forest.fit_predict(selected_vecs)
            
            outlier_vecs = selected_vecs[preds == -1]
            inlier_vecs = selected_vecs[preds == 1]
            
            kmeans_centers = np.array([])
            if len(inlier_vecs) >= K_CLUSTERS:
                kmeans = KMeans(n_clusters=K_CLUSTERS, random_state=42, n_init='auto')
                kmeans.fit(inlier_vecs)
                kmeans_centers = kmeans.cluster_centers_
            elif len(inlier_vecs) > 0:
                kmeans_centers = inlier_vecs
                
            final_representatives_list = []
            if len(outlier_vecs) > 0:
                final_representatives_list.append(outlier_vecs)
            if len(kmeans_centers) > 0:
                final_representatives_list.append(kmeans_centers)

            if not final_representatives_list:
                print(f"[Task Info] No representative vectors generated for Tag: {tag_id}.")
                return

            final_representatives = np.vstack(final_representatives_list)

        points_to_upsert = []
        for vec in final_representatives:
            point_id = str(uuid.uuid4())
            payload = {
                "user_id": user_id,
                "tag_id": str(tag_id)
            }
            points_to_upsert.append(
                models.PointStruct(
                    id=point_id,
                    vector=vec.tolist(),
                    payload=payload
                )
            )

        if points_to_upsert:
            client.upsert(
                collection_name=REPVEC_COLLECTION_NAME,
                points=points_to_upsert,
                wait=True
            )
            print(f"[Task Success] Upserted {len(points_to_upsert)} new repvecs for Tag: {tag_id}.")
        else:
            print(f"[Task Info] No new repvecs to upsert for Tag: {tag_id}.")

    except Exception as e:
        print(f"[Task Exception] Error processing Tag {tag_id}: {str(e)}")
