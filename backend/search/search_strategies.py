from abc import ABC, abstractmethod
from typing import List, Dict
from collections import defaultdict
from gallery.models import Photo, Photo_Tag
from search.embedding_service import create_query_embedding
from gallery.qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME
from qdrant_client.http import models
import uuid
from gallery.tasks import execute_hybrid_search
from .exceptions import SearchExecutionError
from config import settings

SEARCH_SETTINGS = settings.HYBRID_SEARCH_SETTINGS

class SearchStrategy(ABC):
    """Abstract base class for search strategies"""

    @abstractmethod
    def search(self, user, query_params: Dict) -> List[Dict]:
        """Execute search and return results"""
        pass

class TagOnlySearchStrategy(SearchStrategy):
    """Search photos by tags only"""

    def search(self, user, query_params: Dict) -> List[Dict]:
        client = get_qdrant_client()
        tag_ids = query_params['tag_ids']
        offset = query_params.get('offset', 0)
        limit = query_params.get('limit', 50)

        # 각 태그별 점수를 별도로 저장
        tag_scores_per_photo = defaultdict(dict)  # {photo_id: {tag_id: score}}

        user_filter = models.Filter(
            must=[
                models.FieldCondition(
                    key="user_id",
                    match=models.MatchValue(value=user.id),
                )
            ]
        )

        for tag_id in tag_ids:
            # 이 태그에 직접 속한 사진 ID 조회
            tag_photo_uuids = set(
                Photo_Tag.objects.filter(user=user, tag_id=tag_id).values_list(
                    "photo_id", flat=True
                )
            )

            tag_photo_ids_str = {str(pid) for pid in tag_photo_uuids}

            # 이 태그에 대해 Qdrant recommend API 호출
            if tag_photo_ids_str:
                try:
                    recommend_results = client.recommend(
                        collection_name=IMAGE_COLLECTION_NAME,
                        positive=list(tag_photo_ids_str),
                        query_filter=user_filter,
                        limit=SEARCH_SETTINGS.get("SEARCH_MAX_LIMIT", 1000),
                        with_payload=False,
                        with_vectors=False,
                        score_threshold=SEARCH_SETTINGS.get("SEARCH_SCORE_THRESHOLD", 0.2),
                    )

                    # 이 태그에 대한 점수를 저장
                    for result in recommend_results:
                        tag_scores_per_photo[result.id][str(tag_id)] = result.score

                except Exception as e:
                    print(f"Recommend failed for tag {tag_id}: {e}")

            # 이 태그에 "직접" 속한 사진에 1.0점 부여
            for photo_id_str in tag_photo_ids_str:
                tag_scores_per_photo[photo_id_str][str(tag_id)] = 1.0

        # 하나라도 태그 점수가 있는 사진 선택하고 점수를 곱함
        n = len(tag_ids)
        scale_base = SEARCH_SETTINGS.get("TAG_PRODUCT_SCALE_BASE", 2)
        scale_factor = scale_base ** (n - 1)
        min_score = SEARCH_SETTINGS.get("TAG_MIN_SCORE", 0.1)

        photo_scores = {}
        for photo_id, scores_dict in tag_scores_per_photo.items():
            if len(scores_dict) > 0:  # 하나라도 점수가 있으면
                product_score = 1.0
                for tag_id in tag_ids:
                    # 점수가 없으면 min_score, 있으면 max(score, min_score)
                    score = scores_dict.get(str(tag_id), min_score)
                    score = max(score, min_score)  # 0.1 미만도 0.1로 상향
                    product_score *= score
                # base^(n-1)을 곱해서 스케일링
                photo_scores[photo_id] = product_score * scale_factor

        # 점수순으로 정렬
        sorted_photo_ids = sorted(photo_scores.keys(), key=lambda x: photo_scores[x], reverse=True)

        # Pagination
        paginated_photo_ids = sorted_photo_ids[offset:offset + limit]

        if paginated_photo_ids:
            photo_uuids = [uuid.UUID(pid) for pid in paginated_photo_ids]
            photos = Photo.objects.filter(photo_id__in=photo_uuids).values(
                "photo_id", "photo_path_id", "created_at"
            )

            id_to_meta = {
                str(p["photo_id"]): {
                    "photo_path_id": p["photo_path_id"],
                    "created_at": p["created_at"]
                }
                for p in photos
            }

            # Maintain order
            final_results = [
                {
                    "photo_id": pid,
                    "photo_path_id": id_to_meta[pid]["photo_path_id"],
                    "created_at": id_to_meta[pid]["created_at"]
                }
                for pid in paginated_photo_ids
                if pid in id_to_meta
            ]
            return final_results
        else:
            return []


class SemanticOnlySearchStrategy(SearchStrategy):
    """Search photos by semantic similarity"""

    def search(self, user, query_params: Dict) -> List[Dict]:
        try:

            query_vector = create_query_embedding(query_params['query_text'])
            client = get_qdrant_client()

            user_filter = models.Filter(
                must=[
                    models.FieldCondition(
                        key="user_id",
                        match=models.MatchValue(value=user.id),
                    )
                ]
            )

            search_result = client.search(
                collection_name=IMAGE_COLLECTION_NAME,
                query_vector=query_vector,
                query_filter=user_filter,
                limit=SEARCH_SETTINGS.get("SEARCH_MAX_LIMIT", 1000),
                offset=query_params.get('offset', 0),
                with_payload=True,
                with_vectors=False,
                score_threshold=SEARCH_SETTINGS.get("SEARCH_SCORE_THRESHOLD", 0.4),
            )

            offset = query_params.get('offset', 0)
            limit = query_params.get('limit', 50)

            # Apply pagination to similarity-filtered results
            all_semantic_photo_ids = [point.id for point in search_result]
            semantic_photo_ids = all_semantic_photo_ids[offset:offset + limit]

            if semantic_photo_ids:
                photo_uuids = [uuid.UUID(pid) for pid in semantic_photo_ids]

                photos = Photo.objects.filter(photo_id__in=photo_uuids).values(
                    "photo_id", "photo_path_id", "created_at"
                )

                id_to_meta = {
                    str(p["photo_id"]): {
                        "photo_path_id": p["photo_path_id"],
                        "created_at": p["created_at"]
                    }
                    for p in photos
                }

                # Qdrant 검색 순서 유지
                final_results = [
                    {
                        "photo_id": pid, 
                        "photo_path_id": id_to_meta[pid]["photo_path_id"],
                        "created_at": id_to_meta[pid]["created_at"]
                    }
                    for pid in semantic_photo_ids
                    if pid in id_to_meta
                ]

                return final_results

        except Exception as e:
            raise SearchExecutionError(f"Semantic search failed: {str(e)}") from e

class HybridSearchStrategy(SearchStrategy):
    """Search photos using fusion of tags and semantic similarity"""

    def search(self, user, query_params: Dict) -> List[Dict]:

        return execute_hybrid_search(
            user=user,
            tag_ids=query_params['tag_ids'],
            query_string=query_params['query_text'],
            offset=query_params['offset'],
            limit=query_params['limit'] 
        )


class SearchStrategyFactory:
    """Factory to select appropriate search strategy"""

    @staticmethod
    def create_strategy(has_query: bool, has_tags: bool) -> SearchStrategy:
        if not has_query and has_tags:
            return TagOnlySearchStrategy()
        elif has_query and not has_tags:
            return SemanticOnlySearchStrategy()
        elif has_query and has_tags:
            return HybridSearchStrategy()
        else:
            raise ValueError("Invalid search parameters: must have query or tags")
