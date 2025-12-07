package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.TagItem
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.TagStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var tagStateRepository: TagStateRepository
    private lateinit var sortPreferences: com.example.momentag.data.SortPreferences

    @Before
    fun setUp() {
        localRepository = mockk(relaxed = true)
        remoteRepository = mockk(relaxed = true)
        recommendRepository = mockk(relaxed = true)
        photoSelectionRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)
        tagStateRepository = mockk(relaxed = true)
        sortPreferences = mockk(relaxed = true)

        // Mock TagStateRepository loadingState flow
        every { tagStateRepository.loadingState } returns MutableStateFlow(TagStateRepository.LoadingState.Idle)
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyMap())
        // Mock sortPreferences
        every { sortPreferences.getSortOrder() } returns TagSortOrder.CREATED_DESC

        viewModel =
            HomeViewModel(
                localRepository,
                remoteRepository,
                recommendRepository,
                photoSelectionRepository,
                imageBrowserRepository,
                tagStateRepository,
                sortPreferences,
            )

        // Reset static flag for stories
        HomeViewModel.resetStoriesGeneratedFlag()
    }

    private fun createMockPhoto(
        id: String,
        date: String = "2023-01-01",
    ): Photo =
        Photo(
            photoId = id,
            contentUri = mockk<Uri>(relaxed = true),
            createdAt = date,
        )

    // --- Photo Loading Tests ---

    @Test
    fun `loadAllPhotos success updates state`() =
        runTest {
            val photoResponses = listOf(mockk<PhotoResponse>())
            val photos = listOf(createMockPhoto("p1"))

            coEvery { remoteRepository.getAllPhotos(limit = 66, offset = 0) } returns RemoteRepository.Result.Success(photoResponses)
            every { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.loadAllPhotos()
            advanceUntilIdle()

            assertEquals(photos, viewModel.allPhotos.value)
            assertEquals(1, viewModel.groupedPhotos.value.size)
            assertFalse(viewModel.isLoadingPhotos.value)
        }

    @Test
    fun `loadAllPhotos error sets empty list`() =
        runTest {
            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns RemoteRepository.Result.Error(500, "Error")

            viewModel.loadAllPhotos()
            advanceUntilIdle()

            assertTrue(viewModel.allPhotos.value.isEmpty())
            assertFalse(viewModel.isLoadingPhotos.value)
        }

    @Test
    fun `loadMorePhotos appends photos`() =
        runTest {
            // Initial load
            val initialPhotos = List(66) { createMockPhoto("p$it") }
            val initialResponses = List(66) { mockk<PhotoResponse>() }

            coEvery { remoteRepository.getAllPhotos(limit = 66, offset = 0) } returns RemoteRepository.Result.Success(initialResponses)
            every { localRepository.toPhotos(initialResponses) } returns initialPhotos

            viewModel.loadAllPhotos()
            advanceUntilIdle()

            // Load more
            val newPhotos = listOf(createMockPhoto("p_new"))
            val newResponses = listOf(mockk<PhotoResponse>())

            coEvery { remoteRepository.getAllPhotos(limit = 66, offset = 66) } returns RemoteRepository.Result.Success(newResponses)
            every { localRepository.toPhotos(newResponses) } returns newPhotos

            viewModel.loadMorePhotos()
            advanceUntilIdle()

            assertEquals(67, viewModel.allPhotos.value.size)
            assertTrue(viewModel.allPhotos.value.containsAll(newPhotos))
        }

    // --- Tag Management Tests ---

    @Test
    fun `loadServerTags delegates to repository`() =
        runTest {
            viewModel.loadServerTags()
            advanceUntilIdle()
            coVerify { tagStateRepository.loadTags() }
        }

    @Test
    fun `deleteTag success updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { tagStateRepository.deleteTag(tagId) } returns RemoteRepository.Result.Success(Unit)

            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            assertEquals(HomeViewModel.HomeDeleteState.Success, viewModel.homeDeleteState.value)
        }

    @Test
    fun `deleteTag unauthorized updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { tagStateRepository.deleteTag(tagId) } returns RemoteRepository.Result.Unauthorized("401")

            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Error)
            assertEquals(
                HomeViewModel.HomeError.Unauthorized,
                (viewModel.homeDeleteState.value as HomeViewModel.HomeDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTag network error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { tagStateRepository.deleteTag(tagId) } returns RemoteRepository.Result.NetworkError("Error")

            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Error)
            assertEquals(
                HomeViewModel.HomeError.NetworkError,
                (viewModel.homeDeleteState.value as HomeViewModel.HomeDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTag bad request updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { tagStateRepository.deleteTag(tagId) } returns RemoteRepository.Result.BadRequest("Error")

            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Error)
            assertEquals(
                HomeViewModel.HomeError.UnknownError,
                (viewModel.homeDeleteState.value as HomeViewModel.HomeDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTag exception updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { tagStateRepository.deleteTag(tagId) } returns RemoteRepository.Result.Exception(Exception("Error"))

            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Error)
            assertEquals(
                HomeViewModel.HomeError.UnknownError,
                (viewModel.homeDeleteState.value as HomeViewModel.HomeDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTag unknown error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { tagStateRepository.deleteTag(tagId) } returns RemoteRepository.Result.Error(500, "Error")

            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            assertTrue(viewModel.homeDeleteState.value is HomeViewModel.HomeDeleteState.Error)
            assertEquals(
                HomeViewModel.HomeError.UnknownError,
                (viewModel.homeDeleteState.value as HomeViewModel.HomeDeleteState.Error).error,
            )
        }

    @Test
    fun `resetDeleteState resets to Idle`() {
        viewModel.resetDeleteState()
        assertEquals(HomeViewModel.HomeDeleteState.Idle, viewModel.homeDeleteState.value)
    }

    // --- UI State Tests ---

    @Test
    fun `setSortOrder updates state`() {
        viewModel.setSortOrder(TagSortOrder.NAME_ASC)
        assertEquals(TagSortOrder.NAME_ASC, viewModel.sortOrder.value)
    }

    @Test
    fun `selection mode toggles correctly`() {
        viewModel.setSelectionMode(true)
        assertTrue(viewModel.isSelectionMode.value)

        viewModel.setSelectionMode(false)
        assertFalse(viewModel.isSelectionMode.value)
    }

    @Test
    fun `setIsShowingAllPhotos updates state`() {
        viewModel.setIsShowingAllPhotos(true)
        assertTrue(viewModel.isShowingAllPhotos.value)
    }

    @Test
    fun `setShouldReturnToAllPhotos updates state`() {
        viewModel.setShouldReturnToAllPhotos(true)
        assertTrue(viewModel.shouldReturnToAllPhotos.value)
    }

    // --- Scroll Position Tests ---

    @Test
    fun `scroll position setters update state`() {
        viewModel.setAllPhotosScrollPosition(10, 20)
        assertEquals(10, viewModel.allPhotosScrollIndex.value)
        assertEquals(20, viewModel.allPhotosScrollOffset.value)

        viewModel.setTagAlbumScrollPosition(5, 15)
        assertEquals(5, viewModel.tagAlbumScrollIndex.value)
        assertEquals(15, viewModel.tagAlbumScrollOffset.value)
    }

    @Test
    fun `setScrollToIndex updates state`() {
        viewModel.setScrollToIndex(100)
        assertEquals(100, viewModel.scrollToIndex.value)
    }

    @Test
    fun `clearScrollToIndex resets state`() {
        viewModel.setScrollToIndex(100)
        viewModel.clearScrollToIndex()
        assertNull(viewModel.scrollToIndex.value)
    }

    @Test
    fun `restoreScrollPosition calculates index correctly`() =
        runTest {
            // Setup photos and groups
            val photo1 = createMockPhoto("p1", "2023-01-01")
            val photo2 = createMockPhoto("p2", "2023-01-01")
            val photos = listOf(photo1, photo2)
            val responses = listOf(mockk<PhotoResponse>(), mockk<PhotoResponse>())

            coEvery { remoteRepository.getAllPhotos(any(), any()) } returns RemoteRepository.Result.Success(responses)
            every { localRepository.toPhotos(responses) } returns photos

            viewModel.loadAllPhotos()
            advanceUntilIdle()

            // Mock last viewed index as 1 (photo2)
            every { imageBrowserRepository.getCurrentIndex() } returns 1

            viewModel.restoreScrollPosition()

            // Header + index in group (1) = 1 + 1 = 2
            assertEquals(2, viewModel.scrollToIndex.value)
        }

    // --- Story Generation Tests ---

    @Test
    fun `preGenerateStoriesOnce runs only once`() =
        runTest {
            viewModel.preGenerateStoriesOnce()
            advanceUntilIdle()
            coVerify(exactly = 1) { recommendRepository.getStories(any()) }

            viewModel.preGenerateStoriesOnce()
            advanceUntilIdle()
            // Should still be 1
            coVerify(exactly = 1) { recommendRepository.getStories(any()) }
        }

    // --- sortTags Tests ---

    private fun createMockTagItem(
        tagName: String,
        tagId: String = "id_$tagName",
        photoCount: Int = 0,
        updatedAt: String? = null,
    ): TagItem =
        TagItem(
            tagName = tagName,
            coverImageId = null,
            tagId = tagId,
            createdAt = null,
            updatedAt = updatedAt,
            photoCount = photoCount,
        )

    @Test
    fun `sortTags NAME_ASC sorts tags alphabetically ascending`() =
        runTest {
            // Setup tags with TagStateRepository
            val tags =
                listOf(
                    createMockTagItem("Zebra"),
                    createMockTagItem("Apple"),
                    createMockTagItem("Mango"),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            // Create fresh viewModel with the mocked flow
            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            // Collect from homeLoadingState to trigger subscription
            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            // Set sort order
            viewModel.setSortOrder(TagSortOrder.NAME_ASC)

            // Update the state to Success
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            // Verify sorted result
            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            assertEquals(listOf("Apple", "Mango", "Zebra"), result.map { it.tagName })

            job.cancel()
        }

    @Test
    fun `sortTags NAME_DESC sorts tags alphabetically descending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("Apple"),
                    createMockTagItem("Zebra"),
                    createMockTagItem("Mango"),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.NAME_DESC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            assertEquals(listOf("Zebra", "Mango", "Apple"), result.map { it.tagName })

            job.cancel()
        }

    @Test
    fun `sortTags COUNT_ASC sorts tags by photo count ascending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("Tag1", photoCount = 50),
                    createMockTagItem("Tag2", photoCount = 5),
                    createMockTagItem("Tag3", photoCount = 25),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.COUNT_ASC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            assertEquals(listOf(5, 25, 50), result.map { it.photoCount })

            job.cancel()
        }

    @Test
    fun `sortTags COUNT_DESC sorts tags by photo count descending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("Tag1", photoCount = 5),
                    createMockTagItem("Tag2", photoCount = 50),
                    createMockTagItem("Tag3", photoCount = 25),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.COUNT_DESC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            assertEquals(listOf(50, 25, 5), result.map { it.photoCount })

            job.cancel()
        }

    @Test
    fun `sortTags CREATED_DESC sorts by updatedAt date descending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("Old", updatedAt = "2023-01-01T10:00:00.000000Z"),
                    createMockTagItem("Newest", updatedAt = "2023-12-31T23:59:59.999999Z"),
                    createMockTagItem("Middle", updatedAt = "2023-06-15T12:30:00.000000Z"),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.CREATED_DESC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            assertEquals(listOf("Newest", "Middle", "Old"), result.map { it.tagName })

            job.cancel()
        }

    @Test
    fun `sortTags CREATED_DESC handles different date formats`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("Format1", updatedAt = "2023-01-01T10:00:00.000000Z"),
                    createMockTagItem("Format2", updatedAt = "2023-06-15T12:30:00Z"),
                    createMockTagItem("Format3", updatedAt = "2023-12-31T23:59:59"),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.CREATED_DESC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            // All should parse correctly and sort by date descending
            assertEquals(listOf("Format3", "Format2", "Format1"), result.map { it.tagName })

            job.cancel()
        }

    @Test
    fun `sortTags CREATED_DESC handles null dates`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("WithDate", updatedAt = "2023-06-15T12:30:00.000000Z"),
                    createMockTagItem("NoDate", updatedAt = null),
                    createMockTagItem("AnotherWithDate", updatedAt = "2023-01-01T10:00:00.000000Z"),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.CREATED_DESC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            // Tags with parseable dates should come first, null dates last
            // Within parseable dates: descending order
            assertEquals(3, result.size)
            // WithDate (June) should be first, then AnotherWithDate (Jan), then NoDate
            assertTrue(result[0].tagName == "WithDate")
            assertTrue(result[1].tagName == "AnotherWithDate")
            assertTrue(result[2].tagName == "NoDate")

            job.cancel()
        }

    @Test
    fun `sortTags CREATED_DESC handles invalid date strings`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("Valid", updatedAt = "2023-06-15T12:30:00.000000Z"),
                    createMockTagItem("Invalid", updatedAt = "invalid-date"),
                    createMockTagItem("AnotherValid", updatedAt = "2023-01-01T10:00:00.000000Z"),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.CREATED_DESC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            // Tags with parseable dates should come first, invalid dates treated like null
            assertEquals(3, result.size)
            assertTrue(result[0].tagName == "Valid")
            assertTrue(result[1].tagName == "AnotherValid")

            job.cancel()
        }

    @Test
    fun `sortTags handles empty list`() =
        runTest {
            val tags = emptyList<TagItem>()

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.NAME_ASC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            assertTrue(result.isEmpty())

            job.cancel()
        }

    @Test
    fun `sortTags handles single item list`() =
        runTest {
            val tags = listOf(createMockTagItem("OnlyTag"))

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.NAME_DESC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            assertEquals(1, result.size)
            assertEquals("OnlyTag", result[0].tagName)

            job.cancel()
        }

    @Test
    fun `sortTags COUNT_ASC handles equal photo counts`() =
        runTest {
            val tags =
                listOf(
                    createMockTagItem("Tag1", photoCount = 10),
                    createMockTagItem("Tag2", photoCount = 10),
                    createMockTagItem("Tag3", photoCount = 5),
                )

            val loadingStateFlow =
                MutableStateFlow<TagStateRepository.LoadingState>(
                    TagStateRepository.LoadingState.Idle,
                )
            every { tagStateRepository.loadingState } returns loadingStateFlow

            viewModel =
                HomeViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    photoSelectionRepository,
                    imageBrowserRepository,
                    tagStateRepository,
                    sortPreferences,
                )

            var latestState: HomeViewModel.HomeLoadingState? = null
            val job =
                launch {
                    viewModel.homeLoadingState.collect { latestState = it }
                }

            viewModel.setSortOrder(TagSortOrder.COUNT_ASC)
            loadingStateFlow.value = TagStateRepository.LoadingState.Success(tags)
            advanceUntilIdle()

            val result = (latestState as HomeViewModel.HomeLoadingState.Success).tags
            // First should have count 5, then the two with count 10
            assertEquals(5, result[0].photoCount)
            assertEquals(10, result[1].photoCount)
            assertEquals(10, result[2].photoCount)

            job.cancel()
        }
}
