from rest_framework import serializers

class SignUpResponse(serializers.Serializer):
    id = serializers.UUIDField()

class SignInResponse(serializers.Serializer):
    access_token = serializers.CharField()
    refresh_token = serializers.CharField()

class RefreshResponse(serializers.Serializer):
    access_token = serializers.CharField()

class RefreshErrorResponse(serializers.Serializer):
    detail = serializers.CharField()