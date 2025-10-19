import uuid
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.parsers import MultiPartParser, FormParser
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
from qdrant_client import models

from .reponse_serializers import ResPhotoSerializer, ResPhotoTagListSerializer, ResPhotoIdSerializer, ResTagIdSerializer, ResTagVectorSerializer
from .request_serializers import ReqPhotoDetailSerializer, ReqPhotoIdSerializer, ReqTagNameSerializer, ReqTagIdSerializer
from .serializers import TagSerializer
from .models import Photo_Tag, Tag
from .qdrant_utils import client, IMAGE_COLLECTION_NAME
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework.permissions import IsAuthenticated

from django.core.files.storage import FileSystemStorage
from django.conf import settings
from .tasks import process_and_embed_photo, create_or_update_tag_embedding

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
                description="Created",
                schema=ResPhotoIdSerializer(many=True)
            ),
            400: openapi.Response(
                description="Bad Request - Request form mismatch"
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
                type=openapi.TYPE_STRING),
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
    def post(self, request, *args, **kwargs):
        try:
            import json
            
            photos = request.FILES.getlist('photo')
            metadata_json = request.POST.get('metadata')
            
            if not metadata_json:
                return Response({"error": "metadata field is required"}, status=status.HTTP_400_BAD_REQUEST)
            
            try:
                metadata_list = json.loads(metadata_json)
            except json.JSONDecodeError:
                return Response({"error": "Invalid JSON format in metadata field"}, status=status.HTTP_400_BAD_REQUEST)
            
            if len(photos) != len(metadata_list):
                return Response({"error": "Number of photos and metadata entries must match"}, status=status.HTTP_400_BAD_REQUEST)
            
            photos_data = []
            for i, photo in enumerate(photos):
                if i >= len(metadata_list):
                    return Response({"error": "Insufficient metadata for all photos"}, status=status.HTTP_400_BAD_REQUEST)
                
                metadata = metadata_list[i]
                photos_data.append({
                    'photo': photo,
                    'filename': metadata.get('filename'),
                    'photo_path_id': metadata.get('photo_path_id'),
                    'created_at': metadata.get('created_at'),
                    'lat': metadata.get('lat'),
                    'lng': metadata.get('lng')
                })

            serializer = ReqPhotoDetailSerializer(data=photos_data, many=True)
            
            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
            
            photos_data = serializer.validated_data
            
            fs = FileSystemStorage(location=settings.MEDIA_ROOT)

            for data in photos_data:
                image_file = data['photo']
                
                temp_filename = f"{uuid.uuid4()}_{image_file.name}"
                saved_path = fs.save(temp_filename, image_file)
                full_path = fs.path(saved_path)

                process_and_embed_photo.delay(
                    image_path=full_path,
                    user_id=request.user.id,
                    filename=data['filename'],
                    photo_path_id=data['photo_path_id'],
                    created_at=data['created_at'].isoformat(),
                    lat=data['lat'],
                    lng=data['lng']
                )

            return Response({"message": "Photos are being processed."}, status=status.HTTP_202_ACCEPTED)
        except Exception as e:
                return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
    
    @swagger_auto_schema(
        operation_summary="Photo All View",
        operation_description="Get all the photos the user has uploaded",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResPhotoSerializer(many=True)
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),     
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def get(self, request, *args, **kwargs):
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
                    with_payload=True
                )
                
                all_user_points.extend(points)
                
                if next_offset is None:
                    break
            
            photos = []
            for point in all_user_points:
                photos.append({
                    "photo_id": point.id,
                    "photo_path_id": point.payload.get("photo_path_id")
                })

            return Response(photos, status=status.HTTP_200_OK)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
    
    
        
class PhotoDetailView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="Photo Detail View",
        operation_description="Get detailed information of tags of a single photo",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResPhotoTagListSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No photo with photo_id as its id"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def get(self, request, photo_id, *args, **kwargs):
        try:
            user_filter = models.Filter(
                must=[
                    models.FieldCondition(
                        key="user_id",
                        match=models.MatchValue(value=request.user.id),
                    ), 
                    models.FieldCondition(
                        key="photo_id",
                        match=models.MatchValue(value=str(photo_id)),
                    )
                ]
            )

            all_photo_points = []
            next_offset = None

            while True:
                points, next_offset = client.scroll(
                    collection_name=IMAGE_COLLECTION_NAME,
                    scroll_filter=user_filter,
                    limit=200,
                    offset=next_offset, 
                    with_payload=True
                )
                
                all_photo_points.extend(points)
                
                if next_offset is None:
                    break
                
            if all_photo_points.size == 0:
                return Response({"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND)
            
            photos = []
            
            for point in all_photo_points:
                photo_tags = Photo_Tag.objects.filter(photo_id=point.id)
                for pt in photo_tags:
                    tag = Tag.objects.get(id=pt.tag_id)
                    photos.append({
                        "photo_path_id": point.payload.get("photo_path_id"),
                        "tags": [{"tag_id": tag.id, "tag": tag.tag}]
                    })
                    
            return Response(photos, status=status.HTTP_200_OK)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
        
    @swagger_auto_schema(
        operation_summary="Delete a Photo",
        operation_description="Delete a photo from the app",
        request_body=None,
        responses={
            204: openapi.Response(
                description="No Content"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def delete(self, request, photo_id, *args, **kwargs):
        try:
            photo_tag = Photo_Tag.objects.get(id=photo_id, user=request.user)
            photo_tag.delete()

            client.delete(
                collection_name=IMAGE_COLLECTION_NAME,
                points_selector=[str(photo_id)],
                wait=True
            )
            
            return Response(status=status.HTTP_204_NO_CONTENT)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
        

class BulkDeletePhotoView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Delete Photos",
        operation_description="Delete photos from the app",
        request_body=ReqPhotoIdSerializer(many=True),
        responses={
            204: openapi.Response(
                description="No Content"
            ),
            400: openapi.Response(
                description="Bad Request - Request form mismatch"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def delete(self, request, *args, **kwargs):
        try:
            serializer = ReqPhotoDetailSerializer(data=request.data, many=True)
            
            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
            
            photos_data = serializer.validated_data
            
            photos = []
            
            for data in photos_data:
                photos.append(data['photo_id'])
                
            client.delete(
                collection_name=IMAGE_COLLECTION_NAME,  
                points_selector=[str(photo_id) for photo_id in photos],
                wait=True
            )
            
            return Response(status=status.HTTP_204_NO_CONTENT)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
    

class GetPhotosByTagView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Get Photos List for a Tag Album",
        operation_description="Get a list of photo_path_ids in a tag album",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResPhotoSerializer(many=True)
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Photo not found"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def get(self, request, tag_id, *args, **kwargs):
        try:
            photo_tags = Photo_Tag.objects.filter(tag_id=tag_id)
            
            photo_ids = [str(pt.photo_id) for pt in photo_tags]
            
            retrieved_points = client.retrieve(
                collection_name=IMAGE_COLLECTION_NAME,
                ids=photo_ids,
                with_payload=True
            )
            
            photos = []
            
            for point in retrieved_points:
                photos.append({
                    "photo_id": point.id,
                    "photo_path_id": point.payload.get("photo_path_id")
                })
            
            return Response(photos, status=status.HTTP_200_OK)
        except Photo_Tag.DoesNotExist:
            return Response({"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
        

class PostPhotoTagsView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Add Tags to a Photo",
        operation_description="Create new Tag-Photo relationships",
        request_body=ReqTagIdSerializer(many=True),
        responses={
            201: openapi.Response(
                description="Success"
            ),
            400: openapi.Response(
                description="Bad Request - Request form mismatch"   
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No such tag or photo"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def post(self, request, photo_id, *args, **kwargs):
        try:
            serializer = ReqTagIdSerializer(data=request.data, many=True)
            
            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            tag_ids = [data['tag_id'] for data in serializer.validated_data]
            
            if not client.exists(collection_name=IMAGE_COLLECTION_NAME, point_id=str(photo_id)):
                return Response({"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND)

            created_photo_tags = []
            
            for tag_id in tag_ids:
                pt_id = uuid.uuid4()
                created_photo_tags.append(Photo_Tag(id=pt_id, photo_id=photo_id, tag_id=tag_id, user=request.user))
            
            Photo_Tag.objects.bulk_create(created_photo_tags)

            return Response(status=status.HTTP_201_OK)
        except Tag.DoesNotExist:
            return Response({"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
        
class DeletePhotoTagsView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Delete a Tag from a Photo",
        operation_description="Delete a Tag-Photo relationship",
        request_body=None,
        responses={
            204: openapi.Response(
                description="No Content"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No such tag or photo"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def delete(self, request, photo_id, tag_id, *args, **kwargs):
        try:
            Tag.objects.get(id=tag_id, user=request.user)
            
            if not client.exists(collection_name=IMAGE_COLLECTION_NAME, point_id=str(photo_id)):
                return Response({"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND)

            photo_tag = Photo_Tag.objects.get(photo_id=photo_id, tag_id=tag_id, user=request.user)

            if photo_tag.exists():
                photo_tag.delete()
            
            return Response(status=status.HTTP_204_NO_CONTENT)
        except Tag.DoesNotExist:
            return Response({"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND)
        except Photo_Tag.DoesNotExist:
            return Response({"error": "No such tag or photo"}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
      

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
                schema=TagSerializer(many=True)
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No tag with tag_id as its id"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def get(self, request, *args, **kwargs):
        try:
            tag = Tag.objects.filter(user=request.user)
            tags = []
            for t in tag:
                tags.append({"tag_id": t.id, "tag": t.tag})
                   
            response_serializer = TagSerializer(tags, many=True)
            
            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response({"error": "No tag with tag_id as its id"}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
    
    
    @swagger_auto_schema(
        operation_summary="Create a Tag",
        operation_description="Createa new tag",
        request_body=ReqTagNameSerializer(),
        responses={
            201: openapi.Response(
                description="Created",
                schema=ResTagIdSerializer()
            ),
            400: openapi.Response(
                description="Bad Request - Request form mismatch"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ), 
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def post(self, request, *args, **kwargs):
        try:
            serializer = ReqTagNameSerializer(data=request.data)
            
            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
            
            data = serializer.validated_data
        
            tag_id = uuid.uuid4()
            tag_name = data['tag']
            
            create_or_update_tag_embedding.delay(
                user_id=request.user.id,
                tag_name=tag_name,
                tag_id=tag_id
            )

            response_serializer = ResTagIdSerializer({"tag_id": tag_id})
            
            return Response(response_serializer.data, status=status.HTTP_201_CREATED)
        except Exception as e:
                return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)


class TagDetailView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Delete a Tag",
        operation_description="Delete a tag",
        request_body=None,
        responses={
            204: openapi.Response(
                description="No Content"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No tag such that tag's id is tag_id"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def delete(self, request, tag_id, *args, **kwargs):
        try:
            tag = Tag.objects.get(id=tag_id, user=request.user)
            tag.delete()

            return Response(status=status.HTTP_204_NO_CONTENT)
        except Tag.DoesNotExist:
            return Response({"error": "Tag not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
    
    
    @swagger_auto_schema(
        operation_summary="Rename a Tag",
        operation_description="Change the name of a tag",
        request_body=ReqTagNameSerializer(),
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResTagIdSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Tag not found"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def put(self, request, tag_id, *args, **kwargs):
        try:
            serializer = ReqTagNameSerializer(data=request.data)

            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            data = serializer.validated_data
            tag_name = data['tag']
            create_or_update_tag_embedding.delay(
                user_id=request.user.id,
                tag_name=tag_name,
                tag_id=tag_id
            )

            response_serializer = ResTagIdSerializer({"tag_id": tag_id})
            
            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response({"error": "Tag not found"}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
            
    
    @swagger_auto_schema(
        operation_summary="Get Tag Info",
        operation_description="Get information about a tag",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=ResTagVectorSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - No tag with tag_id as its id"
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def get(self, request, tag_id, *args, **kwargs):
        try:
            tag = Tag.objects.get(id=tag_id, user=request.user)
            response_serializer = ResTagVectorSerializer({"tag": tag.tag, "embedding": tag.embedding})
            
            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response({"error": "No tag with tag_id as its id"}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
        