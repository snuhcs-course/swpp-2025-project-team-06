import uuid
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.parsers import MultiPartParser
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
from qdrant_client import models

from .serializers import PhotoDetailSerializer, PhotoIdPathSerializer, TagSerializer, PhotoDetailSerializer, PhotoIdSerializer
from .models import Photo_Tag, Tag
from .vision_service import get_image_embedding
from .qdrant_utils import client, IMAGE_COLLECTION_NAME
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework.permissions import IsAuthenticated

class PhotosView(APIView):
    parser_classes = [MultiPartParser]
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Upload a photo",
        operation_description="Upload a new photo with metadata",
        request_body=PhotoDetailSerializer(),
        responses={
            201: openapi.Response(
                description="Created",
                schema=PhotoIdSerializer()
            ),
            400: openapi.Response(
                description="Bad Request - Request form mismatch"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ), 
            500: openapi.Response(
                description="Internal Server Error - Failed to process image"
            ),
        },
    )
    def post(self, request, *args, **kwargs):
        serializer = PhotoDetailSerializer(data=request.data)
        
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        
        data = serializer.validated_data
    
        photo_id = uuid.uuid4()
        user_id = request.user.id
        photo_path_id = data['photo_path_id']
        image_file = data['photo']
        embedding = get_image_embedding(image_file)
        created_at = data['created_at']
        location = {'lat': data['lat'], 'lng': data['lng']}
        
        if embedding is None:
            return Response({"error": f"Failed to process image"}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

        client.upsert(collection_name=IMAGE_COLLECTION_NAME, points=[models.PointStruct(id=str(photo_id), vector=embedding, payload={"user_id": user_id, "photo_path_id": photo_path_id, "created_at": created_at.isoformat(), "lat": location['lat'], "lng": location['lng']})], wait=True)

        response_serializer = PhotoIdSerializer({"photo_id": photo_id})
        return Response(response_serializer.data, status=status.HTTP_201_CREATED)
           
class PhotoBatchView(APIView):
    parser_classes = [MultiPartParser]
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Upload photos in batch",
        operation_description="Upload new photos with metadata",
        request_body=PhotoDetailSerializer(many=True),
        responses={
            201: openapi.Response(
                description="Created",
                schema=PhotoIdSerializer(many=True)
            ),
            400: openapi.Response(
                description="Bad Request - Request form mismatch"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ), 
            500: openapi.Response(
                description="Internal Server Error - Failed to process image"
            ),
        },
    )
    def post(self, request, *args, **kwargs):

        serializer = PhotoDetailSerializer(data=request.data, many=True)
        
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        
        photos_data = serializer.validated_data
        
        points_to_upsert = []
        created_photos = []

        for data in photos_data:
            photo_id = uuid.uuid4()
            user_id = request.user.id
            photo_path_id = data['photo_path_id']
            image_file = data['photo']
            embedding = get_image_embedding(image_file)
            created_at = data['created_at']
            # location = data['location']
            location = {'lat': data['lat'], 'lng': data['lng']}
            
            if embedding is None:
                return Response({"error": f"Failed to process image"}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
            
            points_to_upsert.append(
                models.PointStruct(id=str(photo_id), vector=embedding, payload={"user_id": user_id, "photo_path_id": photo_path_id, "created_at": created_at.isoformat(), "lat": location['lat'], "lng": location['lng']})
            )
            created_photos.append({"photo_id": photo_id})

        if points_to_upsert:
            client.upsert(collection_name=IMAGE_COLLECTION_NAME, points=points_to_upsert, wait=True)

        response_serializer = PhotoIdSerializer(created_photos, many=True)
        return Response(response_serializer.data, status=status.HTTP_201_CREATED)
    
    
    @swagger_auto_schema(
        operation_summary="Delete all photos of the authenticated user",
        operation_description="Delete all photos associated with the authenticated user",
        request_body=None,
        responses={
            204: openapi.Response(
                description="No Content"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Photo not found"
            ),
            500: openapi.Response(
                description="Internal Server Error"
            ),
        },
    )
    def delete(self, request, *args, **kwargs):
        try:
            photo_tags = Photo_Tag.objects.filter(user=request.user)
            photo_ids_to_delete = [str(pt.photo_id) for pt in photo_tags]

            client.delete(
                collection_name=IMAGE_COLLECTION_NAME,
                points_selector=photo_ids_to_delete,
                wait=True
            )
            
            photo_tags.delete()
            return Response(status=status.HTTP_204_NO_CONTENT)
        except Photo_Tag.DoesNotExist:
            return Response({"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)


class PhotoDetailView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Delete a photo with photo_id of the authenticated user",
        operation_description="Delete a photo associated with the authenticated user using photo_id",
        request_body=None,
        responses={
            204: openapi.Response(
                description="No Content"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Photo not found"
            ),
            500: openapi.Response(
                description="Internal Server Error"
            ),
        },
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
        except Photo_Tag.DoesNotExist:
            return Response({"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
    
    
    @swagger_auto_schema(
        operation_summary="Retrieve a photo with photo_id of the authenticated user",
        operation_description="Retrieve a photo associated with the authenticated user using photo_id",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=PhotoDetailSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Photo not found"
            ),
            500: openapi.Response(
                description="Internal Server Error"
            ),
        
        },
    )
    def get(self, request, photo_id, *args, **kwargs):
        try:
            retrieved_points = client.retrieve(
                collection_name=IMAGE_COLLECTION_NAME,
                ids=[str(photo_id)],
                with_payload=True
            )
            
            
            photo_path_id = retrieved_points[0].payload.get("photo_path_id")
            tags_qs = Photo_Tag.objects.filter(photo_id=photo_id)
            tags = [str(tag.tag_id) for tag in tags_qs]
            photo_detail = {
                "photo_path_id": photo_path_id,
                "tags": tags
            }
            
            if not retrieved_points:
                return Response({"error": "Photo not found in vector database."}, status=status.HTTP_404_NOT_FOUND)
            
            serializer = PhotoDetailSerializer(photo_detail)
            return Response(serializer.data, status=status.HTTP_200_OK)
        
        except Photo_Tag.DoesNotExist:
            return Response({"error": "Photo not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

class TaggedPhotoListView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Retrieve all photos with tag_id of the authenticated user",
        operation_description="Retrieve all photos associated with the authenticated user using tag_id",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=PhotoIdPathSerializer(many=True)
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Photo not found"
            ),
            500: openapi.Response(
                description="Internal Server Error - Failed to process image"
            ),
        },
    )
    def get(self, request, *args, **kwargs):
        tag_id = request.query_params.get('tag_id')
        try:
            photo_tags = Photo_Tag.objects.filter(tag_id=tag_id)

            serializer = TagSerializer(photo_tags, many=True)
        
            data = serializer.validated_data
            photo_details = []
            for item in data:
                photo_id = item['photo_id']
                retrieved_points = client.retrieve(
                    collection_name=IMAGE_COLLECTION_NAME,
                    ids=[str(photo_id)],
                    with_payload=True
                )
                
                if not retrieved_points:
                    continue
                
                photo_path_id = retrieved_points[0].payload.get("photo_path_id")
                
                photo_details.append({
                    "photo_id": photo_id,
                    "photo_path": photo_path_id
                })

            return Response(photo_details, status=status.HTTP_200_OK)

        except Tag.DoesNotExist:
            return Response({"error": "Tag not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)