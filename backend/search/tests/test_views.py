import uuid
from django.urls import reverse
from django.contrib.auth.models import User
from django.utils import timezone
from rest_framework.test import APIClient, APITestCase
from rest_framework import status
from rest_framework_simplejwt.tokens import RefreshToken
from unittest.mock import patch, MagicMock
from gallery.models import Tag, Photo_Tag, Photo
from qdrant_client.http import models as qdrant_models


class SemanticSearchViewTest(APITestCase):
    """SemanticSearchView 테스트 - 시맨틱 검색 기능"""

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

        # Create test tags
        self.tag1 = Tag.objects.create(user=self.user, tag="sunset")
        self.tag2 = Tag.objects.create(user=self.user, tag="beach")

        # Create test photos
        self.photo1 = Photo.objects.create(
            user=self.user,
            photo_path_id=1001,
            filename="test_photo1.jpg",
            created_at=timezone.now(),
            lat=37.5,
            lng=127.0,
        )
        self.photo2 = Photo.objects.create(
            user=self.user,
            photo_path_id=1002,
            filename="test_photo2.jpg",
            created_at=timezone.now(),
            lat=35.1,
            lng=129.0,
        )

        # Create photo-tag associations
        Photo_Tag.objects.create(user=self.user, photo=self.photo1, tag=self.tag1)
        Photo_Tag.objects.create(user=self.user, photo=self.photo2, tag=self.tag2)

        # URL for semantic search
        self.search_url = reverse("search:semantic-search")

    def test_search_missing_query_parameter(self):
        """Test search without query parameter"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url)

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("error", response.data)
        self.assertEqual(response.data["error"], "query parameter is required.")

    def test_search_with_tag_only(self):
        """Test search with tag only (no semantic query)"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        
        # Search for {sunset}
        response = self.client.get(self.search_url, {"query": "{sunset}"})

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsInstance(response.data, list)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]["photo_path_id"], 1001)

    def test_search_with_multiple_tags(self):
        """Test search with multiple tags"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        
        # Search for {sunset} {beach}
        response = self.client.get(self.search_url, {"query": "{sunset} {beach}"})

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsInstance(response.data, list)
        self.assertEqual(len(response.data), 2)

    def test_search_with_invalid_tag(self):
        """Test search with non-existent tag"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        
        # Search for {nonexistent}
        response = self.client.get(self.search_url, {"query": "{nonexistent}"})

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("message", response.data)

    @patch("search.views.get_qdrant_client")
    @patch("search.views.create_query_embedding")
    def test_search_with_semantic_query_only(self, mock_embedding, mock_get_client):
        """Test search with semantic query only (no tags)"""
        # Mock embedding
        mock_embedding.return_value = [0.1] * 512

        # Mock Qdrant search result
        mock_point = MagicMock()
        mock_point.id = str(self.photo1.photo_id)
        mock_point.payload = {"user_id": self.user.id}
        
        mock_get_client.return_value.search.return_value = [mock_point]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url, {"query": "beautiful sunset"})

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsInstance(response.data, list)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]["photo_path_id"], 1001)

        # Verify embedding was created
        mock_embedding.assert_called_once_with("beautiful sunset")

        # Verify Qdrant search was called
        mock_get_client.return_value.search.assert_called_once()

    @patch("search.views.get_qdrant_client")
    @patch("search.views.create_query_embedding")
    def test_search_with_semantic_query_empty_results(
        self, mock_embedding, mock_get_client
    ):
        """Test semantic search with no matching results"""
        # Mock embedding
        mock_embedding.return_value = [0.1] * 512

        # Mock empty Qdrant search result
        mock_get_client.return_value.search.return_value = []

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url, {"query": "random query"})

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsInstance(response.data, list)
        self.assertEqual(len(response.data), 0)

    @patch("search.views.execute_hybrid_search")
    @patch("search.views.create_query_embedding")
    def test_search_with_tag_and_semantic_query(
        self, mock_embedding, mock_hybrid_search
    ):
        """Test hybrid search with both tag and semantic query"""
        # Mock embedding
        mock_embedding.return_value = [0.1] * 512

        # Mock hybrid search result
        mock_hybrid_search.return_value = [
            {
                "photo_id": str(self.photo1.photo_id),
                "photo_path_id": 1001,
                "created_at": self.photo1.created_at,
            }
        ]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(
            self.search_url, {"query": "{sunset} beautiful scenery"}
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIsInstance(response.data, list)
        self.assertEqual(len(response.data), 1)

        # Verify hybrid search was called
        mock_hybrid_search.assert_called_once()
        call_args = mock_hybrid_search.call_args
        self.assertEqual(call_args[1]["user"], self.user)
        self.assertEqual(call_args[1]["query_string"], "beautiful scenery")
        self.assertIn(self.tag1.tag_id, call_args[1]["tag_ids"])

    @patch("search.views.get_qdrant_client")
    @patch("search.views.create_query_embedding")
    def test_search_with_offset(self, mock_embedding, mock_get_client):
        """Test search with offset parameter (pagination)"""
        # Mock embedding
        mock_embedding.return_value = [0.1] * 512

        # Mock Qdrant search result
        mock_point = MagicMock()
        mock_point.id = str(self.photo2.photo_id)
        mock_point.payload = {"user_id": self.user.id}
        
        mock_get_client.return_value.search.return_value = [mock_point]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(
            self.search_url, {"query": "sunset", "offset": 10}
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        
        # Verify offset was passed to Qdrant search
        call_kwargs = mock_get_client.return_value.search.call_args[1]
        self.assertEqual(call_kwargs["offset"], 10)

    @patch("search.views.get_qdrant_client")
    @patch("search.views.create_query_embedding")
    def test_search_semantic_query_exception_handling(
        self, mock_embedding, mock_get_client
    ):
        """Test exception handling during semantic search"""
        # Mock embedding
        mock_embedding.return_value = [0.1] * 512

        # Mock Qdrant search to raise exception
        mock_get_client.return_value.search.side_effect = Exception("Qdrant error")

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url, {"query": "test query"})

        self.assertEqual(response.status_code, status.HTTP_500_INTERNAL_SERVER_ERROR)
        self.assertIn("error", response.data)
        self.assertIn("Semantic search failed", response.data["error"])

    def test_search_unauthorized(self):
        """Test search without authentication"""
        response = self.client.get(self.search_url, {"query": "sunset"})

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_search_response_structure(self):
        """Test response structure for tag-only search"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url, {"query": "{sunset}"})

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        
        # Verify response structure
        if len(response.data) > 0:
            photo = response.data[0]
            self.assertIn("photo_id", photo)
            self.assertIn("photo_path_id", photo)
            self.assertIn("created_at", photo)

    @patch("search.views.get_qdrant_client")
    @patch("search.views.create_query_embedding")
    def test_search_with_score_threshold(self, mock_embedding, mock_get_client):
        """Test that semantic search uses score threshold"""
        # Mock embedding
        mock_embedding.return_value = [0.1] * 512

        # Mock Qdrant search result
        mock_get_client.return_value.search.return_value = []

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url, {"query": "sunset"})

        # Verify score_threshold was used
        call_kwargs = mock_get_client.return_value.search.call_args[1]
        self.assertEqual(call_kwargs["score_threshold"], 0.2)

    def test_search_empty_query_after_tag_removal(self):
        """Test search with only tags in curly braces and no text"""
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url, {"query": "   "})

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    @patch("search.views.get_qdrant_client")
    @patch("search.views.create_query_embedding")
    def test_search_maintains_qdrant_order(self, mock_embedding, mock_get_client):
        """Test that search results maintain Qdrant's order"""
        # Mock embedding
        mock_embedding.return_value = [0.1] * 512

        # Mock Qdrant search results in specific order
        mock_point1 = MagicMock()
        mock_point1.id = str(self.photo2.photo_id)
        
        mock_point2 = MagicMock()
        mock_point2.id = str(self.photo1.photo_id)
        
        mock_get_client.return_value.search.return_value = [mock_point1, mock_point2]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {self.access_token}")
        response = self.client.get(self.search_url, {"query": "test"})

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        
        # Verify order is maintained
        self.assertEqual(str(response.data[0]["photo_id"]), str(self.photo2.photo_id))
        self.assertEqual(str(response.data[1]["photo_id"]), str(self.photo1.photo_id))
