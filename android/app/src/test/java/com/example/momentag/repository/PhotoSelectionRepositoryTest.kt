package com.example.momentag.repository

import com.example.momentag.model.Photo
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoSelectionRepositoryTest {
    private lateinit var repository: PhotoSelectionRepository

    @Before
    fun setUp() {
        repository = PhotoSelectionRepository()
    }

    // Helper function
    private fun createPhoto(id: String = "photo1"): Photo = Photo(photoId = id, contentUri = mockk(), createdAt = "2024-01-01T00:00:00Z")

    // ========== Initial State Tests ==========

    @Test
    fun `initial tagName is empty`() {
        // Then
        assertEquals("", repository.tagName.value)
    }

    @Test
    fun `initial selectedPhotos is empty`() {
        // Then
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    @Test
    fun `initial hasChanges returns false`() {
        // Then
        assertFalse(repository.hasChanges())
    }

    // ========== Initialize Tests ==========

    @Test
    fun `initialize sets tagName and photos`() =
        runTest {
            // Given
            val tagName = "TestTag"
            val photos = listOf(createPhoto("photo1"))

            // When
            repository.initialize(tagName, photos)

            // Then
            assertEquals(tagName, repository.tagName.value)
            assertEquals(photos.associateBy { it.photoId }, repository.selectedPhotos.value)
        }

    @Test
    fun `initialize with null tagName sets empty string`() =
        runTest {
            // Given
            val photos = listOf(createPhoto("photo1"))

            // When
            repository.initialize(null, photos)

            // Then
            assertEquals("", repository.tagName.value)
            assertEquals(photos.associateBy { it.photoId }, repository.selectedPhotos.value)
        }

    @Test
    fun `initialize with empty photos sets empty list`() =
        runTest {
            // Given
            val tagName = "TestTag"

            // When
            repository.initialize(tagName, emptyList())

            // Then
            assertEquals(tagName, repository.tagName.value)
            assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
        }

    @Test
    fun `initialize can override existing data`() =
        runTest {
            // Given - initial data
            repository.updateTagName("OldTag")
            repository.addPhoto(createPhoto("oldPhoto"))

            // When - initialize with new data
            val newTagName = "NewTag"
            val newPhotos = listOf(createPhoto("newPhoto"))
            repository.initialize(newTagName, newPhotos)

            // Then
            assertEquals(newTagName, repository.tagName.value)
            assertEquals(newPhotos.associateBy { it.photoId }, repository.selectedPhotos.value)
        }

    // ========== UpdateTagName Tests ==========

    @Test
    fun `updateTagName changes tagName value`() {
        // Given
        val tagName = "MyTag"

        // When
        repository.updateTagName(tagName)

        // Then
        assertEquals(tagName, repository.tagName.value)
    }

    @Test
    fun `updateTagName can be called multiple times`() {
        // When
        repository.updateTagName("Tag1")
        assertEquals("Tag1", repository.tagName.value)

        repository.updateTagName("Tag2")
        assertEquals("Tag2", repository.tagName.value)

        repository.updateTagName("Tag3")
        assertEquals("Tag3", repository.tagName.value)
    }

    @Test
    fun `updateTagName with empty string`() {
        // Given
        repository.updateTagName("SomeTag")

        // When
        repository.updateTagName("")

        // Then
        assertEquals("", repository.tagName.value)
    }

    // ========== AddPhoto Tests ==========

    @Test
    fun `addPhoto adds photo to empty list`() {
        // Given
        val photo = createPhoto("photo1")

        // When
        repository.addPhoto(photo)

        // Then
        assertEquals(mapOf(photo.photoId to photo), repository.selectedPhotos.value)
    }

    @Test
    fun `addPhoto adds multiple photos`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")

        // When
        repository.addPhoto(photo1)
        repository.addPhoto(photo2)

        // Then
        assertEquals(2, repository.selectedPhotos.value.size)
        assertTrue(repository.selectedPhotos.value.containsKey(photo1.photoId))
        assertTrue(repository.selectedPhotos.value.containsKey(photo2.photoId))
    }

    @Test
    fun `addPhoto does not add duplicate photo`() {
        // Given
        val photo = createPhoto("photo1")
        repository.addPhoto(photo)

        // When - try to add same photo again
        repository.addPhoto(photo)

        // Then - still only one photo
        assertEquals(1, repository.selectedPhotos.value.size)
        assertEquals(mapOf(photo.photoId to photo), repository.selectedPhotos.value)
    }

    @Test
    fun `addPhoto does not add photo with same photoId`() {
        // Given
        val photo1 = Photo(photoId = "photo1", contentUri = mockk(), createdAt = "2024-01-01T00:00:00Z")
        val photo2 = Photo(photoId = "photo1", contentUri = mockk(), createdAt = "2024-01-01T00:00:00Z") // Same ID, different instance
        repository.addPhoto(photo1)

        // When
        repository.addPhoto(photo2)

        // Then - still only one photo
        assertEquals(1, repository.selectedPhotos.value.size)
    }

    // ========== RemovePhoto Tests ==========

    @Test
    fun `removePhoto removes existing photo`() {
        // Given
        val photo = createPhoto("photo1")
        repository.addPhoto(photo)

        // When
        repository.removePhoto(photo)

        // Then
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    @Test
    fun `removePhoto removes only specified photo`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")
        val photo3 = createPhoto("photo3")
        repository.addPhoto(photo1)
        repository.addPhoto(photo2)
        repository.addPhoto(photo3)

        // When
        repository.removePhoto(photo2)

        // Then
        assertEquals(2, repository.selectedPhotos.value.size)
        assertTrue(repository.selectedPhotos.value.containsKey(photo1.photoId))
        assertFalse(repository.selectedPhotos.value.containsKey(photo2.photoId))
        assertTrue(repository.selectedPhotos.value.containsKey(photo3.photoId))
    }

    @Test
    fun `removePhoto on non-existing photo does nothing`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")
        repository.addPhoto(photo1)

        // When
        repository.removePhoto(photo2)

        // Then
        assertEquals(mapOf(photo1.photoId to photo1), repository.selectedPhotos.value)
    }

    @Test
    fun `removePhoto matches by photoId`() {
        // Given
        val photo1 = Photo(photoId = "photo1", contentUri = mockk(), createdAt = "2024-01-01T00:00:00Z")
        val photo2 = Photo(photoId = "photo1", contentUri = mockk(), createdAt = "2024-01-01T00:00:00Z") // Same ID, different instance
        repository.addPhoto(photo1)

        // When
        repository.removePhoto(photo2)

        // Then
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    // ========== TogglePhoto Tests ==========

    @Test
    fun `togglePhoto adds photo when not selected`() {
        // Given
        val photo = createPhoto("photo1")

        // When
        repository.togglePhoto(photo)

        // Then
        assertEquals(mapOf(photo.photoId to photo), repository.selectedPhotos.value)
    }

    @Test
    fun `togglePhoto removes photo when already selected`() {
        // Given
        val photo = createPhoto("photo1")
        repository.addPhoto(photo)

        // When
        repository.togglePhoto(photo)

        // Then
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    @Test
    fun `togglePhoto twice returns to original state`() {
        // Given
        val photo = createPhoto("photo1")

        // When - toggle twice
        repository.togglePhoto(photo)
        repository.togglePhoto(photo)

        // Then
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    @Test
    fun `togglePhoto works with multiple photos`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")

        // When
        repository.togglePhoto(photo1)
        repository.togglePhoto(photo2)
        repository.togglePhoto(photo1) // deselect photo1

        // Then
        assertEquals(1, repository.selectedPhotos.value.size)
        assertEquals(
            photo2,
            repository.selectedPhotos.value.values
                .first(),
        )
    }

    // ========== Clear Tests ==========

    @Test
    fun `clear resets tagName to empty`() {
        // Given
        repository.updateTagName("TestTag")

        // When
        repository.clear()

        // Then
        assertEquals("", repository.tagName.value)
    }

    @Test
    fun `clear resets selectedPhotos to empty`() {
        // Given
        repository.addPhoto(createPhoto("photo1"))
        repository.addPhoto(createPhoto("photo2"))

        // When
        repository.clear()

        // Then
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    @Test
    fun `clear resets both tagName and selectedPhotos`() {
        // Given
        repository.updateTagName("TestTag")
        repository.addPhoto(createPhoto("photo1"))

        // When
        repository.clear()

        // Then
        assertEquals("", repository.tagName.value)
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    @Test
    fun `clear can be called multiple times`() {
        // Given
        repository.updateTagName("TestTag")
        repository.addPhoto(createPhoto("photo1"))

        // When
        repository.clear()
        repository.clear()
        repository.clear()

        // Then
        assertEquals("", repository.tagName.value)
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    @Test
    fun `clear on empty repository does nothing`() {
        // When
        repository.clear()

        // Then
        assertEquals("", repository.tagName.value)
        assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
    }

    // ========== HasChanges Tests ==========

    @Test
    fun `hasChanges returns true when tagName is set`() {
        // Given
        repository.updateTagName("TestTag")

        // When
        val result = repository.hasChanges()

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasChanges returns true when photos are selected`() {
        // Given
        repository.addPhoto(createPhoto("photo1"))

        // When
        val result = repository.hasChanges()

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasChanges returns true when both tagName and photos are set`() {
        // Given
        repository.updateTagName("TestTag")
        repository.addPhoto(createPhoto("photo1"))

        // When
        val result = repository.hasChanges()

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasChanges returns false after clear`() {
        // Given
        repository.updateTagName("TestTag")
        repository.addPhoto(createPhoto("photo1"))

        // When
        repository.clear()
        val result = repository.hasChanges()

        // Then
        assertFalse(result)
    }

    @Test
    fun `hasChanges returns false when tagName is empty string`() {
        // Given
        repository.updateTagName("")

        // When
        val result = repository.hasChanges()

        // Then
        assertFalse(result)
    }

    // ========== Integration Tests ==========

    @Test
    fun `complete workflow - initialize, update, clear`() =
        runTest {
            // Given - initialize
            repository.initialize("InitialTag", listOf(createPhoto("photo1")))
            assertEquals("InitialTag", repository.tagName.value)
            assertEquals(1, repository.selectedPhotos.value.size)

            // When - update
            repository.updateTagName("UpdatedTag")
            repository.addPhoto(createPhoto("photo2"))
            assertEquals("UpdatedTag", repository.tagName.value)
            assertEquals(2, repository.selectedPhotos.value.size)

            // When - clear
            repository.clear()
            assertEquals("", repository.tagName.value)
            assertEquals(emptyMap<String, Photo>(), repository.selectedPhotos.value)
            assertFalse(repository.hasChanges())
        }

    @Test
    fun `workflow - add multiple photos and toggle`() {
        // When
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")
        val photo3 = createPhoto("photo3")

        repository.addPhoto(photo1)
        repository.addPhoto(photo2)
        repository.addPhoto(photo3)
        assertEquals(3, repository.selectedPhotos.value.size)

        repository.togglePhoto(photo2) // remove
        assertEquals(2, repository.selectedPhotos.value.size)

        repository.togglePhoto(photo2) // add back
        assertEquals(3, repository.selectedPhotos.value.size)

        repository.removePhoto(photo1)
        assertEquals(2, repository.selectedPhotos.value.size)
    }

    @Test
    fun `workflow - state flows are properly exposed`() {
        // Given
        val photo = createPhoto("photo1")

        // When
        repository.updateTagName("TestTag")
        repository.addPhoto(photo)

        // Then - flows reflect current state
        assertEquals("TestTag", repository.tagName.value)
        assertEquals(mapOf(photo.photoId to photo), repository.selectedPhotos.value)
        assertTrue(repository.hasChanges())
    }
}
