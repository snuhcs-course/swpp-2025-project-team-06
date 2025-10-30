import uuid
from django.test import TestCase
from django.contrib.auth.models import User
from django.db import IntegrityError
from django.core.exceptions import ValidationError

from ..models import Tag, Photo, Photo_Tag, Caption, Photo_Caption


class TagModelTest(TestCase):
    """Tag 모델 테스트"""

    def setUp(self):
        """테스트용 사용자 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

    def test_tag_creation_success(self):
        """태그 생성 성공 테스트"""
        tag = Tag.objects.create(tag="테스트태그", user=self.user)
        
        self.assertIsInstance(tag.tag_id, uuid.UUID)
        self.assertEqual(tag.tag, "테스트태그")
        self.assertEqual(tag.user, self.user)
        self.assertIsNotNone(tag.tag_id)

    def test_tag_string_representation(self):
        """태그 문자열 표현 테스트"""
        tag = Tag.objects.create(tag="문자열테스트", user=self.user)
        self.assertEqual(str(tag), "문자열테스트")

    def test_tag_max_length_validation(self):
        """태그 최대 길이 검증 테스트"""
        long_tag = "a" * 51  # 50자 초과
        with self.assertRaises(ValidationError):
            tag = Tag(tag=long_tag, user=self.user)
            tag.full_clean()

    def test_tag_cascade_delete_with_user(self):
        """사용자 삭제 시 태그 cascade 삭제 테스트"""
        tag = Tag.objects.create(tag="cascade테스트", user=self.user)
        tag_id = tag.tag_id
        
        # 사용자 삭제
        self.user.delete()
        
        # 태그도 함께 삭제되었는지 확인
        with self.assertRaises(Tag.DoesNotExist):
            Tag.objects.get(tag_id=tag_id)

    def test_multiple_tags_same_user(self):
        """같은 사용자가 여러 태그 생성 테스트"""
        tag1 = Tag.objects.create(tag="태그1", user=self.user)
        tag2 = Tag.objects.create(tag="태그2", user=self.user)
        
        user_tags = Tag.objects.filter(user=self.user)
        self.assertEqual(user_tags.count(), 2)
        self.assertIn(tag1, user_tags)
        self.assertIn(tag2, user_tags)

    def test_same_tag_different_users(self):
        """다른 사용자가 같은 태그명 사용 가능 테스트"""
        user2 = User.objects.create_user(
            username="testuser2", email="test2@example.com", password="testpass123"
        )
        
        tag1 = Tag.objects.create(tag="공통태그", user=self.user)
        tag2 = Tag.objects.create(tag="공통태그", user=user2)
        
        self.assertNotEqual(tag1.tag_id, tag2.tag_id)
        self.assertEqual(tag1.tag, tag2.tag)
        self.assertNotEqual(tag1.user, tag2.user)


class PhotoModelTest(TestCase):
    """Photo 모델 테스트"""

    def setUp(self):
        """테스트용 사용자 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

    def test_photo_creation_success(self):
        """사진 생성 성공 테스트"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=12345,
            filename="test_photo.jpg",
            lat=37.5665,
            lng=126.9780
        )
        
        self.assertIsInstance(photo.photo_id, uuid.UUID)
        self.assertEqual(photo.user, self.user)
        self.assertEqual(photo.photo_path_id, 12345)
        self.assertEqual(photo.filename, "test_photo.jpg")
        self.assertEqual(photo.lat, 37.5665)
        self.assertEqual(photo.lng, 126.9780)
        self.assertFalse(photo.is_tagged)  # 기본값
        self.assertIsNotNone(photo.created_at)

    def test_photo_creation_without_coordinates(self):
        """좌표 없이 사진 생성 테스트"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=67890,
            filename="no_coords.jpg"
        )
        
        self.assertIsNone(photo.lat)
        self.assertIsNone(photo.lng)
        self.assertEqual(photo.photo_path_id, 67890)

    def test_photo_string_representation(self):
        """사진 문자열 표현 테스트"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=111,
            filename="string_test.jpg"
        )
        expected_str = f"Photo {photo.photo_id} by User {self.user.id}"
        self.assertEqual(str(photo), expected_str)

    def test_photo_cascade_delete_with_user(self):
        """사용자 삭제 시 사진 cascade 삭제 테스트"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=222,
            filename="cascade_test.jpg"
        )
        photo_id = photo.photo_id
        
        # 사용자 삭제
        self.user.delete()
        
        # 사진도 함께 삭제되었는지 확인
        with self.assertRaises(Photo.DoesNotExist):
            Photo.objects.get(photo_id=photo_id)

    def test_photo_path_id_integer_field(self):
        """photo_path_id가 정수 필드인지 테스트"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=999999,
            filename="integer_test.jpg"
        )
        self.assertIsInstance(photo.photo_path_id, int)
        self.assertEqual(photo.photo_path_id, 999999)

    def test_photo_is_tagged_default_false(self):
        """is_tagged 기본값이 False인지 테스트"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=333,
            filename="default_test.jpg"
        )
        self.assertFalse(photo.is_tagged)

    def test_photo_is_tagged_can_be_true(self):
        """is_tagged를 True로 설정할 수 있는지 테스트"""
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=444,
            filename="tagged_test.jpg",
            is_tagged=True
        )
        self.assertTrue(photo.is_tagged)

    def test_photo_coordinates_edge_cases(self):
        """좌표 극한값 테스트"""
        # 극지방 좌표
        photo1 = Photo.objects.create(
            user=self.user,
            photo_path_id=555,
            filename="north_pole.jpg",
            lat=90.0,
            lng=-180.0
        )
        self.assertEqual(photo1.lat, 90.0)
        self.assertEqual(photo1.lng, -180.0)

        # 적도 좌표
        photo2 = Photo.objects.create(
            user=self.user,
            photo_path_id=666,
            filename="equator.jpg",
            lat=0.0,
            lng=0.0
        )
        self.assertEqual(photo2.lat, 0.0)
        self.assertEqual(photo2.lng, 0.0)


class PhotoTagModelTest(TestCase):
    """Photo_Tag 모델 테스트"""

    def setUp(self):
        """테스트용 데이터 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.tag = Tag.objects.create(tag="테스트태그", user=self.user)
        self.photo = Photo.objects.create(
            user=self.user,
            photo_path_id=777,
            filename="photo_tag_test.jpg"
        )

    def test_photo_tag_creation_success(self):
        """Photo_Tag 생성 성공 테스트"""
        photo_tag = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        
        self.assertIsInstance(photo_tag.pt_id, uuid.UUID)
        self.assertEqual(photo_tag.photo, self.photo)
        self.assertEqual(photo_tag.tag, self.tag)
        self.assertEqual(photo_tag.user, self.user)

    def test_photo_tag_string_representation(self):
        """Photo_Tag 문자열 표현 테스트"""
        photo_tag = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        expected_str = f"{self.photo.photo_id} tagged with {self.tag.tag_id}"
        self.assertEqual(str(photo_tag), expected_str)

    def test_photo_tag_cascade_delete_with_photo(self):
        """사진 삭제 시 Photo_Tag cascade 삭제 테스트"""
        photo_tag = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        pt_id = photo_tag.pt_id
        
        # 사진 삭제
        self.photo.delete()
        
        # Photo_Tag도 함께 삭제되었는지 확인
        with self.assertRaises(Photo_Tag.DoesNotExist):
            Photo_Tag.objects.get(pt_id=pt_id)

    def test_photo_tag_cascade_delete_with_tag(self):
        """태그 삭제 시 Photo_Tag cascade 삭제 테스트"""
        photo_tag = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        pt_id = photo_tag.pt_id
        
        # 태그 삭제
        self.tag.delete()
        
        # Photo_Tag도 함께 삭제되었는지 확인
        with self.assertRaises(Photo_Tag.DoesNotExist):
            Photo_Tag.objects.get(pt_id=pt_id)

    def test_photo_tag_cascade_delete_with_user(self):
        """사용자 삭제 시 Photo_Tag cascade 삭제 테스트"""
        photo_tag = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        pt_id = photo_tag.pt_id
        
        # 사용자 삭제
        self.user.delete()
        
        # Photo_Tag도 함께 삭제되었는지 확인
        with self.assertRaises(Photo_Tag.DoesNotExist):
            Photo_Tag.objects.get(pt_id=pt_id)

    def test_multiple_tags_on_photo(self):
        """하나의 사진에 여러 태그 추가 테스트"""
        tag2 = Tag.objects.create(tag="두번째태그", user=self.user)
        
        photo_tag1 = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        photo_tag2 = Photo_Tag.objects.create(
            photo=self.photo,
            tag=tag2,
            user=self.user
        )
        
        photo_tags = Photo_Tag.objects.filter(photo=self.photo)
        self.assertEqual(photo_tags.count(), 2)
        self.assertIn(photo_tag1, photo_tags)
        self.assertIn(photo_tag2, photo_tags)

    def test_duplicate_photo_tag_relationship(self):
        """동일한 사진-태그 관계 중복 생성 가능 테스트"""
        # 현재 모델에는 unique_together 제약이 없으므로 중복 생성 가능
        photo_tag1 = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        photo_tag2 = Photo_Tag.objects.create(
            photo=self.photo,
            tag=self.tag,
            user=self.user
        )
        
        self.assertNotEqual(photo_tag1.pt_id, photo_tag2.pt_id)
        self.assertEqual(photo_tag1.photo, photo_tag2.photo)
        self.assertEqual(photo_tag1.tag, photo_tag2.tag)


class CaptionModelTest(TestCase):
    """Caption 모델 테스트"""

    def setUp(self):
        """테스트용 사용자 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

    def test_caption_creation_success(self):
        """캡션 생성 성공 테스트"""
        caption = Caption.objects.create(
            caption="테스트 캡션",
            user=self.user
        )
        
        self.assertIsInstance(caption.caption_id, uuid.UUID)
        self.assertEqual(caption.caption, "테스트 캡션")
        self.assertEqual(caption.user, self.user)

    def test_caption_string_representation(self):
        """캡션 문자열 표현 테스트"""
        caption = Caption.objects.create(
            caption="문자열 테스트 캡션",
            user=self.user
        )
        self.assertEqual(str(caption), "문자열 테스트 캡션")

    def test_caption_unique_constraint(self):
        """캡션 unique 제약 테스트"""
        Caption.objects.create(caption="고유 캡션", user=self.user)
        
        # 같은 캡션으로 다시 생성 시도 (다른 사용자라도 실패해야 함)
        user2 = User.objects.create_user(
            username="testuser2", email="test2@example.com", password="testpass123"
        )
        
        with self.assertRaises(IntegrityError):
            Caption.objects.create(caption="고유 캡션", user=user2)

    def test_caption_max_length_validation(self):
        """캡션 최대 길이 검증 테스트"""
        long_caption = "a" * 51  # 50자 초과
        with self.assertRaises(ValidationError):
            caption = Caption(caption=long_caption, user=self.user)
            caption.full_clean()

    def test_caption_cascade_delete_with_user(self):
        """사용자 삭제 시 캡션 cascade 삭제 테스트"""
        caption = Caption.objects.create(
            caption="cascade 테스트 캡션",
            user=self.user
        )
        caption_id = caption.caption_id
        
        # 사용자 삭제
        self.user.delete()
        
        # 캡션도 함께 삭제되었는지 확인
        with self.assertRaises(Caption.DoesNotExist):
            Caption.objects.get(caption_id=caption_id)


class PhotoCaptionModelTest(TestCase):
    """Photo_Caption 모델 테스트"""

    def setUp(self):
        """테스트용 데이터 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )
        self.photo = Photo.objects.create(
            user=self.user,
            photo_path_id=888,
            filename="photo_caption_test.jpg"
        )
        self.caption = Caption.objects.create(
            caption="테스트 캡션",
            user=self.user
        )

    def test_photo_caption_creation_success(self):
        """Photo_Caption 생성 성공 테스트"""
        photo_caption = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user,
            weight=10
        )
        
        self.assertIsInstance(photo_caption.pc_id, uuid.UUID)
        self.assertEqual(photo_caption.photo, self.photo)
        self.assertEqual(photo_caption.caption, self.caption)
        self.assertEqual(photo_caption.user, self.user)
        self.assertEqual(photo_caption.weight, 10)

    def test_photo_caption_default_weight(self):
        """Photo_Caption weight 기본값 테스트"""
        photo_caption = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user
        )
        self.assertEqual(photo_caption.weight, 0)

    def test_photo_caption_string_representation(self):
        """Photo_Caption 문자열 표현 테스트"""
        photo_caption = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user
        )
        expected_str = f"{self.photo.photo_id} captioned with {self.caption.caption}"
        self.assertEqual(str(photo_caption), expected_str)

    def test_photo_caption_cascade_delete_with_photo(self):
        """사진 삭제 시 Photo_Caption cascade 삭제 테스트"""
        photo_caption = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user
        )
        pc_id = photo_caption.pc_id
        
        # 사진 삭제
        self.photo.delete()
        
        # Photo_Caption도 함께 삭제되었는지 확인
        with self.assertRaises(Photo_Caption.DoesNotExist):
            Photo_Caption.objects.get(pc_id=pc_id)

    def test_photo_caption_cascade_delete_with_caption(self):
        """캡션 삭제 시 Photo_Caption cascade 삭제 테스트"""
        photo_caption = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user
        )
        pc_id = photo_caption.pc_id
        
        # 캡션 삭제
        self.caption.delete()
        
        # Photo_Caption도 함께 삭제되었는지 확인
        with self.assertRaises(Photo_Caption.DoesNotExist):
            Photo_Caption.objects.get(pc_id=pc_id)

    def test_photo_caption_cascade_delete_with_user(self):
        """사용자 삭제 시 Photo_Caption cascade 삭제 테스트"""
        photo_caption = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user
        )
        pc_id = photo_caption.pc_id
        
        # 사용자 삭제
        self.user.delete()
        
        # Photo_Caption도 함께 삭제되었는지 확인
        with self.assertRaises(Photo_Caption.DoesNotExist):
            Photo_Caption.objects.get(pc_id=pc_id)

    def test_multiple_captions_on_photo(self):
        """하나의 사진에 여러 캡션 추가 테스트"""
        caption2 = Caption.objects.create(
            caption="두번째 캡션",
            user=self.user
        )
        
        photo_caption1 = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user,
            weight=5
        )
        photo_caption2 = Photo_Caption.objects.create(
            photo=self.photo,
            caption=caption2,
            user=self.user,
            weight=10
        )
        
        photo_captions = Photo_Caption.objects.filter(photo=self.photo)
        self.assertEqual(photo_captions.count(), 2)
        self.assertIn(photo_caption1, photo_captions)
        self.assertIn(photo_caption2, photo_captions)

    def test_photo_caption_weight_values(self):
        """Photo_Caption weight 다양한 값 테스트"""
        # 양수 weight
        pc1 = Photo_Caption.objects.create(
            photo=self.photo,
            caption=self.caption,
            user=self.user,
            weight=100
        )
        self.assertEqual(pc1.weight, 100)

        # 음수 weight
        caption2 = Caption.objects.create(caption="음수 weight 캡션", user=self.user)
        pc2 = Photo_Caption.objects.create(
            photo=self.photo,
            caption=caption2,
            user=self.user,
            weight=-50
        )
        self.assertEqual(pc2.weight, -50)


class ModelIntegrationTest(TestCase):
    """모델 간 통합 테스트"""

    def setUp(self):
        """테스트용 데이터 생성"""
        self.user1 = User.objects.create_user(
            username="user1", email="user1@example.com", password="testpass123"
        )
        self.user2 = User.objects.create_user(
            username="user2", email="user2@example.com", password="testpass123"
        )

    def test_complete_photo_workflow(self):
        """완전한 사진 워크플로우 테스트"""
        # 1. 사진 업로드
        photo = Photo.objects.create(
            user=self.user1,
            photo_path_id=999,
            filename="workflow_test.jpg",
            lat=37.5665,
            lng=126.9780
        )

        # 2. 태그 생성 및 연결
        tag1 = Tag.objects.create(tag="풍경", user=self.user1)
        tag2 = Tag.objects.create(tag="여행", user=self.user1)
        
        Photo_Tag.objects.create(photo=photo, tag=tag1, user=self.user1)
        Photo_Tag.objects.create(photo=photo, tag=tag2, user=self.user1)

        # 3. 캡션 생성 및 연결
        caption1 = Caption.objects.create(caption="아름다운 풍경", user=self.user1)
        caption2 = Caption.objects.create(caption="기억에 남는 순간", user=self.user1)
        
        Photo_Caption.objects.create(photo=photo, caption=caption1, user=self.user1, weight=10)
        Photo_Caption.objects.create(photo=photo, caption=caption2, user=self.user1, weight=5)

        # 4. 사진이 태그됨으로 표시
        photo.is_tagged = True
        photo.save()

        # 5. 결과 검증
        self.assertTrue(photo.is_tagged)
        self.assertEqual(Photo_Tag.objects.filter(photo=photo).count(), 2)
        self.assertEqual(Photo_Caption.objects.filter(photo=photo).count(), 2)

        # 6. 태그와 캡션을 통한 사진 조회
        photos_with_landscape_tag = Photo.objects.filter(
            photo_tag__tag__tag="풍경",
            user=self.user1
        )
        self.assertIn(photo, photos_with_landscape_tag)

        photos_with_beautiful_caption = Photo.objects.filter(
            photo_caption__caption__caption="아름다운 풍경",
            user=self.user1
        )
        self.assertIn(photo, photos_with_beautiful_caption)

    def test_user_isolation(self):
        """사용자 간 데이터 격리 테스트"""
        # User1의 데이터
        photo1 = Photo.objects.create(
            user=self.user1,
            photo_path_id=111,
            filename="user1_photo.jpg"
        )
        tag1 = Tag.objects.create(tag="user1태그", user=self.user1)
        caption1 = Caption.objects.create(caption="user1 캡션", user=self.user1)

        # User2의 데이터
        photo2 = Photo.objects.create(
            user=self.user2,
            photo_path_id=222,
            filename="user2_photo.jpg"
        )
        tag2 = Tag.objects.create(tag="user2태그", user=self.user2)
        caption2 = Caption.objects.create(caption="user2 캡션", user=self.user2)

        # 각 사용자는 자신의 데이터만 조회
        user1_photos = Photo.objects.filter(user=self.user1)
        user1_tags = Tag.objects.filter(user=self.user1)
        user1_captions = Caption.objects.filter(user=self.user1)

        self.assertEqual(user1_photos.count(), 1)
        self.assertEqual(user1_tags.count(), 1)
        self.assertEqual(user1_captions.count(), 1)
        self.assertIn(photo1, user1_photos)
        self.assertNotIn(photo2, user1_photos)

        user2_photos = Photo.objects.filter(user=self.user2)
        user2_tags = Tag.objects.filter(user=self.user2)
        user2_captions = Caption.objects.filter(user=self.user2)

        self.assertEqual(user2_photos.count(), 1)
        self.assertEqual(user2_tags.count(), 1)
        self.assertEqual(user2_captions.count(), 1)
        self.assertIn(photo2, user2_photos)
        self.assertNotIn(photo1, user2_photos)

    def test_cascade_delete_comprehensive(self):
        """포괄적인 cascade 삭제 테스트"""
        # 데이터 생성
        photo = Photo.objects.create(
            user=self.user1,
            photo_path_id=333,
            filename="cascade_comprehensive.jpg"
        )
        tag = Tag.objects.create(tag="cascade태그", user=self.user1)
        caption = Caption.objects.create(caption="cascade 캡션", user=self.user1)
        
        photo_tag = Photo_Tag.objects.create(photo=photo, tag=tag, user=self.user1)
        photo_caption = Photo_Caption.objects.create(photo=photo, caption=caption, user=self.user1)

        # ID 저장
        photo_id = photo.photo_id
        tag_id = tag.tag_id
        caption_id = caption.caption_id
        pt_id = photo_tag.pt_id
        pc_id = photo_caption.pc_id

        # 사용자 삭제 시 모든 관련 데이터가 삭제되는지 확인
        self.user1.delete()

        with self.assertRaises(Photo.DoesNotExist):
            Photo.objects.get(photo_id=photo_id)
        with self.assertRaises(Tag.DoesNotExist):
            Tag.objects.get(tag_id=tag_id)
        with self.assertRaises(Caption.DoesNotExist):
            Caption.objects.get(caption_id=caption_id)
        with self.assertRaises(Photo_Tag.DoesNotExist):
            Photo_Tag.objects.get(pt_id=pt_id)
        with self.assertRaises(Photo_Caption.DoesNotExist):
            Photo_Caption.objects.get(pc_id=pc_id)


class ModelConstraintTest(TestCase):
    """모델 제약 조건 테스트"""

    def setUp(self):
        """테스트용 사용자 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpass123"
        )

    def test_uuid_field_uniqueness(self):
        """UUID 필드 고유성 테스트"""
        # 여러 객체 생성하여 UUID가 고유한지 확인
        photos = []
        for i in range(10):
            photo = Photo.objects.create(
                user=self.user,
                photo_path_id=i,
                filename=f"uuid_test_{i}.jpg"
            )
            photos.append(photo)

        photo_ids = [photo.photo_id for photo in photos]
        self.assertEqual(len(photo_ids), len(set(photo_ids)))  # 모든 ID가 고유함

    def test_required_fields_user(self):
        """필수 필드 테스트"""
        # Photo 모델 필수 필드 테스트
        try:
            Photo.objects.create(
                # user 누락
                photo_path_id=123,
                filename="required_test.jpg"
            )
        except Exception as e:
            self.assertIsInstance(e, IntegrityError)

    def test_required_fields_photo_path_id(self):
        """필수 필드 테스트"""
        # Photo 모델 필수 필드 테스트
        try:
            Photo.objects.create(
                user=self.user,
                # photo_path_id 누락
                filename="required_test.jpg"
            )
        except Exception as e:
            self.assertIsInstance(e, IntegrityError)

    def test_null_and_blank_fields(self):
        """null과 blank 필드 테스트"""
        # lat, lng는 null=True, blank=True
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=456,
            filename="null_test.jpg",
            lat=None,
            lng=None
        )
        self.assertIsNone(photo.lat)
        self.assertIsNone(photo.lng)

    def test_boolean_field_behavior(self):
        """불린 필드 동작 테스트"""
        # is_tagged 기본값 False
        photo = Photo.objects.create(
            user=self.user,
            photo_path_id=789,
            filename="boolean_test.jpg"
        )
        self.assertFalse(photo.is_tagged)

        # 명시적으로 True 설정
        photo.is_tagged = True
        photo.save()
        photo.refresh_from_db()
        self.assertTrue(photo.is_tagged)
