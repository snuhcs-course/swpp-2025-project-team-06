package com.example.momentag.viewmodel

import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectImageViewModelTest {
    private lateinit var viewModel: SelectImageViewModel
    private lateinit var draftTagRepository: DraftTagRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        draftTagRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        remoteRepository = mockk(relaxed = true)

        // Mock repository flows
        every { draftTagRepository.tagName } returns MutableStateFlow("")
        every { draftTagRepository.selectedPhotos } returns MutableStateFlow(emptyList())

        viewModel =
            SelectImageViewModel(
                draftTagRepository = draftTagRepository,
                localRepository = localRepository,
                remoteRepository = remoteRepository,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // Helper functions
    private fun createPhotoResponse(id: String = "photo1") =
        PhotoResponse(
            photoId = id,
            photoPathId = 1L,
        )

    private fun createPhoto(id: String = "photo1") =
        Photo(
            photoId = id,
            contentUri = mockk(),
        )

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has empty allPhotos`() {
        // Then
        assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)
    }

    @Test
    fun `tagName flow is exposed from repository`() {
        // Given
        val tagNameFlow = MutableStateFlow("TestTag")
        every { draftTagRepository.tagName } returns tagNameFlow

        // When
        val newViewModel =
            SelectImageViewModel(
                draftTagRepository = draftTagRepository,
                localRepository = localRepository,
                remoteRepository = remoteRepository,
            )

        // Then
        assertEquals("TestTag", newViewModel.tagName.value)
    }

    @Test
    fun `selectedPhotos flow is exposed from repository`() {
        // Given
        val photos = listOf(createPhoto("photo1"))
        val selectedPhotosFlow = MutableStateFlow(photos)
        every { draftTagRepository.selectedPhotos } returns selectedPhotosFlow

        // When
        val newViewModel =
            SelectImageViewModel(
                draftTagRepository = draftTagRepository,
                localRepository = localRepository,
                remoteRepository = remoteRepository,
            )

        // Then
        assertEquals(photos, newViewModel.selectedPhotos.value)
    }

    // ========== getAllPhotos Tests ==========

    @Test
    fun `getAllPhotos with success loads photos`() =
        runTest {
            // Given
            val photoResponses = listOf(createPhotoResponse("photo1"), createPhotoResponse("photo2"))
            val photos = listOf(createPhoto("photo1"), createPhoto("photo2"))
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.getAllPhotos()

            // Then
            assertEquals(photos, viewModel.allPhotos.value)
            coVerify { remoteRepository.getAllPhotos() }
            coVerify { localRepository.toPhotos(photoResponses) }
        }

    @Test
    fun `getAllPhotos with empty success loads empty list`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()

            // When
            viewModel.getAllPhotos()

            // Then
            assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)
        }

    @Test
    fun `getAllPhotos with BadRequest does not update allPhotos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.BadRequest("Bad request")

            // When
            viewModel.getAllPhotos()

            // Then
            assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)
            coVerify { remoteRepository.getAllPhotos() }
            coVerify(exactly = 0) { localRepository.toPhotos(any()) }
        }

    @Test
    fun `getAllPhotos with Unauthorized does not update allPhotos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.getAllPhotos()

            // Then
            assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)
        }

    @Test
    fun `getAllPhotos with Error does not update allPhotos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Error(500, "Error")

            // When
            viewModel.getAllPhotos()

            // Then
            assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)
        }

    @Test
    fun `getAllPhotos with NetworkError does not update allPhotos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.NetworkError("Network error")

            // When
            viewModel.getAllPhotos()

            // Then
            assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)
        }

    @Test
    fun `getAllPhotos with Exception does not update allPhotos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Exception(java.lang.Exception("Exception"))

            // When
            viewModel.getAllPhotos()

            // Then
            assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)
        }

    @Test
    fun `getAllPhotos can be called multiple times`() =
        runTest {
            // Given
            val photoResponses1 = listOf(createPhotoResponse("photo1"))
            val photos1 = listOf(createPhoto("photo1"))
            val photoResponses2 = listOf(createPhotoResponse("photo2"), createPhotoResponse("photo3"))
            val photos2 = listOf(createPhoto("photo2"), createPhoto("photo3"))

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(photoResponses1) andThen
                RemoteRepository.Result.Success(photoResponses2)
            coEvery { localRepository.toPhotos(photoResponses1) } returns photos1
            coEvery { localRepository.toPhotos(photoResponses2) } returns photos2

            // When - first call
            viewModel.getAllPhotos()
            assertEquals(photos1, viewModel.allPhotos.value)

            // When - second call
            viewModel.getAllPhotos()
            assertEquals(photos2, viewModel.allPhotos.value)

            // Then
            coVerify(exactly = 2) { remoteRepository.getAllPhotos() }
        }

    // ========== togglePhoto Tests ==========

    @Test
    fun `togglePhoto calls repository togglePhoto`() {
        // Given
        val photo = createPhoto("photo1")

        // When
        viewModel.togglePhoto(photo)

        // Then
        verify { draftTagRepository.togglePhoto(photo) }
    }

    @Test
    fun `togglePhoto can be called multiple times`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")

        // When
        viewModel.togglePhoto(photo1)
        viewModel.togglePhoto(photo2)
        viewModel.togglePhoto(photo1)

        // Then
        verify(exactly = 2) { draftTagRepository.togglePhoto(photo1) }
        verify(exactly = 1) { draftTagRepository.togglePhoto(photo2) }
    }

    // ========== addPhoto Tests ==========

    @Test
    fun `addPhoto calls repository addPhoto`() {
        // Given
        val photo = createPhoto("photo1")

        // When
        viewModel.addPhoto(photo)

        // Then
        verify { draftTagRepository.addPhoto(photo) }
    }

    @Test
    fun `addPhoto can be called multiple times`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")

        // When
        viewModel.addPhoto(photo1)
        viewModel.addPhoto(photo2)

        // Then
        verify { draftTagRepository.addPhoto(photo1) }
        verify { draftTagRepository.addPhoto(photo2) }
    }

    // ========== removePhoto Tests ==========

    @Test
    fun `removePhoto calls repository removePhoto`() {
        // Given
        val photo = createPhoto("photo1")

        // When
        viewModel.removePhoto(photo)

        // Then
        verify { draftTagRepository.removePhoto(photo) }
    }

    @Test
    fun `removePhoto can be called multiple times`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")

        // When
        viewModel.removePhoto(photo1)
        viewModel.removePhoto(photo2)

        // Then
        verify { draftTagRepository.removePhoto(photo1) }
        verify { draftTagRepository.removePhoto(photo2) }
    }

    // ========== isPhotoSelected Tests ==========

    @Test
    fun `isPhotoSelected returns true when photo is selected`() {
        // Given
        val photo = createPhoto("photo1")
        val selectedPhotosFlow = MutableStateFlow(listOf(photo))
        every { draftTagRepository.selectedPhotos } returns selectedPhotosFlow
        val newViewModel =
            SelectImageViewModel(
                draftTagRepository = draftTagRepository,
                localRepository = localRepository,
                remoteRepository = remoteRepository,
            )

        // When
        val result = newViewModel.isPhotoSelected(photo)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isPhotoSelected returns false when photo is not selected`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")
        val selectedPhotosFlow = MutableStateFlow(listOf(photo1))
        every { draftTagRepository.selectedPhotos } returns selectedPhotosFlow
        val newViewModel =
            SelectImageViewModel(
                draftTagRepository = draftTagRepository,
                localRepository = localRepository,
                remoteRepository = remoteRepository,
            )

        // When
        val result = newViewModel.isPhotoSelected(photo2)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isPhotoSelected returns false when no photos selected`() {
        // Given
        val photo = createPhoto("photo1")

        // When
        val result = viewModel.isPhotoSelected(photo)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isPhotoSelected matches by photoId`() {
        // Given
        val photo1 = Photo(photoId = "photo1", contentUri = mockk())
        val photo2 = Photo(photoId = "photo1", contentUri = mockk()) // Same photoId, different instance
        val selectedPhotosFlow = MutableStateFlow(listOf(photo1))
        every { draftTagRepository.selectedPhotos } returns selectedPhotosFlow
        val newViewModel =
            SelectImageViewModel(
                draftTagRepository = draftTagRepository,
                localRepository = localRepository,
                remoteRepository = remoteRepository,
            )

        // When
        val result = newViewModel.isPhotoSelected(photo2)

        // Then
        assertTrue(result)
    }

    // ========== Integration Tests ==========

    @Test
    fun `complete workflow - load photos then select and deselect`() =
        runTest {
            // Given
            val photoResponses = listOf(createPhotoResponse("photo1"))
            val photos = listOf(createPhoto("photo1"))
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When - load photos
            viewModel.getAllPhotos()
            assertEquals(photos, viewModel.allPhotos.value)

            // When - select photo
            viewModel.addPhoto(photos[0])
            verify { draftTagRepository.addPhoto(photos[0]) }

            // When - deselect photo
            viewModel.removePhoto(photos[0])
            verify { draftTagRepository.removePhoto(photos[0]) }
        }

    @Test
    fun `complete workflow - toggle photos`() =
        runTest {
            // Given
            val photoResponses = listOf(createPhotoResponse("photo1"), createPhotoResponse("photo2"))
            val photos = listOf(createPhoto("photo1"), createPhoto("photo2"))
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When - load photos
            viewModel.getAllPhotos()
            assertEquals(2, viewModel.allPhotos.value.size)

            // When - toggle photos
            viewModel.togglePhoto(photos[0])
            viewModel.togglePhoto(photos[1])
            viewModel.togglePhoto(photos[0]) // toggle again

            // Then
            verify(exactly = 2) { draftTagRepository.togglePhoto(photos[0]) }
            verify(exactly = 1) { draftTagRepository.togglePhoto(photos[1]) }
        }

    @Test
    fun `error handling does not affect photo operations`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Error(500, "Error")

            // When - load fails
            viewModel.getAllPhotos()
            assertEquals(emptyList<Photo>(), viewModel.allPhotos.value)

            // When - still can manipulate photos
            val photo = createPhoto("photo1")
            viewModel.addPhoto(photo)
            viewModel.togglePhoto(photo)
            viewModel.removePhoto(photo)

            // Then
            verify { draftTagRepository.addPhoto(photo) }
            verify { draftTagRepository.togglePhoto(photo) }
            verify { draftTagRepository.removePhoto(photo) }
        }
}
