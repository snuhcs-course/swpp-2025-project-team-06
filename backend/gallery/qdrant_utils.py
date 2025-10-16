from django.conf import settings
from qdrant_client import QdrantClient, models

QDRANT_URL = settings.QDRANT_CLUSTER_URL
QDRANT_API_KEY = settings.QDRANT_API_KEY

client = QdrantClient(
    url=QDRANT_URL,
    api_key=QDRANT_API_KEY,
)

IMAGE_COLLECTION_NAME = "my_image_collection"

def create_image_collection():
    try:
        client.get_collection(collection_name=IMAGE_COLLECTION_NAME)
        print(f"Collection '{IMAGE_COLLECTION_NAME}' already exists.")
    except Exception:
        client.create_collection(
            collection_name=IMAGE_COLLECTION_NAME,
            vectors_config=models.VectorParams(size=512, distance=models.Distance.COSINE),
        )
        print(f"Collection '{IMAGE_COLLECTION_NAME}' created.")

def ensure_indexes():
    indexes = {
        "user_id": models.PayloadSchemaType.INTEGER,
        "filename": models.PayloadSchemaType.KEYWORD,
        "photo_path_id": models.PayloadSchemaType.INTEGER,
        "created_at": models.PayloadSchemaType.DATETIME,
        "lat": models.PayloadSchemaType.FLOAT,
        "lng": models.PayloadSchemaType.FLOAT,
    }

    for field, schema in indexes.items():
        try:
            client.create_payload_index(
                collection_name=IMAGE_COLLECTION_NAME,
                field_name=field,
                field_schema=schema,
            )
            print(f"Index created for '{field}' ({schema}).")
        except Exception as e:
            print(f"(Info) Index for '{field}' may already exist: {e}")

create_image_collection()

ensure_indexes()