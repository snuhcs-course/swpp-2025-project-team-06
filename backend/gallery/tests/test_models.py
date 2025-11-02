import uuid
from django.test import TestCase
from django.contrib.auth.models import User
from django.core.exceptions import ValidationError

from ..models import Tag, Photo_Tag, Caption, Photo_Caption
from django.db import models


class TagModelTest(TestCase):
    def setUp(self):
        """테스트용 사용자 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpassword123"
        )

        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpassword123"
        )

    def test_tag_creation_success(self):
        """태그 생성 성공 테스트"""
        tag = Tag.objects.create(tag="여행", user=self.user)

        self.assertEqual(tag.tag, "여행")
        self.assertEqual(tag.user, self.user)
        self.assertIsInstance(tag.tag_id, uuid.UUID)
        self.assertTrue(Tag.objects.filter(tag_id=tag.tag_id).exists())

    def test_tag_auto_uuid_generation(self):
        """태그 ID 자동 생성 테스트"""
        tag1 = Tag.objects.create(tag="태그1", user=self.user)
        tag2 = Tag.objects.create(tag="태그2", user=self.user)

        # UUID가 자동으로 생성되는지 확인
        self.assertIsInstance(tag1.tag_id, uuid.UUID)
        self.assertIsInstance(tag2.tag_id, uuid.UUID)

        # 각 태그의 UUID가 고유한지 확인
        self.assertNotEqual(tag1.tag_id, tag2.tag_id)

    def test_tag_str_method(self):
        """태그 __str__ 메서드 테스트"""
        tag = Tag.objects.create(tag="음식", user=self.user)

        self.assertEqual(str(tag), "음식")

    def test_tag_user_relationship(self):
        """태그-사용자 관계 테스트"""
        tag = Tag.objects.create(tag="사진", user=self.user)

        # 사용자 관계 확인
        self.assertEqual(tag.user, self.user)
        self.assertEqual(tag.user.username, "testuser")

        # 사용자의 태그 역참조 확인
        user_tags = self.user.tag_set.all()
        self.assertIn(tag, user_tags)

    def test_tag_max_length_validation(self):
        """태그 최대 길이 검증 테스트"""
        # 50자 정확히 (경계값)
        long_tag = "a" * 50
        tag = Tag.objects.create(tag=long_tag, user=self.user)
        self.assertEqual(len(tag.tag), 50)

        # 50자 초과 시 에러 발생하는지 확인
        with self.assertRaises(ValidationError):
            too_long_tag = "a" * 51
            tag = Tag(tag=too_long_tag, user=self.user)
            tag.full_clean()  # 모델 검증 실행

    def test_tag_empty_string(self):
        """빈 태그명 테스트"""
        tag = Tag.objects.create(tag="", user=self.user)

        self.assertEqual(tag.tag, "")
        self.assertEqual(tag.user, self.user)

    def test_tag_special_characters(self):
        """특수문자 포함 태그 테스트"""
        special_tags = [
            "한글태그",
            "English Tag",
            "태그123",
            "태그!@#",
            "タグ",  # 일본어
            "标签",  # 중국어
        ]

        created_tags = []
        for tag_name in special_tags:
            tag = Tag.objects.create(tag=tag_name, user=self.user)
            created_tags.append(tag)
            self.assertEqual(tag.tag, tag_name)

    def test_multiple_users_same_tag_name(self):
        """여러 사용자가 같은 이름의 태그 생성 가능 테스트"""
        tag_name = "동일한태그"

        tag1 = Tag.objects.create(tag=tag_name, user=self.user)

        tag2 = Tag.objects.create(tag=tag_name, user=self.other_user)

        # 같은 이름이지만 다른 사용자의 태그
        self.assertEqual(tag1.tag, tag2.tag)
        self.assertNotEqual(tag1.user, tag2.user)
        self.assertNotEqual(tag1.tag_id, tag2.tag_id)

    def test_user_cascade_delete(self):
        """사용자 삭제 시 태그도 삭제되는지 테스트"""
        tag = Tag.objects.create(tag="삭제될태그", user=self.user)
        tag_id = tag.tag_id

        # 태그가 생성되었는지 확인
        self.assertTrue(Tag.objects.filter(tag_id=tag_id).exists())

        # 사용자 삭제
        self.user.delete()

        # 태그도 함께 삭제되었는지 확인
        self.assertFalse(Tag.objects.filter(tag_id=tag_id).exists())

    def test_tag_queryset_filtering(self):
        """태그 쿼리셋 필터링 테스트"""
        # 여러 태그 생성
        tag1 = Tag.objects.create(tag="태그1", user=self.user)
        tag2 = Tag.objects.create(tag="태그2", user=self.user)
        tag3 = Tag.objects.create(tag="태그3", user=self.other_user)

        # 사용자별 필터링
        user_tags = Tag.objects.filter(user=self.user)
        self.assertEqual(user_tags.count(), 2)
        self.assertIn(tag1, user_tags)
        self.assertIn(tag2, user_tags)
        self.assertNotIn(tag3, user_tags)

        # 태그명으로 필터링
        specific_tag = Tag.objects.filter(tag="태그1")
        self.assertEqual(specific_tag.count(), 1)
        self.assertEqual(specific_tag.first(), tag1)

    def test_tag_ordering(self):
        """태그 정렬 테스트"""
        # 여러 태그 생성 (순서대로)
        Tag.objects.create(tag="C태그", user=self.user)
        Tag.objects.create(tag="A태그", user=self.user)
        Tag.objects.create(tag="B태그", user=self.user)

        # 태그명으로 정렬
        ordered_tags = Tag.objects.filter(user=self.user).order_by("tag")
        tag_names = [tag.tag for tag in ordered_tags]

        self.assertEqual(tag_names, ["A태그", "B태그", "C태그"])

    def test_tag_update(self):
        """태그 업데이트 테스트"""
        tag = Tag.objects.create(tag="원본태그", user=self.user)
        original_id = tag.tag_id

        # 태그명 업데이트
        tag.tag = "수정된태그"
        tag.save()

        # 데이터베이스에서 다시 조회
        updated_tag = Tag.objects.get(tag_id=original_id)
        self.assertEqual(updated_tag.tag, "수정된태그")
        self.assertEqual(updated_tag.tag_id, original_id)  # ID는 변경되지 않음

    def test_tag_bulk_operations(self):
        """태그 대량 작업 테스트"""
        # 대량 생성
        tags_data = [Tag(tag=f"태그{i}", user=self.user) for i in range(10)]
        Tag.objects.bulk_create(tags_data)

        # 생성된 태그 수 확인
        user_tags_count = Tag.objects.filter(user=self.user).count()
        self.assertEqual(user_tags_count, 10)

        # 대량 삭제
        Tag.objects.filter(user=self.user, tag__startswith="태그").delete()
        remaining_count = Tag.objects.filter(user=self.user).count()
        self.assertEqual(remaining_count, 0)

    def test_tag_model_fields(self):
        """태그 모델 필드 속성 테스트"""
        tag = Tag.objects.create(tag="필드테스트", user=self.user)

        # 필드 타입 확인
        self.assertIsInstance(tag._meta.get_field("tag_id"), models.UUIDField)
        self.assertIsInstance(tag._meta.get_field("tag"), models.CharField)
        self.assertIsInstance(tag._meta.get_field("user"), models.ForeignKey)

        # 필드 속성 확인
        tag_field = tag._meta.get_field("tag")
        self.assertEqual(tag_field.max_length, 50)

        tag_id_field = tag._meta.get_field("tag_id")
        self.assertTrue(tag_id_field.primary_key)
        self.assertFalse(tag_id_field.editable)


class PhotoTagModelTest(TestCase):
    """Photo_Tag 모델 테스트"""

    def setUp(self):
        """테스트용 데이터 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpassword123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpassword123"
        )
        self.tag = Tag.objects.create(tag="테스트태그", user=self.user)
        self.photo_id = uuid.uuid4()

    def test_photo_tag_creation_success(self):
        """Photo_Tag 생성 성공 테스트"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )

        self.assertEqual(photo_tag.tag, self.tag)
        self.assertEqual(photo_tag.user, self.user)
        self.assertEqual(photo_tag.photo_id, self.photo_id)
        self.assertIsInstance(photo_tag.pt_id, uuid.UUID)
        self.assertTrue(Photo_Tag.objects.filter(pt_id=photo_tag.pt_id).exists())

    def test_photo_tag_auto_uuid_generation(self):
        """Photo_Tag ID 자동 생성 테스트"""
        photo_tag1 = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=uuid.uuid4()
        )
        photo_tag2 = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=uuid.uuid4()
        )

        # UUID가 자동으로 생성되고 고유한지 확인
        self.assertIsInstance(photo_tag1.pt_id, uuid.UUID)
        self.assertIsInstance(photo_tag2.pt_id, uuid.UUID)
        self.assertNotEqual(photo_tag1.pt_id, photo_tag2.pt_id)

    def test_photo_tag_str_method(self):
        """Photo_Tag __str__ 메서드 테스트"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )

        expected_str = f"{self.photo_id} tagged with {self.tag.tag_id}"
        self.assertEqual(str(photo_tag), expected_str)

    def test_photo_tag_foreign_key_relationships(self):
        """Photo_Tag의 외래키 관계 테스트"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )

        # Tag 관계 확인
        self.assertEqual(photo_tag.tag, self.tag)
        self.assertEqual(photo_tag.tag.tag, "테스트태그")

        # User 관계 확인
        self.assertEqual(photo_tag.user, self.user)
        self.assertEqual(photo_tag.user.username, "testuser")

    def test_photo_tag_cascade_delete_tag(self):
        """태그 삭제 시 Photo_Tag도 삭제되는지 테스트"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )
        pt_id = photo_tag.pt_id

        # Photo_Tag가 생성되었는지 확인
        self.assertTrue(Photo_Tag.objects.filter(pt_id=pt_id).exists())

        # 태그 삭제
        self.tag.delete()

        # Photo_Tag도 함께 삭제되었는지 확인
        self.assertFalse(Photo_Tag.objects.filter(pt_id=pt_id).exists())

    def test_photo_tag_cascade_delete_user(self):
        """사용자 삭제 시 Photo_Tag도 삭제되는지 테스트"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )
        pt_id = photo_tag.pt_id

        # Photo_Tag가 생성되었는지 확인
        self.assertTrue(Photo_Tag.objects.filter(pt_id=pt_id).exists())

        # 사용자 삭제 (태그도 함께 삭제됨)
        self.user.delete()

        # Photo_Tag도 함께 삭제되었는지 확인
        self.assertFalse(Photo_Tag.objects.filter(pt_id=pt_id).exists())

    def test_photo_tag_multiple_tags_same_photo(self):
        """같은 사진에 여러 태그 연결 테스트"""
        tag2 = Tag.objects.create(tag="두번째태그", user=self.user)

        photo_tag1 = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )
        photo_tag2 = Photo_Tag.objects.create(
            tag=tag2, user=self.user, photo_id=self.photo_id
        )

        # 같은 사진에 여러 태그가 연결되는지 확인
        photo_tags = Photo_Tag.objects.filter(photo_id=self.photo_id)
        self.assertEqual(photo_tags.count(), 2)
        self.assertIn(photo_tag1, photo_tags)
        self.assertIn(photo_tag2, photo_tags)

    def test_photo_tag_same_tag_multiple_photos(self):
        """같은 태그를 여러 사진에 연결 테스트"""
        photo_id2 = uuid.uuid4()

        photo_tag1 = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )
        photo_tag2 = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=photo_id2
        )

        # 같은 태그가 여러 사진에 연결되는지 확인
        tag_usages = Photo_Tag.objects.filter(tag=self.tag)
        self.assertEqual(tag_usages.count(), 2)
        self.assertIn(photo_tag1, tag_usages)
        self.assertIn(photo_tag2, tag_usages)

    def test_photo_tag_filtering_by_user(self):
        """사용자별 Photo_Tag 필터링 테스트"""
        other_tag = Tag.objects.create(tag="다른태그", user=self.other_user)

        user_photo_tag = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )
        other_user_photo_tag = Photo_Tag.objects.create(
            tag=other_tag, user=self.other_user, photo_id=uuid.uuid4()
        )

        # 사용자별 필터링
        user_photo_tags = Photo_Tag.objects.filter(user=self.user)
        self.assertEqual(user_photo_tags.count(), 1)
        self.assertIn(user_photo_tag, user_photo_tags)
        self.assertNotIn(other_user_photo_tag, user_photo_tags)

    def test_photo_tag_model_fields(self):
        """Photo_Tag 모델 필드 속성 테스트"""
        photo_tag = Photo_Tag.objects.create(
            tag=self.tag, user=self.user, photo_id=self.photo_id
        )

        # 필드 타입 확인
        self.assertIsInstance(photo_tag._meta.get_field("pt_id"), models.UUIDField)
        self.assertIsInstance(photo_tag._meta.get_field("tag"), models.ForeignKey)
        self.assertIsInstance(photo_tag._meta.get_field("user"), models.ForeignKey)
        self.assertIsInstance(photo_tag._meta.get_field("photo_id"), models.UUIDField)

        # 필드 속성 확인
        pt_id_field = photo_tag._meta.get_field("pt_id")
        self.assertTrue(pt_id_field.primary_key)
        self.assertFalse(pt_id_field.editable)


class CaptionModelTest(TestCase):
    """Caption 모델 테스트"""

    def setUp(self):
        """테스트용 사용자 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpassword123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpassword123"
        )

    def test_caption_creation_success(self):
        """Caption 생성 성공 테스트"""
        caption = Caption.objects.create(caption="아름다운 풍경", user=self.user)

        self.assertEqual(caption.caption, "아름다운 풍경")
        self.assertEqual(caption.user, self.user)
        self.assertIsInstance(caption.caption_id, uuid.UUID)
        self.assertTrue(Caption.objects.filter(caption_id=caption.caption_id).exists())

    def test_caption_auto_uuid_generation(self):
        """Caption ID 자동 생성 테스트"""
        caption1 = Caption.objects.create(caption="캡션1", user=self.user)
        caption2 = Caption.objects.create(caption="캡션2", user=self.user)

        # UUID가 자동으로 생성되고 고유한지 확인
        self.assertIsInstance(caption1.caption_id, uuid.UUID)
        self.assertIsInstance(caption2.caption_id, uuid.UUID)
        self.assertNotEqual(caption1.caption_id, caption2.caption_id)

    def test_caption_str_method(self):
        """Caption __str__ 메서드 테스트"""
        caption = Caption.objects.create(caption="테스트 캡션", user=self.user)

        self.assertEqual(str(caption), "테스트 캡션")

    def test_caption_unique_constraint(self):
        """Caption unique 제약 테스트"""
        # 첫 번째 캡션 생성
        Caption.objects.create(caption="고유한캡션", user=self.user)

        # 같은 캡션 텍스트로 다시 생성 시도 (다른 사용자라도 실패해야 함)
        with self.assertRaises(Exception):  # IntegrityError 또는 ValidationError
            Caption.objects.create(caption="고유한캡션", user=self.other_user)

    def test_caption_max_length_validation(self):
        """Caption 최대 길이 검증 테스트"""
        # 50자 정확히 (경계값)
        long_caption = "a" * 50
        caption = Caption.objects.create(caption=long_caption, user=self.user)
        self.assertEqual(len(caption.caption), 50)

        # 50자 초과 시 에러 발생하는지 확인
        with self.assertRaises(ValidationError):
            too_long_caption = "a" * 51
            caption = Caption(caption=too_long_caption, user=self.user)
            caption.full_clean()

    def test_caption_user_relationship(self):
        """Caption-사용자 관계 테스트"""
        caption = Caption.objects.create(caption="사용자 캡션", user=self.user)

        # 사용자 관계 확인
        self.assertEqual(caption.user, self.user)
        self.assertEqual(caption.user.username, "testuser")

        # 사용자의 캡션 역참조 확인
        user_captions = self.user.caption_set.all()
        self.assertIn(caption, user_captions)

    def test_caption_cascade_delete(self):
        """사용자 삭제 시 Caption도 삭제되는지 테스트"""
        caption = Caption.objects.create(caption="삭제될캡션", user=self.user)
        caption_id = caption.caption_id

        # 캡션이 생성되었는지 확인
        self.assertTrue(Caption.objects.filter(caption_id=caption_id).exists())

        # 사용자 삭제
        self.user.delete()

        # 캡션도 함께 삭제되었는지 확인
        self.assertFalse(Caption.objects.filter(caption_id=caption_id).exists())

    def test_caption_special_characters(self):
        """특수문자 포함 캡션 테스트"""
        special_captions = [
            "한글 캡션",
            "English Caption",
            "캡션123",
            "캡션!@#$%",
            "キャプション",  # 일본어
            "字幕",  # 중국어
            "🏖️🎵 이모지 캡션",
        ]

        for i, caption_text in enumerate(special_captions):
            # unique 제약 때문에 각각 다른 텍스트 사용
            unique_caption_text = f"{caption_text}_{i}"
            caption = Caption.objects.create(
                caption=unique_caption_text, user=self.user
            )
            self.assertEqual(caption.caption, unique_caption_text)

    def test_caption_model_fields(self):
        """Caption 모델 필드 속성 테스트"""
        caption = Caption.objects.create(caption="필드테스트", user=self.user)

        # 필드 타입 확인
        self.assertIsInstance(caption._meta.get_field("caption_id"), models.UUIDField)
        self.assertIsInstance(caption._meta.get_field("caption"), models.CharField)
        self.assertIsInstance(caption._meta.get_field("user"), models.ForeignKey)

        # 필드 속성 확인
        caption_field = caption._meta.get_field("caption")
        self.assertEqual(caption_field.max_length, 50)

        caption_id_field = caption._meta.get_field("caption_id")
        self.assertTrue(caption_id_field.primary_key)
        self.assertFalse(caption_id_field.editable)


class PhotoCaptionModelTest(TestCase):
    """Photo_Caption 모델 테스트"""

    def setUp(self):
        """테스트용 데이터 생성"""
        self.user = User.objects.create_user(
            username="testuser", email="test@example.com", password="testpassword123"
        )
        self.other_user = User.objects.create_user(
            username="otheruser", email="other@example.com", password="testpassword123"
        )
        self.caption = Caption.objects.create(caption="테스트캡션", user=self.user)
        self.photo_id = uuid.uuid4()

    def test_photo_caption_creation_success(self):
        """Photo_Caption 생성 성공 테스트"""
        photo_caption = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id, weight=85
        )

        self.assertEqual(photo_caption.caption, self.caption)
        self.assertEqual(photo_caption.user, self.user)
        self.assertEqual(photo_caption.photo_id, self.photo_id)
        self.assertEqual(photo_caption.weight, 85)
        self.assertIsInstance(photo_caption.pc_id, uuid.UUID)

    def test_photo_caption_auto_uuid_generation(self):
        """Photo_Caption ID 자동 생성 테스트"""
        photo_caption1 = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=uuid.uuid4()
        )
        photo_caption2 = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=uuid.uuid4()
        )

        # UUID가 자동으로 생성되고 고유한지 확인
        self.assertIsInstance(photo_caption1.pc_id, uuid.UUID)
        self.assertIsInstance(photo_caption2.pc_id, uuid.UUID)
        self.assertNotEqual(photo_caption1.pc_id, photo_caption2.pc_id)

    def test_photo_caption_str_method(self):
        """Photo_Caption __str__ 메서드 테스트"""
        photo_caption = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id
        )

        expected_str = f"{self.photo_id} captioned with {self.caption}"
        self.assertEqual(str(photo_caption), expected_str)

    def test_photo_caption_default_weight(self):
        """Photo_Caption weight 기본값 테스트"""
        photo_caption = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id
        )

        # 기본값이 0인지 확인
        self.assertEqual(photo_caption.weight, 0)

    def test_photo_caption_weight_values(self):
        """Photo_Caption weight 값 범위 테스트"""
        weight_values = [0, 50, 100, -10, 999]

        for weight in weight_values:
            photo_caption = Photo_Caption.objects.create(
                caption=self.caption,
                user=self.user,
                photo_id=uuid.uuid4(),
                weight=weight,
            )
            self.assertEqual(photo_caption.weight, weight)

    def test_photo_caption_foreign_key_relationships(self):
        """Photo_Caption의 외래키 관계 테스트"""
        photo_caption = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id
        )

        # Caption 관계 확인
        self.assertEqual(photo_caption.caption, self.caption)
        self.assertEqual(photo_caption.caption.caption, "테스트캡션")

        # User 관계 확인
        self.assertEqual(photo_caption.user, self.user)
        self.assertEqual(photo_caption.user.username, "testuser")

    def test_photo_caption_cascade_delete_caption(self):
        """캡션 삭제 시 Photo_Caption도 삭제되는지 테스트"""
        photo_caption = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id
        )
        pc_id = photo_caption.pc_id

        # Photo_Caption이 생성되었는지 확인
        self.assertTrue(Photo_Caption.objects.filter(pc_id=pc_id).exists())

        # 캡션 삭제
        self.caption.delete()

        # Photo_Caption도 함께 삭제되었는지 확인
        self.assertFalse(Photo_Caption.objects.filter(pc_id=pc_id).exists())

    def test_photo_caption_cascade_delete_user(self):
        """사용자 삭제 시 Photo_Caption도 삭제되는지 테스트"""
        photo_caption = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id
        )
        pc_id = photo_caption.pc_id

        # Photo_Caption이 생성되었는지 확인
        self.assertTrue(Photo_Caption.objects.filter(pc_id=pc_id).exists())

        # 사용자 삭제 (캡션도 함께 삭제됨)
        self.user.delete()

        # Photo_Caption도 함께 삭제되었는지 확인
        self.assertFalse(Photo_Caption.objects.filter(pc_id=pc_id).exists())

    def test_photo_caption_multiple_captions_same_photo(self):
        """같은 사진에 여러 캡션 연결 테스트"""
        caption2 = Caption.objects.create(caption="두번째캡션", user=self.user)

        photo_caption1 = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id, weight=80
        )
        photo_caption2 = Photo_Caption.objects.create(
            caption=caption2, user=self.user, photo_id=self.photo_id, weight=60
        )

        # 같은 사진에 여러 캡션이 연결되는지 확인
        photo_captions = Photo_Caption.objects.filter(photo_id=self.photo_id)
        self.assertEqual(photo_captions.count(), 2)
        self.assertIn(photo_caption1, photo_captions)
        self.assertIn(photo_caption2, photo_captions)

    def test_photo_caption_weight_ordering(self):
        """Photo_Caption weight 기준 정렬 테스트"""
        caption2 = Caption.objects.create(caption="캡션2", user=self.user)
        caption3 = Caption.objects.create(caption="캡션3", user=self.user)

        # 다른 weight로 여러 캡션 생성
        Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id, weight=50
        )
        Photo_Caption.objects.create(
            caption=caption2, user=self.user, photo_id=self.photo_id, weight=90
        )
        Photo_Caption.objects.create(
            caption=caption3, user=self.user, photo_id=self.photo_id, weight=70
        )

        # weight 내림차순 정렬
        ordered_captions = Photo_Caption.objects.filter(
            photo_id=self.photo_id
        ).order_by("-weight")

        weights = [pc.weight for pc in ordered_captions]
        self.assertEqual(weights, [90, 70, 50])

    def test_photo_caption_model_fields(self):
        """Photo_Caption 모델 필드 속성 테스트"""
        photo_caption = Photo_Caption.objects.create(
            caption=self.caption, user=self.user, photo_id=self.photo_id
        )

        # 필드 타입 확인
        self.assertIsInstance(photo_caption._meta.get_field("pc_id"), models.UUIDField)
        self.assertIsInstance(
            photo_caption._meta.get_field("caption"), models.ForeignKey
        )
        self.assertIsInstance(photo_caption._meta.get_field("user"), models.ForeignKey)
        self.assertIsInstance(
            photo_caption._meta.get_field("photo_id"), models.UUIDField
        )
        self.assertIsInstance(
            photo_caption._meta.get_field("weight"), models.IntegerField
        )

        # 필드 속성 확인
        pc_id_field = photo_caption._meta.get_field("pc_id")
        self.assertTrue(pc_id_field.primary_key)
        self.assertFalse(pc_id_field.editable)

        weight_field = photo_caption._meta.get_field("weight")
        self.assertEqual(weight_field.default, 0)
