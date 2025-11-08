"""
Celery configuration for distributed task processing.

Tasks are routed to different queues based on resource requirements:
- gpu_tasks: GPU-intensive operations (image embeddings, tag generation, rep vectors)
- cpu_tasks: CPU-only I/O operations (story generation)

Running Workers:
    # GPU server (GPU-intensive tasks)
    celery -A config worker -Q gpu_tasks --loglevel=info --concurrency=2
    
    # CPU server (lightweight I/O tasks)
    celery -A config worker -Q cpu_tasks --loglevel=info --concurrency=4
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
    # CPU-only tasks (run on CPU server)
    'gallery.tasks.generate_stories_task': {'queue': 'cpu_tasks'},
    
    # GPU-intensive tasks (run on GPU server)
    'gallery.tasks.compute_and_store_rep_vectors': {'queue': 'gpu_tasks'},
    'gallery.gpu_tasks.*': {'queue': 'gpu_tasks'},
}

# Default queue for unspecified tasks (send to GPU server)
app.conf.task_default_queue = 'gpu_tasks'
app.conf.task_default_exchange = 'tasks'
app.conf.task_default_routing_key = 'task.gpu'