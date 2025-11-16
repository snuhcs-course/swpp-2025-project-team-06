"""
Tests for gallery/views.py

All external dependencies (Qdrant, Redis, Celery, Storage) are mocked.
"""

import uuid
import json
from io import BytesIO
from unittest.mock import MagicMock, patch
from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
from django.urls import reverse
from rest_framework.test import APIClient
from rest_framework import status
from PIL import Image

from ..models import Photo, Tag, Photo_Tag


class PhotoViewTest(TestCase):
    """PhotoView 테스트 (POST, GET)"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)
        self.url = reverse('gallery:photos')

    def make_test_image(self, format="JPEG", size=(100, 100), color=(255, 0, 0)):
        file = BytesIO()
        image = Image.new("RGB", size, color)
        image.save(file, format)
        file.name = f"test.{format.lower()}"
        file.seek(0)
        return file

    @patch("gallery.views.process_and_embed_photos_batch.delay")
    @patch("gallery.views.upload_photo")
    def test_post_photo_success(self, mock_upload, mock_process):
        """사진 업로드 성공"""
        # Mock upload_photo to return storage keys
        mock_upload.side_effect = lambda data: uuid.uuid4()

        # Create fake image files
        photo1 = self.make_test_image()
        photo1.name = "test1.jpg"
        photo2 = self.make_test_image()
        photo2.name = "test2.jpg"

        metadata = [
            {
                "filename": "test1.jpg",
                "photo_path_id": 101,
                "created_at": "2024-01-01T00:00:00Z",
                "lat": 37.5,
                "lng": 127.0,
            },
            {
                "filename": "test2.jpg",
                "photo_path_id": 102,
                "created_at": "2024-01-02T00:00:00Z",
                "lat": 37.6,
                "lng": 127.1,
            },
        ]

        response = self.client.post(
            self.url,
            {
                "photo": [photo1, photo2],
                "metadata": json.dumps(metadata),
            },
            format="multipart",
        )

        self.assertEqual(response.status_code, status.HTTP_202_ACCEPTED)
        self.assertIn("message", response.data)
        
        # Verify photos created in DB
        self.assertEqual(Photo.objects.filter(user=self.user).count(), 2)
        
        # Verify batch processing called
        mock_process.assert_called_once()

    def test_post_photo_missing_metadata(self):
        """metadata 누락 시 400 에러"""
        photo = BytesIO(b"fake_image")
        photo.name = "test.jpg"

        response = self.client.post(
            self.url,
            {"photo": [photo]},
            format="multipart",
        )

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_photo_invalid_json_metadata(self):
        """잘못된 JSON metadata"""
        photo = BytesIO(b"fake_image")
        photo.name = "test.jpg"

        response = self.client.post(
            self.url,
            {
                "photo": [photo],
                "metadata": "invalid json",
            },
            format="multipart",
        )

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_photo_mismatched_count(self):
        """사진과 metadata 개수 불일치"""
        photo = BytesIO(b"fake_image")
        photo.name = "test.jpg"

        metadata = [
            {"filename": "test1.jpg", "photo_path_id": 101, "created_at": "2024-01-01T00:00:00Z", "lat": 37.5, "lng": 127.0},
            {"filename": "test2.jpg", "photo_path_id": 102, "created_at": "2024-01-02T00:00:00Z", "lat": 37.6, "lng": 127.1},
        ]

        response = self.client.post(
            self.url,
            {
                "photo": [photo],  # 1개
                "metadata": json.dumps(metadata),  # 2개
            },
            format="multipart",
        )

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_get_photos_success(self):
        """사진 목록 조회 성공"""
        # Create test photos
        for i in range(15):
            Photo.objects.create(
                photo_id=uuid.uuid4(),
                user=self.user,
                photo_path_id=100 + i,
                created_at=timezone.now(),
            )

        response = self.client.get(f"{self.url}?offset=0&limit=10")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 10)

    def test_get_photos_pagination(self):
        """페이지네이션 테스트"""
        for i in range(25):
            Photo.objects.create(
                photo_id=uuid.uuid4(),
                user=self.user,
                photo_path_id=100 + i,
                created_at=timezone.now(),
            )

        response = self.client.get(f"{self.url}?offset=10&limit=10")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 10)

    def test_get_photos_invalid_offset(self):
        """잘못된 offset 파라미터"""
        response = self.client.get(f"{self.url}?offset=invalid&limit=10")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_photo_unauthenticated(self):
        """인증되지 않은 사용자"""
        self.client.force_authenticate(user=None)

        photo = BytesIO(b"fake_image")
        photo.name = "test.jpg"
        metadata = [{"filename": "test.jpg", "photo_path_id": 101, "created_at": "2024-01-01T00:00:00Z", "lat": 37.5, "lng": 127.0}]

        response = self.client.post(
            self.url,
            {
                "photo": [photo],
                "metadata": json.dumps(metadata),
            },
            format="multipart",
        )

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class PhotoDetailViewTest(TestCase):
    """PhotoDetailView 테스트 (GET, DELETE)"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.photo = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=12345,
            created_at=timezone.now(),
            lat=37.5,
            lng=127.0,
        )

        self.tag1 = Tag.objects.create(tag="태그1", user=self.user)
        self.tag2 = Tag.objects.create(tag="태그2", user=self.user)

        Photo_Tag.objects.create(user=self.user, photo=self.photo, tag=self.tag1)
        Photo_Tag.objects.create(user=self.user, photo=self.photo, tag=self.tag2)

        self.url = reverse('gallery:photo_detail', kwargs={'photo_id': self.photo.photo_id})

    @patch("gallery.views.get_redis")
    @patch("gallery.views.requests.get")
    def test_get_photo_detail_success(self, mock_requests_get, mock_get_redis):
        """사진 상세 정보 조회 성공"""
        # Mock Redis
        mock_redis = MagicMock()
        mock_redis.get.return_value = None  # Cache miss
        mock_get_redis.return_value = mock_redis

        # Mock geocoding API
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "results": [{"formatted_address": "서울특별시 강남구"}]
        }
        mock_requests_get.return_value = mock_response

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["photo_path_id"], 12345)
        self.assertEqual(len(response.data["tags"]), 2)

    def test_get_photo_detail_not_found(self):
        """존재하지 않는 사진"""
        fake_id = uuid.uuid4()
        url = reverse('gallery:photo_detail', kwargs={'photo_id': fake_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    @patch("gallery.views.compute_and_store_rep_vectors.delay")
    @patch("gallery.views.get_qdrant_client")
    def test_delete_photo_success(self, mock_get_client, mock_compute):
        """사진 삭제 성공"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        response = self.client.delete(self.url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # Verify photo deleted from DB
        self.assertFalse(Photo.objects.filter(photo_id=self.photo.photo_id).exists())
        
        # Verify Qdrant delete called
        mock_client.delete.assert_called_once()
        
        # Verify rep vectors recomputation triggered
        self.assertEqual(mock_compute.call_count, 2)  # 2 tags


class BulkDeletePhotoViewTest(TestCase):
    """BulkDeletePhotoView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=101,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=102,
            created_at=timezone.now(),
        )

        self.url = reverse('gallery:photos_bulk_delete')

    @patch("gallery.views.compute_and_store_rep_vectors.delay")
    @patch("gallery.views.get_qdrant_client")
    def test_bulk_delete_photos_success(self, mock_get_client, mock_compute):
        """여러 사진 삭제 성공"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        data = {
            "photos": [
                {"photo_id": str(self.photo1.photo_id)},
                {"photo_id": str(self.photo2.photo_id)},
            ]
        }

        response = self.client.post(self.url, data, format="json")

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # Verify photos deleted
        self.assertFalse(Photo.objects.filter(photo_id=self.photo1.photo_id).exists())
        self.assertFalse(Photo.objects.filter(photo_id=self.photo2.photo_id).exists())

    def test_bulk_delete_invalid_data(self):
        """잘못된 데이터 형식"""
        response = self.client.post(self.url, {}, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)


class GetPhotosByTagViewTest(TestCase):
    """GetPhotosByTagView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.tag = Tag.objects.create(tag="테스트태그", user=self.user)

        self.photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=101,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=102,
            created_at=timezone.now(),
        )

        Photo_Tag.objects.create(user=self.user, photo=self.photo1, tag=self.tag)
        Photo_Tag.objects.create(user=self.user, photo=self.photo2, tag=self.tag)

        self.url = reverse('gallery:photos_by_tag', kwargs={'tag_id': self.tag.tag_id})

    def test_get_photos_by_tag_success(self):
        """태그별 사진 조회 성공"""
        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_get_photos_by_tag_not_found(self):
        """존재하지 않는 태그"""
        fake_id = uuid.uuid4()
        url = reverse('gallery:photos_by_tag', kwargs={'tag_id': fake_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)


class PostPhotoTagsViewTest(TestCase):
    """PostPhotoTagsView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.photo = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=12345,
            created_at=timezone.now(),
        )

        self.tag1 = Tag.objects.create(tag="태그1", user=self.user)
        self.tag2 = Tag.objects.create(tag="태그2", user=self.user)

        self.url = reverse('gallery:photo_tags', kwargs={'photo_id': self.photo.photo_id})

    @patch("gallery.views.compute_and_store_rep_vectors.delay")
    def test_post_photo_tags_success(self, mock_compute):
        """사진에 태그 추가 성공"""
        data = [
            {"tag_id": str(self.tag1.tag_id)},
            {"tag_id": str(self.tag2.tag_id)},
        ]

        response = self.client.post(
            self.url, data, format="json"
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        
        # Verify Photo_Tag created
        self.assertTrue(
            Photo_Tag.objects.filter(
                user=self.user, photo=self.photo, tag=self.tag1
            ).exists()
        )
        self.assertTrue(
            Photo_Tag.objects.filter(
                user=self.user, photo=self.photo, tag=self.tag2
            ).exists()
        )

    def test_post_photo_tags_photo_not_found(self):
        """존재하지 않는 사진"""
        fake_id = uuid.uuid4()
        data = [{"tag_id": str(self.tag1.tag_id)}]

        url = reverse('gallery:photo_tags', kwargs={'photo_id': fake_id})
        response = self.client.post(
            url, data, format="json"
        )

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_post_photo_tags_tag_not_found(self):
        """존재하지 않는 태그"""
        fake_tag_id = uuid.uuid4()
        data = [{"tag_id": str(fake_tag_id)}]

        response = self.client.post(
            self.url, data, format="json"
        )

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)


class DeletePhotoTagsViewTest(TestCase):
    """DeletePhotoTagsView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.photo = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=12345,
            created_at=timezone.now(),
        )

        self.tag = Tag.objects.create(tag="태그1", user=self.user)

        Photo_Tag.objects.create(user=self.user, photo=self.photo, tag=self.tag)

        self.url = reverse('gallery:delete_photo_tag', kwargs={
            'photo_id': self.photo.photo_id,
            'tag_id': self.tag.tag_id
        })

    @patch("gallery.views.compute_and_store_rep_vectors.delay")
    def test_delete_photo_tag_success(self, mock_compute):
        """사진-태그 관계 삭제 성공"""
        response = self.client.delete(self.url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # Verify Photo_Tag deleted
        self.assertFalse(
            Photo_Tag.objects.filter(
                user=self.user, photo=self.photo, tag=self.tag
            ).exists()
        )
        
        # Verify rep vectors recomputation triggered
        mock_compute.assert_called_once()

    def test_delete_photo_tag_not_found(self):
        """존재하지 않는 관계"""
        fake_tag_id = uuid.uuid4()
        url = reverse('gallery:delete_photo_tag', kwargs={
            'photo_id': self.photo.photo_id,
            'tag_id': fake_tag_id
        })
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)


class GetRecommendTagViewTest(TestCase):
    """GetRecommendTagView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.photo = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=12345,
            created_at=timezone.now(),
        )

        self.tag1 = Tag.objects.create(tag="추천태그1", user=self.user)
        self.tag2 = Tag.objects.create(tag="추천태그2", user=self.user)

        self.url = reverse('gallery:tag_recommendation', kwargs={'photo_id': self.photo.photo_id})

    @patch("gallery.views.tag_recommendation")
    def test_get_recommend_tag_success(self, mock_tag_rec):
        """태그 추천 성공"""
        mock_tag_rec.return_value = [self.tag1, self.tag2]

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_get_recommend_tag_photo_not_found(self):
        """존재하지 않는 사진"""
        fake_id = uuid.uuid4()
        url = reverse('gallery:tag_recommendation', kwargs={'photo_id': fake_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)


class PhotoRecommendationViewTest(TestCase):
    """PhotoRecommendationView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.tag = Tag.objects.create(tag="테스트태그", user=self.user)

        self.url = reverse('gallery:photo_recommendation', kwargs={'tag_id': self.tag.tag_id})

    @patch("gallery.views.recommend_photo_from_tag")
    def test_get_photo_recommendation_success(self, mock_recommend):
        """사진 추천 성공"""
        mock_recommend.return_value = [
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 101, "created_at": timezone.now()},
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 102, "created_at": timezone.now()},
        ]

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_get_photo_recommendation_tag_not_found(self):
        """존재하지 않는 태그"""
        fake_id = uuid.uuid4()
        url = reverse('gallery:photo_recommendation', kwargs={'tag_id': fake_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)


class PhotoToPhotoRecommendationViewTest(TestCase):
    """PhotoToPhotoRecommendationView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.url = reverse('gallery:photo_to_photo_recommendation')

    @patch("gallery.views.recommend_photo_from_photo")
    def test_photo_to_photo_recommendation_success(self, mock_recommend):
        """사진 기반 추천 성공"""
        photo_ids = [str(uuid.uuid4()), str(uuid.uuid4())]
        
        mock_recommend.return_value = [
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 201, "created_at": timezone.now()},
        ]

        data = {"photos": photo_ids}
        response = self.client.post(self.url, data, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_photo_to_photo_recommendation_invalid_data(self):
        """잘못된 데이터 형식"""
        response = self.client.post(self.url, {}, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)


class TagViewTest(TestCase):
    """TagView 테스트 (GET, POST)"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.url = reverse('gallery:tags')

    def test_get_all_tags_success(self):
        """모든 태그 조회 성공"""
        tag1 = Tag.objects.create(tag="태그1", user=self.user)
        tag2 = Tag.objects.create(tag="태그2", user=self.user)
        # Create photos for thumbnails
        photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=101,
            created_at=timezone.now(),
        )
        Photo_Tag.objects.create(user=self.user, photo=photo1, tag=tag1)
        Photo_Tag.objects.create(user=self.user, photo=photo1, tag=tag2)

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    @patch("gallery.views.compute_and_store_rep_vectors.delay")
    def test_post_tag_success(self, mock_compute):
        """새 태그 생성 성공"""
        data = {"tag": "새태그"}

        response = self.client.post(self.url, data, format="json")

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("tag_id", response.data)
        
        # Verify tag created
        self.assertTrue(Tag.objects.filter(tag="새태그", user=self.user).exists())

    def test_post_tag_duplicate(self):
        """중복 태그 생성 시도"""
        Tag.objects.create(tag="중복태그", user=self.user)

        data = {"tag": "중복태그"}
        response = self.client.post(self.url, data, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_post_tag_invalid_data(self):
        """잘못된 데이터 형식"""
        response = self.client.post(self.url, {}, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)


class TagDetailViewTest(TestCase):
    """TagDetailView 테스트 (GET, PUT, DELETE)"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.tag = Tag.objects.create(tag="테스트태그", user=self.user)

        self.url = reverse('gallery:tag_detail', kwargs={'tag_id': self.tag.tag_id})

    @patch("gallery.views.get_qdrant_client")
    def test_delete_tag_success(self, mock_get_client):
        """태그 삭제 성공"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        response = self.client.delete(self.url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        
        # Verify tag deleted
        self.assertFalse(Tag.objects.filter(tag_id=self.tag.tag_id).exists())

    def test_delete_tag_not_found(self):
        """존재하지 않는 태그"""
        fake_id = uuid.uuid4()
        url = reverse('gallery:tag_detail', kwargs={'tag_id': fake_id})
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    @patch("gallery.views.compute_and_store_rep_vectors.delay")
    def test_put_tag_success(self, mock_compute):
        """태그 이름 변경 성공"""
        data = {"tag": "변경된태그"}

        response = self.client.put(self.url, data, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        
        # Verify tag name changed
        self.tag.refresh_from_db()
        self.assertEqual(self.tag.tag, "변경된태그")

    def test_put_tag_duplicate(self):
        """중복 태그 이름으로 변경 시도"""
        Tag.objects.create(tag="기존태그", user=self.user)

        data = {"tag": "기존태그"}
        response = self.client.put(self.url, data, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_get_tag_info_success(self):
        """태그 정보 조회 성공"""
        # Create some photos for the tag
        photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=101,
            created_at=timezone.now(),
        )
        Photo_Tag.objects.create(user=self.user, photo=photo1, tag=self.tag)

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["tag"], "테스트태그")


class StoryViewTest(TestCase):
    """StoryView 테스트"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.url = reverse('gallery:stories')

    @patch("gallery.tasks.tag_recommendation_batch")
    def test_get_stories_success(self, mock_tag_batch):
        """스토리 조회 성공"""
        # Create photos without tags
        photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=101,
            created_at=timezone.now(),
        )
        photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=102,
            created_at=timezone.now(),
        )

        mock_tag_batch.return_value = {
            str(photo1.photo_id): ["태그1", "태그2"],
            str(photo2.photo_id): ["태그3"],
        }

        response = self.client.get(f"{self.url}?size=5")

        self.assertEqual(response.status_code, status.HTTP_200_OK)


class NewStoryViewTest(TestCase):
    """NewStoryView 테스트 (GET, POST)"""

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.client.force_authenticate(user=self.user)

        self.url = reverse('gallery:new_stories')

    @patch("gallery.views.get_redis")
    def test_get_new_stories_success(self, mock_get_redis):
        """Redis에서 스토리 조회 성공"""
        mock_redis = MagicMock()
        mock_redis.exists.return_value = 1  # Key exists
        mock_redis.get.return_value = json.dumps([
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 101, "tags": ["태그1"]},
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 102, "tags": ["태그2"]},
        ])
        mock_get_redis.return_value = mock_redis

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)

    @patch("gallery.views.get_redis")
    def test_get_new_stories_empty(self, mock_get_redis):
        """Redis에 스토리가 없는 경우"""
        mock_redis = MagicMock()
        mock_redis.exists.return_value = 0  # Key does not exist
        mock_redis.get.return_value = None
        mock_get_redis.return_value = mock_redis

        response = self.client.get(self.url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("error", response.data)

    @patch("gallery.views.generate_stories_task.delay")
    def test_post_new_stories_success(self, mock_generate):
        """스토리 생성 요청 성공"""
        response = self.client.post(f"{self.url}?size=10")

        self.assertEqual(response.status_code, status.HTTP_202_ACCEPTED)
        mock_generate.assert_called_once_with(self.user.id, 10)

    def test_post_new_stories_invalid_size(self):
        """잘못된 size 파라미터"""
        response = self.client.post(f"{self.url}?size=invalid")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
