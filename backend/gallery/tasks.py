import os
import uuid
from celery import shared_task
from qdrant_client import models

from .vision_service import get_image_embedding
from .qdrant_utils import client, IMAGE_COLLECTION_NAME
from .models import Tag, User

import numpy as np
import faiss

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
            print(f"[Celery Task Error] File not found even after waiting: {image_path}")
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
            print(f"[Celery Task Success] Created tag '{tag_name}' with id {tag_id}")
        else:
            print(f"[Celery Task Success] Updated tag '{tag_name}' with id {tag_id}")

    except Exception as e:
        print(f"[Celery Task Exception] Error creating/updating tag '{tag_name}': {str(e)}")


def create_query_embedding(query):
    model = get_text_model()  # lazy-load
    return model.encode(query)

def tag_recommendation(user_id, photo_id):
    refvec_points = []
    
    user_filter = models.Filter(
                must=[
                    models.FieldCondition(
                        key="user_id",
                        match=models.MatchValue(value=user_id),
                    )
                ]
            )
    next_offset = None
    while True:
        points, next_offset = client.scroll(
            collection_name=IMAGE_COLLECTION_NAME,
            scroll_filter=user_filter,
            limit=200,
            offset=next_offset, 
            with_payload=True
        )
        
        refvec_points.extend(points)
        
        if next_offset is None:
            break
    
    all_representative_vectors = []
    
    for point in refvec_points:
        tag = Tag.objects.get(tag_id = point.payload['tag_id'])
        tag_name = tag.tag
        all_representative_vectors.append({"vector":point.vector, "tag_name":tag_name})
    
    retrieved_points = client.retrieve(
        collection_name=IMAGE_COLLECTION_NAME,
        ids=photo_id,
        with_payload=True, 
        with_vectors=True,
    )
    
    image_vec = retrieved_points.vector
    image_vector = np.array(image_vec)
    
    tag = find_most_similar_tag(image_vector, all_representative_vectors)
    tag_id = Tag.objects.get(tag = tag).tag_id
    
    return tag, tag_id


def find_most_similar_tag(image_vector: np.ndarray, refvecs: list) -> str:
    representative_vectors = np.array([item["vector"] for item in refvecs])
    representative_tags = [item["tag_name"] for item in refvecs]

    d = representative_vectors.shape[1]
    
    norms = np.linalg.norm(representative_vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10
    normalized_vectors = (representative_vectors / norms).astype('float32')

    index = faiss.IndexFlatL2(d)
    index.add(normalized_vectors)

    query_vector = np.array(image_vector).reshape(1, -1).astype('float32')
    norm = np.linalg.norm(query_vector)
    if norm == 0: norm = 1e-10
    normalized_query = query_vector / norm

    _distances, indices = index.search(normalized_query, 1)
    
    most_similar_index = indices[0][0]
    most_similar_tag = representative_tags[most_similar_index]
    
    return most_similar_tag

def is_valid_uuid(uuid_to_test):
    try:
        uuid.UUID(str(uuid_to_test))
    except ValueError:
        return False
    return True
    