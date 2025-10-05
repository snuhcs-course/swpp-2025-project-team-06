# iter1_demo-frontend

The most basic parts for frontend implementation have been implemented.

The images were accessed from a local gallery and used a dataset sorted chronologically. Once the backend implementation is complete, the photos will be displayed via the URI received through the backend.

For the tags, 13 tags were displayed in order through hard coding. Once the backend implementation is complete, the tags will be displayed via the URI received through the backend.

## Features

1. Tag-based Album View
On the home screen, you can view user-created tags and featured images at a glance. Tap on each tag to collect related photos.

2. Photo Detail View and Tag Management
Selecting a photo allows you to view it full screen. You can view a list of tags associated with the photo, add new tags, or delete existing ones.

3. Local Gallery Integration
MomenTag integrates with existing gallery albums stored on your device. You can easily add and manage tags by importing photos from local albums. You can access it by clicking the momentag screen on the home screen. It will be modified to the same format as the original wire diagram.

4. Tag Search
You can quickly search for relevant photos by tagging them using the search bar on the home screen. Searching will be possible once the search algorithm and backend are implemented.

## Getting Started

You need to install the app on your personal phone through Android Studio and allow it to access the gallery.


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
