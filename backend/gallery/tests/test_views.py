import uuid
import json

from django.urls import reverse
from django.contrib.auth.models import User
from rest_framework.test import APIClient, APITestCase
from rest_framework import status
from rest_framework_simplejwt.tokens import RefreshToken
from unittest.mock import patch, MagicMock
from gallery.models import Tag, Photo_Tag

from PIL import Image
from io import BytesIO
from django.core.files.uploadedfile import SimpleUploadedFile


class PhotoViewTest(APITestCase):
    """PhotoView 테스트 - 사진 업로드 및 목록 조회"""

    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

        # Generate JWT token
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # Create test image files
        self.test_image = self._create_test_image()
        self.test_image2 = self._create_test_image("test_image2.jpg")

        # URL for photo list/upload
        self.photos_url = reverse("gallery:photos")

    def _create_test_image(self, name="test_image.jpg"):
        """Create a test image file"""
        # Create a simple test image
        image = Image.new("RGB", (100, 100), color="red")
        img_io = BytesIO()
        image.save(img_io, format="JPEG")
        img_io.seek(0)

        return SimpleUploadedFile(
            name=name, content=img_io.read(), content_type="image/jpeg"
        )

    def _create_large_test_image(self, name="large_test_image.jpg"):
        """Create a larger test image file"""
        # Create a larger test image
        image = Image.new("RGB", (1000, 1000), color="blue")
        img_io = BytesIO()
        image.save(img_io, format="JPEG", quality=95)
        img_io.seek(0)

        return SimpleUploadedFile(
            name=name, content=img_io.read(), content_type="image/jpeg"
        )

    @patch("gallery.views.get_qdrant_client")
    def test_get_photos_success(self, mock_get_client):
        """Test successful photo list retrieval"""
        # Mock Qdrant response
        mock_points = []
        for i in range(5):
            mock_point = MagicMock()
            mock_point.id = str(uuid.uuid4())
            mock_point.payload = {"photo_path_id": 100 + i}
            mock_points.append(mock_point)

        # Mock the scroll method to return photos in batches
        mock_get_client.return_value.scroll.return_value = (mock_points, None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.photos_url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsInstance(response.data, list)
        self.assertEqual(len(response.data), 5)

        # Verify photo structure
        for photo in response.data:
            self.assertIn("photo_id", photo)
            self.assertIn("photo_path_id", photo)

    @patch("gallery.views.get_qdrant_client")  
    def test_get_photos_empty_results(self, mock_get_client):
        """Test photo list when user has no photos"""
        # Mock empty Qdrant response
        mock_get_client.return_value.scroll.return_value = ([], None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.photos_url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    @patch("gallery.views.get_qdrant_client")
    def test_get_photos_pagination(self, mock_get_client):
        """Test photo list pagination logic"""
        # Mock large number of photos
        mock_points_batch1 = []
        for i in range(50):
            mock_point = MagicMock()
            mock_point.id = str(uuid.uuid4())
            mock_point.payload = {"photo_path_id": i}
            mock_points_batch1.append(mock_point)

        mock_points_batch2 = []
        for i in range(50, 80):
            mock_point = MagicMock()
            mock_point.id = str(uuid.uuid4())
            mock_point.payload = {"photo_path_id": i}
            mock_points_batch2.append(mock_point)

        # Mock scroll to simulate pagination
        mock_get_client.return_value.scroll.side_effect = [
            (mock_points_batch1, 50),  # First batch with next_offset
            (mock_points_batch2, None),  # Second batch, no more data
        ]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.photos_url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        # Total photos from both batches
        self.assertEqual(len(response.data), 80)

    def test_get_photos_unauthorized(self):
        """Test unauthorized photo list access"""
        response = self.client.get(self.photos_url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    @patch("gallery.views.process_and_embed_photo.delay")
    @patch("gallery.views.FileSystemStorage")
    def test_post_photo_success_single(self, mock_storage, mock_celery_task):
        """Test successful single photo upload"""
        # Mock Celery task
        mock_celery_task.return_value = MagicMock(id="task-123")

        # Mock file storage
        mock_fs_instance = MagicMock()
        mock_fs_instance.save.return_value = "saved_image.jpg"
        mock_fs_instance.path.return_value = "/media/saved_image.jpg"
        mock_storage.return_value = mock_fs_instance

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Prepare metadata
        metadata = [
            {
                "filename": "test_image.jpg",
                "photo_path_id": 12345,
                "created_at": "2023-10-30T10:00:00Z",
                "lat": 37.5665,
                "lng": 126.9780,
            }
        ]

        data = {"photo": [self.test_image], "metadata": json.dumps(metadata)}

        response = self.client.post(self.photos_url, data, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_202_ACCEPTED)
        self.assertIn("message", response.data)
        self.assertIn("being processed", response.data["message"])

        # Verify Celery task was called
        mock_celery_task.assert_called_once()

    @patch("gallery.views.process_and_embed_photo.delay")
    @patch("gallery.views.FileSystemStorage")
    def test_post_photo_success_multiple(self, mock_storage, mock_celery_task):
        """Test successful multiple photo upload"""
        # Mock Celery task
        mock_celery_task.return_value = MagicMock(id="task-456")

        # Mock file storage
        mock_fs_instance = MagicMock()
        mock_fs_instance.save.side_effect = ["saved_image1.jpg", "saved_image2.jpg"]
        mock_fs_instance.path.side_effect = [
            "/media/saved_image1.jpg",
            "/media/saved_image2.jpg",
        ]
        mock_storage.return_value = mock_fs_instance

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Prepare metadata for multiple photos
        metadata = [
            {
                "filename": "test_image1.jpg",
                "photo_path_id": 12345,
                "created_at": "2023-10-30T10:00:00Z",
                "lat": 37.5665,
                "lng": 126.9780,
            },
            {
                "filename": "test_image2.jpg",
                "photo_path_id": 12346,
                "created_at": "2023-10-30T10:05:00Z",
                "lat": 37.5666,
                "lng": 126.9781,
            },
        ]

        data = {
            "photo": [self.test_image, self.test_image2],
            "metadata": json.dumps(metadata),
        }

        response = self.client.post(self.photos_url, data, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_202_ACCEPTED)

        # Verify Celery task was called for each photo
        self.assertEqual(mock_celery_task.call_count, 2)

    def test_post_photo_missing_metadata(self):
        """Test photo upload without metadata"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        data = {
            "photo": [self.test_image]
            # metadata field missing
        }

        response = self.client.post(self.photos_url, data, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("metadata field is required", response.data["error"])

    def test_post_photo_invalid_metadata_json(self):
        """Test photo upload with invalid JSON metadata"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        data = {"photo": [self.test_image], "metadata": "invalid json string"}

        response = self.client.post(self.photos_url, data, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("Invalid JSON format", response.data["error"])

    def test_post_photo_metadata_count_mismatch(self):
        """Test photo upload with mismatched photo and metadata count"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # 2 photos but only 1 metadata entry
        metadata = [
            {
                "filename": "test_image.jpg",
                "photo_path_id": 12345,
                "created_at": "2023-10-30T10:00:00Z",
                "lat": 37.5665,
                "lng": 126.9780,
            }
        ]

        data = {
            "photo": [self.test_image, self.test_image2],
            "metadata": json.dumps(metadata),
        }

        response = self.client.post(self.photos_url, data, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn(
            "Number of photos and metadata entries must match", response.data["error"]
        )

    def test_post_photo_invalid_serializer_data(self):
        """Test photo upload with invalid serializer data"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Missing required fields in metadata
        metadata = [
            {
                "filename": "test_image.jpg",
                # missing required fields like photo_path_id, created_at, lat, lng
            }
        ]

        data = {"photo": [self.test_image], "metadata": json.dumps(metadata)}

        response = self.client.post(self.photos_url, data, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_photo_unauthorized(self):
        """Test unauthorized photo upload"""
        metadata = [
            {
                "filename": "test_image.jpg",
                "photo_path_id": 12345,
                "created_at": "2023-10-30T10:00:00Z",
                "lat": 37.5665,
                "lng": 126.9780,
            }
        ]

        data = {"photo": [self.test_image], "metadata": json.dumps(metadata)}

        response = self.client.post(self.photos_url, data, format="multipart")

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    @patch("gallery.views.process_and_embed_photo.delay")
    @patch("gallery.views.FileSystemStorage")
    def test_post_photo_with_large_file(self, mock_storage, mock_celery_task):
        """Test photo upload with large file"""
        # Mock Celery task
        mock_celery_task.return_value = MagicMock(id="task-789")

        # Mock file storage
        mock_fs_instance = MagicMock()
        mock_fs_instance.save.return_value = "large_saved_image.jpg"
        mock_fs_instance.path.return_value = "/media/large_saved_image.jpg"
        mock_storage.return_value = mock_fs_instance

        large_image = self._create_large_test_image()

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        metadata = [
            {
                "filename": "large_test_image.jpg",
                "photo_path_id": 99999,
                "created_at": "2023-10-30T10:00:00Z",
                "lat": 37.5665,
                "lng": 126.9780,
            }
        ]

        data = {"photo": [large_image], "metadata": json.dumps(metadata)}

        response = self.client.post(self.photos_url, data, format="multipart")

        # Should handle large files appropriately
        self.assertIn(
            response.status_code,
            [status.HTTP_202_ACCEPTED, status.HTTP_413_REQUEST_ENTITY_TOO_LARGE],
        )

    @patch("gallery.views.get_qdrant_client")
    def test_get_photos_filter_conditions(self, mock_get_client):
        """Test that correct filter conditions are applied for photo list"""
        mock_get_client.return_value.scroll.return_value = ([], None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        self.client.get(self.photos_url)

        # Verify the filter conditions
        call_args = mock_get_client.return_value.scroll.call_args
        filter_obj = call_args[1]["scroll_filter"]

        # Should filter by user_id
        user_condition = filter_obj.must[0]
        self.assertEqual(user_condition.key, "user_id")
        self.assertEqual(user_condition.match.value, self.user.id)

    def test_post_photo_edge_case_coordinates(self):
        """Test photo upload with edge case coordinates"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Test with extreme coordinate values
        extreme_coordinates = [
            # North Pole, International Date Line
            {"lat": 90.0, "lng": -180.0},
            # South Pole, International Date Line
            {"lat": -90.0, "lng": 180.0},
            {"lat": 0.0, "lng": 0.0},  # Equator, Prime Meridian
        ]

        for coords in extreme_coordinates:
            with self.subTest(coords=coords):
                metadata = [
                    {
                        "filename": "test_extreme.jpg",
                        "photo_path_id": 11111,
                        "created_at": "2023-10-30T10:00:00Z",
                        "lat": coords["lat"],
                        "lng": coords["lng"],
                    }
                ]

                # Create fresh image for each test
                test_image = self._create_test_image("extreme_test.jpg")

                data = {"photo": [test_image], "metadata": json.dumps(metadata)}

                response = self.client.post(self.photos_url, data, format="multipart")

                # Should be valid coordinates
                self.assertIn(
                    response.status_code,
                    [
                        status.HTTP_202_ACCEPTED,
                        status.HTTP_400_BAD_REQUEST,  # In case of validation
                    ],
                )


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
    @patch("gallery.views.get_qdrant_client")
    def test_get_recommend_tag_success(
        self, mock_get_client, mock_tag_recommendation
    ):
        mock_get_client.return_value.retrieve.return_value = ["some_point_data"]
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

    @patch("gallery.views.get_qdrant_client")
    def test_get_recommend_tag_photo_not_found(self, mock_get_client):
        mock_get_client.return_value.retrieve.return_value = []
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


class PhotoToPhotoRecommendationViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpass123"
        )

        # Generate JWT token for authenticated user
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # URL for the view
        self.url = reverse("gallery:photo_to_photo_recommendation")

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_success(self, mock_recommend):
        """Test successful retrieval of photo-to-photo recommendations"""
        # Setup mock data
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()
        photo_id3 = uuid.uuid4()

        mock_recommendations = [
            {"photo_id": str(photo_id2), "photo_path_id": 102},
            {"photo_id": str(photo_id3), "photo_path_id": 103},
        ]
        mock_recommend.return_value = mock_recommendations

        # Make request
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": [str(photo_id1)]}
        response = self.client.post(self.url, payload, format="json")

        # Assert
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)
        self.assertEqual(response.data, mock_recommendations)
        mock_recommend.assert_called_once()

        # Verify the function was called with correct arguments
        call_args = mock_recommend.call_args
        self.assertEqual(call_args[0][0], self.user)
        self.assertEqual(call_args[0][1], [photo_id1])

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_multiple_photos(self, mock_recommend):
        """Test recommendations with multiple input photos"""
        photo_ids = [uuid.uuid4() for _ in range(3)]

        mock_recommendations = [
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 201},
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 202},
        ]
        mock_recommend.return_value = mock_recommendations

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": [str(pid) for pid in photo_ids]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

        # Verify the function was called with multiple photo IDs
        call_args = mock_recommend.call_args
        self.assertEqual(len(call_args[0][1]), 3)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_empty_results(self, mock_recommend):
        """Test when recommendation function returns empty list"""
        mock_recommend.return_value = []

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": [str(uuid.uuid4())]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    def test_post_recommendations_unauthorized_no_token(self):
        """Test that request without authentication token fails"""
        payload = {"photos": [str(uuid.uuid4())]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_post_recommendations_unauthorized_invalid_token(self):
        """Test that request with invalid token fails"""
        self.client.credentials(HTTP_AUTHORIZATION="Bearer invalid_token")
        payload = {"photos": [str(uuid.uuid4())]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_post_recommendations_missing_photos_field(self):
        """Test request with missing 'photos' field"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("photos", response.data)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_empty_photos_list(self, mock_recommend):
        """Test request with empty photos list returns empty recommendations"""
        mock_recommend.return_value = []

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": []}
        response = self.client.post(self.url, payload, format="json")

        # Empty list is valid input, should return empty recommendations
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    def test_post_recommendations_invalid_photo_id_format(self):
        """Test request with invalid UUID format"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": ["not-a-uuid", "also-invalid"]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_recommendations_mixed_valid_invalid_uuids(self):
        """Test request with mix of valid and invalid UUIDs"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        valid_uuid = str(uuid.uuid4())
        payload = {"photos": [valid_uuid, "invalid-uuid"]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_recommendations_wrong_data_type(self):
        """Test request with wrong data type for photos field"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Test with string instead of list
        payload = {"photos": "not-a-list"}
        response = self.client.post(self.url, payload, format="json")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

        # Test with integer
        payload = {"photos": 123}
        response = self.client.post(self.url, payload, format="json")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_single_photo(self, mock_recommend):
        """Test recommendation with single photo ID"""
        photo_id = uuid.uuid4()
        mock_recommendations = [
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 100},
        ]
        mock_recommend.return_value = mock_recommendations

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": [str(photo_id)]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_large_photo_list(self, mock_recommend):
        """Test recommendation with large list of photo IDs"""
        photo_ids = [uuid.uuid4() for _ in range(50)]
        mock_recommendations = [
            {"photo_id": str(uuid.uuid4()), "photo_path_id": i} for i in range(20)
        ]
        mock_recommend.return_value = mock_recommendations

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": [str(pid) for pid in photo_ids]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 20)

    def test_only_post_method_allowed(self):
        """Test that only POST method is allowed"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Test GET
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)

        # Test PUT
        response = self.client.put(self.url, {})
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)

        # Test DELETE
        response = self.client.delete(self.url)
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)

        # Test PATCH
        response = self.client.patch(self.url, {})
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_returns_correct_structure(self, mock_recommend):
        """Test that response has correct structure with photo_id and photo_path_id"""
        photo_id = uuid.uuid4()
        expected_photo_id = str(uuid.uuid4())
        expected_path_id = 999

        mock_recommendations = [
            {"photo_id": expected_photo_id, "photo_path_id": expected_path_id},
        ]
        mock_recommend.return_value = mock_recommendations

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": [str(photo_id)]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

        result = response.data[0]
        self.assertIn("photo_id", result)
        self.assertIn("photo_path_id", result)
        self.assertEqual(result["photo_id"], expected_photo_id)
        self.assertEqual(result["photo_path_id"], expected_path_id)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_user_isolation(self, mock_recommend):
        """Test that recommendations use the authenticated user"""
        mock_recommendations = []
        mock_recommend.return_value = mock_recommendations

        # Login as first user
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {"photos": [str(uuid.uuid4())]}
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)

        # Verify the function was called with the correct user
        call_args = mock_recommend.call_args
        self.assertEqual(call_args[0][0], self.user)

        # Now test with other user
        other_refresh = RefreshToken.for_user(self.other_user)
        other_access_token = str(other_refresh.access_token)

        mock_recommend.reset_mock()
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {other_access_token}")
        response = self.client.post(self.url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)

        # Verify the function was called with the other user
        call_args = mock_recommend.call_args
        self.assertEqual(call_args[0][0], self.other_user)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_with_duplicate_photo_ids(self, mock_recommend):
        """Test request with duplicate photo IDs in the list"""
        photo_id = uuid.uuid4()
        mock_recommendations = [
            {"photo_id": str(uuid.uuid4()), "photo_path_id": 100},
        ]
        mock_recommend.return_value = mock_recommendations

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        # Include the same photo ID multiple times
        payload = {"photos": [str(photo_id), str(photo_id), str(photo_id)]}
        response = self.client.post(self.url, payload, format="json")

        # Should still succeed (duplicates are allowed in the request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        # Verify the function was called with the list (including duplicates)
        call_args = mock_recommend.call_args
        self.assertEqual(len(call_args[0][1]), 3)

    def test_post_recommendations_malformed_json(self):
        """Test request with malformed JSON"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")

        # Send invalid JSON (not using format="json" to bypass DRF's parser)
        response = self.client.post(
            self.url, data="invalid-json", content_type="application/json"
        )

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    @patch("gallery.views.recommend_photo_from_photo")
    def test_post_recommendations_extra_fields_ignored(self, mock_recommend):
        """Test that extra fields in request are ignored"""
        photo_id = uuid.uuid4()
        mock_recommendations = []
        mock_recommend.return_value = mock_recommendations

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        payload = {
            "photos": [str(photo_id)],
            "extra_field": "should be ignored",
            "another_field": 123,
        }
        response = self.client.post(self.url, payload, format="json")

        # Should succeed despite extra fields
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        mock_recommend.assert_called_once()


class PhotoDetailViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpass123"
        )

        # Generate JWT token
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # Test data
        self.photo_id = uuid.uuid4()
        self.tag = Tag.objects.create(tag="test_tag", user=self.user)
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_photo_detail_success_with_tags(self, mock_get_client):

        """Test successful photo detail retrieval with tags"""
        # Mock Qdrant response
        mock_point = MagicMock()
        mock_point.payload = {  # payload는 딕셔너리 그 자체
            "photo_path_id": 123,
            "user_id": self.user.id,
        }
        mock_get_client.return_value.retrieve.return_value = [mock_point]
        
        # Create photo-tag relationship
        Photo_Tag.objects.create(photo_id=self.photo_id, tag=self.tag, user=self.user)

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("photo_path_id", response.data)
        self.assertIn("tags", response.data)
        self.assertEqual(response.data["photo_path_id"], 123)
        self.assertEqual(len(response.data["tags"]), 1)
        self.assertEqual(response.data["tags"][0]["tag"], "test_tag")
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_photo_detail_success_no_tags(self, mock_get_client):
        """Test successful photo detail retrieval without tags"""
        mock_point = MagicMock()
        mock_point.payload = {  # payload는 딕셔너리 그 자체
            "photo_path_id": 123,
            "user_id": self.user.id,
        }
        mock_get_client.return_value.retrieve.return_value = [mock_point]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["photo_path_id"], 123)
        self.assertEqual(len(response.data["tags"]), 0)
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_photo_detail_not_found(self, mock_get_client):
        """Test photo not found"""
        mock_get_client.return_value.retrieve.return_value = []
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("Photo not found", response.data["error"])
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_photo_detail_wrong_user(self, mock_get_client):
        """Test accessing photo of another user"""
        mock_point = MagicMock()
        mock_point.payload.get.return_value = {"photo_path_id": 123, "user_id": self.other_user.id}
        mock_get_client.return_value.retrieve.return_value = [mock_point]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_get_photo_detail_unauthorized(self):
        """Test unauthorized access"""
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        
    @patch("gallery.views.get_qdrant_client")
    def test_delete_photo_success(self, mock_get_client):
        """Test successful photo deletion"""
        # Create photo-tag relationship
        Photo_Tag.objects.create(photo_id=self.photo_id, tag=self.tag, user=self.user)

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        # Verify photo-tag relationship is deleted
        self.assertFalse(Photo_Tag.objects.filter(photo_id=self.photo_id).exists())

    def test_delete_photo_unauthorized(self):
        """Test unauthorized photo deletion"""
        url = reverse("gallery:photo_detail", kwargs={"photo_id": self.photo_id})
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class BulkDeletePhotoViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

        # Generate JWT token
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # Test data
        self.photo_ids = [uuid.uuid4(), uuid.uuid4(), uuid.uuid4()]
        self.tag = Tag.objects.create(tag="test_tag", user=self.user)

        # Create photo-tag relationships
        for photo_id in self.photo_ids:
            Photo_Tag.objects.create(photo_id=photo_id, tag=self.tag, user=self.user)
    
    @patch("gallery.views.get_qdrant_client")
    def test_bulk_delete_photos_success(self, mock_get_client):
        """Test successful bulk photo deletion"""
        payload = {"photos": [{"photo_id": str(pid)} for pid in self.photo_ids]}

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_bulk_delete")
        response = self.client.post(url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        # Verify all photo-tag relationships are deleted
        for photo_id in self.photo_ids:
            self.assertFalse(Photo_Tag.objects.filter(photo_id=photo_id).exists())

    def test_bulk_delete_photos_invalid_serializer(self):
        """Test bulk delete with invalid serializer data"""
        payload = {"photos": [{"invalid_field": "invalid_value"}]}

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_bulk_delete")
        response = self.client.post(url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_bulk_delete_photos_unauthorized(self):
        """Test unauthorized bulk delete"""
        payload = {"photos": [{"photo_id": str(pid)} for pid in self.photo_ids]}

        url = reverse("gallery:photos_bulk_delete")
        response = self.client.post(url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class GetPhotosByTagViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

        # Generate JWT token
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # Test data
        self.tag = Tag.objects.create(tag="test_tag", user=self.user)
        self.photo_ids = [uuid.uuid4(), uuid.uuid4()]

        # Create photo-tag relationships
        for photo_id in self.photo_ids:
            Photo_Tag.objects.create(photo_id=photo_id, tag=self.tag, user=self.user)
            
    @patch("gallery.views.get_qdrant_client")
    def test_get_photos_by_tag_success(self, mock_get_client):
        """Test successful retrieval of photos by tag"""
        # Mock Qdrant response
        mock_points = []
        for i, photo_id in enumerate(self.photo_ids):
            mock_point = MagicMock()
            mock_point.id = str(photo_id)
            mock_point.payload.get.return_value = 100 + i
            mock_points.append(mock_point)
        mock_get_client.return_value.retrieve.return_value = mock_points
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_by_tag", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("photos", response.data)
        self.assertEqual(len(response.data["photos"]), 2)

    def test_get_photos_by_tag_no_photos(self):
        """Test retrieval when tag has no photos"""
        empty_tag = Tag.objects.create(tag="empty_tag", user=self.user)

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photos_by_tag", kwargs={"tag_id": empty_tag.tag_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["photos"]), 0)

    def test_get_photos_by_tag_unauthorized(self):
        """Test unauthorized access"""
        url = reverse("gallery:photos_by_tag", kwargs={"tag_id": self.tag.tag_id})
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class PostPhotoTagsViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

        # Generate JWT token
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # Test data
        self.photo_id = uuid.uuid4()
        self.tag1 = Tag.objects.create(tag="tag1", user=self.user)
        self.tag2 = Tag.objects.create(tag="tag2", user=self.user)
        
    @patch("gallery.views.get_qdrant_client")
    def test_post_photo_tags_success(self, mock_get_client):
        """Test successful addition of tags to photo"""
        # Mock Qdrant responses
        mock_get_client.return_value.retrieve.return_value = [MagicMock()]  # Photo exists
        mock_get_client.return_value.set_payload.return_value = None
        
        payload = [
            {"tag_id": str(self.tag1.tag_id)},
            {"tag_id": str(self.tag2.tag_id)}
        ]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_200_OK)

        # Verify photo-tag relationships are created
        self.assertTrue(Photo_Tag.objects.filter(photo_id=self.photo_id, tag=self.tag1).exists())
        self.assertTrue(Photo_Tag.objects.filter(photo_id=self.photo_id, tag=self.tag2).exists())
        
    @patch("gallery.views.get_qdrant_client")
    def test_post_photo_tags_photo_not_found(self, mock_get_client):
        """Test adding tags to non-existent photo"""
        mock_get_client.return_value.retrieve.return_value = []  # Photo doesn't exist
        
        payload = [{"tag_id": str(self.tag1.tag_id)}]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertIn("No such photo", response.data["error"])

    def test_post_photo_tags_invalid_serializer(self):
        """Test adding tags with invalid serializer data"""
        payload = [{"invalid_field": "invalid_value"}]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_photo_tags_unauthorized(self):
        """Test unauthorized tag addition"""
        payload = [{"tag_id": str(self.tag1.tag_id)}]

        url = reverse("gallery:photo_tags", kwargs={"photo_id": self.photo_id})
        response = self.client.post(url, payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class DeletePhotoTagsViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

        # Generate JWT token
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)

        # Test data
        self.photo_id = uuid.uuid4()
        self.tag = Tag.objects.create(tag="test_tag", user=self.user)
        self.photo_tag = Photo_Tag.objects.create(
            photo_id=self.photo_id, tag=self.tag, user=self.user
        )
        
    @patch("gallery.views.get_qdrant_client")
    def test_delete_photo_tag_success_with_remaining_tags(self, mock_get_client):
        """Test successful deletion of photo-tag relationship with remaining tags"""
        # Create another tag for the same photo
        another_tag = Tag.objects.create(tag="another_tag", user=self.user)
        Photo_Tag.objects.create(photo_id=self.photo_id, tag=another_tag, user=self.user)

        mock_get_client.return_value.retrieve.return_value = [MagicMock()]
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag",
            kwargs={"photo_id": self.photo_id, "tag_id": self.tag.tag_id},
        )
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        # Verify the specific photo-tag relationship is deleted
        self.assertFalse(
            Photo_Tag.objects.filter(photo_id=self.photo_id, tag=self.tag).exists()
        )
        # Verify other photo-tag relationship still exists
        self.assertTrue(
            Photo_Tag.objects.filter(photo_id=self.photo_id, tag=another_tag).exists()
        )
        
    @patch("gallery.views.get_qdrant_client")
    def test_delete_photo_tag_success_no_remaining_tags(self, mock_get_client):
        """Test successful deletion with no remaining tags (should update isTagged)"""
        mock_get_client.return_value.retrieve.return_value = [MagicMock()]
        mock_get_client.return_value.set_payload.return_value = None
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag",
            kwargs={"photo_id": self.photo_id, "tag_id": self.tag.tag_id},
        )
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        # Verify the photo-tag relationship is deleted
        self.assertFalse(
            Photo_Tag.objects.filter(photo_id=self.photo_id, tag=self.tag).exists()
        )
        # Verify Qdrant set_payload was called to update isTagged
        mock_get_client.return_value.set_payload.assert_called_once()
        
    @patch("gallery.views.get_qdrant_client")
    def test_delete_photo_tag_not_found(self, mock_get_client):
        """Test deletion of non-existent photo-tag relationship"""
        mock_get_client.return_value.retrieve.return_value = [MagicMock()]
        mock_get_client.return_value.set_payload.return_value = None

        non_existent_tag = Tag.objects.create(tag="non_existent", user=self.user)

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse(
            "gallery:delete_photo_tag",
            kwargs={"photo_id": self.photo_id, "tag_id": non_existent_tag.tag_id},
        )
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_delete_photo_tag_unauthorized(self):
        """Test unauthorized deletion"""
        url = reverse(
            "gallery:delete_photo_tag",
            kwargs={"photo_id": self.photo_id, "tag_id": self.tag.tag_id},
        )
        response = self.client.delete(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class StoryViewTest(APITestCase):
    def setUp(self):
        """Set up test client and test data"""
        self.client = APIClient()

        # Create test user
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

        # Generate JWT token
        refresh = RefreshToken.for_user(self.user)
        self.access_token = str(refresh.access_token)
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_stories_success(self, mock_get_client):
        """Test successful retrieval of stories"""
        # Mock Qdrant response
        mock_points = []
        for i in range(5):
            mock_point = MagicMock()
            mock_point.id = str(uuid.uuid4())
            mock_point.payload.get.return_value = 100 + i
            mock_points.append(mock_point)
        
        mock_get_client.return_value.scroll.return_value = (mock_points, None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("recs", response.data)
        self.assertEqual(len(response.data["recs"]), 5)

        # Check structure of each recommendation
        for rec in response.data["recs"]:
            self.assertIn("photo_id", rec)
            self.assertIn("photo_path_id", rec)
            
    @patch("gallery.views.get_qdrant_client")
    def test_get_stories_empty_results(self, mock_get_client):
        """Test retrieval when no untagged photos exist"""
        mock_get_client.return_value.scroll.return_value = ([], None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("recs", response.data)
        self.assertEqual(len(response.data["recs"]), 0)
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_stories_with_pagination(self, mock_get_client):
        """Test story retrieval with pagination parameters"""
        # Mock response for page 2 with page_size 3
        mock_points = []
        for i in range(3):
            mock_point = MagicMock()
            mock_point.id = str(uuid.uuid4())
            mock_point.payload.get.return_value = 200 + i
            mock_points.append(mock_point)
        
        mock_get_client.return_value.scroll.return_value = (mock_points, None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        response = self.client.get(url, {"page": 2, "pagesize": 3})

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["recs"]), 3)

        # Verify correct offset was calculated (page=2, page_size=3 -> offset=3)
        call_args = mock_get_client.return_value.scroll.call_args
        self.assertEqual(call_args[1]["offset"], 3)
        self.assertEqual(call_args[1]["limit"], 3)
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_stories_invalid_pagination_params(self, mock_get_client):
        """Test story retrieval with invalid pagination parameters"""
        mock_get_client.return_value.scroll.return_value = ([], None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")

        # Test with negative page number
        response = self.client.get(url, {"page": -1, "pagesize": 10})
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        # Test with page_size exceeding maximum
        response = self.client.get(url, {"page": 1, "pagesize": 500})
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        # Verify page_size was capped at 200
        call_args = mock_get_client.return_value.scroll.call_args
        self.assertEqual(call_args[1]["limit"], 200)

    def test_get_stories_unauthorized(self):
        """Test unauthorized access to stories"""
        url = reverse("gallery:stories")
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        
    @patch("gallery.views.get_qdrant_client")
    def test_get_stories_filter_conditions(self, mock_get_client):
        """Test that correct filter conditions are applied"""
        mock_get_client.return_value.scroll.return_value = ([], None)
        
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        url = reverse("gallery:stories")
        self.client.get(url)

        # Verify the filter conditions
        call_args = mock_get_client.return_value.scroll.call_args
        filter_obj = call_args[1]["scroll_filter"]

        # Should filter by user_id and isTagged=False
        self.assertEqual(len(filter_obj.must), 2)

        # Check user_id condition
        user_condition = filter_obj.must[0]
        self.assertEqual(user_condition.key, "user_id")
        self.assertEqual(user_condition.match.value, self.user.id)

        # Check isTagged condition
        tagged_condition = filter_obj.must[1]
        self.assertEqual(tagged_condition.key, "isTagged")
        self.assertEqual(tagged_condition.match.value, False)
