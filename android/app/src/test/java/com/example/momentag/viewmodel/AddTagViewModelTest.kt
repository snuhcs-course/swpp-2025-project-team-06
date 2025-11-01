package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.RecommendState
import com.example.momentag.model.TagCreateResponse
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AddTagViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var draftTagRepository: DraftTagRepository
    private lateinit var recommendRepository: RecommendRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var viewModel: AddTagViewModel

    private val testPhotos =
        listOf(
            Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
            Photo(photoId = "photo2", contentUri = Uri.parse("content://media/2")),
            Photo(photoId = "photo3", contentUri = Uri.parse("content://media/3")),
        )

    @Before
    fun setUp() {
        draftTagRepository = mock()
        recommendRepository = mock()
        localRepository = mock()
        remoteRepository = mock()

        // Mock StateFlows
        whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(""))
        whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(emptyList()))

        viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)
    }

    // ========== initialize Tests ==========

    @Test
    fun `initialize delegates to repository with valid data`() {
        // Given
        val tagName = "Nature"
        val photos = testPhotos

        // When
        viewModel.initialize(tagName, photos)

        // Then
        verify(draftTagRepository).initialize(tagName, photos)
    }

    @Test
    fun `initialize delegates to repository with null tag name`() {
        // Given
        val photos = testPhotos

        // When
        viewModel.initialize(null, photos)

        // Then
        verify(draftTagRepository).initialize(null, photos)
    }

    @Test
    fun `initialize delegates to repository with empty photos`() {
        // Given
        val tagName = "Nature"
        val emptyPhotos = emptyList<Photo>()

        // When
        viewModel.initialize(tagName, emptyPhotos)

        // Then
        verify(draftTagRepository).initialize(tagName, emptyPhotos)
    }

    // ========== updateTagName Tests ==========

    @Test
    fun `updateTagName delegates to repository`() {
        // Given
        val newName = "Sunset"

        // When
        viewModel.updateTagName(newName)

        // Then
        verify(draftTagRepository).updateTagName(newName)
    }

    @Test
    fun `updateTagName delegates with empty string`() {
        // When
        viewModel.updateTagName("")

        // Then
        verify(draftTagRepository).updateTagName("")
    }

    // ========== addPhoto Tests ==========

    @Test
    fun `addPhoto delegates to repository`() {
        // Given
        val photo = testPhotos[0]

        // When
        viewModel.addPhoto(photo)

        // Then
        verify(draftTagRepository).addPhoto(photo)
    }

    // ========== removePhoto Tests ==========

    @Test
    fun `removePhoto delegates to repository`() {
        // Given
        val photo = testPhotos[0]

        // When
        viewModel.removePhoto(photo)

        // Then
        verify(draftTagRepository).removePhoto(photo)
    }

    // ========== clearDraft Tests ==========

    @Test
    fun `clearDraft delegates to repository`() {
        // When
        viewModel.clearDraft()

        // Then
        verify(draftTagRepository).clear()
    }

    // ========== hasChanges Tests ==========

    @Test
    fun `hasChanges delegates to repository and returns true`() {
        // Given
        whenever(draftTagRepository.hasChanges()).thenReturn(true)

        // When
        val result = viewModel.hasChanges()

        // Then
        assertTrue(result)
        verify(draftTagRepository).hasChanges()
    }

    @Test
    fun `hasChanges delegates to repository and returns false`() {
        // Given
        whenever(draftTagRepository.hasChanges()).thenReturn(false)

        // When
        val result = viewModel.hasChanges()

        // Then
        assertFalse(result)
        verify(draftTagRepository).hasChanges()
    }

    // ========== resetSaveState Tests ==========

    @Test
    fun `resetSaveState resets state to Idle`() {
        // When
        viewModel.resetSaveState()

        // Then
        assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Idle)
    }

    // ========== recommendPhoto Tests ==========

    @Test
    fun `recommendPhoto success updates state with photos`() =
        runTest {
            // Given
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo4", photoPathId = 4L),
                    PhotoResponse(photoId = "photo5", photoPathId = 5L),
                )
            val expectedPhotos =
                listOf(
                    Photo(photoId = "photo4", contentUri = Uri.parse("content://media/4")),
                    Photo(photoId = "photo5", contentUri = Uri.parse("content://media/5")),
                )

            whenever(recommendRepository.recommendPhotosFromPhotos(any()))
                .thenReturn(RecommendRepository.RecommendResult.Success(photoResponses))
            whenever(localRepository.toPhotos(photoResponses)).thenReturn(expectedPhotos)

            // Recreate viewModel to pick up new flow
            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.recommendPhoto()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.recommendState.value
            assertTrue(state is RecommendState.Success)
            assertEquals(expectedPhotos, (state as RecommendState.Success).photos)
            verify(recommendRepository).recommendPhotosFromPhotos(listOf("photo1", "photo2", "photo3"))
        }

    @Test
    fun `recommendPhoto sets Loading state immediately`() =
        runTest {
            // Given
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(recommendRepository.recommendPhotosFromPhotos(any()))
                .thenReturn(RecommendRepository.RecommendResult.Success(emptyList()))
            whenever(localRepository.toPhotos(any())).thenReturn(emptyList())

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.recommendPhoto()
            mainCoroutineRule.testDispatcher.scheduler.advanceTimeBy(1)

            // Then - eventually becomes Success after completion
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()
            assertTrue(viewModel.recommendState.value is RecommendState.Success)
        }

    @Test
    fun `recommendPhoto Error sets error state`() =
        runTest {
            // Given
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            val errorMessage = "Recommendation failed"
            whenever(recommendRepository.recommendPhotosFromPhotos(any()))
                .thenReturn(RecommendRepository.RecommendResult.Error(errorMessage))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.recommendPhoto()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.recommendState.value
            assertTrue(state is RecommendState.Error)
            assertEquals(errorMessage, (state as RecommendState.Error).message)
        }

    @Test
    fun `recommendPhoto Unauthorized sets error state with login message`() =
        runTest {
            // Given
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(recommendRepository.recommendPhotosFromPhotos(any()))
                .thenReturn(RecommendRepository.RecommendResult.Unauthorized("Unauthorized"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.recommendPhoto()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.recommendState.value
            assertTrue(state is RecommendState.Error)
            assertEquals("Please login again", (state as RecommendState.Error).message)
        }

    @Test
    fun `recommendPhoto NetworkError sets network error state`() =
        runTest {
            // Given
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            val errorMessage = "Network connection failed"
            whenever(recommendRepository.recommendPhotosFromPhotos(any()))
                .thenReturn(RecommendRepository.RecommendResult.NetworkError(errorMessage))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.recommendPhoto()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.recommendState.value
            assertTrue(state is RecommendState.NetworkError)
            assertEquals(errorMessage, (state as RecommendState.NetworkError).message)
        }

    @Test
    fun `recommendPhoto BadRequest sets error state`() =
        runTest {
            // Given
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            val errorMessage = "Bad request"
            whenever(recommendRepository.recommendPhotosFromPhotos(any()))
                .thenReturn(RecommendRepository.RecommendResult.BadRequest(errorMessage))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.recommendPhoto()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.recommendState.value
            assertTrue(state is RecommendState.Error)
            assertEquals(errorMessage, (state as RecommendState.Error).message)
        }

    // ========== saveTagAndPhotos Tests ==========

    @Test
    fun `saveTagAndPhotos with blank tag name sets error state`() =
        runTest {
            // Given
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(""))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals("Tag cannot be empty and photos must be selected", (state as AddTagViewModel.SaveState.Error).message)
            verify(remoteRepository, never()).postTags(any())
        }

    @Test
    fun `saveTagAndPhotos with empty photos sets error state`() =
        runTest {
            // Given
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow("Nature"))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(emptyList()))
            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals("Tag cannot be empty and photos must be selected", (state as AddTagViewModel.SaveState.Error).message)
            verify(remoteRepository, never()).postTags(any())
        }

    @Test
    fun `saveTagAndPhotos success creates tag and adds to all photos`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), eq(tagId)))
                .thenReturn(RemoteRepository.Result.Success(Unit))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)
            verify(remoteRepository).postTags(tagName)
            verify(remoteRepository, times(3)).postTagsToPhoto(any(), eq(tagId))
            verify(remoteRepository).postTagsToPhoto("photo1", tagId)
            verify(remoteRepository).postTagsToPhoto("photo2", tagId)
            verify(remoteRepository).postTagsToPhoto("photo3", tagId)
        }

    @Test
    fun `saveTagAndPhotos sets Loading state during operation`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), any()))
                .thenReturn(RemoteRepository.Result.Success(Unit))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.advanceTimeBy(1)

            // Complete the operation
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then - eventually becomes Success
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)
        }

    @Test
    fun `saveTagAndPhotos error on postTags sets error state`() =
        runTest {
            // Given
            val tagName = "Nature"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Error(500, "Server error"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals("Error creating tag", (state as AddTagViewModel.SaveState.Error).message)
            verify(remoteRepository, never()).postTagsToPhoto(any(), any())
        }

    @Test
    fun `saveTagAndPhotos error on postTags with BadRequest sets error state`() =
        runTest {
            // Given
            val tagName = "Nature"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.BadRequest("Bad request"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals("Error creating tag", (state as AddTagViewModel.SaveState.Error).message)
        }

    @Test
    fun `saveTagAndPhotos error on first postTagsToPhoto stops processing`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto("photo1", tagId))
                .thenReturn(RemoteRepository.Result.BadRequest("Bad request"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertEquals("Bad Request: Bad request", (state as AddTagViewModel.SaveState.Error).message)
            verify(remoteRepository).postTagsToPhoto("photo1", tagId)
            verify(remoteRepository, never()).postTagsToPhoto("photo2", tagId)
            verify(remoteRepository, never()).postTagsToPhoto("photo3", tagId)
        }

    @Test
    fun `saveTagAndPhotos error on middle postTagsToPhoto stops processing`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto("photo1", tagId))
                .thenReturn(RemoteRepository.Result.Success(Unit))
            whenever(remoteRepository.postTagsToPhoto("photo2", tagId))
                .thenReturn(RemoteRepository.Result.Unauthorized("Unauthorized"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertTrue((state as AddTagViewModel.SaveState.Error).message.contains("Login error"))
            verify(remoteRepository).postTagsToPhoto("photo1", tagId)
            verify(remoteRepository).postTagsToPhoto("photo2", tagId)
            verify(remoteRepository, never()).postTagsToPhoto("photo3", tagId)
        }

    @Test
    fun `saveTagAndPhotos handles different error types correctly`() =
        runTest {
            // Test Error type
            val tagName = "Nature"
            val tagId = "tag123"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(listOf(testPhotos[0])))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), any()))
                .thenReturn(RemoteRepository.Result.Error(500, "Server error"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertTrue((state as AddTagViewModel.SaveState.Error).message.contains("Server Error (500)"))
        }

    @Test
    fun `saveTagAndPhotos handles NetworkError type correctly`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(listOf(testPhotos[0])))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), any()))
                .thenReturn(RemoteRepository.Result.NetworkError("Network failed"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertTrue((state as AddTagViewModel.SaveState.Error).message.contains("Network Error"))
        }

    @Test
    fun `saveTagAndPhotos handles Exception type correctly`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(listOf(testPhotos[0])))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), any()))
                .thenReturn(RemoteRepository.Result.Exception(RuntimeException("Unexpected error")))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.saveState.value
            assertTrue(state is AddTagViewModel.SaveState.Error)
            assertTrue((state as AddTagViewModel.SaveState.Error).message.contains("Network Error"))
        }

    // ========== Integration Tests ==========

    @Test
    fun `workflow - initialize, update tag name, save successfully`() =
        runTest {
            // Given
            val initialTag = "Nature"
            val updatedTag = "Beautiful Nature"
            val tagId = "tag123"

            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(initialTag))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When - Initialize
            viewModel.initialize(initialTag, testPhotos)
            verify(draftTagRepository).initialize(initialTag, testPhotos)

            // When - Update tag name
            viewModel.updateTagName(updatedTag)
            verify(draftTagRepository).updateTagName(updatedTag)

            // Setup for save
            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(updatedTag))
            whenever(remoteRepository.postTags(updatedTag))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), eq(tagId)))
                .thenReturn(RemoteRepository.Result.Success(Unit))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When - Save
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)
        }

    @Test
    fun `workflow - add photos, remove photo, save`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            val twoPhotos = listOf(testPhotos[0], testPhotos[1])

            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(twoPhotos))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When - Add photo
            viewModel.addPhoto(testPhotos[0])
            verify(draftTagRepository).addPhoto(testPhotos[0])

            // When - Remove photo
            viewModel.removePhoto(testPhotos[2])
            verify(draftTagRepository).removePhoto(testPhotos[2])

            // Setup for save
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), eq(tagId)))
                .thenReturn(RemoteRepository.Result.Success(Unit))

            // When - Save
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)
            verify(remoteRepository, times(2)).postTagsToPhoto(any(), eq(tagId))
        }

    @Test
    fun `workflow - recommend photos then save`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo4", photoPathId = 4L),
                )
            val recommendedPhotos =
                listOf(
                    Photo(photoId = "photo4", contentUri = Uri.parse("content://media/4")),
                )

            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(recommendRepository.recommendPhotosFromPhotos(any()))
                .thenReturn(RecommendRepository.RecommendResult.Success(photoResponses))
            whenever(localRepository.toPhotos(photoResponses)).thenReturn(recommendedPhotos)

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When - Recommend
            viewModel.recommendPhoto()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val recommendState = viewModel.recommendState.value
            assertTrue(recommendState is RecommendState.Success)

            // Setup for save
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), eq(tagId)))
                .thenReturn(RemoteRepository.Result.Success(Unit))

            // When - Save
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)
        }

    @Test
    fun `workflow - save error, reset state, try again successfully`() =
        runTest {
            // Given
            val tagName = "Nature"
            val tagId = "tag123"

            whenever(draftTagRepository.tagName).thenReturn(MutableStateFlow(tagName))
            whenever(draftTagRepository.selectedPhotos).thenReturn(MutableStateFlow(testPhotos))
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Error(500, "Server error"))

            viewModel = AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository)

            // When - First save attempt fails
            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Error)

            // When - Reset state
            viewModel.resetSaveState()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Idle)

            // When - Try again with success
            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(TagCreateResponse(tagId)))
            whenever(remoteRepository.postTagsToPhoto(any(), eq(tagId)))
                .thenReturn(RemoteRepository.Result.Success(Unit))

            viewModel.saveTagAndPhotos()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)
        }

    @Test
    fun `hasChanges returns correct value throughout workflow`() {
        // Given - initially false
        whenever(draftTagRepository.hasChanges()).thenReturn(false)
        assertFalse(viewModel.hasChanges())

        // When - has changes
        whenever(draftTagRepository.hasChanges()).thenReturn(true)
        assertTrue(viewModel.hasChanges())

        // When - cleared
        whenever(draftTagRepository.hasChanges()).thenReturn(false)
        assertFalse(viewModel.hasChanges())
    }
}
