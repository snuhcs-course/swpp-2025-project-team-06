import uuid
from unittest.mock import patch, MagicMock

from django.urls import reverse
from django.test import TestCase
from rest_framework.test import APITestCase
from rest_framework import status

from django.contrib.auth.models import User
from .models import Tag

import numpy as np
from qdrant_client.http import models

class GetRecommendTagViewTest(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='password123')
        self.user_id = self.user.id
        self.tag = Tag.objects.create(user=self.user, tag_id=1, tag='test_tag')
        self.photo_id = uuid.uuid4()

    @patch('gallery.views.tag_recommendation')
    @patch('gallery.views.client')
    def test_get_recommend_tag_success(self, mock_qdrant_client, mock_tag_recommendation):
        mock_qdrant_client.retrieve.return_value = ['some_point_data'] 
        mock_tag_recommendation.return_value = ('recommended_tag', self.tag.tag_id)

        self.client.force_authenticate(user=self.user)
        url = f'/api/photos/{self.photo_id}/recommendation/'
        
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        expected_data = {'tag_id': self.tag.tag_id, 'tag': 'recommended_tag'}
        
        self.assertEqual(int(response.data['tag_id']), expected_data['tag_id'])
        self.assertEqual(response.data['tag'], expected_data['tag'])
        mock_tag_recommendation.assert_called_once_with(self.user.id, self.photo_id)

    def test_get_recommend_tag_unauthorized(self):
        url = f'/api/photos/{self.photo_id}/recommendation/'

        response = self.client.get(url)
        
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    @patch('gallery.views.client')
    def test_get_recommend_tag_photo_not_found(self, mock_qdrant_client):
        mock_qdrant_client.retrieve.return_value = []
        self.client.force_authenticate(user=self.user)
        url = f'/api/photos/{self.photo_id}/recommendation/'
        
        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(response.data, {"error": "No such photo"})

    def test_get_recommend_tag_invalid_uuid(self):
        self.client.force_authenticate(user=self.user)
        invalid_photo_id = "this-is-not-a-uuid"
        url = f'/api/photos/{invalid_photo_id}/recommendation/'

        response = self.client.get(url)

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)


class TaskFunctionsTest(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='password123')
        self.user_id = self.user.id
        self.photo_id = uuid.uuid4()
        Tag.objects.create(user=self.user, tag_id=1, tag='test_tag_1')

    @patch('gallery.tasks.find_most_similar_tag')
    @patch('gallery.tasks.Tag.objects.get')
    @patch('gallery.tasks.client')
    def test_tag_recommendation(self, mock_client, mock_tag_get, mock_find_tag):
        mock_scroll_point = models.ScoredPoint(
            id=str(uuid.uuid4()), version=1, score=1.0, 
            payload={'user_id': self.user_id, 'tag_id': 1}, vector=[0.1] * 128
        )
        mock_retrieve_point = models.Record(
            id=str(self.photo_id), payload={}, vector=[0.2] * 128
        )
        mock_client.scroll.side_effect = [
            ([mock_scroll_point], 1),
            ([], None)
        ]
        mock_client.retrieve.return_value = mock_retrieve_point
        
        mock_tag_instance = MagicMock()
        mock_tag_instance.tag = 'test_tag_1'
        mock_tag_instance.tag_id = 1
        mock_tag_get.return_value = mock_tag_instance
        
        mock_find_tag.return_value = 'test_tag_1'

        from .tasks import tag_recommendation
        tag, tag_id = tag_recommendation(self.user_id, self.photo_id)

        self.assertEqual(tag, 'test_tag_1')
        self.assertEqual(tag_id, 1)
        mock_find_tag.assert_called_once()