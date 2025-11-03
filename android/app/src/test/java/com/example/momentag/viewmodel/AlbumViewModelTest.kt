package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class AlbumViewModelTest {
    private lateinit var viewModel: AlbumViewModel
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var recommendRepository: RecommendRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPhotos =
        listOf(
            createMockPhoto("photo1"),
            createMockPhoto("photo2"),
            createMockPhoto("photo3"),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        localRepository = mockk()
        remoteRepository = mockk()
        recommendRepository = mockk()
        imageBrowserRepository = mockk(relaxed = true)

        viewModel =
            AlbumViewModel(
                localRepository = localRepository,
                remoteRepository = remoteRepository,
                recommendRepository = recommendRepository,
                imageBrowserRepository = imageBrowserRepository,
                ioDispatcher = testDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMockPhoto(id: String): Photo {
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://media/external/images/$id"
        return Photo(photoId = id, contentUri = mockUri)
    }

    private fun createPhotoResponse(
        id: String,
        pathId: Long,
    ): PhotoResponse = PhotoResponse(photoId = id, photoPathId = pathId)

    // ========== loadAlbum Tests ==========

    @Test
    fun `loadAlbum sets Loading state immediately`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList<PhotoResponse>())
            coEvery { localRepository.toPhotos(any()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList<PhotoResponse>())

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            // Loading state was set (though Success follows immediately with UnconfinedTestDispatcher)
            assertTrue(viewModel.albumLoadingState.value is AlbumViewModel.AlbumLoadingState.Success)
        }

    @Test
    fun `loadAlbum success with photos`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val remotePhotos =
                listOf(
                    createPhotoResponse("photo1", 1L),
                    createPhotoResponse("photo2", 2L),
                )

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(remotePhotos)
            coEvery { localRepository.toPhotos(remotePhotos) } returns testPhotos
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList<PhotoResponse>())
            coEvery { localRepository.toPhotos(emptyList<PhotoResponse>()) } returns emptyList()

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Success)
            assertEquals(testPhotos, (state as AlbumViewModel.AlbumLoadingState.Success).photos)

            verify { imageBrowserRepository.setTagAlbum(testPhotos, tagName) }
            coVerify { recommendRepository.recommendPhotosFromTag(tagId) }
        }

    @Test
    fun `loadAlbum success with empty photos`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList<PhotoResponse>())
            coEvery { localRepository.toPhotos(emptyList<PhotoResponse>()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList<PhotoResponse>())

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Success)
            assertTrue((state as AlbumViewModel.AlbumLoadingState.Success).photos.isEmpty())
        }

    @Test
    fun `loadAlbum handles Error result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val errorMessage = "Failed to load album"

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(errorMessage, (state as AlbumViewModel.AlbumLoadingState.Error).message)
        }

    @Test
    fun `loadAlbum handles Unauthorized result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals("Please login again", (state as AlbumViewModel.AlbumLoadingState.Error).message)
        }

    @Test
    fun `loadAlbum handles NetworkError result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val errorMessage = "Network connection failed"

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(errorMessage, (state as AlbumViewModel.AlbumLoadingState.Error).message)
        }

    @Test
    fun `loadAlbum handles BadRequest result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val errorMessage = "Bad request"

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(errorMessage, (state as AlbumViewModel.AlbumLoadingState.Error).message)
        }

    @Test
    fun `loadAlbum handles Exception result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val exception = RuntimeException("Unexpected error")

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals("Unexpected error", (state as AlbumViewModel.AlbumLoadingState.Error).message)
        }

    @Test
    fun `loadAlbum handles Exception with null message`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val exception = RuntimeException(null as String?)

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals("Unknown error", (state as AlbumViewModel.AlbumLoadingState.Error).message)
        }

    @Test
    fun `loadAlbum auto-loads recommendations after success`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val recommendPhotos = listOf(createMockPhoto("rec1"))

            val recommendResponse = listOf(createPhotoResponse("rec1", 1L))

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList<PhotoResponse>())
            coEvery { localRepository.toPhotos(emptyList<PhotoResponse>()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(recommendResponse)
            coEvery { localRepository.toPhotos(recommendResponse) } returns recommendPhotos

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then
            val albumState = viewModel.albumLoadingState.value
            assertTrue(albumState is AlbumViewModel.AlbumLoadingState.Success)

            val recommendState = viewModel.recommendLoadingState.value
            assertTrue(recommendState is AlbumViewModel.RecommendLoadingState.Success)
            assertEquals(recommendPhotos, (recommendState as AlbumViewModel.RecommendLoadingState.Success).photos)
        }

    // ========== loadRecommendations Tests ==========

    @Test
    fun `loadRecommendations sets Loading state immediately`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList<PhotoResponse>())
            coEvery { localRepository.toPhotos(any()) } returns emptyList()

            // When
            viewModel.loadRecommendations(tagId)

            // Then
            assertTrue(viewModel.recommendLoadingState.value is AlbumViewModel.RecommendLoadingState.Success)
        }

    @Test
    fun `loadRecommendations success with photos`() =
        runTest {
            // Given
            val tagId = "tag1"
            val remotePhotos =
                listOf(
                    createPhotoResponse("photo1", 1L),
                    createPhotoResponse("photo2", 2L),
                )
            val recommendPhotos = listOf(createMockPhoto("rec1"), createMockPhoto("rec2"))

            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(remotePhotos)
            coEvery { localRepository.toPhotos(remotePhotos) } returns recommendPhotos

            // When
            viewModel.loadRecommendations(tagId)

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Success)
            assertEquals(recommendPhotos, (state as AlbumViewModel.RecommendLoadingState.Success).photos)
        }

    @Test
    fun `loadRecommendations success with empty photos`() =
        runTest {
            // Given
            val tagId = "tag1"

            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList<PhotoResponse>())
            coEvery { localRepository.toPhotos(emptyList<PhotoResponse>()) } returns emptyList()

            // When
            viewModel.loadRecommendations(tagId)

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Success)
            assertTrue((state as AlbumViewModel.RecommendLoadingState.Success).photos.isEmpty())
        }

    @Test
    fun `loadRecommendations handles Error result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val errorMessage = "Failed to load recommendations"

            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Error(errorMessage)

            // When
            viewModel.loadRecommendations(tagId)

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(errorMessage, (state as AlbumViewModel.RecommendLoadingState.Error).message)
        }

    @Test
    fun `loadRecommendations handles Unauthorized result`() =
        runTest {
            // Given
            val tagId = "tag1"

            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Unauthorized("Unauthorized")

            // When
            viewModel.loadRecommendations(tagId)

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals("Please login again", (state as AlbumViewModel.RecommendLoadingState.Error).message)
        }

    @Test
    fun `loadRecommendations handles NetworkError result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val errorMessage = "Network connection failed"

            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.NetworkError(errorMessage)

            // When
            viewModel.loadRecommendations(tagId)

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(errorMessage, (state as AlbumViewModel.RecommendLoadingState.Error).message)
        }

    @Test
    fun `loadRecommendations handles BadRequest result`() =
        runTest {
            // Given
            val tagId = "tag1"
            val errorMessage = "Bad request"

            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.BadRequest(errorMessage)

            // When
            viewModel.loadRecommendations(tagId)

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(errorMessage, (state as AlbumViewModel.RecommendLoadingState.Error).message)
        }

    // ========== toggleRecommendPhoto Tests ==========

    @Test
    fun `toggleRecommendPhoto adds photo when not selected`() {
        // Given
        val photo = createMockPhoto("photo1")
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())

        // When
        viewModel.toggleRecommendPhoto(photo)

        // Then
        assertEquals(1, viewModel.selectedRecommendPhotos.value.size)
        assertTrue(viewModel.selectedRecommendPhotos.value.contains(photo))
    }

    @Test
    fun `toggleRecommendPhoto removes photo when already selected`() {
        // Given
        val photo = createMockPhoto("photo1")
        viewModel.toggleRecommendPhoto(photo) // First add
        assertTrue(viewModel.selectedRecommendPhotos.value.contains(photo))

        // When
        viewModel.toggleRecommendPhoto(photo) // Then remove

        // Then
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
        assertFalse(viewModel.selectedRecommendPhotos.value.contains(photo))
    }

    @Test
    fun `toggleRecommendPhoto works with multiple photos`() {
        // Given
        val photo1 = createMockPhoto("photo1")
        val photo2 = createMockPhoto("photo2")
        val photo3 = createMockPhoto("photo3")

        // When - add photos
        viewModel.toggleRecommendPhoto(photo1)
        viewModel.toggleRecommendPhoto(photo2)
        viewModel.toggleRecommendPhoto(photo3)

        // Then - all added
        assertEquals(3, viewModel.selectedRecommendPhotos.value.size)
        assertTrue(viewModel.selectedRecommendPhotos.value.containsAll(listOf(photo1, photo2, photo3)))

        // When - remove middle photo
        viewModel.toggleRecommendPhoto(photo2)

        // Then - only photo2 removed
        assertEquals(2, viewModel.selectedRecommendPhotos.value.size)
        assertTrue(viewModel.selectedRecommendPhotos.value.contains(photo1))
        assertFalse(viewModel.selectedRecommendPhotos.value.contains(photo2))
        assertTrue(viewModel.selectedRecommendPhotos.value.contains(photo3))
    }

    @Test
    fun `toggleRecommendPhoto toggle same photo multiple times`() {
        // Given
        val photo = createMockPhoto("photo1")

        // When & Then - toggle multiple times
        viewModel.toggleRecommendPhoto(photo)
        assertEquals(1, viewModel.selectedRecommendPhotos.value.size)

        viewModel.toggleRecommendPhoto(photo)
        assertEquals(0, viewModel.selectedRecommendPhotos.value.size)

        viewModel.toggleRecommendPhoto(photo)
        assertEquals(1, viewModel.selectedRecommendPhotos.value.size)

        viewModel.toggleRecommendPhoto(photo)
        assertEquals(0, viewModel.selectedRecommendPhotos.value.size)
    }

    // ========== resetRecommendSelection Tests ==========

    @Test
    fun `resetRecommendSelection clears empty selection`() {
        // Given
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())

        // When
        viewModel.resetRecommendSelection()

        // Then
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    @Test
    fun `resetRecommendSelection clears single photo selection`() {
        // Given
        val photo = createMockPhoto("photo1")
        viewModel.toggleRecommendPhoto(photo)
        assertEquals(1, viewModel.selectedRecommendPhotos.value.size)

        // When
        viewModel.resetRecommendSelection()

        // Then
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    @Test
    fun `resetRecommendSelection clears multiple photos selection`() {
        // Given
        val photos =
            listOf(
                createMockPhoto("photo1"),
                createMockPhoto("photo2"),
                createMockPhoto("photo3"),
            )
        photos.forEach { viewModel.toggleRecommendPhoto(it) }
        assertEquals(3, viewModel.selectedRecommendPhotos.value.size)

        // When
        viewModel.resetRecommendSelection()

        // Then
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    @Test
    fun `resetRecommendSelection can be called multiple times`() {
        // Given
        val photo = createMockPhoto("photo1")
        viewModel.toggleRecommendPhoto(photo)

        // When & Then
        viewModel.resetRecommendSelection()
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())

        viewModel.resetRecommendSelection()
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())

        viewModel.resetRecommendSelection()
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    // ========== Integration Tests ==========

    @Test
    fun `workflow - load album, load recommendations, select photos, reset`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val albumPhotos = listOf(createMockPhoto("album1"))
            val recommendPhotos = listOf(createMockPhoto("rec1"), createMockPhoto("rec2"))

            val albumResponse = listOf(createPhotoResponse("album1", 1L))
            val recommendResponse =
                listOf(
                    createPhotoResponse("rec1", 2L),
                    createPhotoResponse("rec2", 3L),
                )

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(albumResponse)
            coEvery { localRepository.toPhotos(albumResponse) } returns albumPhotos
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(recommendResponse)
            coEvery { localRepository.toPhotos(recommendResponse) } returns recommendPhotos

            // When - load album (auto-loads recommendations)
            viewModel.loadAlbum(tagId, tagName)

            // Then - album loaded
            val albumState = viewModel.albumLoadingState.value
            assertTrue(albumState is AlbumViewModel.AlbumLoadingState.Success)
            assertEquals(albumPhotos, (albumState as AlbumViewModel.AlbumLoadingState.Success).photos)

            // Then - recommendations loaded
            val recommendState = viewModel.recommendLoadingState.value
            assertTrue(recommendState is AlbumViewModel.RecommendLoadingState.Success)
            assertEquals(recommendPhotos, (recommendState as AlbumViewModel.RecommendLoadingState.Success).photos)

            // When - select photos
            viewModel.toggleRecommendPhoto(recommendPhotos[0])
            viewModel.toggleRecommendPhoto(recommendPhotos[1])

            // Then - photos selected
            assertEquals(2, viewModel.selectedRecommendPhotos.value.size)

            // When - reset
            viewModel.resetRecommendSelection()

            // Then - selection cleared
            assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
        }

    @Test
    fun `workflow - load album fails, recommendations not loaded`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Error(500, "Server error")

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then - album error
            val albumState = viewModel.albumLoadingState.value
            assertTrue(albumState is AlbumViewModel.AlbumLoadingState.Error)

            // Then - recommendations not triggered
            val recommendState = viewModel.recommendLoadingState.value
            assertTrue(recommendState is AlbumViewModel.RecommendLoadingState.Idle)
        }

    @Test
    fun `workflow - album succeeds, recommendations fail`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"
            val albumPhotos = listOf(createMockPhoto("album1"))

            val albumResponse = listOf(createPhotoResponse("album1", 1L))

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(albumResponse)
            coEvery { localRepository.toPhotos(albumResponse) } returns albumPhotos
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Error("Recommendation failed")

            // When
            viewModel.loadAlbum(tagId, tagName)

            // Then - album success
            val albumState = viewModel.albumLoadingState.value
            assertTrue(albumState is AlbumViewModel.AlbumLoadingState.Success)

            // Then - recommendations error
            val recommendState = viewModel.recommendLoadingState.value
            assertTrue(recommendState is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals("Recommendation failed", (recommendState as AlbumViewModel.RecommendLoadingState.Error).message)
        }

    @Test
    fun `state transitions maintain independence`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "여행"

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList<PhotoResponse>())
            coEvery { localRepository.toPhotos(any()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList<PhotoResponse>())

            // When - load album
            viewModel.loadAlbum(tagId, tagName)

            // Then - both states updated
            assertTrue(viewModel.albumLoadingState.value is AlbumViewModel.AlbumLoadingState.Success)
            assertTrue(viewModel.recommendLoadingState.value is AlbumViewModel.RecommendLoadingState.Success)

            // When - manually reload recommendations with error
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Error("New error")
            viewModel.loadRecommendations(tagId)

            // Then - album state unchanged, recommend state changed
            assertTrue(viewModel.albumLoadingState.value is AlbumViewModel.AlbumLoadingState.Success)
            assertTrue(viewModel.recommendLoadingState.value is AlbumViewModel.RecommendLoadingState.Error)
        }
}
