package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Album
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalViewModelTest {
    private lateinit var localRepository: LocalRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var viewModel: LocalViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        localRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)
        viewModel = LocalViewModel(localRepository, imageBrowserRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Helper functions
    private fun createMockUri(path: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns "content://media/external/images/media/$path"
        every { uri.lastPathSegment } returns path
        return uri
    }

    // ========== getImages Tests ==========

    @Test
    fun `getImages fetches images and updates state`() =
        runTest {
            // Given
            val mockUris =
                listOf(
                    createMockUri("1"),
                    createMockUri("2"),
                    createMockUri("3"),
                )
            coEvery { localRepository.getImages() } returns mockUris

            // When
            viewModel.getImages()
            Thread.sleep(100) // Wait for Dispatchers.IO to complete

            // Then
            assertEquals(mockUris, viewModel.image.value)
            coVerify { localRepository.getImages() }
        }

    @Test
    fun `getImages with empty list updates state to empty`() =
        runTest {
            // Given
            coEvery { localRepository.getImages() } returns emptyList()

            // When
            viewModel.getImages()
            Thread.sleep(100)

            // Then
            assertEquals(emptyList<Uri>(), viewModel.image.value)
        }

    // ========== getAlbums Tests ==========

    @Test
    fun `getAlbums fetches albums and updates state`() =
        runTest {
            // Given
            val mockAlbums =
                listOf(
                    Album(1L, "Vacation", createMockUri("thumb1")),
                    Album(2L, "Family", createMockUri("thumb2")),
                )
            coEvery { localRepository.getAlbums() } returns mockAlbums

            // When
            viewModel.getAlbums()
            Thread.sleep(100)

            // Then
            assertEquals(mockAlbums, viewModel.albums.value)
            coVerify { localRepository.getAlbums() }
        }

    @Test
    fun `getAlbums with empty list updates state to empty`() =
        runTest {
            // Given
            coEvery { localRepository.getAlbums() } returns emptyList()

            // When
            viewModel.getAlbums()
            Thread.sleep(100)

            // Then
            assertEquals(emptyList<Album>(), viewModel.albums.value)
        }

    // ========== getImagesForAlbum Tests ==========

    @Test
    fun `getImagesForAlbum fetches images for specific album`() =
        runTest {
            // Given
            val albumId = 123L
            val mockUris =
                listOf(
                    createMockUri("10"),
                    createMockUri("20"),
                )
            coEvery { localRepository.getImagesForAlbum(albumId) } returns mockUris

            // When
            viewModel.getImagesForAlbum(albumId)
            Thread.sleep(100)

            // Then
            assertEquals(mockUris, viewModel.imagesInAlbum.value)
            coVerify { localRepository.getImagesForAlbum(albumId) }
        }

    @Test
    fun `getImagesForAlbum with empty album returns empty list`() =
        runTest {
            // Given
            val albumId = 456L
            coEvery { localRepository.getImagesForAlbum(albumId) } returns emptyList()

            // When
            viewModel.getImagesForAlbum(albumId)
            Thread.sleep(100)

            // Then
            assertEquals(emptyList<Uri>(), viewModel.imagesInAlbum.value)
        }

    // ========== setTagAlbumBrowsingSession Tests ==========

    @Test
    fun `setTagAlbumBrowsingSession converts URIs to Photos and calls repository`() =
        runTest {
            // Given
            val uri1 = createMockUri("100")
            val uri2 = createMockUri("200")
            val uris = listOf(uri1, uri2)
            val tagName = "여행"

            // When
            viewModel.setTagAlbumBrowsingSession(uris, tagName)

            // Then
            verify {
                imageBrowserRepository.setTagAlbum(
                    match { photos ->
                        photos.size == 2 &&
                            photos[0].photoId == "100" &&
                            photos[0].contentUri == uri1 &&
                            photos[1].photoId == "200" &&
                            photos[1].contentUri == uri2
                    },
                    tagName,
                )
            }
        }

    @Test
    fun `setTagAlbumBrowsingSession with empty list calls repository with empty list`() =
        runTest {
            // Given
            val tagName = "Empty"

            // When
            viewModel.setTagAlbumBrowsingSession(emptyList(), tagName)

            // Then
            verify {
                imageBrowserRepository.setTagAlbum(emptyList(), tagName)
            }
        }

    @Test
    fun `setTagAlbumBrowsingSession uses full URI if lastPathSegment is null`() =
        runTest {
            // Given
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns "content://full/path"
            every { uri.lastPathSegment } returns null
            val tagName = "Test"

            // When
            viewModel.setTagAlbumBrowsingSession(listOf(uri), tagName)

            // Then
            verify {
                imageBrowserRepository.setTagAlbum(
                    match { photos ->
                        photos.size == 1 &&
                            photos[0].photoId == "content://full/path"
                    },
                    tagName,
                )
            }
        }

    // ========== setLocalAlbumBrowsingSession Tests ==========

    @Test
    fun `setLocalAlbumBrowsingSession converts URIs to Photos and calls repository`() =
        runTest {
            // Given
            val uri1 = createMockUri("300")
            val uri2 = createMockUri("400")
            val uris = listOf(uri1, uri2)
            val albumName = "Summer 2024"

            // When
            viewModel.setLocalAlbumBrowsingSession(uris, albumName)

            // Then
            verify {
                imageBrowserRepository.setLocalAlbum(
                    match { photos ->
                        photos.size == 2 &&
                            photos[0].photoId == "300" &&
                            photos[0].contentUri == uri1 &&
                            photos[1].photoId == "400" &&
                            photos[1].contentUri == uri2
                    },
                    albumName,
                )
            }
        }

    @Test
    fun `setLocalAlbumBrowsingSession with empty list calls repository with empty list`() =
        runTest {
            // Given
            val albumName = "Empty Album"

            // When
            viewModel.setLocalAlbumBrowsingSession(emptyList(), albumName)

            // Then
            verify {
                imageBrowserRepository.setLocalAlbum(emptyList(), albumName)
            }
        }

    @Test
    fun `setLocalAlbumBrowsingSession uses full URI if lastPathSegment is null`() =
        runTest {
            // Given
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns "content://custom/uri"
            every { uri.lastPathSegment } returns null
            val albumName = "Test Album"

            // When
            viewModel.setLocalAlbumBrowsingSession(listOf(uri), albumName)

            // Then
            verify {
                imageBrowserRepository.setLocalAlbum(
                    match { photos ->
                        photos.size == 1 &&
                            photos[0].photoId == "content://custom/uri"
                    },
                    albumName,
                )
            }
        }

    // ========== setGalleryBrowsingSession Tests ==========

    @Test
    fun `setGalleryBrowsingSession converts URIs to Photos and calls repository`() =
        runTest {
            // Given
            val uri1 = createMockUri("500")
            val uri2 = createMockUri("600")
            val uri3 = createMockUri("700")
            val uris = listOf(uri1, uri2, uri3)

            // When
            viewModel.setGalleryBrowsingSession(uris)

            // Then
            verify {
                imageBrowserRepository.setGallery(
                    match { photos ->
                        photos.size == 3 &&
                            photos[0].photoId == "500" &&
                            photos[0].contentUri == uri1 &&
                            photos[1].photoId == "600" &&
                            photos[1].contentUri == uri2 &&
                            photos[2].photoId == "700" &&
                            photos[2].contentUri == uri3
                    },
                )
            }
        }

    @Test
    fun `setGalleryBrowsingSession with empty list calls repository with empty list`() =
        runTest {
            // When
            viewModel.setGalleryBrowsingSession(emptyList())

            // Then
            verify {
                imageBrowserRepository.setGallery(emptyList())
            }
        }

    @Test
    fun `setGalleryBrowsingSession uses full URI if lastPathSegment is null`() =
        runTest {
            // Given
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns "content://gallery/image"
            every { uri.lastPathSegment } returns null

            // When
            viewModel.setGalleryBrowsingSession(listOf(uri))

            // Then
            verify {
                imageBrowserRepository.setGallery(
                    match { photos ->
                        photos.size == 1 &&
                            photos[0].photoId == "content://gallery/image"
                    },
                )
            }
        }

    // ========== Integration Tests ==========

    @Test
    fun `multiple operations maintain separate state`() =
        runTest {
            // Given
            val images = listOf(createMockUri("1"))
            val albums = listOf(Album(1L, "Test", createMockUri("2")))
            val albumImages = listOf(createMockUri("3"))

            coEvery { localRepository.getImages() } returns images
            coEvery { localRepository.getAlbums() } returns albums
            coEvery { localRepository.getImagesForAlbum(1L) } returns albumImages

            // When
            viewModel.getImages()
            viewModel.getAlbums()
            viewModel.getImagesForAlbum(1L)
            Thread.sleep(100)

            // Then - each state is independent
            assertEquals(images, viewModel.image.value)
            assertEquals(albums, viewModel.albums.value)
            assertEquals(albumImages, viewModel.imagesInAlbum.value)
        }

    @Test
    fun `browsing session setters can be called in sequence`() =
        runTest {
            // Given
            val uri = createMockUri("123")
            val uris = listOf(uri)

            // When - call all three session setters
            viewModel.setTagAlbumBrowsingSession(uris, "Tag")
            viewModel.setLocalAlbumBrowsingSession(uris, "Album")
            viewModel.setGalleryBrowsingSession(uris)

            // Then - all three methods called
            verify(exactly = 1) { imageBrowserRepository.setTagAlbum(any(), "Tag") }
            verify(exactly = 1) { imageBrowserRepository.setLocalAlbum(any(), "Album") }
            verify(exactly = 1) { imageBrowserRepository.setGallery(any()) }
        }
}
