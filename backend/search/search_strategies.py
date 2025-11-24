from abc import ABC, abstractmethod
from typing import List, Dict
from gallery.models import Photo, Photo_Tag, Tag
from search.embedding_service import create_query_embedding
from gallery.qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME
from qdrant_client.http import models
import uuid
from gallery.tasks import execute_hybrid_search
from .exceptions import SearchExecutionError

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

        photos = Photo.objects.filter(photo_id__in=photo_tags).values(
            "photo_id", "photo_path_id", "created_at"
        )

        return [
            {
                "photo_id": str(p["photo_id"]),
                "photo_path_id": p["photo_path_id"],
                "created_at": p["created_at"],
            }
            for p in photos
        ]

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
                limit=query_params.get('limit', 50),
                offset=query_params.get('offset', 0),
                with_payload=True,
                with_vectors=False,
                score_threshold=0.2,
            )

            semantic_photo_ids = [point.id for point in search_result]

            if not semantic_photo_ids:
                return []

            photo_uuids = [uuid.UUID(pid) for pid in semantic_photo_ids]
            photos = Photo.objects.filter(photo_id__in=photo_uuids).values(
                "photo_id", "photo_path_id", "created_at"
            )

            # Preserve Qdrant search order
            id_to_meta = {
                str(p["photo_id"]): {
                    "photo_path_id": p["photo_path_id"],
                    "created_at": p["created_at"]
                }
                for p in photos
            }

            return [
                {
                    "photo_id": pid,
                    "photo_path_id": id_to_meta[pid]["photo_path_id"],
                    "created_at": id_to_meta[pid]["created_at"]
                }
                for pid in semantic_photo_ids
                if pid in id_to_meta
            ]
        except Exception as e:
            raise SearchExecutionError(f"Semantic search failed: {str(e)}") from e

class HybridSearchStrategy(SearchStrategy):
    """Search photos using fusion of tags and semantic similarity"""

    def search(self, user, query_params: Dict) -> List[Dict]:

        return execute_hybrid_search(
            user=user,
            tag_ids=query_params['tag_ids'],
            query_string=query_params['query_text']
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
