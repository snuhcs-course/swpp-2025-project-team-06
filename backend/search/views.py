from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework_simplejwt.authentication import JWTAuthentication
from gallery.tasks import create_query_embedding 
from gallery.qdrant_utils import client, IMAGE_COLLECTION_NAME
from .response_serializers import SemanticSearchResponseSerializer
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi



class SemanticSearchView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
    operation_summary="Semantic Search",
    operation_description="Search photos semantically using a query string.",
    manual_parameters=[
        openapi.Parameter(
            'query',
            openapi.IN_QUERY,
            description='Search query string',
            type=openapi.TYPE_STRING,
            required=True
        ),
        openapi.Parameter(
            'offset',
            openapi.IN_QUERY,
            description='Number of results to skip (for pagination)',
            type=openapi.TYPE_INTEGER,
            required=False,
            default=0
        ),
        openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)
    ],
    responses={
        200: openapi.Response(
            description="OK - List of photo IDs matching the query",
            schema=SemanticSearchResponseSerializer
        ),
        400: openapi.Response(
            description="Bad Request - Invalid input"
        ),
        401: openapi.Response(
            description="Unauthorized - Invalid or expired token"
        ),
    },
)
    def get(self, request):
        query = request.GET.get('query', '')
        offset = int(request.GET.get('offset', 0))
        
        query_embedding = create_query_embedding(query).tolist()

        search_result = client.query_points(
            collection_name=IMAGE_COLLECTION_NAME,
            query=query_embedding,
            query_filter=None,
            limit=20,
            offset=offset,
        ).points

        payloads = [point.payload["photo_path_id"] for point in search_result]

        serializer = SemanticSearchResponseSerializer(data={"photos": payloads})

        if serializer.is_valid():
            return Response(serializer.data, status=status.HTTP_200_OK)
        else:
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
