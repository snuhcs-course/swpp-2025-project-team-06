from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework_simplejwt.authentication import JWTAuthentication
import re
from gallery.models import Tag, Photo_Tag
from gallery.tasks import (
    create_query_embedding,
    execute_hybrid_graph_search,
)
from gallery.qdrant_utils import client, IMAGE_COLLECTION_NAME
from .response_serializers import PhotoResponseSerializer
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
import uuid

TAG_REGEX = re.compile(r"\{([^}]+)\}")


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
                default=0,
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
            query = request.GET.get("query", "")
            if not query:
                return Response(
                    {"error": "query parameter is required."},
                    status=status.HTTP_400_BAD_REQUEST,
                )
                
            offset = int(request.GET.get("offset", 0))
            user = request.user
            
            TAG_EDGE_WEIGHT = 10.0
            
            tag_names = TAG_REGEX.findall(query)
            semantic_query = TAG_REGEX.sub("something", query).strip()
            
            personalization_nodes = set()

            valid_tags = []
            if tag_names:
                valid_tags = Tag.objects.filter(
                    user=user, tag__in=tag_names
                )
                for tag_obj in valid_tags:
                    personalization_nodes.add(tag_obj)

            semantic_photo_ids = []
            if semantic_query:
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

                    search_result = client.search(
                        collection_name=IMAGE_COLLECTION_NAME, #
                        query_vector=query_vector,
                        query_filter=user_filter,
                        limit=20,
                        offset=offset,
                    )
                    
                    for point in search_result:
                        personalization_nodes.add(uuid.UUID(point.id))
                        semantic_photo_ids.append(point.id)

                except Exception as e:
                    return Response(
                        {"error": f"Semantic search failed: {str(e)}"},
                        status=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    )
            
            # --- 3. 최종 검색 실행 ---            
            final_results = []
            
            if not semantic_query and valid_tags:
                # (A) 태그만 있는 경우 (e.g. "{room_escape}"): 태그에 있는 사진만 보여주기
                tag_ids = [tag.tag_id for tag in valid_tags]

                photo_tags = Photo_Tag.objects.filter(
                    user=user, 
                    tag_id__in=tag_ids
                ).values_list('photo_id', flat=True).distinct()

                photo_ids_str = [str(pid) for pid in photo_tags]

                if photo_ids_str:
                    points = client.retrieve(
                        collection_name=IMAGE_COLLECTION_NAME, #
                        ids=photo_ids_str,
                        with_payload=["photo_path_id"], #
                    )
                    
                    final_results = [
                        {"photo_id": point.id, "photo_path_id": point.payload["photo_path_id"]}
                        for point in points
                    ]

            elif semantic_query and not valid_tags:
                # (B) 시맨틱 쿼리만 있는 경우 (기존 검색과 동일)
                if semantic_photo_ids:
                    points = client.retrieve(
                        collection_name=IMAGE_COLLECTION_NAME,
                        ids=semantic_photo_ids,
                        with_payload=["photo_path_id"],
                    )
                    final_results = [
                        {"photo_id": point.id, "photo_path_id": point.payload["photo_path_id"]}
                        for point in points
                    ]

            elif semantic_query and valid_tags:
                # (C) 하이브리드 검색 
                final_results = execute_hybrid_graph_search(
                    user=user,
                    personalization_nodes=personalization_nodes,
                    tag_edge_weight=TAG_EDGE_WEIGHT,
                ) #

            else:
                # (D) 쿼리가 비어있거나, 유효한 태그가 하나도 없는 경우
                return Response(
                    {"message": "No valid tags found or query is empty."},
                    status=status.HTTP_400_BAD_REQUEST
                )

            serializer = PhotoResponseSerializer(final_results, many=True) #
            return Response(serializer.data, status=status.HTTP_200_OK)

        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )
