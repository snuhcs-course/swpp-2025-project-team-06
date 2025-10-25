import uuid

from django.urls import reverse
from django.contrib.auth.models import User
from rest_framework.test import APIClient, APITestCase
from rest_framework import status
from rest_framework_simplejwt.tokens import RefreshToken
from unittest.mock import patch
from gallery.models import Tag, Photo_Tag


class PhotoRecommendationViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test users
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpass123"
        )

        # Create test tags
        self.user_tag = Tag.objects.create(
            tag_id=uuid.uuid4(), user=self.user, tag="Test Tag"
        )
        self.other_user_tag = Tag.objects.create(
            tag_id=uuid.uuid4(), user=self.other_user, tag="Other Tag"
        )

        # Generate JWT token for authenticated user
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # URL for the view
        self.url = reverse(
            "gallery:photo_recommendation", kwargs={"tag_id": self.user_tag.tag_id}
        )

    @patch("gallery.views.recommend_photo_from_tag")
    def test_get_recommendations_success(self, mock_recommend):
        """Test successful retrieval of photo recommendations"""
        mock_photos = [
            {"photo_id": uuid.uuid4(), "photo_path_id": 1},
            {"photo_id": uuid.uuid4(), "photo_path_id": 2},
        ]

        mock_recommend.return_value = mock_photos

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data, mock_photos)

    def test_get_recommendations_unauthorized_no_token(self):
        """Test that request without authentication token fails"""
        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_get_recommendations_unauthorized_invalid_token(self):
        """Test that request with invalid token fails"""
        self.client.credentials(HTTP_AUTHORIZATION="Bearer invalid_token")
        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_get_recommendations_tag_not_found(self):
        """Test request with non-existent tag ID"""
        non_existent_tag_id = uuid.uuid4()
        url = f"/api/tags/{non_existent_tag_id}/recommendations/"

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_get_recommendations_tag_belongs_to_other_user(self):
        """Test that user cannot access another user's tags"""
        url = reverse(
            "gallery:photo_recommendation", kwargs={"tag_id": self.other_user_tag.tag_id}
        )

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    @patch("gallery.views.recommend_photo_from_tag")
    def test_get_recommendations_empty_results(self, mock_recommend):
        """Test when recommendation function returns empty list"""
        mock_recommend.return_value = []

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)

        self.assertEqual(response.data, [])

    def test_only_get_method_allowed(self):
        """Test that only GET method is allowed"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Test POST
        response = self.client.post(self.url, {})
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)

        # Test PUT
        response = self.client.put(self.url, {})
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)

        # Test DELETE
        response = self.client.delete(self.url)
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)


class TagViewTest(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpassword123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="password123"
        )

        # JWT 토큰 생성
        refresh_token = RefreshToken.for_user(self.user)
        self.access_token = str(refresh_token.access_token)

        # 테스트용 태그들 생성
        self.tag1 = Tag.objects.create(tag="여행", user=self.user)
        self.tag2 = Tag.objects.create(tag="음식", user=self.user)
        self.tag3 = Tag.objects.create(tag="다른유저태그", user=self.other_user)

        self.tag_list_url = reverse("gallery:tags")

    def test_get_all_tags_success(self):
        """사용자의 모든 태그 조회 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        response = self.client.get(self.tag_list_url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)  # 현재 사용자의 태그만 2개

        tag_names = [tag["tag"] for tag in response.data]
        self.assertIn("여행", tag_names)
        self.assertIn("음식", tag_names)
        self.assertNotIn("다른유저태그", tag_names)

        # TagSerializer는 tag_id와 tag 필드를 반환
        tag_ids = [tag["tag_id"] for tag in response.data]
        self.assertIn(str(self.tag1.tag_id), tag_ids)
        self.assertIn(str(self.tag2.tag_id), tag_ids)

    def test_get_all_tags_no_tags(self):
        """태그가 없는 사용자의 태그 조회 테스트"""
        # 새로운 사용자 생성 (태그 없음)
        new_user = User.objects.create_user(
            username="newuser", email="new@example.com", password="password123"
        )
        new_refresh_token = RefreshToken.for_user(new_user)
        new_access_token = str(new_refresh_token.access_token)

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {new_access_token}")

        response = self.client.get(self.tag_list_url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    def test_get_all_tags_unauthorized(self):
        """인증 없이 태그 조회 시도 테스트"""
        response = self.client.get(self.tag_list_url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_create_tag_success(self):
        """태그 생성 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        payload = {"tag": "새로운태그"}
        response = self.client.post(self.tag_list_url, payload)

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("tag_id", response.data)

        # 태그가 실제로 생성되었는지 확인
        self.assertTrue(Tag.objects.filter(tag="새로운태그", user=self.user).exists())

    def test_create_tag_duplicate(self):
        """중복 태그 생성 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        payload = {"tag": "여행"}  # 이미 존재하는 태그
        response = self.client.post(self.tag_list_url, payload)

        self.assertEqual(response.status_code, status.HTTP_409_CONFLICT)
        self.assertIn("already exists", response.data["detail"])

    def test_create_tag_invalid_data(self):
        """잘못된 데이터로 태그 생성 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        payload = {"tag": ""}  # 빈 태그명
        response = self.client.post(self.tag_list_url, payload)

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_tag_missing_field(self):
        """필수 필드 누락으로 태그 생성 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        payload = {}  # tag 필드 누락
        response = self.client.post(self.tag_list_url, payload)

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_tag_unauthorized(self):
        """인증 없이 태그 생성 시도 테스트"""
        payload = {"tag": "새태그"}
        response = self.client.post(self.tag_list_url, payload)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_create_tag_long_name(self):
        """긴 이름의 태그 생성 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # 50자 정확히 (모델의 max_length)
        long_tag_name = "a" * 50
        payload = {"tag": long_tag_name}
        response = self.client.post(self.tag_list_url, payload)

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

        # 50자 초과 (데이터베이스 레벨에서 처리됨)
        too_long_tag_name = "a" * 51
        payload = {"tag": too_long_tag_name}
        response = self.client.post(self.tag_list_url, payload)

        # 데이터베이스 또는 시리얼라이저에서 에러 처리됨
        self.assertIn(
            response.status_code,
            [status.HTTP_400_BAD_REQUEST, status.HTTP_500_INTERNAL_SERVER_ERROR],
        )


class TagDetailViewTest(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpassword123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="password123"
        )

        # JWT 토큰 생성
        refresh_token = RefreshToken.for_user(self.user)
        self.access_token = str(refresh_token.access_token)

        other_refresh_token = RefreshToken.for_user(self.other_user)
        self.other_access_token = str(other_refresh_token.access_token)

        # 테스트용 태그들 생성
        self.user_tag = Tag.objects.create(tag="내태그", user=self.user)
        self.other_user_tag = Tag.objects.create(
            tag="다른유저태그", user=self.other_user
        )

    def test_get_tag_info_success(self):
        """태그 정보 조회 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["tag"], "내태그")

    def test_get_tag_info_not_owner(self):
        """다른 사용자의 태그 정보 조회 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse(
            "gallery:tag_detail", kwargs={"tag_id": self.other_user_tag.tag_id}
        )
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertIn("Forbidden", response.data["error"])

    def test_get_tag_info_not_found(self):
        """존재하지 않는 태그 정보 조회 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        fake_tag_id = uuid.uuid4()
        url = reverse("gallery:tag_detail", kwargs={"tag_id": fake_tag_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_get_tag_info_unauthorized(self):
        """인증 없이 태그 정보 조회 시도 테스트"""
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_update_tag_success(self):
        """태그 이름 수정 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        payload = {"tag": "수정된태그"}
        response = self.client.put(url, payload)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["tag_id"], str(self.user_tag.tag_id))

        # 태그가 실제로 수정되었는지 확인
        updated_tag = Tag.objects.get(tag_id=self.user_tag.tag_id)
        self.assertEqual(updated_tag.tag, "수정된태그")

    def test_update_tag_not_owner(self):
        """다른 사용자의 태그 수정 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse(
            "gallery:tag_detail", kwargs={"tag_id": self.other_user_tag.tag_id}
        )
        payload = {"tag": "수정시도"}
        response = self.client.put(url, payload)

        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertIn("Forbidden", response.data["error"])

    def test_update_tag_not_found(self):
        """존재하지 않는 태그 수정 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        fake_tag_id = uuid.uuid4()
        url = reverse("gallery:tag_detail", kwargs={"tag_id": fake_tag_id})
        payload = {"tag": "수정시도"}
        response = self.client.put(url, payload)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_update_tag_invalid_data(self):
        """잘못된 데이터로 태그 수정 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        payload = {"tag": ""}  # 빈 태그명
        response = self.client.put(url, payload)

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_update_tag_unauthorized(self):
        """인증 없이 태그 수정 시도 테스트"""
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        payload = {"tag": "수정시도"}
        response = self.client.put(url, payload)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_delete_tag_success(self):
        """태그 삭제 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)

        # 태그가 실제로 삭제되었는지 확인
        self.assertFalse(Tag.objects.filter(tag_id=self.user_tag.tag_id).exists())

    def test_delete_tag_not_owner(self):
        """다른 사용자의 태그 삭제 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse(
            "gallery:tag_detail", kwargs={"tag_id": self.other_user_tag.tag_id}
        )
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertIn("Forbidden", response.data["error"])

        # 태그가 삭제되지 않았는지 확인
        self.assertTrue(Tag.objects.filter(tag_id=self.other_user_tag.tag_id).exists())

    def test_delete_tag_not_found(self):
        """존재하지 않는 태그 삭제 시도 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        fake_tag_id = uuid.uuid4()
        url = reverse("gallery:tag_detail", kwargs={"tag_id": fake_tag_id})
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_delete_tag_unauthorized(self):
        """인증 없이 태그 삭제 시도 테스트"""
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_delete_tag_with_photo_associations(self):
        """사진과 연결된 태그 삭제 테스트"""
        # Photo_Tag 관계 생성 (모의)
        photo_id = uuid.uuid4()
        Photo_Tag.objects.create(tag=self.user_tag, user=self.user, photo_id=photo_id)

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.user_tag.tag_id})
        response = self.client.delete(url)

        # 태그가 삭제되면 관련 Photo_Tag도 CASCADE로 삭제됨
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(Tag.objects.filter(tag_id=self.user_tag.tag_id).exists())
        self.assertFalse(Photo_Tag.objects.filter(tag=self.user_tag).exists())


class TagIntegrationTest(APITestCase):
    """태그 관련 통합 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpassword123"
        )

        refresh_token = RefreshToken.for_user(self.user)
        self.access_token = str(refresh_token.access_token)

        self.tag_list_url = reverse("gallery:tags")

    def test_tag_crud_flow(self):
        """태그 CRUD 전체 플로우 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # 1. 태그 생성
        create_payload = {"tag": "테스트태그"}
        create_response = self.client.post(self.tag_list_url, create_payload)
        self.assertEqual(create_response.status_code, status.HTTP_201_CREATED)

        tag_id = create_response.data["tag_id"]

        # 2. 태그 목록 조회 (생성된 태그 포함)
        list_response = self.client.get(self.tag_list_url)
        self.assertEqual(list_response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(list_response.data), 1)
        self.assertEqual(list_response.data[0]["tag"], "테스트태그")

        # 3. 태그 상세 조회
        detail_url = reverse("gallery:tag_detail", kwargs={"tag_id": tag_id})
        detail_response = self.client.get(detail_url)
        self.assertEqual(detail_response.status_code, status.HTTP_200_OK)
        self.assertEqual(detail_response.data["tag"], "테스트태그")

        # 4. 태그 수정
        update_payload = {"tag": "수정된태그"}
        update_response = self.client.put(detail_url, update_payload)
        self.assertEqual(update_response.status_code, status.HTTP_200_OK)

        # 5. 수정 확인
        updated_detail_response = self.client.get(detail_url)
        self.assertEqual(updated_detail_response.status_code, status.HTTP_200_OK)
        self.assertEqual(updated_detail_response.data["tag"], "수정된태그")

        # 6. 태그 삭제
        delete_response = self.client.delete(detail_url)
        self.assertEqual(delete_response.status_code, status.HTTP_204_NO_CONTENT)

        # 7. 삭제 확인
        deleted_detail_response = self.client.get(detail_url)
        self.assertEqual(deleted_detail_response.status_code, status.HTTP_404_NOT_FOUND)

        # 8. 태그 목록이 비어있는지 확인
        final_list_response = self.client.get(self.tag_list_url)
        self.assertEqual(final_list_response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(final_list_response.data), 0)

    def test_multiple_users_tag_isolation(self):
        """여러 사용자 간 태그 격리 테스트"""
        # 다른 사용자 생성
        other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="password123"
        )
        other_refresh_token = RefreshToken.for_user(other_user)
        other_access_token = str(other_refresh_token.access_token)

        # 첫 번째 사용자로 태그 생성
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        create_response = self.client.post(self.tag_list_url, {"tag": "유저1태그"})
        self.assertEqual(create_response.status_code, status.HTTP_201_CREATED)

        # 두 번째 사용자로 같은 이름의 태그 생성 (허용되어야 함)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {other_access_token}")
        other_create_response = self.client.post(
            self.tag_list_url, {"tag": "유저1태그"}
        )
        self.assertEqual(other_create_response.status_code, status.HTTP_201_CREATED)

        # 각 사용자는 자신의 태그만 볼 수 있어야 함
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        user1_tags = self.client.get(self.tag_list_url)
        self.assertEqual(len(user1_tags.data), 1)

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {other_access_token}")
        user2_tags = self.client.get(self.tag_list_url)
        self.assertEqual(len(user2_tags.data), 1)

        # 태그 ID는 달라야 함
        self.assertNotEqual(
            create_response.data["tag_id"], other_create_response.data["tag_id"]
        )
