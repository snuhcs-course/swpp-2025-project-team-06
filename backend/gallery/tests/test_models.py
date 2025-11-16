from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
from django.db import IntegrityError
import uuid

from gallery.models import Tag, Photo, Photo_Tag, Caption, Photo_Caption


class TagModelTest(TestCase):
    """Tests for Tag model"""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')

    def test_tag_creation(self):
        """Test creating a tag"""
        tag = Tag.objects.create(
            tag='sunset',
            user=self.user
        )
        self.assertIsNotNone(tag.tag_id)
        self.assertIsInstance(tag.tag_id, uuid.UUID)
        self.assertEqual(tag.tag, 'sunset')
        self.assertEqual(tag.user, self.user)

    def test_tag_str_method(self):
        """Test Tag __str__ method returns tag name"""
        tag = Tag.objects.create(tag='nature', user=self.user)
        self.assertEqual(str(tag), 'nature')

    def test_tag_timestamps(self):
        """Test Tag created_at and updated_at are set"""
        tag = Tag.objects.create(tag='test', user=self.user)
        self.assertIsNotNone(tag.created_at)
        self.assertIsNotNone(tag.updated_at)

    def test_tag_cascade_delete(self):
        """Test that deleting user deletes associated tags"""
        tag = Tag.objects.create(tag='test', user=self.user)
        tag_id = tag.tag_id
        self.user.delete()
        self.assertFalse(Tag.objects.filter(tag_id=tag_id).exists())


class PhotoModelTest(TestCase):
    """Tests for Photo model"""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')

    def test_photo_creation(self):
        """Test creating a photo"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=1,
            filename='test.jpg',
            created_at=timezone.now()
        )
        self.assertIsNotNone(photo.photo_id)
        self.assertIsInstance(photo.photo_id, uuid.UUID)
        self.assertEqual(photo.user, self.user)
        self.assertEqual(photo.photo_path_id, 1)
        self.assertEqual(photo.filename, 'test.jpg')
        self.assertFalse(photo.is_tagged)

    def test_photo_str_method(self):
        """Test Photo __str__ method returns formatted string"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=1,
            filename='test.jpg',
            created_at=timezone.now()
        )
        expected = f"Photo {photo.photo_id} by User {self.user.id}"
        self.assertEqual(str(photo), expected)

    def test_photo_with_coordinates(self):
        """Test creating a photo with GPS coordinates"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=2,
            filename='geo.jpg',
            created_at=timezone.now(),
            lat=37.5665,
            lng=126.9780
        )
        self.assertEqual(photo.lat, 37.5665)
        self.assertEqual(photo.lng, 126.9780)

    def test_photo_unique_constraint(self):
        """Test unique constraint on user and photo_path_id"""
        Photo.objects.create(
            user=self.user,
            photo_path_id=100,
            filename='photo1.jpg',
            created_at=timezone.now()
        )
        
        with self.assertRaises(IntegrityError):
            Photo.objects.create(
                user=self.user,
                photo_path_id=100,
                filename='photo2.jpg',
                created_at=timezone.now()
            )

    def test_photo_different_users_same_path_id(self):
        """Test that different users can have same photo_path_id"""
        user2 = User.objects.create_user(username='testuser2', password='testpass123')
        
        photo1 = Photo.objects.create(
            user=self.user,
            photo_path_id=100,
            filename='photo1.jpg',
            created_at=timezone.now()
        )
        
        photo2 = Photo.objects.create(
            user=user2,
            photo_path_id=100,
            filename='photo2.jpg',
            created_at=timezone.now()
        )
        
        self.assertNotEqual(photo1.photo_id, photo2.photo_id)
        self.assertEqual(photo1.photo_path_id, photo2.photo_path_id)

    def test_photo_is_tagged_default(self):
        """Test that is_tagged defaults to False"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=1,
            filename='test.jpg',
            created_at=timezone.now()
        )
        self.assertFalse(photo.is_tagged)

    def test_photo_cascade_delete(self):
        """Test that deleting user deletes associated photos"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=1,
            filename='test.jpg',
            created_at=timezone.now()
        )
        photo_id = photo.photo_id
        self.user.delete()
        self.assertFalse(Photo.objects.filter(photo_id=photo_id).exists())


class PhotoTagModelTest(TestCase):
    """Tests for Photo_Tag model"""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')
        self.tag = Tag.objects.create(tag='sunset', user=self.user)
        self.photo = Photo.objects.create(
            user=self.user,
            photo_path_id=1,
            filename='test.jpg',
            created_at=timezone.now()
        )

    def test_photo_tag_creation(self):
        """Test creating a photo-tag relationship"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag,
            user=self.user,
            photo=self.photo
        )
        self.assertIsNotNone(photo_tag.pt_id)
        self.assertIsInstance(photo_tag.pt_id, uuid.UUID)
        self.assertEqual(photo_tag.tag, self.tag)
        self.assertEqual(photo_tag.user, self.user)
        self.assertEqual(photo_tag.photo, self.photo)

    def test_photo_tag_str_method(self):
        """Test Photo_Tag __str__ method returns formatted string"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag,
            user=self.user,
            photo=self.photo
        )
        expected = f"{self.photo.photo_id} tagged with {self.tag.tag_id}"
        self.assertEqual(str(photo_tag), expected)

    def test_photo_tag_cascade_delete_tag(self):
        """Test that deleting tag deletes photo_tag"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag,
            user=self.user,
            photo=self.photo
        )
        pt_id = photo_tag.pt_id
        self.tag.delete()
        self.assertFalse(Photo_Tag.objects.filter(pt_id=pt_id).exists())

    def test_photo_tag_cascade_delete_photo(self):
        """Test that deleting photo deletes photo_tag"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag,
            user=self.user,
            photo=self.photo
        )
        pt_id = photo_tag.pt_id
        self.photo.delete()
        self.assertFalse(Photo_Tag.objects.filter(pt_id=pt_id).exists())

    def test_photo_tag_cascade_delete_user(self):
        """Test that deleting user deletes photo_tag"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag,
            user=self.user,
            photo=self.photo
        )
        pt_id = photo_tag.pt_id
        self.user.delete()
        self.assertFalse(Photo_Tag.objects.filter(pt_id=pt_id).exists())


class CaptionModelTest(TestCase):
    """Tests for Caption model"""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')

    def test_caption_creation(self):
        """Test creating a caption"""
        caption = Caption.objects.create(
            user=self.user,
            caption='Beautiful sunset'
        )
        self.assertIsNotNone(caption.caption_id)
        self.assertIsInstance(caption.caption_id, uuid.UUID)
        self.assertEqual(caption.caption, 'Beautiful sunset')
        self.assertEqual(caption.user, self.user)

    def test_caption_str_method(self):
        """Test Caption __str__ method returns caption text"""
        caption = Caption.objects.create(
            user=self.user,
            caption='Amazing view'
        )
        self.assertEqual(str(caption), 'Amazing view')

    def test_caption_unique_together(self):
        """Test unique_together constraint on user and caption"""
        Caption.objects.create(
            user=self.user,
            caption='Test caption'
        )
        
        with self.assertRaises(IntegrityError):
            Caption.objects.create(
                user=self.user,
                caption='Test caption'
            )

    def test_caption_different_users_same_text(self):
        """Test that different users can have same caption text"""
        user2 = User.objects.create_user(username='testuser2', password='testpass123')
        
        caption1 = Caption.objects.create(
            user=self.user,
            caption='Great photo'
        )
        
        caption2 = Caption.objects.create(
            user=user2,
            caption='Great photo'
        )
        
        self.assertNotEqual(caption1.caption_id, caption2.caption_id)
        self.assertEqual(caption1.caption, caption2.caption)

    def test_caption_cascade_delete(self):
        """Test that deleting user deletes associated captions"""
        caption = Caption.objects.create(
            user=self.user,
            caption='Test'
        )
        caption_id = caption.caption_id
        self.user.delete()
        self.assertFalse(Caption.objects.filter(caption_id=caption_id).exists())


class PhotoCaptionModelTest(TestCase):
    """Tests for Photo_Caption model"""

    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpass123')
        self.caption = Caption.objects.create(
            user=self.user,
            caption='Beautiful sunset'
        )
        self.photo = Photo.objects.create(
            user=self.user,
            photo_path_id=1,
            filename='test.jpg',
            created_at=timezone.now()
        )

    def test_photo_caption_creation(self):
        """Test creating a photo-caption relationship"""
        photo_caption = Photo_Caption.objects.create(
            user=self.user,
            photo=self.photo,
            caption=self.caption,
            weight=5
        )
        self.assertIsNotNone(photo_caption.pc_id)
        self.assertIsInstance(photo_caption.pc_id, uuid.UUID)
        self.assertEqual(photo_caption.user, self.user)
        self.assertEqual(photo_caption.photo, self.photo)
        self.assertEqual(photo_caption.caption, self.caption)
        self.assertEqual(photo_caption.weight, 5)

    def test_photo_caption_str_method(self):
        """Test Photo_Caption __str__ method returns formatted string"""
        photo_caption = Photo_Caption.objects.create(
            user=self.user,
            photo=self.photo,
            caption=self.caption
        )
        expected = f"{self.photo.photo_id} captioned with {self.caption.caption}"
        self.assertEqual(str(photo_caption), expected)

    def test_photo_caption_default_weight(self):
        """Test that weight defaults to 0"""
        photo_caption = Photo_Caption.objects.create(
            user=self.user,
            photo=self.photo,
            caption=self.caption
        )
        self.assertEqual(photo_caption.weight, 0)

    def test_photo_caption_cascade_delete_photo(self):
        """Test that deleting photo deletes photo_caption"""
        photo_caption = Photo_Caption.objects.create(
            user=self.user,
            photo=self.photo,
            caption=self.caption
        )
        pc_id = photo_caption.pc_id
        self.photo.delete()
        self.assertFalse(Photo_Caption.objects.filter(pc_id=pc_id).exists())

    def test_photo_caption_cascade_delete_caption(self):
        """Test that deleting caption deletes photo_caption"""
        photo_caption = Photo_Caption.objects.create(
            user=self.user,
            photo=self.photo,
            caption=self.caption
        )
        pc_id = photo_caption.pc_id
        self.caption.delete()
        self.assertFalse(Photo_Caption.objects.filter(pc_id=pc_id).exists())

    def test_photo_caption_cascade_delete_user(self):
        """Test that deleting user deletes photo_caption"""
        photo_caption = Photo_Caption.objects.create(
            user=self.user,
            photo=self.photo,
            caption=self.caption
        )
        pc_id = photo_caption.pc_id
        self.user.delete()
        self.assertFalse(Photo_Caption.objects.filter(pc_id=pc_id).exists())
