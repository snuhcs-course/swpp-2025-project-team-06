from rest_framework import serializers

class TagIdSerializer(serializers.Serializer):
    tag_id = serializers.UUIDField(help_text="태그의 고유 ID")