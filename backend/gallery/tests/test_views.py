import uuid
import json

from django.urls import reverse
from django.contrib.auth.models import User
from rest_framework.test import APIClient, APITestCase
from rest_framework import status
from rest_framework_simplejwt.tokens import RefreshToken
from unittest.mock import patch, MagicMock
from gallery.models import Tag, Photo_Tag, Photo

from PIL import Image
from io import BytesIO
from django.core.files.uploadedfile import SimpleUploadedFile


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


class PhotoViewTest(APITestCase):
    """PhotoView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        self.photos_url = reverse("gallery:photos")

    def _create_test_image(self, name="test_image.jpg"):
        """테스트용 이미지 파일 생성"""
        image = Image.new('RGB', (100, 100), color='red')
        img_io = BytesIO()
        image.save(img_io, format='JPEG')
        img_io.seek(0)
        
        return SimpleUploadedFile(
            name=name,
            content=img_io.read(),
            content_type='image/jpeg'
        )

    @patch("gallery.views.process_and_embed_photo")
    def test_post_photos_success(self, mock_process):
        """사진 업로드 성공 테스트"""
        test_image = self._create_test_image()
        metadata = json.dumps([{
            "filename": "test.jpg",
            "photo_path_id": 123,
            "created_at": "2023-01-01T00:00:00Z",
            "lat": 37.5665,
            "lng": 126.9780
        }])
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(
            self.photos_url,
            {
                "photo": [test_image],
                "metadata": metadata
            },
            format="multipart"
        )
        
        self.assertEqual(response.status_code, status.HTTP_202_ACCEPTED)
        self.assertIn("Photos are being processed", response.data["message"])
        mock_process.delay.assert_called_once()

    def test_post_photos_missing_metadata(self):
        """메타데이터 누락 시 실패 테스트"""
        test_image = self._create_test_image()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(
            self.photos_url,
            {"photo": [test_image]},
            format="multipart"
        )
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("metadata field is required", response.data["error"])

    def test_post_photos_invalid_json_metadata(self):
        """잘못된 JSON 메타데이터 테스트"""
        test_image = self._create_test_image()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(
            self.photos_url,
            {
                "photo": [test_image],
                "metadata": "invalid json"
            },
            format="multipart"
        )
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("Invalid JSON format", response.data["error"])

    def test_post_photos_mismatch_count(self):
        """사진과 메타데이터 개수 불일치 테스트"""
        test_image = self._create_test_image()
        metadata = json.dumps([
            {"filename": "test1.jpg", "photo_path_id": 123, "created_at": "2023-01-01T00:00:00Z", "lat": 37.5665, "lng": 126.9780},
            {"filename": "test2.jpg", "photo_path_id": 456, "created_at": "2023-01-01T00:00:00Z", "lat": 37.5665, "lng": 126.9780}
        ])
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(
            self.photos_url,
            {
                "photo": [test_image],  # 1개
                "metadata": metadata    # 2개
            },
            format="multipart"
        )
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("Number of photos and metadata entries must match", response.data["error"])

    def test_get_photos_success(self):
        """사진 목록 조회 성공 테스트"""
        # 테스트 사진 생성
        photo1 = Photo.objects.create(
            user=self.user,
            photo_path_id=123,
            filename="test1.jpg",
            lat=37.5665,
            lng=126.9780
        )
        photo2 = Photo.objects.create(
            user=self.user,
            photo_path_id=456,
            filename="test2.jpg"
        )
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.photos_url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)
        
        # 사진 데이터 구조 확인
        photo_data = response.data[0]
        self.assertIn("photo_id", photo_data)
        self.assertIn("photo_path_id", photo_data)

    def test_get_photos_unauthorized(self):
        """인증되지 않은 사용자의 사진 조회 테스트"""
        response = self.client.get(self.photos_url)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class PhotoDetailViewTest(APITestCase):
    """PhotoDetailView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        # 테스트 사진 생성
        self.photo_id = uuid.uuid4()
        self.photo = Photo.objects.create(
            photo_id=self.photo_id,
            user=self.user,
            photo_path_id=123,
            filename="test.jpg"
        )

    def test_get_photo_detail_success_with_tags(self):
        """태그가 있는 사진 상세 조회 성공 테스트"""
        # 태그 생성 및 연결
        tag1 = Tag.objects.create(tag="tag1", user=self.user)
        tag2 = Tag.objects.create(tag="tag2", user=self.user)
        
        Photo_Tag.objects.create(photo=self.photo, tag=tag1, user=self.user)
        Photo_Tag.objects.create(photo=self.photo, tag=tag2, user=self.user)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["photo_path_id"], 123)
        self.assertEqual(len(response.data["tags"]), 2)
        
        # 태그 데이터 구조 확인
        tag_data = response.data["tags"][0]
        self.assertIn("tag_id", tag_data)
        self.assertIn("tag", tag_data)

    def test_get_photo_detail_success_no_tags(self):
        """태그가 없는 사진 상세 조회 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["photo_path_id"], 123)
        self.assertEqual(len(response.data["tags"]), 0)

    def test_get_photo_detail_not_found(self):
        """존재하지 않는 사진 조회 테스트"""
        non_existent_id = uuid.uuid4()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": non_existent_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("Photo not found", response.data["error"])

    @patch("gallery.views.client")
    def test_delete_photo_success(self, mock_client):
        """사진 삭제 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # Qdrant 삭제가 호출되었는지 확인
        mock_client.delete.assert_called_once_with(
            collection_name="my_image_collection",
            points_selector=[str(self.photo_id)],
            wait=True,
        )
        
        # 데이터베이스에서 삭제되었는지 확인
        with self.assertRaises(Photo.DoesNotExist):
            Photo.objects.get(photo_id=self.photo_id)

    def test_get_photo_detail_unauthorized(self):
        """인증되지 않은 사용자의 사진 상세 조회 테스트"""
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class BulkDeletePhotoViewTest(APITestCase):
    """BulkDeletePhotoView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        # 테스트 사진들 생성
        self.photo_ids = []
        for i in range(3):
            photo_id = uuid.uuid4()
            Photo.objects.create(
                photo_id=photo_id,
                user=self.user,
                photo_path_id=i,
                filename=f"test{i}.jpg"
            )
            self.photo_ids.append(photo_id)

    @patch("gallery.views.client")
    def test_bulk_delete_photos_success(self, mock_client):
        """사진 일괄 삭제 성공 테스트"""
        payload = {
            "photos": [{"photo_id": str(pid)} for pid in self.photo_ids[:2]]
        }
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_bulk_delete")
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # Qdrant 삭제가 호출되었는지 확인
        mock_client.delete.assert_called_once()
        
        # 데이터베이스에서 삭제되었는지 확인
        remaining_photos = Photo.objects.filter(user=self.user).count()
        self.assertEqual(remaining_photos, 1)

    def test_bulk_delete_photos_invalid_serializer(self):
        """잘못된 데이터로 일괄 삭제 테스트"""
        payload = {
            "photos": [{"invalid_field": "invalid_value"}]
        }
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_bulk_delete")
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_bulk_delete_photos_unauthorized(self):
        """인증되지 않은 사용자의 일괄 삭제 테스트"""
        payload = {
            "photos": [{"photo_id": str(pid)} for pid in self.photo_ids]
        }
        
        url = reverse("gallery:photos_bulk_delete")
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class GetPhotosByTagViewTest(APITestCase):
    """GetPhotosByTagView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        # 테스트 데이터 생성
        self.tag = Tag.objects.create(tag="test_tag", user=self.user)
        self.photos = []
        
        # 사진들과 사진-태그 관계 생성
        for i in range(2):
            photo = Photo.objects.create(
                user=self.user,
                photo_path_id=i,
                filename=f"test{i}.jpg"
            )
            self.photos.append(photo)
            Photo_Tag.objects.create(photo=photo, tag=self.tag, user=self.user)

    def test_get_photos_by_tag_success(self):
        """태그별 사진 조회 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_by_tag", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["photos"]), 2)
        
        # 사진 데이터 구조 확인
        for photo_data in response.data["photos"]:
            self.assertIn("photo_id", photo_data)
            self.assertIn("photo_path_id", photo_data)

    def test_get_photos_by_tag_no_photos(self):
        """태그에 사진이 없는 경우 테스트"""
        empty_tag = Tag.objects.create(tag="empty_tag", user=self.user)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_by_tag", kwargs={"tag_id": empty_tag.tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["photos"]), 0)

    def test_get_photos_by_tag_not_found(self):
        """존재하지 않는 태그로 조회 테스트"""
        non_existent_tag_id = uuid.uuid4()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_by_tag", kwargs={"tag_id": non_existent_tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("Tag not found", response.data["error"])

    def test_get_photos_by_tag_unauthorized(self):
        """인증되지 않은 사용자의 태그별 사진 조회 테스트"""
        url = reverse("gallery:photos_by_tag", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class PostPhotoTagsViewTest(APITestCase):
    """PostPhotoTagsView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        # 테스트 데이터 생성
        self.photo_id = uuid.uuid4()
        self.photo = Photo.objects.create(
            photo_id=self.photo_id,
            user=self.user,
            photo_path_id=123,
            filename="test.jpg"
        )
        self.tag1 = Tag.objects.create(tag="tag1", user=self.user)
        self.tag2 = Tag.objects.create(tag="tag2", user=self.user)

    def test_post_photo_tags_success(self):
        """사진에 태그 추가 성공 테스트"""
        payload = [
            {"tag_id": str(self.tag1.tag_id)},
            {"tag_id": str(self.tag2.tag_id)}
        ]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        
        # 사진-태그 관계가 생성되었는지 확인
        self.assertTrue(Photo_Tag.objects.filter(photo=self.photo, tag=self.tag1).exists())
        self.assertTrue(Photo_Tag.objects.filter(photo=self.photo, tag=self.tag2).exists())
        
        # 사진이 태그됨으로 표시되었는지 확인
        self.photo.refresh_from_db()
        self.assertTrue(self.photo.is_tagged)

    def test_post_photo_tags_photo_not_found(self):
        """존재하지 않는 사진에 태그 추가 테스트"""
        non_existent_photo_id = uuid.uuid4()
        
        payload = [{"tag_id": str(self.tag1.tag_id)}]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": non_existent_photo_id})
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No such photo", response.data["error"])

    def test_post_photo_tags_tag_not_found(self):
        """존재하지 않는 태그로 추가 테스트"""
        non_existent_tag_id = uuid.uuid4()
        
        payload = [{"tag_id": str(non_existent_tag_id)}]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No such tag", response.data["error"])

    def test_post_photo_tags_invalid_serializer(self):
        """잘못된 데이터로 태그 추가 테스트"""
        payload = [{"invalid_field": "invalid_value"}]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_photo_tags_duplicate_relationship(self):
        """이미 존재하는 사진-태그 관계 추가 테스트"""
        # 기존 관계 생성
        Photo_Tag.objects.create(photo=self.photo, tag=self.tag1, user=self.user)
        
        payload = [{"tag_id": str(self.tag1.tag_id)}]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        
        # 중복 관계가 생성되지 않았는지 확인
        relationship_count = Photo_Tag.objects.filter(photo=self.photo, tag=self.tag1).count()
        self.assertEqual(relationship_count, 1)

    def test_post_photo_tags_unauthorized(self):
        """인증되지 않은 사용자의 태그 추가 테스트"""
        payload = [{"tag_id": str(self.tag1.tag_id)}]
        
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class DeletePhotoTagsViewTest(APITestCase):
    """DeletePhotoTagsView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        # 테스트 데이터 생성
        self.photo_id = uuid.uuid4()
        self.photo = Photo.objects.create(
            photo_id=self.photo_id,
            user=self.user,
            photo_path_id=123,
            filename="test.jpg",
            is_tagged=True
        )
        self.tag = Tag.objects.create(tag="test_tag", user=self.user)
        # 사진-태그 관계 생성
        self.photo_tag = Photo_Tag.objects.create(photo=self.photo, tag=self.tag, user=self.user)

    def test_delete_photo_tag_success_with_remaining_tags(self):
        """다른 태그가 남아있는 상태에서 사진-태그 관계 삭제 성공 테스트"""
        # 다른 태그 추가
        another_tag = Tag.objects.create(tag="another_tag", user=self.user)
        Photo_Tag.objects.create(photo=self.photo, tag=another_tag, user=self.user)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag", 
            kwargs={"photo_id": self.photo_id, "tag_id": self.tag.tag_id}
        )
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # 관계가 삭제되었는지 확인
        with self.assertRaises(Photo_Tag.DoesNotExist):
            Photo_Tag.objects.get(photo=self.photo, tag=self.tag, user=self.user)
        
        # 사진이 여전히 태그됨으로 표시되어 있는지 확인 (다른 태그가 남아있으므로)
        self.photo.refresh_from_db()
        self.assertTrue(self.photo.is_tagged)

    def test_delete_photo_tag_success_no_remaining_tags(self):
        """마지막 태그 삭제 시 is_tagged 업데이트 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag", 
            kwargs={"photo_id": self.photo_id, "tag_id": self.tag.tag_id}
        )
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # 관계가 삭제되었는지 확인
        with self.assertRaises(Photo_Tag.DoesNotExist):
            Photo_Tag.objects.get(photo=self.photo, tag=self.tag, user=self.user)
        
        # 사진이 태그되지 않음으로 표시되었는지 확인
        self.photo.refresh_from_db()
        self.assertFalse(self.photo.is_tagged)

    def test_delete_photo_tag_photo_not_found(self):
        """존재하지 않는 사진의 태그 삭제 테스트"""
        non_existent_photo_id = uuid.uuid4()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag", 
            kwargs={"photo_id": non_existent_photo_id, "tag_id": self.tag.tag_id}
        )
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No such tag or photo", response.data["error"])

    def test_delete_photo_tag_tag_not_found(self):
        """존재하지 않는 태그 삭제 테스트"""
        non_existent_tag_id = uuid.uuid4()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag", 
            kwargs={"photo_id": self.photo_id, "tag_id": non_existent_tag_id}
        )
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No such tag or photo", response.data["error"])

    def test_delete_photo_tag_relationship_not_found(self):
        """사진-태그 관계가 존재하지 않는 경우 테스트"""
        # 관계없는 태그 생성
        unrelated_tag = Tag.objects.create(tag="unrelated_tag", user=self.user)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag", 
            kwargs={"photo_id": self.photo_id, "tag_id": unrelated_tag.tag_id}
        )
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No such tag or photo", response.data["error"])

    def test_delete_photo_tag_unauthorized(self):
        """인증되지 않은 사용자의 태그 삭제 테스트"""
        url = reverse(
            "gallery:delete_photo_tag", 
            kwargs={"photo_id": self.photo_id, "tag_id": self.tag.tag_id}
        )
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class TagViewTest(APITestCase):
    """TagView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        self.tags_url = reverse("gallery:tags")

    def test_get_tags_success(self):
        """태그 목록 조회 성공 테스트"""
        # 테스트 태그들 생성
        tag1 = Tag.objects.create(tag="tag1", user=self.user)
        tag2 = Tag.objects.create(tag="tag2", user=self.user)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.tags_url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)
        
        # 태그 데이터 구조 확인
        tag_data = response.data[0]
        self.assertIn("tag_id", tag_data)
        self.assertIn("tag", tag_data)

    def test_get_tags_empty(self):
        """태그가 없는 경우 조회 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.tags_url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    def test_post_tag_success(self):
        """태그 생성 성공 테스트"""
        payload = {"tag": "new_tag"}
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(self.tags_url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("tag_id", response.data)
        
        # 데이터베이스에 생성되었는지 확인
        self.assertTrue(Tag.objects.filter(tag="new_tag", user=self.user).exists())

    def test_post_tag_duplicate(self):
        """중복 태그 생성 테스트"""
        # 기존 태그 생성
        Tag.objects.create(tag="existing_tag", user=self.user)
        
        payload = {"tag": "existing_tag"}
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(self.tags_url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_409_CONFLICT)
        self.assertIn("already exists", response.data["detail"])

    def test_post_tag_too_long(self):
        """태그명이 너무 긴 경우 테스트"""
        long_tag = "a" * 51  # 50자 초과
        payload = {"tag": long_tag}
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(self.tags_url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("cannot exceed 50 characters", response.data["error"])

    def test_post_tag_invalid_serializer(self):
        """잘못된 데이터로 태그 생성 테스트"""
        payload = {"invalid_field": "value"}
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.post(self.tags_url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_get_tags_unauthorized(self):
        """인증되지 않은 사용자의 태그 조회 테스트"""
        response = self.client.get(self.tags_url)
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_post_tag_unauthorized(self):
        """인증되지 않은 사용자의 태그 생성 테스트"""
        payload = {"tag": "new_tag"}
        
        response = self.client.post(self.tags_url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class TagDetailViewTest(APITestCase):
    """TagDetailView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자들 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
        # 테스트 태그 생성
        self.tag = Tag.objects.create(tag="test_tag", user=self.user)
        self.other_user_tag = Tag.objects.create(tag="other_tag", user=self.other_user)

    def test_get_tag_detail_success(self):
        """태그 상세 조회 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["tag"], "test_tag")

    def test_get_tag_detail_not_found(self):
        """존재하지 않는 태그 조회 테스트"""
        non_existent_tag_id = uuid.uuid4()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": non_existent_tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No tag with tag_id", response.data["error"])

    def test_get_tag_detail_forbidden(self):
        """다른 사용자의 태그 조회 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.other_user_tag.tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_put_tag_success(self):
        """태그 이름 변경 성공 테스트"""
        payload = {"tag": "updated_tag"}
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.put(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("tag_id", response.data)
        
        # 데이터베이스에서 업데이트되었는지 확인
        self.tag.refresh_from_db()
        self.assertEqual(self.tag.tag, "updated_tag")

    def test_put_tag_not_found(self):
        """존재하지 않는 태그 수정 테스트"""
        non_existent_tag_id = uuid.uuid4()
        payload = {"tag": "updated_tag"}
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": non_existent_tag_id})
        response = self.client.put(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_put_tag_forbidden(self):
        """다른 사용자의 태그 수정 테스트"""
        payload = {"tag": "updated_tag"}
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.other_user_tag.tag_id})
        response = self.client.put(url, payload, format="json")
        
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_delete_tag_success(self):
        """태그 삭제 성공 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # 데이터베이스에서 삭제되었는지 확인
        with self.assertRaises(Tag.DoesNotExist):
            Tag.objects.get(tag_id=self.tag.tag_id)

    def test_delete_tag_not_found(self):
        """존재하지 않는 태그 삭제 테스트"""
        non_existent_tag_id = uuid.uuid4()
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": non_existent_tag_id})
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_delete_tag_forbidden(self):
        """다른 사용자의 태그 삭제 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.other_user_tag.tag_id})
        response = self.client.delete(url)
        
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_tag_detail_unauthorized(self):
        """인증되지 않은 사용자의 태그 상세 조회 테스트"""
        url = reverse("gallery:tag_detail", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class StoryViewTest(APITestCase):
    """StoryView 테스트"""

    def setUp(self):
        """테스트용 데이터 설정"""
        self.client = APIClient()
        
        # 테스트 사용자 생성
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        
        # JWT 토큰 생성
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

    def test_get_stories_success(self):
        """스토리 조회 성공 테스트"""
        # 태그되지 않은 사진들 생성
        photos = []
        for i in range(5):
            photo = Photo.objects.create(
                user=self.user,
                photo_path_id=i,
                filename=f"story{i}.jpg",
                is_tagged=False
            )
            photos.append(photo)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("recs", response.data)
        self.assertEqual(len(response.data["recs"]), 5)
        
        # 각 추천 사진의 구조 확인
        for rec in response.data["recs"]:
            self.assertIn("photo_id", rec)
            self.assertIn("photo_path_id", rec)

    def test_get_stories_empty_results(self):
        """태그되지 않은 사진이 없는 경우 테스트"""
        # 태그된 사진만 생성
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=1,
            filename="tagged.jpg",
            is_tagged=True
        )
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("recs", response.data)
        self.assertEqual(len(response.data["recs"]), 0)

    def test_get_stories_with_size_parameter(self):
        """size 파라미터를 사용한 스토리 조회 테스트"""
        # 10개의 태그되지 않은 사진 생성
        for i in range(10):
            Photo.objects.create(
                user=self.user,
                photo_path_id=i,
                filename=f"story{i}.jpg",
                is_tagged=False
            )
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url, {"size": 3})
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["recs"]), 3)

    def test_get_stories_invalid_size_parameter(self):
        """잘못된 size 파라미터 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url, {"size": "invalid"})
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("Invalid size parameter", response.data["error"])

    def test_get_stories_negative_size_parameter(self):
        """음수 size 파라미터 테스트"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url, {"size": -1})
        
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("Size parameter must be positive", response.data["error"])

    def test_get_stories_large_size_parameter(self):
        """큰 size 파라미터 테스트 (200으로 제한)"""
        # 5개의 사진만 생성
        for i in range(5):
            Photo.objects.create(
                user=self.user,
                photo_path_id=i,
                filename=f"story{i}.jpg",
                is_tagged=False
            )
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url, {"size": 300})  # 200을 초과하는 값
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        # 실제 사진 개수만큼만 반환
        self.assertEqual(len(response.data["recs"]), 5)

    def test_get_stories_unauthorized(self):
        """인증되지 않은 사용자의 스토리 조회 테스트"""
        url = reverse("gallery:stories")
        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


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

    def test_get_recommendations_tag_belongs_to_other_user(self):
        """Test that user cannot access another user's tags"""
        url = reverse(
            "gallery:photo_recommendation",
            kwargs={"tag_id": self.other_user_tag.tag_id},
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


class GetRecommendTagViewTest(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", password="password123"
        )
        self.user_id = self.user.id
        self.tag = Tag.objects.create(user=self.user, tag_id=1, tag="test_tag")
        self.photo_id = uuid.uuid4()
        self.url = reverse(
            "gallery:tag_recommendation", kwargs={"photo_id": self.photo_id}
        )

    @patch("gallery.views.tag_recommendation")
    @patch("gallery.views.client")
    def test_get_recommend_tag_success(
        self, mock_qdrant_client, mock_tag_recommendation
    ):
        mock_qdrant_client.retrieve.return_value = ["some_point_data"]
        mock_tag_recommendation.return_value = ("recommended_tag", self.tag.tag_id)

        self.client.force_authenticate(user=self.user)

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        expected_data = {"tag_id": self.tag.tag_id, "tag": "recommended_tag"}

        self.assertEqual(int(response.data["tag_id"]), expected_data["tag_id"])
        self.assertEqual(response.data["tag"], expected_data["tag"])
        mock_tag_recommendation.assert_called_once_with(self.user.id, self.photo_id)

    def test_get_recommend_tag_unauthorized(self):
        url = f"/api/photos/{self.photo_id}/recommendation/"

        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    @patch("gallery.views.client")
    def test_get_recommend_tag_photo_not_found(self, mock_qdrant_client):
        mock_qdrant_client.retrieve.return_value = []
        self.client.force_authenticate(user=self.user)

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(response.data, {"error": "No such photo"})

    def test_get_recommend_tag_invalid_uuid(self):
        self.client.force_authenticate(user=self.user)
        invalid_photo_id = "this-is-not-a-uuid"
        url = f"/api/photos/{invalid_photo_id}/recommendation/"

        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

