from rest_framework import serializers


class ReqPhotoDetailSerializer(serializers.Serializer):
    photo = serializers.ImageField(help_text="업로드할 이미지 파일")
    filename = serializers.CharField(help_text="이미지 파일의 이름")
    photo_path_id = serializers.IntegerField(help_text="이미지 파일의 경로 ID")
    created_at = serializers.DateTimeField(help_text="이미지 파일이 생성된 시간")
    lat = serializers.FloatField(help_text="위도")
    lng = serializers.FloatField(help_text="경도")


class ReqPhotoIdSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")


class ReqTagNameSerializer(serializers.Serializer):
    tag = serializers.CharField(help_text="태그 이름")


class ReqTagIdSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")


class ReqPhotoListSerializer(serializers.Serializer):
    photos = serializers.ListField(
        child=serializers.UUIDField(),
        help_text="추천을 받기 위한 사진들의 고유 ID 리스트",
    )


class ReqPhotoBulkDeleteSerializer(serializers.Serializer):
    photos = ReqPhotoIdSerializer(many=True, help_text="삭제할 사진 ID 목록")
