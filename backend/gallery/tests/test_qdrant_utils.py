import gallery.qdrant_utils
from django.test import TestCase
from unittest.mock import patch, MagicMock, call
from qdrant_client.http.exceptions import UnexpectedResponse
from qdrant_client import models

from gallery.qdrant_utils import (
    get_qdrant_client,
    initialize_qdrant,
    IMAGE_COLLECTION_NAME,
    REPVEC_COLLECTION_NAME,
    TAG_PRESET_COLLECTION_NAME,
    QDRANT_URL,
    QDRANT_API_KEY,
)


class GetQdrantClientTest(TestCase):
    """Tests for get_qdrant_client function"""

    def setUp(self):
        """Reset the singleton client before each test."""
        gallery.qdrant_utils._qdrant_client = None

    @patch('gallery.qdrant_utils.QdrantClient')
    def test_get_qdrant_client_returns_client(self, mock_qdrant_client_class):
        """Test that get_qdrant_client returns a QdrantClient instance"""
        mock_client = MagicMock()
        mock_qdrant_client_class.return_value = mock_client

        result = get_qdrant_client()

        # Verify QdrantClient was called with correct parameters
        mock_qdrant_client_class.assert_called_once_with(
            url=QDRANT_URL,
            api_key=QDRANT_API_KEY,
        )
        self.assertEqual(result, mock_client)

    @patch('gallery.qdrant_utils.QdrantClient')
    def test_get_qdrant_client_uses_settings(self, mock_qdrant_client_class):
        """Test that get_qdrant_client uses Django settings"""
        get_qdrant_client()

        call_kwargs = mock_qdrant_client_class.call_args[1]
        self.assertIn('url', call_kwargs)
        self.assertIn('api_key', call_kwargs)


class InitializeQdrantTest(TestCase):
    """Tests for initialize_qdrant function"""

    def setUp(self):
        """Set up mock client for each test"""
        self.mock_client = MagicMock()

    @patch('gallery.qdrant_utils.get_qdrant_client')
    def test_initialize_collections_already_exist(self, mock_get_client):
        """Test when both collections already exist"""
        mock_get_client.return_value = self.mock_client
        
        # Mock that collections already exist
        self.mock_client.get_collection.return_value = MagicMock()

        initialize_qdrant()

        # Verify get_collection was called for both collections
        expected_calls = [
            call(collection_name=IMAGE_COLLECTION_NAME),
            call(collection_name=REPVEC_COLLECTION_NAME),
        ]
        self.mock_client.get_collection.assert_has_calls(expected_calls, any_order=True)
        
        # Verify create_collection was NOT called
        self.mock_client.create_collection.assert_not_called()

    @patch('gallery.qdrant_utils.get_qdrant_client')
    @patch('builtins.print')
    def test_initialize_creates_missing_image_collection(self, mock_print, mock_get_client):
        """Test creating IMAGE_COLLECTION when it doesn't exist"""
        mock_get_client.return_value = self.mock_client
        
        # Mock that IMAGE_COLLECTION doesn't exist, REPVEC_COLLECTION does
        def get_collection_side_effect(collection_name):
            if collection_name == IMAGE_COLLECTION_NAME:
                raise UnexpectedResponse(404, "Not Found", b"", {})
            return MagicMock()
        
        self.mock_client.get_collection.side_effect = get_collection_side_effect

        initialize_qdrant()

        # Verify create_collection was called for IMAGE_COLLECTION
        self.mock_client.create_collection.assert_called_once()
        call_kwargs = self.mock_client.create_collection.call_args[1]
        self.assertEqual(call_kwargs['collection_name'], IMAGE_COLLECTION_NAME)
        self.assertIsInstance(call_kwargs['vectors_config'], models.VectorParams)
        self.assertEqual(call_kwargs['vectors_config'].size, 512)
        self.assertEqual(call_kwargs['vectors_config'].distance, models.Distance.COSINE)

        # Verify print statement
        mock_print.assert_called_with(f"Collection '{IMAGE_COLLECTION_NAME}' created.")

    @patch('gallery.qdrant_utils.get_qdrant_client')
    @patch('builtins.print')
    def test_initialize_creates_missing_repvec_collection(self, mock_print, mock_get_client):
        """Test creating REPVEC_COLLECTION when it doesn't exist"""
        mock_get_client.return_value = self.mock_client
        
        # Mock that REPVEC_COLLECTION doesn't exist, IMAGE_COLLECTION does
        def get_collection_side_effect(collection_name):
            if collection_name == REPVEC_COLLECTION_NAME:
                raise ValueError("Collection not found")
            return MagicMock()
        
        self.mock_client.get_collection.side_effect = get_collection_side_effect

        initialize_qdrant()

        # Verify create_collection was called for REPVEC_COLLECTION
        create_calls = self.mock_client.create_collection.call_args_list
        self.assertEqual(len(create_calls), 1)
        call_kwargs = create_calls[0][1]
        self.assertEqual(call_kwargs['collection_name'], REPVEC_COLLECTION_NAME)

        # Verify print statement
        mock_print.assert_called_with(f"Collection '{REPVEC_COLLECTION_NAME}' created.")

    @patch('gallery.qdrant_utils.get_qdrant_client')
    @patch('builtins.print')
    def test_initialize_creates_both_collections(self, mock_print, mock_get_client):
        """Test creating both collections when neither exists"""
        mock_get_client.return_value = self.mock_client
        
        # Mock that neither collection exists
        self.mock_client.get_collection.side_effect = UnexpectedResponse(404, "Not Found", b"", {})

        initialize_qdrant()

        # Verify create_collection was called twice
        self.assertEqual(self.mock_client.create_collection.call_count, 2)
        
        # Verify both collections were created
        collection_names = [call[1]['collection_name'] for call in self.mock_client.create_collection.call_args_list]
        self.assertIn(IMAGE_COLLECTION_NAME, collection_names)
        self.assertIn(REPVEC_COLLECTION_NAME, collection_names)

    @patch('gallery.qdrant_utils.get_qdrant_client')
    def test_initialize_creates_image_collection_indexes(self, mock_get_client):
        """Test that image collection indexes are created"""
        mock_get_client.return_value = self.mock_client
        
        # Mock that collections don't exist
        self.mock_client.get_collection.side_effect = UnexpectedResponse(404, "Not Found", b"", {})

        initialize_qdrant()

        # Verify payload indexes were created for image collection
        expected_fields = ["user_id", "filename", "photo_path_id", "created_at", "lat", "lng", "isTagged"]
        
        create_index_calls = self.mock_client.create_payload_index.call_args_list
        created_fields = []
        
        for call_item in create_index_calls:
            call_kwargs = call_item[1]
            if call_kwargs['collection_name'] == IMAGE_COLLECTION_NAME:
                created_fields.append(call_kwargs['field_name'])
        
        for field in expected_fields:
            self.assertIn(field, created_fields)

    @patch('gallery.qdrant_utils.get_qdrant_client')
    def test_initialize_creates_repvec_collection_indexes(self, mock_get_client):
        """Test that repvec collection indexes are created"""
        mock_get_client.return_value = self.mock_client
        
        # Mock that collections don't exist
        self.mock_client.get_collection.side_effect = UnexpectedResponse(404, "Not Found", b"", {})

        initialize_qdrant()

        # Verify payload indexes were created for repvec collection
        expected_fields = ["user_id", "tag_id"]
        
        create_index_calls = self.mock_client.create_payload_index.call_args_list
        created_fields = []
        
        for call_item in create_index_calls:
            call_kwargs = call_item[1]
            if call_kwargs['collection_name'] == REPVEC_COLLECTION_NAME:
                created_fields.append(call_kwargs['field_name'])
        
        for field in expected_fields:
            self.assertIn(field, created_fields)

    @patch('gallery.qdrant_utils.get_qdrant_client')
    def test_initialize_handles_index_already_exists(self, mock_get_client):
        """Test that initialize handles UnexpectedResponse when index already exists"""
        mock_get_client.return_value = self.mock_client
        
        # Mock that collections don't exist
        self.mock_client.get_collection.side_effect = UnexpectedResponse(404, "Not Found", b"", {})
        
        # Mock that create_payload_index raises UnexpectedResponse (index exists)
        self.mock_client.create_payload_index.side_effect = UnexpectedResponse(409, "Index already exists", b"", {})

        # Should not raise exception
        try:
            initialize_qdrant()
        except UnexpectedResponse:
            self.fail("initialize_qdrant raised UnexpectedResponse unexpectedly")

        # Verify create_payload_index was called (and errors were caught)
        self.assertTrue(self.mock_client.create_payload_index.called)

    @patch('gallery.qdrant_utils.get_qdrant_client')
    def test_initialize_image_index_field_schemas(self, mock_get_client):
        """Test that image collection indexes use correct field schemas"""
        mock_get_client.return_value = self.mock_client
        
        self.mock_client.get_collection.side_effect = UnexpectedResponse(404, "Not Found", b"", {})

        initialize_qdrant()

        # Verify field schemas
        expected_schemas = {
            "user_id": models.PayloadSchemaType.INTEGER,
            "filename": models.PayloadSchemaType.KEYWORD,
            "photo_path_id": models.PayloadSchemaType.INTEGER,
            "created_at": models.PayloadSchemaType.DATETIME,
            "lat": models.PayloadSchemaType.FLOAT,
            "lng": models.PayloadSchemaType.FLOAT,
            "isTagged": models.PayloadSchemaType.BOOL,
        }

        create_index_calls = self.mock_client.create_payload_index.call_args_list
        
        for call_item in create_index_calls:
            call_kwargs = call_item[1]
            if call_kwargs['collection_name'] == IMAGE_COLLECTION_NAME:
                field_name = call_kwargs['field_name']
                if field_name in expected_schemas:
                    self.assertEqual(call_kwargs['field_schema'], expected_schemas[field_name])

    @patch('gallery.qdrant_utils.get_qdrant_client')
    def test_initialize_repvec_index_field_schemas(self, mock_get_client):
        """Test that repvec collection indexes use correct field schemas"""
        mock_get_client.return_value = self.mock_client
        
        self.mock_client.get_collection.side_effect = UnexpectedResponse(404, "Not Found", b"", {})

        initialize_qdrant()

        # Verify field schemas
        expected_schemas = {
            "user_id": models.PayloadSchemaType.INTEGER,
            "tag_id": models.PayloadSchemaType.KEYWORD,
        }

        create_index_calls = self.mock_client.create_payload_index.call_args_list
        
        for call_item in create_index_calls:
            call_kwargs = call_item[1]
            if call_kwargs['collection_name'] == REPVEC_COLLECTION_NAME:
                field_name = call_kwargs['field_name']
                if field_name in expected_schemas:
                    self.assertEqual(call_kwargs['field_schema'], expected_schemas[field_name])


class QdrantConstantsTest(TestCase):
    """Tests for Qdrant constants"""

    def test_collection_names_are_strings(self):
        """Test that collection name constants are strings"""
        self.assertIsInstance(IMAGE_COLLECTION_NAME, str)
        self.assertIsInstance(REPVEC_COLLECTION_NAME, str)
        self.assertIsInstance(TAG_PRESET_COLLECTION_NAME, str)

    def test_collection_names_not_empty(self):
        """Test that collection names are not empty"""
        self.assertTrue(len(IMAGE_COLLECTION_NAME) > 0)
        self.assertTrue(len(REPVEC_COLLECTION_NAME) > 0)
        self.assertTrue(len(TAG_PRESET_COLLECTION_NAME) > 0)

    def test_collection_names_are_unique(self):
        """Test that all collection names are unique"""
        names = [IMAGE_COLLECTION_NAME, REPVEC_COLLECTION_NAME, TAG_PRESET_COLLECTION_NAME]
        self.assertEqual(len(names), len(set(names)))
