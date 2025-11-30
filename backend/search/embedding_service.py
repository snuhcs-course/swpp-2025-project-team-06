"""
Text embedding service for semantic search.

This module provides text-to-vector conversion using CLIP's multilingual text encoder.
Used for semantic photo search by converting query strings to embeddings.

Note: This runs synchronously on the Django server during search requests,
not as an async Celery task.
"""

import threading
import torch
from sentence_transformers import SentenceTransformer

# Model configuration
_TEXT_MODEL_NAME = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
_text_model = None  # Global cache
_text_model_lock = threading.Lock()

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"


def get_text_model():
    """
    Lazy-load CLIP text model for query embeddings (thread-safe).

    Model is loaded once and cached globally for the Django process.
    Uses double-check locking pattern to prevent race conditions.

    Returns:
        SentenceTransformer: CLIP multilingual text encoder
    """
    global _text_model
    if _text_model is None:
        with _text_model_lock:
            # Double-check pattern: another thread might have initialized while we waited
            if _text_model is None:
                print("[INFO] Loading CLIP text model for search...")
                _text_model = SentenceTransformer(_TEXT_MODEL_NAME, device=DEVICE)
    return _text_model


def create_query_embedding(query: str):
    """
    Generate text embedding for a search query.

    Converts text queries into vectors that can be compared with image embeddings
    for semantic search.

    Args:
        query: Search query string

    Returns:
        List of floats representing the query embedding vector
    """
    model = get_text_model()
    return model.encode(query).tolist()
