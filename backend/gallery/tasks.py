import os
import uuid
import numpy as np
from celery import shared_task
from faiss import IndexFlatL2
from qdrant_client import models

from .vision_service import get_image_embedding
from .qdrant_utils import client, IMAGE_COLLECTION_NAME, REFVEC_COLLECTION_NAME
from .models import Tag, User, Photo_Tag

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
def process_and_embed_photo(image_path, user_id, filename, photo_path_id, created_at, lat, lng):
    try:

        # 파일이 실제로 생길 때까지 잠시 대기
        waited = 0
        while not os.path.exists(image_path) and waited < MAX_WAIT:
            time.sleep(WAIT_INTERVAL)
            waited += WAIT_INTERVAL

        if not os.path.exists(image_path):
            print(f"[Celery Task Error] File not found even after waiting: {
                  image_path}")
            return

        embedding = get_image_embedding(image_path)

        if embedding is None:
            print(
                f"[Celery Task Error] Failed to create embedding for {filename}")
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
                "lng": lng
            }
        )

        client.upsert(
            collection_name=IMAGE_COLLECTION_NAME,
            points=[point_to_upsert],
            wait=True
        )
        print(f"[Celery Task Success] Processed and upserted photo {filename}")

    except Exception as e:
        print(f"[Celery Task Exception] Error processing {filename}: {str(e)}")
    finally:
        if os.path.exists(image_path):
            os.remove(image_path)


@shared_task
def create_or_update_tag_embedding(user_id, tag_name, tag_id):
    try:
        model = get_text_model()  # lazy-load
        embedding = model.encode([tag_name])

        user_instance = User.objects.get(id=user_id)

        tag, created = Tag.objects.update_or_create(
            tag_id=tag_id,
            user=user_instance,
            defaults={'tag': tag_name, 'embedding': embedding}
        )

        if created:
            print(f"[Celery Task Success] Created tag '{
                  tag_name}' with id {tag_id}")
        else:
            print(f"[Celery Task Success] Updated tag '{
                  tag_name}' with id {tag_id}")

    except Exception as e:
        print(
            f"[Celery Task Exception] Error creating/updating tag '{tag_name}': {str(e)}")


def create_query_embedding(query):
    model = get_text_model()  # lazy-load
    return model.encode(query)


def tag_recommendation(photo_id):
    tag = "test"
    tag_id = uuid.uuid4()

    return tag, tag_id


def recommend_photo_from_tag(user_id: int, tag_id: int):
    rep_vectors, rep_vec_idx_to_tag = retrieve_all_rep_vectors(user_id)

    faiss_index = build_faiss_index(rep_vectors)

    img_vectors, img_idx_to_metadata = retrieve_images_without_tag(
        user_id, tag_id)

    recommendation_idx = find_similar_images_with_tag(
        tag_id,
        faiss_index,
        rep_vectors,
        rep_vec_idx_to_tag,
        img_vectors,
    )

    return [
        img_idx_to_metadata[idx]
        for idx in recommendation_idx
    ]


def is_valid_uuid(uuid_to_test):
    try:
        uuid.UUID(str(uuid_to_test))
    except ValueError:
        return False
    return True


def retrieve_all_rep_vectors(user_id: int):
    BATCH_SIZE = 256

    rep_vectors = np.array(dtype=np.float32)
    rep_vec_idx_to_tag = []

    user_filter = models.Filter(
        must=[
            models.FieldCondition(
                key="user_id", match=models.MatchValue(value=user_id))
        ]
    )

    offset = 0

    while offset is not None:
        rep_points, offset = client.scroll(
            REFVEC_COLLECTION_NAME,
            scroll_filter=user_filter,
            offset=offset,
            limit=BATCH_SIZE,
            with_payload=True,
            with_vectors=True,
        )

        rep_vectors = np.vstack(
            rep_vectors, [point.vector for point in rep_points])

        rep_vec_idx_to_tag.extend(
            (point.payload["tag_id"] for point in rep_points))

    return rep_vectors, rep_vec_idx_to_tag


def retrieve_images_without_tag(user_id: int, tag_id: uuid.UUID):
    BATCH_SIZE = 256

    all_points = []

    user_filter = models.Filter(
        must=[
            models.FieldCondition(
                key="user_id", match=models.MatchValue(value=user_id)
            )
        ]
    )

    offset = 0

    while offset is not None:
        points, offset = client.scroll(
            IMAGE_COLLECTION_NAME,
            scroll_filter=user_filter,
            offset=offset,
            limit=BATCH_SIZE,
            with_payload=True,
            with_vectors=True,
        )

        all_points.extend(points)

    tagged_photo_ids = Photo_Tag.objects.filter(
        user__id=user_id
    ).filter(
        tag_id=tag_id
    ).values_list("photo_id", flat=True)

    points_without_tag = all_points.filter(
        lambda point: point.payload["photo_id"] not in tagged_photo_ids
    )

    img_vectors = np.ndarray(
        [point.vector for point in points_without_tag], dtype=np.float32)

    img_idx_to_metadata = [
        {
            "photo_id": point.payload["photo_id"],
            "photo_path_id": point.payload["photo_path_id"],
        }
        for point in points_without_tag
    ]

    return img_vectors, img_idx_to_metadata


def build_faiss_index(rep_vectors: np.ndarray):
    # normalize rep. vectors
    norms = np.linalg.norm(rep_vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10
    rep_vectors = rep_vectors / norms

    faiss_index = IndexFlatL2(rep_vectors.shape[1])
    faiss_index.add(rep_vectors)

    return faiss_index


def find_similar_images_with_tag(
    tag_id,
    faiss_index,
    rep_vector,
    rep_vec_idx_to_tag,
    img_vectors
):
    K = 1

    # normalize img vectors
    norms = np.linalg.norm(img_vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10
    img_vectors = img_vectors / norms

    _, top_k_tags = faiss_index.search(img_vectors, K)

    img_idx = [
        idx
        for idx, closest_tags
        in enumerate(top_k_tags)
        if any((rep_vec_idx_to_tag[idx] == tag_id for idx in closest_tags))
    ]

    return img_idx
