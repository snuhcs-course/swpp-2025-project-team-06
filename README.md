# Iteration 2 Working Demo

## MomenTag application

MomenTag android application and backend API server are implemented.

### Implemented features

- Authentication
  - Sign up
  - Sign in
  - Sign out
  - Refresh token
- Image upload
  - Upload image after allowing access
- Image search
  - Search using natural language query
  - Natural language search aware of tag names
  - Browse search results
  - Select photos from search results to create tags
- Tag
  - Create tag
  - Display tag albums in a grid on the home screen
  - View photos contained in a tag album
  - Delete tag albums
  - Delete tags from individual photos
- Recommend
  - Photo recommendations based on selected photos when creating a tag
  - Photo recommendations that fit a tag album, within the tag album
  - Recommend tags for individual photos
- Moment (Story of Memory)
  - Scroll down through photos
  - Recommend 3 tags that fit each photo
  - Select recommended tags to add them to the corresponding photo

### Getting started

#### Prerequisite

**The backend server is not deployed on cloud. You have to run local server instance.**

This demo uses

- `uv` for dependency management
- `docker, docker-compose` for running local services
- `MySQL` for database

Install above tools properly.

Also, this demo targets python >= 3.13. If necessary, install appropriate version with uv.

#### MySQL setup

Inside the MySQL prompt, run the following commands. Modify userid and password as you wish.

```SQL
CREATE DATABASE momentag_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'userid'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON momentag_db.* TO 'userid'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

#### Running demo

After tool installation, modify line 3 of `strings.xml` on android code to match your local server IP.

Since this demo version supports only HTTP endpoints, test on local network.

**Avoid exposing the backend server on the internet directly**

Furthermore, if you want to test with more images, feel free to modify line 264 of `LocalRepository.kt` to increase numbers of images uploaded.

```bash
# Following commands are executed in backend dir
cd backend

# Sync dependencies
uv sync

# Set env variables
export SECRET_KEY="$YOUR_SECRET_KEY"
export QDRANT_CLIENT_URL="http://localhost:6333"
export QDRANT_API_KEY="dummy_API_KEY"
export DJANGO_ALLOWED_HOSTS="$YOUR_IP_ADDRESS"

export DB_HOST="127.0.0.1"
export DB_PORT="3306"
export DB_NAME="momentag_db"
export DB_USER="userid"
export DB_PASSWORD="password"

# Run local qdrant, redis container
docker-compose up -d

# Run database migrations
uv run manage.py migrate

# Run local celery workers (terminal 1)
uv run celery -A config worker -l info

# Run local backend server (terminal 2)
uv run manage.py runserver 0.0.0.0:8000
```

Build android application and install it.

Run MomenTag application to test features.

### Demo video
[Video link](https://drive.google.com/file/d/1WJB-m30L-vLv2m7La7_Y_DP7M5aNsjgm/view?usp=sharing)