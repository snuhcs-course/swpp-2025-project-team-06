from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework_simplejwt.authentication import JWTAuthentication
import re
from gallery.models import Tag, Photo_Tag, Photo
from .embedding_service import create_query_embedding
from gallery.tasks import execute_hybrid_search
from gallery.qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME
from qdrant_client.http import models
from .response_serializers import PhotoResponseSerializer
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
import uuid
from django.conf import settings

TAG_REGEX = re.compile(r"\{([^}]+)\}")

SEARCH_SETTINGS = settings.HYBRID_SEARCH_SETTINGS


class SemanticSearchView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Semantic Search",
        operation_description="Search photos semantically using a query string.",
        manual_parameters=[
            openapi.Parameter(
                "query",
                openapi.IN_QUERY,
                description="Search query string",
                type=openapi.TYPE_STRING,
                required=True,
            ),
            openapi.Parameter(
                "offset",
                openapi.IN_QUERY,
                description="Number of results to skip (for pagination)",
                type=openapi.TYPE_INTEGER,
                required=False,
                default=SEARCH_SETTINGS.get("SEARCH_DEFAULT_OFFSET", 0),
            ),
            openapi.Parameter(
                "limit",
                openapi.IN_QUERY,
                description="Number of results to return per page",
                type=openapi.TYPE_INTEGER,
                required=False,
                default=SEARCH_SETTINGS.get("SEARCH_PAGE_SIZE", 66),
            ),
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            ),
        ],
        responses={
            200: openapi.Response(
                description="OK - List of photo IDs matching the query",
                schema=PhotoResponseSerializer(many=True),
            ),
            400: openapi.Response(description="Bad Request - Invalid input"),
            401: openapi.Response(
                description="Unauthorized - Invalid or expired token"
            ),
        },
    )
    def get(self, request):
        try:
            client = get_qdrant_client()
            query = request.GET.get("query", "")
            if not query:
                return Response(
                    {"error": "query parameter is required."},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            offset = int(request.GET.get("offset", SEARCH_SETTINGS.get("SEARCH_DEFAULT_OFFSET", 0)))
            limit = int(request.GET.get("limit", SEARCH_SETTINGS.get("SEARCH_PAGE_SIZE", 66)))
            user = request.user

            tag_names = TAG_REGEX.findall(query)
            semantic_query = TAG_REGEX.sub("", query).strip()

            valid_tag_ids = []
            if tag_names:
                valid_tag_ids = list(
                    Tag.objects.filter(
                        user=user, tag__in=tag_names
                    ).values_list("tag_id", flat=True)
                )

            final_results = []

            if not semantic_query and valid_tag_ids:
                # Get photos directly tagged
                tagged_photo_ids = list(
                    Photo_Tag.objects.filter(user=user, tag_id__in=valid_tag_ids)
                    .values_list("photo_id", flat=True)
                    .distinct()
                )

                tagged_photo_ids_str = [str(pid) for pid in tagged_photo_ids]

                # Get representative vectors for the tags
                from gallery.tasks import retrieve_all_rep_vectors_of_tag
                all_rep_vectors = []
                for tag_id in valid_tag_ids:
                    rep_vectors = retrieve_all_rep_vectors_of_tag(user, tag_id)
                    all_rep_vectors.extend(rep_vectors)

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
                        print(f"Recommend failed: {e}")

                # Combine: tagged photos first, then similar photos
                all_photo_ids = tagged_photo_ids_str + similar_photo_ids
                paginated_photo_ids = all_photo_ids[offset:offset + limit]

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

            elif semantic_query and not valid_tag_ids:
                try:
                    query_vector = create_query_embedding(semantic_query)

                    user_filter = models.Filter(
                        must=[
                            models.FieldCondition(
                                key="user_id",
                                match=models.MatchValue(value=user.id),
                            )
                        ]
                    )

                    # Search with large limit to get all results above threshold
                    # Then apply pagination manually
                    search_result = client.search(
                        collection_name=IMAGE_COLLECTION_NAME,
                        query_vector=query_vector,
                        query_filter=user_filter,
                        limit=SEARCH_SETTINGS.get("SEARCH_MAX_LIMIT", 1000),
                        with_payload=True,
                        with_vectors=False,
                        score_threshold=SEARCH_SETTINGS.get("SEARCH_SCORE_THRESHOLD", 0.2),
                    )

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
                except Exception as e:
                    return Response(
                        {"error": f"Semantic search failed: {str(e)}"},
                        status=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    )

            elif semantic_query and valid_tag_ids:
                final_results = execute_hybrid_search(
                    user=user,
                    tag_ids=valid_tag_ids,
                    query_string=semantic_query,
                    offset=offset,
                    limit=limit
                )

            else:
                return Response(
                    {"message": "No valid tags found or query is empty."},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            serializer = PhotoResponseSerializer(final_results, many=True)
            return Response(serializer.data, status=status.HTTP_200_OK)

        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )
