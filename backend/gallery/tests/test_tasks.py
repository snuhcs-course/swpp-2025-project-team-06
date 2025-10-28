import uuid

import numpy as np
from unittest.mock import patch, MagicMock
from django.test import TestCase
from django.contrib.auth.models import User
from qdrant_client.http import models

from gallery.models import Tag, Photo_Tag
from gallery.tasks import (
    retrieve_all_rep_vectors_of_tag,
    recommend_photo_from_tag,
    tag_recommendation,
)


# Create your tests here.
class TaskFunctionsTest(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", password="password123"
        )
        self.user_id = self.user.id
        self.photo_id = uuid.uuid4()
        self.tag_id = uuid.uuid4()
        self.tag = Tag.objects.create(tag_id=self.tag_id, user=self.user, tag="test")
        self.photo_tag = Photo_Tag.objects.create(
            user=self.user, tag=self.tag, photo_id=self.photo_id
        )

    @patch("gallery.tasks.get_qdrant_client")
    def test_retrieve_all_rep_vectors_of_tag_success(self, mock_get_client):
        mock_point1 = MagicMock()
        mock_point1.vector = [0.1] * 512

        mock_point2 = MagicMock()
        mock_point2.vector = [0.2] * 512

        mock_get_client.return_value.scroll.return_value = ([mock_point1, mock_point2], None)

        expected = [
            [0.1] * 512,
            [0.2] * 512,
        ]

        results = retrieve_all_rep_vectors_of_tag(self.user_id, self.tag_id)

        self.assertEqual(results, expected)

    @patch("gallery.tasks.get_qdrant_client")  # adjust patch path to where client is imported
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_basic(self, mock_retrieve, mock_get_client):
        """Test basic recommendation flow with single representative vector"""
        # Setup
        rep_vector = [0.1] * 512
        mock_retrieve.return_value = [rep_vector]

        first_uuid = uuid.uuid4()

        mock_points = [
            MagicMock(payload={"photo_id": first_uuid, "photo_path_id": 1}),
            MagicMock(payload={"photo_id": uuid.uuid4(), "photo_path_id": 2}),
            MagicMock(payload={"photo_id": uuid.uuid4(), "photo_path_id": 3}),
        ]
        mock_get_client.return_value.query_points.return_value = MagicMock(points=mock_points)

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        self.assertEqual(len(result), 3)
        self.assertEqual(result[0]["photo_id"], first_uuid)
        self.assertEqual(result[0]["photo_path_id"], 1)
        mock_retrieve.assert_called_once_with(self.user_id, self.tag_id)

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_filters_tagged_photos(self, mock_retrieve, mock_get_client):
        """Test that already tagged photos are filtered out"""
        # Setup
        rep_vector = [0.1] * 512
        mock_retrieve.return_value = [rep_vector]

        first_uuid = uuid.uuid4()
        third_uuid = uuid.uuid4()

        mock_points = [
            MagicMock(payload={"photo_id": first_uuid, "photo_path_id": 1}),
            MagicMock(payload={"photo_id": self.photo_id, "photo_path_id": 2}),
            MagicMock(payload={"photo_id": third_uuid, "photo_path_id": 3}),
        ]
        mock_get_client.return_value.query_points.return_value = MagicMock(points=mock_points)

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        self.assertEqual(len(result), 2)
        photo_ids = [r["photo_id"] for r in result]
        self.assertIn(first_uuid, photo_ids)
        self.assertIn(third_uuid, photo_ids)
        self.assertNotIn(self.photo_id, photo_ids)

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_rrf_with_multiple_vectors(
        self, mock_retrieve, mock_get_client
    ):
        """Test RRF scoring with multiple representative vectors"""
        # Setup
        rep_vectors = [[0.1] * 512, [0.2] * 512]
        mock_retrieve.return_value = rep_vectors

        uuids = [uuid.uuid4() for _ in range(4)]

        # First query returns photo1, photo2, photo3
        mock_points_1 = [
            MagicMock(payload={"photo_id": uuids[0], "photo_path_id": 1}),
            MagicMock(payload={"photo_id": uuids[1], "photo_path_id": 2}),
            MagicMock(payload={"photo_id": uuids[2], "photo_path_id": 3}),
        ]

        # Second query returns photo2, photo1, photo4
        mock_points_2 = [
            MagicMock(payload={"photo_id": uuids[1], "photo_path_id": 2}),
            MagicMock(payload={"photo_id": uuids[3], "photo_path_id": 4}),
            MagicMock(payload={"photo_id": uuids[0], "photo_path_id": 1}),
        ]

        mock_get_client.return_value.query_points.side_effect = [
            MagicMock(points=mock_points_1),
            MagicMock(points=mock_points_2),
        ]

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        # photo2 should rank highest (appears 2nd and 1st in each query)
        # photo1 should rank second (appears 1st and 3rd)
        self.assertEqual(result[0]["photo_id"], uuids[1])
        self.assertEqual(result[1]["photo_id"], uuids[0])

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_empty_rep_vectors(self, mock_retrieve, mock_get_client):
        """Test behavior when no representative vectors exist"""
        # Setup
        mock_retrieve.return_value = []

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        self.assertEqual(len(result), 0)
        mock_get_client.return_value.query_points.assert_not_called()

    @patch("gallery.tasks.Tag.objects.get")
    @patch("gallery.tasks.get_qdrant_client")
    def test_tag_recommendation_success(self, mock_get_client, mock_tag_get):
        mock_retrieve_point = models.Record(
            id=str(self.photo_id), payload={}, vector=[0.1] * 768
        )
        mock_get_client.return_value.retrieve.return_value = [mock_retrieve_point]

        mock_search_result_point = models.ScoredPoint(
            id=str(uuid.uuid4()),
            version=1,
            score=0.95,
            payload={"user_id": self.user_id, "tag_id": self.tag.tag_id},
            vector=None,
        )
        mock_get_client.return_value.search.return_value = [mock_search_result_point]

        mock_tag_get.return_value = self.tag

        tag_name, tag_id = tag_recommendation(self.user_id, self.photo_id)

        self.assertEqual(tag_name, "test")
        self.assertEqual(tag_id, self.tag.tag_id)

        mock_get_client.return_value.retrieve.assert_called_once_with(
            collection_name="my_image_collection",
            ids=[self.photo_id],
            with_vectors=True,
        )

        mock_get_client.return_value.search.assert_called_once()
        call_args, call_kwargs = mock_get_client.return_value.search.call_args
        self.assertEqual(call_kwargs["collection_name"], "my_repvec_collection")
        self.assertEqual(call_kwargs["limit"], 1)
        np.testing.assert_array_equal(
            call_kwargs["query_vector"], mock_retrieve_point.vector
        )

        mock_tag_get.assert_called_once_with(tag_id=self.tag.tag_id)

    @patch("gallery.tasks.get_qdrant_client")
    def test_tag_recommendation_no_similar_tag_found(self, mock_get_client):
        mock_retrieve_point = models.Record(
            id=str(self.photo_id), payload={}, vector=[0.1] * 768
        )
        mock_get_client.return_value.retrieve.return_value = [mock_retrieve_point]
        mock_get_client.return_value.search.return_value = []

        tag_name, tag_id = tag_recommendation(self.user_id, self.photo_id)

        self.assertIsNone(tag_name)
        self.assertIsNone(tag_id)