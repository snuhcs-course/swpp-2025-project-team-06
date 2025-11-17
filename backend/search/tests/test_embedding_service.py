import unittest
from unittest.mock import patch, MagicMock
import torch
import numpy as np
from search.embedding_service import (
    get_text_model,
    create_query_embedding,
    _TEXT_MODEL_NAME,
    DEVICE,
)


class EmbeddingServiceTest(unittest.TestCase):
    """Test embedding service functions"""

    def setUp(self):
        """Set up test fixtures"""
        # Reset global model cache before each test
        import search.embedding_service
        search.embedding_service._text_model = None

    def tearDown(self):
        """Clean up after each test"""
        # Reset global model cache after each test
        import search.embedding_service
        search.embedding_service._text_model = None

    @patch("search.embedding_service.SentenceTransformer")
    def test_get_text_model_loads_model(self, mock_sentence_transformer):
        """Test that get_text_model loads the correct model"""
        mock_model = MagicMock()
        mock_sentence_transformer.return_value = mock_model

        result = get_text_model()

        # Verify model was loaded with correct parameters
        mock_sentence_transformer.assert_called_once_with(
            _TEXT_MODEL_NAME, device=DEVICE
        )
        self.assertEqual(result, mock_model)

    @patch("search.embedding_service.SentenceTransformer")
    def test_get_text_model_caches_model(self, mock_sentence_transformer):
        """Test that get_text_model caches the model globally"""
        mock_model = MagicMock()
        mock_sentence_transformer.return_value = mock_model

        # First call - should load model
        result1 = get_text_model()
        
        # Second call - should use cached model
        result2 = get_text_model()

        # Model should only be loaded once
        mock_sentence_transformer.assert_called_once()
        
        # Both calls should return the same instance
        self.assertIs(result1, result2)

    @patch("search.embedding_service.SentenceTransformer")
    def test_get_text_model_prints_loading_message(self, mock_sentence_transformer):
        """Test that get_text_model prints loading message on first load"""
        mock_model = MagicMock()
        mock_sentence_transformer.return_value = mock_model

        with patch('builtins.print') as mock_print:
            get_text_model()
            
            # Verify loading message was printed
            mock_print.assert_called_once_with(
                "[INFO] Loading CLIP text model for search..."
            )

    @patch("search.embedding_service.get_text_model")
    def test_create_query_embedding_returns_list(self, mock_get_text_model):
        """Test that create_query_embedding returns a list of floats"""
        mock_model = MagicMock()
        mock_embedding = np.array([0.1, 0.2, 0.3, 0.4])
        mock_model.encode.return_value = mock_embedding
        mock_get_text_model.return_value = mock_model

        result = create_query_embedding("test query")

        # Verify encode was called with the query
        mock_model.encode.assert_called_once_with("test query")
        
        # Verify result is a list
        self.assertIsInstance(result, list)
        self.assertEqual(result, [0.1, 0.2, 0.3, 0.4])

    @patch("search.embedding_service.get_text_model")
    def test_create_query_embedding_with_empty_string(self, mock_get_text_model):
        """Test create_query_embedding with empty string"""
        mock_model = MagicMock()
        mock_embedding = np.array([0.0] * 512)
        mock_model.encode.return_value = mock_embedding
        mock_get_text_model.return_value = mock_model

        result = create_query_embedding("")

        mock_model.encode.assert_called_once_with("")
        self.assertIsInstance(result, list)
        self.assertEqual(len(result), 512)

    @patch("search.embedding_service.get_text_model")
    def test_create_query_embedding_with_multilingual_text(self, mock_get_text_model):
        """Test create_query_embedding with multilingual text"""
        mock_model = MagicMock()
        mock_embedding = np.array([0.5] * 512)
        mock_model.encode.return_value = mock_embedding
        mock_get_text_model.return_value = mock_model

        # Test with Korean text
        result = create_query_embedding("아름다운 석양")

        mock_model.encode.assert_called_once_with("아름다운 석양")
        self.assertIsInstance(result, list)
        self.assertEqual(len(result), 512)

    @patch("search.embedding_service.get_text_model")
    def test_create_query_embedding_with_long_text(self, mock_get_text_model):
        """Test create_query_embedding with long text"""
        mock_model = MagicMock()
        mock_embedding = np.array([0.7] * 512)
        mock_model.encode.return_value = mock_embedding
        mock_get_text_model.return_value = mock_model

        long_query = "beautiful sunset over the ocean " * 10
        result = create_query_embedding(long_query)

        mock_model.encode.assert_called_once_with(long_query)
        self.assertIsInstance(result, list)

    @patch("search.embedding_service.get_text_model")
    def test_create_query_embedding_with_special_characters(
        self, mock_get_text_model
    ):
        """Test create_query_embedding with special characters"""
        mock_model = MagicMock()
        mock_embedding = np.array([0.3] * 512)
        mock_model.encode.return_value = mock_embedding
        mock_get_text_model.return_value = mock_model

        query_with_special_chars = "sunset @ beach! #photography 2024"
        result = create_query_embedding(query_with_special_chars)

        mock_model.encode.assert_called_once_with(query_with_special_chars)
        self.assertIsInstance(result, list)

    def test_device_configuration(self):
        """Test that DEVICE is set correctly"""
        # DEVICE should be either 'cuda' or 'cpu'
        self.assertIn(DEVICE, ['cuda', 'cpu'])
        
        # Verify it matches torch.cuda.is_available()
        expected_device = "cuda" if torch.cuda.is_available() else "cpu"
        self.assertEqual(DEVICE, expected_device)

    def test_model_name_constant(self):
        """Test that model name constant is set correctly"""
        self.assertEqual(
            _TEXT_MODEL_NAME, 
            "sentence-transformers/clip-ViT-B-32-multilingual-v1"
        )

    @patch("search.embedding_service.get_text_model")
    def test_create_query_embedding_calls_get_text_model(self, mock_get_text_model):
        """Test that create_query_embedding calls get_text_model"""
        mock_model = MagicMock()
        mock_embedding = np.array([0.1] * 512)
        mock_model.encode.return_value = mock_embedding
        mock_get_text_model.return_value = mock_model

        create_query_embedding("test")

        # Verify get_text_model was called
        mock_get_text_model.assert_called_once()

    @patch("search.embedding_service.SentenceTransformer")
    def test_get_text_model_uses_correct_device(self, mock_sentence_transformer):
        """Test that get_text_model uses the correct device"""
        mock_model = MagicMock()
        mock_sentence_transformer.return_value = mock_model

        get_text_model()

        # Verify device parameter was passed correctly
        call_kwargs = mock_sentence_transformer.call_args[1]
        self.assertEqual(call_kwargs['device'], DEVICE)

    @patch("search.embedding_service.get_text_model")
    def test_create_query_embedding_output_format(self, mock_get_text_model):
        """Test that create_query_embedding output is in correct format"""
        mock_model = MagicMock()
        # Create a realistic 512-dimensional embedding
        mock_embedding = np.random.rand(512).astype(np.float32)
        mock_model.encode.return_value = mock_embedding
        mock_get_text_model.return_value = mock_model

        result = create_query_embedding("test query")

        # Verify output is a list of numbers
        self.assertIsInstance(result, list)
        self.assertEqual(len(result), 512)
        
        # Verify all elements are numbers
        for element in result:
            self.assertIsInstance(element, (int, float))

    @patch("search.embedding_service.get_text_model")
    def test_multiple_query_embeddings(self, mock_get_text_model):
        """Test creating multiple query embeddings sequentially"""
        mock_model = MagicMock()
        
        # Different embeddings for different queries
        embedding1 = np.array([0.1] * 512)
        embedding2 = np.array([0.2] * 512)
        mock_model.encode.side_effect = [embedding1, embedding2]
        
        mock_get_text_model.return_value = mock_model

        result1 = create_query_embedding("query 1")
        result2 = create_query_embedding("query 2")

        # Verify both queries were processed
        self.assertEqual(mock_model.encode.call_count, 2)
        
        # Verify results are different
        self.assertNotEqual(result1, result2)
        self.assertEqual(result1[0], 0.1)
        self.assertEqual(result2[0], 0.2)
