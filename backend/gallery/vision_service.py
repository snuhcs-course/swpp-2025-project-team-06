from sentence_transformers import SentenceTransformer
from PIL import Image
import torch


def get_image_embedding(image_path):
    try:
        print(f"[INFO] Generating embedding for: {image_path} ...", flush=True)   # Debug print statement

        image = Image.open(image_path).convert("RGB")
        model = SentenceTransformer('clip-ViT-B-32')
        
        print(f"[DONE] Finished: {image_path}\n", flush=True)   # Debug print statement

        with torch.no_grad():
            embedding = model.encode(image)

        return embedding

    except Exception as e:
        print(f"Error creating CLIP embedding: {e}")
        return None