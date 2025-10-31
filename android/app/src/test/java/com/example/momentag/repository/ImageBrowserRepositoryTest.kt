package com.example.momentag.repository

import android.net.Uri
import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Android 9.0 for stable behavior
class ImageBrowserRepositoryTest {
    private lateinit var repository: ImageBrowserRepository

    private val testPhotos =
        listOf(
            Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
            Photo(photoId = "photo2", contentUri = Uri.parse("content://media/2")),
            Photo(photoId = "photo3", contentUri = Uri.parse("content://media/3")),
        )

    @Before
    fun setUp() {
        repository = ImageBrowserRepository()
    }

    // ========== hasSession Tests ==========

    @Test
    fun `hasSession returns false when no session is set`() {
        // When
        val hasSession = repository.hasSession()

        // Then
        assertFalse(hasSession)
    }

    @Test
    fun `hasSession returns true after setting search results`() {
        // Given
        repository.setSearchResults(testPhotos, "nature")

        // When
        val hasSession = repository.hasSession()

        // Then
        assertTrue(hasSession)
    }

    @Test
    fun `hasSession returns false after clearing session`() {
        // Given
        repository.setSearchResults(testPhotos, "nature")

        // When
        repository.clear()

        // Then
        assertFalse(repository.hasSession())
    }

    // ========== setSearchResults Tests ==========

    @Test
    fun `setSearchResults stores search session correctly`() {
        // Given
        val query = "sunset"

        // When
        repository.setSearchResults(testPhotos, query)

        // Then
        val context = repository.getPhotoContext("photo1")
        assertNotNull(context)
        assertEquals(ImageContext.ContextType.SEARCH_RESULT, context!!.contextType)
        assertEquals(testPhotos, context.images)
        assertEquals(0, context.currentIndex)
    }

    @Test
    fun `setSearchResults with empty list stores empty session`() {
        // Given
        val emptyPhotos = emptyList<Photo>()

        // When
        repository.setSearchResults(emptyPhotos, "nonexistent")

        // Then
        assertTrue(repository.hasSession())
        assertNull(repository.getPhotoContext("photo1"))
    }

    @Test
    fun `setSearchResults replaces previous session`() {
        // Given
        repository.setTagAlbum(testPhotos, "OldTag")
        val newPhotos = listOf(Photo(photoId = "newPhoto", contentUri = Uri.parse("content://media/99")))

        // When
        repository.setSearchResults(newPhotos, "newQuery")

        // Then
        val context = repository.getPhotoContext("newPhoto")
        assertNotNull(context)
        assertEquals(ImageContext.ContextType.SEARCH_RESULT, context!!.contextType)
        assertNull(repository.getPhotoContext("photo1")) // Old session photos should not be accessible
    }

    // ========== setTagAlbum Tests ==========

    @Test
    fun `setTagAlbum stores tag album session correctly`() {
        // Given
        val tagName = "Vacation"

        // When
        repository.setTagAlbum(testPhotos, tagName)

        // Then
        val context = repository.getPhotoContext("photo2")
        assertNotNull(context)
        assertEquals(ImageContext.ContextType.TAG_ALBUM, context!!.contextType)
        assertEquals(testPhotos, context.images)
        assertEquals(1, context.currentIndex)
    }

    @Test
    fun `setTagAlbum with single photo stores correctly`() {
        // Given
        val singlePhoto = listOf(Photo(photoId = "single", contentUri = Uri.parse("content://media/1")))

        // When
        repository.setTagAlbum(singlePhoto, "SingleTag")

        // Then
        val context = repository.getPhotoContext("single")
        assertNotNull(context)
        assertEquals(ImageContext.ContextType.TAG_ALBUM, context!!.contextType)
        assertEquals(0, context.currentIndex)
        assertEquals(1, context.images.size)
    }

    // ========== setLocalAlbum Tests ==========

    @Test
    fun `setLocalAlbum stores local album session correctly`() {
        // Given
        val albumName = "Camera"

        // When
        repository.setLocalAlbum(testPhotos, albumName)

        // Then
        val context = repository.getPhotoContext("photo3")
        assertNotNull(context)
        assertEquals(ImageContext.ContextType.ALBUM, context!!.contextType)
        assertEquals(testPhotos, context.images)
        assertEquals(2, context.currentIndex)
    }

    @Test
    fun `setLocalAlbum replaces tag album session`() {
        // Given
        repository.setTagAlbum(testPhotos, "TagAlbum")

        // When
        repository.setLocalAlbum(testPhotos, "LocalAlbum")

        // Then
        val context = repository.getPhotoContext("photo1")
        assertNotNull(context)
        assertEquals(ImageContext.ContextType.ALBUM, context!!.contextType)
    }

    // ========== setGallery Tests ==========

    @Test
    fun `setGallery stores gallery session correctly`() {
        // When
        repository.setGallery(testPhotos)

        // Then
        val context = repository.getPhotoContext("photo1")
        assertNotNull(context)
        assertEquals(ImageContext.ContextType.GALLERY, context!!.contextType)
        assertEquals(testPhotos, context.images)
    }

    @Test
    fun `setGallery with large photo list stores correctly`() {
        // Given
        val largePhotoList =
            (1..100).map { i ->
                Photo(photoId = "photo$i", contentUri = Uri.parse("content://media/$i"))
            }

        // When
        repository.setGallery(largePhotoList)

        // Then
        val context = repository.getPhotoContext("photo50")
        assertNotNull(context)
        assertEquals(49, context!!.currentIndex)
        assertEquals(100, context.images.size)
    }

    // ========== getPhotoContext Tests ==========

    @Test
    fun `getPhotoContext returns null when no session exists`() {
        // When
        val context = repository.getPhotoContext("photo1")

        // Then
        assertNull(context)
    }

    @Test
    fun `getPhotoContext returns null for non-existent photoId`() {
        // Given
        repository.setGallery(testPhotos)

        // When
        val context = repository.getPhotoContext("nonExistentId")

        // Then
        assertNull(context)
    }

    @Test
    fun `getPhotoContext returns correct index for first photo`() {
        // Given
        repository.setGallery(testPhotos)

        // When
        val context = repository.getPhotoContext("photo1")

        // Then
        assertNotNull(context)
        assertEquals(0, context!!.currentIndex)
        assertEquals("photo1", context.images[context.currentIndex].photoId)
    }

    @Test
    fun `getPhotoContext returns correct index for middle photo`() {
        // Given
        repository.setGallery(testPhotos)

        // When
        val context = repository.getPhotoContext("photo2")

        // Then
        assertNotNull(context)
        assertEquals(1, context!!.currentIndex)
        assertEquals("photo2", context.images[context.currentIndex].photoId)
    }

    @Test
    fun `getPhotoContext returns correct index for last photo`() {
        // Given
        repository.setGallery(testPhotos)

        // When
        val context = repository.getPhotoContext("photo3")

        // Then
        assertNotNull(context)
        assertEquals(2, context!!.currentIndex)
        assertEquals("photo3", context.images[context.currentIndex].photoId)
    }

    @Test
    fun `getPhotoContext returns full photo list in context`() {
        // Given
        repository.setSearchResults(testPhotos, "test")

        // When
        val context = repository.getPhotoContext("photo2")

        // Then
        assertNotNull(context)
        assertEquals(3, context!!.images.size)
        assertEquals("photo1", context.images[0].photoId)
        assertEquals("photo2", context.images[1].photoId)
        assertEquals("photo3", context.images[2].photoId)
    }

    // ========== getPhotoContextByUri Tests ==========

    @Test
    fun `getPhotoContextByUri returns null when no session exists`() {
        // Given
        val uri = Uri.parse("content://media/1")

        // When
        val context = repository.getPhotoContextByUri(uri)

        // Then
        assertNull(context)
    }

    @Test
    fun `getPhotoContextByUri returns null for non-existent uri`() {
        // Given
        repository.setGallery(testPhotos)
        val uri = Uri.parse("content://media/999")

        // When
        val context = repository.getPhotoContextByUri(uri)

        // Then
        assertNull(context)
    }

    @Test
    fun `getPhotoContextByUri returns correct context for first photo`() {
        // Given
        repository.setGallery(testPhotos)
        val uri = Uri.parse("content://media/1")

        // When
        val context = repository.getPhotoContextByUri(uri)

        // Then
        assertNotNull(context)
        assertEquals(0, context!!.currentIndex)
        assertEquals(ImageContext.ContextType.GALLERY, context.contextType)
    }

    @Test
    fun `getPhotoContextByUri returns correct context for middle photo`() {
        // Given
        repository.setTagAlbum(testPhotos, "TestTag")
        val uri = Uri.parse("content://media/2")

        // When
        val context = repository.getPhotoContextByUri(uri)

        // Then
        assertNotNull(context)
        assertEquals(1, context!!.currentIndex)
        assertEquals(ImageContext.ContextType.TAG_ALBUM, context.contextType)
    }

    @Test
    fun `getPhotoContextByUri returns correct context for last photo`() {
        // Given
        repository.setLocalAlbum(testPhotos, "Album")
        val uri = Uri.parse("content://media/3")

        // When
        val context = repository.getPhotoContextByUri(uri)

        // Then
        assertNotNull(context)
        assertEquals(2, context!!.currentIndex)
        assertEquals(ImageContext.ContextType.ALBUM, context.contextType)
    }

    // ========== clear Tests ==========

    @Test
    fun `clear removes current session`() {
        // Given
        repository.setGallery(testPhotos)
        assertTrue(repository.hasSession())

        // When
        repository.clear()

        // Then
        assertFalse(repository.hasSession())
        assertNull(repository.getPhotoContext("photo1"))
    }

    @Test
    fun `clear can be called multiple times safely`() {
        // Given
        repository.setGallery(testPhotos)

        // When
        repository.clear()
        repository.clear()
        repository.clear()

        // Then
        assertFalse(repository.hasSession())
    }

    @Test
    fun `clear on empty repository does not throw`() {
        // When/Then - should not throw exception
        repository.clear()

        // Then
        assertFalse(repository.hasSession())
    }

    // ========== Integration Tests ==========

    @Test
    fun `session workflow - search to tag album to gallery`() {
        // Step 1: Set search results
        repository.setSearchResults(testPhotos, "sunset")
        var context = repository.getPhotoContext("photo1")
        assertEquals(ImageContext.ContextType.SEARCH_RESULT, context!!.contextType)

        // Step 2: Switch to tag album
        repository.setTagAlbum(testPhotos, "Nature")
        context = repository.getPhotoContext("photo1")
        assertEquals(ImageContext.ContextType.TAG_ALBUM, context!!.contextType)

        // Step 3: Switch to gallery
        repository.setGallery(testPhotos)
        context = repository.getPhotoContext("photo1")
        assertEquals(ImageContext.ContextType.GALLERY, context!!.contextType)

        // Step 4: Clear
        repository.clear()
        assertNull(repository.getPhotoContext("photo1"))
    }

    @Test
    fun `multiple context lookups return consistent results`() {
        // Given
        repository.setGallery(testPhotos)

        // When - multiple lookups
        val context1 = repository.getPhotoContext("photo2")
        val context2 = repository.getPhotoContext("photo2")
        val context3 = repository.getPhotoContextByUri(Uri.parse("content://media/2"))

        // Then - all should be consistent
        assertEquals(context1!!.currentIndex, context2!!.currentIndex)
        assertEquals(context1.currentIndex, context3!!.currentIndex)
        assertEquals(context1.contextType, context2.contextType)
        assertEquals(context1.contextType, context3.contextType)
    }

    @Test
    fun `different context types maintain separate metadata`() {
        // Test 1: Search result
        repository.setSearchResults(testPhotos, "query1")
        val searchContext = repository.getPhotoContext("photo1")
        assertEquals(ImageContext.ContextType.SEARCH_RESULT, searchContext!!.contextType)

        // Test 2: Tag album
        repository.setTagAlbum(testPhotos, "TagName")
        val tagContext = repository.getPhotoContext("photo1")
        assertEquals(ImageContext.ContextType.TAG_ALBUM, tagContext!!.contextType)

        // Test 3: Local album
        repository.setLocalAlbum(testPhotos, "AlbumName")
        val albumContext = repository.getPhotoContext("photo1")
        assertEquals(ImageContext.ContextType.ALBUM, albumContext!!.contextType)

        // Test 4: Gallery
        repository.setGallery(testPhotos)
        val galleryContext = repository.getPhotoContext("photo1")
        assertEquals(ImageContext.ContextType.GALLERY, galleryContext!!.contextType)
    }

    @Test
    fun `photo context preserves order of photos`() {
        // Given - photos in specific order
        val orderedPhotos =
            listOf(
                Photo(photoId = "z", contentUri = Uri.parse("content://z")),
                Photo(photoId = "a", contentUri = Uri.parse("content://a")),
                Photo(photoId = "m", contentUri = Uri.parse("content://m")),
            )

        // When
        repository.setGallery(orderedPhotos)

        // Then - order should be preserved
        val contextZ = repository.getPhotoContext("z")
        val contextA = repository.getPhotoContext("a")
        val contextM = repository.getPhotoContext("m")

        assertEquals(0, contextZ!!.currentIndex)
        assertEquals(1, contextA!!.currentIndex)
        assertEquals(2, contextM!!.currentIndex)

        assertEquals("z", contextZ.images[0].photoId)
        assertEquals("a", contextZ.images[1].photoId)
        assertEquals("m", contextZ.images[2].photoId)
    }
}
