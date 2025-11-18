package com.example.momentag.model

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataModelsTest {
    private lateinit var mockUri: Uri

    @Before
    fun setUp() {
        mockUri = mockk()
        every { mockUri.toString() } returns "content://test/image.jpg"
    }

    // ========== Tag Tests ==========

    @Test
    fun `Tag should be created with correct properties`() {
        // Given
        val tagName = "Nature"
        val tagId = "tag123"

        // When
        val tag = Tag(tagName = tagName, tagId = tagId)

        // Then
        assertEquals(tagName, tag.tagName)
        assertEquals(tagId, tag.tagId)
    }

    @Test
    fun `Tag equality test`() {
        // Given
        val tag1 = Tag(tagName = "Nature", tagId = "tag123")
        val tag2 = Tag(tagName = "Nature", tagId = "tag123")
        val tag3 = Tag(tagName = "City", tagId = "tag456")

        // Then
        assertEquals(tag1, tag2)
        assertNotEquals(tag1, tag3)
    }

    @Test
    fun `Tag copy should create new instance with modified properties`() {
        // Given
        val originalTag = Tag(tagName = "Nature", tagId = "tag123")

        // When
        val copiedTag = originalTag.copy(tagName = "Wildlife")

        // Then
        assertEquals("Wildlife", copiedTag.tagName)
        assertEquals("tag123", copiedTag.tagId)
        assertNotEquals(originalTag, copiedTag)
    }

    // ========== TagItem Tests ==========

    @Test
    fun `TagItem should be created with correct properties`() {
        // Given
        val tagName = "Vacation"
        val coverImageId = 100L
        val tagId = "tag789"
        val createdAt = "2025-11-01T10:00:00"
        val updatedAt = "2025-11-02T15:30:00"
        val photoCount = 42

        // When
        val tagItem =
            TagItem(
                tagName = tagName,
                coverImageId = coverImageId,
                tagId = tagId,
                createdAt = createdAt,
                updatedAt = updatedAt,
                photoCount = photoCount,
            )

        // Then
        assertEquals(tagName, tagItem.tagName)
        assertEquals(coverImageId, tagItem.coverImageId)
        assertEquals(tagId, tagItem.tagId)
        assertEquals(createdAt, tagItem.createdAt)
        assertEquals(updatedAt, tagItem.updatedAt)
        assertEquals(photoCount, tagItem.photoCount)
    }

    @Test
    fun `TagItem with null coverImageId should be handled correctly`() {
        // Given & When
        val tagItem =
            TagItem(
                tagName = "Test",
                coverImageId = null,
                tagId = "tag001",
                createdAt = "2025-11-01T10:00:00",
                updatedAt = null,
                photoCount = 0,
            )

        // Then
        assertNull(tagItem.coverImageId)
        assertNull(tagItem.updatedAt)
        assertEquals("Test", tagItem.tagName)
        assertEquals("tag001", tagItem.tagId)
        assertEquals(0, tagItem.photoCount)
    }

    // ========== TagCreateRequest Tests - REMOVED (class no longer exists) ==========

    // ========== PhotoDetailResponse Tests ==========

    @Test
    fun `PhotoDetailResponse should be created with correct properties`() {
        // Given
        val photoPathId = 12345L
        val address = "Seoul, South Korea"
        val tags =
            listOf(
                Tag("Nature", "tag1"),
                Tag("Sunset", "tag2"),
            )

        // When
        val response =
            PhotoDetailResponse(
                photoPathId = photoPathId,
                address = address,
                tags = tags,
            )

        // Then
        assertEquals(photoPathId, response.photoPathId)
        assertEquals(address, response.address)
        assertEquals(2, response.tags.size)
        assertEquals("Nature", response.tags[0].tagName)
        assertEquals("Sunset", response.tags[1].tagName)
    }

    @Test
    fun `PhotoDetailResponse with empty tags list`() {
        // Given & When
        val response =
            PhotoDetailResponse(
                photoPathId = 999L,
                address = null,
                tags = emptyList(),
            )

        // Then
        assertTrue(response.tags.isEmpty())
        assertEquals(999L, response.photoPathId)
        assertNull(response.address)
    }

    // ========== PhotoResponse Tests ==========

    @Test
    fun `PhotoResponse should be created with correct properties`() {
        // Given
        val photoId = "photo123"
        val photoPathId = 456L
        val createdAt = "2025-11-01T10:00:00"

        // When
        val response =
            PhotoResponse(
                photoId = photoId,
                photoPathId = photoPathId,
                createdAt = createdAt,
            )

        // Then
        assertEquals(photoId, response.photoId)
        assertEquals(photoPathId, response.photoPathId)
        assertEquals(createdAt, response.createdAt)
    }

    // ========== Photo Tests ==========

    @Test
    fun `Photo should be created with Uri`() {
        // Given
        val photoId = "photo789"
        val createdAt = "2025-11-01T10:00:00"

        // When
        val photo =
            Photo(
                photoId = photoId,
                contentUri = mockUri,
                createdAt = createdAt,
            )

        // Then
        assertEquals(photoId, photo.photoId)
        assertEquals(mockUri, photo.contentUri)
        assertEquals(createdAt, photo.createdAt)
        assertEquals("content://test/image.jpg", photo.contentUri.toString())
    }

    @Test
    fun `Photo equality test with same Uri`() {
        // Given
        val createdAt = "2025-11-01T10:00:00"
        val photo1 = Photo(photoId = "photo1", contentUri = mockUri, createdAt = createdAt)
        val photo2 = Photo(photoId = "photo1", contentUri = mockUri, createdAt = createdAt)

        // Then
        assertEquals(photo1, photo2)
    }

    // ========== Photos Tests ==========

    @Test
    fun `Photos should contain list of Photo objects`() {
        // Given
        val createdAt = "2025-11-01T10:00:00"
        val photoList =
            listOf(
                Photo("photo1", mockUri, createdAt),
                Photo("photo2", mockUri, createdAt),
                Photo("photo3", mockUri, createdAt),
            )

        // When
        val photos = Photos(photos = photoList)

        // Then
        assertEquals(3, photos.photos.size)
        assertEquals("photo1", photos.photos[0].photoId)
        assertEquals("photo2", photos.photos[1].photoId)
        assertEquals("photo3", photos.photos[2].photoId)
    }

    @Test
    fun `Photos with empty list`() {
        // Given & When
        val photos = Photos(photos = emptyList())

        // Then
        assertTrue(photos.photos.isEmpty())
    }

    // ========== TagId Tests ==========

    @Test
    fun `TagId should be created with id`() {
        // Given
        val tagId = "tag999"

        // When
        val tag = TagId(id = tagId)

        // Then
        assertEquals(tagId, tag.id)
    }

    // ========== PhotoTag Tests ==========

    @Test
    fun `PhotoTag should be created with ptId`() {
        // Given
        val ptId = 555L

        // When
        val photoTag = PhotoTag(ptId = ptId)

        // Then
        assertEquals(ptId, photoTag.ptId)
    }

    // ========== Album Tests ==========

    @Test
    fun `Album should be created with correct properties`() {
        // Given
        val albumId = 10L
        val albumName = "Summer 2025"

        // When
        val album =
            Album(
                albumId = albumId,
                albumName = albumName,
                thumbnailUri = mockUri,
            )

        // Then
        assertEquals(albumId, album.albumId)
        assertEquals(albumName, album.albumName)
        assertEquals(mockUri, album.thumbnailUri)
    }

    @Test
    fun `Album copy should work correctly`() {
        // Given
        val original =
            Album(
                albumId = 1L,
                albumName = "Old Name",
                thumbnailUri = mockUri,
            )

        // When
        val copied = original.copy(albumName = "New Name")

        // Then
        assertEquals(1L, copied.albumId)
        assertEquals("New Name", copied.albumName)
        assertEquals(mockUri, copied.thumbnailUri)
    }

    // ========== PhotoToPhotoRequest Tests ==========

    @Test
    fun `PhotoToPhotoRequest should contain list of photo IDs`() {
        // Given
        val photoIds = listOf("photo1", "photo2", "photo3")

        // When
        val request = PhotoToPhotoRequest(photos = photoIds)

        // Then
        assertEquals(3, request.photos.size)
        assertEquals("photo1", request.photos[0])
        assertEquals("photo3", request.photos[2])
    }

    @Test
    fun `PhotoToPhotoRequest with empty list`() {
        // Given & When
        val request = PhotoToPhotoRequest(photos = emptyList())

        // Then
        assertTrue(request.photos.isEmpty())
    }

    // ========== LoginRequest Tests ==========

    @Test
    fun `LoginRequest should be created with username and password`() {
        // Given
        val username = "testuser"
        val password = "testpass123"

        // When
        val request =
            LoginRequest(
                username = username,
                password = password,
            )

        // Then
        assertEquals(username, request.username)
        assertEquals(password, request.password)
    }

    @Test
    fun `LoginRequest with empty credentials`() {
        // Given & When
        val request = LoginRequest(username = "", password = "")

        // Then
        assertTrue(request.username.isEmpty())
        assertTrue(request.password.isEmpty())
    }

    // ========== RegisterRequest Tests ==========

    @Test
    fun `RegisterRequest should be created with all fields`() {
        // Given
        val email = "test@example.com"
        val username = "testuser"
        val password = "password123"

        // When
        val request =
            RegisterRequest(
                email = email,
                username = username,
                password = password,
            )

        // Then
        assertEquals(email, request.email)
        assertEquals(username, request.username)
        assertEquals(password, request.password)
    }

    @Test
    fun `RegisterRequest email validation format`() {
        // Given
        val email = "valid.email@domain.com"
        val request =
            RegisterRequest(
                email = email,
                username = "user",
                password = "pass",
            )

        // Then
        assertTrue(request.email.contains("@"))
        assertTrue(request.email.contains("."))
    }

    // ========== RegisterResponse Tests ==========

    @Test
    fun `RegisterResponse should return user ID`() {
        // Given
        val userId = 12345

        // When
        val response = RegisterResponse(id = userId)

        // Then
        assertEquals(userId, response.id)
    }

    @Test
    fun `RegisterResponse with negative ID should be handled`() {
        // Given & When
        val response = RegisterResponse(id = -1)

        // Then
        assertEquals(-1, response.id)
    }

    // ========== LoginResponse Tests ==========

    @Test
    fun `LoginResponse should contain tokens`() {
        // Given
        val accessToken = "access_token_xyz"
        val refreshToken = "refresh_token_abc"

        // When
        val response =
            LoginResponse(
                access_token = accessToken,
                refresh_token = refreshToken,
            )

        // Then
        assertEquals(accessToken, response.access_token)
        assertEquals(refreshToken, response.refresh_token)
    }

    @Test
    fun `LoginResponse tokens should not be empty`() {
        // Given & When
        val response =
            LoginResponse(
                access_token = "token123",
                refresh_token = "refresh456",
            )

        // Then
        assertFalse(response.access_token.isEmpty())
        assertFalse(response.refresh_token.isEmpty())
    }

    // ========== RefreshRequest Tests ==========

    @Test
    fun `RefreshRequest should contain refresh token`() {
        // Given
        val refreshToken = "refresh_token_123"

        // When
        val request = RefreshRequest(refresh_token = refreshToken)

        // Then
        assertEquals(refreshToken, request.refresh_token)
    }

    // ========== RefreshResponse Tests ==========

    @Test
    fun `RefreshResponse should return new access token`() {
        // Given
        val newAccessToken = "new_access_token_456"

        // When
        val response = RefreshResponse(access_token = newAccessToken)

        // Then
        assertEquals(newAccessToken, response.access_token)
    }

    // ========== StoryResponse Tests ==========

    @Test
    fun `StoryResponse should contain photo information and tags`() {
        // Given
        val photoId = "photo1"
        val photoPathId = 1L
        val tags = listOf("tag1", "tag2", "tag3")

        // When
        val response = StoryResponse(photoId = photoId, photoPathId = photoPathId, tags = tags)

        // Then
        assertEquals(photoId, response.photoId)
        assertEquals(photoPathId, response.photoPathId)
        assertEquals(3, response.tags.size)
        assertEquals("tag1", response.tags[0])
    }

    @Test
    fun `StoryResponse with empty tags`() {
        // Given & When
        val response = StoryResponse(photoId = "photo1", photoPathId = 1L, tags = emptyList())

        // Then
        assertTrue(response.tags.isEmpty())
    }

    // ========== PhotoMeta Tests ==========

    @Test
    fun `PhotoMeta should be created with all fields`() {
        // Given
        val filename = "IMG_001.jpg"
        val photoPathId = 100
        val createdAt = "2025-11-02T10:30:00"
        val lat = 37.5665
        val lng = 126.9780

        // When
        val meta =
            PhotoMeta(
                filename = filename,
                photo_path_id = photoPathId,
                created_at = createdAt,
                lat = lat,
                lng = lng,
            )

        // Then
        assertEquals(filename, meta.filename)
        assertEquals(photoPathId, meta.photo_path_id)
        assertEquals(createdAt, meta.created_at)
        assertEquals(lat, meta.lat, 0.0001)
        assertEquals(lng, meta.lng, 0.0001)
    }

    @Test
    fun `PhotoMeta coordinates should handle negative values`() {
        // Given & When
        val meta =
            PhotoMeta(
                filename = "test.jpg",
                photo_path_id = 1,
                created_at = "2025-01-01",
                lat = -33.8688, // Sydney
                lng = 151.2093,
            )

        // Then
        assertTrue(meta.lat < 0)
        assertTrue(meta.lng > 0)
    }

    @Test
    fun `PhotoMeta coordinates should handle zero values`() {
        // Given & When
        val meta =
            PhotoMeta(
                filename = "test.jpg",
                photo_path_id = 1,
                created_at = "2025-01-01",
                lat = 0.0,
                lng = 0.0,
            )

        // Then
        assertEquals(0.0, meta.lat, 0.0001)
        assertEquals(0.0, meta.lng, 0.0001)
    }

    // ========== PhotoUploadData Tests ==========

    @Test
    fun `PhotoUploadData should contain photo parts and metadata`() {
        // Given
        val photoParts =
            listOf(
                mockk<MultipartBody.Part>(),
                mockk<MultipartBody.Part>(),
            )
        val metadata = "{}".toRequestBody("application/json".toMediaTypeOrNull())

        // When
        val uploadData =
            PhotoUploadData(
                photo = photoParts,
                metadata = metadata,
            )

        // Then
        assertEquals(2, uploadData.photo.size)
        assertNotNull(uploadData.metadata)
    }

    @Test
    fun `PhotoUploadData with empty photo list`() {
        // Given
        val metadata = "{}".toRequestBody("application/json".toMediaTypeOrNull())

        // When
        val uploadData =
            PhotoUploadData(
                photo = emptyList(),
                metadata = metadata,
            )

        // Then
        assertTrue(uploadData.photo.isEmpty())
        assertNotNull(uploadData.metadata)
    }

    // ========== StoryModel Tests ==========

    @Test
    fun `StoryModel should be created with all properties`() {
        // Given
        val id = "story1"
        val photoId = "photo1"
        val images = listOf(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
        val date = "2025-11-02"
        val location = "Seoul, Korea"
        val suggestedTags = listOf("Nature", "City", "Night")

        // When
        val story =
            StoryModel(
                id = id,
                photoId = photoId,
                images = images,
                date = date,
                location = location,
                suggestedTags = suggestedTags,
            )

        // Then
        assertEquals(id, story.id)
        assertEquals(photoId, story.photoId)
        assertEquals(2, story.images.size)
        assertEquals(date, story.date)
        assertEquals(location, story.location)
        assertEquals(3, story.suggestedTags.size)
    }

    @Test
    fun `StoryModel with empty lists`() {
        // Given & When
        val story =
            StoryModel(
                id = "story1",
                photoId = "photo1",
                images = emptyList(),
                date = "2025-11-02",
                location = "Seoul",
                suggestedTags = emptyList(),
            )

        // Then
        assertTrue(story.images.isEmpty())
        assertTrue(story.suggestedTags.isEmpty())
        assertFalse(story.location.isEmpty())
    }

    @Test
    fun `StoryModel copy should work correctly`() {
        // Given
        val original =
            StoryModel(
                id = "story1",
                photoId = "photo1",
                images = listOf(Uri.parse("content://media/1")),
                date = "2025-11-01",
                location = "Seoul",
                suggestedTags = listOf("Tag1"),
            )

        // When
        val copied =
            original.copy(
                date = "2025-11-02",
                suggestedTags = listOf("Tag1", "Tag2"),
            )

        // Then
        assertEquals("story1", copied.id)
        assertEquals("2025-11-02", copied.date)
        assertEquals(2, copied.suggestedTags.size)
    }

    @Test
    fun `StoryModel equality test`() {
        // Given
        val uri1 = Uri.parse("content://media/1")
        val uri2 = Uri.parse("content://media/2")
        val story1 =
            StoryModel(
                id = "story1",
                photoId = "photo1",
                images = listOf(uri1),
                date = "2025-11-02",
                location = "Seoul",
                suggestedTags = listOf("Nature"),
            )
        val story2 =
            StoryModel(
                id = "story1",
                photoId = "photo1",
                images = listOf(uri1),
                date = "2025-11-02",
                location = "Seoul",
                suggestedTags = listOf("Nature"),
            )
        val story3 =
            StoryModel(
                id = "story2",
                photoId = "photo2",
                images = listOf(uri2),
                date = "2025-11-03",
                location = "Busan",
                suggestedTags = listOf("City"),
            )

        // Then
        assertEquals(story1, story2)
        assertNotEquals(story1, story3)
    }

    // ========== Additional Edge Case Tests ==========

    @Test
    fun `data classes should support toString`() {
        // Given
        val tag = Tag("TestTag", "tag123")

        // When
        val stringRepresentation = tag.toString()

        // Then
        assertTrue(stringRepresentation.contains("TestTag"))
        assertTrue(stringRepresentation.contains("tag123"))
    }

    @Test
    fun `data classes should support hashCode`() {
        // Given
        val tag1 = Tag("TestTag", "tag123")
        val tag2 = Tag("TestTag", "tag123")
        val tag3 = Tag("DifferentTag", "tag456")

        // Then
        assertEquals(tag1.hashCode(), tag2.hashCode())
        assertNotEquals(tag1.hashCode(), tag3.hashCode())
    }

    @Test
    fun `nested data structures should work correctly`() {
        // Given
        val tags =
            listOf(
                Tag("Tag1", "id1"),
                Tag("Tag2", "id2"),
            )
        val photoResponse =
            PhotoDetailResponse(
                photoPathId = 100L,
                address = "Seoul, South Korea",
                tags = tags,
            )

        // When & Then
        assertEquals(2, photoResponse.tags.size)
        assertEquals("Tag1", photoResponse.tags[0].tagName)
        assertEquals("id2", photoResponse.tags[1].tagId)
    }

    // ========== Image Context Tests ==========

    private fun createSamplePhoto(
        id: String,
        uriString: String,
    ): Photo {
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns uriString
        return Photo(
            photoId = id,
            contentUri = mockUri,
            createdAt = "2024-01-01T00:00:00Z",
        )
    }

    @Test
    fun `ImageContext 생성 테스트`() {
        // Given
        val photos =
            listOf(
                createSamplePhoto("1", "content://media/external/images/1"),
                createSamplePhoto("2", "content://media/external/images/2"),
                createSamplePhoto("3", "content://media/external/images/3"),
            )

        // When
        val imageContext =
            ImageContext(
                images = photos,
                currentIndex = 1,
                contextType = ImageContext.ContextType.ALBUM,
            )

        // Then
        assertEquals(3, imageContext.images.size)
        assertEquals(1, imageContext.currentIndex)
        assertEquals(ImageContext.ContextType.ALBUM, imageContext.contextType)
    }

    @Test
    fun `ImageContext 빈 이미지 리스트 테스트`() {
        // Given
        val emptyPhotos = emptyList<Photo>()

        // When
        val imageContext =
            ImageContext(
                images = emptyPhotos,
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )

        // Then
        assertTrue(imageContext.images.isEmpty())
        assertEquals(0, imageContext.currentIndex)
        assertEquals(ImageContext.ContextType.GALLERY, imageContext.contextType)
    }

    @Test
    fun `ImageContext 모든 ContextType 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))

        // When & Then - ALBUM
        val albumContext = ImageContext(photos, 0, ImageContext.ContextType.ALBUM)
        assertEquals(ImageContext.ContextType.ALBUM, albumContext.contextType)

        // When & Then - TAG_ALBUM
        val tagAlbumContext = ImageContext(photos, 0, ImageContext.ContextType.TAG_ALBUM)
        assertEquals(ImageContext.ContextType.TAG_ALBUM, tagAlbumContext.contextType)

        // When & Then - SEARCH_RESULT
        val searchContext = ImageContext(photos, 0, ImageContext.ContextType.SEARCH_RESULT)
        assertEquals(ImageContext.ContextType.SEARCH_RESULT, searchContext.contextType)

        // When & Then - GALLERY
        val galleryContext = ImageContext(photos, 0, ImageContext.ContextType.GALLERY)
        assertEquals(ImageContext.ContextType.GALLERY, galleryContext.contextType)
    }

    @Test
    fun `ImageContext copy 테스트`() {
        // Given
        val photos =
            listOf(
                createSamplePhoto("1", "content://media/external/images/1"),
                createSamplePhoto("2", "content://media/external/images/2"),
            )
        val original =
            ImageContext(
                images = photos,
                currentIndex = 0,
                contextType = ImageContext.ContextType.ALBUM,
            )

        // When - currentIndex만 변경
        val copied = original.copy(currentIndex = 1)

        // Then
        assertEquals(original.images, copied.images)
        assertEquals(1, copied.currentIndex)
        assertEquals(original.contextType, copied.contextType)
    }

    @Test
    fun `ImageContext copy로 contextType 변경 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        val original =
            ImageContext(
                images = photos,
                currentIndex = 0,
                contextType = ImageContext.ContextType.ALBUM,
            )

        // When
        val copied = original.copy(contextType = ImageContext.ContextType.TAG_ALBUM)

        // Then
        assertEquals(original.images, copied.images)
        assertEquals(original.currentIndex, copied.currentIndex)
        assertEquals(ImageContext.ContextType.TAG_ALBUM, copied.contextType)
    }

    @Test
    fun `ImageContext 경계값 인덱스 테스트`() {
        // Given
        val photos =
            listOf(
                createSamplePhoto("1", "content://media/external/images/1"),
                createSamplePhoto("2", "content://media/external/images/2"),
                createSamplePhoto("3", "content://media/external/images/3"),
            )

        // When - 첫 번째 인덱스
        val firstContext = ImageContext(photos, 0, ImageContext.ContextType.GALLERY)

        // Then
        assertEquals(0, firstContext.currentIndex)

        // When - 마지막 인덱스
        val lastContext = ImageContext(photos, 2, ImageContext.ContextType.GALLERY)

        // Then
        assertEquals(2, lastContext.currentIndex)
    }

    @Test
    fun `ImageContext 데이터 클래스 equals 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        val context1 = ImageContext(photos, 0, ImageContext.ContextType.ALBUM)
        val context2 = ImageContext(photos, 0, ImageContext.ContextType.ALBUM)
        val context3 = ImageContext(photos, 1, ImageContext.ContextType.ALBUM)

        // Then
        assertEquals(context1, context2)
        assertNotEquals(context1, context3)
    }

    @Test
    fun `ImageContext hashCode 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        val context1 = ImageContext(photos, 0, ImageContext.ContextType.ALBUM)
        val context2 = ImageContext(photos, 0, ImageContext.ContextType.ALBUM)

        // Then
        assertEquals(context1.hashCode(), context2.hashCode())
    }

    @Test
    fun `ImageContext toString 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        val context = ImageContext(photos, 0, ImageContext.ContextType.SEARCH_RESULT)

        // When
        val toString = context.toString()

        // Then
        assertTrue(toString.contains("ImageContext"))
        assertTrue(toString.contains("currentIndex=0"))
        assertTrue(toString.contains("SEARCH_RESULT"))
    }

    @Test
    fun `ContextType enum 값 테스트`() {
        // When & Then
        val values = ImageContext.ContextType.values()

        assertEquals(5, values.size)
        assertTrue(values.contains(ImageContext.ContextType.ALBUM))
        assertTrue(values.contains(ImageContext.ContextType.TAG_ALBUM))
        assertTrue(values.contains(ImageContext.ContextType.SEARCH_RESULT))
        assertTrue(values.contains(ImageContext.ContextType.GALLERY))
        assertTrue(values.contains(ImageContext.ContextType.STORY))
    }

    @Test
    fun `ContextType valueOf 테스트`() {
        // When & Then
        assertEquals(ImageContext.ContextType.ALBUM, ImageContext.ContextType.valueOf("ALBUM"))
        assertEquals(ImageContext.ContextType.TAG_ALBUM, ImageContext.ContextType.valueOf("TAG_ALBUM"))
        assertEquals(ImageContext.ContextType.SEARCH_RESULT, ImageContext.ContextType.valueOf("SEARCH_RESULT"))
        assertEquals(ImageContext.ContextType.GALLERY, ImageContext.ContextType.valueOf("GALLERY"))
    }

    @Test
    fun `ImageContext 여러 사진이 있는 경우 테스트`() {
        // Given
        val manyPhotos =
            (1..100).map {
                createSamplePhoto(it.toString(), "content://media/external/images/$it")
            }

        // When
        val context =
            ImageContext(
                images = manyPhotos,
                currentIndex = 50,
                contextType = ImageContext.ContextType.GALLERY,
            )

        // Then
        assertEquals(100, context.images.size)
        assertEquals(50, context.currentIndex)
        assertEquals("51", context.images[50].photoId) // 인덱스 50 = 51번째 사진 (1부터 시작)
    }

    @Test
    fun `ImageContext 음수 인덱스도 허용 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))

        // When
        val context =
            ImageContext(
                images = photos,
                currentIndex = -1,
                contextType = ImageContext.ContextType.ALBUM,
            )

        // Then - 데이터 클래스는 음수 인덱스도 허용 (비즈니스 로직에서 검증해야 함)
        assertEquals(-1, context.currentIndex)
    }

    // ========== TagAlbum Tests ==========

    @Test
    fun `TagAlbum 생성 테스트`() {
        // Given
        val tagName = "여행"
        val photos = listOf("photo1", "photo2", "photo3")

        // When
        val tagAlbum =
            TagAlbum(
                tagName = tagName,
                photos = photos,
            )

        // Then
        assertEquals("여행", tagAlbum.tagName)
        assertEquals(3, tagAlbum.photos.size)
        assertEquals("photo1", tagAlbum.photos[0])
    }

    @Test
    fun `TagAlbum 빈 사진 리스트 테스트`() {
        // Given & When
        val tagAlbum =
            TagAlbum(
                tagName = "빈태그",
                photos = emptyList(),
            )

        // Then
        assertEquals("빈태그", tagAlbum.tagName)
        assertTrue(tagAlbum.photos.isEmpty())
    }

    @Test
    fun `TagAlbum copy 테스트`() {
        // Given
        val original =
            TagAlbum(
                tagName = "가족",
                photos = listOf("photo1", "photo2"),
            )

        // When
        val copied = original.copy(tagName = "친구")

        // Then
        assertEquals("친구", copied.tagName)
        assertEquals(original.photos, copied.photos)
    }

    @Test
    fun `TagAlbum equals 테스트`() {
        // Given
        val album1 = TagAlbum("여행", listOf("photo1", "photo2"))
        val album2 = TagAlbum("여행", listOf("photo1", "photo2"))
        val album3 = TagAlbum("가족", listOf("photo1", "photo2"))

        // Then
        assertEquals(album1, album2)
        assertNotEquals(album1, album3)
    }

    @Test
    fun `TagAlbum hashCode 테스트`() {
        // Given
        val album1 = TagAlbum("여행", listOf("photo1"))
        val album2 = TagAlbum("여행", listOf("photo1"))

        // Then
        assertEquals(album1.hashCode(), album2.hashCode())
    }

    @Test
    fun `TagAlbum 많은 사진이 있는 경우 테스트`() {
        // Given
        val manyPhotos = (1..1000).map { "photo$it" }

        // When
        val tagAlbum =
            TagAlbum(
                tagName = "대용량앨범",
                photos = manyPhotos,
            )

        // Then
        assertEquals(1000, tagAlbum.photos.size)
        assertEquals("photo500", tagAlbum.photos[499])
    }
}
