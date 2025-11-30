# MomenTag

<img width="912" height="220" alt="image" src="https://github.com/user-attachments/assets/424f0b7b-0831-4783-9191-c66821b29984" />


## About the App

MomenTag is a photo search and management application combining AI-powered semantic search with user tagging features. It helps users organize, search, and discover meaningful moments from their photo collections through intelligent recommendations and natural language processing.

## Key Features

### Main Features

1. Semantic image search based on user-generated tags

2. Tag recommendations for images

3. Image recommendations for image sets grouped by tags

4. Moment: Tag recommendations for old photos

### Detailed Features

| Feature | Description |
|---------|-------------|
| Authentication | JWT-based Sign Up/Log In/Log Out, Token refresh |
| Photo Upload | Batch processing (8 photos per batch), GPS metadata extraction |
| Tag Management | Create/Delete user tag albums, Link photos to tags |
| Semantic Search | Natural language photo search (powered by Qdrant Vector DB) |
| Hybrid Search | Combined Tag + Semantic search using `{TagName}` syntax |
| AI Recommendation | Recommend photos for tags, Recommend tags for photos |
| Moments | Scrollable photo feed + Dynamic tag recommendations |
| Auto Captioning | Automatic photo description generation using Vision-Language models |

## Core Workflow

1. **Photo Upload** → GPU worker generates image embeddings → Stored in Qdrant
2. **Search** → Query embedding generated → Vector similarity search → Results returned
3. **Recommendation** → K-means clustering + Graph analysis → Related photos/tags suggested

## Getting Started

### Prerequisite

This program uses

- `uv` for dependency management
- `MySQL` for database
- `Redis` for Celery broker
- `Qdrant Cloud` for vector database

Install above tools properly.

Also, this program targets python >= 3.13. If necessary, install appropriate version with uv.

### MySQL Setup

Inside the MySQL prompt on the CPU server, run the following commands. Modify userid and password as you wish.

```SQL
CREATE DATABASE momentag_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'userid'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON momentag_db.* TO 'userid'@'%';
FLUSH PRIVILEGES;
EXIT;
```

### Running

#### Environment Variables

Set the following environment variables on both CPU and GPU servers:

```bash
export SECRET_KEY="$YOUR_SECRET_KEY"
export QDRANT_CLIENT_URL="$YOUR_QDRANT_CLOUD_URL"
export QDRANT_API_KEY="$YOUR_QDRANT_API_KEY"
export DJANGO_ALLOWED_HOSTS="$YOUR_IP_ADDRESS"

export DB_HOST="$CPU_SERVER_IP"
export DB_PORT="3306"
export DB_NAME="momentag_db"
export DB_USER="userid"
export DB_PASSWORD="password"

export REDIS_URL="redis://$CPU_SERVER_IP:6379"
```

#### CPU Server

```bash
cd backend

# Sync dependencies
uv sync

# Run database migrations
uv run manage.py migrate

# Run Django server
uv run manage.py runserver 0.0.0.0:8080
```

#### GPU Server

Run the following commands in separate terminals:

```bash
cd backend

# Sync dependencies
uv sync

# Run Celery worker for GPU queue (terminal 1)
uv run celery -A config worker -Q gpu -l info --pool=threads -c4

# Run Celery worker for interactive queue (terminal 2)
uv run celery -A config worker -Q interactive -l info --pool=threads -c4
```

#### Android

Build and install the android application, then run MomenTag to test features.

## Demo Video

https://drive.google.com/file/d/1iEFSSpjw1jEGeSKzshcRyRcf0nCqDglV/view?usp=sharing 
