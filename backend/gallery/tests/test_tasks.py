"""
Tests for gallery/tasks.py

All external dependencies (Qdrant, Redis, Celery) are mocked.
"""

import uuid
import json
from datetime import datetime
from unittest.mock import MagicMock, patch, call
from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
import numpy as np

from ..models import Tag, Photo, Photo_Tag, Photo_Caption, Caption
from ..tasks import (
    recommend_photo_from_tag,
    recommend_photo_from_photo,
    tag_recommendation,
    tag_recommendation_batch,
    retrieve_all_rep_vectors_of_tag,
    retrieve_photo_caption_graph,
    execute_hybrid_search,
    is_valid_uuid,
    generate_stories_task,
    compute_and_store_rep_vectors,
)


class RecommendPhotoFromTagTest(TestCase):
    """recommend_photo_from_tag 함수 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
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

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_from_tag_with_results(
        self, mock_retrieve_rep, mock_get_client
    ):
        """태그 기반 사진 추천 - 정상 결과"""
        # Mock rep vectors
        mock_retrieve_rep.return_value = [[0.1, 0.2, 0.3]]

        # Mock Qdrant client
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        # Mock recommend results
        mock_point1 = MagicMock()
        mock_point1.id = str(self.photo1.photo_id)
        mock_point2 = MagicMock()
        mock_point2.id = str(self.photo2.photo_id)

        mock_client.recommend.return_value = [mock_point1, mock_point2]

        # Execute
        results = recommend_photo_from_tag(self.user, self.tag.tag_id)

        # Verify
        self.assertEqual(len(results), 2)
        self.assertEqual(results[0]["photo_id"], str(self.photo1.photo_id))
        self.assertEqual(results[0]["photo_path_id"], 101)
        mock_client.recommend.assert_called_once()

    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_from_tag_no_rep_vectors(self, mock_retrieve_rep):
        """태그에 rep vector가 없는 경우"""
        mock_retrieve_rep.return_value = []

        results = recommend_photo_from_tag(self.user, self.tag.tag_id)

        self.assertEqual(results, [])
        mock_retrieve_rep.assert_called_once_with(self.user, self.tag.tag_id)

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.retrieve_all_rep_vectors_of_tag")
    def test_recommend_photo_from_tag_excludes_tagged_photos(
        self, mock_retrieve_rep, mock_get_client
    ):
        """이미 태그된 사진은 제외"""
        # Tag photo1
        Photo_Tag.objects.create(user=self.user, photo=self.photo1, tag=self.tag)

        mock_retrieve_rep.return_value = [[0.1, 0.2, 0.3]]

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        mock_point1 = MagicMock()
        mock_point1.id = str(self.photo1.photo_id)
        mock_point2 = MagicMock()
        mock_point2.id = str(self.photo2.photo_id)

        mock_client.recommend.return_value = [mock_point1, mock_point2]

        results = recommend_photo_from_tag(self.user, self.tag.tag_id)

        # photo1은 제외되고 photo2만 반환
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["photo_id"], str(self.photo2.photo_id))


class RecommendPhotoFromPhotoTest(TestCase):
    """recommend_photo_from_photo 함수 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=201,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=202,
            created_at=timezone.now(),
        )

    def test_recommend_photo_from_photo_empty_input(self):
        """빈 사진 리스트 입력"""
        results = recommend_photo_from_photo(self.user, [])
        self.assertEqual(results, [])

    @patch("gallery.tasks.get_qdrant_client")
    def test_recommend_photo_from_photo_with_results(self, mock_get_client):
        """사진 기반 추천 - 정상 결과"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        mock_point = MagicMock()
        mock_point.id = str(self.photo2.photo_id)
        mock_client.recommend.return_value = [mock_point]

        results = recommend_photo_from_photo(self.user, [self.photo1.photo_id])

        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["photo_id"], str(self.photo2.photo_id))
        self.assertEqual(results[0]["photo_path_id"], 202)


class TagRecommendationTest(TestCase):
    """tag_recommendation 함수 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.photo = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=301,
            created_at=timezone.now(),
        )
        self.tag1 = Tag.objects.create(tag="태그1", user=self.user)
        self.tag2 = Tag.objects.create(tag="태그2", user=self.user)

    @patch("gallery.tasks.get_qdrant_client")
    def test_tag_recommendation_success(self, mock_get_client):
        """태그 추천 성공"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        # Mock retrieve
        mock_retrieved_point = MagicMock()
        mock_retrieved_point.vector = [0.1, 0.2, 0.3]
        mock_client.retrieve.return_value = [mock_retrieved_point]

        # Mock search
        mock_search_result1 = MagicMock()
        mock_search_result1.payload = {"tag_id": str(self.tag1.tag_id)}
        mock_search_result2 = MagicMock()
        mock_search_result2.payload = {"tag_id": str(self.tag2.tag_id)}

        mock_client.search.return_value = [mock_search_result1, mock_search_result2]

        results = tag_recommendation(self.user, self.photo.photo_id)

        self.assertEqual(len(results), 2)
        self.assertIn(self.tag1, results)
        self.assertIn(self.tag2, results)

    @patch("gallery.tasks.get_qdrant_client")
    def test_tag_recommendation_no_image_found(self, mock_get_client):
        """이미지를 찾을 수 없는 경우"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client
        mock_client.retrieve.return_value = []

        with self.assertRaises(ValueError) as context:
            tag_recommendation(self.user, self.photo.photo_id)

        self.assertIn("not found in collection", str(context.exception))

    @patch("gallery.tasks.get_qdrant_client")
    def test_tag_recommendation_with_nonexistent_tags(self, mock_get_client):
        """존재하지 않는 태그 ID 처리"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        mock_retrieved_point = MagicMock()
        mock_retrieved_point.vector = [0.1, 0.2, 0.3]
        mock_client.retrieve.return_value = [mock_retrieved_point]

        # 존재하지 않는 tag_id
        mock_search_result = MagicMock()
        mock_search_result.payload = {"tag_id": str(uuid.uuid4())}
        mock_client.search.return_value = [mock_search_result]

        results = tag_recommendation(self.user, self.photo.photo_id)

        # 존재하지 않는 태그는 무시됨
        self.assertEqual(len(results), 0)


class TagRecommendationBatchTest(TestCase):
    """tag_recommendation_batch 함수 테스트 (통합)"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=401,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=402,
            created_at=timezone.now(),
        )

    def test_tag_recommendation_batch_empty_input(self):
        """빈 입력 처리"""
        results = tag_recommendation_batch(self.user, [])
        self.assertEqual(results, {})

    @patch("gallery.tasks.get_qdrant_client")
    def test_tag_recommendation_batch_with_results(self, mock_get_client):
        """배치 태그 추천 성공 (preset + user tags)"""
        # Create tags
        user_tag1 = Tag.objects.create(tag="사용자태그1", user=self.user)
        user_tag2 = Tag.objects.create(tag="사용자태그2", user=self.user)

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        # Mock batch retrieve (이미지 벡터 조회)
        mock_point1 = MagicMock()
        mock_point1.id = str(self.photo1.photo_id)
        mock_point1.vector = [0.1, 0.2, 0.3]
        
        mock_point2 = MagicMock()
        mock_point2.id = str(self.photo2.photo_id)
        mock_point2.vector = [0.4, 0.5, 0.6]
        
        mock_client.retrieve.return_value = [mock_point1, mock_point2]

        # Mock search results (preset + user tags)
        # photo1에 대한 preset 검색 결과
        mock_preset1 = MagicMock()
        mock_preset1.payload = {"name": "프리셋태그1"}
        
        mock_preset2 = MagicMock()
        mock_preset2.payload = {"name": "프리셋태그2"}

        # photo1에 대한 user tag 검색 결과
        mock_user_tag1 = MagicMock()
        mock_user_tag1.payload = {"tag_id": str(user_tag1.tag_id)}
        
        mock_user_tag2 = MagicMock()
        mock_user_tag2.payload = {"tag_id": str(user_tag2.tag_id)}

        # photo2에 대한 검색 결과
        mock_preset3 = MagicMock()
        mock_preset3.payload = {"name": "프리셋태그3"}
        
        mock_user_tag3 = MagicMock()
        mock_user_tag3.payload = {"tag_id": str(user_tag1.tag_id)}

        # search 호출 순서: photo1 preset, photo1 user, photo2 preset, photo2 user
        mock_client.search.side_effect = [
            [mock_preset1, mock_preset2],  # photo1 preset
            [mock_user_tag1, mock_user_tag2],  # photo1 user tags
            [mock_preset3],  # photo2 preset
            [mock_user_tag3],  # photo2 user tags
        ]

        results = tag_recommendation_batch(
            self.user, [str(self.photo1.photo_id), str(self.photo2.photo_id)]
        )

        # 결과 검증
        self.assertEqual(len(results), 2)
        self.assertIn(str(self.photo1.photo_id), results)
        self.assertIn(str(self.photo2.photo_id), results)
        
        # photo1 결과: preset 2개 + user tag 2개
        photo1_tags = results[str(self.photo1.photo_id)]
        self.assertIn("프리셋태그1", photo1_tags)
        self.assertIn("프리셋태그2", photo1_tags)
        self.assertIn("사용자태그1", photo1_tags)
        self.assertIn("사용자태그2", photo1_tags)
        
        # photo2 결과: preset 1개 + user tag 1개
        photo2_tags = results[str(self.photo2.photo_id)]
        self.assertIn("프리셋태그3", photo2_tags)
        self.assertIn("사용자태그1", photo2_tags)

        # Mock 호출 검증
        mock_client.retrieve.assert_called_once()
        self.assertEqual(mock_client.search.call_count, 4)  # 2 photos * 2 searches each


class RetrieveAllRepVectorsOfTagTest(TestCase):
    """retrieve_all_rep_vectors_of_tag 함수 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.tag = Tag.objects.create(tag="벡터태그", user=self.user)

    @patch("gallery.tasks.get_qdrant_client")
    def test_retrieve_all_rep_vectors_success(self, mock_get_client):
        """rep vector 조회 성공"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        mock_point1 = MagicMock()
        mock_point1.vector = [0.1, 0.2, 0.3]
        mock_point2 = MagicMock()
        mock_point2.vector = [0.4, 0.5, 0.6]

        mock_client.scroll.return_value = ([mock_point1, mock_point2], None)

        results = retrieve_all_rep_vectors_of_tag(self.user, self.tag.tag_id)

        self.assertEqual(len(results), 2)
        self.assertEqual(results[0], [0.1, 0.2, 0.3])
        self.assertEqual(results[1], [0.4, 0.5, 0.6])

    @patch("gallery.tasks.get_qdrant_client")
    def test_retrieve_all_rep_vectors_empty(self, mock_get_client):
        """rep vector가 없는 경우"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client
        mock_client.scroll.return_value = ([], None)

        results = retrieve_all_rep_vectors_of_tag(self.user, self.tag.tag_id)

        self.assertEqual(results, [])


class RetrievePhotoCaptionGraphTest(TestCase):
    """retrieve_photo_caption_graph 함수 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=501,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=502,
            created_at=timezone.now(),
        )
        self.caption1 = Caption.objects.create(user=self.user, caption="캡션1")
        self.caption2 = Caption.objects.create(user=self.user, caption="캡션2")

    def test_retrieve_photo_caption_graph_empty(self):
        """사진-캡션 관계가 없는 경우"""
        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user)

        self.assertEqual(len(photo_set), 0)
        self.assertEqual(len(caption_set), 0)
        self.assertEqual(len(graph.nodes), 0)

    def test_retrieve_photo_caption_graph_with_data(self):
        """사진-캡션 그래프 생성"""
        Photo_Caption.objects.create(
            user=self.user, photo=self.photo1, caption=self.caption1, weight=1
        )
        Photo_Caption.objects.create(
            user=self.user, photo=self.photo1, caption=self.caption2, weight=2
        )
        Photo_Caption.objects.create(
            user=self.user, photo=self.photo2, caption=self.caption1, weight=3
        )

        photo_set, caption_set, graph = retrieve_photo_caption_graph(self.user)

        self.assertEqual(len(photo_set), 2)
        self.assertEqual(len(caption_set), 2)
        self.assertIn(self.photo1.photo_id, photo_set)
        self.assertIn(self.photo2.photo_id, photo_set)
        self.assertIn(self.caption1.caption_id, caption_set)
        self.assertIn(self.caption2.caption_id, caption_set)

        # 간선(edge) 확인
        self.assertTrue(graph.has_edge(self.photo1.photo_id, self.caption1.caption_id))
        self.assertEqual(
            graph[self.photo1.photo_id][self.caption1.caption_id]["weight"], 1
        )
        self.assertEqual(
            graph[self.photo1.photo_id][self.caption2.caption_id]["weight"], 2
        )
        self.assertEqual(
            graph[self.photo2.photo_id][self.caption1.caption_id]["weight"], 3
        )


class ExecuteHybridSearchTest(TestCase):
    """execute_hybrid_search 함수 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.tag = Tag.objects.create(tag="검색태그", user=self.user)
        self.photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=601,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=602,
            created_at=timezone.now(),
        )

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.phrase_to_words")
    @patch("search.embedding_service.create_query_embedding")
    def test_execute_hybrid_search_tag_only(
        self, mock_create_embedding, mock_phrase_to_words, mock_get_client
    ):
        """태그만 사용한 검색"""
        Photo_Tag.objects.create(user=self.user, photo=self.photo1, tag=self.tag)

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        mock_recommend_result = MagicMock()
        mock_recommend_result.id = str(self.photo2.photo_id)
        mock_recommend_result.score = 0.8
        mock_client.recommend.return_value = [mock_recommend_result]

        results = execute_hybrid_search(
            user=self.user, tag_ids=[self.tag.tag_id], query_string=""
        )

        # photo1 (태그 직접 연결, score=1.0) + photo2 (추천, score=0.8)
        self.assertEqual(len(results), 2)
        photo_ids = [r["photo_id"] for r in results]
        self.assertIn(str(self.photo1.photo_id), photo_ids)
        self.assertIn(str(self.photo2.photo_id), photo_ids)

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.phrase_to_words")
    @patch("search.embedding_service.create_query_embedding")
    def test_execute_hybrid_search_query_only(
        self, mock_create_embedding, mock_phrase_to_words, mock_get_client
    ):
        """쿼리 문자열만 사용한 검색"""
        mock_create_embedding.return_value = [0.1, 0.2, 0.3]

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        mock_search_result = MagicMock()
        mock_search_result.id = str(self.photo1.photo_id)
        mock_search_result.score = 0.9
        mock_client.search.return_value = [mock_search_result]

        mock_phrase_to_words.return_value = ["테스트"]

        results = execute_hybrid_search(
            user=self.user, tag_ids=[], query_string="테스트 쿼리"
        )

        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["photo_id"], str(self.photo1.photo_id))

    @patch("gallery.tasks.get_qdrant_client")
    @patch("gallery.tasks.phrase_to_words")
    @patch("search.embedding_service.create_query_embedding")
    def test_execute_hybrid_search_no_results(
        self, mock_create_embedding, mock_phrase_to_words, mock_get_client
    ):
        """검색 결과가 없는 경우"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client
        mock_client.recommend.return_value = []
        mock_client.search.return_value = []

        results = execute_hybrid_search(
            user=self.user, tag_ids=[], query_string=""
        )

        self.assertEqual(results, [])


class IsValidUuidTest(TestCase):
    """is_valid_uuid 함수 테스트"""

    def test_is_valid_uuid_with_valid_uuid(self):
        """유효한 UUID"""
        valid_uuid = uuid.uuid4()
        self.assertTrue(is_valid_uuid(valid_uuid))
        self.assertTrue(is_valid_uuid(str(valid_uuid)))

    def test_is_valid_uuid_with_invalid_uuid(self):
        """잘못된 UUID"""
        self.assertFalse(is_valid_uuid("not-a-uuid"))
        self.assertFalse(is_valid_uuid("12345"))
        self.assertFalse(is_valid_uuid(""))


class GenerateStoriesTaskTest(TestCase):
    """generate_stories_task Celery 작업 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        # 태그 없는 사진 생성
        self.photo1 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=701,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=uuid.uuid4(),
            user=self.user,
            photo_path_id=702,
            created_at=timezone.now(),
        )

    @patch("config.redis.get_redis")
    @patch("gallery.tasks.tag_recommendation_batch")
    def test_generate_stories_task_success(self, mock_tag_batch, mock_get_redis):
        """스토리 생성 작업 성공"""
        mock_tag_batch.return_value = {
            str(self.photo1.photo_id): ["태그1", "태그2"],
            str(self.photo2.photo_id): ["태그3"],
        }

        mock_redis = MagicMock()
        mock_get_redis.return_value = mock_redis

        generate_stories_task(self.user.id, 10)

        # Redis에 저장 확인
        mock_redis.set.assert_called_once()
        call_args = mock_redis.set.call_args
        saved_data = json.loads(call_args[0][1])

        self.assertEqual(len(saved_data), 2)
        self.assertIn("photo_id", saved_data[0])
        self.assertIn("photo_path_id", saved_data[0])
        self.assertIn("tags", saved_data[0])

    @patch("config.redis.get_redis")
    @patch("gallery.tasks.tag_recommendation_batch")
    def test_generate_stories_task_no_photos(self, mock_tag_batch, mock_get_redis):
        """태그 없는 사진이 없는 경우"""
        # 모든 사진에 태그 추가
        tag = Tag.objects.create(tag="태그", user=self.user)
        Photo_Tag.objects.create(user=self.user, photo=self.photo1, tag=tag)
        Photo_Tag.objects.create(user=self.user, photo=self.photo2, tag=tag)

        mock_tag_batch.return_value = {}
        mock_redis = MagicMock()
        mock_get_redis.return_value = mock_redis

        generate_stories_task(self.user.id, 10)

        # 빈 리스트가 저장되어야 함
        call_args = mock_redis.set.call_args
        saved_data = json.loads(call_args[0][1])
        self.assertEqual(len(saved_data), 0)

    @patch("config.redis.get_redis")
    @patch("gallery.tasks.tag_recommendation_batch")
    def test_generate_stories_task_exception_handling(
        self, mock_tag_batch, mock_get_redis
    ):
        """예외 처리 테스트"""
        mock_tag_batch.side_effect = Exception("Batch processing failed")
        mock_redis = MagicMock()
        mock_get_redis.return_value = mock_redis

        # 예외가 발생해도 함수가 종료되어야 함 (로그만 출력)
        generate_stories_task(self.user.id, 10)

        # Redis에 저장되지 않아야 함
        mock_redis.set.assert_not_called()


class ComputeAndStoreRepVectorsTest(TestCase):
    """compute_and_store_rep_vectors Celery 작업 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.tag = Tag.objects.create(tag="rep벡터태그", user=self.user)

        # 여러 사진 생성
        self.photos = []
        for i in range(15):
            photo = Photo.objects.create(
                photo_id=uuid.uuid4(),
                user=self.user,
                photo_path_id=800 + i,
                created_at=timezone.now(),
            )
            self.photos.append(photo)
            Photo_Tag.objects.create(user=self.user, photo=photo, tag=self.tag)

    @patch("gallery.tasks.get_qdrant_client")
    def test_compute_and_store_rep_vectors_success(self, mock_get_client):
        """rep vector 계산 및 저장 성공"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        # Mock retrieve (사진 벡터)
        mock_points = []
        for photo in self.photos:
            mock_point = MagicMock()
            mock_point.vector = np.random.rand(512).tolist()
            mock_points.append(mock_point)

        mock_client.retrieve.return_value = mock_points

        compute_and_store_rep_vectors(self.user.id, self.tag.tag_id)

        # delete 호출 확인 (기존 rep vector 삭제)
        mock_client.delete.assert_called_once()

        # upsert 호출 확인 (새 rep vector 저장)
        mock_client.upsert.assert_called_once()
        upsert_call_args = mock_client.upsert.call_args
        points_to_upsert = upsert_call_args[1]["points"]

        # ML 모델이 적용되어 대표 벡터가 생성되어야 함
        self.assertGreater(len(points_to_upsert), 0)

    @patch("gallery.tasks.get_qdrant_client")
    def test_compute_and_store_rep_vectors_no_photos(self, mock_get_client):
        """사진이 없는 태그"""
        # 새로운 태그 생성 (사진 없음)
        empty_tag = Tag.objects.create(tag="빈태그", user=self.user)

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        compute_and_store_rep_vectors(self.user.id, empty_tag.tag_id)

        # delete 호출 확인
        mock_client.delete.assert_called_once()

        # upsert는 호출되지 않아야 함
        mock_client.upsert.assert_not_called()

    @patch("gallery.tasks.get_qdrant_client")
    def test_compute_and_store_rep_vectors_few_samples(self, mock_get_client):
        """샘플이 적은 경우 (ML 모델 미적용)"""
        # 새로운 태그와 사진 3개만 생성
        few_tag = Tag.objects.create(tag="적은샘플태그", user=self.user)
        few_photos = []
        for i in range(3):
            photo = Photo.objects.create(
                photo_id=uuid.uuid4(),
                user=self.user,
                photo_path_id=900 + i,
                created_at=timezone.now(),
            )
            few_photos.append(photo)
            Photo_Tag.objects.create(user=self.user, photo=photo, tag=few_tag)

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        mock_points = []
        for photo in few_photos:
            mock_point = MagicMock()
            mock_point.vector = np.random.rand(512).tolist()
            mock_points.append(mock_point)

        mock_client.retrieve.return_value = mock_points

        compute_and_store_rep_vectors(self.user.id, few_tag.tag_id)

        # upsert 호출 확인
        mock_client.upsert.assert_called_once()
        upsert_call_args = mock_client.upsert.call_args
        points_to_upsert = upsert_call_args[1]["points"]

        # 샘플이 적으면 모든 벡터가 rep vector가 됨
        self.assertEqual(len(points_to_upsert), 3)

    @patch("gallery.tasks.get_qdrant_client")
    def test_compute_and_store_rep_vectors_exception(self, mock_get_client):
        """예외 발생 처리"""
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client
        mock_client.retrieve.side_effect = Exception("Qdrant retrieval failed")

        # 예외가 발생해도 함수가 종료되어야 함
        compute_and_store_rep_vectors(self.user.id, self.tag.tag_id)

        # upsert는 호출되지 않아야 함
        mock_client.upsert.assert_not_called()
