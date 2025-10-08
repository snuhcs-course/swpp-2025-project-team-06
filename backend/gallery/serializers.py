from .models import Photo_Tag
from rest_framework import serializers



class PhotoDetailSerializer(serializers.Serializer):
    photo = serializers.ImageField(help_text="업로드할 이미지 파일")
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    created_at = serializers.DateTimeField(help_text="이미지 파일이 생성된 시간")
    # location = LocationSerializer(help_text="이미지 파일의 위치 (위도, 경도)")
    lat = serializers.FloatField(help_text="위도")
    lng = serializers.FloatField(help_text="경도")
    
class TagSerializer(serializers.ModelSerializer):
    class Meta:
        model = Photo_Tag
        fields = ['photo_id', 'tag_id']
        
class PhotoIdSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    
class PhotoIdPathSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path = serializers.ImageField(help_text="사진 파일의 경로")
    
class PhotoTagSerializer(serializers.Serializer):
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    tags = serializers.ListField(child=serializers.CharField(), help_text="사진에 태그된 태그들의 리스트")