import uuid
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.parsers import MultiPartParser, FormParser
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
from qdrant_client import models


from .reponse_serializers import (
    ResPhotoSerializer,
    ResPhotoTagListSerializer,
    ResPhotoIdSerializer,
    ResTagIdSerializer,
    ResTagVectorSerializer,
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
from .models import Photo_Tag, Tag, User, Photo_Caption
from .qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework.permissions import IsAuthenticated

from django.core.files.storage import FileSystemStorage
from django.conf import settings
from django.core.cache import cache

from .tasks import (
    process_and_embed_photo,
    tag_recommendation,
    is_valid_uuid,
    recommend_photo_from_tag,
    recommend_photo_from_photo,
    compute_and_store_rep_vectors,
)


from django.db import transaction


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
                description="JSON string containing metadata for each photo: [{'filename': str, 'photo_path_id': str, 'created_at': str, 'lat': float, 'lng': float}, ...]",
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

            fs = FileSystemStorage(location=settings.MEDIA_ROOT)
            
            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

            for data in photos_data:
                image_file = data["photo"]

                temp_filename = f"{uuid.uuid4()}_{image_file.name}"
                saved_path = fs.save(temp_filename, image_file)
                full_path = fs.path(saved_path)

                process_and_embed_photo.delay(
                    image_path=full_path,
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
        client = get_qdrant_client()
        try:
            user_filter = models.Filter(
                must=[
                    models.FieldCondition(
                        key="user_id",
                        match=models.MatchValue(value=request.user.id),
                    )
                ]
            )

            all_user_points = []
            next_offset = None

            while True:
                points, next_offset = client.scroll(
                    collection_name=IMAGE_COLLECTION_NAME,
                    scroll_filter=user_filter,
                    limit=200,
                    offset=next_offset,
                    with_payload=True,
                )

                all_user_points.extend(points)

                if next_offset is None:
                    break

            photos = []
            for point in all_user_points:
                photos.append(
                    {
                        "photo_id": point.id,
                        "photo_path_id": point.payload.get("photo_path_id"),
                    }
                )

            return Response(photos, status=status.HTTP_200_OK)
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
            client = get_qdrant_client()
            points = client.retrieve(
                collection_name=IMAGE_COLLECTION_NAME,
                ids=[str(photo_id)],
                with_payload=True,
            )

            if not points:
                return Response(
                    {"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND
                )

            # 사용자 권한 확인 (해당 사진이 현재 사용자의 것인지 확인)
            photo_point = points[0]
            if photo_point.payload.get("user_id") != request.user.id:
                return Response(
                    {"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND
                )

            # 해당 사진의 태그들 조회
            photo_tags = Photo_Tag.objects.filter(photo_id=photo_id)

            # 태그 정보 구성
            tags_list = []
            for pt in photo_tags:
                tag = Tag.objects.get(tag_id=pt.tag_id)
                tags_list.append({"tag_id": str(tag.tag_id), "tag": tag.tag})

            # 응답 데이터 구성
            photo_data = {
                "photo_path_id": photo_point.payload.get("photo_path_id"),
                "tags": tags_list,
            }

            serializer = ResPhotoTagListSerializer(photo_data)

            return Response(serializer.data, status=status.HTTP_200_OK)

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
            Photo_Tag.objects.filter(photo_id=photo_id, user=request.user).delete()
            Photo_Caption.objects.filter(photo_id=photo_id, user=request.user).delete()
            
            associated_tags = Photo_Tag.objects.filter(photo_id=photo_id, user=request.user).select_related('tag')
            tag_ids_to_recompute = [str(pt.tag.tag_id) for pt in associated_tags]

            associated_tags.delete()

            client.delete(
                collection_name=IMAGE_COLLECTION_NAME,
                points_selector=[str(photo_id)],
                wait=True,
            )
            
            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

            for tag_id_str in set(tag_ids_to_recompute):
                compute_and_store_rep_vectors.delay(request.user.id, tag_id_str)

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

            photos_data = serializer.validated_data["photos"]

            photos_to_delete = [data["photo_id"] for data in photos_data]

            # 일단 동작만 하게 해놓음.
            # 여러 API가 동시에 들어와서 중복 삭제하는 등의 문제를 해결하는건 나중에 atomic transaction 등의 방식으로 수정
            associated_tags = Photo_Tag.objects.filter(
                photo_id__in=photos_to_delete, user=request.user
            ).select_related('tag')
            tag_ids_to_recompute = [str(pt.tag.tag_id) for pt in associated_tags]
            associated_tags.delete()

            client.delete(
                collection_name=IMAGE_COLLECTION_NAME,
                points_selector=[str(photo_id) for photo_id in photos_to_delete],
                wait=True,
            )
            
            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

            for tag_id_str in set(tag_ids_to_recompute):
                compute_and_store_rep_vectors.delay(request.user.id, tag_id_str)

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
            client = get_qdrant_client()
            photo_tags = Photo_Tag.objects.filter(user=request.user, tag_id=tag_id)

            photo_ids = [str(pt.photo_id) for pt in photo_tags]

            retrieved_points = client.retrieve(
                collection_name=IMAGE_COLLECTION_NAME, ids=photo_ids, with_payload=True
            )

            photos = []

            for point in retrieved_points:
                photos.append(
                    {
                        "photo_id": point.id,
                        "photo_path_id": point.payload.get("photo_path_id"),
                    }
                )

            serializer = ResPhotoSerializer(photos, many=True)

            return Response(serializer.data, status=status.HTTP_200_OK)
        except Photo_Tag.DoesNotExist:
            return Response(
                {"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND
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
            client = get_qdrant_client()
            serializer = ReqTagIdSerializer(data=request.data, many=True)

            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            try:
                User.objects.get(pk=request.user.pk)
            except User.DoesNotExist:
                return Response(
                    {"error": "User not found"}, status=status.HTTP_401_UNAUTHORIZED
                )

            tag_ids = [data["tag_id"] for data in serializer.validated_data]

            points = client.retrieve(
                collection_name=IMAGE_COLLECTION_NAME, ids=[str(photo_id)]
            )
            if not points:
                return Response(
                    {"error": "No such photo"}, status=status.HTTP_404_NOT_FOUND
                )

            with transaction.atomic():
                for tag_id in tag_ids:
                    pt_id = uuid.uuid4()

                    tag = Tag.objects.get(tag_id=tag_id, user=request.user)

                    if Photo_Tag.objects.filter(
                        photo_id=photo_id, tag=tag, user=request.user
                    ).exists():
                        continue  # Skip if the relationship already exists

                    Photo_Tag.objects.create(
                        pt_id=pt_id, photo_id=photo_id, tag=tag, user=request.user
                    )

            # now update the metadata isTagged in Qdrant
            client.set_payload(
                collection_name=IMAGE_COLLECTION_NAME,
                payload={"isTagged": True},
                points=[str(photo_id)],
            )
            
            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

            for tag_id in tag_ids:
                compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))

            return Response(status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response(
                {"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND
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
            client = get_qdrant_client()
            tag = Tag.objects.get(tag_id=tag_id, user=request.user)
            if not client.retrieve(
                collection_name=IMAGE_COLLECTION_NAME, ids=[str(photo_id)]
            ):
                return Response(
                    {"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND
                )

            photo_tag = Photo_Tag.objects.get(
                photo_id=photo_id, tag=tag, user=request.user
            )

            photo_tag.delete()

            # Check if any tags remain for the photo
            remaining_tags = Photo_Tag.objects.filter(
                photo_id=photo_id, user=request.user
            )
            if not remaining_tags.exists():
                # If no tags remain, update isTagged to False in Qdrant
                client.set_payload(
                    collection_name=IMAGE_COLLECTION_NAME,
                    payload={"isTagged": False},
                    points=[str(photo_id)],
                )
                
            print(f"[INFO] Invalidating graph cache for user {request.user.id}")
            cache.delete(f"user_{request.user.id}_combined_graph")

            compute_and_store_rep_vectors.delay(request.user.id, str(tag_id))

            return Response(status=status.HTTP_204_NO_CONTENT)
        except Tag.DoesNotExist:
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
                "page",
                openapi.IN_QUERY,
                description="Page number (default: 1)",
                type=openapi.TYPE_INTEGER,
            ),
            openapi.Parameter(
                "page_size",
                openapi.IN_QUERY,
                description="Number of items per page (default: 50, max: 200)",
                type=openapi.TYPE_INTEGER,
            ),
        ],
    )
    def get(self, request):
        try:
            client = get_qdrant_client()

            # 페이지네이션 파라미터 가져오기
            page = int(request.GET.get("page", 1))
            page_size = min(
                int(request.GET.get("pagesize", 20)), 200
            )  # 최대 200개 제한

            if page < 1:
                page = 1
            if page_size < 1:
                page_size = 20

            # isTagged=False인 사용자의 사진들만 필터링
            user_filter = models.Filter(
                must=[
                    models.FieldCondition(
                        key="user_id",
                        match=models.MatchValue(value=request.user.id),
                    ),
                    models.FieldCondition(
                        key="isTagged",
                        match=models.MatchValue(value=False),
                    ),
                ]
            )

            # 페이지네이션을 위한 offset 계산
            offset = (page - 1) * page_size

            # 요청된 페이지의 데이터만 가져오기
            points, next_offset = client.scroll(
                collection_name=IMAGE_COLLECTION_NAME,
                scroll_filter=user_filter,
                limit=page_size,
                offset=offset,
                with_payload=True,
            )

            # 태그되지 않은 사진이 없는 경우
            if len(points) == 0:
                return Response(
                    {
                        "recs": [],
                    },
                    status=status.HTTP_200_OK,
                )

            # ResPhotoSerializer 형태로 데이터 변환
            photos_data = []
            for point in points:
                photos_data.append(
                    {
                        "photo_id": point.id,
                        "photo_path_id": point.payload.get("photo_path_id"),
                    }
                )

            # ResStorySerializer에 맞는 형태로 응답 데이터 구성
            story_response = {
                "recs": photos_data,
            }

            serializer = ResStorySerializer(story_response)
            return Response(serializer.data, status=status.HTTP_200_OK)

        except Exception as e:
            return Response(
                {"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )
