import uuid

from django.test import TestCase
from django.urls import reverse
from django.contrib.auth.models import User
from rest_framework.test import APIClient
from rest_framework import status
from rest_framework_simplejwt.tokens import RefreshToken
from unittest.mock import patch
from gallery.models import Tag


class PhotoRecommendationViewTest(TestCase):
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
            "photo-recommendation", kwargs={"tag_id": self.user_tag.tag_id}
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
            "photo-recommendation", kwargs={"tag_id": self.other_user_tag.tag_id}
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
