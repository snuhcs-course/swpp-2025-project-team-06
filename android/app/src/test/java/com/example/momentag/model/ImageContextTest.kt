package com.example.momentag.model

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageContextTest {
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

        assertEquals(4, values.size)
        assertTrue(values.contains(ImageContext.ContextType.ALBUM))
        assertTrue(values.contains(ImageContext.ContextType.TAG_ALBUM))
        assertTrue(values.contains(ImageContext.ContextType.SEARCH_RESULT))
        assertTrue(values.contains(ImageContext.ContextType.GALLERY))
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
}
