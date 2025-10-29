from rest_framework import serializers
from .serializers import TagSerializer


class ResPhotoSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path_id = serializers.IntegerField(help_text="사진 파일의 경로 ID")


class ResPhotoTagListSerializer(serializers.Serializer):
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    tags = TagSerializer(many=True)


class ResPhotoIdSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")


class ResTagIdSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")


class ResTagVectorSerializer(serializers.Serializer):
    tag = serializers.CharField(help_text="태그 이름")

class ResStorySerializer(serializers.Serializer):
    recs = ResPhotoSerializer(many=True)

