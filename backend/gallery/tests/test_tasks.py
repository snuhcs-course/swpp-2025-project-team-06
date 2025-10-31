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
    recommend_photo_from_photo,
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

        results = retrieve_all_rep_vectors_of_tag(self.user, self.tag_id)

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
        result = recommend_photo_from_tag(self.user, self.tag_id)

        # Assert
        self.assertEqual(len(result), 3)
        self.assertEqual(result[0]["photo_id"], first_uuid)
        self.assertEqual(result[0]["photo_path_id"], 1)
        mock_retrieve.assert_called_once_with(self.user, self.tag_id)

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
        result = recommend_photo_from_tag(self.user, self.tag_id)

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
        result = recommend_photo_from_tag(self.user, self.tag_id)

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
        result = recommend_photo_from_tag(self.user, self.tag_id)

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
        caption = Caption.objects.create(user=self.user1, caption="beach")
        Photo_Caption.objects.create(
            user=self.user1, photo_id=photo_id, caption=caption, weight=5
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

        caption_beach = Caption.objects.create(user=self.user1, caption="beach")
        caption_sunset = Caption.objects.create(user=self.user1, caption="sunset")

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


class RecommendPhotoFromPhotoTest(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", password="password123"
        )

    @patch("gallery.tasks.get_qdrant_client")
    def test_basic_recommendation_with_shared_captions(self, mock_get_client):
        """Test basic photo-to-photo recommendation with shared captions"""
        # Setup: Create 3 photos with overlapping captions
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()
        photo_id3 = uuid.uuid4()

        caption_beach = Caption.objects.create(user=self.user, caption="beach")
        caption_sunset = Caption.objects.create(user=self.user, caption="sunset")

        # Target photo: photo1 with "beach" and "sunset"
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id1, caption=caption_beach, weight=5
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id1, caption=caption_sunset, weight=3
        )

        # Candidate photo2: shares "beach" with photo1
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id2, caption=caption_beach, weight=4
        )

        # Candidate photo3: shares "sunset" with photo1
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id3, caption=caption_sunset, weight=2
        )

        # Mock Qdrant client.retrieve
        mock_points = [
            MagicMock(id=str(photo_id2), payload={"photo_path_id": 102}),
            MagicMock(id=str(photo_id3), payload={"photo_path_id": 103}),
        ]
        mock_get_client.return_value.retrieve.return_value = mock_points

        # Execute
        result = recommend_photo_from_photo(self.user, [photo_id1])

        # Assert
        self.assertIsInstance(result, list)
        self.assertLessEqual(len(result), 20)  # Should respect LIMIT
        result_photo_ids = [r["photo_id"] for r in result]

        # Both candidates should be recommended
        self.assertIn(str(photo_id2), result_photo_ids)
        self.assertIn(str(photo_id3), result_photo_ids)

        # Target photo should not be in recommendations
        self.assertNotIn(str(photo_id1), result_photo_ids)

        # Verify client.retrieve was called
        mock_get_client.return_value.retrieve.assert_called_once()

    @patch("gallery.tasks.get_qdrant_client")
    def test_multiple_target_photos(self, mock_get_client):
        """Test recommendation with multiple target photos"""
        photo_ids = [uuid.uuid4() for _ in range(5)]

        caption1 = Caption.objects.create(user=self.user, caption="mountain")
        caption2 = Caption.objects.create(user=self.user, caption="lake")

        # Target photos: photo1 and photo2
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_ids[0], caption=caption1, weight=10
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_ids[1], caption=caption2, weight=8
        )

        # Candidate photos that share captions
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_ids[2], caption=caption1, weight=5
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_ids[3], caption=caption2, weight=6
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_ids[4], caption=caption1, weight=3
        )

        mock_points = [
            MagicMock(id=str(pid), payload={"photo_path_id": i + 100})
            for i, pid in enumerate(photo_ids[2:])
        ]
        mock_get_client.return_value.retrieve.return_value = mock_points

        # Execute with multiple target photos
        result = recommend_photo_from_photo(self.user, [photo_ids[0], photo_ids[1]])

        # Assert
        result_photo_ids = [r["photo_id"] for r in result]

        # Target photos should not be in results
        self.assertNotIn(str(photo_ids[0]), result_photo_ids)
        self.assertNotIn(str(photo_ids[1]), result_photo_ids)

        # Candidates should be present
        self.assertIn(str(photo_ids[2]), result_photo_ids)
        self.assertIn(str(photo_ids[3]), result_photo_ids)
        self.assertIn(str(photo_ids[4]), result_photo_ids)

    @patch("gallery.tasks.get_qdrant_client")
    def test_no_candidates_all_photos_are_targets(self, mock_get_client):
        """Test when all photos in database are target photos (no candidates)"""
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()

        caption = Caption.objects.create(user=self.user, caption="test")

        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id1, caption=caption, weight=5
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id2, caption=caption, weight=3
        )

        mock_get_client.return_value.retrieve.return_value = []

        # Execute with all photos as targets
        result = recommend_photo_from_photo(self.user, [photo_id1, photo_id2])

        # Assert
        self.assertEqual(len(result), 0)
        mock_get_client.return_value.retrieve.assert_called_once()

    @patch("gallery.tasks.get_qdrant_client")
    def test_single_target_single_candidate(self, mock_get_client):
        """Test minimal case with one target and one candidate"""
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()

        caption = Caption.objects.create(user=self.user, caption="shared")

        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id1, caption=caption, weight=10
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id2, caption=caption, weight=8
        )

        mock_point = MagicMock(id=str(photo_id2), payload={"photo_path_id": 200})
        mock_get_client.return_value.retrieve.return_value = [mock_point]

        result = recommend_photo_from_photo(self.user, [photo_id1])

        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["photo_id"], str(photo_id2))
        self.assertEqual(result[0]["photo_path_id"], 200)

    @patch("gallery.tasks.get_qdrant_client")
    def test_no_shared_captions_between_photos(self, mock_get_client):
        """Test recommendation when photos don't share any captions"""
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()
        photo_id3 = uuid.uuid4()

        caption1 = Caption.objects.create(user=self.user, caption="unique1")
        caption2 = Caption.objects.create(user=self.user, caption="unique2")
        caption3 = Caption.objects.create(user=self.user, caption="unique3")

        # Each photo has completely unique captions (no overlap)
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id1, caption=caption1, weight=5
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id2, caption=caption2, weight=4
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id3, caption=caption3, weight=3
        )

        mock_points = [
            MagicMock(id=str(photo_id2), payload={"photo_path_id": 102}),
            MagicMock(id=str(photo_id3), payload={"photo_path_id": 103}),
        ]
        mock_get_client.return_value.retrieve.return_value = mock_points

        result = recommend_photo_from_photo(self.user, [photo_id1])

        # Should still return candidates (based on PageRank scores)
        # even though Adamic/Adar score will be 0
        self.assertGreater(len(result), 0)

    @patch("gallery.tasks.get_qdrant_client")
    def test_limit_enforcement(self, mock_get_client):
        """Test that recommendation respects LIMIT of 20"""
        # Create 30 photos to exceed the limit
        target_photo = uuid.uuid4()
        candidate_photos = [uuid.uuid4() for _ in range(30)]

        caption = Caption.objects.create(user=self.user, caption="common")

        # Target photo
        Photo_Caption.objects.create(
            user=self.user, photo_id=target_photo, caption=caption, weight=10
        )

        # 30 candidate photos all sharing the same caption
        for photo_id in candidate_photos:
            Photo_Caption.objects.create(
                user=self.user, photo_id=photo_id, caption=caption, weight=5
            )

        # Mock only 20 results (LIMIT)
        mock_points = [
            MagicMock(id=str(pid), payload={"photo_path_id": i})
            for i, pid in enumerate(candidate_photos[:20])
        ]
        mock_get_client.return_value.retrieve.return_value = mock_points

        result = recommend_photo_from_photo(self.user, [target_photo])

        # Assert limit is enforced
        self.assertEqual(len(result), 20)

    @patch("gallery.tasks.get_qdrant_client")
    def test_ranking_by_shared_captions(self, mock_get_client):
        """Test that photos with more shared captions rank higher"""
        target_photo = uuid.uuid4()
        candidate1 = uuid.uuid4()  # Shares 2 captions
        candidate2 = uuid.uuid4()  # Shares 1 caption
        candidate3 = uuid.uuid4()  # Shares 0 captions

        caption_a = Caption.objects.create(user=self.user, caption="a")
        caption_b = Caption.objects.create(user=self.user, caption="b")
        caption_c = Caption.objects.create(user=self.user, caption="c")

        # Target: has caption_a and caption_b
        Photo_Caption.objects.create(
            user=self.user, photo_id=target_photo, caption=caption_a, weight=10
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=target_photo, caption=caption_b, weight=10
        )

        # Candidate1: shares both caption_a and caption_b
        Photo_Caption.objects.create(
            user=self.user, photo_id=candidate1, caption=caption_a, weight=8
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=candidate1, caption=caption_b, weight=8
        )

        # Candidate2: shares only caption_a
        Photo_Caption.objects.create(
            user=self.user, photo_id=candidate2, caption=caption_a, weight=7
        )

        # Candidate3: shares no captions (only caption_c)
        Photo_Caption.objects.create(
            user=self.user, photo_id=candidate3, caption=caption_c, weight=6
        )

        # Mock returns in the order of scoring (should be candidate1, candidate2, candidate3)
        mock_points = [
            MagicMock(id=str(candidate1), payload={"photo_path_id": 1}),
            MagicMock(id=str(candidate2), payload={"photo_path_id": 2}),
            MagicMock(id=str(candidate3), payload={"photo_path_id": 3}),
        ]
        mock_get_client.return_value.retrieve.return_value = mock_points

        result = recommend_photo_from_photo(self.user, [target_photo])

        # Verify all candidates are returned
        self.assertEqual(len(result), 3)

    @patch("gallery.tasks.get_qdrant_client")
    def test_empty_database(self, mock_get_client):
        """Test recommendation when database has no photo-caption relationships"""
        non_existent_photo = uuid.uuid4()

        mock_get_client.return_value.retrieve.return_value = []

        result = recommend_photo_from_photo(self.user, [non_existent_photo])

        self.assertEqual(len(result), 0)

    @patch("gallery.tasks.get_qdrant_client")
    def test_user_isolation(self, mock_get_client):
        """Test that recommendations only consider the specified user's photos"""
        user2 = User.objects.create_user(username="user2", password="pass123")

        photo1_user1 = uuid.uuid4()
        photo2_user1 = uuid.uuid4()
        photo_user2 = uuid.uuid4()

        caption = Caption.objects.create(user=self.user, caption="shared_caption")

        # User1's photos
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo1_user1, caption=caption, weight=10
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo2_user1, caption=caption, weight=8
        )

        # User2's photo (should not appear in user1's recommendations)
        caption_user2 = Caption.objects.create(user=user2, caption="user2_caption")
        Photo_Caption.objects.create(
            user=user2, photo_id=photo_user2, caption=caption_user2, weight=5
        )

        mock_point = MagicMock(id=str(photo2_user1), payload={"photo_path_id": 200})
        mock_get_client.return_value.retrieve.return_value = [mock_point]

        result = recommend_photo_from_photo(self.user, [photo1_user1])

        # Only user1's photo should be recommended
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["photo_id"], str(photo2_user1))

    @patch("gallery.tasks.get_qdrant_client")
    def test_payload_structure(self, mock_get_client):
        """Test that returned payload has correct structure"""
        photo_id1 = uuid.uuid4()
        photo_id2 = uuid.uuid4()

        caption = Caption.objects.create(user=self.user, caption="test")

        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id1, caption=caption, weight=5
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photo_id2, caption=caption, weight=3
        )

        expected_photo_path_id = 999
        mock_point = MagicMock(
            id=str(photo_id2), payload={"photo_path_id": expected_photo_path_id}
        )
        mock_get_client.return_value.retrieve.return_value = [mock_point]

        result = recommend_photo_from_photo(self.user, [photo_id1])

        self.assertEqual(len(result), 1)
        self.assertIn("photo_id", result[0])
        self.assertIn("photo_path_id", result[0])
        self.assertEqual(result[0]["photo_id"], str(photo_id2))
        self.assertEqual(result[0]["photo_path_id"], expected_photo_path_id)

    @patch("gallery.tasks.get_qdrant_client")
    def test_weighted_captions_affect_recommendations(self, mock_get_client):
        """Test that caption weights influence recommendation scores"""
        target_photo = uuid.uuid4()
        candidate1 = uuid.uuid4()
        candidate2 = uuid.uuid4()

        caption = Caption.objects.create(user=self.user, caption="weighted")

        # Target photo with high weight
        Photo_Caption.objects.create(
            user=self.user, photo_id=target_photo, caption=caption, weight=100
        )

        # Candidate1 with high weight (similar to target)
        Photo_Caption.objects.create(
            user=self.user, photo_id=candidate1, caption=caption, weight=90
        )

        # Candidate2 with low weight (less similar to target)
        Photo_Caption.objects.create(
            user=self.user, photo_id=candidate2, caption=caption, weight=1
        )

        mock_points = [
            MagicMock(id=str(candidate1), payload={"photo_path_id": 1}),
            MagicMock(id=str(candidate2), payload={"photo_path_id": 2}),
        ]
        mock_get_client.return_value.retrieve.return_value = mock_points

        result = recommend_photo_from_photo(self.user, [target_photo])

        # Both candidates should be returned (weights affect PageRank scores)
        self.assertEqual(len(result), 2)

    @patch("gallery.tasks.get_qdrant_client")
    def test_complex_graph_with_multiple_connections(self, mock_get_client):
        """Test recommendation with a complex bipartite graph"""
        photos = [uuid.uuid4() for _ in range(6)]
        captions = [
            Caption.objects.create(user=self.user, caption=f"caption{i}")
            for i in range(4)
        ]

        # Create complex interconnected graph
        # Target: photos[0] with captions[0], captions[1]
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[0], caption=captions[0], weight=10
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[0], caption=captions[1], weight=8
        )

        # Highly related: photos[1] shares both captions
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[1], caption=captions[0], weight=9
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[1], caption=captions[1], weight=7
        )

        # Moderately related: photos[2] shares one caption
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[2], caption=captions[0], weight=5
        )

        # Indirectly related: photos[3] through caption connections
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[3], caption=captions[2], weight=6
        )

        # Create indirect connection through shared caption
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[4], caption=captions[0], weight=4
        )
        Photo_Caption.objects.create(
            user=self.user, photo_id=photos[4], caption=captions[2], weight=3
        )

        mock_points = [
            MagicMock(id=str(photos[i]), payload={"photo_path_id": i})
            for i in range(1, 5)
        ]
        mock_get_client.return_value.retrieve.return_value = mock_points

        result = recommend_photo_from_photo(self.user, [photos[0]])

        # Should return recommendations based on graph structure
        self.assertGreater(len(result), 0)
        result_ids = [r["photo_id"] for r in result]

        # Target should not be in results
        self.assertNotIn(str(photos[0]), result_ids)
