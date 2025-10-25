import os
import uuid
from celery import shared_task
from qdrant_client import models

from .vision_service import get_image_embedding, get_image_captions
from .qdrant_utils import client, IMAGE_COLLECTION_NAME
from .models import User, Photo_Caption, Caption

import time

from sentence_transformers import SentenceTransformer

_TEXT_MODEL_NAME = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
_text_model = None  # 전역 캐시

MAX_WAIT = 2.0  # 최대 2초 대기
WAIT_INTERVAL = 0.1


def get_text_model():
    """Lazy-load text model inside worker"""
    global _text_model
    if _text_model is None:
        print("[INFO] Loading text model inside worker...")
        _text_model = SentenceTransformer(_TEXT_MODEL_NAME)
    return _text_model


@shared_task
def process_and_embed_photo(
    image_path, user_id, filename, photo_path_id, created_at, lat, lng
):
    try:
        # 파일이 실제로 생길 때까지 잠시 대기
        waited = 0
        while not os.path.exists(image_path) and waited < MAX_WAIT:
            time.sleep(WAIT_INTERVAL)
            waited += WAIT_INTERVAL

        if not os.path.exists(image_path):
            print(
                f"[Celery Task Error] File not found even after waiting: {image_path}"
            )
            return

        embedding = get_image_embedding(image_path)

        if embedding is None:
            print(f"[Celery Task Error] Failed to create embedding for {filename}")
            if os.path.exists(image_path):
                os.remove(image_path)
            return

        photo_id = uuid.uuid4()
        point_to_upsert = models.PointStruct(
            id=str(photo_id),
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

        client.upsert(
            collection_name=IMAGE_COLLECTION_NAME, points=[point_to_upsert], wait=True
        )
        print(f"[Celery Task Success] Processed and upserted photo {filename}")

        captions = get_image_captions(image_path)

        # asserts that user id has checked
        user = User.objects.get(id=user_id)

        for word, count in captions.items():
            caption, _ = Caption.objects.get_or_create(
                user=user,
                caption=word,
            )

            _ = Photo_Caption.objects.create(
                user=user,
                photo_id=photo_id,
                caption=caption,
                weight=count,
            )

    except Exception as e:
        print(f"[Celery Task Exception] Error processing {filename}: {str(e)}")
    finally:
        if os.path.exists(image_path):
            os.remove(image_path)


@shared_task
def create_query_embedding(query):
    model = get_text_model()  # lazy-load
    return model.encode(query)
