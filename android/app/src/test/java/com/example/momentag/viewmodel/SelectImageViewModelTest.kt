package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private lateinit var tagNameFlow: MutableStateFlow<String>
    private lateinit var selectedPhotosFlow: MutableStateFlow<Map<String, Photo>>
    private lateinit var existingTagIdFlow: MutableStateFlow<String?>

    @Before
    fun setUp() {
        photoSelectionRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        remoteRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)
        recommendRepository = mockk(relaxed = true)

        tagNameFlow = MutableStateFlow("")
        selectedPhotosFlow = MutableStateFlow(emptyMap())
        existingTagIdFlow = MutableStateFlow(null)

        every { photoSelectionRepository.tagName } returns tagNameFlow
        every { photoSelectionRepository.selectedPhotos } returns selectedPhotosFlow
        every { photoSelectionRepository.existingTagId } returns existingTagIdFlow
        every { photoSelectionRepository.togglePhoto(any()) } just Runs
        every { photoSelectionRepository.addPhoto(any()) } just Runs
        every { photoSelectionRepository.removePhoto(any()) } just Runs
        every { photoSelectionRepository.clear() } just Runs
        every { imageBrowserRepository.setGallery(any()) } just Runs

        viewModel =
            SelectImageViewModel(
                photoSelectionRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
                recommendRepository,
            )
    }

    private fun createMockPhoto(
        id: String,
        contentUri: Uri = mockk(relaxed = true),
    ): Photo =
        Photo(
            photoId = id,
            contentUri = contentUri,
            createdAt = "2023-01-01",
        )

    private fun createMockPhotoResponse(id: String): PhotoResponse =
        mockk<PhotoResponse>(relaxed = true) {
            every { photoId } returns id
        }

    // --- getAllPhotos Tests ---

    @Test
    fun `getAllPhotos success loads photos`() =
        runTest {
            val photoResponses = listOf(createMockPhotoResponse("1"), createMockPhotoResponse("2"))
            val photos = listOf(createMockPhoto("1"), createMockPhoto("2"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            assertEquals(photos, viewModel.allPhotos.value)
            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun `getAllPhotos places selected photos at front`() =
        runTest {
            val selectedPhoto = createMockPhoto("selected1")
            selectedPhotosFlow.value = mapOf("selected1" to selectedPhoto)

            val photoResponses =
                listOf(
                    createMockPhotoResponse("1"),
                    createMockPhotoResponse("selected1"),
                    createMockPhotoResponse("2"),
                )
            val photos = listOf(createMockPhoto("1"), createMockPhoto("selected1"), createMockPhoto("2"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            val allPhotos = viewModel.allPhotos.value
            // Selected photo should be first
            assertEquals("selected1", allPhotos[0].photoId)
            // Then unselected photos
            assertTrue(allPhotos.size == 3)
        }

    @Test
    fun `getAllPhotos error sets empty list`() =
        runTest {
            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Error(500, "Server error")

            viewModel.getAllPhotos()
            advanceUntilIdle()

            assertTrue(viewModel.allPhotos.value.isEmpty())
            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun `getAllPhotos exception sets empty list`() =
        runTest {
            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } throws Exception("Network error")

            viewModel.getAllPhotos()
            advanceUntilIdle()

            assertTrue(viewModel.allPhotos.value.isEmpty())
            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun `getAllPhotos can be called multiple times`() =
        runTest {
            val photoResponses = listOf(createMockPhotoResponse("1"))
            val photos = listOf(createMockPhoto("1"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            // Call again after completion
            viewModel.getAllPhotos()
            advanceUntilIdle()

            // Both calls should complete successfully
            coVerify(atLeast = 1) { remoteRepository.getAllPhotos(limit = 100, offset = 0) }
            assertEquals(photos, viewModel.allPhotos.value)
        }

    // --- loadMorePhotos Tests ---

    @Test
    fun `loadMorePhotos appends new photos`() =
        runTest {
            val initialPhotoResponses = List(100) { createMockPhotoResponse("$it") }
            val initialPhotos = List(100) { createMockPhoto("$it") }
            val newPhotoResponses = listOf(createMockPhotoResponse("100"), createMockPhotoResponse("101"))
            val newPhotos = listOf(createMockPhoto("100"), createMockPhoto("101"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(initialPhotoResponses)
            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 100) } returns
                RemoteRepository.Result.Success(newPhotoResponses)
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { localRepository.toPhotos(newPhotoResponses) } returns newPhotos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            assertEquals(100, viewModel.allPhotos.value.size)

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            assertEquals(102, viewModel.allPhotos.value.size)
            assertFalse(viewModel.isLoadingMore.value)
        }

    @Test
    fun `loadMorePhotos does nothing when already loading more`() =
        runTest {
            val photoResponses = List(100) { createMockPhotoResponse("$it") }
            val photos = List(100) { createMockPhoto("$it") }

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = any()) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            viewModel.loadMorePhotos()
            // Call again before first completes
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Should only be called once for loadMore (plus once for initial load)
            coVerify(exactly = 1) { remoteRepository.getAllPhotos(limit = 100, offset = 100) }
        }

    @Test
    fun `loadMorePhotos does nothing when no more pages`() =
        runTest {
            val photoResponses = listOf(createMockPhotoResponse("1"))
            val photos = listOf(createMockPhoto("1"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            // Since we got less than 100 photos, hasMorePages should be false
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Should not call getAllPhotos again
            coVerify(exactly = 1) { remoteRepository.getAllPhotos(any(), any()) }
        }

    @Test
    fun `loadMorePhotos does nothing when initial load is in progress`() =
        runTest {
            val photoResponses = listOf(createMockPhotoResponse("1"))
            val photos = listOf(createMockPhoto("1"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.getAllPhotos()
            // Don't wait - call loadMore while loading
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Should only call initial load
            coVerify(exactly = 1) { remoteRepository.getAllPhotos(any(), any()) }
        }

    @Test
    fun `loadMorePhotos filters out duplicates`() =
        runTest {
            val initialPhotoResponses = List(100) { createMockPhotoResponse("$it") }
            val initialPhotos = List(100) { createMockPhoto("$it") }
            val newPhotoResponses =
                listOf(
                    createMockPhotoResponse("99"), // Duplicate
                    createMockPhotoResponse("100"),
                )
            val newPhotos = listOf(createMockPhoto("99"), createMockPhoto("100"))

            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(initialPhotoResponses)
            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 100) } returns
                RemoteRepository.Result.Success(newPhotoResponses)
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { localRepository.toPhotos(newPhotoResponses) } returns newPhotos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Should only add photo "100", filtering out duplicate "99"
            assertEquals(101, viewModel.allPhotos.value.size)
            verify { imageBrowserRepository.setGallery(any()) }
        }

    // --- recommendPhoto Tests ---

    @Test
    fun `recommendPhoto success updates state`() =
        runTest {
            selectedPhotosFlow.value = mapOf("1" to createMockPhoto("1"))

            val photoResponses = listOf(createMockPhotoResponse("2"), createMockPhotoResponse("3"))
            val photos = listOf(createMockPhoto("2"), createMockPhoto("3"))

            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("1")) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.recommendPhoto()
            advanceUntilIdle()

            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Success)
            val successState = viewModel.recommendState.value as SelectImageViewModel.RecommendState.Success
            assertEquals(photos, successState.photos)
            assertEquals(photos, viewModel.recommendedPhotos.value)
        }

    @Test
    fun `recommendPhoto filters out already selected photos`() =
        runTest {
            selectedPhotosFlow.value =
                mapOf(
                    "1" to createMockPhoto("1"),
                    "2" to createMockPhoto("2"),
                )

            val photoResponses =
                listOf(
                    createMockPhotoResponse("2"), // Already selected
                    createMockPhotoResponse("3"),
                )
            val photos = listOf(createMockPhoto("2"), createMockPhoto("3"))

            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("1", "2")) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.recommendPhoto()
            advanceUntilIdle()

            // Should filter out photo "2" since it's already selected
            assertEquals(1, viewModel.recommendedPhotos.value.size)
            assertEquals("3", viewModel.recommendedPhotos.value[0].photoId)
        }

    @Test
    fun `recommendPhoto error sets error state`() =
        runTest {
            selectedPhotosFlow.value = mapOf("1" to createMockPhoto("1"))

            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("1")) } returns
                RecommendRepository.RecommendResult.Error("Server error")

            viewModel.recommendPhoto()
            advanceUntilIdle()

            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Error)
            val errorState = viewModel.recommendState.value as SelectImageViewModel.RecommendState.Error
            assertEquals(SelectImageViewModel.SelectImageError.UnknownError, errorState.error)
        }

    @Test
    fun `recommendPhoto unauthorized sets error state`() =
        runTest {
            selectedPhotosFlow.value = mapOf("1" to createMockPhoto("1"))

            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("1")) } returns
                RecommendRepository.RecommendResult.Unauthorized("Unauthorized")

            viewModel.recommendPhoto()
            advanceUntilIdle()

            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Error)
            val errorState = viewModel.recommendState.value as SelectImageViewModel.RecommendState.Error
            assertEquals(SelectImageViewModel.SelectImageError.Unauthorized, errorState.error)
        }

    @Test
    fun `recommendPhoto network error sets error state`() =
        runTest {
            selectedPhotosFlow.value = mapOf("1" to createMockPhoto("1"))

            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("1")) } returns
                RecommendRepository.RecommendResult.NetworkError("Network error")

            viewModel.recommendPhoto()
            advanceUntilIdle()

            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Error)
            val errorState = viewModel.recommendState.value as SelectImageViewModel.RecommendState.Error
            assertEquals(SelectImageViewModel.SelectImageError.NetworkError, errorState.error)
        }

    @Test
    fun `recommendPhoto bad request sets error state`() =
        runTest {
            selectedPhotosFlow.value = mapOf("1" to createMockPhoto("1"))

            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("1")) } returns
                RecommendRepository.RecommendResult.BadRequest("Bad request")

            viewModel.recommendPhoto()
            advanceUntilIdle()

            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Error)
            val errorState = viewModel.recommendState.value as SelectImageViewModel.RecommendState.Error
            assertEquals(SelectImageViewModel.SelectImageError.UnknownError, errorState.error)
        }

    // --- resetRecommendState Tests ---

    @Test
    fun `resetRecommendState resets to Idle`() =
        runTest {
            selectedPhotosFlow.value = mapOf("1" to createMockPhoto("1"))

            val photoResponses = listOf(createMockPhotoResponse("2"))
            val photos = listOf(createMockPhoto("2"))

            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("1")) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.recommendPhoto()
            advanceUntilIdle()

            assertTrue(viewModel.recommendState.value is SelectImageViewModel.RecommendState.Success)
            assertFalse(viewModel.recommendedPhotos.value.isEmpty())

            viewModel.resetRecommendState()

            assertEquals(SelectImageViewModel.RecommendState.Idle, viewModel.recommendState.value)
            assertTrue(viewModel.recommendedPhotos.value.isEmpty())
        }

    // --- addPhotoFromRecommendation Tests ---

    @Test
    fun `addPhotoFromRecommendation adds photo and removes from recommended`() =
        runTest {
            val photo = createMockPhoto("1")
            val recommendedPhotos = listOf(createMockPhoto("1"), createMockPhoto("2"))

            // Setup initial state
            selectedPhotosFlow.value = emptyMap()
            coEvery { recommendRepository.recommendPhotosFromPhotos(any()) } returns
                RecommendRepository.RecommendResult.Success(emptyList())
            coEvery { localRepository.toPhotos(any()) } returns recommendedPhotos

            viewModel.recommendPhoto()
            advanceUntilIdle()

            // Manually set recommended photos for testing
            val photoResponses = listOf(createMockPhotoResponse("1"), createMockPhotoResponse("2"))
            coEvery { localRepository.toPhotos(photoResponses) } returns recommendedPhotos

            viewModel.addPhotoFromRecommendation(photo)

            verify { photoSelectionRepository.addPhoto(photo) }
            // Photo should be removed from recommended list
            assertEquals(1, viewModel.recommendedPhotos.value.size)
            assertEquals("2", viewModel.recommendedPhotos.value[0].photoId)
        }

    @Test
    fun `addPhotoFromRecommendation moves photo to front of allPhotos`() =
        runTest {
            val photo = createMockPhoto("100")
            val initialPhotos = listOf(createMockPhoto("1"), createMockPhoto("2"), createMockPhoto("100"))

            val photoResponses = List(3) { createMockPhotoResponse("${it + 1}") }
            coEvery { remoteRepository.getAllPhotos(limit = 100, offset = 0) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns initialPhotos

            viewModel.getAllPhotos()
            advanceUntilIdle()

            viewModel.addPhotoFromRecommendation(photo)

            // Photo should be at the front
            assertEquals("100", viewModel.allPhotos.value[0].photoId)
            // List should still have same size
            assertEquals(3, viewModel.allPhotos.value.size)
        }

    // --- toggleRecommendPhoto Tests ---

    @Test
    fun `toggleRecommendPhoto adds photo when not selected`() {
        val photo = createMockPhoto("1")

        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())

        viewModel.toggleRecommendPhoto(photo)

        assertEquals(1, viewModel.selectedRecommendPhotos.value.size)
        assertTrue(viewModel.selectedRecommendPhotos.value.containsKey("1"))
    }

    @Test
    fun `toggleRecommendPhoto removes photo when already selected`() {
        val photo = createMockPhoto("1")

        viewModel.toggleRecommendPhoto(photo)
        assertTrue(viewModel.selectedRecommendPhotos.value.containsKey("1"))

        viewModel.toggleRecommendPhoto(photo)
        assertFalse(viewModel.selectedRecommendPhotos.value.containsKey("1"))
    }

    // --- resetRecommendSelection Tests ---

    @Test
    fun `resetRecommendSelection clears selection`() {
        val photo1 = createMockPhoto("1")
        val photo2 = createMockPhoto("2")

        viewModel.toggleRecommendPhoto(photo1)
        viewModel.toggleRecommendPhoto(photo2)
        assertEquals(2, viewModel.selectedRecommendPhotos.value.size)

        viewModel.resetRecommendSelection()

        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    // --- addRecommendedPhotosToSelection Tests ---

    @Test
    fun `addRecommendedPhotosToSelection adds all photos and resets selection`() {
        val photos = listOf(createMockPhoto("1"), createMockPhoto("2"), createMockPhoto("3"))

        viewModel.addRecommendedPhotosToSelection(photos)

        verify(exactly = 3) { photoSelectionRepository.addPhoto(any()) }
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    // --- handlePhotoClick Tests ---

    @Test
    fun `handlePhotoClick in selection mode toggles photo`() {
        val photo = createMockPhoto("1")
        val onNavigate = mockk<(Photo) -> Unit>(relaxed = true)

        viewModel.handlePhotoClick(photo, isSelectionMode = true, onNavigate)

        verify { photoSelectionRepository.togglePhoto(photo) }
        verify(exactly = 0) { onNavigate(any()) }
    }

    @Test
    fun `handlePhotoClick in browse mode navigates`() {
        val photo = createMockPhoto("1")
        val onNavigate = mockk<(Photo) -> Unit>(relaxed = true)

        viewModel.handlePhotoClick(photo, isSelectionMode = false, onNavigate)

        verify { imageBrowserRepository.setGallery(any()) }
        verify { onNavigate(photo) }
        verify(exactly = 0) { photoSelectionRepository.togglePhoto(any()) }
    }

    // --- handleLongClick Tests ---

    @Test
    fun `handleLongClick enters selection mode and adds photo`() {
        val photo = createMockPhoto("1")

        viewModel.setSelectionMode(false)
        assertFalse(viewModel.isSelectionMode.value)

        viewModel.handleLongClick(photo)

        assertTrue(viewModel.isSelectionMode.value)
        verify { photoSelectionRepository.addPhoto(photo) }
    }

    @Test
    fun `handleLongClick when already in selection mode just adds photo`() {
        val photo = createMockPhoto("1")

        viewModel.setSelectionMode(true)
        assertTrue(viewModel.isSelectionMode.value)

        viewModel.handleLongClick(photo)

        assertTrue(viewModel.isSelectionMode.value)
        verify { photoSelectionRepository.addPhoto(photo) }
    }

    // --- Photo Selection Delegation Tests ---

    @Test
    fun `togglePhoto delegates to repository`() {
        val photo = createMockPhoto("1")

        viewModel.togglePhoto(photo)

        verify { photoSelectionRepository.togglePhoto(photo) }
    }

    @Test
    fun `addPhoto delegates to repository`() {
        val photo = createMockPhoto("1")

        viewModel.addPhoto(photo)

        verify { photoSelectionRepository.addPhoto(photo) }
    }

    @Test
    fun `removePhoto delegates to repository`() {
        val photo = createMockPhoto("1")

        viewModel.removePhoto(photo)

        verify { photoSelectionRepository.removePhoto(photo) }
    }

    @Test
    fun `clearDraft delegates to repository`() {
        viewModel.clearDraft()

        verify { photoSelectionRepository.clear() }
    }

    // --- isPhotoSelected Tests ---

    @Test
    fun `isPhotoSelected returns true when photo is selected`() {
        val photo = createMockPhoto("1")
        selectedPhotosFlow.value = mapOf("1" to photo)

        assertTrue(viewModel.isPhotoSelected(photo))
    }

    @Test
    fun `isPhotoSelected returns false when photo is not selected`() {
        val photo = createMockPhoto("1")
        selectedPhotosFlow.value = emptyMap()

        assertFalse(viewModel.isPhotoSelected(photo))
    }

    // --- setGalleryBrowsingSession Tests ---

    @Test
    fun `setGalleryBrowsingSession sets gallery in repository`() {
        viewModel.setGalleryBrowsingSession()

        verify { imageBrowserRepository.setGallery(any()) }
    }

    // --- setSelectionMode Tests ---

    @Test
    fun `setSelectionMode updates state`() {
        assertTrue(viewModel.isSelectionMode.value) // Default is true

        viewModel.setSelectionMode(false)
        assertFalse(viewModel.isSelectionMode.value)

        viewModel.setSelectionMode(true)
        assertTrue(viewModel.isSelectionMode.value)
    }

    // --- isAddingToExistingTag Tests ---

    @Test
    fun `isAddingToExistingTag returns true when existingTagId is set`() {
        existingTagIdFlow.value = "tag123"

        assertTrue(viewModel.isAddingToExistingTag())
    }

    @Test
    fun `isAddingToExistingTag returns false when existingTagId is null`() {
        existingTagIdFlow.value = null

        assertFalse(viewModel.isAddingToExistingTag())
    }

    // --- handleDoneButtonClick Tests ---

    @Test
    fun `handleDoneButtonClick does nothing when existingTagId is null`() =
        runTest {
            existingTagIdFlow.value = null

            viewModel.handleDoneButtonClick()
            advanceUntilIdle()

            assertEquals(SelectImageViewModel.AddPhotosState.Idle, viewModel.addPhotosState.value)
            coVerify(exactly = 0) { remoteRepository.postTagsToPhoto(any(), any()) }
        }

    @Test
    fun `handleDoneButtonClick adds photos to existing tag successfully`() =
        runTest {
            existingTagIdFlow.value = "tag123"
            tagNameFlow.value = "TestTag"
            val photo1 = createMockPhoto("1")
            val photo2 = createMockPhoto("2")
            selectedPhotosFlow.value = mapOf("1" to photo1, "2" to photo2)

            coEvery { remoteRepository.postTagsToPhoto("1", "tag123") } returns
                RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.postTagsToPhoto("2", "tag123") } returns
                RemoteRepository.Result.Success(Unit)

            viewModel.handleDoneButtonClick()
            advanceUntilIdle()

            assertEquals(SelectImageViewModel.AddPhotosState.Success, viewModel.addPhotosState.value)
            verify { photoSelectionRepository.clear() }
        }

    @Test
    fun `handleDoneButtonClick handles error`() =
        runTest {
            existingTagIdFlow.value = "tag123"
            tagNameFlow.value = "TestTag"
            val photo1 = createMockPhoto("1")
            selectedPhotosFlow.value = mapOf("1" to photo1)

            coEvery { remoteRepository.postTagsToPhoto("1", "tag123") } returns
                RemoteRepository.Result.Error(500, "Server error")

            viewModel.handleDoneButtonClick()
            advanceUntilIdle()

            assertTrue(viewModel.addPhotosState.value is SelectImageViewModel.AddPhotosState.Error)
            val errorState = viewModel.addPhotosState.value as SelectImageViewModel.AddPhotosState.Error
            assertEquals(SelectImageViewModel.SelectImageError.UnknownError, errorState.error)
        }

    @Test
    fun `handleDoneButtonClick handles unauthorized error`() =
        runTest {
            existingTagIdFlow.value = "tag123"
            tagNameFlow.value = "TestTag"
            val photo1 = createMockPhoto("1")
            selectedPhotosFlow.value = mapOf("1" to photo1)

            coEvery { remoteRepository.postTagsToPhoto("1", "tag123") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.handleDoneButtonClick()
            advanceUntilIdle()

            assertTrue(viewModel.addPhotosState.value is SelectImageViewModel.AddPhotosState.Error)
            val errorState = viewModel.addPhotosState.value as SelectImageViewModel.AddPhotosState.Error
            assertEquals(SelectImageViewModel.SelectImageError.Unauthorized, errorState.error)
        }

    @Test
    fun `handleDoneButtonClick handles network error`() =
        runTest {
            existingTagIdFlow.value = "tag123"
            tagNameFlow.value = "TestTag"
            val photo1 = createMockPhoto("1")
            selectedPhotosFlow.value = mapOf("1" to photo1)

            coEvery { remoteRepository.postTagsToPhoto("1", "tag123") } returns
                RemoteRepository.Result.NetworkError("Network error")

            viewModel.handleDoneButtonClick()
            advanceUntilIdle()

            assertTrue(viewModel.addPhotosState.value is SelectImageViewModel.AddPhotosState.Error)
            val errorState = viewModel.addPhotosState.value as SelectImageViewModel.AddPhotosState.Error
            assertEquals(SelectImageViewModel.SelectImageError.NetworkError, errorState.error)
        }

    @Test
    fun `handleDoneButtonClick stops on first error`() =
        runTest {
            existingTagIdFlow.value = "tag123"
            tagNameFlow.value = "TestTag"
            val photo1 = createMockPhoto("1")
            val photo2 = createMockPhoto("2")
            val photo3 = createMockPhoto("3")
            selectedPhotosFlow.value = linkedMapOf("1" to photo1, "2" to photo2, "3" to photo3)

            coEvery { remoteRepository.postTagsToPhoto("1", "tag123") } returns
                RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.postTagsToPhoto("2", "tag123") } returns
                RemoteRepository.Result.Error(500, "Server error")
            coEvery { remoteRepository.postTagsToPhoto("3", "tag123") } returns
                RemoteRepository.Result.Success(Unit)

            viewModel.handleDoneButtonClick()
            advanceUntilIdle()

            assertTrue(viewModel.addPhotosState.value is SelectImageViewModel.AddPhotosState.Error)
            // Should stop after photo2 error, not call photo3
            coVerify(exactly = 0) { remoteRepository.postTagsToPhoto("3", "tag123") }
            verify(exactly = 0) { photoSelectionRepository.clear() }
        }

    // --- Initial State Tests ---

    @Test
    fun `initial state is correct`() {
        assertTrue(viewModel.allPhotos.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isLoadingMore.value)
        assertEquals(SelectImageViewModel.RecommendState.Idle, viewModel.recommendState.value)
        assertTrue(viewModel.isSelectionMode.value) // Default is true
        assertTrue(viewModel.recommendedPhotos.value.isEmpty())
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
        assertEquals(SelectImageViewModel.AddPhotosState.Idle, viewModel.addPhotosState.value)
    }

    // --- StateFlow Exposure Tests ---

    @Test
    fun `tagName flow is exposed from repository`() {
        tagNameFlow.value = "MyTag"

        assertEquals("MyTag", viewModel.tagName.value)
    }

    @Test
    fun `selectedPhotos flow is exposed from repository`() {
        val photo = createMockPhoto("1")
        selectedPhotosFlow.value = mapOf("1" to photo)

        assertEquals(1, viewModel.selectedPhotos.value.size)
        assertEquals(photo, viewModel.selectedPhotos.value["1"])
    }

    @Test
    fun `existingTagId flow is exposed from repository`() {
        existingTagIdFlow.value = "tag123"

        assertEquals("tag123", viewModel.existingTagId.value)

        existingTagIdFlow.value = null
        assertNull(viewModel.existingTagId.value)
    }
}
