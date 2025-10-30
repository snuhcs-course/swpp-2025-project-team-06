package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoTagState
import com.example.momentag.model.Tag
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class ImageDetailViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule() // Assuming MainCoroutineRule is available

    // Mocks
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var recommendRepository: RecommendRepository
    private var mockUri: Uri = mock()

    // System Under Test
    private lateinit var viewModel: ImageDetailViewModel

    // Test Data
    private val testTag = Tag(tagId = "t1", tagName = "Existing")
    private val testPhoto = Photo(
        photoId = "p1",
        contentUri = mockUri,
    )
    private val testPhotoDetail = PhotoDetailResponse(
        photoPathId = 1L,
        tags = listOf(testTag),
    )
    private val testContext = ImageContext(
        images = listOf(testPhoto),
        currentIndex = 0,
        contextType = ImageContext.ContextType.GALLERY,
    )

    @Before
    fun setUp() {
        imageBrowserRepository = mock()
        remoteRepository = mock()
        recommendRepository = mock()
        mockUri = mock() // Mock android.net.Uri

        viewModel = ImageDetailViewModel(
            imageBrowserRepository,
            remoteRepository,
            recommendRepository,
        )
    }

    @Test
    fun loadImageContextTest() {
        val photoId = "p1"
        whenever(imageBrowserRepository.getPhotoContext(photoId)).thenReturn(testContext)

        viewModel.loadImageContext(photoId)

        assertEquals(testContext, viewModel.imageContext.value)
        verify(imageBrowserRepository).getPhotoContext(photoId)
    }

    @Test
    fun loadImageContextByUriFoundTest() {
        whenever(imageBrowserRepository.getPhotoContextByUri(mockUri)).thenReturn(testContext)

        viewModel.loadImageContextByUri(mockUri)

        assertEquals(testContext, viewModel.imageContext.value)
        verify(imageBrowserRepository).getPhotoContextByUri(mockUri)
    }

    @Test
    fun loadImageContextByUriNotFoundTest() {
        // URI가 세션에 없는 경우
        whenever(imageBrowserRepository.getPhotoContextByUri(mockUri)).thenReturn(null)

        viewModel.loadImageContextByUri(mockUri)

        val context = viewModel.imageContext.value
        assertNotNull(context)
        assertEquals(1, context!!.images.size)
        assertEquals(mockUri, context.images[0].contentUri)
        assertEquals("", context.images[0].photoId) // Standalone 이미지는 ID가 없음
        assertEquals(0, context.currentIndex)
        assertEquals(ImageContext.ContextType.GALLERY, context.contextType)
    }

    @Test
    fun loadPhotoTagsEmptyIdTest() = runTest {
        viewModel.loadPhotoTags("")

        assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
        verify(remoteRepository, never()).getPhotoDetail(any())
        verify(recommendRepository, never()).recommendTagFromPhoto(any())
    }

    @Test
    fun loadPhotoTagsSuccessTest() = runTest {
        val photoId = "p1"
        val detailResult = RemoteRepository.Result.Success(testPhotoDetail)
        // recommendTagFromPhoto는 Tag 객체를 반환합니다.
        val recommendData = Tag(tagName = "Recommended", tagId = "t-rec")
        val recommendResult = RecommendRepository.RecommendResult.Success(recommendData)

        whenever(remoteRepository.getPhotoDetail(photoId)).thenReturn(detailResult)
        whenever(recommendRepository.recommendTagFromPhoto(photoId)).thenReturn(recommendResult)

        viewModel.loadPhotoTags(photoId)

        // Coroutine 실행
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.photoTagState.value
        assertTrue(state is PhotoTagState.Success)
        assertEquals(testPhotoDetail.tags, (state as PhotoTagState.Success).existingTags)
        assertEquals(listOf("Recommended"), state.recommendedTags)
    }

    @Test
    fun loadPhotoTagsSuccessDuplicateRecommendTest() = runTest {
        // 추천 태그가 이미 존재하는 태그일 경우
        val photoId = "p1"
        val detailResult = RemoteRepository.Result.Success(testPhotoDetail)
        // "Existing" 태그는 이미 testPhotoDetail에 있습니다.
        val recommendData = Tag(tagName = "Existing", tagId = "t1") // 중복
        val recommendResult = RecommendRepository.RecommendResult.Success(recommendData)

        whenever(remoteRepository.getPhotoDetail(photoId)).thenReturn(detailResult)
        whenever(recommendRepository.recommendTagFromPhoto(photoId)).thenReturn(recommendResult)

        viewModel.loadPhotoTags(photoId)
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.photoTagState.value
        assertTrue(state is PhotoTagState.Success)
        assertEquals(testPhotoDetail.tags, (state as PhotoTagState.Success).existingTags)
        assertEquals(emptyList<String>(), state.recommendedTags) // 중복되므로 추천 목록에 없음
    }

    @Test
    fun loadPhotoTagsRecommendErrorTest() = runTest {
        // 태그 추천은 실패했지만, 기존 태그 로드는 성공한 경우
        val photoId = "p1"
        val detailResult = RemoteRepository.Result.Success(testPhotoDetail)
        // RecommendRepository의 Result Type을 사용합니다.
        val recommendResult = RecommendRepository.RecommendResult.Error<Tag>("Recommend failed")

        whenever(remoteRepository.getPhotoDetail(photoId)).thenReturn(detailResult)
        whenever(recommendRepository.recommendTagFromPhoto(photoId)).thenReturn(recommendResult)

        viewModel.loadPhotoTags(photoId)
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.photoTagState.value
        assertTrue(state is PhotoTagState.Success) // 여전히 Success여야 함
        assertEquals(testPhotoDetail.tags, (state as PhotoTagState.Success).existingTags)
        assertEquals(emptyList<String>(), state.recommendedTags) // 추천 태그는 비어있음
    }

    @Test
    fun loadPhotoTagsDetailErrorTest() = runTest {
        // 사진 상세 정보 로드 자체를 실패한 경우
        val photoId = "p1"
        // RemoteRepository의 Result Type과 PhotoDetailResponse를 사용합니다.
        val detailError = RemoteRepository.Result.Error<PhotoDetailResponse>(404, "Not Found")

        whenever(remoteRepository.getPhotoDetail(photoId)).thenReturn(detailError)

        viewModel.loadPhotoTags(photoId)
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.photoTagState.value
        assertTrue(state is PhotoTagState.Error)
        assertEquals("Not Found", (state as PhotoTagState.Error).message)

        // 추천 API는 호출되지 않았어야 함
        verify(recommendRepository, never()).recommendTagFromPhoto(any())
    }

    @Test
    fun deleteTagFromPhotoSuccessTest() = runTest {
        val photoId = "p1"
        val tagId = "t1"
        // RemoteRepository.removeTagFromPhoto는 Result<Unit>을 반환합니다.
        val deleteResult = RemoteRepository.Result.Success(Unit)

        whenever(remoteRepository.removeTagFromPhoto(photoId, tagId)).thenReturn(deleteResult)

        viewModel.deleteTagFromPhoto(photoId, tagId)
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.tagDeleteState.value is ImageDetailViewModel.TagDeleteState.Success)
        verify(remoteRepository).removeTagFromPhoto(photoId, tagId)
    }

    @Test
    fun deleteTagFromPhotoErrorTest() = runTest {
        val photoId = "p1"
        val tagId = "t1"
        val errorResult = RemoteRepository.Result.Error<Unit>(500, "Server Error")

        whenever(remoteRepository.removeTagFromPhoto(photoId, tagId)).thenReturn(errorResult)

        viewModel.deleteTagFromPhoto(photoId, tagId)
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()

        val state = viewModel.tagDeleteState.value
        assertTrue(state is ImageDetailViewModel.TagDeleteState.Error)
        assertEquals("Server Error", (state as ImageDetailViewModel.TagDeleteState.Error).message)
    }

    @Test
    fun resetDeleteStateTest() = runTest {
        // given: 상태를 Error로 변경
        val errorResult = RemoteRepository.Result.Error<Unit>(500, "Server Error")
        whenever(remoteRepository.removeTagFromPhoto(any(), any())).thenReturn(errorResult)
        viewModel.deleteTagFromPhoto("p1", "t1")
        mainCoroutineRule.testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.tagDeleteState.value is ImageDetailViewModel.TagDeleteState.Error)

        // when: 상태 리셋
        viewModel.resetDeleteState()

        // then: Idle 상태로 변경
        assertTrue(viewModel.tagDeleteState.value is ImageDetailViewModel.TagDeleteState.Idle)
    }

    @Test
    fun clearImageContextTest() {
        // given: 상태 설정
        val photoId = "p1"
        whenever(imageBrowserRepository.getPhotoContext(photoId)).thenReturn(testContext)

        // Act 1 (Arrange의 일부)
        viewModel.loadImageContext(photoId)
        assertNotNull(viewModel.imageContext.value)
        assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle) // loadPhotoTags("")는 Idle로 설정됨

        // when
        viewModel.clearImageContext()

        // then
        assertNull(viewModel.imageContext.value)
        assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
    }
}