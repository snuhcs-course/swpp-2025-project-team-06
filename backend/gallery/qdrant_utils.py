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

create_image_collection()