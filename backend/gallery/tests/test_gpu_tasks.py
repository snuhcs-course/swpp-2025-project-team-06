"""
Tests for gallery/gpu_tasks.py

All GPU dependencies (models, Qdrant, storage) are mocked.
"""

import uuid
from io import BytesIO
from unittest.mock import MagicMock, patch
from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
import numpy as np

from ..models import Photo, Caption, Photo_Caption
from ..gpu_tasks import (
    get_image_model,
    get_caption_processor,
    get_caption_model,
    get_image_embedding,
    get_image_captions,
    phrase_to_words,
    get_image_embeddings_batch,
    get_image_captions_batch,
    process_and_embed_photo,
    process_and_embed_photos_batch,
)


class PhraseToWordsTest(TestCase):
    """phrase_to_words 함수 테스트"""

    def test_phrase_to_words_basic(self):
        """기본 단어 추출 (소문자 변환, 구두점 제거, 불용어 필터링)"""
        phrase = "A quick brown fox jumps over the lazy dog."
        result = phrase_to_words(phrase)
        
        # 불용어 제거 확인 (a, the, over는 STOP_WORDS)
        self.assertIn("quick", result)
        self.assertIn("brown", result)
        self.assertIn("fox", result)
        self.assertIn("jumps", result)
        self.assertIn("lazy", result)
        self.assertIn("dog", result)
        self.assertNotIn("a", result)
        self.assertNotIn("the", result)

    def test_phrase_to_words_stop_words(self):
        """photo, image 등 커스텀 불용어 제거"""
        phrase = "This is a beautiful photo of an image."
        result = phrase_to_words(phrase)
        
        self.assertIn("beautiful", result)
        self.assertNotIn("photo", result)
        self.assertNotIn("image", result)
        self.assertNotIn("this", result)

    def test_phrase_to_words_empty(self):
        """빈 문자열 처리"""
        result = phrase_to_words("")
        self.assertEqual(result, [])

    def test_phrase_to_words_numbers_removed(self):
        """숫자 제거"""
        phrase = "There are 3 cats and 2 dogs in 2024."
        result = phrase_to_words(phrase)
        
        self.assertIn("cats", result)
        self.assertIn("dogs", result)
        self.assertIn("there", result)
        self.assertNotIn("3", result)
        self.assertNotIn("2", result)
        self.assertNotIn("2024", result)

    def test_phrase_to_words_short_words_removed(self):
        """1글자 단어 제거"""
        phrase = "I go to a big zoo"
        result = phrase_to_words(phrase)
        
        self.assertIn("go", result)
        self.assertIn("big", result)
        self.assertIn("zoo", result)
        self.assertNotIn("i", result)  # 1글자


class GetImageModelTest(TestCase):
    """get_image_model 함수 테스트"""

    def setUp(self):
        """각 테스트 전에 전역 변수 초기화"""
        import gallery.gpu_tasks as gpu_tasks
        gpu_tasks._image_model = None

    @patch("gallery.gpu_tasks.SentenceTransformer")
    def test_get_image_model_loads_once(self, mock_sentence_transformer):
        """모델이 한 번만 로드되는지 확인 (캐싱)"""
        mock_model = MagicMock()
        mock_sentence_transformer.return_value = mock_model

        # 첫 번째 호출
        model1 = get_image_model()
        # 두 번째 호출
        model2 = get_image_model()

        # 같은 인스턴스 반환
        self.assertIs(model1, model2)
        # SentenceTransformer는 한 번만 호출
        self.assertEqual(mock_sentence_transformer.call_count, 1)


class GetCaptionProcessorTest(TestCase):
    """get_caption_processor 함수 테스트"""

    def setUp(self):
        """각 테스트 전에 전역 변수 초기화"""
        import gallery.gpu_tasks as gpu_tasks
        gpu_tasks._caption_processor = None

    @patch("gallery.gpu_tasks.BlipProcessor.from_pretrained")
    def test_get_caption_processor_loads_once(self, mock_from_pretrained):
        """프로세서가 한 번만 로드되는지 확인 (캐싱)"""
        mock_processor = MagicMock()
        mock_from_pretrained.return_value = mock_processor

        # 첫 번째 호출
        processor1 = get_caption_processor()
        # 두 번째 호출
        processor2 = get_caption_processor()

        # 같은 인스턴스 반환
        self.assertIs(processor1, processor2)
        # from_pretrained는 한 번만 호출
        self.assertEqual(mock_from_pretrained.call_count, 1)


class GetCaptionModelTest(TestCase):
    """get_caption_model 함수 테스트"""

    def setUp(self):
        """각 테스트 전에 전역 변수 초기화"""
        import gallery.gpu_tasks as gpu_tasks
        gpu_tasks._caption_model = None

    @patch("gallery.gpu_tasks.BlipForConditionalGeneration.from_pretrained")
    def test_get_caption_model_loads_once(self, mock_from_pretrained):
        """모델이 한 번만 로드되는지 확인 (캐싱)"""
        mock_model = MagicMock()
        mock_model.to.return_value = mock_model
        mock_from_pretrained.return_value = mock_model

        # 첫 번째 호출
        model1 = get_caption_model()
        # 두 번째 호출
        model2 = get_caption_model()

        # 같은 인스턴스 반환
        self.assertIs(model1, model2)
        # from_pretrained는 한 번만 호출
        self.assertEqual(mock_from_pretrained.call_count, 1)


class GetImageEmbeddingTest(TestCase):
    """get_image_embedding 함수 테스트"""

    @patch("gallery.gpu_tasks.get_image_model")
    @patch("gallery.gpu_tasks.Image.open")
    @patch("gallery.gpu_tasks.torch.no_grad")
    def test_get_image_embedding_success(self, mock_no_grad, mock_image_open, mock_get_model):
        """이미지 임베딩 생성 성공"""
        # Mock image
        mock_image = MagicMock()
        mock_image_open.return_value = mock_image
        mock_image.convert.return_value = mock_image

        # Mock model
        mock_model = MagicMock()
        mock_get_model.return_value = mock_model
        
        fake_embedding = np.random.rand(512).tolist()
        mock_model.encode.return_value = fake_embedding

        # Create fake image data
        image_data = BytesIO(b"fake_image_data")

        result = get_image_embedding(image_data)

        self.assertEqual(result, fake_embedding)
        mock_image.convert.assert_called_once_with("RGB")
        mock_model.encode.assert_called_once_with(mock_image)

    @patch("gallery.gpu_tasks.Image.open")
    def test_get_image_embedding_failure(self, mock_image_open):
        """이미지 열기 실패 시 None 반환"""
        mock_image_open.side_effect = Exception("Failed to open image")

        image_data = BytesIO(b"invalid_data")
        result = get_image_embedding(image_data)

        self.assertIsNone(result)


class GetImageCaptionsTest(TestCase):
    """get_image_captions 함수 테스트"""

    @patch("gallery.gpu_tasks.get_caption_model")
    @patch("gallery.gpu_tasks.get_caption_processor")
    @patch("gallery.gpu_tasks.Image.open")
    @patch("gallery.gpu_tasks.torch.no_grad")
    def test_get_image_captions_success(
        self, mock_no_grad, mock_image_open, mock_get_processor, mock_get_model
    ):
        """이미지 캡션 생성 성공"""
        # Mock image
        mock_image = MagicMock()
        mock_image_open.return_value = mock_image
        mock_image.convert.return_value = mock_image

        # Mock processor
        mock_processor = MagicMock()
        mock_get_processor.return_value = mock_processor

        # Create mock tensor with .to() method
        mock_tensor = MagicMock()
        mock_tensor.to.return_value = mock_tensor
        mock_processor.return_value = {"pixel_values": mock_tensor}

        # Mock model
        mock_model = MagicMock()
        mock_get_model.return_value = mock_model

        # Mock generated outputs
        mock_outputs = ["output1", "output2", "output3", "output4", "output5"]
        mock_model.generate.return_value = mock_outputs

        # Mock decoded captions
        mock_processor.decode.side_effect = [
            "a brown dog running",
            "a dog playing outside",
            "brown dog in the park",
            "happy dog",
            "a cute dog",
        ]

        image_data = BytesIO(b"fake_image_data")
        result = get_image_captions(image_data)

        # 단어 빈도 확인
        self.assertIn("dog", result)
        self.assertIn("brown", result)
        self.assertEqual(result["dog"], 5)  # "dog" appears in all 5 captions
        self.assertEqual(result["brown"], 2)  # "brown" appears twice

    @patch("gallery.gpu_tasks.get_caption_model")
    @patch("gallery.gpu_tasks.get_caption_processor")
    @patch("gallery.gpu_tasks.Image.open")
    @patch("gallery.gpu_tasks.torch.no_grad")
    def test_get_image_captions_empty_after_filtering(
        self, mock_no_grad, mock_image_open, mock_get_processor, mock_get_model
    ):
        """불용어만 있는 캡션 처리"""
        mock_image = MagicMock()
        mock_image_open.return_value = mock_image
        mock_image.convert.return_value = mock_image

        mock_processor = MagicMock()
        mock_get_processor.return_value = mock_processor

        mock_tensor = MagicMock()
        mock_tensor.to.return_value = mock_tensor
        mock_processor.return_value = {"pixel_values": mock_tensor}

        mock_model = MagicMock()
        mock_get_model.return_value = mock_model
        mock_model.generate.return_value = ["out1", "out2"]

        # 불용어만 있는 캡션
        mock_processor.decode.side_effect = [
            "a photo of an image",
            "this is a picture",
        ]

        image_data = BytesIO(b"fake_image_data")
        result = get_image_captions(image_data)

        self.assertEqual(result, {})


class GetImageEmbeddingsBatchTest(TestCase):
    """get_image_embeddings_batch 함수 테스트"""

    @patch("gallery.gpu_tasks.get_image_model")
    @patch("gallery.gpu_tasks.Image.open")
    @patch("gallery.gpu_tasks.torch.no_grad")
    def test_get_image_embeddings_batch_success(
        self, mock_no_grad, mock_image_open, mock_get_model
    ):
        """배치 임베딩 생성 성공"""
        # Mock images
        mock_image1 = MagicMock()
        mock_image2 = MagicMock()
        mock_image_open.side_effect = [mock_image1, mock_image2]

        # Mock model
        mock_model = MagicMock()
        mock_get_model.return_value = mock_model

        fake_embeddings = [
            np.random.rand(512).tolist(),
            np.random.rand(512).tolist(),
        ]
        mock_model.encode.return_value = fake_embeddings

        image_data_list = [
            BytesIO(b"fake_image_1"),
            BytesIO(b"fake_image_2"),
        ]

        result = get_image_embeddings_batch(image_data_list)

        self.assertEqual(len(result), 2)
        self.assertEqual(result[0], fake_embeddings[0])
        self.assertEqual(result[1], fake_embeddings[1])

    @patch("gallery.gpu_tasks.Image.open")
    def test_get_image_embeddings_batch_partial_failure(self, mock_image_open):
        """일부 이미지 실패 시 None 반환"""
        mock_image1 = MagicMock()
        mock_image_open.side_effect = [
            mock_image1,
            Exception("Failed to open image 2"),
        ]

        image_data_list = [
            BytesIO(b"fake_image_1"),
            BytesIO(b"invalid_data"),
        ]

        result = get_image_embeddings_batch(image_data_list)

        # 첫 번째는 None이 아니고, 두 번째는 None
        self.assertEqual(len(result), 2)
        self.assertIsNone(result[1])


class GetImageCaptionsBatchTest(TestCase):
    """get_image_captions_batch 함수 테스트"""

    @patch("gallery.gpu_tasks.get_caption_model")
    @patch("gallery.gpu_tasks.get_caption_processor")
    @patch("gallery.gpu_tasks.Image.open")
    @patch("gallery.gpu_tasks.torch.no_grad")
    def test_get_image_captions_batch_success(
        self, mock_no_grad, mock_image_open, mock_get_processor, mock_get_model
    ):
        """배치 캡션 생성 성공"""
        # Mock images
        mock_image1 = MagicMock()
        mock_image2 = MagicMock()
        mock_image_open.side_effect = [mock_image1, mock_image2]

        # Mock processor
        mock_processor = MagicMock()
        mock_get_processor.return_value = mock_processor

        mock_tensor = MagicMock()
        mock_tensor.to.return_value = mock_tensor
        mock_processor.return_value = {"pixel_values": mock_tensor}

        # Mock model
        mock_model = MagicMock()
        mock_get_model.return_value = mock_model

        # 2개 이미지 × 5개 캡션 = 10개 출력
        mock_outputs = [f"output{i}" for i in range(10)]
        mock_model.generate.return_value = mock_outputs

        # Image 1: 5 captions
        # Image 2: 5 captions
        mock_processor.decode.side_effect = [
            "a cat sitting",      # img1
            "cat on chair",       # img1
            "cute cat",           # img1
            "cat looking",        # img1
            "cat relaxing",       # img1
            "a dog running",      # img2
            "dog playing",        # img2
            "brown dog",          # img2
            "dog outside",        # img2
            "happy dog",          # img2
        ]

        image_data_list = [
            BytesIO(b"fake_image_1"),
            BytesIO(b"fake_image_2"),
        ]

        result = get_image_captions_batch(image_data_list)

        self.assertEqual(len(result), 2)
        
        # Image 1 결과
        self.assertIn("cat", result[0])
        self.assertEqual(result[0]["cat"], 5)
        
        # Image 2 결과
        self.assertIn("dog", result[1])
        self.assertEqual(result[1]["dog"], 5)


class ProcessAndEmbedPhotoTest(TestCase):
    """process_and_embed_photo Celery 작업 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.storage_key = uuid.uuid4()
        self.photo = Photo.objects.create(
            photo_id=self.storage_key,
            user=self.user,
            photo_path_id=12345,
            created_at=timezone.now(),
        )

    @patch("gallery.storage_service.delete_photo")
    @patch("gallery.storage_service.download_photo")
    @patch("gallery.gpu_tasks.get_image_captions")
    @patch("gallery.gpu_tasks.get_image_embedding")
    @patch("gallery.gpu_tasks.get_qdrant_client")
    def test_process_and_embed_photo_success(
        self,
        mock_get_client,
        mock_get_embedding,
        mock_get_captions,
        mock_download,
        mock_delete,
    ):
        """사진 처리 및 임베딩 저장 성공"""
        # Mock download
        fake_image_data = BytesIO(b"fake_image_data")
        mock_download.return_value = fake_image_data

        # Mock embedding
        fake_embedding = np.random.rand(512).tolist()
        mock_get_embedding.return_value = fake_embedding

        # Mock captions
        fake_captions = {"dog": 3, "brown": 2, "running": 1}
        mock_get_captions.return_value = fake_captions

        # Mock Qdrant client
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        # Execute task
        process_and_embed_photo(
            storage_key=str(self.storage_key),
            user_id=self.user.id,
            filename="test.jpg",
            photo_path_id=12345,
            created_at=str(timezone.now()),
            lat=37.5,
            lng=127.0,
        )

        # Verify Qdrant upsert
        mock_client.upsert.assert_called_once()
        upsert_call = mock_client.upsert.call_args
        self.assertEqual(len(upsert_call[1]["points"]), 1)

        # Verify captions saved to DB
        photo_captions = Photo_Caption.objects.filter(
            user=self.user, photo=self.photo
        )
        self.assertEqual(photo_captions.count(), 3)

        # Verify Caption objects created
        captions = Caption.objects.filter(user=self.user)
        self.assertEqual(captions.count(), 3)

        # Verify cleanup
        mock_delete.assert_called_once_with(str(self.storage_key))

    @patch("gallery.storage_service.delete_photo")
    @patch("gallery.storage_service.download_photo")
    @patch("gallery.gpu_tasks.get_image_embedding")
    @patch("gallery.gpu_tasks.get_qdrant_client")
    def test_process_and_embed_photo_embedding_failure(
        self, mock_get_client, mock_get_embedding, mock_download, mock_delete
    ):
        """임베딩 생성 실패 시 처리"""
        fake_image_data = BytesIO(b"fake_image_data")
        mock_download.return_value = fake_image_data

        # 임베딩 실패
        mock_get_embedding.return_value = None

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        process_and_embed_photo(
            storage_key=str(self.storage_key),
            user_id=self.user.id,
            filename="test.jpg",
            photo_path_id=12345,
            created_at=str(timezone.now()),
            lat=37.5,
            lng=127.0,
        )

        # Qdrant에 업로드되지 않음
        mock_client.upsert.assert_not_called()

        # 여전히 cleanup은 실행됨
        mock_delete.assert_called_once()

    @patch("gallery.storage_service.delete_photo")
    @patch("gallery.storage_service.download_photo")
    def test_process_and_embed_photo_download_failure(
        self, mock_download, mock_delete
    ):
        """다운로드 실패 시 예외 처리"""
        mock_download.side_effect = Exception("Download failed")

        process_and_embed_photo(
            storage_key=str(self.storage_key),
            user_id=self.user.id,
            filename="test.jpg",
            photo_path_id=12345,
            created_at=str(timezone.now()),
            lat=37.5,
            lng=127.0,
        )

        # finally 블록 cleanup 실행
        mock_delete.assert_called_once_with(str(self.storage_key))


class ProcessAndEmbedPhotosBatchTest(TestCase):
    """process_and_embed_photos_batch Celery 작업 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.storage_key1 = uuid.uuid4()
        self.storage_key2 = uuid.uuid4()
        
        self.photo1 = Photo.objects.create(
            photo_id=self.storage_key1,
            user=self.user,
            photo_path_id=101,
            created_at=timezone.now(),
        )
        self.photo2 = Photo.objects.create(
            photo_id=self.storage_key2,
            user=self.user,
            photo_path_id=102,
            created_at=timezone.now(),
        )

    @patch("gallery.storage_service.delete_photo")
    @patch("gallery.storage_service.download_photo")
    @patch("gallery.gpu_tasks.get_image_captions_batch")
    @patch("gallery.gpu_tasks.get_image_embeddings_batch")
    @patch("gallery.gpu_tasks.get_qdrant_client")
    def test_process_and_embed_photos_batch_success(
        self,
        mock_get_client,
        mock_get_embeddings,
        mock_get_captions,
        mock_download,
        mock_delete,
    ):
        """배치 사진 처리 성공"""
        # Mock download
        fake_image_data1 = BytesIO(b"fake_image_1")
        fake_image_data2 = BytesIO(b"fake_image_2")
        mock_download.side_effect = [fake_image_data1, fake_image_data2]

        # Mock embeddings
        fake_embeddings = [
            np.random.rand(512).tolist(),
            np.random.rand(512).tolist(),
        ]
        mock_get_embeddings.return_value = fake_embeddings

        # Mock captions
        fake_captions = [
            {"cat": 3, "cute": 2},
            {"dog": 4, "happy": 1},
        ]
        mock_get_captions.return_value = fake_captions

        # Mock Qdrant client
        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        photos_metadata = [
            {
                "storage_key": str(self.storage_key1),
                "user_id": self.user.id,
                "filename": "photo1.jpg",
                "photo_path_id": 101,
                "created_at": str(timezone.now()),
                "lat": 37.5,
                "lng": 127.0,
            },
            {
                "storage_key": str(self.storage_key2),
                "user_id": self.user.id,
                "filename": "photo2.jpg",
                "photo_path_id": 102,
                "created_at": str(timezone.now()),
                "lat": 37.6,
                "lng": 127.1,
            },
        ]

        process_and_embed_photos_batch(photos_metadata)

        # Verify Qdrant batch upsert
        mock_client.upsert.assert_called_once()
        upsert_call = mock_client.upsert.call_args
        self.assertEqual(len(upsert_call[1]["points"]), 2)

        # Verify captions saved to DB
        photo1_captions = Photo_Caption.objects.filter(
            user=self.user, photo=self.photo1
        )
        self.assertEqual(photo1_captions.count(), 2)  # cat, cute

        photo2_captions = Photo_Caption.objects.filter(
            user=self.user, photo=self.photo2
        )
        self.assertEqual(photo2_captions.count(), 2)  # dog, happy

        # Verify cleanup called for both
        self.assertEqual(mock_delete.call_count, 2)

    def test_process_and_embed_photos_batch_empty_input(self):
        """빈 입력 처리"""
        process_and_embed_photos_batch([])
        # 에러 없이 종료되어야 함

    @patch("gallery.storage_service.delete_photo")
    @patch("gallery.storage_service.download_photo")
    @patch("gallery.gpu_tasks.get_image_captions_batch")
    @patch("gallery.gpu_tasks.get_image_embeddings_batch")
    @patch("gallery.gpu_tasks.get_qdrant_client")
    def test_process_and_embed_photos_batch_partial_failure(
        self,
        mock_get_client,
        mock_get_embeddings,
        mock_get_captions,
        mock_download,
        mock_delete,
    ):
        """일부 사진 처리 실패"""
        # 첫 번째는 성공, 두 번째는 다운로드 실패
        fake_image_data1 = BytesIO(b"fake_image_1")
        mock_download.side_effect = [
            fake_image_data1,
            Exception("Download failed"),
        ]

        # 첫 번째 사진만 처리됨
        fake_embeddings = [np.random.rand(512).tolist()]
        mock_get_embeddings.return_value = fake_embeddings

        fake_captions = [{"cat": 2}]
        mock_get_captions.return_value = fake_captions

        mock_client = MagicMock()
        mock_get_client.return_value = mock_client

        photos_metadata = [
            {
                "storage_key": str(self.storage_key1),
                "user_id": self.user.id,
                "filename": "photo1.jpg",
                "photo_path_id": 101,
                "created_at": str(timezone.now()),
                "lat": 37.5,
                "lng": 127.0,
            },
            {
                "storage_key": str(self.storage_key2),
                "user_id": self.user.id,
                "filename": "photo2.jpg",
                "photo_path_id": 102,
                "created_at": str(timezone.now()),
                "lat": 37.6,
                "lng": 127.1,
            },
        ]

        process_and_embed_photos_batch(photos_metadata)

        # 첫 번째 사진만 Qdrant에 업로드
        mock_client.upsert.assert_called_once()
        upsert_call = mock_client.upsert.call_args
        self.assertEqual(len(upsert_call[1]["points"]), 1)

        # 첫 번째 사진만 캡션 저장
        photo1_captions = Photo_Caption.objects.filter(
            user=self.user, photo=self.photo1
        )
        self.assertEqual(photo1_captions.count(), 1)

        photo2_captions = Photo_Caption.objects.filter(
            user=self.user, photo=self.photo2
        )
        self.assertEqual(photo2_captions.count(), 0)

        # cleanup은 두 번 시도 (첫 번째만 성공)
        self.assertEqual(mock_delete.call_count, 2)
