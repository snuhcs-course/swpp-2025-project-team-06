package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.TagResponse
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
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
class HomeViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var recommendRepository: RecommendRepository
    private lateinit var photoSelectionRepository: PhotoSelectionRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        localRepository = mockk(relaxed = true)
        remoteRepository = mockk()
        recommendRepository = mockk(relaxed = true)
        photoSelectionRepository = mockk()
        imageBrowserRepository = mockk(relaxed = true)

        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyList())

        viewModel =
            HomeViewModel(
                localRepository,
                remoteRepository,
                recommendRepository,
                photoSelectionRepository,
                imageBrowserRepository,
            )
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Uri::class)
        // Reset the static flag using the dedicated method
        HomeViewModel.resetStoriesGeneratedFlag()
    }

    private fun createMockUri(path: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns path
        every { uri.lastPathSegment } returns path.substringAfterLast("/")
        return uri
    }

    // Helper functions
    private fun createTagResponse(
        id: String = "tag1",
        name: String = "TestTag",
        photoCount: Int = 5,
    ) = TagResponse(
        tagId = id,
        tagName = name,
        photoCount = photoCount,
        thumbnailPhotoPathId = 1L,
        createdAt = "2025-01-01T00:00:00Z",
        updatedAt = "2025-01-01T00:00:00Z",
    )

    private fun createPhotoResponse(id: String = "photo1") =
        PhotoResponse(
            photoId = id,
            photoPathId = 1L,
            createdAt = "2025-01-01T00:00:00.000000Z",
        )

    private fun createPhoto(id: String = "photo1"): Photo {
        val uri = createMockUri("content://media/external/images/media/$id")
        every { Uri.parse("content://media/external/images/media/$id") } returns uri
        return Photo(
            photoId = id,
            contentUri = uri,
            createdAt = "2025-01-01T00:00:00.000000Z",
        )
    }

    // Selection mode tests
    @Test
    fun `setSelectionMode updates isSelectionMode state`() {
        // When
        viewModel.setSelectionMode(true)

        // Then
        assertTrue(viewModel.isSelectionMode.value)

        // When
        viewModel.setSelectionMode(false)

        // Then
        assertFalse(viewModel.isSelectionMode.value)
    }

    // Show all photos tests
    @Test
    fun `setIsShowingAllPhotos updates isShowingAllPhotos state`() {
        // When
        viewModel.setIsShowingAllPhotos(true)

        // Then
        assertTrue(viewModel.isShowingAllPhotos.value)

        // When
        viewModel.setIsShowingAllPhotos(false)

        // Then
        assertFalse(viewModel.isShowingAllPhotos.value)
    }

    // Tag loading tests
    @Test
    fun `loadServerTags success updates state with tags`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "Tag 1", 5),
                    createTagResponse("tag2", "Tag 2", 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.homeLoadingState.value
            assertTrue(state is HomeViewModel.HomeLoadingState.Success)
            assertEquals(2, (state as HomeViewModel.HomeLoadingState.Success).tags.size)
        }

    @Test
    fun `loadServerTags error updates state with error message`() =
        runTest {
            // Given
            val errorMessage = "Network error"
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.homeLoadingState.value
            assertTrue(state is HomeViewModel.HomeLoadingState.Error)
            assertEquals(errorMessage, (state as HomeViewModel.HomeLoadingState.Error).message)
        }

    @Test
    fun `loadServerTags unauthorized updates state with error`() =
        runTest {
            // Given
            val errorMessage = "Unauthorized"
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.homeLoadingState.value
            assertTrue(state is HomeViewModel.HomeLoadingState.Error)
        }

    // Tag sorting tests
    @Test
    fun `setSortOrder updates sortOrder and re-sorts tags`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "B Tag", 10),
                    createTagResponse("tag2", "A Tag", 5),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)
            viewModel.loadServerTags()
            advanceUntilIdle()

            // When - sort by name ascending
            viewModel.setSortOrder(TagSortOrder.NAME_ASC)

            // Then
            assertEquals(TagSortOrder.NAME_ASC, viewModel.sortOrder.value)
            val state = viewModel.homeLoadingState.value
            assertTrue(state is HomeViewModel.HomeLoadingState.Success)
            val sortedTags = (state as HomeViewModel.HomeLoadingState.Success).tags
            assertEquals("A Tag", sortedTags[0].tagName)
            assertEquals("B Tag", sortedTags[1].tagName)
        }

    @Test
    fun `setSortOrder NAME_DESC sorts by name descending`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "A Tag", 10),
                    createTagResponse("tag2", "B Tag", 5),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)
            viewModel.loadServerTags()
            advanceUntilIdle()

            // When
            viewModel.setSortOrder(TagSortOrder.NAME_DESC)

            // Then
            val state = viewModel.homeLoadingState.value as HomeViewModel.HomeLoadingState.Success
            assertEquals("B Tag", state.tags[0].tagName)
            assertEquals("A Tag", state.tags[1].tagName)
        }

    @Test
    fun `setSortOrder COUNT_ASC sorts by photo count ascending`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "Tag 1", 10),
                    createTagResponse("tag2", "Tag 2", 5),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)
            viewModel.loadServerTags()
            advanceUntilIdle()

            // When
            viewModel.setSortOrder(TagSortOrder.COUNT_ASC)

            // Then
            val state = viewModel.homeLoadingState.value as HomeViewModel.HomeLoadingState.Success
            assertEquals(5, state.tags[0].photoCount)
            assertEquals(10, state.tags[1].photoCount)
        }

    @Test
    fun `setSortOrder COUNT_DESC sorts by photo count descending`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "Tag 1", 5),
                    createTagResponse("tag2", "Tag 2", 10),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)
            viewModel.loadServerTags()
            advanceUntilIdle()

            // When
            viewModel.setSortOrder(TagSortOrder.COUNT_DESC)

            // Then
            val state = viewModel.homeLoadingState.value as HomeViewModel.HomeLoadingState.Success
            assertEquals(10, state.tags[0].photoCount)
            assertEquals(5, state.tags[1].photoCount)
        }

    // Delete tag tests
    @Test
    fun `deleteTag success updates state to Success`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { remoteRepository.removeTag(tagId) } returns RemoteRepository.Result.Success(Unit)

            // When
            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Success)
            coVerify { remoteRepository.removeTag(tagId) }
        }

    @Test
    fun `deleteTag error updates state with error message`() =
        runTest {
            // Given
            val tagId = "tag1"
            val errorMessage = "Failed to delete"
            coEvery { remoteRepository.removeTag(tagId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.homeDeleteState.value
            assertTrue(state is HomeViewModel.HomeDeleteState.Error)
            assertEquals(errorMessage, (state as HomeViewModel.HomeDeleteState.Error).message)
        }

    @Test
    fun `resetDeleteState resets state to Idle`() =
        runTest {
            // Given
            coEvery { remoteRepository.removeTag(any()) } returns RemoteRepository.Result.Success(Unit)
            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            // When
            viewModel.resetDeleteState()

            // Then
            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Idle)
        }

    // Photo loading tests
    @Test
    fun `loadAllPhotos success loads photos and groups by date`() =
        runTest {
            // Given
            val photoResponses =
                listOf(
                    createPhotoResponse("photo1"),
                    createPhotoResponse("photo2"),
                )
            val photos =
                listOf(
                    createPhoto("photo1"),
                    createPhoto("photo2"),
                )
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.loadAllPhotos()
            advanceUntilIdle()

            // Then
            assertEquals(2, viewModel.allPhotos.value.size)
            assertFalse(viewModel.isLoadingPhotos.value)
            verify { imageBrowserRepository.setGallery(photos) }
        }

    @Test
    fun `loadAllPhotos error clears photos`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns
                RemoteRepository.Result.Error(500, "Error")

            // When
            viewModel.loadAllPhotos()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.allPhotos.value.isEmpty())
            assertFalse(viewModel.isLoadingPhotos.value)
        }

    @Test
    fun `loadMorePhotos loads additional photos`() =
        runTest {
            // Given - initial load with exactly 66 photos to enable "hasMorePhotos"
            val initialPhotos = (1..66).map { createPhotoResponse("photo$it") }
            val mappedInitialPhotos = (1..66).map { createPhoto("photo$it") }
            coEvery { remoteRepository.getAllPhotos(66, 0) } returns
                RemoteRepository.Result.Success(initialPhotos)
            coEvery { localRepository.toPhotos(initialPhotos) } returns mappedInitialPhotos
            viewModel.loadAllPhotos()
            advanceUntilIdle()

            // When - load more
            val morePhotos = listOf(createPhotoResponse("photo67"))
            val mappedMorePhotos = listOf(createPhoto("photo67"))
            coEvery { remoteRepository.getAllPhotos(66, 66) } returns
                RemoteRepository.Result.Success(morePhotos)
            coEvery { localRepository.toPhotos(morePhotos) } returns mappedMorePhotos
            viewModel.loadMorePhotos()
            advanceUntilIdle()

            // Then
            assertEquals(67, viewModel.allPhotos.value.size)
            assertFalse(viewModel.isLoadingMorePhotos.value)
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
    fun `resetSelection delegates to photoSelectionRepository`() {
        // Given
        every { photoSelectionRepository.clear() } returns Unit

        // When
        viewModel.resetSelection()

        // Then
        verify { photoSelectionRepository.clear() }
    }

    @Test
    fun `getPhotosToShare returns selected photos`() {
        // Given
        val photos =
            listOf(
                createPhoto("photo1"),
                createPhoto("photo2"),
            )
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)
        val newViewModel =
            HomeViewModel(
                localRepository,
                remoteRepository,
                recommendRepository,
                photoSelectionRepository,
                imageBrowserRepository,
            )

        // When
        val result = newViewModel.getPhotosToShare()

        // Then
        assertEquals(photos, result)
    }

    // Scroll position tests
    @Test
    fun `setAllPhotosScrollPosition updates scroll index and offset`() {
        // When
        viewModel.setAllPhotosScrollPosition(10, 50)

        // Then
        assertEquals(10, viewModel.allPhotosScrollIndex.value)
        assertEquals(50, viewModel.allPhotosScrollOffset.value)
    }

    @Test
    fun `setTagAlbumScrollPosition updates scroll index and offset`() {
        // When
        viewModel.setTagAlbumScrollPosition(5, 25)

        // Then
        assertEquals(5, viewModel.tagAlbumScrollIndex.value)
        assertEquals(25, viewModel.tagAlbumScrollOffset.value)
    }

    @Test
    fun `setShouldReturnToAllPhotos updates state`() {
        // When
        viewModel.setShouldReturnToAllPhotos(true)

        // Then
        assertTrue(viewModel.shouldReturnToAllPhotos.value)

        // When
        viewModel.setShouldReturnToAllPhotos(false)

        // Then
        assertFalse(viewModel.shouldReturnToAllPhotos.value)
    }

    // Gallery browsing session
    @Test
    fun `setGalleryBrowsingSession updates imageBrowserRepository`() {
        // Given
        val photos = listOf(createPhoto())
        coEvery { remoteRepository.getAllPhotos(any(), any()) } returns
            RemoteRepository.Result.Success(listOf(createPhotoResponse()))
        coEvery { localRepository.toPhotos(any()) } returns photos

        runTest {
            viewModel.loadAllPhotos()
            advanceUntilIdle()

            // When
            viewModel.setGalleryBrowsingSession()

            // Then
            verify(atLeast = 1) { imageBrowserRepository.setGallery(photos) }
        }
    }

    // Story pre-generation test
    @Test
    fun `preGenerateStoriesOnce calls recommendRepository`() =
        runTest {
            // Given
            coEvery { recommendRepository.generateStories(20) } returns RecommendRepository.StoryResult.Success(Unit)

            // When
            viewModel.preGenerateStoriesOnce()
            advanceUntilIdle()

            // Then
            coVerify { recommendRepository.generateStories(20) }
        }

    @Test
    fun `preGenerateStoriesOnce only generates once per session`() =
        runTest {
            // Given
            coEvery { recommendRepository.generateStories(20) } returns RecommendRepository.StoryResult.Success(Unit)

            // When
            viewModel.preGenerateStoriesOnce()
            advanceUntilIdle()
            viewModel.preGenerateStoriesOnce()
            advanceUntilIdle()

            // Then - should only be called once despite two invocations
            coVerify(exactly = 1) { recommendRepository.generateStories(20) }
        }
}
