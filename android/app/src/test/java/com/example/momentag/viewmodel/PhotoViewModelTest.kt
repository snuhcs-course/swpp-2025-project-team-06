package com.example.momentag.viewmodel

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.momentag.model.Photo
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
        mockkStatic(Uri::class)
        mockkStatic(WorkManager::class)
        remoteRepository = mockk()
        localRepository = mockk()
        albumUploadJobCount = MutableStateFlow(0)
        context = mockk(relaxed = true)

        // Mock WorkManager
        val workManager = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any()) } returns workManager

        viewModel = PhotoViewModel(remoteRepository, localRepository, albumUploadJobCount)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Uri::class)
        unmockkStatic(WorkManager::class)
    }

    private fun createMockUri(path: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns path
        every { uri.lastPathSegment } returns path.substringAfterLast("/")
        return uri
    }

    private fun createPhoto(id: String = "123"): Photo {
        val uri = createMockUri("content://media/external/images/media/$id")
        every { Uri.parse("content://media/external/images/media/$id") } returns uri
        return Photo(
            photoId = id,
            contentUri = uri,
            createdAt = "2025-01-01",
        )
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

    // uploadPhotosForAlbums tests
    @Test
    fun `uploadPhotosForAlbums with empty set does nothing`() =
        runTest {
            // Given
            val emptySet = emptySet<Long>()

            // When
            viewModel.uploadPhotosForAlbums(emptySet, context)
            advanceUntilIdle()

            // Then
            verify(exactly = 0) { WorkManager.getInstance(any<Context>()).enqueue(any<WorkRequest>()) }
            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadPhotosForAlbums with single album enqueues work and sets message`() =
        runTest {
            // Given
            val albumIds = setOf(1L)
            val workManager = mockk<WorkManager>(relaxed = true)
            every { WorkManager.getInstance(context) } returns workManager

            // When
            viewModel.uploadPhotosForAlbums(albumIds, context)
            advanceUntilIdle()

            // Then
            verify(exactly = 1) { workManager.enqueue(any<WorkRequest>()) }
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadPhotosForAlbums with multiple albums enqueues multiple works`() =
        runTest {
            // Given
            val albumIds = setOf(1L, 2L, 3L)
            val workManager = mockk<WorkManager>(relaxed = true)
            every { WorkManager.getInstance(context) } returns workManager

            // When
            viewModel.uploadPhotosForAlbums(albumIds, context)
            advanceUntilIdle()

            // Then
            verify(exactly = 3) { workManager.enqueue(any<WorkRequest>()) }
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    // uploadSelectedPhotos tests
    @Test
    fun `uploadSelectedPhotos with empty set does nothing`() =
        runTest {
            // Given
            val emptySet = emptySet<Photo>()

            // When
            viewModel.uploadSelectedPhotos(emptySet, context)
            advanceUntilIdle()

            // Then
            verify(exactly = 0) { WorkManager.getInstance(any<Context>()).enqueue(any<WorkRequest>()) }
            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadSelectedPhotos with invalid photo IDs sets error`() =
        runTest {
            // Given
            val photos = setOf(createPhoto("invalid-id"), createPhoto("another-invalid"))

            // When
            viewModel.uploadSelectedPhotos(photos, context)
            advanceUntilIdle()

            // Then
            verify(exactly = 0) { WorkManager.getInstance(any<Context>()).enqueue(any<WorkRequest>()) }
            assertEquals(PhotoViewModel.PhotoError.NoPhotosSelected, viewModel.uiState.value.error)
        }

    @Test
    fun `uploadSelectedPhotos with valid photo IDs enqueues work and sets message`() =
        runTest {
            // Given
            val photos = setOf(createPhoto("123"), createPhoto("456"))
            val workManager = mockk<WorkManager>(relaxed = true)
            every { WorkManager.getInstance(context) } returns workManager

            // When
            viewModel.uploadSelectedPhotos(photos, context)
            advanceUntilIdle()

            // Then
            verify(exactly = 1) { workManager.enqueue(any<WorkRequest>()) }
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadSelectedPhotos with mixed valid and invalid IDs uploads only valid ones`() =
        runTest {
            // Given
            val photos = setOf(createPhoto("123"), createPhoto("invalid"), createPhoto("456"))
            val workManager = mockk<WorkManager>(relaxed = true)
            every { WorkManager.getInstance(context) } returns workManager

            // When
            viewModel.uploadSelectedPhotos(photos, context)
            advanceUntilIdle()

            // Then
            verify(exactly = 1) { workManager.enqueue(any<WorkRequest>()) }
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    // Message clearing tests
    @Test
    fun `infoMessageShown clears user message`() =
        runTest {
            // Given - set a user message first
            val albumIds = setOf(1L)
            val workManager = mockk<WorkManager>(relaxed = true)
            every { WorkManager.getInstance(context) } returns workManager
            viewModel.uploadPhotosForAlbums(albumIds, context)
            advanceUntilIdle()
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)

            // When
            viewModel.infoMessageShown()
            advanceUntilIdle()

            // Then
            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun `errorMessageShown clears error`() =
        runTest {
            // Given - set an error message first
            val photos = setOf(createPhoto("invalid"))
            viewModel.uploadSelectedPhotos(photos, context)
            advanceUntilIdle()
            assertEquals(PhotoViewModel.PhotoError.NoPhotosSelected, viewModel.uiState.value.error)

            // When
            viewModel.errorMessageShown()
            advanceUntilIdle()

            // Then
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `multiple album uploads update message correctly`() =
        runTest {
            // Given
            val workManager = mockk<WorkManager>(relaxed = true)
            every { WorkManager.getInstance(context) } returns workManager

            // When - first upload
            viewModel.uploadPhotosForAlbums(setOf(1L), context)
            advanceUntilIdle()

            // Then
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)

            // When - clear message
            viewModel.infoMessageShown()
            advanceUntilIdle()

            // Then
            assertNull(viewModel.uiState.value.userMessage)

            // When - second upload
            viewModel.uploadPhotosForAlbums(setOf(2L), context)
            advanceUntilIdle()

            // Then
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `loading state updates with multiple job count changes`() =
        runTest {
            // Initial state
            assertFalse(viewModel.uiState.value.isLoading)

            // When multiple jobs start
            albumUploadJobCount.value = 3
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isLoading)

            // When some jobs complete but not all
            albumUploadJobCount.value = 1
            advanceUntilIdle()

            // Then - still loading
            assertTrue(viewModel.uiState.value.isLoading)

            // When all jobs complete
            albumUploadJobCount.value = 0
            advanceUntilIdle()

            // Then - not loading
            assertFalse(viewModel.uiState.value.isLoading)
        }
}
