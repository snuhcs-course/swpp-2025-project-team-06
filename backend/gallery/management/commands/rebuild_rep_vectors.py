"""
Django management command to rebuild all representative vectors using HDBSCAN.

Usage:
    python manage.py rebuild_rep_vectors [--user-id USER_ID] [--flush-only]

Options:
    --user-id USER_ID    Only rebuild rep vectors for a specific user
    --flush-only         Only flush existing rep vectors without rebuilding
"""

from django.core.management.base import BaseCommand
from django.contrib.auth import get_user_model
from gallery.models import Tag
from gallery.tasks import compute_and_store_rep_vectors
from gallery.qdrant_utils import get_qdrant_client, REPVEC_COLLECTION_NAME
from qdrant_client.http import models

User = get_user_model()


class Command(BaseCommand):
    help = 'Rebuild all representative vectors using HDBSCAN'

    def add_arguments(self, parser):
        parser.add_argument(
            '--user-id',
            type=int,
            help='Only rebuild rep vectors for a specific user ID',
        )
        parser.add_argument(
            '--flush-only',
            action='store_true',
            help='Only flush existing rep vectors without rebuilding',
        )

    def handle(self, *args, **options):
        user_id = options.get('user_id')
        flush_only = options.get('flush_only')

        client = get_qdrant_client()

        # Step 1: Flush existing rep vectors
        self.stdout.write(self.style.WARNING('Step 1: Flushing all existing representative vectors...'))

        try:
            if user_id:
                # Flush only for specific user
                delete_filter = models.Filter(
                    must=[
                        models.FieldCondition(
                            key="user_id",
                            match=models.MatchValue(value=user_id)
                        )
                    ]
                )
                client.delete(
                    collection_name=REPVEC_COLLECTION_NAME,
                    points_selector=models.FilterSelector(filter=delete_filter),
                    wait=True,
                )
                self.stdout.write(self.style.SUCCESS(f'✓ Flushed rep vectors for user {user_id}'))
            else:
                # Flush all rep vectors
                # Get all points and delete them
                scroll_result = client.scroll(
                    collection_name=REPVEC_COLLECTION_NAME,
                    limit=10000,
                    with_payload=False,
                    with_vectors=False,
                )
                point_ids = [point.id for point in scroll_result[0]]

                if point_ids:
                    client.delete(
                        collection_name=REPVEC_COLLECTION_NAME,
                        points_selector=point_ids,
                        wait=True,
                    )
                    self.stdout.write(self.style.SUCCESS(f'✓ Flushed {len(point_ids)} rep vectors'))
                else:
                    self.stdout.write(self.style.SUCCESS('✓ No rep vectors to flush'))
        except Exception as e:
            self.stdout.write(self.style.ERROR(f'✗ Error flushing rep vectors: {e}'))
            return

        if flush_only:
            self.stdout.write(self.style.SUCCESS('\n=== Flush completed ==='))
            return

        # Step 2: Rebuild rep vectors for all tags
        self.stdout.write(self.style.WARNING('\nStep 2: Rebuilding representative vectors using HDBSCAN...'))

        if user_id:
            try:
                user = User.objects.get(id=user_id)
                tags = Tag.objects.filter(user=user)
            except User.DoesNotExist:
                self.stdout.write(self.style.ERROR(f'✗ User with ID {user_id} does not exist'))
                return
        else:
            tags = Tag.objects.all()
            users = User.objects.all()
            self.stdout.write(f'Found {users.count()} users')

        total_tags = tags.count()
        self.stdout.write(f'Found {total_tags} tags to rebuild')

        if total_tags == 0:
            self.stdout.write(self.style.WARNING('No tags found. Nothing to rebuild.'))
            return

        # Trigger Celery tasks for each tag
        processed = 0
        for tag in tags:
            try:
                compute_and_store_rep_vectors.delay(tag.user.id, tag.tag_id)
                processed += 1

                if processed % 10 == 0:
                    self.stdout.write(f'  Queued {processed}/{total_tags} tags...')
            except Exception as e:
                self.stdout.write(self.style.ERROR(f'✗ Error queuing tag {tag.tag_id}: {e}'))

        self.stdout.write(self.style.SUCCESS(f'\n✓ Queued {processed}/{total_tags} tags for rep vector computation'))
        self.stdout.write(self.style.SUCCESS('✓ Celery workers will process these tasks asynchronously'))
        self.stdout.write(self.style.WARNING('\nNote: Check Celery worker logs to monitor progress'))
        self.stdout.write(self.style.SUCCESS('\n=== Rebuild initiated successfully ==='))
