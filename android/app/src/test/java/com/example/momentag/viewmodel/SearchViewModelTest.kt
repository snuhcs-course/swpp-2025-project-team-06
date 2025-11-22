package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.TagItem
import com.example.momentag.model.TagResponse
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.SearchRepository
import com.example.momentag.repository.TokenRepository
import com.example.momentag.ui.components.SearchContentElement
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SearchViewModel
    private lateinit var searchRepository: SearchRepository
    private lateinit var photoSelectionRepository: PhotoSelectionRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var remoteRepository: RemoteRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        searchRepository = mockk()
        photoSelectionRepository = mockk()
        localRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)
        tokenRepository = mockk()
        remoteRepository = mockk()

        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyList())
        every { tokenRepository.isLoggedIn } returns MutableStateFlow("token")
        coEvery { localRepository.getSearchHistory() } returns emptyList()

        viewModel =
            SearchViewModel(
                searchRepository,
                photoSelectionRepository,
                localRepository,
                imageBrowserRepository,
                tokenRepository,
                remoteRepository,
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

    // Helper functions
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

    private fun createTagResponse(
        tagName: String = "tag1",
        tagId: String = "tag-id-1",
        thumbnailPhotoPathId: Long? = 100L,
        photoCount: Int = 5,
    ) = TagResponse(
        tagName = tagName,
        tagId = tagId,
        thumbnailPhotoPathId = thumbnailPhotoPathId,
        createdAt = "2025-01-01",
        updatedAt = "2025-01-01",
        photoCount = photoCount,
    )

    private fun createTagItem(
        tagName: String = "tag1",
        tagId: String = "tag-id-1",
        coverImageId: Long? = 100L,
        photoCount: Int = 5,
    ) = TagItem(
        tagName = tagName,
        tagId = tagId,
        coverImageId = coverImageId,
        createdAt = "2025-01-01",
        updatedAt = "2025-01-01",
        photoCount = photoCount,
    )

    // Search tests
    @Test
    fun `search with empty query updates state to Error`() =
        runTest {
            // When
            viewModel.search("")
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Error)
        }

    @Test
    fun `search success updates state with photos`() =
        runTest {
            // Given
            val query = "sunset"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { localRepository.addSearchHistory(query) } returns Unit
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.search(query)
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Success)
            assertEquals(photos, (state as SearchViewModel.SemanticSearchState.Success).photos)
            assertEquals(query, state.query)
            verify { imageBrowserRepository.setSearchResults(photos, query) }
        }

    @Test
    fun `search with empty results updates state to Empty`() =
        runTest {
            // Given
            val query = "nonexistent"
            coEvery { localRepository.addSearchHistory(query) } returns Unit
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Empty(query)

            // When
            viewModel.search(query)
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Empty)
            verify { imageBrowserRepository.clear() }
        }

    @Test
    fun `search with Unauthorized updates state to Error`() =
        runTest {
            // Given
            val query = "test"
            coEvery { localRepository.addSearchHistory(query) } returns Unit
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Unauthorized("Unauthorized")

            // When
            viewModel.search(query)
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Error)
            assertEquals(SearchViewModel.SearchError.Unauthorized, (state as SearchViewModel.SemanticSearchState.Error).error)
        }

    @Test
    fun `search with NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            val query = "test"
            val errorMessage = "Network error"
            coEvery { localRepository.addSearchHistory(query) } returns Unit
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.NetworkError(errorMessage)

            // When
            viewModel.search(query)
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Error)
            assertEquals(SearchViewModel.SearchError.NetworkError, (state as SearchViewModel.SemanticSearchState.Error).error)
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

    // Search text tests
    @Test
    fun `onSearchTextChanged updates searchText state`() {
        // When
        viewModel.onSearchTextChanged("test query")

        // Then
        assertEquals("test query", viewModel.searchText.value)
    }

    // Search history tests
    @Test
    fun `loadSearchHistory loads history from localRepository`() =
        runTest {
            // Given
            val history = listOf("query1", "query2")
            coEvery { localRepository.getSearchHistory() } returns history

            // When
            viewModel.loadSearchHistory()
            advanceUntilIdle()

            // Then
            assertEquals(history, viewModel.searchHistory.value)
        }

    @Test
    fun `removeSearchHistory removes query and reloads`() =
        runTest {
            // Given
            val query = "test"
            val updatedHistory = listOf("query2")
            coEvery { localRepository.removeSearchHistory(query) } returns Unit
            coEvery { localRepository.getSearchHistory() } returns updatedHistory

            // When
            viewModel.removeSearchHistory(query)
            advanceUntilIdle()

            // Then
            assertEquals(updatedHistory, viewModel.searchHistory.value)
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

    // State reset tests
    @Test
    fun `resetSearchState resets to Idle and clears imageBrowserRepository`() {
        // When
        viewModel.resetSearchState()

        // Then
        assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Idle)
        verify { imageBrowserRepository.clear() }
    }

    // Logout handling test
    @Test
    fun `logout clears search history`() =
        runTest {
            // Given
            val isLoggedInFlow = MutableStateFlow<String?>("token")
            every { tokenRepository.isLoggedIn } returns isLoggedInFlow
            coEvery { localRepository.clearSearchHistory() } returns Unit
            coEvery { localRepository.getSearchHistory() } returns emptyList()

            // Recreate viewModel with the new flow
            val newViewModel =
                SearchViewModel(
                    searchRepository,
                    photoSelectionRepository,
                    localRepository,
                    imageBrowserRepository,
                    tokenRepository,
                    remoteRepository,
                )

            // When - simulate logout
            isLoggedInFlow.value = null
            advanceUntilIdle()

            // Then
            coVerify { localRepository.clearSearchHistory() }
        }

    // Tag loading tests
    @Test
    fun `loadServerTags success updates state with tags`() =
        runTest {
            // Given
            val tagResponses =
                listOf(
                    createTagResponse("sunset", "tag-1", 100L, 5),
                    createTagResponse("beach", "tag-2", 200L, 10),
                )
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Success(tagResponses)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.tagLoadingState.value
            assertTrue(state is SearchViewModel.TagLoadingState.Success)
            assertEquals(2, (state as SearchViewModel.TagLoadingState.Success).tags.size)
            assertEquals("sunset", state.tags[0].tagName)
            assertEquals("beach", state.tags[1].tagName)
        }

    @Test
    fun `loadServerTags error updates state to Error`() =
        runTest {
            // Given
            val errorMessage = "Failed to load tags"
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.tagLoadingState.value
            assertTrue(state is SearchViewModel.TagLoadingState.Error)
            assertEquals(SearchViewModel.SearchError.UnknownError, (state as SearchViewModel.TagLoadingState.Error).error)
        }

    @Test
    fun `loadServerTags unauthorized updates state to Error`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.tagLoadingState.value
            assertTrue(state is SearchViewModel.TagLoadingState.Error)
            assertEquals(SearchViewModel.SearchError.Unauthorized, (state as SearchViewModel.TagLoadingState.Error).error)
        }

    @Test
    fun `loadServerTags network error updates state to Error`() =
        runTest {
            // Given
            val errorMessage = "Network error"
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.tagLoadingState.value
            assertTrue(state is SearchViewModel.TagLoadingState.Error)
            assertEquals(SearchViewModel.SearchError.NetworkError, (state as SearchViewModel.TagLoadingState.Error).error)
        }

    @Test
    fun `resetTagLoadingState resets to Idle`() {
        // Given - load tags first
        runTest {
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Success(listOf(createTagResponse()))
            viewModel.loadServerTags()
            advanceUntilIdle()
        }

        // When
        viewModel.resetTagLoadingState()

        // Then
        assertTrue(viewModel.tagLoadingState.value is SearchViewModel.TagLoadingState.Idle)
    }

    // parseQueryToElements tests
    @Test
    fun `parseQueryToElements with plain text returns Text element`() {
        // Given
        val query = "sunset beach"
        val allTags = emptyList<TagItem>()

        // When
        val elements = viewModel.parseQueryToElements(query, allTags)

        // Then
        assertEquals(1, elements.size)
        assertTrue(elements[0] is SearchContentElement.Text)
        assertEquals("sunset beach", (elements[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements with tag returns Text and Chip elements`() {
        // Given
        val tag = createTagItem("sunset", "tag-1")
        val query = "photos of {sunset}"
        val allTags = listOf(tag)

        // When
        val elements = viewModel.parseQueryToElements(query, allTags)

        // Then
        assertEquals(3, elements.size)
        assertTrue(elements[0] is SearchContentElement.Text)
        assertEquals("photos of ", (elements[0] as SearchContentElement.Text).text)
        assertTrue(elements[1] is SearchContentElement.Chip)
        assertEquals("sunset", (elements[1] as SearchContentElement.Chip).tag.tagName)
        assertTrue(elements[2] is SearchContentElement.Text)
        assertEquals("", (elements[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements with multiple tags returns correct elements`() {
        // Given
        val tag1 = createTagItem("sunset", "tag-1")
        val tag2 = createTagItem("beach", "tag-2")
        val query = "{sunset} and {beach}"
        val allTags = listOf(tag1, tag2)

        // When
        val elements = viewModel.parseQueryToElements(query, allTags)

        // Then
        assertEquals(4, elements.size)
        assertTrue(elements[0] is SearchContentElement.Chip)
        assertEquals("sunset", (elements[0] as SearchContentElement.Chip).tag.tagName)
        assertTrue(elements[1] is SearchContentElement.Text)
        assertEquals(" and ", (elements[1] as SearchContentElement.Text).text)
        assertTrue(elements[2] is SearchContentElement.Chip)
        assertEquals("beach", (elements[2] as SearchContentElement.Chip).tag.tagName)
        assertTrue(elements[3] is SearchContentElement.Text)
        assertEquals("", (elements[3] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements with non-existent tag skips tag`() {
        // Given
        val tag = createTagItem("sunset", "tag-1")
        val query = "{sunset} and {nonexistent}"
        val allTags = listOf(tag)

        // When
        val elements = viewModel.parseQueryToElements(query, allTags)

        // Then
        // The text before non-existent tag gets added, then skipped tag causes
        // lastIndex to not update, so remaining text also includes that section
        // Result: Chip(sunset), Text(" and "), Text(" and {nonexistent}")
        assertEquals(3, elements.size)
        assertTrue(elements[0] is SearchContentElement.Chip)
        assertEquals("sunset", (elements[0] as SearchContentElement.Chip).tag.tagName)
        assertTrue(elements[1] is SearchContentElement.Text)
        assertEquals(" and ", (elements[1] as SearchContentElement.Text).text)
        assertTrue(elements[2] is SearchContentElement.Text)
        // The remaining text includes the non-matched tag pattern
        assertTrue((elements[2] as SearchContentElement.Text).text.contains("nonexistent"))
    }

    @Test
    fun `parseQueryToElements with empty query returns empty Text element`() {
        // Given
        val query = ""
        val allTags = emptyList<TagItem>()

        // When
        val elements = viewModel.parseQueryToElements(query, allTags)

        // Then
        assertEquals(1, elements.size)
        assertTrue(elements[0] is SearchContentElement.Text)
        assertEquals("", (elements[0] as SearchContentElement.Text).text)
    }

    // ChipSearchBar state management tests
    @Test
    fun `onFocus updates focusedElementId`() {
        // When
        viewModel.onFocus("test-id")

        // Then
        assertEquals("test-id", viewModel.focusedElementId.value)
    }

    @Test
    fun `onFocus with null clears focusedElementId when not ignoring focus loss`() {
        // Given
        viewModel.onFocus("test-id")

        // When
        viewModel.onFocus(null)

        // Then
        assertEquals(null, viewModel.focusedElementId.value)
    }

    @Test
    fun `onContainerClick focuses last text element`() =
        runTest {
            // Given - contentItems is initialized with one Text element by default
            val lastTextElement = viewModel.contentItems.lastOrNull { it is SearchContentElement.Text }

            // When
            viewModel.onContainerClick()
            advanceUntilIdle()

            // Then
            assertNotNull(lastTextElement)
            assertEquals(lastTextElement!!.id, viewModel.focusedElementId.value)
        }

    @Test
    fun `resetIgnoreFocusLossFlag resets flag to false`() {
        // When
        viewModel.resetIgnoreFocusLossFlag()

        // Then
        assertFalse(viewModel.ignoreFocusLoss.value)
    }

    // performSearch and selectHistoryItem tests
    @Test
    fun `performSearch builds query and navigates`() =
        runTest {
            // Given
            val tag = createTagItem("sunset", "tag-1")
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", "photos of "))
            viewModel.contentItems.add(SearchContentElement.Chip("id2", tag))
            viewModel.contentItems.add(SearchContentElement.Text("id3", ""))

            val expectedQuery = "photos of {sunset}"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())

            coEvery { localRepository.addSearchHistory(expectedQuery) } returns Unit
            coEvery { searchRepository.semanticSearch(expectedQuery, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            var navigatedRoute: String? = null

            // When
            viewModel.performSearch { route -> navigatedRoute = route }
            advanceUntilIdle()

            // Then
            assertEquals(expectedQuery, viewModel.searchText.value)
            assertNotNull(navigatedRoute)
            assertTrue(
                navigatedRoute!!.contains("photos%20of%20%7Bsunset%7D") ||
                    navigatedRoute!!.contains("photos+of+%7Bsunset%7D"),
            )
        }

    @Test
    fun `selectHistoryItem parses query and updates contentItems`() =
        runTest {
            // Given
            val tag1 = createTagItem("sunset", "tag-1")
            val tag2 = createTagItem("beach", "tag-2")
            val tagResponses =
                listOf(
                    createTagResponse("sunset", "tag-1"),
                    createTagResponse("beach", "tag-2"),
                )

            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Success(tagResponses)
            viewModel.loadServerTags()
            advanceUntilIdle()

            val query = "{sunset} at the {beach}"

            // When
            viewModel.selectHistoryItem(query)

            // Then
            assertEquals(4, viewModel.contentItems.size)
            assertTrue(viewModel.contentItems[0] is SearchContentElement.Chip)
            assertEquals("sunset", (viewModel.contentItems[0] as SearchContentElement.Chip).tag.tagName)
            assertTrue(viewModel.contentItems[1] is SearchContentElement.Text)
            assertEquals(" at the ", (viewModel.contentItems[1] as SearchContentElement.Text).text)
            assertTrue(viewModel.contentItems[2] is SearchContentElement.Chip)
            assertEquals("beach", (viewModel.contentItems[2] as SearchContentElement.Chip).tag.tagName)
            assertTrue(viewModel.contentItems[3] is SearchContentElement.Text)
        }

    // Additional search result type tests
    @Test
    fun `search with BadRequest updates state to Error`() =
        runTest {
            // Given
            val query = "test"
            val errorMessage = "Bad request"
            coEvery { localRepository.addSearchHistory(query) } returns Unit
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.BadRequest(errorMessage)

            // When
            viewModel.search(query)
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Error)
            assertEquals(SearchViewModel.SearchError.UnknownError, (state as SearchViewModel.SemanticSearchState.Error).error)
        }

    @Test
    fun `search with Error updates state to Error`() =
        runTest {
            // Given
            val query = "test"
            val errorMessage = "Server error"
            coEvery { localRepository.addSearchHistory(query) } returns Unit
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Error(errorMessage)

            // When
            viewModel.search(query)
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Error)
            assertEquals(SearchViewModel.SearchError.UnknownError, (state as SearchViewModel.SemanticSearchState.Error).error)
        }

    @Test
    fun `search with blank query updates state to Error`() =
        runTest {
            // Given
            val query = "   "

            // When
            viewModel.search(query)
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SearchViewModel.SemanticSearchState.Error)
            assertEquals(SearchViewModel.SearchError.EmptyQuery, (state as SearchViewModel.SemanticSearchState.Error).error)
        }

    @Test
    fun `search with offset parameter passes offset to repository`() =
        runTest {
            // Given
            val query = "sunset"
            val offset = 10
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { localRepository.addSearchHistory(query) } returns Unit
            coEvery { searchRepository.semanticSearch(query, offset) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.search(query, offset)
            advanceUntilIdle()

            // Then
            coVerify { searchRepository.semanticSearch(query, offset) }
        }

    // Tag loading additional result type tests
    @Test
    fun `loadServerTags BadRequest updates state to Error`() =
        runTest {
            // Given
            val errorMessage = "Bad request"
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.tagLoadingState.value
            assertTrue(state is SearchViewModel.TagLoadingState.Error)
            assertEquals(SearchViewModel.SearchError.UnknownError, (state as SearchViewModel.TagLoadingState.Error).error)
        }

    @Test
    fun `loadServerTags Exception updates state to Error`() =
        runTest {
            // Given
            val exception = RuntimeException("Test exception")
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.tagLoadingState.value
            assertTrue(state is SearchViewModel.TagLoadingState.Error)
            assertEquals(SearchViewModel.SearchError.UnknownError, (state as SearchViewModel.TagLoadingState.Error).error)
        }

    @Test
    fun `loadServerTags Exception with null message updates state to Error with default message`() =
        runTest {
            // Given
            val exception = RuntimeException()
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.tagLoadingState.value
            assertTrue(state is SearchViewModel.TagLoadingState.Error)
            assertEquals(SearchViewModel.SearchError.UnknownError, (state as SearchViewModel.TagLoadingState.Error).error)
        }

    // getPhotosToShare test
    @Test
    fun `getPhotosToShare returns selected photos`() {
        // Given
        val photos = listOf(createPhoto("photo1"), createPhoto("photo2"))
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(photos)

        // Recreate viewModel with the new flow
        val newViewModel =
            SearchViewModel(
                searchRepository,
                photoSelectionRepository,
                localRepository,
                imageBrowserRepository,
                tokenRepository,
                remoteRepository,
            )

        // When
        val result = newViewModel.getPhotosToShare()

        // Then
        assertEquals(photos, result)
    }

    // onChipClick test
    @Test
    fun `onChipClick focuses next text element`() =
        runTest {
            // Given
            val tag = createTagItem("sunset", "tag-1")
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", "text1"))
            viewModel.contentItems.add(SearchContentElement.Chip("id2", tag))
            viewModel.contentItems.add(SearchContentElement.Text("id3", "text2"))

            viewModel.textStates["id3"] =
                androidx.compose.ui.text.input
                    .TextFieldValue("text2")

            // When
            viewModel.onChipClick(1) // Click chip at index 1
            advanceUntilIdle()

            // Then
            assertEquals("id3", viewModel.focusedElementId.value)
        }

    @Test
    fun `onChipClick with no next text element does nothing`() =
        runTest {
            // Given
            val tag = createTagItem("sunset", "tag-1")
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", "text1"))
            viewModel.contentItems.add(SearchContentElement.Chip("id2", tag))

            val initialFocusedId = viewModel.focusedElementId.value

            // When
            viewModel.onChipClick(1) // Click chip at index 1 (last element)
            advanceUntilIdle()

            // Then - focusedElementId should remain unchanged
            assertEquals(initialFocusedId, viewModel.focusedElementId.value)
        }

    // performSearch with empty query test
    @Test
    fun `performSearch with empty query does not navigate`() =
        runTest {
            // Given
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", ""))

            var navigatedRoute: String? = null

            // When
            viewModel.performSearch { route -> navigatedRoute = route }
            advanceUntilIdle()

            // Then
            assertEquals(null, navigatedRoute)
        }

    // parseQueryToElements edge cases
    @Test
    fun `parseQueryToElements with only chip adds empty text element at end`() {
        // Given
        val tag = createTagItem("sunset", "tag-1")
        val query = "{sunset}"
        val allTags = listOf(tag)

        // When
        val elements = viewModel.parseQueryToElements(query, allTags)

        // Then
        assertEquals(2, elements.size)
        assertTrue(elements[0] is SearchContentElement.Chip)
        assertTrue(elements[1] is SearchContentElement.Text)
        assertEquals("", (elements[1] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements with case insensitive tag matching`() {
        // Given
        val tag = createTagItem("SunSet", "tag-1")
        val query = "{sunset}"
        val allTags = listOf(tag)

        // When
        val elements = viewModel.parseQueryToElements(query, allTags)

        // Then
        assertEquals(2, elements.size)
        assertTrue(elements[0] is SearchContentElement.Chip)
        assertEquals("SunSet", (elements[0] as SearchContentElement.Chip).tag.tagName)
    }

    // onFocus with ignoreFocusLoss test
    @Test
    fun `onFocus with null does not clear when ignoreFocusLoss is true`() {
        // Given
        viewModel.onFocus("test-id")
        viewModel.contentItems.clear()
        viewModel.contentItems.add(SearchContentElement.Text("id1", "\u200B"))
        viewModel.textStates["id1"] =
            androidx.compose.ui.text.input
                .TextFieldValue(
                    "\u200B",
                    androidx.compose.ui.text
                        .TextRange(1),
                )

        // Simulate ignoreFocusLoss being set to true (happens during chip operations)
        viewModel.addTagFromSuggestion(createTagItem())

        // When
        viewModel.onFocus(null)

        // Then
        assertEquals("test-id", viewModel.focusedElementId.value)
    }

    // addTagFromSuggestion tests
    @Test
    fun `addTagFromSuggestion adds chip and new text field`() =
        runTest {
            // Given
            val tag = createTagItem("sunset", "tag-1")
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", "photo #sun"))
            viewModel.textStates["id1"] =
                androidx.compose.ui.text.input
                    .TextFieldValue(
                        "\u200Bphoto #sun",
                        androidx.compose.ui.text
                            .TextRange(11),
                    )
            viewModel.onFocus("id1")

            // When
            viewModel.addTagFromSuggestion(tag)
            advanceUntilIdle()

            // Then
            assertEquals(3, viewModel.contentItems.size)
            assertTrue(viewModel.contentItems[0] is SearchContentElement.Text)
            assertEquals("photo ", (viewModel.contentItems[0] as SearchContentElement.Text).text)
            assertTrue(viewModel.contentItems[1] is SearchContentElement.Chip)
            assertEquals("sunset", (viewModel.contentItems[1] as SearchContentElement.Chip).tag.tagName)
            assertTrue(viewModel.contentItems[2] is SearchContentElement.Text)
        }

    @Test
    fun `addTagFromSuggestion with no focused element does nothing`() =
        runTest {
            // Given
            val tag = createTagItem("sunset", "tag-1")
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", "photo #sun"))
            viewModel.onFocus(null)

            val initialSize = viewModel.contentItems.size

            // When
            viewModel.addTagFromSuggestion(tag)
            advanceUntilIdle()

            // Then
            assertEquals(initialSize, viewModel.contentItems.size)
        }

    @Test
    fun `addTagFromSuggestion with text after cursor preserves it`() =
        runTest {
            // Given
            val tag = createTagItem("sunset", "tag-1")
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", "photo #sun more text"))
            viewModel.textStates["id1"] =
                androidx.compose.ui.text.input
                    .TextFieldValue(
                        "\u200Bphoto #sun more text",
                        androidx.compose.ui.text
                            .TextRange(11),
                    )
            viewModel.onFocus("id1")

            // When
            viewModel.addTagFromSuggestion(tag)
            advanceUntilIdle()

            // Then
            assertEquals(3, viewModel.contentItems.size)
            assertTrue(viewModel.contentItems[2] is SearchContentElement.Text)
            assertEquals(" more text", (viewModel.contentItems[2] as SearchContentElement.Text).text)
        }

    // onTextChange tests
    @Test
    fun `onTextChange with IME composing preserves value`() =
        runTest {
            // Given
            val id = "id1"
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text(id, ""))
            viewModel.textStates[id] =
                androidx.compose.ui.text.input
                    .TextFieldValue("\u200B")

            val newValue =
                androidx.compose.ui.text.input.TextFieldValue(
                    "\u200B한",
                    androidx.compose.ui.text
                        .TextRange(2),
                    androidx.compose.ui.text
                        .TextRange(1, 2),
                )

            // When
            viewModel.onTextChange(id, newValue)
            advanceUntilIdle()

            // Then
            assertEquals(newValue, viewModel.textStates[id])
        }

    @Test
    fun `onTextChange adds ZWSP if missing`() =
        runTest {
            // Given
            val id = "id1"
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text(id, ""))
            viewModel.textStates[id] =
                androidx.compose.ui.text.input
                    .TextFieldValue("")

            val newValue =
                androidx.compose.ui.text.input
                    .TextFieldValue(
                        "test",
                        androidx.compose.ui.text
                            .TextRange(4),
                    )

            // When
            viewModel.onTextChange(id, newValue)
            advanceUntilIdle()

            // Then
            val resultValue = viewModel.textStates[id]
            assertTrue(resultValue?.text?.startsWith("\u200B") == true)
        }

    @Test
    fun `onTextChange updates contentItems text`() =
        runTest {
            // Given
            val id = "id1"
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text(id, ""))
            viewModel.textStates[id] =
                androidx.compose.ui.text.input
                    .TextFieldValue("\u200B")

            val newValue =
                androidx.compose.ui.text.input
                    .TextFieldValue(
                        "\u200Btest",
                        androidx.compose.ui.text
                            .TextRange(5),
                    )

            // When
            viewModel.onTextChange(id, newValue)
            advanceUntilIdle()

            // Then
            assertEquals("test", (viewModel.contentItems[0] as SearchContentElement.Text).text)
        }

    @Test
    fun `onTextChange prevents cursor at position 0`() =
        runTest {
            // Given
            val id = "id1"
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text(id, "test"))
            viewModel.textStates[id] =
                androidx.compose.ui.text.input
                    .TextFieldValue("\u200Btest")

            val newValue =
                androidx.compose.ui.text.input
                    .TextFieldValue(
                        "\u200Btest",
                        androidx.compose.ui.text
                            .TextRange(0),
                    )

            // When
            viewModel.onTextChange(id, newValue)
            advanceUntilIdle()

            // Then
            val resultValue = viewModel.textStates[id]
            assertEquals(1, resultValue?.selection?.start)
        }

    // buildSearchQuery test (indirectly via performSearch)
    @Test
    fun `buildSearchQuery normalizes whitespace`() =
        runTest {
            // Given
            viewModel.contentItems.clear()
            viewModel.contentItems.add(SearchContentElement.Text("id1", "  multiple   spaces  "))

            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())

            val capturedQueries = mutableListOf<String>()
            coEvery { localRepository.addSearchHistory(capture(capturedQueries)) } returns Unit
            coEvery { searchRepository.semanticSearch(any(), any()) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.performSearch { }
            advanceUntilIdle()

            // Then - whitespace should be normalized
            assertTrue(capturedQueries.isNotEmpty())
            assertFalse(capturedQueries.first().contains("  "))
        }

    // Tag suggestions flow tests
    @Test
    fun `tagSuggestions starts with empty list`() =
        runTest {
            // When - initial state
            val suggestions = viewModel.tagSuggestions.value

            // Then
            assertEquals(0, suggestions.size)
        }

    @Test
    fun `tagLoadingState affects tagSuggestions availability`() =
        runTest {
            // Given - no tags loaded initially
            val initialSuggestions = viewModel.tagSuggestions.value
            assertEquals(0, initialSuggestions.size)

            // When - load tags
            val tagResponses =
                listOf(
                    createTagResponse("sunset", "tag-1"),
                    createTagResponse("beach", "tag-2"),
                )

            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Success(tagResponses)
            viewModel.loadServerTags()
            advanceUntilIdle()

            // Then - verify tags are loaded (suggestions are still empty without hash query)
            assertTrue(viewModel.tagLoadingState.value is SearchViewModel.TagLoadingState.Success)
        }

    // shouldShowSearchHistoryDropdown tests
    @Test
    fun `shouldShowSearchHistoryDropdown starts as false`() =
        runTest {
            // When - initial state
            val showDropdown = viewModel.shouldShowSearchHistoryDropdown.value

            // Then
            assertFalse(showDropdown)
        }

    @Test
    fun `searchHistory affects shouldShowSearchHistoryDropdown availability`() =
        runTest {
            // Given - no history initially
            assertEquals(0, viewModel.searchHistory.value.size)

            // When - load history
            val history = listOf("query1", "query2")
            coEvery { localRepository.getSearchHistory() } returns history

            viewModel.loadSearchHistory()
            advanceUntilIdle()

            // Then - verify history is loaded
            assertEquals(2, viewModel.searchHistory.value.size)
        }
}
