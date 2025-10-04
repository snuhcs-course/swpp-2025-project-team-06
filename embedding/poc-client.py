from pathlib import Path
from shutil import copy
from sys import exit
from time import time

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct
from requests import post
from requests.exceptions import RequestException

EMBEDDING_URL = "http://localhost:8888"
N_LIMIT = 5
ID_OFFSET = 0


def embed_query(query):
    try:
        response = post(
            f"{EMBEDDING_URL}/embed/text",
            params={
                "text": query,
            },
        )
        response.raise_for_status()
        return response.json()["embedding"]
    except RequestException as e:
        print(f"Error embedding text: {e}")
        exit(1)


def embed_images(filenames):
    fds = [(filename, open(filename, "rb")) for filename in filenames]
    files = [("files", (filename, fd, "image/jpeg")) for filename, fd in fds]
    try:
        response = post(
            f"{EMBEDDING_URL}/embed/image",
            files=files,
        )

        for _, fd in fds:
            fd.close()

        response.raise_for_status()
        return response.json()
    except RequestException as e:
        print(f"Error embedding image: {e}")
        exit(1)


def group_images(query, filenames):
    out_dir = Path(f"output/{query}")
    out_dir.mkdir(parents=True, exist_ok=True)

    for filename in filenames:
        infile = Path(filename)
        outfile = out_dir / infile.name

        copy(infile, outfile)


def search_images(client, query):
    qv = embed_query(query)

    results = client.query_points(
        collection_name="test",
        query=qv,
        with_payload=["filename"],
        limit=N_LIMIT,
    ).points

    group_images(query, [result.payload["filename"] for result in results])


def insert_images(client):
    global ID_OFFSET
    BATCH_SIZE = 256

    img_dir = Path("images")
    images = [str(p) for p in img_dir.glob("*.jpg")]

    for offset in range(0, len(images), BATCH_SIZE):
        st = time()

        batch_files = images[offset : offset + BATCH_SIZE]

        results = embed_images(batch_files)

        points = [
            PointStruct(
                id=ID_OFFSET + i,
                vector=result["embedding"],
                payload={"filename": result["filename"]},
            )
            for i, result in enumerate(results)
        ]

        client.upsert(
            collection_name="test",
            points=points,
            wait=True,
        )

        ID_OFFSET += len(results)

        et = time()

        print(f"inserted {len(batch_files)} images in {(et - st) * 1000}ms")


def main():
    client = QdrantClient(host="localhost", port=6333)

    if not client.collection_exists(collection_name="test"):
        client.create_collection(
            collection_name="test",
            vectors_config=VectorParams(size=512, distance=Distance.COSINE),
        )

    selection = input("insert images? (y/N)").strip()
    if selection and selection[0].lower() == "y":
        insert_images(client)

    while True:
        query = input("query> ").strip()

        if not query:
            break

        search_images(client, query)


if __name__ == "__main__":
    main()
