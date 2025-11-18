package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectImageViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SelectImageViewModel
    private lateinit var photoSelectionRepository: PhotoSelectionRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var recommendRepository: RecommendRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        photoSelectionRepository = mockk()
        localRepository = mockk()
        remoteRepository = mockk()
        imageBrowserRepository = mockk(relaxed = true)
        recommendRepository = mockk()

        every { photoSelectionRepository.tagName } returns MutableStateFlow("")
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyList())
        every { photoSelectionRepository.existingTagId } returns MutableStateFlow(null)

        viewModel =
            SelectImageViewModel(
                photoSelectionRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
                recommendRepository,
            )
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Uri::class)
    }

    private fun createMockUri(path: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns path
        every { uri.lastPathSegment } returns path.substringAfterLast("/")
        return uri
    }

    private fun createPhotoResponse(id: String = "photo1") =
        PhotoResponse(
            photoId = id,
            photoPathId = 1L,
            createdAt = "2025-01-01",
        )

    private fun createPhoto(id: String = "photo1"): Photo {
        val uri = createMockUri("content://media/external/images/media/$id")
        every { Uri.parse("content://media/external/images/media/$id") } returns uri
        return Photo(
            photoId = id,
            contentUri = uri,
            createdAt = "2025-01-01",
        )
    }

    // Get all photos tests
    @Test
    fun `getAllPhotos success loads photos`() =
        runTest {
            // Given
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.getAllPhotos()
            advanceUntilIdle()

            // Then
            assertEquals(photos, viewModel.allPhotos.value)
            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun `getAllPhotos error clears photos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns
                RemoteRepository.Result.Error(500, "Error")

            // When
            viewModel.getAllPhotos()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.allPhotos.value.isEmpty())
            assertFalse(viewModel.isLoading.value)
        }

    // Recommend photo tests
    @Test
    fun `recommendPhoto success updates recommendState`() =
        runTest {
            // Given
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("photo1")) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            val newViewModel =
                SelectImageViewModel(
                    photoSelectionRepository,
                    localRepository,
                    remoteRepository,
                    imageBrowserRepository,
                    recommendRepository,
                )

            // When
            newViewModel.recommendPhoto()
            advanceUntilIdle()

            // Then
            val state = newViewModel.recommendState.value
            assertTrue(state is SelectImageViewModel.RecommendState.Success)
            assertEquals(photos, (state as SelectImageViewModel.RecommendState.Success).photos)
        }

    // Photo selection tests
    @Test
    fun `togglePhoto delegates to photoSelectionRepository`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.togglePhoto(photo) } returns Unit

        // When
        viewModel.togglePhoto(photo)

        // Then
        verify { photoSelectionRepository.togglePhoto(photo) }
    }

    @Test
    fun `addPhoto delegates to photoSelectionRepository`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.addPhoto(photo) } returns Unit

        // When
        viewModel.addPhoto(photo)

        // Then
        verify { photoSelectionRepository.addPhoto(photo) }
    }

    @Test
    fun `removePhoto delegates to photoSelectionRepository`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.removePhoto(photo) } returns Unit

        // When
        viewModel.removePhoto(photo)

        // Then
        verify { photoSelectionRepository.removePhoto(photo) }
    }

    // Selection mode tests
    @Test
    fun `setSelectionMode updates isSelectionMode`() {
        // When
        viewModel.setSelectionMode(true)

        // Then
        assertTrue(viewModel.isSelectionMode.value)

        // When
        viewModel.setSelectionMode(false)

        // Then
        assertFalse(viewModel.isSelectionMode.value)
    }

    // Browsing session test
    @Test
    fun `setGalleryBrowsingSession updates imageBrowserRepository`() {
        // When
        viewModel.setGalleryBrowsingSession()

        // Then
        verify { imageBrowserRepository.setGallery(any()) }
    }

    @Test
    fun `loadMorePhotos success appends new photos`() =
        runTest {
            // Given
            val initialPhotoResponses = (1..100).map { createPhotoResponse("photo$it") }
            val initialPhotos = (1..100).map { createPhoto("photo$it") }
            val newPhotoResponses = listOf(createPhotoResponse("photo101"))
            val newPhotos = listOf(createPhoto("photo101"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(initialPhotoResponses)
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            viewModel.getAllPhotos()
            advanceUntilIdle()

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 100) } returns
                RemoteRepository.Result.Success(newPhotoResponses)
            coEvery { localRepository.toPhotos(newPhotoResponses) } returns newPhotos

            // When
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Then
            assertEquals(initialPhotos + newPhotos, viewModel.allPhotos.value)
            assertFalse(viewModel.isLoadingMore.value)
        }

    @Test
    fun `loadMorePhotos does not load when already loading`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns RemoteRepository.Result.Success(emptyList())
            viewModel.loadMorePhotos() // Start loading

            // When
            viewModel.loadMorePhotos() // Try to load again
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { remoteRepository.getAllPhotos(any(), any()) }
        }

    @Test
    fun `loadMorePhotos does not load when no more pages`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns RemoteRepository.Result.Success(emptyList())
            viewModel.getAllPhotos()
            advanceUntilIdle()

            // When
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { remoteRepository.getAllPhotos(any(), any()) }
        }

    @Test
    fun `loadMorePhotos handles error from repository`() =
        runTest {
            // Given
            viewModel.getAllPhotos()
            advanceUntilIdle()
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns RemoteRepository.Result.Error(500, "Error")

            // When
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.isLoadingMore.value)
        }

    @Test
    fun `getAllPhotos with selected photos prepends them`() =
        runTest {
            // Given
            val selectedPhotos = listOf(createPhoto("selected"))
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(selectedPhotos)
            val photoResponses = listOf(createPhotoResponse("photo1"))
            val photos = listOf(createPhoto("photo1"))
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            val newViewModel =
                SelectImageViewModel(
                    photoSelectionRepository,
                    localRepository,
                    remoteRepository,
                    imageBrowserRepository,
                    recommendRepository,
                )

            // When
            newViewModel.getAllPhotos()
            advanceUntilIdle()

            // Then
            assertEquals(selectedPhotos + photos, newViewModel.allPhotos.value)
        }

    @Test
    fun `recommendPhoto handles Unauthorized error`() =
        runTest {
            // Given
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(listOf(createPhoto()))
            coEvery { recommendRepository.recommendPhotosFromPhotos(any()) } returns
                RecommendRepository.RecommendResult.Unauthorized("Unauthorized")

            // When
            viewModel.recommendPhoto()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Error)
        }

    @Test
    fun `recommendPhoto handles NetworkError`() =
        runTest {
            // Given
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(listOf(createPhoto()))
            coEvery { recommendRepository.recommendPhotosFromPhotos(any()) } returns
                RecommendRepository.RecommendResult.NetworkError("Network Error")

            // When
            viewModel.recommendPhoto()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.NetworkError)
        }

    @Test
    fun `recommendPhoto handles BadRequest`() =
        runTest {
            // Given
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(listOf(createPhoto()))
            coEvery { recommendRepository.recommendPhotosFromPhotos(any()) } returns
                RecommendRepository.RecommendResult.BadRequest("Bad Request")

            // When
            viewModel.recommendPhoto()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Error)
        }

    @Test
    fun `recommendPhoto handles generic error`() =
        runTest {
            // Given
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(listOf(createPhoto()))
            coEvery { recommendRepository.recommendPhotosFromPhotos(any()) } returns
                RecommendRepository.RecommendResult.Error("Generic error")

            // When
            viewModel.recommendPhoto()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Error)
        }

    @Test
    fun `addPhotoFromRecommendation adds photo and updates lists`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.addPhoto(photo) } returns Unit
        viewModel.addPhotoFromRecommendation(photo)

        // Then
        verify { photoSelectionRepository.addPhoto(photo) }
        assertFalse(viewModel.recommendedPhotos.value.contains(photo))
    }

    @Test
    fun `handlePhotoClick in selection mode toggles photo`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.togglePhoto(photo) } returns Unit

        // When
        viewModel.handlePhotoClick(photo, true) {}

        // Then
        verify { photoSelectionRepository.togglePhoto(photo) }
    }

    @Test
    fun `handlePhotoClick not in selection mode navigates`() {
        // Given
        val photo = createPhoto()
        val onNavigate: (Photo) -> Unit = mockk(relaxed = true)

        // When
        viewModel.handlePhotoClick(photo, false, onNavigate)

        // Then
        verify { onNavigate(photo) }
    }

    @Test
    fun `handleLongClick enters selection mode`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.togglePhoto(photo) } returns Unit
        viewModel.setSelectionMode(false)

        // When
        viewModel.handleLongClick(photo)

        // Then
        assertTrue(viewModel.isSelectionMode.value)
        verify { photoSelectionRepository.togglePhoto(photo) }
    }

    @Test
    fun `clearDraft calls repository`() {
        // Given
        every { photoSelectionRepository.clear() } returns Unit

        // When
        viewModel.clearDraft()

        // Then
        verify { photoSelectionRepository.clear() }
    }

    @Test
    fun `isPhotoSelected returns correct status`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(listOf(photo1))
        val newViewModel =
            SelectImageViewModel(photoSelectionRepository, localRepository, remoteRepository, imageBrowserRepository, recommendRepository)

        // Then
        assertTrue(newViewModel.isPhotoSelected(photo1))
        assertFalse(newViewModel.isPhotoSelected(photo2))
    }

    @Test
    fun `handleDoneButtonClick calls addPhotosToExistingTag`() =
        runTest {
            // Given
            every { photoSelectionRepository.existingTagId } returns MutableStateFlow("tag1")
            every { photoSelectionRepository.tagName } returns MutableStateFlow("Tag 1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(listOf(createPhoto()))
            coEvery { remoteRepository.postTagsToPhoto(any(), any()) } returns RemoteRepository.Result.Success(Unit)
            every { photoSelectionRepository.clear() } returns Unit
            val newViewModel =
                SelectImageViewModel(
                    photoSelectionRepository,
                    localRepository,
                    remoteRepository,
                    imageBrowserRepository,
                    recommendRepository,
                )

            // When
            newViewModel.handleDoneButtonClick()
            advanceUntilIdle()

            // Then
            coVerify { remoteRepository.postTagsToPhoto(any(), "tag1") }
        }

    @Test
    fun `addPhotosToExistingTag success case`() =
        runTest {
            // Given
            val photos = listOf(createPhoto("photo1"), createPhoto("photo2"))
            every { photoSelectionRepository.existingTagId } returns MutableStateFlow("tag1")
            every { photoSelectionRepository.tagName } returns MutableStateFlow("Tag 1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTagsToPhoto(any(), any()) } returns RemoteRepository.Result.Success(Unit)
            every { photoSelectionRepository.clear() } returns Unit
            val newViewModel =
                SelectImageViewModel(
                    photoSelectionRepository,
                    localRepository,
                    remoteRepository,
                    imageBrowserRepository,
                    recommendRepository,
                )

            // When
            newViewModel.handleDoneButtonClick()
            advanceUntilIdle()

            // Then
            coVerify(exactly = 2) { remoteRepository.postTagsToPhoto(any(), "tag1") }
            verify { photoSelectionRepository.clear() }
            assertTrue(newViewModel.addPhotosState.value is SelectImageViewModel.AddPhotosState.Success)
        }

    @Test
    fun `addPhotosToExistingTag handles error on one photo`() =
        runTest {
            // Given
            val photos = listOf(createPhoto("photo1"), createPhoto("photo2"))
            every { photoSelectionRepository.existingTagId } returns MutableStateFlow("tag1")
            every { photoSelectionRepository.tagName } returns MutableStateFlow("Tag 1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.postTagsToPhoto("photo2", "tag1") } returns RemoteRepository.Result.Error(500, "Error")
            val newViewModel =
                SelectImageViewModel(
                    photoSelectionRepository,
                    localRepository,
                    remoteRepository,
                    imageBrowserRepository,
                    recommendRepository,
                )

            // When
            newViewModel.handleDoneButtonClick()
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { remoteRepository.postTagsToPhoto("photo1", "tag1") }
            coVerify(exactly = 1) { remoteRepository.postTagsToPhoto("photo2", "tag1") }
            assertTrue(newViewModel.addPhotosState.value is SelectImageViewModel.AddPhotosState.Error)
        }

    // Adding to existing tag tests
    @Test
    fun `isAddingToExistingTag returns true when existingTagId is set`() {
        // Given
        every { photoSelectionRepository.existingTagId } returns MutableStateFlow("tag1")
        val newViewModel =
            SelectImageViewModel(
                photoSelectionRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
                recommendRepository,
            )

        // When
        val result = newViewModel.isAddingToExistingTag()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isAddingToExistingTag returns false when existingTagId is null`() {
        // Given
        every { photoSelectionRepository.existingTagId } returns MutableStateFlow(null)
        val newViewModel =
            SelectImageViewModel(
                photoSelectionRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
                recommendRepository,
            )

        // When
        val result = newViewModel.isAddingToExistingTag()

        // Then
        assertFalse(result)
    }


//    // ========== RecommendState Tests ==========
//
//    private fun createSamplePhoto(
//        id: String,
//        uriString: String,
//    ): Photo {
//        val mockUri = mockk<Uri>()
//        every { mockUri.toString() } returns uriString
//        return Photo(photoId = id, contentUri = mockUri, createdAt = "2024-01-01T00:00:00Z")
//    }
//
//    @Test
//    fun `RecommendState Idle 상태 테스트`() {
//        // When
//        val state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.Idle
//
//        // Then
//        assertTrue(state is SelectImageViewModel.RecommendState.Idle)
//        assertFalse(state is SelectImageViewModel.RecommendState.Loading)
//        assertFalse(state is SelectImageViewModel.RecommendState.Success)
//        assertFalse(state is SelectImageViewModel.RecommendState.Error)
//    }
//
//    @Test
//    fun `RecommendState Loading 상태 테스트`() {
//        // When
//        val state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.Loading
//
//        // Then
//        assertTrue(state is SelectImageViewModel.RecommendState.Loading)
//        assertFalse(state is SelectImageViewModel.RecommendState.Idle)
//        assertFalse(state is SelectImageViewModel.RecommendState.Success)
//    }
//
//    @Test
//    fun `RecommendState Success 상태 테스트`() {
//        // Given
//        val photos =
//            listOf(
//                createSamplePhoto("1", "content://media/external/images/1"),
//                createSamplePhoto("2", "content://media/external/images/2"),
//            )
//
//        // When
//        val state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.Success(photos)
//
//        // Then
//        assertTrue(state is SelectImageViewModel.RecommendState.Success)
//        val successState = state as SelectImageViewModel.RecommendState.Success
//        assertEquals(2, successState.photos.size)
//        assertEquals("1", successState.photos[0].photoId)
//    }
//
//    @Test
//    fun `RecommendState Success 빈 사진 리스트 테스트`() {
//        // When
//        val state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.Success(emptyList())
//
//        // Then
//        assertTrue(state is SelectImageViewModel.RecommendState.Success)
//        assertTrue((state as SelectImageViewModel.RecommendState.Success).photos.isEmpty())
//    }
//
//    @Test
//    fun `RecommendState Error 상태 테스트`() {
//        // Given
//        val errorMessage = "사진을 불러오는데 실패했습니다"
//
//        // When
//        val state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.Error(errorMessage)
//
//        // Then
//        assertTrue(state is SelectImageViewModel.RecommendState.Error)
//        assertEquals(errorMessage, (state as SelectImageViewModel.RecommendState.Error).message)
//    }
//
//    @Test
//    fun `RecommendState NetworkError 상태 테스트`() {
//        // Given
//        val networkErrorMessage = "네트워크 연결을 확인해주세요"
//
//        // When
//        val state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.NetworkError(networkErrorMessage)
//
//        // Then
//        assertTrue(state is SelectImageViewModel.RecommendState.NetworkError)
//        assertEquals(networkErrorMessage, (state as SelectImageViewModel.RecommendState.NetworkError).message)
//    }
//
//    @Test
//    fun `RecommendState 상태 전환 시나리오 테스트`() {
//        // Idle -> Loading
//        var state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.Idle
//        assertTrue(state is SelectImageViewModel.RecommendState.Idle)
//
//        // Loading
//        state = SelectImageViewModel.RecommendState.Loading
//        assertTrue(state is SelectImageViewModel.RecommendState.Loading)
//
//        // Loading -> Success
//        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
//        state = SelectImageViewModel.RecommendState.Success(photos)
//        assertTrue(state is SelectImageViewModel.RecommendState.Success)
//        assertEquals(1, (state as SelectImageViewModel.RecommendState.Success).photos.size)
//    }
//
//    @Test
//    fun `RecommendState 에러 처리 시나리오 테스트`() {
//        // Idle -> Loading
//        var state: SelectImageViewModel.RecommendState = SelectImageViewModel.RecommendState.Idle
//        state = SelectImageViewModel.RecommendState.Loading
//
//        // Loading -> Error
//        state = SelectImageViewModel.RecommendState.Error("일반 에러 발생")
//        assertTrue(state is SelectImageViewModel.RecommendState.Error)
//
//        // 다시 시도 -> NetworkError
//        state = SelectImageViewModel.RecommendState.NetworkError("네트워크 에러 발생")
//        assertTrue(state is SelectImageViewModel.RecommendState.NetworkError)
//    }
//
//    @Test
//    fun `RecommendState Success copy 테스트`() {
//        // Given
//        val photos1 = listOf(createSamplePhoto("1", "content://media/external/images/1"))
//        val original = SelectImageViewModel.RecommendState.Success(photos1)
//
//        // When
//        val photos2 =
//            listOf(
//                createSamplePhoto("1", "content://media/external/images/1"),
//                createSamplePhoto("2", "content://media/external/images/2"),
//            )
//        val copied = original.copy(photos = photos2)
//
//        // Then
//        assertEquals(2, copied.photos.size)
//        assertNotEquals(original.photos.size, copied.photos.size)
//    }
//
//    @Test
//    fun `RecommendState Error copy 테스트`() {
//        // Given
//        val original = SelectImageViewModel.RecommendState.Error("원래 에러")
//
//        // When
//        val copied = original.copy(message = "새로운 에러")
//
//        // Then
//        assertEquals("새로운 에러", copied.message)
//        assertNotEquals(original.message, copied.message)
//    }
//
//    @Test
//    fun `RecommendState NetworkError copy 테스트`() {
//        // Given
//        val original = SelectImageViewModel.RecommendState.NetworkError("원래 네트워크 에러")
//
//        // When
//        val copied = original.copy(message = "새로운 네트워크 에러")
//
//        // Then
//        assertEquals("새로운 네트워크 에러", copied.message)
//    }
//
//    @Test
//    fun `RecommendState Success equals 테스트`() {
//        // Given
//        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
//        val success1 = SelectImageViewModel.RecommendState.Success(photos)
//        val success2 = SelectImageViewModel.RecommendState.Success(photos)
//
//        // Then
//        assertEquals(success1, success2)
//    }
//
//    @Test
//    fun `RecommendState Error equals 테스트`() {
//        // Given
//        val error1 = SelectImageViewModel.RecommendState.Error("같은 에러")
//        val error2 = SelectImageViewModel.RecommendState.Error("같은 에러")
//        val error3 = SelectImageViewModel.RecommendState.Error("다른 에러")
//
//        // Then
//        assertEquals(error1, error2)
//        assertNotEquals(error1, error3)
//    }
//
//    @Test
//    fun `RecommendState 다양한 상태 when 표현식 테스트`() {
//        // Given
//        val states =
//            listOf<RecommendState>(
//                SelectImageViewModel.RecommendState.Idle,
//                SelectImageViewModel.RecommendState.Loading,
//                SelectImageViewModel.RecommendState.Success(emptyList()),
//                SelectImageViewModel.RecommendState.Error("에러"),
//                SelectImageViewModel.RecommendState.NetworkError("네트워크 에러"),
//            )
//
//        // When & Then
//        states.forEach { state ->
//            val result =
//                when (state) {
//                    is SelectImageViewModel.RecommendState.Idle -> "idle"
//                    is SelectImageViewModel.RecommendState.Loading -> "loading"
//                    is SelectImageViewModel.RecommendState.Success -> "success"
//                    is SelectImageViewModel.RecommendState.Error -> "error"
//                    is SelectImageViewModel.RecommendState.NetworkError -> "network_error"
//                }
//            assertNotNull(result)
//        }
//    }
//
//    @Test
//    fun `RecommendState 타입 체크 테스트`() {
//        // Given
//        val states: List<RecommendState> =
//            listOf(
//                SelectImageViewModel.RecommendState.Idle,
//                SelectImageViewModel.RecommendState.Loading,
//                SelectImageViewModel.RecommendState.Success(emptyList()),
//                SelectImageViewModel.RecommendState.Error("에러"),
//                SelectImageViewModel.RecommendState.NetworkError("네트워크 에러"),
//            )
//
//        // Then
//        assertEquals(5, states.size)
//        assertTrue(states[0] is SelectImageViewModel.RecommendState.Idle)
//        assertTrue(states[1] is SelectImageViewModel.RecommendState.Loading)
//        assertTrue(states[2] is SelectImageViewModel.RecommendState.Success)
//        assertTrue(states[3] is SelectImageViewModel.RecommendState.Error)
//        assertTrue(states[4] is SelectImageViewModel.RecommendState.NetworkError)
//    }
//
//    @Test
//    fun `RecommendState Success 대량 사진 데이터 테스트`() {
//        // Given
//        val manyPhotos =
//            (1..1000).map {
//                createSamplePhoto(it.toString(), "content://media/external/images/$it")
//            }
//
//        // When
//        val state = SelectImageViewModel.RecommendState.Success(manyPhotos)
//
//        // Then
//        assertTrue(state is SelectImageViewModel.RecommendState.Success)
//        assertEquals(1000, state.photos.size)
//        assertEquals("500", state.photos[499].photoId)
//    }
//
//    @Test
//    fun `RecommendState 빈 에러 메시지 테스트`() {
//        // When
//        val error = SelectImageViewModel.RecommendState.Error("")
//        val networkError = SelectImageViewModel.RecommendState.NetworkError("")
//
//        // Then
//        assertTrue(error.message.isEmpty())
//        assertTrue(networkError.message.isEmpty())
//    }
//
//    @Test
//    fun `RecommendState 긴 에러 메시지 테스트`() {
//        // Given
//        val longMessage = "에러".repeat(1000)
//
//        // When
//        val error = SelectImageViewModel.RecommendState.Error(longMessage)
//
//        // Then
//        assertEquals(2000, error.message.length) // "에러" = 2자 * 1000
//    }
}
