from common.models import Photo_Tag
from rest_framework import serializers

class TagNameSerializer(serializers.Serializer):
    tag = serializers.CharField(help_text="태그 이름")
    
class TagVectorSerializer(serializers.Serializer):
    tag = serializers.CharField(help_text="태그 이름")
    embedding = serializers.ListField(child=serializers.FloatField(), help_text="태그의 벡터 임베딩")
    
class TagSerializer(serializers.ModelSerializer):
    class Meta:
        model = Photo_Tag
        fields = ['photo_id', 'tag_id']
        