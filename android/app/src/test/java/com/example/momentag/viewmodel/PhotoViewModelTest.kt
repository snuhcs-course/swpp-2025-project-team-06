package com.example.momentag.viewmodel

import com.example.momentag.model.PhotoUploadData
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewModelTest {
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var viewModel: PhotoViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        remoteRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        viewModel = PhotoViewModel(remoteRepository, localRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state is correct`() =
        runTest {
            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.userMessage)
            assertFalse(state.isUploadSuccess)
        }

    // ========== uploadPhotos Success Tests ==========

    @Test
    fun `uploadPhotos success with 202 code updates state correctly`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Success(202)

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.isUploadSuccess)
            assertEquals("Success", state.userMessage)
            coVerify { localRepository.getPhotoUploadRequest() }
            coVerify { remoteRepository.uploadPhotos(mockRequest) }
        }

    @Test
    fun `uploadPhotos success with other code updates state with code message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Success(200)

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Upload successful (Code: 200)", state.userMessage)
        }

    @Test
    fun `uploadPhotos sets loading state immediately`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(any()) } coAnswers {
                // Check state during upload
                val loadingState = viewModel.uiState.value
                assertTrue(loadingState.isLoading)
                assertNull(loadingState.userMessage)
                assertFalse(loadingState.isUploadSuccess)
                RemoteRepository.Result.Success(202)
            }

            // When
            viewModel.uploadPhotos()

            // Then - after completion, loading is false
            assertFalse(viewModel.uiState.value.isLoading)
        }

    // ========== uploadPhotos Error Tests ==========

    @Test
    fun `uploadPhotos BadRequest updates state with error message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.BadRequest("Bad request")

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Request form mismatch", state.userMessage)
        }

    @Test
    fun `uploadPhotos Unauthorized updates state with error message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("The refresh token is expired", state.userMessage)
        }

    @Test
    fun `uploadPhotos Error updates state with error code`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Error(500, "Server error")

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Unexpected error: 500", state.userMessage)
        }

    @Test
    fun `uploadPhotos Exception updates state with exception message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            val exception = Exception("Test exception")
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Unknown error: Test exception", state.userMessage)
        }

    @Test
    fun `uploadPhotos NetworkError updates state with network error message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.NetworkError("Network error")

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Network error", state.userMessage)
        }

    // ========== uploadPhotos Exception Handling Tests ==========

    @Test
    fun `uploadPhotos handles IOException with network error message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } throws IOException("Connection timeout")

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Network error", state.userMessage)
        }

    @Test
    fun `uploadPhotos handles generic Exception with unknown error message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } throws Exception("Unexpected error")

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Unknown error: Unexpected error", state.userMessage)
        }

    @Test
    fun `uploadPhotos handles Exception with null message`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } throws Exception(null as String?)

            // When
            viewModel.uploadPhotos()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isUploadSuccess)
            assertEquals("Unknown error: null", state.userMessage)
        }

    // ========== userMessageShown Tests ==========

    @Test
    fun `userMessageShown resets userMessage to null`() =
        runTest {
            // Given - set a message first
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Success(202)
            viewModel.uploadPhotos()

            // Verify message is set
            assertEquals("Success", viewModel.uiState.value.userMessage)

            // When
            viewModel.userMessageShown()

            // Then
            assertNull(viewModel.uiState.value.userMessage)
            // Other states remain unchanged
            assertTrue(viewModel.uiState.value.isUploadSuccess)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `userMessageShown can be called multiple times`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.BadRequest("Bad request")
            viewModel.uploadPhotos()

            // When - call multiple times
            viewModel.userMessageShown()
            viewModel.userMessageShown()
            viewModel.userMessageShown()

            // Then - still null
            assertNull(viewModel.uiState.value.userMessage)
        }

    // ========== Integration Tests ==========

    @Test
    fun `upload flow - success then show message then reset`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Success(202)

            // When - upload
            viewModel.uploadPhotos()

            // Then - success state
            var state = viewModel.uiState.value
            assertTrue(state.isUploadSuccess)
            assertEquals("Success", state.userMessage)
            assertFalse(state.isLoading)

            // When - message shown
            viewModel.userMessageShown()

            // Then - message cleared
            state = viewModel.uiState.value
            assertNull(state.userMessage)
            assertTrue(state.isUploadSuccess) // isUploadSuccess remains
            assertFalse(state.isLoading)
        }

    @Test
    fun `upload flow - error then show message then upload again`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.NetworkError("Network error")

            // When - first upload fails
            viewModel.uploadPhotos()

            // Then - error state
            var state = viewModel.uiState.value
            assertEquals("Network error", state.userMessage)
            assertFalse(state.isUploadSuccess)

            // When - message shown
            viewModel.userMessageShown()

            // Then - message cleared
            assertNull(viewModel.uiState.value.userMessage)

            // When - upload again successfully
            coEvery { remoteRepository.uploadPhotos(mockRequest) } returns
                RemoteRepository.Result.Success(202)
            viewModel.uploadPhotos()

            // Then - success state
            state = viewModel.uiState.value
            assertEquals("Success", state.userMessage)
            assertTrue(state.isUploadSuccess)
        }

    @Test
    fun `state transitions maintain correct values`() =
        runTest {
            // Given
            val mockRequest = mockk<PhotoUploadData>()
            coEvery { localRepository.getPhotoUploadRequest() } returns mockRequest

            // Test different result types maintain state correctly
            val testCases =
                listOf(
                    RemoteRepository.Result.Success(202) to "Success",
                    RemoteRepository.Result.BadRequest<Int>("Bad request") to "Request form mismatch",
                    RemoteRepository.Result.Unauthorized<Int>("Unauthorized") to "The refresh token is expired",
                    RemoteRepository.Result.NetworkError<Int>("Network error") to "Network error",
                )

            testCases.forEach { (result, expectedMessage) ->
                // Given
                coEvery { remoteRepository.uploadPhotos(mockRequest) } returns result

                // When
                viewModel.uploadPhotos()

                // Then
                val state = viewModel.uiState.value
                assertEquals(expectedMessage, state.userMessage)
                assertFalse(state.isLoading)

                // Reset for next test
                viewModel.userMessageShown()
            }
        }
}
