"""
Celery configuration for distributed task processing.

Most tasks (image processing, embeddings) require GPU workers.
CPU-only tasks (compute_and_store_rep_vectors) also run on the same workers.

Running Workers:
    celery -A config worker --loglevel=info
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

# Import to register worker_process_init signal handler
from gallery.gpu_tasks import initialize_models  # noqa: F401, E402
