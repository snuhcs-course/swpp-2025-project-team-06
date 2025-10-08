from rest_framework import serializers

class PhotoDetailSerializer(serializers.Serializer):
    photo = serializers.ImageField(help_text="업로드할 이미지 파일")
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    created_at = serializers.DateTimeField(help_text="이미지 파일이 생성된 시간")
    lat = serializers.FloatField(help_text="위도")
    lng = serializers.FloatField(help_text="경도")
        
class PhotoIdSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    
class PhotoIdPathSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path = serializers.ImageField(help_text="사진 파일의 경로")
    
class TagSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")
    tag = serializers.CharField(help_text="태그 이름")
    
class PhotoTagListSerializer(serializers.Serializer):
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    tags = TagSerializer(many=True)
    
class PhotoTagSerializer(serializers.Serializer):
    pt_id = serializers.UUIDField(help_text="Photo_Tag의 고유 ID")
    