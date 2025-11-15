package com.example.momentag.viewmodel

import android.content.Context
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: PhotoViewModel
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var albumUploadJobCount: MutableStateFlow<Int>
    private lateinit var context: Context

    @Before
    fun setUp() {
        remoteRepository = mockk()
        localRepository = mockk()
        albumUploadJobCount = MutableStateFlow(0)
        context = mockk(relaxed = true)

        viewModel = PhotoViewModel(remoteRepository, localRepository, albumUploadJobCount)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // Upload job count observation tests
    @Test
    fun `uiState isLoading tracks albumUploadJobCount`() =
        runTest {
            // Initial state
            assertFalse(viewModel.uiState.value.isLoading)

            // When job count increases
            albumUploadJobCount.value = 1
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isLoading)

            // When job count goes back to 0
            albumUploadJobCount.value = 0
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isLoading)
        }

    // Upload photos tests
    @Test
    fun `uploadPhotos success updates state correctly`() =
        runTest {
            // Given
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            coEvery { localRepository.getPhotoUploadRequest() } returns photoUploadData
            coEvery { remoteRepository.uploadPhotos(photoUploadData) } returns
                RemoteRepository.Result.Success(202)

            // When
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.isUploadSuccess)
            coVerify { localRepository.getPhotoUploadRequest() }
            coVerify { remoteRepository.uploadPhotos(photoUploadData) }
        }

    @Test
    fun `uploadPhotos with BadRequest updates state with error message`() =
        runTest {
            // Given
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            coEvery { localRepository.getPhotoUploadRequest() } returns photoUploadData
            coEvery { remoteRepository.uploadPhotos(photoUploadData) } returns
                RemoteRepository.Result.BadRequest("Invalid request")

            // When
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isUploadSuccess)
        }

    @Test
    fun `uploadPhotos with Unauthorized updates state with error message`() =
        runTest {
            // Given
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            coEvery { localRepository.getPhotoUploadRequest() } returns photoUploadData
            coEvery { remoteRepository.uploadPhotos(photoUploadData) } returns
                RemoteRepository.Result.Unauthorized("Token expired")

            // When
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isUploadSuccess)
        }

    @Test
    fun `uploadPhotos with NetworkError updates state with error message`() =
        runTest {
            // Given
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            coEvery { localRepository.getPhotoUploadRequest() } returns photoUploadData
            coEvery { remoteRepository.uploadPhotos(photoUploadData) } returns
                RemoteRepository.Result.NetworkError("Network error")

            // When
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isUploadSuccess)
        }

    @Test
    fun `uploadPhotos with Error updates state with error message`() =
        runTest {
            // Given
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            coEvery { localRepository.getPhotoUploadRequest() } returns photoUploadData
            coEvery { remoteRepository.uploadPhotos(photoUploadData) } returns
                RemoteRepository.Result.Error(500, "Server error")

            // When
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isUploadSuccess)
        }

    @Test
    fun `uploadPhotos with Exception updates state with error message`() =
        runTest {
            // Given
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            val exception = RuntimeException("Unexpected error")
            coEvery { localRepository.getPhotoUploadRequest() } returns photoUploadData
            coEvery { remoteRepository.uploadPhotos(photoUploadData) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isUploadSuccess)
        }

    // Message management tests
    @Test
    fun `infoMessageShown clears userMessage`() =
        runTest {
            // Given - set up a scenario with a message
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            coEvery { localRepository.getPhotoUploadRequest() } returns photoUploadData
            coEvery { remoteRepository.uploadPhotos(photoUploadData) } returns
                RemoteRepository.Result.Success(202)
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // When
            viewModel.infoMessageShown()

            // Then
            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun `errorMessageShown clears errorMessage`() =
        runTest {
            // Given - set up a scenario with an error message
            val photoUploadData = PhotoUploadData(emptyList(), "".toRequestBody())
            coEvery { localRepository.getPhotoUploadRequest() } throws RuntimeException("Error")
            viewModel.uploadPhotos()
            advanceUntilIdle()

            // When
            viewModel.errorMessageShown()

            // Then
            assertNull(viewModel.uiState.value.errorMessage)
        }
}
