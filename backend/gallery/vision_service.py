from transformers import CLIPProcessor, CLIPModel
from PIL import Image
import torch

MODEL_ID = "openai/clip-vit-base-patch32"

processor = CLIPProcessor.from_pretrained(MODEL_ID)
model = CLIPModel.from_pretrained(MODEL_ID)

def get_image_embedding(image_file):
    try:
        image = Image.open(image_file).convert("RGB")
        inputs = processor(images=image, return_tensors="pt")

        with torch.no_grad():
            image_features = model.get_image_features(**inputs)

        embedding = image_features[0].tolist()
        return embedding

    except Exception as e:
        print(f"Error creating CLIP embedding: {e}")
        return None