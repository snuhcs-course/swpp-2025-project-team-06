#!/bin/bash
# CPU Worker startup script for lightweight I/O tasks
# This worker handles story generation and other CPU-bound operations

echo "Starting CPU Celery Worker..."
echo "Queue: cpu_tasks"
echo "Concurrency: 4 workers"
echo ""

celery -A config worker \
    -Q cpu_tasks \
    --loglevel=info \
    --concurrency=4 \
    --hostname=cpu_worker@%h
