"""
GPU-dependent Celery tasks for image processing.

This module contains:
- Vision model loading (CLIP for embeddings, BLIP for captions)
- Image inference functions
- Async Celery task: process_and_embed_photo

These require GPU-enabled Celery workers.

To run workers:
    celery -A config worker --loglevel=info
"""

import re
import torch
from io import BytesIO
from collections import Counter
from itertools import chain
from celery import shared_task
from qdrant_client import models
from sentence_transformers import SentenceTransformer
from transformers import BlipProcessor, BlipForConditionalGeneration
from PIL import Image

from .qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME
from .models import User, Photo_Caption, Caption, Photo

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

_IMAGE_MODEL_NAME = "clip-ViT-B-32"

# Global model caches (lazy-loaded)
_image_model = None

_caption_processor = None
_caption_model = None

# Stop words for caption processing
STOP_WORDS = {
    "a",
    "an",
    "the",
    "in",
    "on",
    "of",
    "at",
    "for",
    "with",
    "by",
    "about",
    "is",
    "are",
    "was",
    "were",
    "it",
    "this",
    "that",
    "and",
    "or",
    "but",
    "photo",
    "picture",
    "image",
}


# ============================================================================
# Vision Model Loaders
# ============================================================================


def get_image_model():
    """Lazy-load CLIP image model once per worker"""
    global _image_model
    if _image_model is None:
        print(f"[INFO] Loading CLIP image model ({_IMAGE_MODEL_NAME}) on {DEVICE}...")
        _image_model = SentenceTransformer(_IMAGE_MODEL_NAME, device=DEVICE)
    return _image_model


def get_caption_processor():
    """Lazy-load BLIP caption processor"""
    global _caption_processor
    if _caption_processor is None:
        print("[INFO] Loading BLIP captioning processor inside worker...")
        _caption_processor = BlipProcessor.from_pretrained(
            "Salesforce/blip-image-captioning-base",
        )
    return _caption_processor


def get_caption_model():
    """Lazy-load BLIP caption model"""
    global _caption_model
    if _caption_model is None:
        print(f"[INFO] Loading BLIP captioning model on {DEVICE}...")
        _caption_model = BlipForConditionalGeneration.from_pretrained(
            "Salesforce/blip-image-captioning-base",
            torch_dtype=torch.float16 if DEVICE == "cuda" else torch.float32,
        ).to(DEVICE)
    return _caption_model


# ============================================================================
# Vision Inference Functions
# ============================================================================


def get_image_embedding(image_data: BytesIO):
    """
    Generate CLIP embedding for an image.

    Args:
        image_data: BytesIO object containing image data

    Returns:
        Image embedding vector or None on error
    """
    try:
        print("[INFO] Generating embedding from image data ...", flush=True)

        image = Image.open(image_data).convert("RGB")
        model = get_image_model()

        with torch.no_grad():
            embedding = model.encode(image)

        print("[DONE] Finished image embedding\n", flush=True)
        return embedding

    except Exception as e:
        print(f"Error creating CLIP embedding: {e}")
        return None


def get_image_captions(image_data: BytesIO) -> dict[str, int]:
    """
    Generate BLIP captions for an image and extract keywords.

    Args:
        image_data: BytesIO object containing image data

    Returns:
        Dictionary of word -> count from generated captions
    """
    processor = get_caption_processor()
    model = get_caption_model()

    image = Image.open(image_data).convert("RGB")

    inputs = processor(images=image, return_tensors="pt")  # pyright: ignore[reportCallIssue]
    # Move inputs to the same device as the model
    inputs = {k: v.to(DEVICE) for k, v in inputs.items()}

    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=20,
            do_sample=True,
            top_k=50,
            top_p=0.95,
            num_return_sequences=5,
        )

        phrases: list[str] = [
            processor.decode(output, skip_special_tokens=True) for output in outputs
        ]

    counter = Counter(
        list(chain.from_iterable((phrase_to_words(phrase) for phrase in phrases)))
    )

    print("[DONE] Finished caption generation\n", flush=True)
    return dict(counter)


def phrase_to_words(text: str) -> list[str]:
    """
    Convert a caption phrase to a list of meaningful words.

    Filters out stop words, punctuation, numbers, and short words.

    Args:
        text: Caption phrase

    Returns:
        List of filtered words
    """
    text = text.lower()  # unified in lowercase
    cleaned_text = re.sub(r"[^a-zA-Z\s]", "", text)

    words = set(cleaned_text.split())  # split to words
    if not words:
        return []

    final_words = [
        word
        for word in words
        if word not in STOP_WORDS and len(word) > 1  # filter stop words and short words
    ]

    return final_words


# ============================================================================
# Batch Vision Inference Functions
# ============================================================================


def get_image_embeddings_batch(image_data_list: list[BytesIO]):
    """
    Generate CLIP embeddings for multiple images at once (batch processing).

    Args:
        image_data_list: List of BytesIO objects containing image data

    Returns:
        List of image embedding vectors or None for failed images
    """
    try:
        print(f"[INFO] Generating embeddings for {len(image_data_list)} images in batch...", flush=True)

        images = []
        valid_indices = []

        for i, image_data in enumerate(image_data_list):
            try:
                image = Image.open(image_data).convert("RGB")
                images.append(image)
                valid_indices.append(i)
            except Exception as e:
                print(f"[ERROR] Failed to load image {i}: {e}")
                continue

        if not images:
            print("[ERROR] No valid images to process")
            return [None] * len(image_data_list)

        model = get_image_model()

        with torch.no_grad():
            # SentenceTransformer's encode can handle batches directly
            embeddings = model.encode(images, batch_size=len(images), show_progress_bar=False)

        # Create result list with None for failed images
        results = [None] * len(image_data_list)
        for idx, embedding in zip(valid_indices, embeddings):
            results[idx] = embedding

        print(f"[DONE] Finished batch embedding for {len(valid_indices)}/{len(image_data_list)} images\n", flush=True)
        return results

    except Exception as e:
        print(f"Error creating batch CLIP embeddings: {e}")
        return [None] * len(image_data_list)


def get_image_captions_batch(image_data_list: list[BytesIO]) -> list[dict[str, int]]:
    """
    Generate BLIP captions for multiple images at once (batch processing).

    Args:
        image_data_list: List of BytesIO objects containing image data

    Returns:
        List of dictionaries (word -> count) from generated captions
    """
    processor = get_caption_processor()
    model = get_caption_model()

    images = []
    valid_indices = []

    for i, image_data in enumerate(image_data_list):
        try:
            image = Image.open(image_data).convert("RGB")
            images.append(image)
            valid_indices.append(i)
        except Exception as e:
            print(f"[ERROR] Failed to load image {i} for captioning: {e}")
            continue

    if not images:
        print("[ERROR] No valid images to caption")
        return [{}] * len(image_data_list)

    # Process all images at once
    inputs = processor(images=images, return_tensors="pt", padding=True)  # pyright: ignore[reportCallIssue]
    # Move inputs to the same device as the model
    inputs = {k: v.to(DEVICE) for k, v in inputs.items()}

    with torch.no_grad():
        # Generate captions for all images in batch
        # We generate 5 captions per image
        num_images = len(images)
        outputs = model.generate(
            **inputs,
            max_new_tokens=20,
            do_sample=True,
            top_k=50,
            top_p=0.95,
            num_return_sequences=5,
        )

        # Decode all outputs
        all_phrases: list[str] = [
            processor.decode(output, skip_special_tokens=True) for output in outputs
        ]

    # Group captions by image (5 captions per image)
    results = [{}] * len(image_data_list)

    for idx, img_idx in enumerate(valid_indices):
        # Get the 5 captions for this image
        start = idx * 5
        end = start + 5
        phrases = all_phrases[start:end]

        counter = Counter(
            list(chain.from_iterable((phrase_to_words(phrase) for phrase in phrases)))
        )

        results[img_idx] = dict(counter)

    print(f"[DONE] Finished batch caption generation for {len(valid_indices)}/{len(image_data_list)} images\n", flush=True)
    return results


# ============================================================================
# Celery Tasks
# ============================================================================


@shared_task
def process_and_embed_photo(
    storage_key, user_id, filename, photo_path_id, created_at, lat, lng
):
    """
    GPU Task: Process photo and generate embeddings/captions.

    This task downloads the photo from storage, generates CLIP embeddings and BLIP captions,
    then uploads the results to Qdrant and Django DB.

    Args:
        storage_key: Unique identifier for the photo in storage
        user_id: ID of the user who uploaded the photo
        filename: Original filename (for metadata)
        photo_path_id: Client-side photo identifier
        created_at: Photo creation timestamp
        lat: Latitude
        lng: Longitude
    """
    from .storage_service import download_photo, delete_photo

    image_data = None
    try:
        client = get_qdrant_client()

        # Download from shared storage to memory (MinIO or local)
        image_data = download_photo(storage_key)

        embedding = get_image_embedding(image_data)

        if embedding is None:
            print(f"[Celery Task Error] Failed to create embedding for {filename}")
            return

        point_to_upsert = models.PointStruct(
            id=str(storage_key),
            vector=embedding,
            payload={
                "user_id": user_id,
                "filename": filename,
                "photo_path_id": photo_path_id,
                "created_at": created_at,
                "lat": lat,
                "lng": lng,
            },
        )

        # asserts that user id has checked
        user = User.objects.get(id=user_id)

        photo = Photo.objects.get(photo_id=storage_key)

        client.upsert(
            collection_name=IMAGE_COLLECTION_NAME, points=[point_to_upsert], wait=True
        )

        # Reset BytesIO position for captions (image_data was already read by embedding)
        image_data.seek(0)
        captions = get_image_captions(image_data)

        for word, count in captions.items():
            caption, _ = Caption.objects.get_or_create(
                user=user,
                caption=word,
            )

            _ = Photo_Caption.objects.create(
                user=user,
                photo=photo,
                caption=caption,
                weight=count,
            )

        print(f"[Celery Task Success] Processed and upserted photo {filename}")

    except Exception as e:
        print(f"[Celery Task Exception] Error processing {filename}: {str(e)}")
    finally:
        # Cleanup in-memory buffer
        if image_data is not None:
            image_data.close()
        # Cleanup storage
        delete_photo(storage_key)


@shared_task
def process_and_embed_photos_batch(photos_metadata: list[dict]):
    """
    GPU Task: Batch process multiple photos and generate embeddings/captions.

    This task downloads multiple photos from storage, generates CLIP embeddings and BLIP captions
    in batches for better GPU efficiency, then uploads the results to Qdrant and Django DB.

    Args:
        photos_metadata: List of photo metadata dictionaries, each containing:
            - storage_key: Unique identifier for the photo in storage
            - user_id: ID of the user who uploaded the photo
            - filename: Original filename (for metadata)
            - photo_path_id: Client-side photo identifier
            - created_at: Photo creation timestamp
            - lat: Latitude
            - lng: Longitude
    """
    from .storage_service import download_photo, delete_photo

    if not photos_metadata:
        print("[Celery Batch Task] No photos to process")
        return

    print(f"[Celery Batch Task] Processing batch of {len(photos_metadata)} photos")

    image_data_list = []
    storage_keys = []

    try:
        client = get_qdrant_client()

        # Step 1: Download all photos from storage
        for metadata in photos_metadata:
            storage_key = metadata["storage_key"]
            try:
                image_data = download_photo(storage_key)
                image_data_list.append(image_data)
                storage_keys.append(storage_key)
            except Exception as e:
                print(f"[Celery Batch Task Error] Failed to download photo {storage_key}: {str(e)}")
                image_data_list.append(None)
                storage_keys.append(storage_key)

        # Step 2: Generate embeddings in batch
        embeddings = get_image_embeddings_batch(
            [img for img in image_data_list if img is not None]
        )

        # Step 3: Reset BytesIO positions and generate captions in batch
        for img_data in image_data_list:
            if img_data is not None:
                img_data.seek(0)

        captions_list = get_image_captions_batch(
            [img for img in image_data_list if img is not None]
        )

        # Step 4: Store results for each photo
        points_to_upsert = []
        valid_idx = 0  # Track index in the filtered (non-None) lists

        for i, metadata in enumerate(photos_metadata):
            storage_key = metadata["storage_key"]
            user_id = metadata["user_id"]
            filename = metadata["filename"]
            photo_path_id = metadata["photo_path_id"]
            created_at = metadata["created_at"]
            lat = metadata["lat"]
            lng = metadata["lng"]

            # Skip if image download failed
            if image_data_list[i] is None:
                print(f"[Celery Batch Task] Skipping failed photo {filename}")
                continue

            embedding = embeddings[valid_idx]
            captions = captions_list[valid_idx]
            valid_idx += 1

            if embedding is None:
                print(f"[Celery Batch Task Error] Failed to create embedding for {filename}")
                continue

            # Prepare Qdrant point
            point_to_upsert = models.PointStruct(
                id=str(storage_key),
                vector=embedding,
                payload={
                    "user_id": user_id,
                    "filename": filename,
                    "photo_path_id": photo_path_id,
                    "created_at": created_at,
                    "lat": lat,
                    "lng": lng,
                },
            )
            points_to_upsert.append(point_to_upsert)

            # Store captions in Django DB
            try:
                user = User.objects.get(id=user_id)
                photo = Photo.objects.get(photo_id=storage_key)

                for word, count in captions.items():
                    caption, _ = Caption.objects.get_or_create(
                        user=user,
                        caption=word,
                    )

                    Photo_Caption.objects.create(
                        user=user,
                        photo=photo,
                        caption=caption,
                        weight=count,
                    )

                print(f"[Celery Batch Task] Successfully processed photo {filename}")

            except Exception as e:
                print(f"[Celery Batch Task Exception] Error storing captions for {filename}: {str(e)}")

        # Step 5: Batch upsert to Qdrant
        if points_to_upsert:
            client.upsert(
                collection_name=IMAGE_COLLECTION_NAME,
                points=points_to_upsert,
                wait=True,
            )
            print(f"[Celery Batch Task Success] Upserted {len(points_to_upsert)} photos to Qdrant")

    except Exception as e:
        print(f"[Celery Batch Task Exception] Error in batch processing: {str(e)}")

    finally:
        # Cleanup: Close all in-memory buffers and delete from storage
        for image_data in image_data_list:
            if image_data is not None:
                image_data.close()

        for storage_key in storage_keys:
            try:
                delete_photo(storage_key)
            except Exception as e:
                print(f"[Celery Batch Task] Failed to delete photo {storage_key}: {str(e)}")
