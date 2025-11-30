package com.example.momentag.repository

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.momentag.model.PhotoResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30]) // Ensure APIs like ContentResolver.query(Bundle) and ExifInterface(InputStream) are available
class LocalRepositoryTest {
    private lateinit var context: Context
    private lateinit var contentResolver: android.content.ContentResolver
    private lateinit var localRepository: LocalRepository
    private lateinit var gson: Gson

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        context = spy(app)
        contentResolver = spy(app.contentResolver)
        doReturn(contentResolver).`when`(context).contentResolver

        // Stub getString to avoid Resources$NotFoundException in Robolectric
        doReturn("Unknown date").`when`(context).getString(anyInt())

        gson = Gson()
        localRepository = LocalRepository(context, gson)
    }

    @Test
    @Config(sdk = [27]) // Force API < 28 to test BitmapFactory path and failure handling
    fun `resizeImage returns null when stream fails`() {
        // Given
        val contentUri = Uri.parse("content://media/external/images/media/123")
        doReturn(null).`when`(contentResolver).openInputStream(contentUri)

        // When
        val result = localRepository.resizeImage(contentUri, 100, 100)

        // Then
        assertEquals(null, result)
    }

    @Test
    @Config(sdk = [27]) // Force API < 28 to test BitmapFactory path
    fun `resizeImage resizes large image successfully (API less than 28)`() {
        // Given
        val contentUri = Uri.parse("content://media/external/images/media/123")
        val originalWidth = 2000
        val originalHeight = 2000
        val maxWidth = 500
        val maxHeight = 500

        val imageBytes = createTestImageBytes(originalWidth, originalHeight)

        // Mock openInputStream to return a new stream each time (called twice: bounds & decode)
        org.mockito.Mockito
            .doAnswer {
                java.io.ByteArrayInputStream(imageBytes)
            }.`when`(contentResolver)
            .openInputStream(contentUri)

        // When
        val result = localRepository.resizeImage(contentUri, maxWidth, maxHeight)

        // Then
        org.junit.Assert.assertNotNull(result)
        val resultBitmap = android.graphics.BitmapFactory.decodeByteArray(result, 0, result!!.size)

        // Verify dimensions are within limits
        assertTrue(resultBitmap.width <= maxWidth)
        assertTrue(resultBitmap.height <= maxHeight)
        // Verify it's not too small (should be close to target)
        assertTrue(resultBitmap.width > maxWidth / 2)
        assertTrue(resultBitmap.height > maxHeight / 2)
    }

    private fun createTestImageBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, stream)
        return stream.toByteArray()
    }

    // ========== Search History Tests ==========

    @Test
    fun `getSearchHistory returns empty list when no history exists`() {
        // When
        val history = localRepository.getSearchHistory()

        // Then
        assertTrue(history.isEmpty())
    }

    @Test
    fun `addSearchHistory adds item to top`() {
        // When
        localRepository.addSearchHistory("query1")
        localRepository.addSearchHistory("query2")

        // Then
        val history = localRepository.getSearchHistory()
        assertEquals(2, history.size)
        assertEquals("query2", history[0])
        assertEquals("query1", history[1])
    }

    @Test
    fun `addSearchHistory limits size to 10`() {
        // Given
        for (i in 1..15) {
            localRepository.addSearchHistory("query$i")
        }

        // When
        val history = localRepository.getSearchHistory()

        // Then
        assertEquals(10, history.size)
        assertEquals("query15", history[0]) // Most recent
        assertEquals("query6", history[9]) // Oldest kept
    }

    @Test
    fun `addSearchHistory moves existing item to top`() {
        // Given
        localRepository.addSearchHistory("query1")
        localRepository.addSearchHistory("query2")

        // When
        localRepository.addSearchHistory("query1")

        // Then
        val history = localRepository.getSearchHistory()
        assertEquals(2, history.size)
        assertEquals("query1", history[0])
        assertEquals("query2", history[1])
    }

    @Test
    fun `removeSearchHistory removes item`() {
        // Given
        localRepository.addSearchHistory("query1")
        localRepository.addSearchHistory("query2")

        // When
        localRepository.removeSearchHistory("query1")

        // Then
        val history = localRepository.getSearchHistory()
        assertEquals(1, history.size)
        assertEquals("query2", history[0])
    }

    @Test
    fun `clearSearchHistory clears all items`() {
        // Given
        localRepository.addSearchHistory("query1")
        localRepository.addSearchHistory("query2")

        // When
        localRepository.clearSearchHistory()

        // Then
        val history = localRepository.getSearchHistory()
        assertTrue(history.isEmpty())
    }

    // ========== Photo Conversion Tests ==========

    @Test
    fun `toPhotos converts valid PhotoResponse`() {
        // Given
        val photoPathId = 12345L
        val contentUri =
            android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photoPathId,
            )

        // Mock the ContentResolver query result
        val cursor = MatrixCursor(arrayOf(MediaStore.Images.Media._ID))
        cursor.addRow(arrayOf(photoPathId))

        doReturn(cursor).`when`(contentResolver).query(
            eq(contentUri),
            any(),
            any(),
            any(),
            any(),
        )

        val photoResponse =
            PhotoResponse(
                photoId = "backend-uuid-123",
                photoPathId = photoPathId,
                createdAt = "2024-01-01T00:00:00Z",
            )

        // When
        val photos = localRepository.toPhotos(listOf(photoResponse))

        // Then
        assertEquals("Should return 1 photo", 1, photos.size)
        assertEquals("backend-uuid-123", photos[0].photoId)
        assertEquals(contentUri, photos[0].contentUri)
        assertEquals("2024-01-01T00:00:00Z", photos[0].createdAt)
    }

    @Test
    fun `toPhotos skips invalid PhotoResponse with non-existent ID`() {
        // Given
        val photoResponse =
            PhotoResponse(
                photoId = "backend-uuid-456",
                photoPathId = 99999L, // Non-existent ID
                createdAt = "2024-01-01T00:00:00Z",
            )

        // When
        val photos = localRepository.toPhotos(listOf(photoResponse))

        // Then
        assertTrue(photos.isEmpty())
    }

    @Test
    fun `toPhotos skips PhotoResponse with invalid ID (0 or negative)`() {
        // Given
        val photoResponse =
            PhotoResponse(
                photoId = "backend-uuid-789",
                photoPathId = 0L,
                createdAt = "2024-01-01T00:00:00Z",
            )

        // When
        val photos = localRepository.toPhotos(listOf(photoResponse))

        // Then
        assertTrue(photos.isEmpty())
    }

    // ========== Album & Image Query Tests ==========

    @Test
    fun `getAlbums returns list of albums from MediaStore`() {
        // Given
        val bucketId = 123L
        val bucketName = "Test Album"
        val imageId = 456L

        val cursor =
            MatrixCursor(
                arrayOf(
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media._ID,
                ),
            )
        cursor.addRow(arrayOf(bucketId, bucketName, imageId))

        doReturn(cursor).`when`(contentResolver).query(
            eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            any(),
            any(),
            any(),
            any(),
        )

        // When
        val albums = localRepository.getAlbums()

        // Then
        assertEquals(1, albums.size)
        assertEquals(bucketId, albums[0].albumId)
        assertEquals(bucketName, albums[0].albumName)
    }

    @Test
    fun `getImagesForAlbum returns photos for specific album`() {
        // Given
        val albumId = 123L
        val imageId = 456L
        val dateTaken = 1704067200000L // 2024-01-01
        val dateAdded = 1704067200L

        val cursor =
            MatrixCursor(
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                ),
            )
        cursor.addRow(arrayOf(imageId, dateTaken, dateAdded))

        doReturn(cursor).`when`(contentResolver).query(
            eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            any(),
            any(),
            any(),
            any(),
        )

        // When
        val photos = localRepository.getImagesForAlbum(albumId)

        // Then
        assertEquals(1, photos.size)
        val expectedUri =
            android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageId,
            )
        assertEquals(expectedUri, photos[0].contentUri)
    }

    @Test
    fun `getPhotoDate returns formatted date`() {
        // Given
        val photoPathId = 789L
        val dateTaken = 1704067200000L // 2024-01-01

        val contentUri =
            android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photoPathId,
            )

        val cursor =
            MatrixCursor(
                arrayOf(
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                ),
            )
        cursor.addRow(arrayOf(dateTaken, 0L))

        doReturn(cursor).`when`(contentResolver).query(
            eq(contentUri),
            any(),
            any(),
            any(),
            any(),
        )

        // When
        val dateString = localRepository.getPhotoDate(photoPathId)

        // Then
        assertEquals("2024.01.01", dateString)
    }

    @Test
    fun `getPhotoDate returns unknown date when data missing`() {
        // Given
        val photoPathId = 999L
        val contentUri =
            android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photoPathId,
            )

        // Empty cursor
        val cursor =
            MatrixCursor(
                arrayOf(
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                ),
            )

        doReturn(cursor).`when`(contentResolver).query(
            eq(contentUri),
            any(),
            any(),
            any(),
            any(),
        )

        // When
        val dateString = localRepository.getPhotoDate(photoPathId)

        // Then
        // Note: We need to check what the actual string resource is.
        // Assuming "Unknown date" based on code reading, but better to check context.getString
        val expected = context.getString(com.example.momentag.R.string.localrepo_unknown_date)
        assertEquals(expected, dateString)
    }

    @Test
    fun `getImagesForAlbumPaginated returns photos with limit and offset`() {
        // Given
        val albumId = 123L
        val limit = 10
        val offset = 0
        val imageId = 456L
        val dateTaken = 1704067200000L // 2024-01-01
        val dateAdded = 1704067200L

        val cursor =
            MatrixCursor(
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                ),
            )
        cursor.addRow(arrayOf(imageId, dateTaken, dateAdded))

        doReturn(cursor).`when`(contentResolver).query(
            eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            any(),
            any(), // Bundle
            any(), // CancellationSignal
        )

        // When
        val photos = localRepository.getImagesForAlbumPaginated(albumId, limit, offset)

        // Then
        assertEquals(1, photos.size)
        val expectedUri =
            android.content.ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageId,
            )
        assertEquals(expectedUri, photos[0].contentUri)
    }

    @Test
    fun `getPhotoLocation returns unknown location when stream fails`() {
        // Given
        val photoPathId = 999L
        // We don't set up a cursor or stream, so openInputStream will likely return null or fail

        // When
        val location = localRepository.getPhotoLocation(photoPathId)

        // Then
        assertEquals("Unknown date", location)
    }
}
