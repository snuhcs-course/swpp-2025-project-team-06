package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.TagId
import com.example.momentag.model.TagResponse
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val tagNameFlow = MutableStateFlow("")
    private val selectedPhotosFlow = MutableStateFlow<Map<String, Photo>>(emptyMap())

    @Before
    fun setUp() {
        photoSelectionRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        remoteRepository = mockk(relaxed = true)

        every { photoSelectionRepository.tagName } returns tagNameFlow
        every { photoSelectionRepository.selectedPhotos } returns selectedPhotosFlow

        // Default behavior for init block
        coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
    }

    private fun createViewModel() {
        viewModel = AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository)
    }

    private fun createMockPhoto(id: String): Photo =
        Photo(
            photoId = id,
            contentUri = mockk<Uri>(relaxed = true),
            createdAt = "2023-01-01",
        )

    @Test
    fun `init loads existing tags`() =
        runTest {
            // Given
            val tags =
                listOf(
                    TagResponse("tag1", "1", null, null, null, 0),
                    TagResponse("tag2", "2", null, null, null, 0),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            // When
            createViewModel()
            advanceUntilIdle()

            // Then
            assertEquals(listOf("tag1", "tag2"), viewModel.existingTags.value)
        }

    @Test
    fun `isTagNameDuplicate updates when tagName changes`() =
        runTest {
            // Given
            val tags = listOf(TagResponse("existingTag", "1", null, null, null, 0))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)
            createViewModel()
            advanceUntilIdle()

            // When
            tagNameFlow.value = "existingTag"
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.isTagNameDuplicate.value)

            // When
            tagNameFlow.value = "newTag"
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.isTagNameDuplicate.value)
        }

    @Test
    fun `initialize delegates to repository`() {
        createViewModel()
        val photos = listOf(createMockPhoto("p1"))
        viewModel.initialize("initial", photos)
        verify { photoSelectionRepository.initialize("initial", photos) }
    }

    @Test
    fun `updateTagName delegates to repository`() {
        createViewModel()
        viewModel.updateTagName("new name")
        verify { photoSelectionRepository.updateTagName("new name") }
    }

    @Test
    fun `addPhoto delegates to repository`() {
        createViewModel()
        val photo = createMockPhoto("p1")
        viewModel.addPhoto(photo)
        verify { photoSelectionRepository.addPhoto(photo) }
    }

    @Test
    fun `removePhoto delegates to repository`() {
        createViewModel()
        val photo = createMockPhoto("p1")
        viewModel.removePhoto(photo)
        verify { photoSelectionRepository.removePhoto(photo) }
    }

    @Test
    fun `clearDraft delegates to repository`() {
        createViewModel()
        viewModel.clearDraft()
        verify { photoSelectionRepository.clear() }
    }

    @Test
    fun `hasChanges delegates to repository`() {
        createViewModel()
        every { photoSelectionRepository.hasChanges() } returns true
        assertTrue(viewModel.hasChanges())
    }

    @Test
    fun `saveTagAndPhotos with empty name sets error`() =
        runTest {
            createViewModel()
            tagNameFlow.value = ""

            viewModel.saveTagAndPhotos()

            assertEquals(AddTagViewModel.AddTagError.EmptyName, (viewModel.saveState.value as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos with no photos sets error`() =
        runTest {
            createViewModel()
            tagNameFlow.value = "valid name"
            selectedPhotosFlow.value = emptyMap()

            viewModel.saveTagAndPhotos()

            assertEquals(AddTagViewModel.AddTagError.NoPhotos, (viewModel.saveState.value as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos success`() =
        runTest {
            // Given
            createViewModel()
            tagNameFlow.value = "New Tag"
            val photo = createMockPhoto("p1")
            selectedPhotosFlow.value = mapOf("p1" to photo)

            coEvery { remoteRepository.postTags("New Tag") } returns RemoteRepository.Result.Success(TagId("t1"))
            coEvery { remoteRepository.postTagsToPhoto("p1", "t1") } returns RemoteRepository.Result.Success(Unit)

            // When
            viewModel.saveTagAndPhotos()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)
            coVerify { remoteRepository.postTags("New Tag") }
            coVerify { remoteRepository.postTagsToPhoto("p1", "t1") }
        }

    @Test
    fun `saveTagAndPhotos tag creation unauthorized`() =
        runTest {
            createViewModel()
            tagNameFlow.value = "New Tag"
            selectedPhotosFlow.value = mapOf("p1" to createMockPhoto("p1"))

            coEvery { remoteRepository.postTags("New Tag") } returns RemoteRepository.Result.Unauthorized("401")

            viewModel.saveTagAndPhotos()
            advanceUntilIdle()

            assertEquals(AddTagViewModel.AddTagError.Unauthorized, (viewModel.saveState.value as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos tag creation network error`() =
        runTest {
            createViewModel()
            tagNameFlow.value = "New Tag"
            selectedPhotosFlow.value = mapOf("p1" to createMockPhoto("p1"))

            coEvery { remoteRepository.postTags("New Tag") } returns RemoteRepository.Result.NetworkError("Error")

            viewModel.saveTagAndPhotos()
            advanceUntilIdle()

            assertEquals(AddTagViewModel.AddTagError.NetworkError, (viewModel.saveState.value as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `saveTagAndPhotos photo association failure`() =
        runTest {
            createViewModel()
            tagNameFlow.value = "New Tag"
            selectedPhotosFlow.value = mapOf("p1" to createMockPhoto("p1"))

            coEvery { remoteRepository.postTags("New Tag") } returns RemoteRepository.Result.Success(TagId("t1"))
            coEvery { remoteRepository.postTagsToPhoto("p1", "t1") } returns RemoteRepository.Result.NetworkError("Error")

            viewModel.saveTagAndPhotos()
            advanceUntilIdle()

            assertEquals(AddTagViewModel.AddTagError.NetworkError, (viewModel.saveState.value as AddTagViewModel.SaveState.Error).error)
        }

    @Test
    fun `resetSaveState resets to Idle`() =
        runTest {
            createViewModel()
            tagNameFlow.value = "" // Cause error
            viewModel.saveTagAndPhotos()
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Error)

            viewModel.resetSaveState()

            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Idle)
        }
}
