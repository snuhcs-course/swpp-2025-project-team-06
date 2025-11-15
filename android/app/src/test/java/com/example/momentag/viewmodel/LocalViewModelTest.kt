package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Album
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class LocalViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: LocalViewModel
    private lateinit var localRepository: LocalRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var albumUploadSuccessEvent: MutableSharedFlow<Long>

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        localRepository = mockk()
        imageBrowserRepository = mockk(relaxed = true)
        albumUploadSuccessEvent = MutableSharedFlow()

        viewModel = LocalViewModel(localRepository, imageBrowserRepository, albumUploadSuccessEvent)
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

    // Get images tests
    @Test
    fun `getImages loads images from localRepository`() =
        runTest {
            // Given
            val uri1 = createMockUri("content://media/external/images/media/1")
            val uri2 = createMockUri("content://media/external/images/media/2")
            every { Uri.parse("content://media/external/images/media/1") } returns uri1
            every { Uri.parse("content://media/external/images/media/2") } returns uri2
            val uris = listOf(uri1, uri2)
            coEvery { localRepository.getImages() } returns uris

            // When
            viewModel.getImages()
            advanceUntilIdle()

            // Then
            assertEquals(uris, viewModel.image.value)
        }

    // Get albums tests
    @Test
    fun `getAlbums loads albums from localRepository`() =
        runTest {
            // Given
            val uri1 = createMockUri("content://1")
            val uri2 = createMockUri("content://2")
            every { Uri.parse("content://1") } returns uri1
            every { Uri.parse("content://2") } returns uri2
            val albums =
                listOf(
                    Album(1L, "Album 1", uri1),
                    Album(2L, "Album 2", uri2),
                )
            coEvery { localRepository.getAlbums() } returns albums

            // When
            viewModel.getAlbums()
            advanceUntilIdle()

            // Then
            assertEquals(albums, viewModel.albums.value)
        }

    // Get images for album tests
    @Test
    fun `getImagesForAlbum loads album images and clears selection`() =
        runTest {
            // Given
            val albumId = 1L
            val uri1 = createMockUri("content://1")
            val uri2 = createMockUri("content://2")
            every { Uri.parse("content://1") } returns uri1
            every { Uri.parse("content://2") } returns uri2
            val photos =
                listOf(
                    Photo("1", uri1, "2025-01-01"),
                    Photo("2", uri2, "2025-01-02"),
                )
            coEvery { localRepository.getImagesForAlbum(albumId) } returns photos

            // When
            viewModel.getImagesForAlbum(albumId)
            advanceUntilIdle()

            // Then
            assertEquals(photos, viewModel.imagesInAlbum.value)
            assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
        }

    // Photo selection tests
    @Test
    fun `togglePhotoSelection adds and removes photos`() {
        // Given
        val uri = createMockUri("content://1")
        every { Uri.parse("content://1") } returns uri
        val photo = Photo("1", uri, "2025-01-01")

        // When - add to selection
        viewModel.togglePhotoSelection(photo)

        // Then
        assertTrue(viewModel.selectedPhotosInAlbum.value.contains(photo))

        // When - remove from selection
        viewModel.togglePhotoSelection(photo)

        // Then
        assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
    }

    @Test
    fun `clearPhotoSelection clears all selections`() {
        // Given
        val uri1 = createMockUri("content://1")
        val uri2 = createMockUri("content://2")
        every { Uri.parse("content://1") } returns uri1
        every { Uri.parse("content://2") } returns uri2
        val photo1 = Photo("1", uri1, "2025-01-01")
        val photo2 = Photo("2", uri2, "2025-01-02")
        viewModel.togglePhotoSelection(photo1)
        viewModel.togglePhotoSelection(photo2)

        // When
        viewModel.clearPhotoSelection()

        // Then
        assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
    }

    // Album selection tests
    @Test
    fun `toggleAlbumSelection adds and removes albums`() {
        // When - add to selection
        viewModel.toggleAlbumSelection(1L)

        // Then
        assertTrue(viewModel.selectedAlbumIds.value.contains(1L))

        // When - remove from selection
        viewModel.toggleAlbumSelection(1L)

        // Then
        assertFalse(viewModel.selectedAlbumIds.value.contains(1L))
    }

    @Test
    fun `selectAllAlbums selects all provided albums`() {
        // Given
        val uri1 = createMockUri("content://1")
        val uri2 = createMockUri("content://2")
        val uri3 = createMockUri("content://3")
        every { Uri.parse("content://1") } returns uri1
        every { Uri.parse("content://2") } returns uri2
        every { Uri.parse("content://3") } returns uri3
        val albums =
            listOf(
                Album(1L, "Album 1", uri1),
                Album(2L, "Album 2", uri2),
                Album(3L, "Album 3", uri3),
            )

        // When
        viewModel.selectAllAlbums(albums)

        // Then
        assertEquals(setOf(1L, 2L, 3L), viewModel.selectedAlbumIds.value)
    }

    @Test
    fun `clearAlbumSelection clears all album selections`() {
        // Given
        viewModel.toggleAlbumSelection(1L)
        viewModel.toggleAlbumSelection(2L)

        // When
        viewModel.clearAlbumSelection()

        // Then
        assertTrue(viewModel.selectedAlbumIds.value.isEmpty())
    }

    // Browsing session tests
    @Test
    fun `setGalleryBrowsingSession sets photos in imageBrowserRepository`() {
        // Given
        val uri1 = createMockUri("content://media/external/images/media/1")
        val uri2 = createMockUri("content://media/external/images/media/2")
        every { Uri.parse("content://media/external/images/media/1") } returns uri1
        every { Uri.parse("content://media/external/images/media/2") } returns uri2
        val uris = listOf(uri1, uri2)

        // When
        viewModel.setGalleryBrowsingSession(uris)

        // Then
        verify { imageBrowserRepository.setGallery(any()) }
    }

    @Test
    fun `setLocalAlbumBrowsingSession sets photos in imageBrowserRepository`() {
        // Given
        val uri1 = createMockUri("content://1")
        val uri2 = createMockUri("content://2")
        every { Uri.parse("content://1") } returns uri1
        every { Uri.parse("content://2") } returns uri2
        val photos =
            listOf(
                Photo("1", uri1, "2025-01-01"),
                Photo("2", uri2, "2025-01-02"),
            )
        val albumName = "Test Album"

        // When
        viewModel.setLocalAlbumBrowsingSession(photos, albumName)

        // Then
        verify { imageBrowserRepository.setLocalAlbum(photos, albumName) }
    }

    // Album upload success event tests
    @Test
    fun `albumUploadSuccessEvent clears photo selection for album 0L`() =
        runTest {
            // Given
            val uri = createMockUri("content://1")
            every { Uri.parse("content://1") } returns uri
            val photo = Photo("1", uri, "2025-01-01")
            viewModel.togglePhotoSelection(photo)

            // When
            albumUploadSuccessEvent.emit(0L)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
        }

    @Test
    fun `albumUploadSuccessEvent removes album from selection`() =
        runTest {
            // Given
            viewModel.toggleAlbumSelection(1L)
            viewModel.toggleAlbumSelection(2L)

            // When
            albumUploadSuccessEvent.emit(1L)
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.selectedAlbumIds.value.contains(1L))
            assertTrue(viewModel.selectedAlbumIds.value.contains(2L))
        }
}
