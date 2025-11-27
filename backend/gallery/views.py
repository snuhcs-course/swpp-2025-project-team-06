import requests
from django.db.models import Exists, OuterRef, Count, Subquery, Q
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.parsers import MultiPartParser, FormParser
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
from django.db import IntegrityError

from .reponse_serializers import (
    ResPhotoSerializer,
    ResPhotoTagListSerializer,
    ResTagIdSerializer,
    ResTagVectorSerializer,
    NewResStorySerializer,
    ResTagThumbnailSerializer,
    ResStorySerializer,
)
from .request_serializers import (
    ReqPhotoDetailSerializer,
    ReqTagNameSerializer,
    ReqTagIdSerializer,
    ReqPhotoListSerializer,
    ReqPhotoBulkDeleteSerializer,
)

from .serializers import TagSerializer
from .models import Photo_Tag, Tag, Photo
from .qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework.permissions import IsAuthenticated

from .tasks import (
    tag_recommendation,
    recommend_photo_from_tag,
    recommend_photo_from_photo,
    compute_and_store_rep_vectors,
    generate_stories_task,
)
from .gpu_tasks import (
    process_and_embed_photos_batch,  # GPU-dependent task (batch)
)
from .storage_service import upload_photo, delete_photo
import logging
from config.redis import get_redis
import json
from django.conf import settings
from .decorators import (
    log_request,
    validate_pagination,
    handle_exceptions,
    validate_uuid,
    require_ownership
)

logger = logging.getLogger(__name__)


class PhotoView(APIView):
    parser_classes = (MultiPartParser, FormParser)
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Upload Photos",
        operation_description="Upload photos to the backend. Photos are automatically chunked into batches of 8 for GPU efficiency.",
        request_body=ReqPhotoDetailSerializer(many=True),
        responses={
            202: openapi.Response(description="Accepted - Photos are being processed"),
            400: openapi.Response(description="Bad Request - Request form mismatch"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            ),
            openapi.Parameter(
                name="photo",
                in_=openapi.IN_FORM,
                type=openapi.TYPE_FILE,
                description="Photo file to upload",
                required=True,
            ),
            openapi.Parameter(
                name="metadata",
                in_=openapi.IN_FORM,
                type=openapi.TYPE_STRING,
                description="JSON string containing metadata for each photo: [{'filename': str, 'photo_path_id': int, 'created_at': str, 'lat': float, 'lng': float}, ...]",
                required=True,
            ),
        ],
        consumes=["multipart/form-data"],
    )
    @log_request
    @handle_exceptions
    def post(self, request):
        import json

        photos = request.FILES.getlist("photo")
        metadata_json = request.POST.get("metadata")

        if not metadata_json:
            return Response(
                {"error": "metadata field is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            metadata_list = json.loads(metadata_json)
        except json.JSONDecodeError:
            return Response(
                {"error": "Invalid JSON format in metadata field"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if len(photos) != len(metadata_list):
            return Response(
                {"error": "Number of photos and metadata entries must match"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        photos_data = []
        for i, photo in enumerate(photos):
            if i >= len(metadata_list):
                return Response(
                    {"error": "Insufficient metadata for all photos"},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            metadata = metadata_list[i]
            photos_data.append(
                {
                    "photo": photo,
                    "filename": metadata.get("filename"),
                    "photo_path_id": metadata.get("photo_path_id"),
                    "created_at": metadata.get("created_at"),
                    "lat": metadata.get("lat"),
                    "lng": metadata.get("lng"),
                }
            )

        serializer = ReqPhotoDetailSerializer(data=photos_data, many=True)

        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        photos_data = serializer.validated_data

        all_metadata = []
        skipped_count = 0

        for data in photos_data:
            storage_key = None

            try:
                image_file = data["photo"]

                storage_key = upload_photo(image_file)

                photo = Photo.objects.create(
                    user=request.user,
                    photo_id=storage_key,
                    photo_path_id=data["photo_path_id"],
                    filename=data["filename"],
                    created_at=data["created_at"],
                    lat=data["lat"],
                    lng=data["lng"],
                )

                all_metadata.append(
                    {
                        "storage_key": storage_key,
                        "user_id": request.user.id,
                        "filename": data["filename"],
                        "photo_path_id": data["photo_path_id"],
                        "created_at": data["created_at"].isoformat(),
                        "lat": data["lat"],
                        "lng": data["lng"],
                    }
                )

            except IntegrityError:
                skipped_count += 1
                logger.warning(
                    f"Skipping duplicate photo: User {request.user.id}, Path ID {data['photo_path_id']}"
                )

                if storage_key:
                    logger.warning(
                        f"Cleaning up orphaned file from storage: {storage_key}"
                    )
                    delete_photo(storage_key)

                continue

        if not all_metadata:
            return Response([], status=status.HTTP_200_OK)

        BATCH_SIZE = 8
        r = get_redis()
        tasks_info = []

        for i in range(0, len(all_metadata), BATCH_SIZE):
            batch_metadata = all_metadata[i : i + BATCH_SIZE]
            task = process_and_embed_photos_batch.delay(batch_metadata)
            
            redis_key = f"user_tasks:{request.user.id}"
            r.lpush(redis_key, task.id)
            r.expire(redis_key, 60 * 60 * 24)
            
            tasks_info.append({
                "task_id": str(task.id),
                "photo_path_ids": [m["photo_path_id"] for m in batch_metadata]
            })

            logger.info(
                f"Dispatched batch task {task.id} for {len(batch_metadata)} new photos (batch {i // BATCH_SIZE + 1})"
            )

        return Response(
            tasks_info,
            status=status.HTTP_202_ACCEPTED,
        )

    @swagger_auto_schema(
        operation_summary="Photo All View",
        operation_description="Get all the photos the user has uploaded",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success", schema=ResPhotoSerializer(many=True)
            ),
            400: openapi.Response(
                description="Bad Request - Invalid offset or limit parameter"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            ),
            openapi.Parameter(
                "offset",
                openapi.IN_QUERY,
                description="number of photos to skip",
                type=openapi.TYPE_INTEGER,
            ),
            openapi.Parameter(
                "limit",
                openapi.IN_QUERY,
                description="maximum number of photos to return",
                type=openapi.TYPE_INTEGER,
            ),
        ],
    )
    @log_request
    @handle_exceptions
    @validate_pagination(max_limit=100)
    def get(self, request):
        offset = request.validated_offset
        limit = request.validated_limit

        photos = Photo.objects.filter(user=request.user).order_by("-created_at")
        photos = photos[offset : offset + limit]

        serializer = ResPhotoSerializer(photos, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)


class PhotoDetailView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Photo Detail View",
        operation_description="Get detailed information of tags of a single photo",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success", schema=ResPhotoTagListSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No photo with photo_id as its id"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('photo_id')
    @require_ownership(Photo, 'photo_id', 'photo_id')
    def get(self, request, photo_id):
        photo = Photo.objects.get(photo_id=photo_id)
        photo_tags = Photo_Tag.objects.filter(photo=photo, user=request.user)
        tags = [photo_tag.tag for photo_tag in photo_tags]

        tag_list = [
            {
                "tag_id": str(tag.tag_id),
                "tag": tag.tag,
            }
            for tag in tags
        ]

        lat = str(photo.lat)
        lng = str(photo.lng)

        r = get_redis()
        address = ""
        key = f"cord:({lat},{lng})"
        
        if r.get(key):
            address = r.get(key)
        else:
            URL = f"https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x={lng}&y={lat}"
            KM_REST_API_KEY = settings.KM_REST_API_KEY
            headers = {"Authorization": f"KakaoAK {KM_REST_API_KEY}"}
            resp = requests.get(URL, headers=headers, timeout=5)

            if resp.status_code == 200:
                data = resp.json()
                if data.get("documents"):
                    address = data["documents"][0]["address_name"]

            r.set(key, address, ex=60 * 60 * 24)

        photo_data = {
            "photo_path_id": photo.photo_path_id,
            "address": address,
            "tags": tag_list,
        }

        serializer = ResPhotoTagListSerializer(photo_data)
        return Response(serializer.data, status=status.HTTP_200_OK)

    @swagger_auto_schema(
        operation_summary="Delete a Photo",
        operation_description="Delete a photo from the app",
        request_body=None,
        responses={
            204: openapi.Response(description="No Content"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('photo_id')
    @require_ownership(Photo, 'photo_id', 'photo_id')
    def delete(self, request, photo_id):
        client = get_qdrant_client()

        associated_photo_tags = Photo_Tag.objects.filter(
            photo__photo_id=photo_id, user=request.user
        )
        tag_ids_to_recompute = [str(pt.tag.tag_id) for pt in associated_photo_tags]

        Photo.objects.filter(photo_id=photo_id, user=request.user).delete()

        client.delete(
            collection_name=IMAGE_COLLECTION_NAME,
            points_selector=[str(photo_id)],
            wait=True,
        )

        for tag_id in tag_ids_to_recompute:
            compute_and_store_rep_vectors.delay(request.user.id, tag_id)

        return Response(status=status.HTTP_204_NO_CONTENT)


class BulkDeletePhotoView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Delete Photos",
        operation_description="Delete photos from the app",
        request_body=ReqPhotoBulkDeleteSerializer(),
        responses={
            204: openapi.Response(description="No Content"),
            400: openapi.Response(description="Bad Request - Request form mismatch"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    def post(self, request):
        client = get_qdrant_client()
        serializer = ReqPhotoBulkDeleteSerializer(data=request.data)

        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        photos_raw_data = serializer.validated_data["photos"]
        photo_ids_to_delete = [data["photo_id"] for data in photos_raw_data]

        associated_photo_tags = Photo_Tag.objects.filter(
            photo__photo_id__in=photo_ids_to_delete, user=request.user
        )
        tag_ids_to_recompute = {str(pt.tag.tag_id) for pt in associated_photo_tags}

        Photo.objects.filter(
            photo_id__in=photo_ids_to_delete, user=request.user
        ).delete()
        client.delete(
            collection_name=IMAGE_COLLECTION_NAME,
            points_selector=[str(pid) for pid in photo_ids_to_delete],
            wait=True,
        )

        for tag_id in tag_ids_to_recompute:
            compute_and_store_rep_vectors.delay(request.user.id, tag_id)

        return Response(status=status.HTTP_204_NO_CONTENT)


class GetPhotosByTagView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Photos List for a Tag Album",
        operation_description="Get a list of photo_path_ids in a tag album",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success", schema=ResPhotoSerializer(many=True)
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(description="Not Found - Photo not found"),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('tag_id')
    @require_ownership(Tag, 'tag_id', 'tag_id')
    def get(self, request, tag_id):
        tag = Tag.objects.get(tag_id=tag_id)
        photo_tags = Photo_Tag.objects.filter(tag=tag, user=request.user)
        photos = [photo_tag.photo for photo_tag in photo_tags]

        serializer = ResPhotoSerializer(photos, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)


class PostPhotoTagsView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Add Tags to a Photo",
        operation_description="Create new Tag-Photo relationships",
        request_body=ReqTagIdSerializer(many=True),
        responses={
            201: openapi.Response(description="Success"),
            400: openapi.Response(description="Bad Request - Request form mismatch"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(description="Not Found - No such tag or photo"),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('photo_id')
    @require_ownership(Photo, 'photo_id', 'photo_id')
    def post(self, request, photo_id):
        serializer = ReqTagIdSerializer(data=request.data, many=True)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        tag_ids = [data["tag_id"] for data in serializer.validated_data]
        photo = Photo.objects.get(photo_id=photo_id)

        tags_to_recompute = set()
        for tag_id in tag_ids:
            tag = Tag.objects.get(tag_id=tag_id, user=request.user)
            if not Photo_Tag.objects.filter(photo=photo, tag=tag, user=request.user).exists():
                Photo_Tag.objects.create(photo=photo, tag=tag, user=request.user)
                tags_to_recompute.add(str(tag_id))

        for tag_id in tags_to_recompute:
            compute_and_store_rep_vectors.delay(request.user.id, tag_id)

        return Response(status=status.HTTP_200_OK)


class DeletePhotoTagsView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Delete a Tag from a Photo",
        operation_description="Delete a Tag-Photo relationship",
        request_body=None,
        responses={
            204: openapi.Response(description="No Content"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(description="Not Found - No such tag or photo"),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('photo_id')
    @validate_uuid('tag_id')
    @require_ownership(Photo, 'photo_id', 'photo_id')
    @require_ownership(Tag, 'tag_id', 'tag_id')
    def delete(self, request, photo_id, tag_id):
        photo_tag = Photo_Tag.objects.get(
            photo__photo_id=photo_id, tag__tag_id=tag_id, user=request.user
        )
        tag = photo_tag.tag
        photo_tag.delete()

        # Check if tag still has any photos associated
        remaining_photo_tags = Photo_Tag.objects.filter(tag=tag, user=request.user).count()

        if remaining_photo_tags == 0:
            # No photos left for this tag - delete the tag itself
            tag.delete()
            # No need to compute repvec - tag is gone
            # compute_and_store_rep_vectors will handle cleanup in Qdrant
            compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))
        else:
            # Tag still has photos - recompute representative vectors
            compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))

        return Response(status=status.HTTP_204_NO_CONTENT)


class GetRecommendTagView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Recommended Tag of Photo",
        operation_description="Get recommended tag about a photo.",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success", schema=TagSerializer(many=True)
            ),
            400: openapi.Response(description="Bad Request - Request form mismatch"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - : No photo with photo_id as its id"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('photo_id')
    @require_ownership(Photo, 'photo_id', 'photo_id')
    def get(self, request, photo_id, *args, **kwargs):
        # Returns list of dicts: {'tag': name, 'tag_id': id_or_empty, 'is_preset': bool}
        tag_recommendations = tag_recommendation(request.user, photo_id)
        return Response(tag_recommendations, status=status.HTTP_200_OK)


class PhotoRecommendationView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Recommended Photos of a Tag",
        operation_description="Get recommended photos about a tag.",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResPhotoSerializer(many=True),
            ),
            400: openapi.Response(description="Bad Request - Request form mismatch"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - : No tag with tag_id as its id"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('tag_id')
    @require_ownership(Tag, 'tag_id', 'tag_id')
    def get(self, request, tag_id, *args, **kwargs):
        photos = recommend_photo_from_tag(request.user, tag_id)
        serializer = ResPhotoSerializer(photos, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)


class PhotoToPhotoRecommendationView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Recommended Photos of Given Photos",
        operation_description="Get recommended photos based on a list of input photos using bipartite graph analysis.",
        request_body=ReqPhotoListSerializer,
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResPhotoSerializer(many=True),
            ),
            400: openapi.Response(description="Bad Request - Request form mismatch"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    def post(self, request):
        serializer = ReqPhotoListSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        photo_ids = serializer.validated_data["photos"]
        photos = recommend_photo_from_photo(request.user, photo_ids)

        return Response(photos, status=status.HTTP_200_OK)


class TagView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get All Tags",
        operation_description="Get all the tags that the user created",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResTagThumbnailSerializer(many=True),
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No tag with tag_id as its id"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    def get(self, request):
        latest_photo_subquery = (
            Photo_Tag.objects.filter(tag=OuterRef("pk"), user=request.user)
            .select_related("photo")
            .order_by("-photo__created_at")
        )

        tags = Tag.objects.filter(user=request.user).annotate(
            photo_count=Count("photo_tag", filter=Q(photo_tag__user=request.user)),
            thumbnail_path_id=Subquery(
                latest_photo_subquery.values("photo__photo_path_id")[:1]
            ),
        )

        response_serializer = ResTagThumbnailSerializer(tags, many=True)
        return Response(response_serializer.data, status=status.HTTP_200_OK)

    @swagger_auto_schema(
        operation_summary="Create a Tag",
        operation_description="Create a new tag",
        request_body=ReqTagNameSerializer(),
        responses={
            201: openapi.Response(description="Created", schema=ResTagIdSerializer()),
            400: openapi.Response(description="Bad Request - Request form mismatch"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    def post(self, request):
        serializer = ReqTagNameSerializer(data=request.data)

        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data

        if len(data["tag"]) > 50:
            return Response(
                {"error": "Tag name cannot exceed 50 characters."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        tag, created = Tag.objects.get_or_create(tag=data["tag"], user=request.user)
        
        status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
        
        response_serializer = ResTagIdSerializer({"tag_id": tag.tag_id})
        return Response(response_serializer.data, status=status_code)


class TagDetailView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Delete a Tag",
        operation_description="Delete a tag (only the tag owner can delete)",
        request_body=None,
        responses={
            204: openapi.Response(description="No Content"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            403: openapi.Response(
                description="Forbidden - You are not the owner of this tag"
            ),
            404: openapi.Response(
                description="Not Found - No tag such that tag's id is tag_id"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('tag_id')
    @require_ownership(Tag, 'tag_id', 'tag_id')
    def delete(self, request, tag_id):
        tag = Tag.objects.get(tag_id=tag_id)
        compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))
        tag.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)

    @swagger_auto_schema(
        operation_summary="Rename a Tag",
        operation_description="Change the name of a tag",
        request_body=ReqTagNameSerializer(),
        responses={
            200: openapi.Response(description="Success", schema=ResTagIdSerializer()),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            403: openapi.Response(
                description="Forbidden - You are not the owner of this tag"
            ),
            404: openapi.Response(description="Not Found - Tag not found"),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('tag_id')
    @require_ownership(Tag, 'tag_id', 'tag_id')
    def put(self, request, tag_id):
        serializer = ReqTagNameSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data
        old_tag = Tag.objects.get(tag_id=tag_id)
        old_tag.tag = data["tag"]
        old_tag.save()

        response_serializer = ResTagIdSerializer({"tag_id": tag_id})
        return Response(response_serializer.data, status=status.HTTP_200_OK)

    @swagger_auto_schema(
        operation_summary="Get Tag Info",
        operation_description="Get information about a tag",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success", schema=ResTagVectorSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            403: openapi.Response(
                description="Forbidden - You are not the owner of this tag"
            ),
            404: openapi.Response(
                description="Not Found - No tag with tag_id as its id"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    @validate_uuid('tag_id')
    @require_ownership(Tag, 'tag_id', 'tag_id')
    def get(self, request, tag_id):
        tag = Tag.objects.get(tag_id=tag_id)
        response_serializer = ResTagVectorSerializer({"tag": tag.tag})
        return Response(response_serializer.data, status=status.HTTP_200_OK)


class StoryView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Stories",
        operation_description="Get stories generated from user's photos with pagination",
        request_body=None,
        responses={
            200: openapi.Response(description="Success", schema=ResStorySerializer()),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            ),
            openapi.Parameter(
                "size",
                openapi.IN_QUERY,
                description="number of photos",
                type=openapi.TYPE_INTEGER,
            ),
        ],
    )
    @log_request
    @handle_exceptions
    def get(self, request):
        try:
            size = int(request.GET.get("size", 20))
            if size < 1:
                return Response(
                    {"error": "Size parameter must be positive"},
                    status=status.HTTP_400_BAD_REQUEST,
                )
            size = min(size, 200)
        except ValueError:
            return Response(
                {"error": "Invalid size parameter"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        has_tags = Photo_Tag.objects.filter(photo=OuterRef("pk"), user=request.user)
        photos_queryset = (
            Photo.objects.filter(user=request.user)
            .exclude(Exists(has_tags))
            .order_by("?")[:size]
        )

        photos_data = [
            {
                "photo_id": str(photo.photo_id),
                "photo_path_id": photo.photo_path_id,
                "created_at": photo.created_at,
            }
            for photo in photos_queryset
        ]

        story_response = {"recs": photos_data}
        serializer = ResStorySerializer(story_response)
        return Response(serializer.data, status=status.HTTP_200_OK)


class NewStoryView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Stories from redis",
        operation_description="Get stories from redis",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success", schema=NewResStorySerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            )
        ],
    )
    @log_request
    @handle_exceptions
    def get(self, request):
        r = get_redis()
        exists = r.exists(request.user.id)
        if not exists:
            return Response(
                {"error": "We're working on your stories! Please wait a moment."},
                status=status.HTTP_404_NOT_FOUND,
            )

        story_data_json = r.get(request.user.id)
        story_data = json.loads(story_data_json)
        serializer = NewResStorySerializer(story_data, many=True)

        r.delete(request.user.id)

        return Response(serializer.data, status=status.HTTP_200_OK)

    @swagger_auto_schema(
        operation_summary="Generate and save stories into redis",
        operation_description="Generate and save stories into redis",
        request_body=None,
        responses={
            202: openapi.Response(description="Accepted - Story generation started"),
            400: openapi.Response(description="Bad Request - Invalid size parameter"),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            ),
            openapi.Parameter(
                "size",
                openapi.IN_QUERY,
                description="number of photos",
                type=openapi.TYPE_INTEGER,
            ),
        ],
    )
    @log_request
    @handle_exceptions
    def post(self, request):
        try:
            size = int(request.GET.get("size", 20))
            if size < 1:
                return Response(
                    {"error": "Size parameter must be positive"},
                    status=status.HTTP_400_BAD_REQUEST,
                )
            size = min(size, 200)
        except ValueError:
            return Response(
                {"error": "Invalid size parameter"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        generate_stories_task.delay(request.user.id, size)

        return Response(
            {"message": "Story generation started"}, status=status.HTTP_202_ACCEPTED
        )


class TaskStatusView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Celery Task Status",
        operation_description="Retrieve the status of Celery tasks by ID or a paginated list of recent tasks.",
        manual_parameters=[
            openapi.Parameter(
                "Authorization",
                openapi.IN_HEADER,
                description="access token",
                type=openapi.TYPE_STRING,
            ),
            openapi.Parameter(
                "task_ids",
                openapi.IN_QUERY,
                description="Comma-separated list of task IDs to check. If not provided, recent tasks are returned.",
                type=openapi.TYPE_STRING,
                required=False,
            ),
            openapi.Parameter(
                "limit",
                openapi.IN_QUERY,
                description="Number of recent tasks to retrieve (if task_ids is not provided). Default is 100.",
                type=openapi.TYPE_INTEGER,
                required=False,
            ),
            openapi.Parameter(
                "offset",
                openapi.IN_QUERY,
                description="Offset for retrieving recent tasks (if task_ids is not provided). Default is 0.",
                type=openapi.TYPE_INTEGER,
                required=False,
            ),
        ],
        responses={
            200: openapi.Response(
                description="Success",
                schema=openapi.Schema(
                    type=openapi.TYPE_ARRAY,
                    items=openapi.Schema(
                        type=openapi.TYPE_OBJECT,
                        properties={
                            "task_id": openapi.Schema(type=openapi.TYPE_STRING),
                            "status": openapi.Schema(type=openapi.TYPE_STRING),
                            "result": openapi.Schema(type=openapi.TYPE_STRING, nullable=True),
                            "date_done": openapi.Schema(type=openapi.TYPE_STRING, nullable=True),
                        },
                    ),
                ),
            ),
            401: openapi.Response(description="Unauthorized - The refresh token is expired"),
            500: openapi.Response(description="Internal Server Error"),
        },
    )
    @log_request
    @handle_exceptions
    def get(self, request):
        from celery.result import AsyncResult
        
        task_ids_param = request.GET.get("task_ids")
        r = get_redis()
        
        if task_ids_param:
            task_ids = [tid.strip() for tid in task_ids_param.split(",") if tid.strip()]
        else:
            limit = int(request.GET.get("limit", 100))
            offset = int(request.GET.get("offset", 0))
            
            redis_key = f"user_tasks:{request.user.id}"
            
            task_ids_bytes = r.lrange(redis_key, offset, offset + limit - 1)
            task_ids = [t.decode('utf-8') for t in task_ids_bytes]
        
        results = []
        for task_id in task_ids:
            res = AsyncResult(task_id)
            results.append({
                "task_id": task_id,
                "status": res.status,
            })
            
        return Response(results, status=status.HTTP_200_OK)
