package com.example.momentag.viewmodel

import android.net.Uri
import android.os.Build
import com.example.momentag.model.Album
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class LocalViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: LocalViewModel
    private lateinit var localRepository: LocalRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var albumUploadSuccessEvent: MutableSharedFlow<Long>

    @Before
    fun setUp() {
        localRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)
        albumUploadSuccessEvent = MutableSharedFlow()

        viewModel =
            LocalViewModel(
                localRepository,
                imageBrowserRepository,
                albumUploadSuccessEvent,
            )
    }

    private fun createMockPhoto(
        id: String,
        uri: String = "content://media/$id",
    ): Photo {
        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.toString() } returns uri
        return Photo(
            photoId = id,
            contentUri = mockUri,
            createdAt = "2023-01-01",
        )
    }

    private fun createMockAlbum(
        id: Long,
        name: String,
    ): Album =
        Album(
            albumId = id,
            albumName = name,
            thumbnailUri = mockk<Uri>(relaxed = true),
        )

    // --- Photo Selection Tests ---

    @Test
    fun `togglePhotoSelection adds photo when not selected`() =
        runTest {
            val photo = createMockPhoto("p1")

            viewModel.togglePhotoSelection(photo)
            advanceUntilIdle()

            assertTrue(viewModel.selectedPhotosInAlbum.value.containsKey(photo.contentUri))
            assertEquals(photo, viewModel.selectedPhotosInAlbum.value[photo.contentUri])
        }

    @Test
    fun `togglePhotoSelection removes photo when already selected`() =
        runTest {
            val photo = createMockPhoto("p1")

            viewModel.togglePhotoSelection(photo)
            advanceUntilIdle()
            assertTrue(viewModel.selectedPhotosInAlbum.value.containsKey(photo.contentUri))

            viewModel.togglePhotoSelection(photo)
            advanceUntilIdle()
            assertFalse(viewModel.selectedPhotosInAlbum.value.containsKey(photo.contentUri))
        }

    @Test
    fun `togglePhotoSelection handles multiple photos`() =
        runTest {
            val photo1 = createMockPhoto("p1", "uri1")
            val photo2 = createMockPhoto("p2", "uri2")

            viewModel.togglePhotoSelection(photo1)
            viewModel.togglePhotoSelection(photo2)
            advanceUntilIdle()

            assertEquals(2, viewModel.selectedPhotosInAlbum.value.size)
            assertTrue(viewModel.selectedPhotosInAlbum.value.containsKey(photo1.contentUri))
            assertTrue(viewModel.selectedPhotosInAlbum.value.containsKey(photo2.contentUri))
        }

    @Test
    fun `clearPhotoSelection clears all selected photos`() =
        runTest {
            val photo1 = createMockPhoto("p1", "uri1")
            val photo2 = createMockPhoto("p2", "uri2")

            viewModel.togglePhotoSelection(photo1)
            viewModel.togglePhotoSelection(photo2)
            advanceUntilIdle()
            assertEquals(2, viewModel.selectedPhotosInAlbum.value.size)

            viewModel.clearPhotoSelection()
            advanceUntilIdle()

            assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
        }

    // --- Album Loading Tests ---

    @Test
    fun `getAlbums loads albums from repository`() =
        runTest {
            val albums =
                listOf(
                    createMockAlbum(1L, "Album1"),
                    createMockAlbum(2L, "Album2"),
                )

            coEvery { localRepository.getAlbums() } returns albums

            viewModel.getAlbums()
            advanceUntilIdle()

            assertEquals(albums, viewModel.albums.value)
            coVerify { localRepository.getAlbums() }
        }

    @Test
    fun `getImagesForAlbum loads images and clears selection`() =
        runTest {
            val photo = createMockPhoto("p1")
            val albumId = 1L
            val photos = listOf(photo)

            // First select a photo
            viewModel.togglePhotoSelection(photo)
            advanceUntilIdle()
            assertTrue(viewModel.selectedPhotosInAlbum.value.isNotEmpty())

            coEvery { localRepository.getImagesForAlbum(albumId) } returns photos

            viewModel.getImagesForAlbum(albumId)
            advanceUntilIdle()

            assertEquals(photos, viewModel.imagesInAlbum.value)
            assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
            coVerify { localRepository.getImagesForAlbum(albumId) }
        }

    // --- Paginated Album Loading Tests ---

    @Test
    fun `loadAlbumPhotos loads first page and clears selection`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"
            val photos = List(66) { createMockPhoto("p$it") }

            // First select a photo
            val selectedPhoto = createMockPhoto("selected")
            viewModel.togglePhotoSelection(selectedPhoto)
            advanceUntilIdle()
            assertTrue(viewModel.selectedPhotosInAlbum.value.isNotEmpty())

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns photos

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            assertEquals(photos, viewModel.imagesInAlbum.value)
            assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
            verify { imageBrowserRepository.setLocalAlbum(photos, albumName) }
        }

    @Test
    fun `loadAlbumPhotos sets hasMorePhotos correctly when full page`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"
            val photos = List(66) { createMockPhoto("p$it") }

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns photos

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            // Try to load more - should call repository
            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            } returns emptyList()

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            coVerify {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            }
        }

    @Test
    fun `loadAlbumPhotos sets hasMorePhotos to false when partial page`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"
            val photos = List(30) { createMockPhoto("p$it") } // Less than pageSize

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns photos

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            assertEquals(photos, viewModel.imagesInAlbum.value)

            // Try to load more - should NOT call repository
            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            } returns emptyList()

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Verify repository was NOT called (because hasMorePhotos = false)
            coVerify(exactly = 0) {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            }
        }

    @Test
    fun `loadMorePhotos appends photos to existing list`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"
            val firstPage = List(66) { createMockPhoto("p$it") }
            val secondPage = List(66) { createMockPhoto("p${it + 66}") }

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns firstPage

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            } returns secondPage

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            assertEquals(132, viewModel.imagesInAlbum.value.size)
            assertTrue(viewModel.imagesInAlbum.value.containsAll(firstPage))
            assertTrue(viewModel.imagesInAlbum.value.containsAll(secondPage))
        }

    @Test
    fun `loadMorePhotos does nothing when already loading`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"
            val firstPage = List(66) { createMockPhoto("p$it") }

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns firstPage

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            } coAnswers {
                // Simulate slow loading
                kotlinx.coroutines.delay(1000)
                emptyList()
            }

            // Start first load
            val job1 = launch { viewModel.loadMorePhotos() }
            // Try to start second load immediately
            val job2 = launch { viewModel.loadMorePhotos() }

            advanceUntilIdle()
            job1.cancel()
            job2.cancel()

            // Should only be called once
            coVerify(exactly = 1) {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            }
        }

    @Test
    fun `loadMorePhotos does nothing when currentAlbumId is null`() =
        runTest {
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            coVerify(exactly = 0) {
                localRepository.getImagesForAlbumPaginated(any(), any(), any())
            }
        }

    @Test
    fun `loadMorePhotos updates ImageBrowserRepository with full list`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"
            val firstPage = List(66) { createMockPhoto("p$it") }
            val secondPage = List(10) { createMockPhoto("p${it + 66}") }

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns firstPage

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            } returns secondPage

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Verify that ImageBrowserRepository was updated with the full list
            verify {
                imageBrowserRepository.setLocalAlbum(
                    match { it.size == 76 },
                    albumName,
                )
            }
        }

    // --- Album Selection Tests ---

    @Test
    fun `toggleAlbumSelection adds album when not selected`() =
        runTest {
            val albumId = 1L

            viewModel.toggleAlbumSelection(albumId)
            advanceUntilIdle()

            assertTrue(viewModel.selectedAlbumIds.value.contains(albumId))
        }

    @Test
    fun `toggleAlbumSelection removes album when already selected`() =
        runTest {
            val albumId = 1L

            viewModel.toggleAlbumSelection(albumId)
            advanceUntilIdle()
            assertTrue(viewModel.selectedAlbumIds.value.contains(albumId))

            viewModel.toggleAlbumSelection(albumId)
            advanceUntilIdle()
            assertFalse(viewModel.selectedAlbumIds.value.contains(albumId))
        }

    @Test
    fun `selectAllAlbums selects all album IDs`() =
        runTest {
            val albums =
                listOf(
                    createMockAlbum(1L, "Album1"),
                    createMockAlbum(2L, "Album2"),
                    createMockAlbum(3L, "Album3"),
                )

            viewModel.selectAllAlbums(albums)
            advanceUntilIdle()

            assertEquals(3, viewModel.selectedAlbumIds.value.size)
            assertTrue(viewModel.selectedAlbumIds.value.contains(1L))
            assertTrue(viewModel.selectedAlbumIds.value.contains(2L))
            assertTrue(viewModel.selectedAlbumIds.value.contains(3L))
        }

    @Test
    fun `clearAlbumSelection clears all selected albums`() =
        runTest {
            viewModel.toggleAlbumSelection(1L)
            viewModel.toggleAlbumSelection(2L)
            advanceUntilIdle()
            assertEquals(2, viewModel.selectedAlbumIds.value.size)

            viewModel.clearAlbumSelection()
            advanceUntilIdle()

            assertTrue(viewModel.selectedAlbumIds.value.isEmpty())
        }

    // --- Scroll Position Tests ---

    @Test
    fun `setScrollToIndex updates scroll position`() =
        runTest {
            viewModel.setScrollToIndex(42)
            advanceUntilIdle()

            assertEquals(42, viewModel.scrollToIndex.value)
        }

    @Test
    fun `clearScrollToIndex resets scroll position`() =
        runTest {
            viewModel.setScrollToIndex(42)
            advanceUntilIdle()
            assertEquals(42, viewModel.scrollToIndex.value)

            viewModel.clearScrollToIndex()
            advanceUntilIdle()

            assertNull(viewModel.scrollToIndex.value)
        }

    @Test
    fun `restoreScrollPosition gets index from ImageBrowserRepository`() =
        runTest {
            every { imageBrowserRepository.getCurrentIndex() } returns 100

            viewModel.restoreScrollPosition()
            advanceUntilIdle()

            assertEquals(100, viewModel.scrollToIndex.value)
            verify { imageBrowserRepository.getCurrentIndex() }
        }

    @Test
    fun `restoreScrollPosition does nothing when getCurrentIndex returns null`() =
        runTest {
            every { imageBrowserRepository.getCurrentIndex() } returns null

            viewModel.restoreScrollPosition()
            advanceUntilIdle()

            assertNull(viewModel.scrollToIndex.value)
        }

    // --- Browser Session Tests ---

    @Test
    fun `setLocalAlbumBrowsingSession delegates to ImageBrowserRepository`() =
        runTest {
            val photos = listOf(createMockPhoto("p1"), createMockPhoto("p2"))
            val albumName = "TestAlbum"

            viewModel.setLocalAlbumBrowsingSession(photos, albumName)
            advanceUntilIdle()

            verify { imageBrowserRepository.setLocalAlbum(photos, albumName) }
        }

    // --- Album Upload Success Event Tests ---

    @Test
    fun `albumUploadSuccessEvent with 0L clears photo selection`() =
        runTest {
            // Select some photos
            val photo1 = createMockPhoto("p1", "uri1")
            val photo2 = createMockPhoto("p2", "uri2")
            viewModel.togglePhotoSelection(photo1)
            viewModel.togglePhotoSelection(photo2)
            advanceUntilIdle()
            assertEquals(2, viewModel.selectedPhotosInAlbum.value.size)

            // Emit upload success event with 0L
            albumUploadSuccessEvent.emit(0L)
            advanceUntilIdle()

            // Photo selection should be cleared
            assertTrue(viewModel.selectedPhotosInAlbum.value.isEmpty())
        }

    @Test
    fun `albumUploadSuccessEvent with album ID removes from selected albums`() =
        runTest {
            // Select some albums
            viewModel.toggleAlbumSelection(1L)
            viewModel.toggleAlbumSelection(2L)
            viewModel.toggleAlbumSelection(3L)
            advanceUntilIdle()
            assertEquals(3, viewModel.selectedAlbumIds.value.size)

            // Emit upload success event for album 2L
            albumUploadSuccessEvent.emit(2L)
            advanceUntilIdle()

            // Album 2L should be removed from selection
            assertEquals(2, viewModel.selectedAlbumIds.value.size)
            assertTrue(viewModel.selectedAlbumIds.value.contains(1L))
            assertFalse(viewModel.selectedAlbumIds.value.contains(2L))
            assertTrue(viewModel.selectedAlbumIds.value.contains(3L))
        }

    @Test
    fun `albumUploadSuccessEvent handles multiple events`() =
        runTest {
            // Select albums
            viewModel.toggleAlbumSelection(1L)
            viewModel.toggleAlbumSelection(2L)
            viewModel.toggleAlbumSelection(3L)
            advanceUntilIdle()

            // Emit multiple success events
            albumUploadSuccessEvent.emit(1L)
            advanceUntilIdle()
            albumUploadSuccessEvent.emit(3L)
            advanceUntilIdle()

            // Only album 2L should remain
            assertEquals(1, viewModel.selectedAlbumIds.value.size)
            assertTrue(viewModel.selectedAlbumIds.value.contains(2L))
        }

    @Test
    fun `loadAlbumPhotos does not set browser session when photos are empty`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns emptyList()

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            // Should not call setLocalAlbum when photos are empty
            verify(exactly = 0) {
                imageBrowserRepository.setLocalAlbum(any(), any())
            }
        }

    @Test
    fun `isLoadingMorePhotos is false initially`() =
        runTest {
            assertFalse(viewModel.isLoadingMorePhotos.value)
        }

    @Test
    fun `isLoadingMorePhotos is false after loading completes`() =
        runTest {
            val albumId = 1L
            val albumName = "TestAlbum"
            val firstPage = List(66) { createMockPhoto("p$it") }

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 0,
                )
            } returns firstPage

            viewModel.loadAlbumPhotos(albumId, albumName)
            advanceUntilIdle()

            coEvery {
                localRepository.getImagesForAlbumPaginated(
                    albumId = albumId,
                    limit = 66,
                    offset = 66,
                )
            } returns emptyList()

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Should be false after loading completes
            assertFalse(viewModel.isLoadingMorePhotos.value)
        }
}
