from asyncio import gather
from io import BytesIO
from typing import List

from PIL import Image
from fastapi import FastAPI, UploadFile, File
from sentence_transformers import SentenceTransformer

txt_model = SentenceTransformer("sentence-transformers/clip-ViT-B-32-multilingual-v1")
img_model = SentenceTransformer("clip-ViT-B-32")

app = FastAPI(
    title="Embedding API service",
    description="API to get embeddings for text and images",
)


@app.get("/ping")
def ping():
    return "pong"


@app.post("/embed/text")
def embed_text(text: str):
    embedding = txt_model.encode(text)
    return {"text": text, "embedding": embedding.tolist()}


@app.post("/embed/image")
async def embed_image(files: List[UploadFile] = [File(...)]):
    async def read_image(file: UploadFile) -> Image.Image:
        contents = await file.read()
        return Image.open(BytesIO(contents))

    images = await gather(*(read_image(file) for file in files))

    embeddings = img_model.encode(images)

    results = [
        {"filename": file.filename, "embedding": embedding.tolist()}
        for file, embedding in zip(files, embeddings)
    ]

    return results
