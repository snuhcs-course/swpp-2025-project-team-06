package com.example.momentag.viewmodel

import app.cash.turbine.test
import com.example.momentag.model.RecommendState
import com.example.momentag.model.TagAlbum
import com.example.momentag.repository.RecommendRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RecommendViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var mockRecommendRepository: RecommendRepository

    private lateinit var viewModel: RecommendViewModel

    private val validTagAlbum = TagAlbum(tagName = "바다", photos = listOf(100L, 101L))
    private val emptyTagAlbum = TagAlbum(tagName = "", photos = emptyList())
    private val mockResultPhotos: List<Long> = listOf(201L, 202L)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        viewModel = RecommendViewModel(mockRecommendRepository)
    }

    /**
     * 테스트 1: ViewModel 자체 검증 (입력이 비어있을 때)
     */
    @Test
    fun recommendEmptyQuery() =
        runTest(mainCoroutineRule.testDispatcher) { // [수정]

            viewModel.recommendState.test {
                assertEquals(RecommendState.Idle, awaitItem())

                viewModel.recommend(emptyTagAlbum)

                val errorState = awaitItem() as RecommendState.Error
                assertEquals("Query cannot be empty", errorState.message)

                verify(mockRecommendRepository, never()).recommendPhotos(any())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * 테스트 2: 성공 케이스
     */
    @Test
    fun recommendSuccess() =
        runTest(mainCoroutineRule.testDispatcher) { // [수정]

            // Given
            val successResult = RecommendRepository.RecommendResult.Success(mockResultPhotos)
            whenever(mockRecommendRepository.recommendPhotos(validTagAlbum)).thenReturn(successResult)

            // When & Then
            viewModel.recommendState.test {
                assertEquals(RecommendState.Idle, awaitItem())

                viewModel.recommend(validTagAlbum)

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                assertEquals(RecommendState.Loading, awaitItem())

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                val successState = awaitItem() as RecommendState.Success
                assertEquals(mockResultPhotos, successState.photos)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * 테스트 3: Unauthorized 케이스
     */
    @Test
    fun recommendUnauthorized() =
        runTest(mainCoroutineRule.testDispatcher) {

            val repoResult = RecommendRepository.RecommendResult.Unauthorized("Authentication failed")
            whenever(mockRecommendRepository.recommendPhotos(validTagAlbum)).thenReturn(repoResult)

            viewModel.recommendState.test {
                assertEquals(RecommendState.Idle, awaitItem())

                viewModel.recommend(validTagAlbum)

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                assertEquals(RecommendState.Loading, awaitItem())

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                val vmErrorState = awaitItem() as RecommendState.Error
                assertEquals("Please login again", vmErrorState.message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * 테스트 4: BadRequest 케이스
     */
    @Test
    fun recommendBadRequest() =
        runTest(mainCoroutineRule.testDispatcher) {

            val repoMessage = "Bad request"
            val repoResult = RecommendRepository.RecommendResult.BadRequest(repoMessage)
            whenever(mockRecommendRepository.recommendPhotos(validTagAlbum)).thenReturn(repoResult)

            viewModel.recommendState.test {
                assertEquals(RecommendState.Idle, awaitItem())

                viewModel.recommend(validTagAlbum)

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                assertEquals(RecommendState.Loading, awaitItem())

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                val vmErrorState = awaitItem() as RecommendState.Error
                assertEquals(repoMessage, vmErrorState.message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * 테스트 5: NetworkError 케이스
     */
    @Test
    fun recommendNetworkError() =
        runTest(mainCoroutineRule.testDispatcher) {

            val repoMessage = "Network error: No connection"
            val repoResult = RecommendRepository.RecommendResult.NetworkError(repoMessage)
            whenever(mockRecommendRepository.recommendPhotos(validTagAlbum)).thenReturn(repoResult)

            viewModel.recommendState.test {
                assertEquals(RecommendState.Idle, awaitItem())

                viewModel.recommend(validTagAlbum)

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                assertEquals(RecommendState.Loading, awaitItem())

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                val vmState = awaitItem()
                assertTrue(vmState is RecommendState.NetworkError)
                assertEquals(repoMessage, (vmState as RecommendState.NetworkError).message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * 테스트 6: Repository의 Empty 케이스
     */
    @Test
    fun recommendEmpty() =
        runTest(mainCoroutineRule.testDispatcher) {

            val repoResult = RecommendRepository.RecommendResult.Empty(query = "some_query")
            whenever(mockRecommendRepository.recommendPhotos(validTagAlbum)).thenReturn(repoResult)

            viewModel.recommendState.test {
                assertEquals(RecommendState.Idle, awaitItem())

                viewModel.recommend(validTagAlbum)

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                assertEquals(RecommendState.Loading, awaitItem())

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                val vmErrorState = awaitItem() as RecommendState.Error
                assertEquals("Query cannot be empty", vmErrorState.message)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * 테스트 7: 상태 초기화
     */
    @Test
    fun resetState() =
        runTest(mainCoroutineRule.testDispatcher) {
            val successResult = RecommendRepository.RecommendResult.Success(mockResultPhotos)
            whenever(mockRecommendRepository.recommendPhotos(validTagAlbum)).thenReturn(successResult)

            viewModel.recommendState.test {
                assertEquals(RecommendState.Idle, awaitItem())

                viewModel.recommend(validTagAlbum)

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                assertEquals(RecommendState.Loading, awaitItem())

                mainCoroutineRule.testDispatcher.scheduler.runCurrent()
                assertEquals(RecommendState.Success(mockResultPhotos), awaitItem())

                viewModel.resetSearchState()
                assertEquals(RecommendState.Idle, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}