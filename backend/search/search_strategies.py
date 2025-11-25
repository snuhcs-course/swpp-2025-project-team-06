from abc import ABC, abstractmethod
from typing import List, Dict
from gallery.models import Photo, Photo_Tag
from search.embedding_service import create_query_embedding
from gallery.qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME
from qdrant_client.http import models
import uuid
from gallery.tasks import execute_hybrid_search, retrieve_all_rep_vectors_of_tag
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
        tag_ids = query_params['tag_ids']
        photo_tags = Photo_Tag.objects.filter(
            user=user, tag_id__in=tag_ids
        ).values_list("photo_id", flat=True).distinct()

        client = get_qdrant_client()

        # Get representative vectors for the tags
        all_rep_vectors = []
        for tag_id in tag_ids:
            rep_vectors = retrieve_all_rep_vectors_of_tag(user, tag_id)
            all_rep_vectors.extend(rep_vectors)

        # Get photos directly tagged
        photos = Photo.objects.filter(photo_id__in=photo_tags).values(
            "photo_id", "photo_path_id", "created_at"
        )

        directly_tagged = [
            {
                "photo_id": str(p["photo_id"]),
                "photo_path_id": p["photo_path_id"],
                "created_at": p["created_at"],
            }
            for p in photos
        ]

        tagged_photo_ids_str = [str(pid) for pid in tag_ids]

        # Recommend similar photos using representative vectors
        similar_photo_ids = []
        if all_rep_vectors:
            try:
                user_filter = models.Filter(
                    must=[
                        models.FieldCondition(
                            key="user_id",
                            match=models.MatchValue(value=user.id),
                        )
                    ]
                )

                recommend_results = client.recommend(
                    collection_name=IMAGE_COLLECTION_NAME,
                    positive=all_rep_vectors,
                    query_filter=user_filter,
                    limit=SEARCH_SETTINGS.get("SEARCH_MAX_LIMIT", 1000),
                    with_payload=False,
                    with_vectors=False,
                    score_threshold=SEARCH_SETTINGS.get("SEARCH_SCORE_THRESHOLD", 0.2),
                )

                # Exclude already tagged photos
                similar_photo_ids = [
                    point.id for point in recommend_results
                    if point.id not in tagged_photo_ids_str
                ]

            except Exception as e:
                raise SearchExecutionError(f"Tag-based recommendation failed: {str(e)}") from e

        # Get photo metadata for similar photos
        similar = []
        if similar_photo_ids:
            similar_photo_uuids = [uuid.UUID(pid) for pid in similar_photo_ids]
            similar_photos = Photo.objects.filter(photo_id__in=similar_photo_uuids).values(
                "photo_id", "photo_path_id", "created_at"
            )
            
            # Create a mapping to preserve order
            id_to_meta = {
                str(p["photo_id"]): {
                    "photo_path_id": p["photo_path_id"],
                    "created_at": p["created_at"]
                }
                for p in similar_photos
            }
            
            similar = [
                {
                    "photo_id": pid,
                    "photo_path_id": id_to_meta[pid]["photo_path_id"],
                    "created_at": id_to_meta[pid]["created_at"],
                }
                for pid in similar_photo_ids
                if pid in id_to_meta
            ]
            
        return directly_tagged + similar


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
