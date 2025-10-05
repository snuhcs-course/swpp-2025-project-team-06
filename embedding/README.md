# iter1_demo-embedding

Simple text/image embedding API service has been implemented.

PoC client is provided to test image retrieval via natural language search.

## Features

Embedding API provides following endpoints:
- GET `/ping`: healthcheck
- POST `/embed/text`: takes text given as parameter `text`, returns embedding of the text
- POST `/embed/image`: takes list of multipart-encoded image files, returns list of `{filename, embedding}`

PoC client provides following features:
- insert images in `images` directory to self-hosted qdrant instance
- search images using natural language query

## Running demo

### Prerequisite

This demo uses
- `uv` for dependency management
- `docker, docker-compose` for running local qdrant instance
Install above tools properly.

Also, this demo targets python >= 3.9.
If necessary, install python with uv.

### Getting started

```bash
# Sync dependencies
uv sync

# Create directories
mkdir images qdrant_storage

# Place your images in images directory

# Run local qdrant instance
docker-compose up -d

# Run local embedding API service (terminal 1)
uv run uvicorn api:app --host 127.0.0.1 --port 8888

# or host embedding API on remote via SSH tunneling
# ssh -L 8888:127.0.0.1:8000 <gpu host>
# clone, sync dependencies
# uv run uvicorn api:app --host 127.0.0.1 --port 8000

# Run PoC client (terminal 2)
uv run poc-client.py

# retrieved images will be placed in output/<query> directory
```
