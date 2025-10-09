from rest_framework import serializers
from .serializers import TagSerializer

class PhotoSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path_id = serializers.IntegerField(help_text="사진 파일의 경로 ID")
    
class PhotoTagListSerializer(serializers.Serializer):
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    tags = TagSerializer(many=True)
    
class PhotoIdSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    
class TagSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")
    tag = serializers.CharField(help_text="태그 이름")
    
class TagIdSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")
    
class TagVectorSerializer(serializers.Serializer):
    tag = serializers.CharField(help_text="태그 이름")
    embedding = serializers.ListField(child=serializers.FloatField(), help_text="태그의 벡터 임베딩")
