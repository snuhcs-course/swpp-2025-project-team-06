from rest_framework import serializers

class SignUpRequest(serializers.Serializer):
    username = serializers.CharField()
    email = serializers.EmailField()
    password = serializers.CharField()

class SignInRequest(serializers.Serializer):
    username = serializers.CharField()
    password = serializers.CharField()

class SignOutRequest(serializers.Serializer):
    refresh_token = serializers.CharField()