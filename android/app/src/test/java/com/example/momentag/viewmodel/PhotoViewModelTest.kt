package com.example.momentag.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.momentag.model.Photo
import com.example.momentag.model.UploadJobState
import com.example.momentag.model.UploadStatus
import com.example.momentag.model.UploadType
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.UploadStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
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
    private lateinit var uploadStateRepository: UploadStateRepository
    private lateinit var albumUploadJobCount: MutableStateFlow<Int>
    private lateinit var mockContext: Context
    private lateinit var mockWorkManager: WorkManager
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setUp() {
        remoteRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        uploadStateRepository = mockk(relaxed = true)
        albumUploadJobCount = MutableStateFlow(0)
        mockContext = mockk(relaxed = true)
        mockWorkManager = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)

        // Mock WorkManager.getInstance
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager
        every { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) } returns mockk(relaxed = true)

        // Mock Context.contentResolver
        every { mockContext.contentResolver } returns mockContentResolver

        // Default mock for getAllActiveStates
        coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

        viewModel =
            PhotoViewModel(
                remoteRepository,
                localRepository,
                uploadStateRepository,
                albumUploadJobCount,
            )
    }

    @After
    fun tearDown() {
        unmockkStatic(WorkManager::class)
    }

    private fun createMockPhoto(
        id: String,
        contentUri: Uri = mockk(relaxed = true),
    ): Photo {
        every { contentUri.lastPathSegment } returns id
        return Photo(
            photoId = id,
            contentUri = contentUri,
            createdAt = "2023-01-01",
        )
    }

    private fun createMockUploadJobState(
        jobId: String = "job1",
        type: UploadType = UploadType.ALBUM,
        albumId: Long? = 1L,
        status: UploadStatus = UploadStatus.RUNNING,
        totalPhotoIds: List<Long> = listOf(1L, 2L, 3L),
    ): UploadJobState =
        UploadJobState(
            jobId = jobId,
            type = type,
            albumId = albumId,
            status = status,
            totalPhotoIds = totalPhotoIds,
            failedPhotoIds = emptyList(),
            currentChunkIndex = 0,
            createdAt = System.currentTimeMillis(),
        )

    private fun mockCursorForPhotoIds(photoIds: List<Long>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        var currentIndex = -1

        every { cursor.getColumnIndexOrThrow(any()) } returns 0
        every { cursor.moveToNext() } answers {
            currentIndex++
            currentIndex < photoIds.size
        }
        every { cursor.getLong(0) } answers {
            photoIds[currentIndex]
        }
        every { cursor.close() } just runs

        return cursor
    }

    // --- Initialization Tests ---

    @Test
    fun `init subscribes to albumUploadJobCount and updates loading state`() =
        runTest {
            // ViewModel is already created in setUp with initial count of 0
            assertFalse(viewModel.uiState.value.isLoading)

            // Update count to 1
            albumUploadJobCount.value = 1
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isLoading)

            // Update count back to 0
            albumUploadJobCount.value = 0
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `init subscribes to albumUploadJobCount with multiple jobs`() =
        runTest {
            albumUploadJobCount.value = 3
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isLoading)
        }

    // --- uploadPhotosForAlbums Tests ---

    @Test
    fun `uploadPhotosForAlbums does nothing when albumIds is empty`() =
        runTest {
            viewModel.uploadPhotosForAlbums(emptySet(), mockContext)
            advanceUntilIdle()

            verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
            coVerify(exactly = 0) { uploadStateRepository.saveState(any()) }
        }

    @Test
    fun `uploadPhotosForAlbums starts new upload for album without existing paused job`() =
        runTest {
            val albumId = 1L
            val photoIds = listOf(1L, 2L, 3L)
            val cursor = mockCursorForPhotoIds(photoIds)

            every {
                mockContentResolver.query(any(), any(), any(), any(), any())
            } returns cursor

            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadPhotosForAlbums(setOf(albumId), mockContext)
            advanceUntilIdle()

            // Verify state was saved
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertEquals(UploadType.ALBUM, savedState.type)
            assertEquals(albumId, savedState.albumId)
            assertEquals(UploadStatus.RUNNING, savedState.status)
            assertEquals(photoIds, savedState.totalPhotoIds)
            assertTrue(savedState.failedPhotoIds.isEmpty())
            assertEquals(0, savedState.currentChunkIndex)

            // Verify WorkManager was called
            verify { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }

            // Verify user message
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)

            // Verify cursor was closed
            verify { cursor.close() }
        }

    @Test
    fun `uploadPhotosForAlbums resumes existing paused upload`() =
        runTest {
            val albumId = 1L
            val existingJobId = "existing-job-123"
            val pausedJob =
                createMockUploadJobState(
                    jobId = existingJobId,
                    albumId = albumId,
                    status = UploadStatus.PAUSED,
                )

            coEvery { uploadStateRepository.getAllActiveStates() } returns listOf(pausedJob)

            viewModel.uploadPhotosForAlbums(setOf(albumId), mockContext)
            advanceUntilIdle()

            // Verify state was updated to RUNNING
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertEquals(existingJobId, savedState.jobId)
            assertEquals(UploadStatus.RUNNING, savedState.status)

            // Verify WorkManager was called
            verify { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }

            // Verify user message
            assertEquals("Resuming upload...", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadPhotosForAlbums handles multiple albums`() =
        runTest {
            val albumId1 = 1L
            val albumId2 = 2L
            val photoIds = listOf(1L, 2L, 3L)
            val cursor = mockCursorForPhotoIds(photoIds)

            every {
                mockContentResolver.query(any(), any(), any(), any(), any())
            } returns cursor

            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadPhotosForAlbums(setOf(albumId1, albumId2), mockContext)
            advanceUntilIdle()

            // Verify state was saved for each album
            coVerify(exactly = 2) { uploadStateRepository.saveState(any()) }

            // Verify WorkManager was called twice
            verify(exactly = 2) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
        }

    @Test
    fun `uploadPhotosForAlbums handles empty album`() =
        runTest {
            val albumId = 1L
            val cursor = mockCursorForPhotoIds(emptyList())

            every {
                mockContentResolver.query(any(), any(), any(), any(), any())
            } returns cursor

            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadPhotosForAlbums(setOf(albumId), mockContext)
            advanceUntilIdle()

            // Should still save state even with empty album
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertTrue(savedState.totalPhotoIds.isEmpty())

            verify { cursor.close() }
        }

    @Test
    fun `uploadPhotosForAlbums handles null cursor`() =
        runTest {
            val albumId = 1L

            every {
                mockContentResolver.query(any(), any(), any(), any(), any())
            } returns null

            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadPhotosForAlbums(setOf(albumId), mockContext)
            advanceUntilIdle()

            // Should still save state with empty photo list
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertTrue(savedState.totalPhotoIds.isEmpty())
        }

    // --- uploadSelectedPhotos Tests ---

    @Test
    fun `uploadSelectedPhotos does nothing when photos set is empty`() =
        runTest {
            viewModel.uploadSelectedPhotos(emptySet(), mockContext)
            advanceUntilIdle()

            verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
            coVerify(exactly = 0) { uploadStateRepository.saveState(any()) }
        }

    @Test
    fun `uploadSelectedPhotos sets error when photo IDs cannot be extracted`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            // Mock lastPathSegment to return null
            every { photo1.contentUri.lastPathSegment } returns null

            viewModel.uploadSelectedPhotos(setOf(photo1), mockContext)
            advanceUntilIdle()

            assertEquals(
                PhotoViewModel.PhotoError.NoPhotosSelected,
                viewModel.uiState.value.error,
            )

            verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
            coVerify(exactly = 0) { uploadStateRepository.saveState(any()) }
        }

    @Test
    fun `uploadSelectedPhotos sets error when photo IDs are invalid`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            // Mock lastPathSegment to return non-numeric value
            every { photo1.contentUri.lastPathSegment } returns "invalid"

            viewModel.uploadSelectedPhotos(setOf(photo1), mockContext)
            advanceUntilIdle()

            assertEquals(
                PhotoViewModel.PhotoError.NoPhotosSelected,
                viewModel.uiState.value.error,
            )

            verify(exactly = 0) { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
            coVerify(exactly = 0) { uploadStateRepository.saveState(any()) }
        }

    @Test
    fun `uploadSelectedPhotos starts new upload for selected photos`() =
        runTest {
            val photo1 = createMockPhoto("1")
            val photo2 = createMockPhoto("2")
            val photo3 = createMockPhoto("3")

            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadSelectedPhotos(setOf(photo1, photo2, photo3), mockContext)
            advanceUntilIdle()

            // Verify state was saved
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertEquals(UploadType.SELECTED_PHOTOS, savedState.type)
            assertNull(savedState.albumId)
            assertEquals(UploadStatus.RUNNING, savedState.status)
            assertEquals(listOf(1L, 2L, 3L), savedState.totalPhotoIds)
            assertTrue(savedState.failedPhotoIds.isEmpty())
            assertEquals(0, savedState.currentChunkIndex)

            // Verify WorkManager was called
            verify { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }

            // Verify user message
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadSelectedPhotos resumes existing paused upload with matching photo IDs`() =
        runTest {
            val photo1 = createMockPhoto("1")
            val photo2 = createMockPhoto("2")

            val existingJobId = "existing-job-456"
            val pausedJob =
                createMockUploadJobState(
                    jobId = existingJobId,
                    type = UploadType.SELECTED_PHOTOS,
                    albumId = null,
                    status = UploadStatus.PAUSED,
                    totalPhotoIds = listOf(1L, 2L),
                )

            coEvery { uploadStateRepository.getAllActiveStates() } returns listOf(pausedJob)

            viewModel.uploadSelectedPhotos(setOf(photo1, photo2), mockContext)
            advanceUntilIdle()

            // Verify state was updated to RUNNING
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertEquals(existingJobId, savedState.jobId)
            assertEquals(UploadStatus.RUNNING, savedState.status)

            // Verify WorkManager was called
            verify { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }

            // Verify user message
            assertEquals("Resuming upload...", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadSelectedPhotos does not resume paused upload with different photo IDs`() =
        runTest {
            val photo1 = createMockPhoto("1")
            val photo2 = createMockPhoto("2")

            val existingJobId = "existing-job-456"
            val pausedJob =
                createMockUploadJobState(
                    jobId = existingJobId,
                    type = UploadType.SELECTED_PHOTOS,
                    albumId = null,
                    status = UploadStatus.PAUSED,
                    totalPhotoIds = listOf(3L, 4L), // Different photo IDs
                )

            coEvery { uploadStateRepository.getAllActiveStates() } returns listOf(pausedJob)

            viewModel.uploadSelectedPhotos(setOf(photo1, photo2), mockContext)
            advanceUntilIdle()

            // Verify a new state was saved (not resuming the existing one)
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            // Should have a different job ID
            assertTrue(savedState.jobId != existingJobId)
            assertEquals(UploadStatus.RUNNING, savedState.status)

            // Verify user message indicates new upload
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadSelectedPhotos does not resume album upload`() =
        runTest {
            val photo1 = createMockPhoto("1")
            val photo2 = createMockPhoto("2")

            val pausedJob =
                createMockUploadJobState(
                    type = UploadType.ALBUM,
                    status = UploadStatus.PAUSED,
                    totalPhotoIds = listOf(1L, 2L),
                )

            coEvery { uploadStateRepository.getAllActiveStates() } returns listOf(pausedJob)

            viewModel.uploadSelectedPhotos(setOf(photo1, photo2), mockContext)
            advanceUntilIdle()

            // Verify new state was saved (not resuming album upload)
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertEquals(UploadType.SELECTED_PHOTOS, savedState.type)

            // Verify user message indicates new upload
            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)
        }

    @Test
    fun `uploadSelectedPhotos handles single photo`() =
        runTest {
            val photo1 = createMockPhoto("123")

            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadSelectedPhotos(setOf(photo1), mockContext)
            advanceUntilIdle()

            // Verify state was saved
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertEquals(listOf(123L), savedState.totalPhotoIds)

            verify { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
        }

    @Test
    fun `uploadSelectedPhotos filters out photos with invalid IDs`() =
        runTest {
            val validPhoto = createMockPhoto("1")
            val invalidPhoto1 = createMockPhoto("invalid")
            every { invalidPhoto1.contentUri.lastPathSegment } returns "invalid"
            val invalidPhoto2 = createMockPhoto("null")
            every { invalidPhoto2.contentUri.lastPathSegment } returns null

            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadSelectedPhotos(setOf(validPhoto, invalidPhoto1, invalidPhoto2), mockContext)
            advanceUntilIdle()

            // Verify state was saved with only valid photo
            val stateSlot = slot<UploadJobState>()
            coVerify { uploadStateRepository.saveState(capture(stateSlot)) }

            val savedState = stateSlot.captured
            assertEquals(listOf(1L), savedState.totalPhotoIds)
        }

    // --- Message Handling Tests ---

    @Test
    fun `infoMessageShown clears user message`() =
        runTest {
            // Set up initial state with a message
            val photo1 = createMockPhoto("1")
            coEvery { uploadStateRepository.getAllActiveStates() } returns emptyList()

            viewModel.uploadSelectedPhotos(setOf(photo1), mockContext)
            advanceUntilIdle()

            assertEquals("Background upload started.", viewModel.uiState.value.userMessage)

            // Clear the message
            viewModel.infoMessageShown()

            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun `errorMessageShown clears error`() =
        runTest {
            // Set up initial state with an error
            val photo1 = createMockPhoto("invalid")
            every { photo1.contentUri.lastPathSegment } returns null

            viewModel.uploadSelectedPhotos(setOf(photo1), mockContext)
            advanceUntilIdle()

            assertEquals(
                PhotoViewModel.PhotoError.NoPhotosSelected,
                viewModel.uiState.value.error,
            )

            // Clear the error
            viewModel.errorMessageShown()

            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `infoMessageShown when no message present does not cause error`() {
        assertNull(viewModel.uiState.value.userMessage)

        viewModel.infoMessageShown()

        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun `errorMessageShown when no error present does not cause error`() {
        assertNull(viewModel.uiState.value.error)

        viewModel.errorMessageShown()

        assertNull(viewModel.uiState.value.error)
    }

    // --- UI State Tests ---

    @Test
    fun `initial UI state is correct`() {
        val initialState = viewModel.uiState.value

        assertFalse(initialState.isLoading)
        assertNull(initialState.userMessage)
        assertNull(initialState.error)
        assertFalse(initialState.isUploadSuccess)
    }

    @Test
    fun `UI state loading updates correctly with job count changes`() =
        runTest {
            assertFalse(viewModel.uiState.value.isLoading)

            albumUploadJobCount.value = 1
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isLoading)

            albumUploadJobCount.value = 2
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isLoading)

            albumUploadJobCount.value = 0
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isLoading)
        }
}
