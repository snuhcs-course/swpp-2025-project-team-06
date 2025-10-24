import uuid
from datetime import datetime
from django.test import TestCase
from django.contrib.auth.models import User
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework import serializers
from PIL import Image
from io import BytesIO

from ..models import Tag
from ..serializers import TagSerializer
from ..request_serializers import (
    ReqPhotoDetailSerializer,
    ReqPhotoIdSerializer,
    ReqTagNameSerializer,
    ReqTagIdSerializer
)
from ..reponse_serializers import (
    ResPhotoSerializer,
    ResPhotoTagListSerializer,
    ResPhotoIdSerializer,
    ResTagIdSerializer,
    ResTagVectorSerializer
)


class TagSerializerTest(TestCase):
    """TagSerializer 테스트"""
    
    def setUp(self):
        self.user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='testpassword123'
        )
        self.tag = Tag.objects.create(tag='테스트태그', user=self.user)
    
    def test_tag_serializer_fields(self):
        """TagSerializer 필드 검증"""
        serializer = TagSerializer(instance=self.tag)
        data = serializer.data
        
        self.assertIn('tag_id', data)
        self.assertIn('tag', data)
        self.assertEqual(len(data), 2)  # 두 필드만 있어야 함
    
    def test_tag_serializer_data_types(self):
        """TagSerializer 데이터 타입 검증"""
        serializer = TagSerializer(instance=self.tag)
        data = serializer.data
        
        self.assertIsInstance(data['tag_id'], str)  # UUID는 문자열로 직렬화
        self.assertIsInstance(data['tag'], str)
        self.assertEqual(data['tag'], '테스트태그')
    
    def test_tag_serializer_with_multiple_tags(self):
        """여러 태그에 대한 TagSerializer 테스트"""
        tag2 = Tag.objects.create(tag='두번째태그', user=self.user)
        tags = [self.tag, tag2]
        
        serializer = TagSerializer(tags, many=True)
        data = serializer.data
        
        self.assertEqual(len(data), 2)
        tag_names = [item['tag'] for item in data]
        self.assertIn('테스트태그', tag_names)
        self.assertIn('두번째태그', tag_names)


class ReqPhotoDetailSerializerTest(TestCase):
    """ReqPhotoDetailSerializer 테스트"""
    
    def create_test_image(self):
        """테스트용 이미지 파일 생성"""
        image = Image.new('RGB', (100, 100), color='red')
        image_file = BytesIO()
        image.save(image_file, 'JPEG')
        image_file.seek(0)
        return SimpleUploadedFile(
            name='test_image.jpg',
            content=image_file.read(),
            content_type='image/jpeg'
        )
    
    def test_req_photo_detail_serializer_valid_data(self):
        """유효한 데이터로 ReqPhotoDetailSerializer 테스트"""
        test_image = self.create_test_image()
        data = {
            'photo': test_image,
            'filename': 'test_image.jpg',
            'photo_path_id': 12345,
            'created_at': datetime.now(),
            'lat': 37.5665,
            'lng': 126.9780
        }
        
        serializer = ReqPhotoDetailSerializer(data=data)
        self.assertTrue(serializer.is_valid())
        
        validated_data = serializer.validated_data
        self.assertEqual(validated_data['filename'], 'test_image.jpg')
        self.assertEqual(validated_data['photo_path_id'], 12345)
        self.assertEqual(validated_data['lat'], 37.5665)
        self.assertEqual(validated_data['lng'], 126.9780)
    
    def test_req_photo_detail_serializer_missing_required_fields(self):
        """필수 필드 누락 테스트"""
        incomplete_data = {
            'filename': 'test_image.jpg',
            'photo_path_id': 12345
            # photo, created_at, lat, lng 누락
        }
        
        serializer = ReqPhotoDetailSerializer(data=incomplete_data)
        self.assertFalse(serializer.is_valid())
        
        errors = serializer.errors
        self.assertIn('photo', errors)
        self.assertIn('created_at', errors)
        self.assertIn('lat', errors)
        self.assertIn('lng', errors)
    
    def test_req_photo_detail_serializer_invalid_data_types(self):
        """잘못된 데이터 타입 테스트"""
        test_image = self.create_test_image()
        data = {
            'photo': test_image,
            'filename': 'test_image.jpg',
            'photo_path_id': 'invalid_integer',  # 문자열이지만 정수여야 함
            'created_at': 'invalid_datetime',     # 잘못된 datetime 형식
            'lat': 'invalid_float',              # 문자열이지만 float여야 함
            'lng': 'invalid_float'               # 문자열이지만 float여야 함
        }
        
        serializer = ReqPhotoDetailSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        
        errors = serializer.errors
        self.assertIn('photo_path_id', errors)
        self.assertIn('created_at', errors)
        self.assertIn('lat', errors)
        self.assertIn('lng', errors)
    
    def test_req_photo_detail_serializer_coordinates_validation(self):
        """좌표 범위 검증 (위도 경도가 유효한 범위인지)"""
        test_image = self.create_test_image()
        
        # 유효한 좌표
        valid_data = {
            'photo': test_image,
            'filename': 'test_image.jpg',
            'photo_path_id': 12345,
            'created_at': datetime.now(),
            'lat': 37.5665,   # 서울 위도
            'lng': 126.9780   # 서울 경도
        }
        serializer = ReqPhotoDetailSerializer(data=valid_data)
        self.assertTrue(serializer.is_valid())
        
        # 극한 좌표 (여전히 유효함)
        extreme_data = {
            'photo': self.create_test_image(),
            'filename': 'test_image.jpg',
            'photo_path_id': 12345,
            'created_at': datetime.now(),
            'lat': 90.0,      # 북극
            'lng': -180.0     # 국제날짜변경선
        }
        serializer = ReqPhotoDetailSerializer(data=extreme_data)
        self.assertTrue(serializer.is_valid())


class ReqPhotoIdSerializerTest(TestCase):
    """ReqPhotoIdSerializer 테스트"""
    
    def test_req_photo_id_serializer_valid_uuid(self):
        """유효한 UUID로 ReqPhotoIdSerializer 테스트"""
        test_uuid = uuid.uuid4()
        data = {'photo_id': test_uuid}
        
        serializer = ReqPhotoIdSerializer(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['photo_id'], test_uuid)
    
    def test_req_photo_id_serializer_invalid_uuid(self):
        """잘못된 UUID로 ReqPhotoIdSerializer 테스트"""
        data = {'photo_id': 'invalid-uuid-string'}
        
        serializer = ReqPhotoIdSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('photo_id', serializer.errors)
    
    def test_req_photo_id_serializer_missing_field(self):
        """필수 필드 누락 테스트"""
        data = {}
        
        serializer = ReqPhotoIdSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('photo_id', serializer.errors)


class ReqTagNameSerializerTest(TestCase):
    """ReqTagNameSerializer 테스트"""
    
    def test_req_tag_name_serializer_valid_data(self):
        """유효한 태그명으로 ReqTagNameSerializer 테스트"""
        data = {'tag': '유효한태그'}
        
        serializer = ReqTagNameSerializer(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['tag'], '유효한태그')
    
    def test_req_tag_name_serializer_empty_tag(self):
        """빈 태그명 테스트"""
        data = {'tag': ''}
        
        serializer = ReqTagNameSerializer(data=data)
        self.assertFalse(serializer.is_valid())
    
    def test_req_tag_name_serializer_long_tag(self):
        """긴 태그명 테스트 (50자 초과)"""
        long_tag = 'a' * 51  # 51자
        data = {'tag': long_tag}
        
        serializer = ReqTagNameSerializer(data=data)
        # 현재 구현에서는 길이 제한이 없음 (추후 max_length 추가 필요)
        self.assertTrue(serializer.is_valid())
    
    def test_req_tag_name_serializer_missing_field(self):
        """필수 필드 누락 테스트"""
        data = {}
        
        serializer = ReqTagNameSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('tag', serializer.errors)
    
    def test_req_tag_name_serializer_special_characters(self):
        """특수문자가 포함된 태그명 테스트"""
        special_tags = [
            '태그@#$',
            'tag with spaces',
            '한글태그123',
            'English_tag',
            '🏖️여행',  # 이모지 포함
        ]
        
        for tag_name in special_tags:
            with self.subTest(tag=tag_name):
                data = {'tag': tag_name}
                serializer = ReqTagNameSerializer(data=data)
                self.assertTrue(serializer.is_valid())


class ReqTagIdSerializerTest(TestCase):
    """ReqTagIdSerializer 테스트"""
    
    def test_req_tag_id_serializer_valid_uuid(self):
        """유효한 UUID로 ReqTagIdSerializer 테스트"""
        test_uuid = uuid.uuid4()
        data = {'tag_id': test_uuid}
        
        serializer = ReqTagIdSerializer(data=data)
        self.assertTrue(serializer.is_valid())
        self.assertEqual(serializer.validated_data['tag_id'], test_uuid)
    
    def test_req_tag_id_serializer_invalid_uuid(self):
        """잘못된 UUID로 ReqTagIdSerializer 테스트"""
        data = {'tag_id': 'not-a-valid-uuid'}
        
        serializer = ReqTagIdSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('tag_id', serializer.errors)
    
    def test_req_tag_id_serializer_missing_field(self):
        """필수 필드 누락 테스트"""
        data = {}
        
        serializer = ReqTagIdSerializer(data=data)
        self.assertFalse(serializer.is_valid())
        self.assertIn('tag_id', serializer.errors)


class ResPhotoSerializerTest(TestCase):
    """ResPhotoSerializer 테스트"""
    
    def test_res_photo_serializer_valid_data(self):
        """유효한 데이터로 ResPhotoSerializer 테스트"""
        test_uuid = uuid.uuid4()
        data = {
            'photo_id': test_uuid,
            'photo_path_id': 12345
        }
        
        serializer = ResPhotoSerializer(data=data)
        self.assertTrue(serializer.is_valid())
        
        validated_data = serializer.validated_data
        self.assertEqual(validated_data['photo_id'], test_uuid)
        self.assertEqual(validated_data['photo_path_id'], 12345)
    
    def test_res_photo_serializer_serialization(self):
        """ResPhotoSerializer 직렬화 테스트"""
        test_uuid = uuid.uuid4()
        data = {
            'photo_id': test_uuid,
            'photo_path_id': 12345
        }
        
        serializer = ResPhotoSerializer(data)
        serialized_data = serializer.data
        
        self.assertEqual(serialized_data['photo_id'], str(test_uuid))
        self.assertEqual(serialized_data['photo_path_id'], 12345)


class ResPhotoTagListSerializerTest(TestCase):
    """ResPhotoTagListSerializer 테스트"""
    
    def setUp(self):
        self.user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='testpassword123'
        )
        self.tag1 = Tag.objects.create(tag='태그1', user=self.user)
        self.tag2 = Tag.objects.create(tag='태그2', user=self.user)
    
    def test_res_photo_tag_list_serializer_with_tags(self):
        """태그가 있는 사진에 대한 ResPhotoTagListSerializer 테스트"""
        data = {
            'photo_path_id': 12345,
            'tags': [self.tag1, self.tag2]
        }
        
        serializer = ResPhotoTagListSerializer(data)
        serialized_data = serializer.data
        
        self.assertEqual(serialized_data['photo_path_id'], 12345)
        self.assertEqual(len(serialized_data['tags']), 2)
        
        tag_names = [tag['tag'] for tag in serialized_data['tags']]
        self.assertIn('태그1', tag_names)
        self.assertIn('태그2', tag_names)
    
    def test_res_photo_tag_list_serializer_no_tags(self):
        """태그가 없는 사진에 대한 ResPhotoTagListSerializer 테스트"""
        data = {
            'photo_path_id': 12345,
            'tags': []
        }
        
        serializer = ResPhotoTagListSerializer(data)
        serialized_data = serializer.data
        
        self.assertEqual(serialized_data['photo_path_id'], 12345)
        self.assertEqual(len(serialized_data['tags']), 0)


class ResPhotoIdSerializerTest(TestCase):
    """ResPhotoIdSerializer 테스트"""
    
    def test_res_photo_id_serializer(self):
        """ResPhotoIdSerializer 테스트"""
        test_uuid = uuid.uuid4()
        data = {'photo_id': test_uuid}
        
        serializer = ResPhotoIdSerializer(data)
        serialized_data = serializer.data
        
        self.assertEqual(serialized_data['photo_id'], str(test_uuid))


class ResTagIdSerializerTest(TestCase):
    """ResTagIdSerializer 테스트"""
    
    def test_res_tag_id_serializer(self):
        """ResTagIdSerializer 테스트"""
        test_uuid = uuid.uuid4()
        data = {'tag_id': test_uuid}
        
        serializer = ResTagIdSerializer(data)
        serialized_data = serializer.data
        
        self.assertEqual(serialized_data['tag_id'], str(test_uuid))


class ResTagVectorSerializerTest(TestCase):
    """ResTagVectorSerializer 테스트"""
    
    def test_res_tag_vector_serializer(self):
        """ResTagVectorSerializer 테스트"""
        data = {'tag': '벡터태그'}
        
        serializer = ResTagVectorSerializer(data)
        serialized_data = serializer.data
        
        self.assertEqual(serialized_data['tag'], '벡터태그')
    
    def test_res_tag_vector_serializer_various_tags(self):
        """다양한 태그명에 대한 ResTagVectorSerializer 테스트"""
        test_tags = [
            '영어Tag',
            '한글태그',
            'Special@#$',
            '🏖️이모지태그',
            '123숫자포함'
        ]
        
        for tag_name in test_tags:
            with self.subTest(tag=tag_name):
                data = {'tag': tag_name}
                serializer = ResTagVectorSerializer(data)
                serialized_data = serializer.data
                self.assertEqual(serialized_data['tag'], tag_name)


class SerializerIntegrationTest(TestCase):
    """Serializer 간 통합 테스트"""
    
    def setUp(self):
        self.user = User.objects.create_user(
            username='testuser',
            email='test@example.com',
            password='testpassword123'
        )
        self.tag = Tag.objects.create(tag='통합테스트태그', user=self.user)
    
    def test_tag_serializer_with_response_serializers(self):
        """TagSerializer와 Response serializer 간 호환성 테스트"""
        # TagSerializer로 태그 직렬화
        tag_serializer = TagSerializer(instance=self.tag)
        tag_data = tag_serializer.data
        
        # ResTagIdSerializer로 tag_id만 추출
        tag_id_data = {'tag_id': tag_data['tag_id']}
        res_tag_id_serializer = ResTagIdSerializer(tag_id_data)
        
        self.assertEqual(
            res_tag_id_serializer.data['tag_id'],
            tag_data['tag_id']
        )
        
        # ResTagVectorSerializer로 tag명만 추출
        tag_vector_data = {'tag': tag_data['tag']}
        res_tag_vector_serializer = ResTagVectorSerializer(tag_vector_data)
        
        self.assertEqual(
            res_tag_vector_serializer.data['tag'],
            tag_data['tag']
        )
    
    def test_request_response_serializer_flow(self):
        """Request → Processing → Response serializer 플로우 테스트"""
        # 1. Request: 태그 생성 요청
        req_data = {'tag': '플로우테스트태그'}
        req_serializer = ReqTagNameSerializer(data=req_data)
        self.assertTrue(req_serializer.is_valid())
        
        # 2. Processing: 태그 생성 (실제 뷰에서 수행될 작업)
        validated_data = req_serializer.validated_data
        new_tag = Tag.objects.create(
            tag=validated_data['tag'], 
            user=self.user
        )
        
        # 3. Response: 생성된 태그 정보 반환
        res_data = {'tag_id': new_tag.tag_id}
        res_serializer = ResTagIdSerializer(res_data)
        
        self.assertEqual(
            res_serializer.data['tag_id'], 
            str(new_tag.tag_id)
        )
        
        # 4. 생성된 태그가 실제로 올바른지 확인
        self.assertTrue(
            Tag.objects.filter(
                tag_id=new_tag.tag_id,
                tag='플로우테스트태그',
                user=self.user
            ).exists()
        )


class SerializerEdgeCaseTest(TestCase):
    """Serializer 엣지 케이스 테스트"""
    
    def test_unicode_handling(self):
        """유니코드 문자 처리 테스트"""
        unicode_tags = [
            '한글태그',
            'English Tag',
            '中文标签',
            'العربية',
            'Русский',
            '🏖️🎵🎨',  # 이모지
            'Mixed한글English123🎯'
        ]
        
        for tag_name in unicode_tags:
            with self.subTest(tag=tag_name):
                # Request serializer 테스트
                req_data = {'tag': tag_name}
                req_serializer = ReqTagNameSerializer(data=req_data)
                self.assertTrue(req_serializer.is_valid())
                
                # Response serializer 테스트
                res_data = {'tag': tag_name}
                res_serializer = ResTagVectorSerializer(res_data)
                self.assertEqual(res_serializer.data['tag'], tag_name)
    
    def test_extreme_values(self):
        """극한 값 테스트"""
        # UUID 테스트
        uuid_serializers = [ReqPhotoIdSerializer, ReqTagIdSerializer]
        
        for serializer_class in uuid_serializers:
            with self.subTest(serializer=serializer_class.__name__):
                # 유효한 UUID들
                valid_uuids = [
                    uuid.uuid4(),
                    uuid.UUID('00000000-0000-0000-0000-000000000000'),
                    uuid.UUID('ffffffff-ffff-ffff-ffff-ffffffffffff'),
                ]
                
                field_name = 'photo_id' if 'Photo' in serializer_class.__name__ else 'tag_id'
                
                for test_uuid in valid_uuids:
                    data = {field_name: test_uuid}
                    serializer = serializer_class(data=data)
                    self.assertTrue(serializer.is_valid())
        
        # 정수 범위 테스트 (photo_path_id)
        extreme_integers = [0, 1, -1, 2147483647, -2147483648]  # 32-bit 정수 범위
        
        for int_value in extreme_integers:
            with self.subTest(value=int_value):
                data = {'photo_path_id': int_value}
                serializer = ResPhotoSerializer(data=data)
                # UUID 필드가 없어서 실패하지만 정수 필드는 검증됨
                self.assertFalse(serializer.is_valid())
                self.assertNotIn('photo_path_id', serializer.errors)
    
    def test_none_and_null_values(self):
        """None과 null 값 처리 테스트"""
        # None 값으로 시리얼라이저 테스트
        serializers_to_test = [
            (ReqTagNameSerializer, {'tag': None}),
            (ReqPhotoIdSerializer, {'photo_id': None}),
            (ReqTagIdSerializer, {'tag_id': None}),
        ]
        
        for serializer_class, data in serializers_to_test:
            with self.subTest(serializer=serializer_class.__name__):
                serializer = serializer_class(data=data)
                self.assertFalse(serializer.is_valid())
                # None 값은 유효하지 않아야 함
                self.assertTrue(len(serializer.errors) > 0)
