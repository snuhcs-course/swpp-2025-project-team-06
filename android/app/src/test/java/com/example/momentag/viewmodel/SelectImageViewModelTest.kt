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
            val state = viewModel.recommendState.value
            assertTrue(state is SelectImageViewModel.RecommendState.Error)
            assertEquals(SelectImageViewModel.SelectImageError.NetworkError, (state as SelectImageViewModel.RecommendState.Error).error)
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
            val state = viewModel.recommendState.value
            assertTrue(state is SelectImageViewModel.RecommendState.Error)
            assertEquals(SelectImageViewModel.SelectImageError.UnknownError, (state as SelectImageViewModel.RecommendState.Error).error)
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
            val state = viewModel.recommendState.value
            assertTrue(state is SelectImageViewModel.RecommendState.Error)
            assertEquals(SelectImageViewModel.SelectImageError.UnknownError, (state as SelectImageViewModel.RecommendState.Error).error)
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
            val state = newViewModel.addPhotosState.value
            assertTrue(state is SelectImageViewModel.AddPhotosState.Error)
            assertEquals(SelectImageViewModel.SelectImageError.UnknownError, (state as SelectImageViewModel.AddPhotosState.Error).error)
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
}
