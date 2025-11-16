"""
Tests for storage_service.py

Tests both LocalStorageBackend and MinIOStorageBackend with mocking.
"""

import os
import tempfile
import uuid
from io import BytesIO
from unittest.mock import patch, MagicMock, mock_open

from django.test import TestCase, override_settings
from django.core.files.uploadedfile import SimpleUploadedFile

from gallery.storage_service import (
    LocalStorageBackend,
    MinIOStorageBackend,
    get_storage_backend,
    upload_photo,
    download_photo,
    delete_photo,
)


class LocalStorageBackendTest(TestCase):
    """Tests for LocalStorageBackend"""

    def setUp(self):
        """Set up test environment with temporary directory"""
        self.temp_dir = tempfile.mkdtemp()
        
    def tearDown(self):
        """Clean up temporary directory"""
        import shutil
        if os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)

    @override_settings(MEDIA_ROOT='/tmp/test_media')
    @patch('os.makedirs')
    def test_local_storage_init_creates_directory(self, mock_makedirs):
        """Test that LocalStorageBackend creates MEDIA_ROOT directory"""
        backend = LocalStorageBackend()
        
        mock_makedirs.assert_called_once_with('/tmp/test_media', exist_ok=True)
        self.assertEqual(backend.base_path, '/tmp/test_media')

    @override_settings(MEDIA_ROOT='/tmp/test_media')
    @patch('builtins.open', new_callable=mock_open)
    @patch('uuid.uuid4')
    @patch('os.makedirs')
    def test_local_storage_upload(self, mock_makedirs, mock_uuid, mock_file):
        """Test uploading file to local storage"""
        # Mock UUID
        test_uuid = uuid.UUID('12345678-1234-5678-1234-567812345678')
        mock_uuid.return_value = test_uuid
        
        # Create mock uploaded file
        file_content = b"test image content"
        uploaded_file = SimpleUploadedFile("test.jpg", file_content)
        
        backend = LocalStorageBackend()
        storage_key = backend.upload(uploaded_file)
        
        # Verify storage key
        self.assertEqual(storage_key, str(test_uuid))
        
        # Verify file was opened and written
        expected_path = f'/tmp/test_media/{test_uuid}.jpg'
        mock_file.assert_called_once_with(expected_path, 'wb')

    @override_settings(MEDIA_ROOT='/tmp/test_media')
    @patch('builtins.open', new_callable=mock_open, read_data=b"test content")
    @patch('os.path.exists', return_value=True)
    @patch('os.makedirs')
    def test_local_storage_download(self, mock_makedirs, mock_exists, mock_file):
        """Test downloading file from local storage"""
        storage_key = "test-key-123"
        
        backend = LocalStorageBackend()
        result = backend.download(storage_key)
        
        # Verify file was opened
        expected_path = f'/tmp/test_media/{storage_key}.jpg'
        mock_file.assert_called_once_with(expected_path, 'rb')
        
        # Verify result is BytesIO
        self.assertIsInstance(result, BytesIO)
        self.assertEqual(result.read(), b"test content")

    @override_settings(MEDIA_ROOT='/tmp/test_media')
    @patch('os.path.exists', return_value=False)
    @patch('os.makedirs')
    def test_local_storage_download_file_not_found(self, mock_makedirs, mock_exists):
        """Test downloading non-existent file raises FileNotFoundError"""
        storage_key = "nonexistent-key"
        
        backend = LocalStorageBackend()
        
        with self.assertRaises(FileNotFoundError):
            backend.download(storage_key)

    @override_settings(MEDIA_ROOT='/tmp/test_media')
    @patch('os.remove')
    @patch('os.path.exists', return_value=True)
    @patch('os.makedirs')
    def test_local_storage_delete_existing_file(self, mock_makedirs, mock_exists, mock_remove):
        """Test deleting existing file from local storage"""
        storage_key = "test-key-456"
        
        backend = LocalStorageBackend()
        backend.delete(storage_key)
        
        # Verify file was removed
        expected_path = f'/tmp/test_media/{storage_key}.jpg'
        mock_remove.assert_called_once_with(expected_path)

    @override_settings(MEDIA_ROOT='/tmp/test_media')
    @patch('os.remove')
    @patch('os.path.exists', return_value=False)
    @patch('os.makedirs')
    @patch('builtins.print')
    def test_local_storage_delete_nonexistent_file(self, mock_print, mock_makedirs, mock_exists, mock_remove):
        """Test deleting non-existent file doesn't raise error"""
        storage_key = "nonexistent-key"
        
        backend = LocalStorageBackend()
        backend.delete(storage_key)
        
        # Verify remove was NOT called
        mock_remove.assert_not_called()
        
        # Verify warning was printed
        mock_print.assert_called()


class MinIOStorageBackendTest(TestCase):
    """Tests for MinIOStorageBackend with mocking"""

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    def test_minio_init_creates_client(self, mock_boto3_client):
        """Test MinIO backend initialization"""
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None  # Bucket exists
        
        MinIOStorageBackend()
        
        # Verify boto3 client was created with correct parameters
        mock_boto3_client.assert_called_once_with(
            's3',
            endpoint_url='http://minio:9000',
            aws_access_key_id='test_access',
            aws_secret_access_key='test_secret',
            region_name='us-east-1'
        )
        
        # Verify bucket existence was checked
        mock_s3_client.head_bucket.assert_called_once_with(Bucket='test-bucket')

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    def test_minio_creates_bucket_if_not_exists(self, mock_boto3_client):
        """Test MinIO creates bucket if it doesn't exist"""
        from botocore.exceptions import ClientError
        
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        
        # Simulate bucket doesn't exist
        mock_s3_client.head_bucket.side_effect = ClientError(
            {'Error': {'Code': '404'}}, 'HeadBucket'
        )
        
        MinIOStorageBackend()
        
        # Verify bucket creation was attempted
        mock_s3_client.create_bucket.assert_called_once_with(Bucket='test-bucket')

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    @patch('uuid.uuid4')
    def test_minio_upload(self, mock_uuid, mock_boto3_client):
        """Test uploading file to MinIO"""
        test_uuid = uuid.UUID('12345678-1234-5678-1234-567812345678')
        mock_uuid.return_value = test_uuid
        
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None
        
        # Create mock uploaded file
        file_content = b"test image content"
        uploaded_file = SimpleUploadedFile("test.jpg", file_content)
        
        backend = MinIOStorageBackend()
        storage_key = backend.upload(uploaded_file)
        
        # Verify storage key
        self.assertEqual(storage_key, str(test_uuid))
        
        # Verify put_object was called with correct parameters
        expected_key = f'photos/{test_uuid}.jpg'
        mock_s3_client.put_object.assert_called_once()
        call_kwargs = mock_s3_client.put_object.call_args[1]
        self.assertEqual(call_kwargs['Bucket'], 'test-bucket')
        self.assertEqual(call_kwargs['Key'], expected_key)
        self.assertEqual(call_kwargs['Body'], file_content)

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    def test_minio_download(self, mock_boto3_client):
        """Test downloading file from MinIO"""
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None
        
        storage_key = "test-key-789"
        
        backend = MinIOStorageBackend()
        result = backend.download(storage_key)
        
        # Verify download_fileobj was called
        expected_key = f'photos/{storage_key}.jpg'
        mock_s3_client.download_fileobj.assert_called_once()
        call_args = mock_s3_client.download_fileobj.call_args[1]
        self.assertEqual(call_args['Bucket'], 'test-bucket')
        self.assertEqual(call_args['Key'], expected_key)
        
        # Verify result is BytesIO
        self.assertIsInstance(result, BytesIO)

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    def test_minio_delete(self, mock_boto3_client):
        """Test deleting file from MinIO"""
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None
        
        storage_key = "test-key-delete"
        
        backend = MinIOStorageBackend()
        backend.delete(storage_key)
        
        # Verify delete_object was called
        expected_key = f'photos/{storage_key}.jpg'
        mock_s3_client.delete_object.assert_called_once_with(
            Bucket='test-bucket',
            Key=expected_key
        )

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='',  # No prefix
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    @patch('uuid.uuid4')
    def test_minio_upload_without_prefix(self, mock_uuid, mock_boto3_client):
        """Test MinIO upload without prefix"""
        test_uuid = uuid.UUID('12345678-1234-5678-1234-567812345678')
        mock_uuid.return_value = test_uuid
        
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None
        
        file_content = b"test content"
        uploaded_file = SimpleUploadedFile("test.jpg", file_content)
        
        backend = MinIOStorageBackend()
        backend.upload(uploaded_file)
        
        # Verify key doesn't have prefix
        call_kwargs = mock_s3_client.put_object.call_args[1]
        self.assertEqual(call_kwargs['Key'], f'{test_uuid}.jpg')

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    def test_minio_upload_error_handling(self, mock_boto3_client):
        """Test MinIO upload error handling"""
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None
        mock_s3_client.put_object.side_effect = Exception("Upload failed")
        
        uploaded_file = SimpleUploadedFile("test.jpg", b"content")
        
        backend = MinIOStorageBackend()
        
        with self.assertRaises(Exception):
            backend.upload(uploaded_file)

    @override_settings(
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test_access',
        MINIO_SECRET_KEY='test_secret',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('boto3.client')
    def test_minio_download_error_handling(self, mock_boto3_client):
        """Test MinIO download error handling"""
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None
        mock_s3_client.download_fileobj.side_effect = Exception("Download failed")
        
        backend = MinIOStorageBackend()
        
        with self.assertRaises(Exception):
            backend.download("test-key")


class StorageServiceHelperTest(TestCase):
    """Tests for storage service helper functions"""

    @override_settings(STORAGE_BACKEND='local', MEDIA_ROOT='/tmp/test')
    @patch('gallery.storage_service._storage_backend', None)
    @patch('os.makedirs')
    def test_get_storage_backend_local(self, mock_makedirs):
        """Test get_storage_backend returns LocalStorageBackend"""
        backend = get_storage_backend()
        
        self.assertIsInstance(backend, LocalStorageBackend)

    @override_settings(
        STORAGE_BACKEND='minio',
        MINIO_ENDPOINT_URL='http://minio:9000',
        MINIO_ACCESS_KEY='test',
        MINIO_SECRET_KEY='test',
        MINIO_BUCKET_NAME='test-bucket',
        MINIO_PREFIX='photos',
        MINIO_REGION='us-east-1'
    )
    @patch('gallery.storage_service._storage_backend', None)
    @patch('boto3.client')
    def test_get_storage_backend_minio(self, mock_boto3_client):
        """Test get_storage_backend returns MinIOStorageBackend"""
        mock_s3_client = MagicMock()
        mock_boto3_client.return_value = mock_s3_client
        mock_s3_client.head_bucket.return_value = None
        
        backend = get_storage_backend()
        
        self.assertIsInstance(backend, MinIOStorageBackend)

    @override_settings(STORAGE_BACKEND='local', MEDIA_ROOT='/tmp/test')
    @patch('gallery.storage_service._storage_backend', None)
    @patch('gallery.storage_service.LocalStorageBackend')
    def test_get_storage_backend_singleton(self, mock_local_backend_class):
        """Test get_storage_backend returns same instance (singleton)"""
        mock_backend = MagicMock()
        mock_local_backend_class.return_value = mock_backend
        
        backend1 = get_storage_backend()
        backend2 = get_storage_backend()
        
        self.assertIs(backend1, backend2)
        # Should only create instance once
        self.assertEqual(mock_local_backend_class.call_count, 1)

    @override_settings(MEDIA_ROOT='/tmp/test')
    @patch('gallery.storage_service.get_storage_backend')
    def test_upload_photo_helper(self, mock_get_backend):
        """Test upload_photo helper function"""
        mock_backend = MagicMock()
        mock_backend.upload.return_value = "test-storage-key"
        mock_get_backend.return_value = mock_backend
        
        uploaded_file = SimpleUploadedFile("test.jpg", b"content")
        result = upload_photo(uploaded_file)
        
        self.assertEqual(result, "test-storage-key")
        mock_backend.upload.assert_called_once_with(uploaded_file)

    @patch('gallery.storage_service.get_storage_backend')
    def test_download_photo_helper(self, mock_get_backend):
        """Test download_photo helper function"""
        mock_backend = MagicMock()
        mock_file_obj = BytesIO(b"test content")
        mock_backend.download.return_value = mock_file_obj
        mock_get_backend.return_value = mock_backend
        
        result = download_photo("test-key")
        
        self.assertEqual(result, mock_file_obj)
        mock_backend.download.assert_called_once_with("test-key")

    @patch('gallery.storage_service.get_storage_backend')
    def test_delete_photo_helper(self, mock_get_backend):
        """Test delete_photo helper function"""
        mock_backend = MagicMock()
        mock_get_backend.return_value = mock_backend
        
        delete_photo("test-key")
        
        mock_backend.delete.assert_called_once_with("test-key")
