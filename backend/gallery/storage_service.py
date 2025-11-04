"""
Storage abstraction layer for photo uploads.

Supports both local filesystem (development) and MinIO (distributed workers).
"""

import os
import uuid
from abc import ABC, abstractmethod
from typing import BinaryIO
from io import BytesIO
from django.conf import settings


class StorageBackend(ABC):
    """Abstract base class for storage backends."""

    @abstractmethod
    def upload(self, file_obj: BinaryIO) -> str:
        """
        Upload a file to storage.

        Args:
            file_obj: File-like object to upload

        Returns:
            Storage key (unique identifier for the file)
        """
        pass

    @abstractmethod
    def download(self, storage_key: str) -> BytesIO:
        """
        Download a file from storage to memory.

        Args:
            storage_key: Unique identifier for the file

        Returns:
            BytesIO object containing the file data
        """
        pass

    @abstractmethod
    def delete(self, storage_key: str) -> None:
        """
        Delete a file from storage.

        Args:
            storage_key: Unique identifier for the file
        """
        pass


class LocalStorageBackend(StorageBackend):
    """Local filesystem storage backend for single-host development."""

    def __init__(self):
        self.base_path = settings.MEDIA_ROOT
        os.makedirs(self.base_path, exist_ok=True)

    def upload(self, file_obj: BinaryIO) -> str:
        """Upload file to local filesystem."""
        # Use UUID only for storage key (secure, no user input)
        storage_key = str(uuid.uuid4())
<<<<<<< HEAD
        file_path = os.path.join(self.base_path, f"{storage_key}.jpg")
=======
        file_path = os.path.join(self.base_path, storage_key)
>>>>>>> feat/celery-worker-decoupling

        with open(file_path, 'wb') as f:
            for chunk in file_obj.chunks():
                f.write(chunk)

        print(f"[LocalStorage] Uploaded to: {file_path}")
        return storage_key

    def download(self, storage_key: str) -> BytesIO:
        """Read file from local filesystem into memory."""
        file_path = os.path.join(self.base_path, f"{storage_key}.jpg")

        if not os.path.exists(file_path):
            raise FileNotFoundError(f"File not found: {file_path}")

        file_obj = BytesIO()
        with open(file_path, 'rb') as f:
            file_obj.write(f.read())

        file_obj.seek(0)  # Reset to beginning for reading
        print(f"[LocalStorage] Loaded file into memory: {file_path}")
        return file_obj

    def delete(self, storage_key: str) -> None:
        """Delete file from local filesystem."""
        file_path = os.path.join(self.base_path, f"{storage_key}.jpg")

        if os.path.exists(file_path):
            os.remove(file_path)
            print(f"[LocalStorage] Deleted: {file_path}")
        else:
            print(f"[LocalStorage] File not found for deletion: {file_path}")


class MinIOStorageBackend(StorageBackend):
    """MinIO (S3-compatible) storage backend for distributed workers."""

    def __init__(self):
        try:
            import boto3
        except ImportError:
            raise ImportError(
                "boto3 is required for MinIO storage. Install with: uv add boto3"
            )

        self.endpoint_url = settings.MINIO_ENDPOINT_URL
        self.access_key = settings.MINIO_ACCESS_KEY
        self.secret_key = settings.MINIO_SECRET_KEY
        self.bucket_name = settings.MINIO_BUCKET_NAME
        self.prefix = settings.MINIO_PREFIX
        self.region = settings.MINIO_REGION

        # Initialize S3 client
        self.s3_client = boto3.client(
            's3',
            endpoint_url=self.endpoint_url,
            aws_access_key_id=self.access_key,
            aws_secret_access_key=self.secret_key,
            region_name=self.region,
        )

        # Ensure bucket exists
        self._ensure_bucket_exists()

    def _ensure_bucket_exists(self):
        """Create bucket if it doesn't exist."""
        from botocore.exceptions import ClientError

        try:
            self.s3_client.head_bucket(Bucket=self.bucket_name)
            print(f"[MinIO] Bucket '{self.bucket_name}' exists.")
        except ClientError:
            try:
                self.s3_client.create_bucket(Bucket=self.bucket_name)
                print(f"[MinIO] Created bucket: {self.bucket_name}")
            except ClientError as e:
                print(f"[MinIO] Failed to create bucket: {e}")
                raise

    def _get_object_key(self, storage_key: str) -> str:
        """Generate full object key with prefix."""
        if self.prefix:
            return f"{self.prefix}/{storage_key}"
        return storage_key

    def upload(self, file_obj: BinaryIO) -> str:
        """Upload file to MinIO."""
        # Use UUID only for storage key (secure, no user input)
        storage_key = str(uuid.uuid4())
<<<<<<< HEAD
        object_key = self._get_object_key(f"{storage_key}.jpg")
=======
        object_key = self._get_object_key(storage_key)
>>>>>>> feat/celery-worker-decoupling

        try:
            # Read file content
            file_content = b""
            for chunk in file_obj.chunks():
                file_content += chunk

            # Upload to MinIO
            self.s3_client.put_object(
                Bucket=self.bucket_name,
                Key=object_key,
                Body=file_content,
            )

            print(f"[MinIO] Uploaded: {object_key}")
            return storage_key
        except Exception as e:
            print(f"[MinIO] Upload failed for {storage_key}: {e}")
            raise

    def download(self, storage_key: str) -> BytesIO:
        """Download file from MinIO to memory."""
        object_key = self._get_object_key(f"{storage_key}.jpg")

        file_obj = BytesIO()

        try:
            # Download from MinIO directly to memory
            self.s3_client.download_fileobj(
                Bucket=self.bucket_name,
                Key=object_key,
                Fileobj=file_obj,
            )

            file_obj.seek(0)  # Reset to beginning for reading
            print(f"[MinIO] Downloaded {object_key} to memory")
            return file_obj
        except Exception as e:
            print(f"[MinIO] Download failed for {storage_key}: {e}")
            raise

    def delete(self, storage_key: str) -> None:
        """Delete file from MinIO."""
        object_key = self._get_object_key(f"{storage_key}.jpg")

        try:
            self.s3_client.delete_object(
                Bucket=self.bucket_name,
                Key=object_key,
            )
            print(f"[MinIO] Deleted: {object_key}")
        except Exception as e:
            print(f"[MinIO] Delete failed for {storage_key}: {e}")
            raise


# Singleton instance
_storage_backend = None


def get_storage_backend() -> StorageBackend:
    """
    Get the configured storage backend (singleton).

    Returns:
        StorageBackend instance (LocalStorageBackend or MinIOStorageBackend)
    """
    global _storage_backend

    if _storage_backend is None:
        backend_type = getattr(settings, 'STORAGE_BACKEND', 'local')

        if backend_type == 'minio':
            print("[StorageService] Using MinIO storage backend")
            _storage_backend = MinIOStorageBackend()
        else:
            print("[StorageService] Using local filesystem storage backend")
            _storage_backend = LocalStorageBackend()

    return _storage_backend


# Helper functions for convenience
def upload_photo(file_obj: BinaryIO) -> str:
    """
    Upload a photo to storage.

    Args:
        file_obj: File-like object to upload

    Returns:
        Storage key (unique identifier)
    """
    backend = get_storage_backend()
    return backend.upload(file_obj)


def download_photo(storage_key: str) -> BytesIO:
    """
    Download a photo from storage to memory.

    Args:
        storage_key: Unique identifier for the file

    Returns:
        BytesIO object containing the file data
    """
    backend = get_storage_backend()
    return backend.download(storage_key)


def delete_photo(storage_key: str) -> None:
    """
    Delete a photo from storage.

    Args:
        storage_key: Unique identifier for the file
    """
    backend = get_storage_backend()
    backend.delete(storage_key)
