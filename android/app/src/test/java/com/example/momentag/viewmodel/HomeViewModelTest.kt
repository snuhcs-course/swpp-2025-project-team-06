package com.example.momentag.viewmodel

import com.example.momentag.model.Tag
import com.example.momentag.model.TagItem
import com.example.momentag.repository.LocalRepository
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class HomeViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule() // Assuming MainCoroutineRule is available

    // Mocks
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository

    // System Under Test
    private lateinit var viewModel: HomeViewModel

    // Test Data
    // DataModels.kt에 정의된 Tag 사용
    private val testTag1 = Tag(tagId = "t1", tagName = "Tag 1")
    private val testTag2 = Tag(tagId = "t2", tagName = "Tag 2")
    private val remoteTags = listOf(testTag1, testTag2)

    @Before
    fun setUp() {
        localRepository = mock()
        remoteRepository = mock()
        viewModel = HomeViewModel(localRepository, remoteRepository)
    }

    // region loadServerTags Tests
    @Test
    fun loadServerTagsSuccessTest() =
        runTest {
            // getAllTags는 Result<List<Tag>>를 반환합니다.
            val successResult = RemoteRepository.Result.Success(remoteTags)
            whenever(remoteRepository.getAllTags()).thenReturn(successResult)

            viewModel.loadServerTags()

            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Check success state
            val state = viewModel.homeLoadingState.value
            assertTrue(state is HomeViewModel.HomeLoadingState.Success)

            // ViewModel이 List<Tag>를 List<TagItem>으로 변환하는지 확인
            val expectedTagItems =
                listOf(
                    TagItem(testTag1.tagName, null, testTag1.tagId),
                    TagItem(testTag2.tagName, null, testTag2.tagId),
                )
            assertEquals(expectedTagItems, (state as HomeViewModel.HomeLoadingState.Success).tags)
        }

    @Test
    fun loadServerTagsErrorTest() =
        runTest {
            val errorResult = RemoteRepository.Result.Error<List<Tag>>(500, "Server Error")
            whenever(remoteRepository.getAllTags()).thenReturn(errorResult)

            viewModel.loadServerTags()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            val state = viewModel.homeLoadingState.value
            assertTrue(state is HomeViewModel.HomeLoadingState.Error)
            assertEquals("Server Error", (state as HomeViewModel.HomeLoadingState.Error).message)
        }

    @Test
    fun loadServerTagsExceptionTest() =
        runTest {
            val exception = RuntimeException("Network failed")
            // RemoteRepository.Result.Exception 타입 사용
            val exceptionResult = RemoteRepository.Result.Exception<List<Tag>>(exception)
            whenever(remoteRepository.getAllTags()).thenReturn(exceptionResult)

            viewModel.loadServerTags()
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            val state = viewModel.homeLoadingState.value
            assertTrue(state is HomeViewModel.HomeLoadingState.Error)
            assertEquals("Network failed", (state as HomeViewModel.HomeLoadingState.Error).message)
        }
    // endregion

    // region deleteTag Tests
    @Test
    fun deleteTagSuccessTest() =
        runTest {
            val tagId = "t1"
            // removeTag는 Result<Unit>을 반환합니다.
            val successResult = RemoteRepository.Result.Success(Unit)
            whenever(remoteRepository.removeTag(tagId)).thenReturn(successResult)

            viewModel.deleteTag(tagId)

            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Check success state
            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Success)
            verify(remoteRepository).removeTag(tagId)
        }

    @Test
    fun deleteTagErrorTest() =
        runTest {
            val tagId = "t1"
            val errorResult = RemoteRepository.Result.Error<Unit>(404, "Tag not found")
            whenever(remoteRepository.removeTag(tagId)).thenReturn(errorResult)

            viewModel.deleteTag(tagId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            val state = viewModel.homeDeleteState.value
            assertTrue(state is HomeViewModel.HomeDeleteState.Error)
            assertEquals("Tag not found", (state as HomeViewModel.HomeDeleteState.Error).message)
        }

    @Test
    fun deleteTagUnauthorizedTest() =
        runTest {
            val tagId = "t1"
            val unauthorizedResult = RemoteRepository.Result.Unauthorized<Unit>("Token expired")
            whenever(remoteRepository.removeTag(tagId)).thenReturn(unauthorizedResult)

            viewModel.deleteTag(tagId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            val state = viewModel.homeDeleteState.value
            assertTrue(state is HomeViewModel.HomeDeleteState.Error)
            assertEquals("Token expired", (state as HomeViewModel.HomeDeleteState.Error).message)
        }
    // endregion

    @Test
    fun resetDeleteStateTest() =
        runTest {
            // given: Set state to error
            val errorResult = RemoteRepository.Result.Error<Unit>(500, "Server Error")
            whenever(remoteRepository.removeTag(any())).thenReturn(errorResult)
            viewModel.deleteTag("t1")
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()
            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Error)

            // when: Reset the state
            viewModel.resetDeleteState()

            // then: State is Idle
            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Idle)
        }
}
