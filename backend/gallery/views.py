import uuid
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.parsers import MultiPartParser, FormParser
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi


from .reponse_serializers import (
    ResPhotoSerializer,
    ResPhotoTagListSerializer,
    ResPhotoIdSerializer,
    ResTagIdSerializer,
    ResTagVectorSerializer,
    ResStorySerializer,
    ResTagAlbumSerializer,
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

from django.core.cache import cache

from .tasks import (
    tag_recommendation,
    is_valid_uuid,
    recommend_photo_from_tag,
    recommend_photo_from_photo,
    compute_and_store_rep_vectors,
)
from .gpu_tasks import process_and_embed_photo  # GPU-dependent task
from .storage_service import upload_photo



class PhotoView(APIView):
    parser_classes = (MultiPartParser, FormParser)
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Upload Photos",
        operation_description="Upload photos to the backend",
        request_body=ReqPhotoDetailSerializer(many=True),
        responses={
            201: openapi.Response(
                description="Created", schema=ResPhotoIdSerializer(many=True)
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

            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

            for data in photos_data:
                image_file = data["photo"]

                # Upload to shared storage (MinIO or local) - storage key is UUID-based
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

                process_and_embed_photo.delay(
                    storage_key=storage_key,
                    user_id=request.user.id,
                    filename=data["filename"],
                    photo_path_id=data["photo_path_id"],
                    created_at=data["created_at"].isoformat(),
                    lat=data["lat"],
                    lng=data["lng"],
                )


            return Response(
                {"message": "Photos are being processed."},
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
    def get(self, request):
        try:
            photos = Photo.objects.filter(user=request.user)
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
                } for tag in tags
            ]

            photo_data = {
                "photo_path_id": photo.photo_path_id,
                "tags": tag_list
            }

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

            associated_photo_tags = Photo_Tag.objects.filter(photo__photo_id=photo_id, user=request.user)
            tag_ids_to_recompute = [str(pt.tag.tag_id) for pt in associated_photo_tags]

            Photo.objects.filter(photo_id=photo_id, user=request.user).delete()

            client.delete(
                collection_name=IMAGE_COLLECTION_NAME,
                points_selector=[str(photo_id)],
                wait=True,
            )
            
            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

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
            
            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

            for tag_id in tag_ids_to_recompute:
                compute_and_store_rep_vectors.delay(request.user.id, tag_id)

            Photo.objects.filter(photo_id__in=photo_ids_to_delete, user=request.user).delete()

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

            photo_tags = Photo_Tag.objects.filter(
                tag=tag,
                user=request.user
            )

            photos = [
                photo_tag.photo for photo_tag in photo_tags
            ]

            photos_data = [
                {
                    "photo_id": photo.photo_id,
                    "photo_path_id": photo.photo_path_id
                } for photo in photos
            ]
            
            response_data = {
                "photos": photos_data
            }

            return Response(ResTagAlbumSerializer(response_data).data, status=status.HTTP_200_OK)
        
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
                    pt_id=uuid.uuid4(), 
                    photo=photo, 
                    tag=tag, 
                    user=request.user
                )

                photo.is_tagged = True
                photo.save()

                print(f"[INFO] Invalidating graph cache for user {request.user.id}")
                cache.delete(f"user_{request.user.id}_combined_graph")

                compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))

            return Response(status=status.HTTP_200_OK)
        except Photo.DoesNotExist:
            return Response(
                {"error": "No such photo"}, status=status.HTTP_404_NOT_FOUND
            )
        except Tag.DoesNotExist:
            return Response(
                {"error": "No such tag"}, status=status.HTTP_404_NOT_FOUND
            )
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
            
            photo_tag = Photo_Tag.objects.get(
                photo=photo, tag=tag, user=request.user
            )

            photo_tag.delete()

            # Check if any tags remain for the photo
            remaining_tags = Photo_Tag.objects.filter(photo=photo, user=request.user)

            if not remaining_tags.exists():
                photo.is_tagged = False
                photo.save()

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

            return Response(photos, status=status.HTTP_200_OK)

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
                description="Success", schema=TagSerializer(many=True)
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

            response_serializer = TagSerializer(tags, many=True)

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
            
            # 태그 삭제시 영향받는 사진들 is_tagged 필드 업데이트
            affected_photo_tags = Photo_Tag.objects.filter(tag=tag, user=request.user)
            affected_photo_ids = [pt.photo.photo_id for pt in affected_photo_tags]
            for affected_photo_id in affected_photo_ids:
                affected_photo = Photo.objects.get(photo_id=affected_photo_id, user=request.user)
                affected_photo_tags_count = Photo_Tag.objects.filter(photo=affected_photo, user=request.user).count()
                if affected_photo_tags_count <= 1:
                    affected_photo.is_tagged = False
                    affected_photo.save()
                
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
                "size", openapi.IN_QUERY, description="number of photos", type=openapi.TYPE_INTEGER
            ),
        ]
    )
    def get(self, request):
        try:   
            # 페이지네이션 파라미터 가져오기 및 검증
            try:
                size = int(request.GET.get('size', 20))
                if size < 1:
                    return Response(
                        {"error": "Size parameter must be positive"}, 
                        status=status.HTTP_400_BAD_REQUEST
                    )
                size = min(size, 200)  # 최대 200개 제한
            except ValueError:
                return Response(
                    {"error": "Invalid size parameter"}, 
                    status=status.HTTP_400_BAD_REQUEST
                )

            # 랜덤 정렬로 매번 다른 순서 보장
            photos_queryset = Photo.objects.filter(
                user=request.user,
                is_tagged=False
            ).order_by('?')[:size]  # 랜덤 정렬 + 슬라이싱
            
            # QuerySet을 유지하면서 데이터 직렬화
            photos_data = [
                {
                    "photo_id": str(photo.photo_id),
                    "photo_path_id": photo.photo_path_id
                } 
                for photo in photos_queryset
            ]

            # 빈 결과 처리
            if not photos_data:
                story_response = {"recs": []}
            else:
                story_response = {"recs": photos_data}

            serializer = ResStorySerializer(story_response)
            return Response(serializer.data, status=status.HTTP_200_OK)

        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )
