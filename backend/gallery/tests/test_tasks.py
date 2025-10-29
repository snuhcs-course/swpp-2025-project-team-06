import uuid

import numpy as np
from unittest.mock import patch, MagicMock
from django.test import TestCase
from django.contrib.auth.models import User
from qdrant_client.http import models
import networkx as nx

from gallery.models import Tag, Photo_Tag, Caption, Photo_Caption
from gallery.tasks import (
    retrieve_all_rep_vectors_of_tag,
    recommend_photo_from_tag,
    tag_recommendation,
    retrieve_photo_caption_graph,
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

    @patch("gallery.tasks.client")
    def test_retrieve_all_rep_vectors_of_tag_success(self, mock_client):
        mock_point1 = MagicMock()
        mock_point1.vector = [0.1] * 512

        mock_point2 = MagicMock()
        mock_point2.vector = [0.2] * 512

        mock_client.scroll.return_value = ([mock_point1, mock_point2], None)

        expected = [
            [0.1] * 512,
            [0.2] * 512,
        ]

        results = retrieve_all_rep_vectors_of_tag(self.user, self.tag_id)

        self.assertEqual(results, expected)

    @patch("gallery.tasks.client")  # adjust patch path to where client is imported
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_basic(self, mock_retrieve, mock_client):
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
        mock_client.query_points.return_value = MagicMock(points=mock_points)

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        self.assertEqual(len(result), 3)
        self.assertEqual(result[0]["photo_id"], first_uuid)
        self.assertEqual(result[0]["photo_path_id"], 1)
        mock_client.query_points.assert_called_once()
        mock_retrieve.assert_called_once_with(self.user_id, self.tag_id)

    @patch("gallery.tasks.client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_filters_tagged_photos(self, mock_retrieve, mock_client):
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
        mock_client.query_points.return_value = MagicMock(points=mock_points)

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        self.assertEqual(len(result), 2)
        photo_ids = [r["photo_id"] for r in result]
        self.assertIn(first_uuid, photo_ids)
        self.assertIn(third_uuid, photo_ids)
        self.assertNotIn(self.photo_id, photo_ids)

    @patch("gallery.tasks.client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_rrf_with_multiple_vectors(
        self, mock_retrieve, mock_client
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

        mock_client.query_points.side_effect = [
            MagicMock(points=mock_points_1),
            MagicMock(points=mock_points_2),
        ]

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        self.assertEqual(mock_client.query_points.call_count, 2)
        # photo2 should rank highest (appears 2nd and 1st in each query)
        # photo1 should rank second (appears 1st and 3rd)
        self.assertEqual(result[0]["photo_id"], uuids[1])
        self.assertEqual(result[1]["photo_id"], uuids[0])

    @patch("gallery.tasks.client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_empty_rep_vectors(self, mock_retrieve, mock_client):
        """Test behavior when no representative vectors exist"""
        # Setup
        mock_retrieve.return_value = []

        # Execute
        result = recommend_photo_from_tag(self.user_id, self.tag_id)

        # Assert
        self.assertEqual(len(result), 0)
        mock_client.query_points.assert_not_called()

    @patch("gallery.tasks.Tag.objects.get")
    @patch("gallery.tasks.client")
    def test_tag_recommendation_success(self, mock_client, mock_tag_get):
        mock_retrieve_point = models.Record(
            id=str(self.photo_id), payload={}, vector=[0.1] * 768
        )
        mock_client.retrieve.return_value = [mock_retrieve_point]

        mock_search_result_point = models.ScoredPoint(
            id=str(uuid.uuid4()),
            version=1,
            score=0.95,
            payload={"user_id": self.user_id, "tag_id": self.tag.tag_id},
            vector=None,
        )
        mock_client.search.return_value = [mock_search_result_point]

        mock_tag_get.return_value = self.tag

        tag_name, tag_id = tag_recommendation(self.user_id, self.photo_id)

        self.assertEqual(tag_name, "test")
        self.assertEqual(tag_id, self.tag.tag_id)

        mock_client.retrieve.assert_called_once_with(
            collection_name="my_image_collection",
            ids=[self.photo_id],
            with_vectors=True,
        )

        mock_client.search.assert_called_once()
        call_args, call_kwargs = mock_client.search.call_args
        self.assertEqual(call_kwargs["collection_name"], "my_repvec_collection")
        self.assertEqual(call_kwargs["limit"], 1)
        np.testing.assert_array_equal(
            call_kwargs["query_vector"], mock_retrieve_point.vector
        )

        mock_tag_get.assert_called_once_with(tag_id=self.tag.tag_id)

    @patch("gallery.tasks.client")
    def test_tag_recommendation_no_similar_tag_found(self, mock_client):
        mock_retrieve_point = models.Record(
            id=str(self.photo_id), payload={}, vector=[0.1] * 768
        )
        mock_client.retrieve.return_value = [mock_retrieve_point]
        mock_client.search.return_value = []

        tag_name, tag_id = tag_recommendation(self.user_id, self.photo_id)

        self.assertIsNone(tag_name)
        self.assertIsNone(tag_id)


class RetrievePhotoCaptionGraphTest(TestCase):
    def setUp(self):
        self.user1 = User.objects.create_user(
            username="testuser1", password="password123"
        )
        self.user2 = User.objects.create_user(
            username="testuser2", password="password123"
        )

    def test_empty_graph_no_photo_captions(self):
        """Test that empty graph is returned when user has no photo captions"""
        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user1)

        self.assertEqual(len(photo_set), 0)
        self.assertEqual(len(caption_set), 0)
        self.assertEqual(graph.number_of_nodes(), 0)
        self.assertEqual(graph.number_of_edges(), 0)

    def test_single_photo_single_caption(self):
        """Test graph with one photo and one caption"""
        photo_id = uuid.uuid4()
        caption = Caption.objects.create(
            user=self.user1, caption="beach"
        )
        Photo_Caption.objects.create(
            user=self.user1,
            photo_id=photo_id,
            caption=caption,
            weight=5
        )

        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user1)

        # Verify sets
        self.assertEqual(len(photo_set), 1)
        self.assertEqual(len(caption_set), 1)
        self.assertIn(photo_id, photo_set)
        self.assertIn(caption.caption_id, caption_set)

        # Verify graph structure
        self.assertEqual(graph.number_of_nodes(), 2)
        self.assertEqual(graph.number_of_edges(), 1)

        # Verify bipartite attributes
        self.assertEqual(graph.nodes[photo_id]["bipartite"], 0)
        self.assertEqual(graph.nodes[caption]["bipartite"], 1)

        # Verify edge weight
        self.assertTrue(graph.has_edge(photo_id, caption))
        self.assertEqual(graph[photo_id][caption]["weight"], 5)

    def test_multiple_photos_shared_captions(self):
        """Test graph where multiple photos share the same captions"""
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()
        photo_id3 = uuid.uuid4()

        caption_beach = Caption.objects.create(
            user=self.user1, caption="beach"
        )
        caption_sunset = Caption.objects.create(
            user=self.user1, caption="sunset"
        )

        # Photo 1: beach (weight=3), sunset (weight=2)
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id1, caption=caption_beach, weight=3
        )
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id1, caption=caption_sunset, weight=2
        )

        # Photo 2: beach (weight=5)
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id2, caption=caption_beach, weight=5
        )

        # Photo 3: sunset (weight=1)
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id3, caption=caption_sunset, weight=1
        )

        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user1)

        # Verify sets
        self.assertEqual(len(photo_set), 3)
        self.assertEqual(len(caption_set), 2)
        self.assertIn(photo_id1, photo_set)
        self.assertIn(photo_id2, photo_set)
        self.assertIn(photo_id3, photo_set)

        # Verify graph structure: 3 photos + 2 captions = 5 nodes, 4 edges
        self.assertEqual(graph.number_of_nodes(), 5)
        self.assertEqual(graph.number_of_edges(), 4)

        # Verify bipartite structure
        for photo_id in [photo_id1, photo_id2, photo_id3]:
            self.assertEqual(graph.nodes[photo_id]["bipartite"], 0)
        for caption in [caption_beach, caption_sunset]:
            self.assertEqual(graph.nodes[caption]["bipartite"], 1)

        # Verify specific edges and weights
        self.assertEqual(graph[photo_id1][caption_beach]["weight"], 3)
        self.assertEqual(graph[photo_id1][caption_sunset]["weight"], 2)
        self.assertEqual(graph[photo_id2][caption_beach]["weight"], 5)
        self.assertEqual(graph[photo_id3][caption_sunset]["weight"], 1)

    def test_multiple_photos_unique_captions(self):
        """Test graph where each photo has unique captions"""
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()

        caption1 = Caption.objects.create(user=self.user1, caption="mountain")
        caption2 = Caption.objects.create(user=self.user1, caption="lake")

        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id1, caption=caption1, weight=10
        )
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id2, caption=caption2, weight=8
        )

        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user1)

        # Verify sets
        self.assertEqual(len(photo_set), 2)
        self.assertEqual(len(caption_set), 2)

        # Verify graph structure
        self.assertEqual(graph.number_of_nodes(), 4)
        self.assertEqual(graph.number_of_edges(), 2)

        # Verify no connection between photo1 and photo2 (they don't share captions)
        self.assertFalse(graph.has_edge(photo_id1, photo_id2))

    def test_user_isolation(self):
        """Test that graph only includes data for the specified user"""
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()

        caption1 = Caption.objects.create(user=self.user1, caption="user1_caption")
        caption2 = Caption.objects.create(user=self.user2, caption="user2_caption")

        # Create photo caption for user1
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id1, caption=caption1, weight=5
        )

        # Create photo caption for user2
        Photo_Caption.objects.create(
            user=self.user2, photo_id=photo_id2, caption=caption2, weight=3
        )

        # Retrieve graph for user1
        photo_set1, caption_set1, graph1 = retrieve_photo_caption_graph(self.user1)

        # Verify only user1's data is present
        self.assertEqual(len(photo_set1), 1)
        self.assertEqual(len(caption_set1), 1)
        self.assertIn(photo_id1, photo_set1)
        self.assertNotIn(photo_id2, photo_set1)
        self.assertIn(caption1.caption_id, caption_set1)
        self.assertNotIn(caption2.caption_id, caption_set1)

        # Retrieve graph for user2
        photo_set2, caption_set2, graph2 = retrieve_photo_caption_graph(self.user2)

        # Verify only user2's data is present
        self.assertEqual(len(photo_set2), 1)
        self.assertEqual(len(caption_set2), 1)
        self.assertIn(photo_id2, photo_set2)
        self.assertNotIn(photo_id1, photo_set2)

    def test_complex_bipartite_graph_structure(self):
        """Test complex graph with multiple photos and overlapping captions"""
        photo_ids = [uuid.uuid4() for _ in range(5)]
        captions = [
            Caption.objects.create(user=self.user1, caption=f"caption{i}")
            for i in range(3)
        ]

        # Create a complex bipartite structure
        # Photo 0 -> caption 0, caption 1
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[0], caption=captions[0], weight=1
        )
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[0], caption=captions[1], weight=2
        )

        # Photo 1 -> caption 1, caption 2
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[1], caption=captions[1], weight=3
        )
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[1], caption=captions[2], weight=4
        )

        # Photo 2 -> caption 0, caption 2
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[2], caption=captions[0], weight=5
        )
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[2], caption=captions[2], weight=6
        )

        # Photo 3 -> caption 1
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[3], caption=captions[1], weight=7
        )

        # Photo 4 -> caption 0
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_ids[4], caption=captions[0], weight=8
        )

        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user1)

        # Verify basic structure
        self.assertEqual(len(photo_set), 5)
        self.assertEqual(len(caption_set), 3)
        self.assertEqual(graph.number_of_nodes(), 8)
        # 2 + 2 + 2 + 1 + 1 = 8 edges total
        self.assertEqual(graph.number_of_edges(), 8)

        # Verify bipartite property
        self.assertTrue(nx.is_bipartite(graph))

        # Verify all photos are in bipartite set 0
        photo_nodes = {n for n, d in graph.nodes(data=True) if d["bipartite"] == 0}
        self.assertEqual(photo_nodes, photo_set)

        # Verify all captions are in bipartite set 1
        caption_nodes = {n for n, d in graph.nodes(data=True) if d["bipartite"] == 1}
        self.assertEqual(len(caption_nodes), 3)

        # Verify no edges between photos (bipartite property)
        for i in range(len(photo_ids)):
            for j in range(i + 1, len(photo_ids)):
                self.assertFalse(graph.has_edge(photo_ids[i], photo_ids[j]))

        # Verify no edges between captions (bipartite property)
        for i in range(len(captions)):
            for j in range(i + 1, len(captions)):
                self.assertFalse(graph.has_edge(captions[i], captions[j]))

    def test_weight_preservation(self):
        """Test that edge weights are correctly preserved"""
        photo_id = uuid.uuid4()
        caption = Caption.objects.create(user=self.user1, caption="test")

        # Create with specific weight
        weight_value = 42
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id, caption=caption, weight=weight_value
        )

        _, _, graph = retrieve_photo_caption_graph(self.user1)

        # Verify weight is preserved
        self.assertEqual(graph[photo_id][caption]["weight"], weight_value)

    def test_zero_weight(self):
        """Test that zero weight edges are handled correctly"""
        photo_id = uuid.uuid4()
        caption = Caption.objects.create(user=self.user1, caption="zero_weight")

        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id, caption=caption, weight=0
        )

        _, _, graph = retrieve_photo_caption_graph(self.user1)

        # Verify edge exists with zero weight
        self.assertTrue(graph.has_edge(photo_id, caption))
        self.assertEqual(graph[photo_id][caption]["weight"], 0)

    def test_duplicate_photo_caption_pairs(self):
        """Test that duplicate photo-caption pairs don't create duplicate nodes"""
        photo_id = uuid.uuid4()
        caption = Caption.objects.create(user=self.user1, caption="duplicate_test")

        # Create first photo-caption relationship
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id, caption=caption, weight=5
        )

        # Create another photo-caption with same photo_id but different caption
        caption2 = Caption.objects.create(user=self.user1, caption="another_caption")
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id, caption=caption2, weight=3
        )

        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user1)

        # Verify photo_id appears only once in photo_set
        self.assertEqual(len(photo_set), 1)
        self.assertEqual(len(caption_set), 2)
        self.assertEqual(graph.number_of_nodes(), 3)  # 1 photo + 2 captions
        self.assertEqual(graph.number_of_edges(), 2)
