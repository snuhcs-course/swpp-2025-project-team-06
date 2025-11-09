from rest_framework import serializers
from .serializers import TagSerializer


class ResPhotoSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path_id = serializers.IntegerField(help_text="사진 파일의 경로 ID")
    created_at = serializers.DateTimeField(help_text="사진 생성 시간", read_only=True)


class ResPhotoTagListSerializer(serializers.Serializer):
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    tags = TagSerializer(many=True)


class ResPhotoIdSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")


class ResTagIdSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")


class ResTagVectorSerializer(serializers.Serializer):
    tag = serializers.CharField(help_text="태그 이름")


class NewResStorySerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path_id = serializers.IntegerField(help_text="사진 파일의 경로 ID")
    tags = TagSerializer(many=True)

class ResStorySerializer(serializers.Serializer):
    recs = ResPhotoSerializer(many=True)


class ResTagAlbumSerializer(serializers.Serializer):
    photos = ResPhotoSerializer(many=True)


class ResTagThumbnailSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")
    tag = serializers.CharField(help_text="태그 이름")
    thumbnail_path_id = serializers.IntegerField(
        allow_null=True, help_text="태그 썸네일 경로 ID"
    )
    created_at = serializers.DateTimeField(read_only=True, help_text="태그 생성 시각")
    updated_at = serializers.DateTimeField(read_only=True, help_text="태그 수정 시각")
    photo_count = serializers.IntegerField(read_only=True, help_text="태그에 포함된 사진 수")
