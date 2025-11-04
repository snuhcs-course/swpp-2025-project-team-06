"""
Celery configuration for distributed task processing.

Most tasks (image processing, embeddings) require GPU workers.
CPU-only tasks (compute_and_store_rep_vectors) also run on the same workers.

Running Workers:
    celery -A config worker --loglevel=info
"""

import os
from celery import Celery

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings')
app = Celery('config')
app.config_from_object('django.conf:settings', namespace='CELERY')
app.autodiscover_tasks()