package com.example.momentag.viewmodel

import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.RecommendState
import com.example.momentag.model.TagAlbum
import com.example.momentag.model.TagCreateResponse
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AddTagViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule() // Assuming MainCoroutineRule is available

    // Mocks
    private lateinit var draftTagRepository: DraftTagRepository
    private lateinit var recommendRepository: RecommendRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository

    // System Under Test
    private lateinit var viewModel: AddTagViewModel

    // Test Data
    private val testPhoto1 = Photo(photoId = "p1", contentUri = mock())
    private val testPhoto2 = Photo(photoId = "p2", contentUri = mock())
    private val testPhotos = listOf(testPhoto1, testPhoto2)

    private val testPhotoResponse1 = PhotoResponse(photoId = "p1", photoPathId = 1L)
    private val testPhotoResponse2 = PhotoResponse(photoId = "p2", photoPathId = 2L)
    private val testPhotoResponses = listOf(testPhotoResponse1, testPhotoResponse2)

    private val testTagId = "t123"
    // RecommendModels.kt에 정의된 TagAlbum
    private val testTagAlbum = TagAlbum(tagName = "Test", photos = emptyList())

    // Mock state flows from DraftTagRepository
    private val tagNameFlow = MutableStateFlow("")
    private val selectedPhotosFlow = MutableStateFlow<List<Photo>>(emptyList())

    @Before
    fun setUp() {
        draftTagRepository = mock()
        recommendRepository = mock()
        localRepository = mock()
        remoteRepository = mock()

        // Stub the repository flows
        whenever(draftTagRepository.tagName).thenReturn(tagNameFlow.asStateFlow())
        whenever(draftTagRepository.selectedPhotos).thenReturn(selectedPhotosFlow.asStateFlow())

        viewModel = AddTagViewModel(
            draftTagRepository,
            recommendRepository,
            localRepository,
            remoteRepository,
        )
    }

    // region DraftTagRepository Delegation Tests
    @Test
    fun initializeTest() {
        viewModel.initialize("Init Tag", testPhotos)
        verify(draftTagRepository).initialize("Init Tag", testPhotos)
    }

    @Test
    fun updateTagNameTest() {
        viewModel.updateTagName("New Name")
        verify(draftTagRepository).updateTagName("New Name")
    }

    @Test
    fun addPhotoTest() {
        viewModel.addPhoto(testPhoto1)
        verify(draftTagRepository).addPhoto(testPhoto1)
    }

    @Test
    fun removePhotoTest() {
        viewModel.removePhoto(testPhoto1)
        verify(draftTagRepository).removePhoto(testPhoto1)
    }

    @Test
    fun clearDraftTest() {
        viewModel.clearDraft()
        verify(draftTagRepository).clear()
    }

    @Test
    fun hasChangesTest() {
        whenever(draftTagRepository.hasChanges()).thenReturn(true)
        assertTrue(viewModel.hasChanges())

        whenever(draftTagRepository.hasChanges()).thenReturn(false)
        assertFalse(viewModel.hasChanges())
    }

    // endregion

    @Test
    fun resetSaveStateTest() {
        // Set state to error
        tagNameFlow.value = "" // Fails validation
        viewModel.saveTagAndPhotos()
        assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Error)

        // Reset
        viewModel.resetSaveState()

        // Verify
        assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Idle)
    }

    // region recommendPhoto Tests
    @Test
    fun recommendPhotoEmptyQueryTest() = runTest{
        val emptyAlbum = TagAlbum(tagName = "", photos = emptyList())
        viewModel.recommendPhoto(emptyAlbum)

        val state = viewModel.recommendState.value
        assertTrue(state is RecommendState.Error)
        assertEquals("Query cannot be empty", (state as RecommendState.Error).message)
        verify(recommendRepository, never()).recommendPhotosFromTag(any())
    }

    @Test
    fun recommendPhotoSuccessTest() = runTest {
        // recommendPhotosFromTag는 List<PhotoResponse>를 반환합니다.
        val recommendResult =
            RecommendRepository.RecommendResult.Success(testPhotoResponses)
        // localRepository.toPhotos가 List<Photo>로 변환합니다.
        whenever(recommendRepository.recommendPhotosFromTag(testTagAlbum.tagName))
            .thenReturn(recommendResult)
        whenever(localRepository.toPhotos(testPhotoResponses)).thenReturn(testPhotos)

        viewModel.recommendPhoto(testTagAlbum)

        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        // Check success state
        val state = viewModel.recommendState.value
        assertTrue(state is RecommendState.Success)
        assertEquals(testPhotos, (state as RecommendState.Success).photos)
    }

    @Test
    fun recommendPhotoErrorTest() = runTest {
        val errorResult =
            RecommendRepository.RecommendResult.Error<List<PhotoResponse>>("Server Error")
        whenever(recommendRepository.recommendPhotosFromTag(testTagAlbum.tagName))
            .thenReturn(errorResult)

        viewModel.recommendPhoto(testTagAlbum)
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.recommendState.value
        assertTrue(state is RecommendState.Error)
        assertEquals("Server Error", (state as RecommendState.Error).message)
    }

    @Test
    fun recommendPhotoUnauthorizedTest() = runTest {
        val unauthorizedResult =
            RecommendRepository.RecommendResult.Unauthorized<List<PhotoResponse>>("Token expired")
        whenever(recommendRepository.recommendPhotosFromTag(testTagAlbum.tagName))
            .thenReturn(unauthorizedResult)

        viewModel.recommendPhoto(testTagAlbum)
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.recommendState.value
        assertTrue(state is RecommendState.Error)
        assertEquals("Please login again", (state as RecommendState.Error).message)
    }

    // endregion

    // region saveTagAndPhotos Tests
    @Test
    fun saveTagAndPhotosValidationErrorTagEmptyTest() = runTest {
        tagNameFlow.value = "" // Empty tag name
        selectedPhotosFlow.value = testPhotos // Valid photos

        viewModel.saveTagAndPhotos()

        val state = viewModel.saveState.value
        assertTrue(state is AddTagViewModel.SaveState.Error)
        assertEquals(
            "Tag cannot be empty and photos must be selected",
            (state as AddTagViewModel.SaveState.Error).message,
        )
        verify(remoteRepository, never()).postTags(any())
    }

    @Test
    fun saveTagAndPhotosValidationErrorPhotosEmptyTest() = runTest {
        tagNameFlow.value = "Test Tag" // Valid tag name
        selectedPhotosFlow.value = emptyList() // Empty photos

        viewModel.saveTagAndPhotos()

        val state = viewModel.saveState.value
        assertTrue(state is AddTagViewModel.SaveState.Error)
        assertEquals(
            "Tag cannot be empty and photos must be selected",
            (state as AddTagViewModel.SaveState.Error).message,
        )
        verify(remoteRepository, never()).postTags(any())
    }

    @Test
    fun saveTagAndPhotosTagCreationErrorTest() = runTest {
        tagNameFlow.value = "Test Tag"
        selectedPhotosFlow.value = testPhotos
        // postTags는 Result<TagCreateResponse>를 반환합니다.
        val errorResult = RemoteRepository.Result.Error<TagCreateResponse>(500, "Server Error")
        whenever(remoteRepository.postTags("Test Tag")).thenReturn(errorResult)

        viewModel.saveTagAndPhotos()

        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        // Check error state
        val state = viewModel.saveState.value
        assertTrue(state is AddTagViewModel.SaveState.Error)
        assertEquals("Error creating tag", (state as AddTagViewModel.SaveState.Error).message)
        verify(remoteRepository, never()).postTagsToPhoto(any(), any())
    }

    @Test
    fun saveTagAndPhotosAddPhotoErrorTest() = runTest {
        tagNameFlow.value = "Test Tag"
        selectedPhotosFlow.value = testPhotos // p1 and p2

        val tagSuccessResult = RemoteRepository.Result.Success(TagCreateResponse(tagId = testTagId))
        val photoSuccessResult = RemoteRepository.Result.Success(Unit)
        val photoFailResult = RemoteRepository.Result.BadRequest<Unit>("Bad photo ID")

        whenever(remoteRepository.postTags("Test Tag")).thenReturn(tagSuccessResult)
        whenever(remoteRepository.postTagsToPhoto(testPhoto1.photoId, testTagId))
            .thenReturn(photoSuccessResult) // p1 succeeds
        whenever(remoteRepository.postTagsToPhoto(testPhoto2.photoId, testTagId))
            .thenReturn(photoFailResult) // p2 fails

        viewModel.saveTagAndPhotos()
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.saveState.value
        assertTrue(state is AddTagViewModel.SaveState.Error)
        assertEquals(
            "Bad Request: Bad photo ID", // ViewModel의 getErrorMessage 포맷에 맞춤
            (state as AddTagViewModel.SaveState.Error).message,
        )

        // Verify calls
        verify(remoteRepository).postTags("Test Tag")
        verify(remoteRepository).postTagsToPhoto(testPhoto1.photoId, testTagId)
        verify(remoteRepository).postTagsToPhoto(testPhoto2.photoId, testTagId)
    }

    @Test
    fun saveTagAndPhotosSuccessTest() = runTest {
        tagNameFlow.value = "Test Tag"
        selectedPhotosFlow.value = testPhotos // p1 and p2

        val tagSuccessResult = RemoteRepository.Result.Success(TagCreateResponse(tagId = testTagId))
        val photoSuccessResult = RemoteRepository.Result.Success(Unit)

        whenever(remoteRepository.postTags("Test Tag")).thenReturn(tagSuccessResult)
        // Both photos succeed
        whenever(remoteRepository.postTagsToPhoto(any(), eq(testTagId)))
            .thenReturn(photoSuccessResult)

        viewModel.saveTagAndPhotos()
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        // Check success state
        assertTrue(viewModel.saveState.value is AddTagViewModel.SaveState.Success)

        // Verify calls
        verify(remoteRepository).postTags("Test Tag")
        verify(remoteRepository, times(2)).postTagsToPhoto(any(), eq(testTagId))
        verify(remoteRepository).postTagsToPhoto(testPhoto1.photoId, testTagId)
        verify(remoteRepository).postTagsToPhoto(testPhoto2.photoId, testTagId)
    }

    // endregion
}