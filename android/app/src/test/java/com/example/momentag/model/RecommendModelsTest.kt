package com.example.momentag.model

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendModelsTest {
    private fun createSamplePhoto(
        id: String,
        uriString: String,
    ): Photo {
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns uriString
        return Photo(photoId = id, contentUri = mockUri, createdAt = "2024-01-01T00:00:00Z",)
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

    // ========== RecommendState Tests ==========

    @Test
    fun `RecommendState Idle 상태 테스트`() {
        // When
        val state: RecommendState = RecommendState.Idle

        // Then
        assertTrue(state is RecommendState.Idle)
        assertFalse(state is RecommendState.Loading)
        assertFalse(state is RecommendState.Success)
        assertFalse(state is RecommendState.Error)
    }

    @Test
    fun `RecommendState Loading 상태 테스트`() {
        // When
        val state: RecommendState = RecommendState.Loading

        // Then
        assertTrue(state is RecommendState.Loading)
        assertFalse(state is RecommendState.Idle)
        assertFalse(state is RecommendState.Success)
    }

    @Test
    fun `RecommendState Success 상태 테스트`() {
        // Given
        val photos =
            listOf(
                createSamplePhoto("1", "content://media/external/images/1"),
                createSamplePhoto("2", "content://media/external/images/2"),
            )

        // When
        val state: RecommendState = RecommendState.Success(photos)

        // Then
        assertTrue(state is RecommendState.Success)
        val successState = state as RecommendState.Success
        assertEquals(2, successState.photos.size)
        assertEquals("1", successState.photos[0].photoId)
    }

    @Test
    fun `RecommendState Success 빈 사진 리스트 테스트`() {
        // When
        val state: RecommendState = RecommendState.Success(emptyList())

        // Then
        assertTrue(state is RecommendState.Success)
        assertTrue((state as RecommendState.Success).photos.isEmpty())
    }

    @Test
    fun `RecommendState Error 상태 테스트`() {
        // Given
        val errorMessage = "사진을 불러오는데 실패했습니다"

        // When
        val state: RecommendState = RecommendState.Error(errorMessage)

        // Then
        assertTrue(state is RecommendState.Error)
        assertEquals(errorMessage, (state as RecommendState.Error).message)
    }

    @Test
    fun `RecommendState NetworkError 상태 테스트`() {
        // Given
        val networkErrorMessage = "네트워크 연결을 확인해주세요"

        // When
        val state: RecommendState = RecommendState.NetworkError(networkErrorMessage)

        // Then
        assertTrue(state is RecommendState.NetworkError)
        assertEquals(networkErrorMessage, (state as RecommendState.NetworkError).message)
    }

    @Test
    fun `RecommendState 상태 전환 시나리오 테스트`() {
        // Idle -> Loading
        var state: RecommendState = RecommendState.Idle
        assertTrue(state is RecommendState.Idle)

        // Loading
        state = RecommendState.Loading
        assertTrue(state is RecommendState.Loading)

        // Loading -> Success
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        state = RecommendState.Success(photos)
        assertTrue(state is RecommendState.Success)
        assertEquals(1, (state as RecommendState.Success).photos.size)
    }

    @Test
    fun `RecommendState 에러 처리 시나리오 테스트`() {
        // Idle -> Loading
        var state: RecommendState = RecommendState.Idle
        state = RecommendState.Loading

        // Loading -> Error
        state = RecommendState.Error("일반 에러 발생")
        assertTrue(state is RecommendState.Error)

        // 다시 시도 -> NetworkError
        state = RecommendState.NetworkError("네트워크 에러 발생")
        assertTrue(state is RecommendState.NetworkError)
    }

    @Test
    fun `RecommendState Success copy 테스트`() {
        // Given
        val photos1 = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        val original = RecommendState.Success(photos1)

        // When
        val photos2 =
            listOf(
                createSamplePhoto("1", "content://media/external/images/1"),
                createSamplePhoto("2", "content://media/external/images/2"),
            )
        val copied = original.copy(photos = photos2)

        // Then
        assertEquals(2, copied.photos.size)
        assertNotEquals(original.photos.size, copied.photos.size)
    }

    @Test
    fun `RecommendState Error copy 테스트`() {
        // Given
        val original = RecommendState.Error("원래 에러")

        // When
        val copied = original.copy(message = "새로운 에러")

        // Then
        assertEquals("새로운 에러", copied.message)
        assertNotEquals(original.message, copied.message)
    }

    @Test
    fun `RecommendState NetworkError copy 테스트`() {
        // Given
        val original = RecommendState.NetworkError("원래 네트워크 에러")

        // When
        val copied = original.copy(message = "새로운 네트워크 에러")

        // Then
        assertEquals("새로운 네트워크 에러", copied.message)
    }

    @Test
    fun `RecommendState Success equals 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        val success1 = RecommendState.Success(photos)
        val success2 = RecommendState.Success(photos)

        // Then
        assertEquals(success1, success2)
    }

    @Test
    fun `RecommendState Error equals 테스트`() {
        // Given
        val error1 = RecommendState.Error("같은 에러")
        val error2 = RecommendState.Error("같은 에러")
        val error3 = RecommendState.Error("다른 에러")

        // Then
        assertEquals(error1, error2)
        assertNotEquals(error1, error3)
    }

    @Test
    fun `RecommendState 다양한 상태 when 표현식 테스트`() {
        // Given
        val states =
            listOf<RecommendState>(
                RecommendState.Idle,
                RecommendState.Loading,
                RecommendState.Success(emptyList()),
                RecommendState.Error("에러"),
                RecommendState.NetworkError("네트워크 에러"),
            )

        // When & Then
        states.forEach { state ->
            val result =
                when (state) {
                    is RecommendState.Idle -> "idle"
                    is RecommendState.Loading -> "loading"
                    is RecommendState.Success -> "success"
                    is RecommendState.Error -> "error"
                    is RecommendState.NetworkError -> "network_error"
                }
            assertNotNull(result)
        }
    }

    @Test
    fun `RecommendState 타입 체크 테스트`() {
        // Given
        val states: List<RecommendState> =
            listOf(
                RecommendState.Idle,
                RecommendState.Loading,
                RecommendState.Success(emptyList()),
                RecommendState.Error("에러"),
                RecommendState.NetworkError("네트워크 에러"),
            )

        // Then
        assertEquals(5, states.size)
        assertTrue(states[0] is RecommendState.Idle)
        assertTrue(states[1] is RecommendState.Loading)
        assertTrue(states[2] is RecommendState.Success)
        assertTrue(states[3] is RecommendState.Error)
        assertTrue(states[4] is RecommendState.NetworkError)
    }

    @Test
    fun `RecommendState Success 대량 사진 데이터 테스트`() {
        // Given
        val manyPhotos =
            (1..1000).map {
                createSamplePhoto(it.toString(), "content://media/external/images/$it")
            }

        // When
        val state = RecommendState.Success(manyPhotos)

        // Then
        assertTrue(state is RecommendState.Success)
        assertEquals(1000, state.photos.size)
        assertEquals("500", state.photos[499].photoId)
    }

    @Test
    fun `RecommendState 빈 에러 메시지 테스트`() {
        // When
        val error = RecommendState.Error("")
        val networkError = RecommendState.NetworkError("")

        // Then
        assertTrue(error.message.isEmpty())
        assertTrue(networkError.message.isEmpty())
    }

    @Test
    fun `RecommendState 긴 에러 메시지 테스트`() {
        // Given
        val longMessage = "에러".repeat(1000)

        // When
        val error = RecommendState.Error(longMessage)

        // Then
        assertEquals(2000, error.message.length) // "에러" = 2자 * 1000
    }
}
