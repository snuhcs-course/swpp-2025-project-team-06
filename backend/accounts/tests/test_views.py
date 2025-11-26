from django.urls import reverse
from django.contrib.auth.models import User
from rest_framework.test import APITestCase
from rest_framework import status
from rest_framework_simplejwt.tokens import RefreshToken


class SignUpViewTest(APITestCase):
    def setUp(self):
        self.signup_url = reverse('accounts:signup')
        self.valid_payload = {
            'username': 'testuser',
            'password': 'testpassword123'
        }

    def test_signup_success(self):
        """회원가입 성공 테스트"""
        response = self.client.post(self.signup_url, self.valid_payload)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn('id', response.data)
        # 사용자가 실제로 생성되었는지 확인
        self.assertTrue(User.objects.filter(username='testuser').exists())

    def test_signup_invalid_data(self):
        """잘못된 데이터로 회원가입 시도"""
        invalid_payload = {
            'username': '',  # 빈 username
            'password': '123'  # 너무 짧은 패스워드
        }
        response = self.client.post(self.signup_url, invalid_payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_signup_missing_fields(self):
        """필수 필드 누락 시 테스트"""
        incomplete_payload = {
            'username': 'testuser'
            # password 누락
        }
        response = self.client.post(self.signup_url, incomplete_payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_signup_duplicate_username(self):
        """중복된 username으로 회원가입 시도"""
        # 먼저 사용자 생성
        User.objects.create_user(
            username='testuser',
            password='password123'
        )

        response = self.client.post(self.signup_url, self.valid_payload)
        self.assertEqual(response.status_code, status.HTTP_409_CONFLICT)


class SignInViewTest(APITestCase):
    def setUp(self):
        self.signin_url = reverse('accounts:signin')
        self.user = User.objects.create_user(
            username='testuser',
            password='testpassword123'
        )

    def test_signin_success(self):
        """로그인 성공 테스트"""
        payload = {
            'username': 'testuser',
            'password': 'testpassword123'
        }
        response = self.client.post(self.signin_url, payload)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('access_token', response.data)
        self.assertIn('refresh_token', response.data)

    def test_signin_invalid_credentials(self):
        """잘못된 비밀번호로 로그인 시도"""
        payload = {
            'username': 'testuser',
            'password': 'wrongpassword'
        }
        response = self.client.post(self.signin_url, payload)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_signin_nonexistent_user(self):
        """존재하지 않는 사용자로 로그인 시도"""
        payload = {
            'username': 'nonexistentuser',
            'password': 'somepassword'
        }
        response = self.client.post(self.signin_url, payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_signin_invalid_data(self):
        """잘못된 데이터 형식으로 로그인 시도"""
        payload = {
            'username': '',  # 빈 username
            'password': ''   # 빈 password
        }
        response = self.client.post(self.signin_url, payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)


class SignOutViewTest(APITestCase):
    def setUp(self):
        self.signout_url = reverse('accounts:signout')
        self.user = User.objects.create_user(
            username='testuser',
            password='testpassword123'
        )
        self.refresh_token = RefreshToken.for_user(self.user)
        self.access_token = str(self.refresh_token.access_token)

    def test_signout_success(self):
        """로그아웃 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.access_token}')
        payload = {
            'refresh_token': str(self.refresh_token)
        }
        response = self.client.post(self.signout_url, payload)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_signout_invalid_token(self):
        """잘못된 refresh token으로 로그아웃 시도"""
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.access_token}')
        payload = {
            'refresh_token': 'invalid_token'
        }
        response = self.client.post(self.signout_url, payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_signout_wrong_user_token(self):
        """다른 사용자의 토큰으로 로그아웃 시도"""
        other_user = User.objects.create_user(
            username='otheruser',
            password='password123'
        )
        other_refresh_token = RefreshToken.for_user(other_user)
        
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.access_token}')
        payload = {
            'refresh_token': str(other_refresh_token)
        }
        response = self.client.post(self.signout_url, payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_signout_without_authentication(self):
        """인증 없이 로그아웃 시도"""
        payload = {
            'refresh_token': str(self.refresh_token)
        }
        response = self.client.post(self.signout_url, payload)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_signout_missing_refresh_token(self):
        """refresh_token 누락하고 로그아웃 시도"""
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.access_token}')
        payload = {}  # refresh_token 누락
        response = self.client.post(self.signout_url, payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)


class TokenRefreshViewTest(APITestCase):
    def setUp(self):
        self.refresh_url = reverse('accounts:token_refresh')
        self.user = User.objects.create_user(
            username='testuser',
            password='testpassword123'
        )
        self.refresh_token = RefreshToken.for_user(self.user)

    def test_token_refresh_success(self):
        """토큰 갱신 성공 테스트"""
        payload = {
            'refresh_token': str(self.refresh_token)
        }
        response = self.client.post(self.refresh_url, payload)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('access_token', response.data)

    def test_token_refresh_invalid_token(self):
        """잘못된 refresh token으로 갱신 시도"""
        payload = {
            'refresh_token': 'invalid_token'
        }
        response = self.client.post(self.refresh_url, payload)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        self.assertIn('detail', response.data)
        self.assertEqual(response.data['detail'], 'please sign in again')

    def test_token_refresh_missing_token(self):
        """refresh_token 누락하고 갱신 시도"""
        payload = {}
        response = self.client.post(self.refresh_url, payload)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_token_refresh_blacklisted_token(self):
        """블랙리스트된 토큰으로 갱신 시도"""
        # 토큰을 블랙리스트에 추가
        self.refresh_token.blacklist()
        
        payload = {
            'refresh_token': str(self.refresh_token)
        }
        response = self.client.post(self.refresh_url, payload)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class AuthenticationIntegrationTest(APITestCase):
    """인증 관련 통합 테스트"""
    
    def test_complete_auth_flow(self):
        """회원가입 -> 로그인 -> 토큰갱신 -> 로그아웃 전체 플로우 테스트"""
        # 1. 회원가입
        signup_payload = {
            'username': 'testuser',
            'password': 'testpassword123'
        }
        signup_response = self.client.post(reverse('accounts:signup'), signup_payload)
        self.assertEqual(signup_response.status_code, status.HTTP_201_CREATED)
        
        # 2. 로그인
        signin_payload = {
            'username': 'testuser',
            'password': 'testpassword123'
        }
        signin_response = self.client.post(reverse('accounts:signin'), signin_payload)
        self.assertEqual(signin_response.status_code, status.HTTP_200_OK)
        
        access_token = signin_response.data['access_token']
        refresh_token = signin_response.data['refresh_token']
        
        # 3. 토큰 갱신
        refresh_payload = {
            'refresh_token': refresh_token
        }
        refresh_response = self.client.post(reverse('accounts:token_refresh'), refresh_payload)
        self.assertEqual(refresh_response.status_code, status.HTTP_200_OK)
        
        # 4. 로그아웃
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {access_token}')
        signout_payload = {
            'refresh_token': refresh_token
        }
        signout_response = self.client.post(reverse('accounts:signout'), signout_payload)
        self.assertEqual(signout_response.status_code, status.HTTP_200_OK)