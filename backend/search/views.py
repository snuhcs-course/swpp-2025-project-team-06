from .decorators import log_request, handle_exceptions, validate_pagination
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework_simplejwt.authentication import JWTAuthentication
import re
from gallery.models import Tag
from .response_serializers import PhotoResponseSerializer
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
from django.conf import settings

from .search_strategies import SearchStrategyFactory

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
    @log_request
    @handle_exceptions
    @validate_pagination(max_limit=50)
    def get(self, request):
        query = request.GET.get("query", "")
        if not query:
            return Response(
                {"error": "query parameter is required."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        offset = request.validated_offset
        user = request.user

        # Parse tags and semantic query
        tag_names = TAG_REGEX.findall(query)
        semantic_query = TAG_REGEX.sub("", query).strip()

        # Get valid tag IDs
        valid_tag_ids = []
        if tag_names:
            valid_tag_ids = list(
                Tag.objects.filter(
                    user=user, tag__in=tag_names
                ).values_list("tag_id", flat=True)
            )

        # Select and execute strategy
        strategy = SearchStrategyFactory.create_strategy(
            has_query=bool(semantic_query),
            has_tags=bool(valid_tag_ids)
        )

        results = strategy.search(user, {
            'tag_ids': valid_tag_ids,
            'query_text': semantic_query,
            'offset': offset,
            'limit': 50
        })

        serializer = PhotoResponseSerializer(results, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)
