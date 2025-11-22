package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.TagId
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTagViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: AddTagViewModel
    private lateinit var photoSelectionRepository: PhotoSelectionRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        photoSelectionRepository = mockk()
        localRepository = mockk()
        remoteRepository = mockk()

        every { photoSelectionRepository.tagName } returns MutableStateFlow("")
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyList())
        coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

        viewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)
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

    private fun createPhoto(id: String = "photo1"): Photo {
        val uri = createMockUri("content://media/external/images/media/$id")
        every { Uri.parse("content://media/external/images/media/$id") } returns uri
        return Photo(
            photoId = id,
            contentUri = uri,
            createdAt = "2025-01-01",
        )
    }

    // Initialize tests
    @Test
    fun `initialize delegates to photoSelectionRepository`() {
        // Given
        val tagName = "Test Tag"
        val photos = listOf(createPhoto())
        every { photoSelectionRepository.initialize(tagName, photos) } returns Unit

        // When
        viewModel.initialize(tagName, photos)

        // Then
        verify { photoSelectionRepository.initialize(tagName, photos) }
    }

    // Update tag name tests
    @Test
    fun `updateTagName delegates to photoSelectionRepository`() {
        // Given
        val tagName = "New Tag"
        every { photoSelectionRepository.updateTagName(tagName) } returns Unit

        // When
        viewModel.updateTagName(tagName)

        // Then
        verify { photoSelectionRepository.updateTagName(tagName) }
    }

    // Add photo tests
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

    // Remove photo tests
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

    // Clear draft tests
    @Test
    fun `clearDraft delegates to photoSelectionRepository`() {
        // Given
        every { photoSelectionRepository.clear() } returns Unit

        // When
        viewModel.clearDraft()

        // Then
        verify { photoSelectionRepository.clear() }
    }

    // Save tag tests
    @Test
    fun `saveTagAndPhotos with empty tag name updates state to Error`() =
        runTest {
            // Given
            every { photoSelectionRepository.tagName } returns MutableStateFlow("")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(listOf(createPhoto()))
            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.EmptyName, (state as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos with empty photos updates state to Error`() =
        runTest {
            // Given
            every { photoSelectionRepository.tagName } returns MutableStateFlow("Test Tag")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyList())
            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.NoPhotos, (state as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos success creates tag and associates photos`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val tagId = "tag-id-123"
            val photos = listOf(createPhoto("photo1"), createPhoto("photo2"))
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(any(), tagId) } returns
                RemoteRepository.Result.Success(Unit)

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            assertTrue(newViewModel.saveState.value is AddTagViewModel.SaveState.Success)
            coVerify { remoteRepository.postTags(tagName) }
            coVerify(exactly = 2) { remoteRepository.postTagsToPhoto(any(), tagId) }
        }

    @Test
    fun `saveTagAndPhotos with tag creation failure updates state to Error`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Error(500, "Server error")

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.UnknownError, (state as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos with tag creation unauthorized updates state to Error`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.Unauthorized, (state as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos with tag creation network error updates state to Error`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns RemoteRepository.Result.NetworkError("Network error")

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.NetworkError, (state as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos with photo association failure updates state to Error`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val tagId = "tag-id-123"
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(any(), tagId) } returns
                RemoteRepository.Result.Error(500, "Server error")

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.UnknownError, (state as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos with photo association unauthorized updates state to Error`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val tagId = "tag-id-123"
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(any(), tagId) } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.Unauthorized, (state as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos with photo association network error updates state to Error`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val tagId = "tag-id-123"
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(any(), tagId) } returns RemoteRepository.Result.NetworkError("Network error")

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)

            // When
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            val state = newViewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals(AddTagViewModel.AddTagError.NetworkError, (state as AddTagViewModel.SaveState.Error).error)
        }

    // Has changes test
    @Test
    fun `hasChanges delegates to photoSelectionRepository`() {
        // Given
        every { photoSelectionRepository.hasChanges() } returns true

        // When
        val result = viewModel.hasChanges()

        // Then
        assertTrue(result)
        verify { photoSelectionRepository.hasChanges() }
    }

    // Reset save state test
    @Test
    fun `resetSaveState resets to Idle`() =
        runTest {
            // Given
            val tagName = "Test Tag"
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.tagName } returns MutableStateFlow(tagName)
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId("tag-id"))
            coEvery { remoteRepository.postTagsToPhoto(any(), any()) } returns
                RemoteRepository.Result.Success(Unit)

            val newViewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)
            newViewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // When
            newViewModel.resetSaveState()

            // Then
            assertTrue(newViewModel.saveState.value is AddTagViewModel.SaveState.Idle)
        }
}
