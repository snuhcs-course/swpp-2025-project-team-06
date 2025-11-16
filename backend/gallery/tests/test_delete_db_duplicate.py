import importlib
import uuid
from io import StringIO
from unittest.mock import patch, MagicMock
from django.test import TestCase



class DeleteDBDuplicateTest(TestCase):
    """
    Tests for delete_db_duplicate.py script
    All DB operations are mocked - no real database access
    """

    def setUp(self):
        """Setup mock data"""
        self.user1_id = 1
        self.user2_id = 2

    def _run_script_with_mocks(self, mock_photo_queryset, mock_client):
        """Helper to run the script with mocked DB and Qdrant client"""
        stdout_capture = StringIO()
        
        with patch('sys.stdout', stdout_capture):
            with patch('sys.exit'):
                with patch('gallery.qdrant_utils.get_qdrant_client', return_value=mock_client):
                    with patch('gallery.models.Photo.objects', mock_photo_queryset):
                        # Reload the module to re-execute the script
                        import gallery.delete_db_duplicate
                        importlib.reload(gallery.delete_db_duplicate)
        
        return stdout_capture.getvalue()

    def test_no_duplicates(self):
        """Test script behavior when there are no duplicates"""
        mock_client = MagicMock()
        mock_client.scroll.return_value = ([], None)

        # Mock Photo queryset - no duplicates
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = []
        mock_queryset.all.return_value.values_list.return_value = [str(uuid.uuid4()), str(uuid.uuid4())]

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        self.assertIn("중복된 (user, photo_path_id) 조합이 없습니다", output)
        self.assertIn("🎉🎉🎉", output)

    def test_duplicates_removal_keeps_latest(self):
        """Test that script keeps the latest photo when removing duplicates"""
        mock_client = MagicMock()
        mock_client.scroll.return_value = ([], None)

        # Mock duplicate detection
        duplicate_group = {'user_id': self.user1_id, 'photo_path_id': 100, 'count': 3}
        
        # Mock Photo objects
        photo_latest = MagicMock()
        photo_latest.photo_id = str(uuid.uuid4())
        
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = [duplicate_group]
        
        # Mock filter for specific duplicates
        mock_duplicate_qs = MagicMock()
        mock_duplicate_qs.order_by.return_value.first.return_value = photo_latest
        mock_duplicate_qs.exclude.return_value.delete.return_value = (2, {})
        mock_queryset.filter.return_value = mock_duplicate_qs
        
        # Mock all() for Qdrant part
        mock_queryset.all.return_value.values_list.return_value = [photo_latest.photo_id]

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        self.assertIn("총 1개의 중복 그룹을 찾았습니다", output)
        self.assertIn("2개 삭제됨", output)

    def test_multiple_duplicate_groups(self):
        """Test script with multiple duplicate groups"""
        mock_client = MagicMock()
        mock_client.scroll.return_value = ([], None)

        # Mock two duplicate groups
        duplicate_groups = [
            {'user_id': self.user1_id, 'photo_path_id': 200, 'count': 2},
            {'user_id': self.user1_id, 'photo_path_id': 201, 'count': 3}
        ]
        
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = duplicate_groups
        
        # Mock filter returns for each group
        def filter_side_effect(*args, **kwargs):
            mock_qs = MagicMock()
            photo_to_keep = MagicMock()
            photo_to_keep.photo_id = str(uuid.uuid4())
            mock_qs.order_by.return_value.first.return_value = photo_to_keep
            
            # Different delete counts for each group
            if kwargs.get('photo_path_id') == 200:
                mock_qs.exclude.return_value.delete.return_value = (1, {})
            else:
                mock_qs.exclude.return_value.delete.return_value = (2, {})
            return mock_qs
        
        mock_queryset.filter.side_effect = filter_side_effect
        mock_queryset.all.return_value.values_list.return_value = [str(uuid.uuid4())]

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        self.assertIn("총 2개의 중복 그룹을 찾았습니다", output)
        self.assertIn("총 3개의 중복 Photo 행을 삭제했습니다", output)

    def test_qdrant_orphan_vector_removal(self):
        """Test that orphaned Qdrant vectors are removed"""
        mock_client = MagicMock()

        # Mock photo IDs in DB
        photo_id_1 = str(uuid.uuid4())
        photo_id_2 = str(uuid.uuid4())
        
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = []
        mock_queryset.all.return_value.values_list.return_value = [photo_id_1, photo_id_2]

        # Mock Qdrant points: 2 valid + 1 orphan
        fake_point1 = MagicMock()
        fake_point1.id = photo_id_1
        fake_point2 = MagicMock()
        fake_point2.id = photo_id_2
        fake_orphan = MagicMock()
        fake_orphan.id = "orphan-uuid-9999"

        mock_client.scroll.return_value = (
            [fake_point1, fake_point2, fake_orphan], 
            None
        )

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        # Verify delete was called with orphan ID
        mock_client.delete.assert_called_once()
        call_kwargs = mock_client.delete.call_args[1]
        self.assertIn("orphan-uuid-9999", call_kwargs["points_selector"])
        self.assertIn("1개의 고아 벡터를 찾아 삭제합니다", output)

    def test_no_orphan_vectors(self):
        """Test when all Qdrant vectors are valid"""
        mock_client = MagicMock()

        photo_id = str(uuid.uuid4())
        
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = []
        mock_queryset.all.return_value.values_list.return_value = [photo_id]

        # Mock Qdrant with only valid IDs
        fake_point = MagicMock()
        fake_point.id = photo_id
        mock_client.scroll.return_value = ([fake_point], None)

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        # Verify delete was NOT called
        mock_client.delete.assert_not_called()
        self.assertIn("고아 벡터가 없습니다", output)

    def test_transaction_rollback_on_sql_error(self):
        """Test that SQL transaction rolls back on error"""
        mock_client = MagicMock()

        # Mock Photo queryset to raise error during duplicate check
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.side_effect = Exception("SQL Error")

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        # Verify error handling
        self.assertIn("🚨🚨🚨 [1부 실패!]", output)
        self.assertIn("SQL 트랜잭션이 롤백되었습니다", output)

    def test_qdrant_pagination(self):
        """Test that script handles Qdrant pagination correctly"""
        mock_client = MagicMock()

        photo_id = str(uuid.uuid4())
        valid_id_2 = str(uuid.uuid4())
        
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = []
        mock_queryset.all.return_value.values_list.return_value = [photo_id, valid_id_2]

        # Mock pagination: first call returns 2 points + offset, second call returns 1 point + None
        fake_point1 = MagicMock()
        fake_point1.id = photo_id
        fake_point2 = MagicMock()
        fake_point2.id = valid_id_2
        fake_orphan = MagicMock()
        fake_orphan.id = "orphan-id"

        mock_client.scroll.side_effect = [
            ([fake_point1, fake_point2], 1000),  # First page
            ([fake_orphan], None)  # Second page (last)
        ]

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        # Verify scroll was called twice
        self.assertEqual(mock_client.scroll.call_count, 2)
        # Verify orphans were collected across pages
        self.assertIn("1개의 고아 벡터를 찾아 삭제합니다", output)

    def test_different_users_same_path_id(self):
        """Test that duplicates are handled per user"""
        mock_client = MagicMock()
        mock_client.scroll.return_value = ([], None)

        # Mock two duplicate groups (different users, same path_id)
        duplicate_groups = [
            {'user_id': self.user1_id, 'photo_path_id': 700, 'count': 2},
            {'user_id': self.user2_id, 'photo_path_id': 700, 'count': 2}
        ]
        
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = duplicate_groups
        
        def filter_side_effect(*args, **kwargs):
            mock_qs = MagicMock()
            photo_to_keep = MagicMock()
            photo_to_keep.photo_id = str(uuid.uuid4())
            mock_qs.order_by.return_value.first.return_value = photo_to_keep
            mock_qs.exclude.return_value.delete.return_value = (1, {})
            return mock_qs
        
        mock_queryset.filter.side_effect = filter_side_effect
        mock_queryset.all.return_value.values_list.return_value = [str(uuid.uuid4())]

        output = self._run_script_with_mocks(mock_queryset, mock_client)
        
        self.assertIn("총 2개의 중복 그룹을 찾았습니다", output)

    def test_qdrant_error_after_sql_success(self):
        """Test that Qdrant errors don't rollback SQL changes"""
        mock_client = MagicMock()

        # Mock duplicate group
        duplicate_group = {'user_id': self.user1_id, 'photo_path_id': 800, 'count': 2}
        
        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = [duplicate_group]
        
        mock_qs = MagicMock()
        photo_to_keep = MagicMock()
        photo_to_keep.photo_id = str(uuid.uuid4())
        mock_qs.order_by.return_value.first.return_value = photo_to_keep
        mock_qs.exclude.return_value.delete.return_value = (1, {})
        mock_queryset.filter.return_value = mock_qs
        
        # Mock Qdrant to raise error
        mock_client.scroll.side_effect = Exception("Qdrant connection error")

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        # SQL cleanup should still have succeeded
        self.assertIn("[1부] SQL DB 작업 완료", output)
        self.assertIn("🚨🚨🚨 [2부 실패!]", output)

    def test_success_completion_message(self):
        """Test that success message appears on completion"""
        mock_client = MagicMock()
        mock_client.scroll.return_value = ([], None)

        mock_queryset = MagicMock()
        mock_queryset.values.return_value.annotate.return_value.filter.return_value = []
        mock_queryset.all.return_value.values_list.return_value = [str(uuid.uuid4())]

        output = self._run_script_with_mocks(mock_queryset, mock_client)

        self.assertIn("🎉🎉🎉 SQL DB 중복 제거 및 이미지 벡터 정리 작업이 완료되었습니다", output)
