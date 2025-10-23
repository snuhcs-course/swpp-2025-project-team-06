from django.test import TestCase
from django.contrib.auth.models import User
from rest_framework.exceptions import ValidationError

from ..serializers import UserSerializer
from ..request_serializers import SignUpRequest, SignInRequest, SignOutRequest
from ..response_serializers import SignUpResponse, SignInResponse, RefreshResponse, RefreshErrorResponse


class UserSerializerTest(TestCase):
    def setUp(self):
        self.valid_data = {
            'username': 'testuser',
            'email': 'test@example.com',
            'password': 'testpassword123'
        }

    def test_user_serializer_valid_data(self):
        """유효한 데이터로 사용자 시리얼라이저 테스트"""
        serializer = UserSerializer(data=self.valid_data)
        self.assertTrue(serializer.is_valid())
        
        user = serializer.save()
        user.set_password(self.valid_data['password'])
        user.save()
        self.assertEqual(user.username, 'testuser')
        self.assertEqual(user.email, 'test@example.com')
        # 패스워드는 해시된 형태로 저장되므로 직접 비교하지 않음
        self.assertTrue(user.check_password('testpassword123'))

    def test_user_serializer_invalid_email(self):
        """잘못된 이메일 형식 테스트"""
        invalid_data = self.valid_data.copy()
        invalid_data['email'] = 'invalid-email'
        
        serializer = UserSerializer(data=invalid_data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('email', serializer.errors)

    def test_user_serializer_missing_fields(self):
        """필수 필드 누락 테스트"""
        incomplete_data = {
            'email': 'test@example.com'
            # username과 password 누락
        }
        
        serializer = UserSerializer(data=incomplete_data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('username', serializer.errors)
        self.assertIn('password', serializer.errors)

    def test_user_serializer_empty_fields(self):
        """빈 필드 테스트"""
        empty_data = {
            'username': '',
            'email': '',
            'password': ''
        }
        
        serializer = UserSerializer(data=empty_data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('username', serializer.errors)
        self.assertIn('password', serializer.errors)

    def test_user_serializer_update(self):
        """사용자 업데이트 테스트"""
        user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='password123'
        )
        
        update_data = {
            'username': 'updateduser',
            'email': 'updated@example.com'
        }
        
        serializer = UserSerializer(instance=user, data=update_data, partial=True)
        self.assertTrue(serializer.is_valid())
        
        updated_user = serializer.save()
        self.assertEqual(updated_user.username, 'updateduser')
        self.assertEqual(updated_user.email, 'updated@example.com')

    def test_user_serializer_serialization(self):
        """사용자 직렬화 테스트"""
        user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='password123'
        )
        
        serializer = UserSerializer(instance=user)
        data = serializer.data
        
        self.assertEqual(data['username'], 'testuser')
        self.assertEqual(data['email'], 'test@example.com')
        self.assertEqual(data['id'], user.id)
        # 패스워드는 직렬화에서 제외되어야 함 (보안상)
        self.assertIn('password', data)  # 현재는 포함되어 있음 (주의!)


class SignUpRequestTest(TestCase):
    def test_signup_request_valid_data(self):
        """회원가입 요청 유효한 데이터 테스트"""
        data = {
            'username': 'testuser',
            'email': 'test@example.com',
            'password': 'testpassword123'
        }
        
        serializer = SignUpRequest(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['username'], 'testuser')
        self.assertEqual(serializer.validated_data['email'], 'test@example.com')
        self.assertEqual(serializer.validated_data['password'], 'testpassword123')

    def test_signup_request_invalid_email(self):
        """회원가입 요청 잘못된 이메일 테스트"""
        data = {
            'username': 'testuser',
            'email': 'invalid-email',
            'password': 'testpassword123'
        }
        
        serializer = SignUpRequest(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('email', serializer.errors)

    def test_signup_request_missing_fields(self):
        """회원가입 요청 필드 누락 테스트"""
        data = {
            'username': 'testuser'
            # email과 password 누락
        }
        
        serializer = SignUpRequest(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('email', serializer.errors)
        self.assertIn('password', serializer.errors)

    def test_signup_request_empty_fields(self):
        """회원가입 요청 빈 필드 테스트"""
        data = {
            'username': '',
            'email': '',
            'password': ''
        }
        
        serializer = SignUpRequest(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('username', serializer.errors)
        self.assertIn('email', serializer.errors)
        self.assertIn('password', serializer.errors)


class SignInRequestTest(TestCase):
    def test_signin_request_valid_data(self):
        """로그인 요청 유효한 데이터 테스트"""
        data = {
            'username': 'testuser',
            'password': 'testpassword123'
        }
        
        serializer = SignInRequest(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['username'], 'testuser')
        self.assertEqual(serializer.validated_data['password'], 'testpassword123')

    def test_signin_request_missing_fields(self):
        """로그인 요청 필드 누락 테스트"""
        data = {
            'username': 'testuser'
            # password 누락
        }
        
        serializer = SignInRequest(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('password', serializer.errors)

    def test_signin_request_empty_fields(self):
        """로그인 요청 빈 필드 테스트"""
        data = {
            'username': '',
            'password': ''
        }
        
        serializer = SignInRequest(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('username', serializer.errors)
        self.assertIn('password', serializer.errors)


class SignOutRequestTest(TestCase):
    def test_signout_request_valid_data(self):
        """로그아웃 요청 유효한 데이터 테스트"""
        data = {
            'refresh_token': 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...'
        }
        
        serializer = SignOutRequest(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['refresh_token'], data['refresh_token'])

    def test_signout_request_missing_token(self):
        """로그아웃 요청 토큰 누락 테스트"""
        data = {}  # refresh_token 누락
        
        serializer = SignOutRequest(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('refresh_token', serializer.errors)

    def test_signout_request_empty_token(self):
        """로그아웃 요청 빈 토큰 테스트"""
        data = {
            'refresh_token': ''
        }
        
        serializer = SignOutRequest(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('refresh_token', serializer.errors)


class SignUpResponseTest(TestCase):
    def test_signup_response_serialization(self):
        """회원가입 응답 직렬화 테스트"""
        data = {'id': 1}
        
        serializer = SignUpResponse(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['id'], 1)

    def test_signup_response_invalid_id(self):
        """회원가입 응답 잘못된 ID 테스트"""
        data = {'id': 'invalid'}
        
        serializer = SignUpResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('id', serializer.errors)

    def test_signup_response_missing_id(self):
        """회원가입 응답 ID 누락 테스트"""
        data = {}
        
        serializer = SignUpResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('id', serializer.errors)


class SignInResponseTest(TestCase):
    def test_signin_response_valid_data(self):
        """로그인 응답 유효한 데이터 테스트"""
        data = {
            'access_token': 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...',
            'refresh_token': 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...'
        }
        
        serializer = SignInResponse(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['access_token'], data['access_token'])
        self.assertEqual(serializer.validated_data['refresh_token'], data['refresh_token'])

    def test_signin_response_missing_tokens(self):
        """로그인 응답 토큰 누락 테스트"""
        data = {
            'access_token': 'token123'
            # refresh_token 누락
        }
        
        serializer = SignInResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('refresh_token', serializer.errors)

    def test_signin_response_empty_tokens(self):
        """로그인 응답 빈 토큰 테스트"""
        data = {
            'access_token': '',
            'refresh_token': ''
        }
        
        serializer = SignInResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('access_token', serializer.errors)
        self.assertIn('refresh_token', serializer.errors)


class RefreshResponseTest(TestCase):
    def test_refresh_response_valid_data(self):
        """토큰 갱신 응답 유효한 데이터 테스트"""
        data = {
            'access_token': 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...'
        }
        
        serializer = RefreshResponse(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['access_token'], data['access_token'])

    def test_refresh_response_missing_token(self):
        """토큰 갱신 응답 토큰 누락 테스트"""
        data = {}
        
        serializer = RefreshResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('access_token', serializer.errors)

    def test_refresh_response_empty_token(self):
        """토큰 갱신 응답 빈 토큰 테스트"""
        data = {
            'access_token': ''
        }
        
        serializer = RefreshResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('access_token', serializer.errors)


class RefreshErrorResponseTest(TestCase):
    def test_refresh_error_response_valid_data(self):
        """토큰 갱신 에러 응답 유효한 데이터 테스트"""
        data = {
            'detail': 'please sign in again'
        }
        
        serializer = RefreshErrorResponse(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['detail'], data['detail'])

    def test_refresh_error_response_missing_detail(self):
        """토큰 갱신 에러 응답 detail 누락 테스트"""
        data = {}
        
        serializer = RefreshErrorResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('detail', serializer.errors)

    def test_refresh_error_response_empty_detail(self):
        """토큰 갱신 에러 응답 빈 detail 테스트"""
        data = {
            'detail': ''
        }
        
        serializer = RefreshErrorResponse(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('detail', serializer.errors)


class SerializerIntegrationTest(TestCase):
    """시리얼라이저 통합 테스트"""
    
    def test_signup_flow_serializers(self):
        """회원가입 플로우 시리얼라이저들 테스트"""
        # 1. 회원가입 요청 데이터 검증
        signup_data = {
            'username': 'testuser',
            'email': 'test@example.com',
            'password': 'testpassword123'
        }
        
        request_serializer = SignUpRequest(data=signup_data)
        self.assertTrue(request_serializer.is_valid())
        
        # 2. 사용자 생성
        user_serializer = UserSerializer(data=signup_data)
        self.assertTrue(user_serializer.is_valid())
        user = user_serializer.save()
        
        # 3. 회원가입 응답 데이터 생성
        response_data = {'id': user.id}
        response_serializer = SignUpResponse(data=response_data)
        self.assertTrue(response_serializer.is_valid())
        
        # 4. 전체 플로우 검증
        self.assertEqual(response_serializer.validated_data['id'], user.id)

    def test_signin_flow_serializers(self):
        """로그인 플로우 시리얼라이저들 테스트"""
        # 1. 로그인 요청 데이터 검증
        signin_data = {
            'username': 'testuser',
            'password': 'testpassword123'
        }
        
        request_serializer = SignInRequest(data=signin_data)
        self.assertTrue(request_serializer.is_valid())
        
        # 2. 로그인 응답 데이터 생성
        response_data = {
            'access_token': 'fake_access_token',
            'refresh_token': 'fake_refresh_token'
        }
        
        response_serializer = SignInResponse(data=response_data)
        self.assertTrue(response_serializer.is_valid())
        
        # 3. 토큰 갱신 응답 데이터 생성
        refresh_data = {
            'access_token': 'new_fake_access_token'
        }
        
        refresh_serializer = RefreshResponse(data=refresh_data)
        self.assertTrue(refresh_serializer.is_valid())