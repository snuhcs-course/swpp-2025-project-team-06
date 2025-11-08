#!/bin/bash
# GPU Worker startup script for GPU-intensive tasks
# This worker handles image embeddings, tag generation, and rep vectors computation

echo "Starting GPU Celery Worker..."
echo "Queue: gpu_tasks"
echo "Concurrency: 2 workers (GPU memory limited)"
echo ""

celery -A config worker \
    -Q gpu_tasks \
    --loglevel=info \
    --concurrency=2 \
    --hostname=gpu_worker@%h
