from sentence_transformers import SentenceTransformer
from PIL import Image
import torch

_image_model = None  # 전역 캐시

def get_image_model():
    """Lazy-load image model once per worker"""
    global _image_model
    if _image_model is None:
        print("[INFO] Loading CLIP image model inside worker...")
        _image_model = SentenceTransformer("clip-ViT-B-32")
    return _image_model

def get_image_embedding(image_path):
    try:
        print(f"[INFO] Generating embedding for: {image_path} ...", flush=True)   # Debug print statement

        image = Image.open(image_path).convert("RGB")
        model = get_image_model()  # lazy-load

        with torch.no_grad():
            embedding = model.encode(image)

        print(f"[DONE] Finished: {image_path}\n", flush=True)   # Debug print statement

        return embedding

    except Exception as e:
        print(f"Error creating CLIP embedding: {e}")
        return None