package com.example.momentag.viewmodel

import android.content.Context
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
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
}
