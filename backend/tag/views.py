import uuid
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi
from sentence_transformers import SentenceTransformer

from .serializers import TagNameSerializer, TagVectorSerializer
from common.serializers import TagIdSerializer
from common.models import Photo_Tag, Tag
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework.permissions import IsAuthenticated

TEXT_MODEL_NAME = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
text_model = SentenceTransformer(TEXT_MODEL_NAME)

class TagNameView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Post a tag with tag name",
        operation_description="Post a new tag with tag name",
        request_body=TagNameSerializer(),
        responses={
            201: openapi.Response(
                description="Created",
                schema=TagIdSerializer()
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
        serializer = TagNameSerializer(data=request.data)
        
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        
        data = serializer.validated_data
    
        tag_id = uuid.uuid4()
        tag_name = data['tag']
        embedding = text_model.encode(tag_name)
        tag_id = uuid.uuid4()

        if embedding.size == 0:
            return Response({"error": f"Failed to process tag"}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
        
        embedding = embedding.tolist()

        tag, created = Tag.objects.get_or_create(id=tag_id, user=request.user, tag=tag_name, embedding=embedding)
        if created:
            tag.save()

        response_serializer = TagIdSerializer({"tag_id": tag.id})
        return Response(response_serializer.data, status=status.HTTP_201_CREATED)
           
class TagDetailView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]
    
    @swagger_auto_schema(
        operation_summary="Delete a tag with tag_id of the authenticated user",
        operation_description="Delete a tag associated with the authenticated user using tag_id",
        request_body=None,
        responses={
            204: openapi.Response(
                description="No Content"
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Tag not found"
            ),
            500: openapi.Response(
                description="Internal Server Error"
            ),
        },
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
        operation_summary="Rename a tag with tag_id of the authenticated user",
        operation_description="Rename a tag associated with the authenticated user using tag_id",
        request_body=TagNameSerializer(),
        responses={
            200: openapi.Response(
                description="Success",
                schema=TagIdSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Tag not found"
            ),
            500: openapi.Response(
                description="Internal Server Error"
            ),
        
        },
    )
    def put(self, request, tag_id, *args, **kwargs):
        try:
            serializer = TagNameSerializer(data=request.data)

            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            data = serializer.validated_data
            tag_name = data['tag']
            embedding = text_model.encode(tag_name)
            if embedding.size == 0:
                return Response({"error": "Failed to process tag"}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
            
            embedding = embedding.tolist()

            tag = Tag.objects.get(id=tag_id, user=request.user)
            tag.tag = tag_name
            tag.embedding = embedding
            tag.save()

            response_serializer = TagIdSerializer({"tag_id": tag.id})
            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response({"error": "Tag not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
            
    
    @swagger_auto_schema(
        operation_summary="Retrieve information about a tag with tag_id of the authenticated user",
        operation_description="Retrieve information about a tag associated with the authenticated user using tag_id",
        request_body=None,
        responses={
            200: openapi.Response(
                description="Success",
                schema=TagVectorSerializer()
            ),
            401: openapi.Response(
                description="Unauthorized - The refresh token is expired"
            ),
            404: openapi.Response(
                description="Not Found - Tag not found"
            ),
            500: openapi.Response(
                description="Internal Server Error"
            ),
        
        },
    )
    def get(self, request, tag_id, *args, **kwargs):
        try:
            tag = Tag.objects.get(id=tag_id, user=request.user)
            response_serializer = TagVectorSerializer({"tag": tag.tag, "embedding": tag.embedding})
            
            return Response(response_serializer.data, status=status.HTTP_200_OK)
        except Tag.DoesNotExist:
            return Response({"error": "Tag not found."}, status=status.HTTP_404_NOT_FOUND)
        except Exception as e:
            return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)
        