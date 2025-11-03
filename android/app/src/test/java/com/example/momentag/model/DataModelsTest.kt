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

        // When
        val tagItem =
            TagItem(
                tagName = tagName,
                coverImageId = coverImageId,
                tagId = tagId,
            )

        // Then
        assertEquals(tagName, tagItem.tagName)
        assertEquals(coverImageId, tagItem.coverImageId)
        assertEquals(tagId, tagItem.tagId)
    }

    @Test
    fun `TagItem with null coverImageId should be handled correctly`() {
        // Given & When
        val tagItem =
            TagItem(
                tagName = "Test",
                coverImageId = null,
                tagId = "tag001",
            )

        // Then
        assertNull(tagItem.coverImageId)
        assertEquals("Test", tagItem.tagName)
        assertEquals("tag001", tagItem.tagId)
    }

    // ========== TagCreateRequest Tests ==========

    @Test
    fun `TagCreateRequest should be created with correct name`() {
        // Given
        val tagName = "NewTag"

        // When
        val request = TagCreateRequest(name = tagName)

        // Then
        assertEquals(tagName, request.name)
    }

    // ========== PhotoDetailResponse Tests ==========

    @Test
    fun `PhotoDetailResponse should be created with correct properties`() {
        // Given
        val photoPathId = 12345L
        val tags =
            listOf(
                Tag("Nature", "tag1"),
                Tag("Sunset", "tag2"),
            )

        // When
        val response =
            PhotoDetailResponse(
                photoPathId = photoPathId,
                tags = tags,
            )

        // Then
        assertEquals(photoPathId, response.photoPathId)
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
                tags = emptyList(),
            )

        // Then
        assertTrue(response.tags.isEmpty())
        assertEquals(999L, response.photoPathId)
    }

    // ========== PhotoResponse Tests ==========

    @Test
    fun `PhotoResponse should be created with correct properties`() {
        // Given
        val photoId = "photo123"
        val photoPathId = 456L

        // When
        val response =
            PhotoResponse(
                photoId = photoId,
                photoPathId = photoPathId,
            )

        // Then
        assertEquals(photoId, response.photoId)
        assertEquals(photoPathId, response.photoPathId)
    }

    // ========== Photo Tests ==========

    @Test
    fun `Photo should be created with Uri`() {
        // Given
        val photoId = "photo789"

        // When
        val photo =
            Photo(
                photoId = photoId,
                contentUri = mockUri,
            )

        // Then
        assertEquals(photoId, photo.photoId)
        assertEquals(mockUri, photo.contentUri)
        assertEquals("content://test/image.jpg", photo.contentUri.toString())
    }

    @Test
    fun `Photo equality test with same Uri`() {
        // Given
        val photo1 = Photo(photoId = "photo1", contentUri = mockUri)
        val photo2 = Photo(photoId = "photo1", contentUri = mockUri)

        // Then
        assertEquals(photo1, photo2)
    }

    // ========== Photos Tests ==========

    @Test
    fun `Photos should contain list of Photo objects`() {
        // Given
        val photoList =
            listOf(
                Photo("photo1", mockUri),
                Photo("photo2", mockUri),
                Photo("photo3", mockUri),
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

    // ========== TagIdRequest Tests ==========

    @Test
    fun `TagIdRequest should be created with tagId`() {
        // Given
        val tagId = "tag999"

        // When
        val request = TagIdRequest(tagId = tagId)

        // Then
        assertEquals(tagId, request.tagId)
    }

    // ========== TagCreateResponse Tests ==========

    @Test
    fun `TagCreateResponse should return tagId`() {
        // Given
        val tagId = "newTag123"

        // When
        val response = TagCreateResponse(tagId = tagId)

        // Then
        assertEquals(tagId, response.tagId)
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
    fun `StoryResponse should contain list of recommendations`() {
        // Given
        val recs =
            listOf(
                PhotoResponse("photo1", 1L),
                PhotoResponse("photo2", 2L),
                PhotoResponse("photo3", 3L),
            )

        // When
        val response = StoryResponse(recs = recs)

        // Then
        assertEquals(3, response.recs.size)
        assertEquals("photo1", response.recs[0].photoId)
        assertEquals(2L, response.recs[1].photoPathId)
    }

    @Test
    fun `StoryResponse with empty recommendations`() {
        // Given & When
        val response = StoryResponse(recs = emptyList())

        // Then
        assertTrue(response.recs.isEmpty())
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
        val images = listOf("img1.jpg", "img2.jpg")
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
                images = listOf("img1.jpg"),
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
        val story1 =
            StoryModel(
                id = "story1",
                photoId = "photo1",
                images = listOf("img1.jpg"),
                date = "2025-11-02",
                location = "Seoul",
                suggestedTags = listOf("Nature"),
            )
        val story2 =
            StoryModel(
                id = "story1",
                photoId = "photo1",
                images = listOf("img1.jpg"),
                date = "2025-11-02",
                location = "Seoul",
                suggestedTags = listOf("Nature"),
            )
        val story3 =
            StoryModel(
                id = "story2",
                photoId = "photo2",
                images = listOf("img2.jpg"),
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
                tags = tags,
            )

        // When & Then
        assertEquals(2, photoResponse.tags.size)
        assertEquals("Tag1", photoResponse.tags[0].tagName)
        assertEquals("id2", photoResponse.tags[1].tagId)
    }
}
