from .serializers import UserSerializer
from .request_serializers import SignUpRequest,SignInRequest, SignOutRequest
from .response_serializers import SignUpResponse, SignInResponse, RefreshResponse, RefreshErrorResponse
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from django.contrib.auth.models import User
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework.permissions import IsAuthenticated
from rest_framework_simplejwt.authentication import JWTAuthentication
from drf_yasg.utils import swagger_auto_schema
from drf_yasg import openapi




class SignUpView(APIView):
    @swagger_auto_schema(
        operation_summary="User signup",
        operation_description="Register a new user with username and password",
        request_body=SignUpRequest,
        responses={
            201: openapi.Response(
                description="Created",
                schema=SignUpResponse
            ),
            400: openapi.Response(
                description="Bad Request - Invalid input data"
            )
        },
    )
    def post(self, request):
        serializer = UserSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        user = serializer.save()
        user.set_password(request.data['password'])
        user.save()
        response_serializer = SignUpResponse({'id': user.id})
        return Response(response_serializer.data, status=status.HTTP_201_CREATED)
    

        
class SignInView(APIView):
    @swagger_auto_schema(
        operation_summary="User signin",
        operation_description="Log in a user with username and password, returning access and refresh tokens.",
        request_body=SignInRequest,
        responses={
            200: openapi.Response(
                description="OK - Signin successful",
                schema=SignInResponse
            ),
            400: openapi.Response(
                description="Bad Request - Invalid input data or user does not exist"
            ),
            401: openapi.Response(
                description="Unauthorized - Incorrect password"
            )
        },
    )
    def post(self, request):
        serializer = SignInRequest(data=request.data)
        if not serializer.is_valid():
            return Response(status=status.HTTP_400_BAD_REQUEST)
        
        username = request.data['username']
        password = request.data['password']
        try:
            user = User.objects.get(username=username)
            if user.check_password(password):
                token = RefreshToken.for_user(user)
                res = {
                    'access_token': str(token.access_token),
                    'refresh_token': str(token)
                }
                return Response(SignInResponse(res).data, status=status.HTTP_200_OK)
            else:
                return Response(status=status.HTTP_401_UNAUTHORIZED)
        except User.DoesNotExist:
            return Response(status=status.HTTP_400_BAD_REQUEST)



class SignOutView(APIView):
    authentication_classes = [JWTAuthentication]
    permission_classes = [IsAuthenticated]

    @swagger_auto_schema(
        operation_summary="User signout",
        operation_description="Logs out the user by blacklisting their refresh token.",
        request_body=SignOutRequest,
        responses={
            200: openapi.Response(
                description="OK - Signout successful"
            ),
            400: openapi.Response(
                description="Bad Request - Invalid refresh token"
            ),
            401: openapi.Response(
                description="Unauthorized - Authentication credentials were not provided or are invalid."
            ),
        },
        manual_parameters=[openapi.Parameter("Authorization", openapi.IN_HEADER, description="access token", type=openapi.TYPE_STRING)]
    )
    def post(self, request):
        serializer = SignOutRequest(data=request.data)
        if not serializer.is_valid():
            return Response(status=status.HTTP_400_BAD_REQUEST)
        try:
            refresh_token = request.data['refresh_token']
            token = RefreshToken(refresh_token)
            if int(token['user_id']) != request.user.id:
                return Response(status=status.HTTP_400_BAD_REQUEST)
            token.blacklist()
            return Response(status=status.HTTP_200_OK)
        except Exception:
            return Response(status=status.HTTP_400_BAD_REQUEST)
            


class TokenRefreshView(APIView):
    
    @swagger_auto_schema(
        operation_summary="Token refresh",
        operation_description="Refresh the access token using a valid refresh token.",
        request_body=SignOutRequest,    
        responses={
            200: openapi.Response(
                description="OK - Token refreshed successfully",
                schema=RefreshResponse
            ),
            400: openapi.Response(
                description="Bad Request - Invalid refresh token"
            ),
            401: openapi.Response(
                schema=RefreshErrorResponse,
                description="Unauthorized - Refresh token is expired or wrong",
                examples={
                    "application/json": {
                        "detail": "please sign in again"
                    }
                }
            ),
        },
    )
    def post(self, request):
        serializer = SignOutRequest(data=request.data)
        if not serializer.is_valid():
            return Response(status=status.HTTP_400_BAD_REQUEST)
    
        refresh_token = request.data['refresh_token']

        try:
            token = RefreshToken(refresh_token)
        except Exception:
            return Response({"detail": "please sign in again"}, status=status.HTTP_401_UNAUTHORIZED)
        
        # Generate new access token
        new_access_token = str(token.access_token)
        
        res = {
        'access_token': new_access_token
        }
        return Response(RefreshResponse(res).data, status=status.HTTP_200_OK)
            
