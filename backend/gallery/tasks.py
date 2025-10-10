import os
import uuid
from celery import shared_task
from qdrant_client import models

from .vision_service import get_image_embedding
from .qdrant_utils import client, IMAGE_COLLECTION_NAME
from .models import Tag, User

from sentence_transformers import SentenceTransformer
TEXT_MODEL_NAME = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
text_model = SentenceTransformer(TEXT_MODEL_NAME)


@shared_task
def process_and_embed_photo(image_path, user_id, filename, photo_path_id, created_at, lat, lng):
    try:
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
        embedding = text_model.encode(tag_name).tolist()
        
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