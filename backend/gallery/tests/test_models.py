import uuid
from django.test import TestCase
from django.contrib.auth.models import User
from django.core.exceptions import ValidationError

from ..models import Tag
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
