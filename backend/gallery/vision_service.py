import torch
import re
from collections import Counter
from itertools import chain

from transformers import BlipProcessor, BlipForConditionalGeneration
from sentence_transformers import SentenceTransformer
from PIL import Image

_image_model = None  # 전역 캐시
_caption_processor = None
_caption_model = None
_DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")


def get_image_model():
    """Lazy-load image model once per worker"""
    global _image_model
    if _image_model is None:
        print("[INFO] Loading CLIP image model inside worker...")
        _image_model = SentenceTransformer("clip-ViT-B-32")
    return _image_model


def get_caption_processor():
    global _caption_processor

    if _caption_processor is None:
        print("[INFO] Loading BLIP captioning model inside worker...")
        _caption_processor = BlipProcessor.from_pretrained(
            "Salesforce/blip-image-captioning-base",
        )

    return _caption_processor


def get_caption_model():
    global _caption_model

    if _caption_model is None:
        print("[INFO] Loading BLIP captioning model inside worker...")
        _caption_model = BlipForConditionalGeneration.from_pretrained(
            "Salesforce/blip-image-captioning-base",
            dtype=torch.float16,
        )
    return _caption_model


def get_image_embedding(image_path):
    try:
        print(
            f"[INFO] Generating embedding for: {image_path} ...", flush=True
        )  # Debug print statement

        image = Image.open(image_path).convert("RGB")
        model = get_image_model()  # lazy-load

        with torch.no_grad():
            embedding = model.encode(image)

        print(f"[DONE] Finished: {image_path}\n", flush=True)  # Debug print statement

        return embedding

    except Exception as e:
        print(f"Error creating CLIP embedding: {e}")
        return None


def get_image_captions(image_path: str) -> dict[str, int]:
    processor = get_caption_processor()
    model = get_caption_model()

    image = Image.open(image_path).convert("RGB")

    inputs = processor(images=image, return_tensors="pt")  # pyright: ignore[reportCallIssue]

    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=20,
            do_sample=True,
            top_k=50,
            top_p=0.95,
            num_return_sequences=5,
        )

        phrases: list[str] = [
            processor.decode(output, skip_special_tokens=True) for output in outputs
        ]

    counter = Counter(
        list(chain.from_iterable((phrase_to_words(phrase) for phrase in phrases)))
    )

    print(f"[DONE] Finished: {image_path}\n", flush=True)

    return dict(counter)


STOP_WORDS = {
    "a",
    "an",
    "the",
    "in",
    "on",
    "of",
    "at",
    "for",
    "with",
    "by",
    "about",
    "is",
    "are",
    "was",
    "were",
    "it",
    "this",
    "that",
    "and",
    "or",
    "but",
    "photo",
    "picture",
    "image",
}


# caption(sentence) -> word list
def phrase_to_words(text: str) -> list[str]:
    text = text.lower()  # unified in lowercase
    cleaned_text = re.sub(r"[^a-zA-Z\s]", "", text)

    words = set(cleaned_text.split())  # split to words
    if not words:
        return []

    final_words = [
        word
        for word in words
        if word not in STOP_WORDS and len(word) > 1  # filter stop words and short words
    ]

    return final_words
