package com.example.momentag.viewmodel

import com.example.momentag.model.ImageOfTagLoadState
import com.example.momentag.model.Photo
import com.example.momentag.model.Photos
import com.example.momentag.model.Tag
import com.example.momentag.model.TagItem
import com.example.momentag.model.TagLoadState
import com.example.momentag.model.Tags
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class TagViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var remoteRepository: RemoteRepository
    private lateinit var viewModel: TagViewModel

    @Before
    fun setUp() {
        remoteRepository = mock()
        viewModel = TagViewModel(remoteRepository, mainCoroutineRule.testDispatcher)
    }

    @Test
    fun initialStateTest() {
        // ViewModel이 방금 생성되었으므로 두 상태 모두 Idle이어야 함
        val tagState = viewModel.tagLoadState.value
        val imageState = viewModel.imageOfTagLoadState.value

        assertTrue(tagState is TagLoadState.Idle)
        assertTrue(imageState is ImageOfTagLoadState.Idle)
    }

    // ‼️ [수정됨] "Init" 테스트가 아닌, 수동 호출 테스트
    @Test
    fun loadServerTagsSuccessTest() = runTest {
        // --- Arrange ---
        val tag1 = Tag(tagName = "food", tagId = 1L)
        val tag2 = Tag(tagName = "travel", tagId = 2L)
        val photo1 = Photo(photoId = 1001L)
        val photo2 = Photo(photoId = 1002L)

        val tagsResult = RemoteRepository.Result.Success(Tags(listOf(tag1, tag2)))
        val photos1Result = RemoteRepository.Result.Success(Photos(listOf(photo1)))
        val photos2Result = RemoteRepository.Result.Success(Photos(listOf(photo2)))

        whenever(remoteRepository.getAllTags()).thenReturn(tagsResult)
        whenever(remoteRepository.getPhotosByTag("food")).thenReturn(photos1Result)
        whenever(remoteRepository.getPhotosByTag("travel")).thenReturn(photos2Result)

        // --- Act ---
        viewModel.loadServerTags() // ‼️ 수동으로 함수 호출
        mainCoroutineRule.testDispatcher.scheduler.runCurrent() // 예약된 코루틴 실행

        // --- Assert ---
        val state = viewModel.tagLoadState.value
        assertTrue(state is TagLoadState.Success)

        val expectedTagItems = listOf(
            TagItem(tagName = "food", coverImageId = 1001L),
            TagItem(tagName = "travel", coverImageId = 1002L)
        )
        assertEquals(expectedTagItems, (state as TagLoadState.Success).tagItems)

        verify(remoteRepository).getAllTags()
        verify(remoteRepository).getPhotosByTag("food")
        verify(remoteRepository).getPhotosByTag("travel")
    }

    // ‼️ [수정됨] "Init" 테스트가 아닌, 수동 호출 테스트
    @Test
    fun loadServerTagsGetAllTagsErrorTest() = runTest {
        // --- Arrange ---
        val errorResult = RemoteRepository.Result.Error<Tags>(500, "Server Error")
        whenever(remoteRepository.getAllTags()).thenReturn(errorResult)

        // --- Act ---
        viewModel.loadServerTags() // ‼️ 수동으로 함수 호출
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        // --- Assert ---
        val state = viewModel.tagLoadState.value
        assertTrue(state is TagLoadState.Error)
        assertEquals("Server Error", (state as TagLoadState.Error).message)

        verify(remoteRepository).getAllTags()
        verify(remoteRepository, never()).getPhotosByTag(any())
    }

    // ‼️ [수정됨] "Init" 테스트가 아닌, 수동 호출 테스트
    @Test
    fun loadServerTagsPartialPhotoErrorTest() = runTest {
        // --- Arrange ---
        val tag1 = Tag(tagName = "food", tagId = 1L)
        val tag2 = Tag(tagName = "fail", tagId = 2L)
        val photo1 = Photo(photoId = 1001L)

        val tagsResult = RemoteRepository.Result.Success(Tags(listOf(tag1, tag2)))
        val photos1Result = RemoteRepository.Result.Success(Photos(listOf(photo1)))
        val photos2Error = RemoteRepository.Result.Error<Photos>(404, "Not Found")

        whenever(remoteRepository.getAllTags()).thenReturn(tagsResult)
        whenever(remoteRepository.getPhotosByTag("food")).thenReturn(photos1Result)
        whenever(remoteRepository.getPhotosByTag("fail")).thenReturn(photos2Error)

        // --- Act ---
        viewModel.loadServerTags() // ‼️ 수동으로 함수 호출
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        // --- Assert ---
        val state = viewModel.tagLoadState.value
        assertTrue(state is TagLoadState.Success)

        val expectedTagItems = listOf(
            TagItem(tagName = "food", coverImageId = 1001L),
            TagItem(tagName = "fail", coverImageId = null)
        )
        assertEquals(expectedTagItems, (state as TagLoadState.Success).tagItems)
    }

    // ---------------------------------------------------------------
    // ‼️ loadImagesOfTag, resetState 테스트는 (Init과 무관하므로) 동일
    // ---------------------------------------------------------------

    @Test
    fun loadImagesOfTagSuccessTest() = runTest {
        // --- Arrange ---
        val photo1 = Photo(photoId = 1001L)
        val photo2 = Photo(photoId = 1002L)
        val photosList = listOf(photo1, photo2)
        val photosWrapper = Photos(photosList)

        val photosResult = RemoteRepository.Result.Success(photosWrapper)
        whenever(remoteRepository.getPhotosByTag("album-tag")).thenReturn(photosResult)

        // --- Act ---
        viewModel.loadImagesOfTag("album-tag")
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        // --- Assert ---
        val state = viewModel.imageOfTagLoadState.value
        assertTrue(state is ImageOfTagLoadState.Success)

        // ImageOfTagLoadState.Success가 Photos 래퍼 객체를 갖는지 확인
        assertEquals(photosWrapper, (state as ImageOfTagLoadState.Success).photos)

        verify(remoteRepository).getPhotosByTag("album-tag")
    }

    @Test
    fun loadImagesOfTagErrorTest() = runTest {
        // --- Arrange ---
        val errorResult = RemoteRepository.Result.NetworkError<Photos>("No network")
        whenever(remoteRepository.getPhotosByTag("error-tag")).thenReturn(errorResult)

        // --- Act ---
        viewModel.loadImagesOfTag("error-tag")
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        // --- Assert ---
        val state = viewModel.imageOfTagLoadState.value
        assertTrue(state is ImageOfTagLoadState.NetworkError)
        assertEquals("No network", (state as ImageOfTagLoadState.NetworkError).message)
    }

    @Test
    fun resetStateTest() = runTest {
        // --- Arrange ---
        val errorResult = RemoteRepository.Result.NetworkError<Photos>("No network")
        whenever(remoteRepository.getPhotosByTag("test-tag")).thenReturn(errorResult)
        viewModel.loadImagesOfTag("test-tag")
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.imageOfTagLoadState.value is ImageOfTagLoadState.NetworkError)

        // --- Act ---
        viewModel.resetState()

        // --- Assert ---
        assertTrue(viewModel.tagLoadState.value is TagLoadState.Idle)
        assertTrue(viewModel.imageOfTagLoadState.value is ImageOfTagLoadState.Idle)
    }
}