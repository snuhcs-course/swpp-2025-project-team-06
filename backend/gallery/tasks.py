"""
CPU-only Celery tasks and helper functions.

This module contains recommendation algorithms, graph-based computations,
and other CPU-bound tasks that don't require GPU acceleration.

For GPU-dependent tasks (image processing, embeddings), see gpu_tasks.py
"""

import uuid
import networkx as nx
from collections import defaultdict
from celery import shared_task
from qdrant_client import models
from django.conf import settings
from django.core.cache import cache

from .qdrant_utils import (
    get_qdrant_client,
    IMAGE_COLLECTION_NAME,
    REPVEC_COLLECTION_NAME,
)
from .models import User, Photo_Caption, Photo_Tag, Tag, Photo
from search.embedding_service import create_query_embedding


import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.cluster import KMeans
import math

SEARCH_SETTINGS = settings.HYBRID_SEARCH_SETTINGS


# aggregates N similarity queries with Reciprocal Rank Fusion
def recommend_photo_from_tag(user: User, tag_id: uuid.UUID):
    client = get_qdrant_client()
    LIMIT = 40
    RRF_CONSTANT = 40

    rep_vectors = retrieve_all_rep_vectors_of_tag(user, tag_id)

    rrf_scores = defaultdict(float)
    photo_uuids = set()

    user_filter = models.Filter(
        must=[
            models.FieldCondition(
                key="user_id",
                match=models.MatchValue(value=user.id),
            )
        ]
    )

    for rep_vector in rep_vectors:
        search_result = client.search(
            IMAGE_COLLECTION_NAME,
            query_vector=rep_vector,
            query_filter=user_filter,
            limit=LIMIT,
        )

        for i, img_point in enumerate(search_result):
            photo_id = uuid.UUID(img_point.id)
            photo_uuids.add(photo_id)
            rrf_scores[photo_id] = rrf_scores[photo_id] + 1 / (RRF_CONSTANT + i + 1)

    rrf_sorted = sorted(
        rrf_scores.items(),
        key=lambda item: item[1],
        reverse=True,
    )

    tagged_photo_ids = set(
        Photo_Tag.objects.filter(user=user, tag__tag_id=tag_id).values_list(
            "photo__photo_id", flat=True
        )
    )

    recommendations = []

    for i, (photo_id, _) in enumerate(rrf_sorted):
        if i >= LIMIT:
            break

        if photo_id not in tagged_photo_ids:
            try:
                photo = Photo.objects.get(photo_id=photo_id)
                recommendations.append(photo)
            except Photo.DoesNotExist:
                continue

    return recommendations


def recommend_photo_from_photo(user: User, photos: list[uuid.UUID]):
    ALPHA = 0.5
    LIMIT = 20

    if not photos:
        return []

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

    # Fetch photo_path_id from Photo model instead of Qdrant
    photo_uuids = [uuid.UUID(pid) for pid in recommend_photos]
    photos = Photo.objects.filter(photo_id__in=photo_uuids).values(
        "photo_id", "photo_path_id"
    )
    id_to_path = {str(p["photo_id"]): p["photo_path_id"] for p in photos}

    # Maintain order from sorted scores
    return [
        {"photo_id": pid, "photo_path_id": id_to_path[pid]}
        for pid in recommend_photos
        if pid in id_to_path
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
        photo_id = photo_caption.photo.photo_id
        if photo_id not in photo_set:
            photo_set.add(photo_id)
            graph.add_node(photo_id, bipartite=0)

        caption_id = photo_caption.caption.caption_id
        if caption_id not in caption_set:
            caption_set.add(caption_id)
            graph.add_node(caption_id, bipartite=1)

        graph.add_edge(photo_id, caption_id, weight=photo_caption.weight)

    return photo_set, caption_set, graph


def retrieve_combined_graph(user: User, tag_edge_weight: float = 10.0):
    cache_key = f"user_{user.id}_combined_graph"

    cached_data = cache.get(cache_key)

    if cached_data:
        print(f"[INFO] User {user.id} graph loaded from CACHE")
        return cached_data["photo_set"], cached_data["meta_set"], cached_data["graph"]

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
        photo_id = photo_caption.photo.photo_id
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
        photo_id = photo_tag.photo.photo_id
        tag_obj = photo_tag.tag

        if photo_id not in photo_set:
            photo_set.add(photo_id)
            graph.add_node(photo_id, bipartite=0)

        if tag_obj not in meta_set:
            meta_set.add(tag_obj)
            graph.add_node(tag_obj, bipartite=1)

        graph.add_edge(photo_id, tag_obj, weight=tag_edge_weight)

    data_to_cache = {"photo_set": photo_set, "meta_set": meta_set, "graph": graph}

    cache.set(cache_key, data_to_cache, timeout=3600)

    print(f"[INFO] User {user.id} graph SAVED to cache")

    return photo_set, meta_set, graph


def execute_hybrid_graph_search(
    user: User,
    # -----------------------------------------------
    # ▼ 기존 파라미터 (personalization_nodes, semantic_scores 등) 대신
    #   아래 두 파라미터로 "변경"합니다.
    # -----------------------------------------------
    tag_ids: list[uuid.UUID],
    query_string: str,
    # -----------------------------------------------
    # 설정값들은 settings.py에서 가져옵니다.
    tag_weight: float = SEARCH_SETTINGS.get("TAG_FUSION_WEIGHT", 1.0),
    semantic_weight: float = SEARCH_SETTINGS.get("SEMANTIC_FUSION_WEIGHT", 1.0),
    recommend_limit: int = SEARCH_SETTINGS.get("RECOMMEND_LIMIT", 50),
    semantic_limit: int = SEARCH_SETTINGS.get("SEMANTIC_LIMIT", 50),
    final_limit: int = SEARCH_SETTINGS.get("FINAL_RESULT_LIMIT", 100),
):
    """
    [새 버전] 태그와 자연어 쿼리를 결합한 Qdrant 기반 하이브리드 검색.
    (기존 NetworkX 그래프 로직을 대체함)

    1. (축 1) 태그 검색:
       - 태그에 직접 연결된 사진 (점수 1.0)
       - Qdrant `recommend`로 찾은 유사 사진 (Qdrant 점수)
    2. (축 2) 자연어 검색:
       - Qdrant `search`로 찾은 시맨틱 유사 사진 (Qdrant 점수)
    3. (퓨전):
       - (축1 점수 * tag_weight) + (축2 점수 * semantic_weight)로 최종 점수 계산
    """

    client = get_qdrant_client()
    phase_1_scores = {}
    phase_2_scores = {}

    # Qdrant 검색 시 다른 유저의 데이터를 침범하지 않도록 필터를 생성합니다.
    user_filter = models.Filter(
        must=[
            models.FieldCondition(
                key="user_id",
                match=models.MatchValue(value=user.id),
            )
        ]
    )

    # --- 1단계: 태그 기반 점수 계산 (축 1) ---
    if tag_ids:
        # 1.1: DB에서 태그에 직접 속한 사진 ID 조회 (TagPhotoSet)
        tag_photo_uuids = set(
            Photo_Tag.objects.filter(
                user=user,
                tag__tag_id__in=tag_ids
            ).values_list("photo__photo_id", flat=True)
        ) # Django ORM 구문 정확

        tag_photo_ids_str = {str(pid) for pid in tag_photo_uuids}

        # 1.2: Qdrant `recommend` API 호출 (SimilarPhotoSet)
        if tag_photo_ids_str:
            try:
                # Qdrant `recommend` API (문법 정확)
                recommend_results = client.recommend(
                    collection_name=IMAGE_COLLECTION_NAME,
                    positive=list(tag_photo_ids_str),
                    query_filter=user_filter,
                    limit=recommend_limit,
                    with_vectors=False,
                    with_payload=False,
                )

                for result in recommend_results:
                    phase_1_scores[result.id] = result.score

            except Exception as e:
                print(f"[HybridSearch Error] Qdrant recommend failed: {e}")
                pass

        # 1.3: 태그에 "직접" 속한 사진(TagPhotoSet)에 1.0점 부여 (덮어쓰기)
        for photo_id_str in tag_photo_ids_str:
            phase_1_scores[photo_id_str] = 1.0


    # --- 2단계: 자연어 기반 점수 계산 (축 2) ---
    if query_string:
        # 2.1: 자연어 쿼리를 임베딩 벡터로 변환
        query_vector = create_query_embedding(query_string)

        # 2.2: Qdrant `search` API 호출 (문법 정확)
        try:
            search_results = client.search(
                collection_name=IMAGE_COLLECTION_NAME,
                query_vector=query_vector,
                query_filter=user_filter,
                limit=semantic_limit,
                with_vectors=False,
                with_payload=False,
            )

            for result in search_results:
                phase_2_scores[result.id] = result.score

        except Exception as e:
            print(f"[HybridSearch Error] Qdrant search failed: {e}")
            pass

    # --- 3단계: 점수 퓨전 ---
    all_candidates = set(phase_1_scores.keys()).union(phase_2_scores.keys())

    if not all_candidates:
        return []

    final_scores = {}
    for photo_id_str in all_candidates:
        p1_score = phase_1_scores.get(photo_id_str, 0.0)
        p2_score = phase_2_scores.get(photo_id_str, 0.0)

        final_scores[photo_id_str] = (tag_weight * p1_score) + (semantic_weight * p2_score)

    sorted_scores_tuple = sorted(
        final_scores.items(),
        key=lambda item: item[1],
        reverse=True
    )

    # 3.4: 최종 결과 포맷팅 (DB 조회)
    recommend_photo_ids_str = [item[0] for item in sorted_scores_tuple[:final_limit]]

    if not recommend_photo_ids_str:
        return []

    photo_uuids = [uuid.UUID(pid) for pid in recommend_photo_ids_str]

    # Photo 모델에서 photo_path_id 조회 (Django ORM 구문 정확)
    photos = Photo.objects.filter(photo_id__in=photo_uuids).values(
        "photo_id", "photo_path_id"
    )

    id_to_path = {str(p["photo_id"]): p["photo_path_id"] for p in photos}

    final_results = [
        {"photo_id": photo_id_str, "photo_path_id": id_to_path[photo_id_str]}
        for photo_id_str in recommend_photo_ids_str
        if photo_id_str in id_to_path
    ]

    return final_results


def is_valid_uuid(uuid_to_test):
    try:
        uuid.UUID(str(uuid_to_test))
    except ValueError:
        return False
    return True


@shared_task
def compute_and_store_rep_vectors(user_id: int, tag_id: uuid.UUID):
    client = get_qdrant_client()
    K_CLUSTERS = 3  # 기본 코드의 k = 3
    OUTLIER_FRACTION = 0.05  # 기본 코드의 outlier_fraction = 0.05
    MIN_SAMPLES_FOR_ML = 10  # ML 모델을 돌리기 위한 최소 샘플 수 (기본 코드 기준)

    print(f"[Task Start] RepVec computation for User: {user_id}, Tag: {tag_id}")

    try:
        photo_tags = Photo_Tag.objects.filter(user__id=user_id, tag__tag_id=tag_id)
        photo_ids = [str(pt.photo.photo_id) for pt in photo_tags]

        delete_filter = models.Filter(
            must=[
                models.FieldCondition(
                    key="user_id", match=models.MatchValue(value=user_id)
                ),
                models.FieldCondition(
                    key="tag_id", match=models.MatchValue(value=str(tag_id))
                ),
            ]
        )
        client.delete(
            collection_name=REPVEC_COLLECTION_NAME,
            points_selector=models.FilterSelector(filter=delete_filter),
            wait=True,
        )
        print(f"[Task Info] Deleted old repvecs for Tag: {tag_id}.")

        if not photo_ids:
            print(
                f"[Task Info] No photos found for Tag: {tag_id}. RepVecs deleted. Task finished."
            )
            return

        points = client.retrieve(
            collection_name=IMAGE_COLLECTION_NAME, ids=photo_ids, with_vectors=True
        )

        selected_vecs = np.array([point.vector for point in points if point.vector])

        if len(selected_vecs) == 0:
            print(
                f"[Task Info] No vectors found in Qdrant for Tag: {tag_id}. Skipping."
            )
            return

        if len(selected_vecs) < MIN_SAMPLES_FOR_ML:
            final_representatives = selected_vecs
        else:
            iso_forest = IsolationForest(
                contamination=OUTLIER_FRACTION, random_state=42
            )
            preds = iso_forest.fit_predict(selected_vecs)

            outlier_vecs = selected_vecs[preds == -1]
            inlier_vecs = selected_vecs[preds == 1]

            kmeans_centers = np.array([])
            if len(inlier_vecs) >= K_CLUSTERS:
                kmeans = KMeans(n_clusters=K_CLUSTERS, random_state=42, n_init="auto")
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
                print(
                    f"[Task Info] No representative vectors generated for Tag: {tag_id}."
                )
                return

            final_representatives = np.vstack(final_representatives_list)

        points_to_upsert = []
        for vec in final_representatives:
            point_id = str(uuid.uuid4())
            payload = {"user_id": user_id, "tag_id": str(tag_id)}
            points_to_upsert.append(
                models.PointStruct(id=point_id, vector=vec.tolist(), payload=payload)
            )

        if points_to_upsert:
            client.upsert(
                collection_name=REPVEC_COLLECTION_NAME,
                points=points_to_upsert,
                wait=True,
            )
            print(
                f"[Task Success] Upserted {len(points_to_upsert)} new repvecs for Tag: {tag_id}."
            )
        else:
            print(f"[Task Info] No new repvecs to upsert for Tag: {tag_id}.")

    except Exception as e:
        print(f"[Task Exception] Error processing Tag {tag_id}: {str(e)}")
