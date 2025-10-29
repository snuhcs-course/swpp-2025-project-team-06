from rest_framework import serializers


class PhotoResponseSerializer(serializers.Serializer):
    photo_id = serializers.UUIDField(help_text="사진의 고유 ID")
    photo_path_id = serializers.IntegerField(help_text="사진 파일의 경로 ID")
