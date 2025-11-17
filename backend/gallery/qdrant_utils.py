import threading

from django.conf import settings
from qdrant_client import QdrantClient, models
from qdrant_client.http.exceptions import UnexpectedResponse

# 싱글톤 인스턴스
_qdrant_client = None
_qdrant_lock = threading.Lock()

def get_qdrant_client():
    # 싱글톤 Qdrant Client 인스턴스 반환 (Thread-safe)
    global _qdrant_client
    if _qdrant_client is None:
        with _qdrant_lock:
            # Double-checked locking pattern
            if _qdrant_client is None:
                _qdrant_client = QdrantClient(
                    url=settings.QDRANT_CLUSTER_URL,
                    api_key=settings.QDRANT_API_KEY,
                )
    return _qdrant_client


IMAGE_COLLECTION_NAME = "my_image_collection"
REPVEC_COLLECTION_NAME = "my_repvec_collection"
TAG_PRESET_COLLECTION_NAME = "tag_recommendation_preset"


def initialize_qdrant():
    client = get_qdrant_client()
    try:
        client.get_collection(collection_name=IMAGE_COLLECTION_NAME)
    except (UnexpectedResponse, ValueError):
        client.create_collection(
            collection_name=IMAGE_COLLECTION_NAME,
            vectors_config=models.VectorParams(
                size=512, distance=models.Distance.COSINE
            ),
        )
        print(f"Collection '{IMAGE_COLLECTION_NAME}' created.")

    try:
        client.get_collection(collection_name=REPVEC_COLLECTION_NAME)
    except (UnexpectedResponse, ValueError):
        client.create_collection(
            collection_name=REPVEC_COLLECTION_NAME,
            vectors_config=models.VectorParams(
                size=512, distance=models.Distance.COSINE
            ),
        )
        print(f"Collection '{REPVEC_COLLECTION_NAME}' created.")

    image_indexes = {
        "user_id": models.PayloadSchemaType.INTEGER,
        "filename": models.PayloadSchemaType.KEYWORD,
        "photo_path_id": models.PayloadSchemaType.INTEGER,
        "created_at": models.PayloadSchemaType.DATETIME,
        "lat": models.PayloadSchemaType.FLOAT,
        "lng": models.PayloadSchemaType.FLOAT,
        "isTagged": models.PayloadSchemaType.BOOL,
    }

    for field, schema in image_indexes.items():
        try:
            client.create_payload_index(
                collection_name=IMAGE_COLLECTION_NAME,
                field_name=field,
                field_schema=schema,
            )
        except UnexpectedResponse:
            pass

    repvec_indexes = {
        "user_id": models.PayloadSchemaType.INTEGER,
        "tag_id": models.PayloadSchemaType.KEYWORD,
    }

    for field, schema in repvec_indexes.items():
        try:
            client.create_payload_index(
                collection_name=REPVEC_COLLECTION_NAME,
                field_name=field,
                field_schema=schema,
            )
        except UnexpectedResponse:
            pass


if __name__ == "__main__":
    initialize_qdrant()
