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

import uuid
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

# Global model caches (lazy-loaded)
_image_model = None
_caption_processor = None
_caption_model = None

# Stop words for caption processing
STOP_WORDS = {
    "a", "an", "the", "in", "on", "of", "at", "for", "with", "by", "about",
    "is", "are", "was", "were", "it", "this", "that", "and", "or", "but",
    "photo", "picture", "image",
}


# ============================================================================
# Vision Model Loaders
# ============================================================================

def get_image_model():
    """Lazy-load CLIP image model once per worker"""
    global _image_model
    if _image_model is None:
        print("[INFO] Loading CLIP image model inside worker...")
        _image_model = SentenceTransformer("clip-ViT-B-32")
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
        print("[INFO] Loading BLIP captioning model inside worker...")
        _caption_model = BlipForConditionalGeneration.from_pretrained(
            "Salesforce/blip-image-captioning-base",
            dtype=torch.float16,
        )
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
        print(f"[INFO] Generating embedding from image data ...", flush=True)

        image = Image.open(image_data).convert("RGB")
        model = get_image_model()

        with torch.no_grad():
            embedding = model.encode(image)

        print(f"[DONE] Finished image embedding\n", flush=True)
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

    print(f"[DONE] Finished caption generation\n", flush=True)
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


