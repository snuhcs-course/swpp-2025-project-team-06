from rest_framework import serializers

class SemanticSearchResponseSerializer(serializers.Serializer):
    photos = serializers.ListField(
        child=serializers.IntegerField(),
    )