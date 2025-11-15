package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.RecommendState
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectImageViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SelectImageViewModel
    private lateinit var photoSelectionRepository: PhotoSelectionRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var recommendRepository: RecommendRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        photoSelectionRepository = mockk()
        localRepository = mockk()
        remoteRepository = mockk()
        imageBrowserRepository = mockk(relaxed = true)
        recommendRepository = mockk()

        every { photoSelectionRepository.tagName } returns MutableStateFlow("")
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyList())
        every { photoSelectionRepository.existingTagId } returns MutableStateFlow(null)

        viewModel =
            SelectImageViewModel(
                photoSelectionRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
                recommendRepository,
            )
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Uri::class)
    }

    private fun createMockUri(path: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns path
        every { uri.lastPathSegment } returns path.substringAfterLast("/")
        return uri
    }

    private fun createPhotoResponse(id: String = "photo1") =
        PhotoResponse(
            photoId = id,
            photoPathId = 1L,
            createdAt = "2025-01-01",
        )

    private fun createPhoto(id: String = "photo1"): Photo {
        val uri = createMockUri("content://media/external/images/media/$id")
        every { Uri.parse("content://media/external/images/media/$id") } returns uri
        return Photo(
            photoId = id,
            contentUri = uri,
            createdAt = "2025-01-01",
        )
    }

    // Get all photos tests
    @Test
    fun `getAllPhotos success loads photos`() =
        runTest {
            // Given
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.getAllPhotos()
            advanceUntilIdle()

            // Then
            assertEquals(photos, viewModel.allPhotos.value)
            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun `getAllPhotos error clears photos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns
                RemoteRepository.Result.Error(500, "Error")

            // When
            viewModel.getAllPhotos()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.allPhotos.value.isEmpty())
            assertFalse(viewModel.isLoading.value)
        }

    // Recommend photo tests
    @Test
    fun `recommendPhoto success updates recommendState`() =
        runTest {
            // Given
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
            coEvery { recommendRepository.recommendPhotosFromPhotos(listOf("photo1")) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            val newViewModel =
                SelectImageViewModel(
                    photoSelectionRepository,
                    localRepository,
                    remoteRepository,
                    imageBrowserRepository,
                    recommendRepository,
                )

            // When
            newViewModel.recommendPhoto()
            advanceUntilIdle()

            // Then
            val state = newViewModel.recommendState.value
            assertTrue(state is RecommendState.Success)
            assertEquals(photos, (state as RecommendState.Success).photos)
        }

    // Photo selection tests
    @Test
    fun `togglePhoto delegates to photoSelectionRepository`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.togglePhoto(photo) } returns Unit

        // When
        viewModel.togglePhoto(photo)

        // Then
        verify { photoSelectionRepository.togglePhoto(photo) }
    }

    @Test
    fun `addPhoto delegates to photoSelectionRepository`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.addPhoto(photo) } returns Unit

        // When
        viewModel.addPhoto(photo)

        // Then
        verify { photoSelectionRepository.addPhoto(photo) }
    }

    @Test
    fun `removePhoto delegates to photoSelectionRepository`() {
        // Given
        val photo = createPhoto()
        every { photoSelectionRepository.removePhoto(photo) } returns Unit

        // When
        viewModel.removePhoto(photo)

        // Then
        verify { photoSelectionRepository.removePhoto(photo) }
    }

    // Selection mode tests
    @Test
    fun `setSelectionMode updates isSelectionMode`() {
        // When
        viewModel.setSelectionMode(true)

        // Then
        assertTrue(viewModel.isSelectionMode.value)

        // When
        viewModel.setSelectionMode(false)

        // Then
        assertFalse(viewModel.isSelectionMode.value)
    }

    // Browsing session test
    @Test
    fun `setGalleryBrowsingSession updates imageBrowserRepository`() {
        // When
        viewModel.setGalleryBrowsingSession()

        // Then
        verify { imageBrowserRepository.setGallery(any()) }
    }

    // Adding to existing tag tests
    @Test
    fun `isAddingToExistingTag returns true when existingTagId is set`() {
        // Given
        every { photoSelectionRepository.existingTagId } returns MutableStateFlow("tag1")
        val newViewModel =
            SelectImageViewModel(
                photoSelectionRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
                recommendRepository,
            )

        // When
        val result = newViewModel.isAddingToExistingTag()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isAddingToExistingTag returns false when existingTagId is null`() {
        // Given
        every { photoSelectionRepository.existingTagId } returns MutableStateFlow(null)
        val newViewModel =
            SelectImageViewModel(
                photoSelectionRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
                recommendRepository,
            )

        // When
        val result = newViewModel.isAddingToExistingTag()

        // Then
        assertFalse(result)
    }
}
