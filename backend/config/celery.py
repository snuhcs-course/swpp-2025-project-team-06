"""
Celery configuration for distributed task processing with separate queues.

Queues:
- gpu: Long-running GPU tasks (image embedding, captioning)
- interactive: Fast response tasks (story generation, rep vector computation)

Running Workers:
    # GPU worker (requires GPU access)
    celery -A config worker -Q gpu --loglevel=info --hostname=gpu@%h

    # Interactive worker (CPU only, fast response)
    celery -A config worker -Q interactive --loglevel=info --hostname=interactive@%h

    # Combined worker (all queues, for development)
    celery -A config worker -Q gpu,interactive --loglevel=info
"""

import os
from celery import Celery

# Apply socket patching for Tailscale SOCKS5 proxy routing
# This must be done BEFORE any network connections are made
from config.socket_patcher import patch_socket
patch_socket()

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings')
app = Celery('config')
app.config_from_object('django.conf:settings', namespace='CELERY')
app.autodiscover_tasks()

# Task routing configuration
app.conf.task_routes = {
    # GPU-intensive tasks -> gpu queue
    'gallery.gpu_tasks.process_and_embed_photo': {'queue': 'gpu'},
    'gallery.gpu_tasks.process_and_embed_photos_batch': {'queue': 'gpu'},

    # Interactive tasks -> interactive queue
    'gallery.tasks.generate_stories_task': {'queue': 'interactive'},
    'gallery.tasks.compute_and_store_rep_vectors': {'queue': 'interactive'},
}

# Default queue for any tasks not explicitly routed
app.conf.task_default_queue = 'interactive'
app.conf.task_default_exchange = 'tasks'
app.conf.task_default_routing_key = 'task.default'