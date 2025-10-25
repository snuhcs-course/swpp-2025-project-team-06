import os
import uuid
from collections import defaultdict
from celery import shared_task
from qdrant_client import models

from .vision_service import get_image_embedding
from .qdrant_utils import client, IMAGE_COLLECTION_NAME, REFVEC_COLLECTION_NAME
from .models import Photo_Tag

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

    except Exception as e:
        print(f"[Celery Task Exception] Error processing {filename}: {str(e)}")
    finally:
        if os.path.exists(image_path):
            os.remove(image_path)


@shared_task
def create_query_embedding(query):
    model = get_text_model()  # lazy-load
    return model.encode(query)


def tag_recommendation(photo_id):
    tag = "test"
    tag_id = uuid.uuid4()

    return tag, tag_id


# aggregates N similarity queries with Reciprocal Rank Fusion
def recommend_photo_from_tag(user_id: int, tag_id: uuid.UUID):
    LIMIT = 40
    RRF_CONSTANT = 40

    rep_vectors = retrieve_all_rep_vectors_of_tag(user_id, tag_id)

    rrf_scores = defaultdict(float)
    photo_id_to_path_id = {}

    for rep_vector in rep_vectors:
        img_points = client.query_points(
            IMAGE_COLLECTION_NAME,
            query=rep_vector,
            with_payload=["photo_id", "photo_path_id"],
            limit=LIMIT,
        ).points

        for i, img_point in enumerate(img_points):
            photo_id = img_point.payload["photo_id"]
            photo_path_id = img_point.payload["photo_path_id"]

            rrf_scores[photo_id] = rrf_scores[photo_id] + 1 / (RRF_CONSTANT + i + 1)
            photo_id_to_path_id[photo_id] = photo_path_id

    rrf_sorted = sorted(
        rrf_scores.items(),
        key=lambda item: item[1],
        reverse=True,
    )

    tagged_photo_ids = (
        Photo_Tag.objects.filter(user__id=user_id)
        .filter(tag_id=tag_id)
        .values_list("photo_id", flat=True)
    )

    recommendations = [
        {"photo_id": photo_id, "photo_path_id": photo_id_to_path_id[photo_id]}
        for (photo_id, _) in rrf_sorted
        if photo_id not in tagged_photo_ids
    ][:LIMIT]

    return recommendations


def is_valid_uuid(uuid_to_test):
    try:
        uuid.UUID(str(uuid_to_test))
    except ValueError:
        return False
    return True


def retrieve_all_rep_vectors_of_tag(user_id: int, tag_id: uuid.UUID):
    LIMIT = 32  # assert max num of rep vectors <= 32

    filters = models.Filter(
        must=[
            models.FieldCondition(
                key="user_id", match=models.MatchValue(value=user_id)
            ),
            models.FieldCondition(
                key="tag_id", match=models.MatchValue(value=str(tag_id))
            ),
        ]
    )

    rep_points, _ = client.scroll(
        REFVEC_COLLECTION_NAME,
        scroll_filter=filters,
        limit=LIMIT,
        with_vectors=True,
    )

    rep_vectors: list[list[float]] = [point.vector for point in rep_points]

    return rep_vectors
