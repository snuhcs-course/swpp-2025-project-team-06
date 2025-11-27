from rest_framework import serializers
from .serializers import TagSerializer


class ResPhotoSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path_id = serializers.IntegerField(help_text="사진 파일의 경로 ID")
    created_at = serializers.DateTimeField(help_text="사진 생성 시간", read_only=True)


class ResPhotoTagListSerializer(serializers.Serializer):
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    address = serializers.CharField(help_text="사진의 위치 정보")
    tags = TagSerializer(many=True)


class ResPhotoIdSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")


class ResTagIdSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")


class ResTagVectorSerializer(serializers.Serializer):
    tag = serializers.CharField(help_text="태그 이름")


class ResStorySerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path_id = serializers.IntegerField(help_text="사진 파일의 경로 ID")
    tags = serializers.ListField(child=serializers.CharField(max_length="255"))


class ResStoryStateSerializer(serializers.Serializer):
    status = serializers.ChoiceField(
        choices=["PROCESSING", "SUCCESS"], help_text="스토리 생성 상태"
    )
    stories = ResStorySerializer(
        many=True, help_text="생성된 스토리 목록 (status가 SUCCESS일 때만 포함)"
    )


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
    photo_count = serializers.IntegerField(
        read_only=True, help_text="태그에 포함된 사진 수"
    )
