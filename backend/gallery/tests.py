import uuid
from unittest.mock import patch

from django.test import TestCase
from rest_framework.test import APITestCase
from rest_framework import status

from django.contrib.auth.models import User
from .models import Tag
from .tasks import tag_recommendation
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
        self.tag = Tag.objects.create(user=self.user, tag_id=1, tag='recommended_tag')

    @patch('gallery.tasks.Tag.objects.get')
    @patch('gallery.tasks.client')
    def test_tag_recommendation_success(self, mock_client, mock_tag_get):
 
        mock_retrieve_point = models.Record(
            id=str(self.photo_id), payload={}, vector=[0.1] * 768
        )
        mock_client.retrieve.return_value = [mock_retrieve_point]

        mock_search_result_point = models.ScoredPoint(
            id=str(uuid.uuid4()), version=1, score=0.95,
            payload={'user_id': self.user_id, 'tag_id': self.tag.tag_id},
            vector=None
        )
        mock_client.search.return_value = [mock_search_result_point]

        mock_tag_get.return_value = self.tag
        
        tag_name, tag_id = tag_recommendation(self.user_id, self.photo_id)

        self.assertEqual(tag_name, 'recommended_tag')
        self.assertEqual(tag_id, self.tag.tag_id)

        mock_client.retrieve.assert_called_once_with(
            collection_name='my_image_collection',
            ids=[self.photo_id],
            with_vectors=True
        )
        
        mock_client.search.assert_called_once()
        call_args, call_kwargs = mock_client.search.call_args
        self.assertEqual(call_kwargs['collection_name'], 'my_repvec_collection')
        self.assertEqual(call_kwargs['limit'], 1)
        np.testing.assert_array_equal(call_kwargs['query_vector'], mock_retrieve_point.vector)

        mock_tag_get.assert_called_once_with(tag_id=self.tag.tag_id)

    @patch('gallery.tasks.client')
    def test_tag_recommendation_no_similar_tag_found(self, mock_client):
        mock_retrieve_point = models.Record(
            id=str(self.photo_id), payload={}, vector=[0.1] * 768
        )
        mock_client.retrieve.return_value = [mock_retrieve_point]
        mock_client.search.return_value = []

        tag_name, tag_id = tag_recommendation(self.user_id, self.photo_id)

        self.assertIsNone(tag_name)
        self.assertIsNone(tag_id)