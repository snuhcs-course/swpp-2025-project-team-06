import uuid
from django.db.models import Exists, OuterRef
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
    ResStorySerializer,
    ResTagThumbnailSerializer,
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
    is_valid_uuid,
    recommend_photo_from_tag,
    recommend_photo_from_photo,
    compute_and_store_rep_vectors,
)
from .gpu_tasks import (
    process_and_embed_photos_batch,  # GPU-dependent task (batch)
)
from .storage_service import upload_photo, delete_photo

from config.redis import get_redis
import json

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
    def post(self, request):
        try:
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
                    print(f"[INFO] Skipping duplicate photo: User {request.user.id}, Path ID {data['photo_path_id']}")
                    
                    if storage_key:
                        print(f"       ... Cleaning up orphaned file from storage: {storage_key}")
                        delete_photo(storage_key)
                    
                    continue

            # Split into batches of 8 for GPU memory management
            if not all_metadata:
                return Response(
                    {"message": f"Processed {len(photos_data)} photos. All were duplicates."},
                    status=status.HTTP_200_OK, # 새 작업이 없으므로 200 OK
                )
            
            # all_metadata 리스트 (신규 사진) 기준으로 배치 생성
            BATCH_SIZE = 8
            for i in range(0, len(all_metadata), BATCH_SIZE):
                batch_metadata = all_metadata[i : i + BATCH_SIZE]
                process_and_embed_photos_batch.delay(batch_metadata)
                print(
                    f"[INFO] Dispatched batch task for {len(batch_metadata)} new photos (batch {i // BATCH_SIZE + 1})"
                )

            # 응답 메시지를 더 명확하게
            return Response(
                {"message": f"Processed {len(photos_data)} photos. {len(all_metadata)} new photos are being processed. {skipped_count} duplicates skipped."},
                status=status.HTTP_202_ACCEPTED,
            )
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
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
    def get(self, request):
        try:
            # Pagination 파라미터 검증
            try:
                offset = int(request.GET.get("offset", 0))
                limit = int(request.GET.get("limit", 100))
                if offset < 0 or limit < 1:
                    return Response(
                        {"error": "Offset must be non-negative and limit must be positive"},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                limit = min(limit, 100)  # 최대 100개 제한
            except ValueError:
                return Response(
                    {"error": "Invalid offset or limit parameter"},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            # created_at 기준으로 최신순 정렬 (가장 최신이 먼저)
            photos = Photo.objects.filter(user=request.user).order_by("-created_at")

            # 페이지네이션 적용
            photos = photos[offset:offset + limit]

            serializer = ResPhotoSerializer(photos, many=True)
            return Response(serializer.data, status=status.HTTP_200_OK)
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def get(self, request, photo_id):
        try:
            photo = Photo.objects.get(photo_id=photo_id, user=request.user)
            photo_tags = Photo_Tag.objects.filter(photo=photo, user=request.user)
            tags = [photo_tag.tag for photo_tag in photo_tags]

            tag_list = [
                {
                    "tag_id": str(tag.tag_id),
                    "tag": tag.tag,
                }
                for tag in tags
            ]

            photo_data = {"photo_path_id": photo.photo_path_id, "tags": tag_list}

            serializer = ResPhotoTagListSerializer(photo_data)

            return Response(serializer.data, status=status.HTTP_200_OK)

        except Photo.DoesNotExist:
            return Response(
                {"error": "Photo not found"}, status=status.HTTP_404_NOT_FOUND
            )

        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

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
    def delete(self, request, photo_id):
        try:
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
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def post(self, request):
        try:
            client = get_qdrant_client()
            serializer = ReqPhotoBulkDeleteSerializer(data=request.data)

            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            photos_raw_data = serializer.validated_data["photos"]

            photo_ids_to_delete = [data["photo_id"] for data in photos_raw_data]

            associated_photo_tags = Photo_Tag.objects.filter(
                photo__photo_id__in=photo_ids_to_delete, user=request.user
            )

            tag_ids_to_recompute = [str(pt.tag.tag_id) for pt in associated_photo_tags]

            client.delete(
                collection_name=IMAGE_COLLECTION_NAME,
                points_selector=[str(photo_id) for photo_id in photo_ids_to_delete],
                wait=True,
            )

            for tag_id in tag_ids_to_recompute:
                compute_and_store_rep_vectors.delay(request.user.id, tag_id)

            Photo.objects.filter(
                photo_id__in=photo_ids_to_delete, user=request.user
            ).delete()

            return Response(status=status.HTTP_204_NO_CONTENT)
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def get(self, request, tag_id):
        try:
            tag = Tag.objects.get(tag_id=tag_id, user=request.user)

            photo_tags = Photo_Tag.objects.filter(tag=tag, user=request.user)

            photos = [photo_tag.photo for photo_tag in photo_tags]

            photos_data = [
                {"photo_id": photo.photo_id, "photo_path_id": photo.photo_path_id}
                for photo in photos
            ]

            serializer = ResPhotoSerializer(photos_data, many=True)

            return Response(serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response(
                {"error": "Tag not found."}, status=status.HTTP_404_NOT_FOUND
            )
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def post(self, request, photo_id):
        try:
            serializer = ReqTagIdSerializer(data=request.data, many=True)

            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            tag_ids = [data["tag_id"] for data in serializer.validated_data]

            photo = Photo.objects.get(photo_id=photo_id, user=request.user)

            for tag_id in tag_ids:
                tag = Tag.objects.get(tag_id=tag_id, user=request.user)

                if Photo_Tag.objects.filter(
                    photo=photo, tag=tag, user=request.user
                ).exists():
                    continue  # Skip if the relationship already exists

                Photo_Tag.objects.create(
                    pt_id=uuid.uuid4(), photo=photo, tag=tag, user=request.user
                )

                compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))

            return Response(status=status.HTTP_200_OK)
        except Photo.DoesNotExist:
            return Response(
                {"error": "No such photo"}, status=status.HTTP_404_NOT_FOUND
            )
        except Tag.DoesNotExist:
            return Response({"error": "No such tag"}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def delete(self, request, photo_id, tag_id):
        try:
            photo = Photo.objects.get(photo_id=photo_id, user=request.user)
            tag = Tag.objects.get(tag_id=tag_id, user=request.user)

            photo_tag = Photo_Tag.objects.get(photo=photo, tag=tag, user=request.user)

            photo_tag.delete()

            compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))

            return Response(status=status.HTTP_204_NO_CONTENT)
        except (Photo.DoesNotExist, Tag.DoesNotExist):
            return Response(
                {"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND
            )
        except Photo_Tag.DoesNotExist:
            return Response(
                {"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND
            )
        except Exception as e:
            print(str(e))
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def get(self, request, photo_id, *args, **kwargs):
        try:
            client = get_qdrant_client()
            if not is_valid_uuid(photo_id):
                return Response(
                    {"error": "Request form mismatch."},
                    status=status.HTTP_400_NOT_FOUND,
                )

            points = client.retrieve(
                collection_name=IMAGE_COLLECTION_NAME, ids=[str(photo_id)]
            )
            if not points:
                return Response(
                    {"error": "No such photo"}, status=status.HTTP_404_NOT_FOUND
                )

            tags = tag_recommendation(request.user, photo_id)

            serializer = TagSerializer(tags, many=True)

            return Response(serializer.data, status=status.HTTP_200_OK)
        except Exception as e:
            print(str(e))
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def get(self, request, tag_id, *args, **kwargs):
        try:
            if not Tag.objects.filter(tag_id=tag_id, user=request.user).exists():
                return Response(
                    {"error": f"No tag with id {tag_id}"},
                    status=status.HTTP_404_NOT_FOUND,
                )

            photos = recommend_photo_from_tag(request.user, tag_id)

            serializer = ResPhotoSerializer(photos, many=True)

            return Response(serializer.data, status=status.HTTP_200_OK)

        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def post(self, request):
        serializer = ReqPhotoListSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        photo_ids = serializer.validated_data["photos"]
        user = request.user

        photos = recommend_photo_from_photo(user, photo_ids)

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
    def get(self, request):
        try:
            tags = Tag.objects.filter(user=request.user)

            # Build response data with thumbnail_path_id for each tag
            tags_data = []
            for tag in tags:
                # Get one photo_path_id from photos with this tag
                photo_tag = (
                    Photo_Tag.objects.filter(tag=tag, user=request.user)
                    .select_related("photo")
                    .first()
                )

                thumbnail_path_id = photo_tag.photo.photo_path_id if photo_tag else None

                tags_data.append(
                    {
                        "tag_id": tag.tag_id,
                        "tag": tag.tag,
                        "thumbnail_path_id": thumbnail_path_id,
                    }
                )

            response_serializer = ResTagThumbnailSerializer(tags_data, many=True)

            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response(
                {"error": "The user has no tags"}, status=status.HTTP_404_NOT_FOUND
            )
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

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
    def post(self, request):
        try:
            serializer = ReqTagNameSerializer(data=request.data)

            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            data = serializer.validated_data

            if len(data["tag"]) > 50:
                return Response(
                    {"error": "Tag name cannot exceed 50 characters."},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            if Tag.objects.filter(tag=data["tag"], user=request.user).exists():
                tag = Tag.objects.get(tag=data["tag"], user=request.user)
                response_serializer = ResTagIdSerializer({"tag_id": tag.tag_id})
                return Response(response_serializer.data, status=status.HTTP_200_OK)

            new_tag = Tag.objects.create(tag=data["tag"], user=request.user)

            response_serializer = ResTagIdSerializer({"tag_id": new_tag.tag_id})

            return Response(response_serializer.data, status=status.HTTP_201_CREATED)
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


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
    def delete(self, request, tag_id):
        try:
            try:
                tag = Tag.objects.get(tag_id=tag_id)
            except Tag.DoesNotExist:
                return Response(
                    {"error": "Tag not found."}, status=status.HTTP_404_NOT_FOUND
                )

            if tag.user != request.user:
                return Response(
                    {"error": "Forbidden - you are not the owner of this tag."},
                    status=status.HTTP_403_FORBIDDEN,
                )

            compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))

            tag.delete()

            return Response(status=status.HTTP_204_NO_CONTENT)
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

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
    def put(self, request, tag_id):
        try:
            serializer = ReqTagNameSerializer(data=request.data)

            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            data = serializer.validated_data

            try:
                old_tag = Tag.objects.get(tag_id=tag_id)
            except Tag.DoesNotExist:
                return Response(
                    {"error": "Tag not found"}, status=status.HTTP_404_NOT_FOUND
                )

            if old_tag.user != request.user:
                return Response(
                    {"error": "Forbidden - you are not the owner of this tag."},
                    status=status.HTTP_403_FORBIDDEN,
                )

            old_tag.tag = data["tag"]
            old_tag.save()

            response_serializer = ResTagIdSerializer({"tag_id": tag_id})
            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

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
    def get(self, request, tag_id):
        try:
            tag = Tag.objects.get(tag_id=tag_id)

            if tag.user != request.user:
                return Response(
                    {"error": "Forbidden - you are not the owner of this tag."},
                    status=status.HTTP_403_FORBIDDEN,
                )

            response_serializer = ResTagVectorSerializer({"tag": tag.tag})

            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response(
                {"error": "No tag with tag_id as its id"},
                status=status.HTTP_404_NOT_FOUND,
            )
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


class StoryView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Get Stories from redis",
        operation_description="Get stories from redis",
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
            )
        ]
    )
    def get(self, request):
        try:
            r = get_redis()
            exists = r.exists(request.user.id)
            if not exists:
                return Response(
                    {"error": "No stories found. Please generate stories first or try again later."},
                    status=status.HTTP_404_NOT_FOUND,
                )
            
            story_data_json = r.get(request.user.id)
            story_data = json.loads(story_data_json)
            serializer = ResStorySerializer(story_data, many=True)

            r.delete(request.user.id)

            return Response(serializer.data, status=status.HTTP_200_OK)
        
        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )


    @swagger_auto_schema(
        operation_summary="Generate and save stories into redis",
        operation_description="Generate and save stories into redis",
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
    def post(self, request):
        try:
            # 페이지네이션 파라미터 가져오기 및 검증
            try:
                size = int(request.GET.get("size", 20))
                if size < 1:
                    return Response(
                        {"error": "Size parameter must be positive"},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                size = min(size, 200)  # 최대 200개 제한
            except ValueError:
                return Response(
                    {"error": "Invalid size parameter"},
                    status=status.HTTP_400_BAD_REQUEST,
                )
            
            # 랜덤 정렬로 매번 다른 순서 보장
            # Filter photos that don't have any Photo_Tag relations
            has_tags = Photo_Tag.objects.filter(photo=OuterRef("pk"), user=request.user)
            photos_queryset = (
                Photo.objects.filter(user=request.user)
                .exclude(Exists(has_tags))
                .order_by("?")[:size]
            )  # 랜덤 정렬 + 슬라이싱

            story_data = []
            for photo in photos_queryset:
                tag_rec = tag_recommendation(request.user, str(photo.photo_id))
                photo_id = str(photo.photo_id)
                photo_path_id = photo.photo_path_id
                tags = [
                    {
                        "tag_id": str(t.tag_id),
                        "tag": t.tag,
                    } for t in tag_rec
                ]
                story_data.append({
                    "photo_id": photo_id,
                    "photo_path_id": photo_path_id,
                    "tags": tags,
                })
            
            r = get_redis()
            r.set(request.user.id, json.dumps(story_data))

            return Response({}, status=status.HTTP_200_OK)

        except Exception as e:
            return Response(
                 {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )