package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.TagItem
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.SearchRepository
import com.example.momentag.repository.TagStateRepository
import com.example.momentag.repository.TokenRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class SearchViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SearchViewModel
    private lateinit var searchRepository: SearchRepository
    private lateinit var photoSelectionRepository: PhotoSelectionRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var tagStateRepository: TagStateRepository
    private lateinit var isLoggedInFlow: MutableStateFlow<String?>
    private lateinit var tagsFlow: MutableStateFlow<List<TagItem>>

    @Before
    fun setUp() {
        searchRepository = mockk(relaxed = true)
        photoSelectionRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        tagStateRepository = mockk(relaxed = true)

        isLoggedInFlow = MutableStateFlow("token123")
        tagsFlow = MutableStateFlow(emptyList())

        every { tokenRepository.isLoggedIn } returns isLoggedInFlow
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyMap())
        every { tagStateRepository.tags } returns tagsFlow
        every { imageBrowserRepository.clear() } just Runs
        every { imageBrowserRepository.setSearchResults(any(), any()) } just Runs

        coEvery { localRepository.getSearchHistory() } returns emptyList()

        viewModel =
            SearchViewModel(
                searchRepository,
                photoSelectionRepository,
                localRepository,
                imageBrowserRepository,
                tokenRepository,
                tagStateRepository,
            )
    }

    private fun createMockPhoto(
        id: String,
        contentUri: Uri = mockk(relaxed = true),
    ): Photo =
        Photo(
            photoId = id,
            contentUri = contentUri,
            createdAt = "2023-01-01",
        )

    private fun createMockPhotoResponse(id: String): PhotoResponse =
        mockk<PhotoResponse>(relaxed = true) {
            every { photoId } returns id
        }

    // --- Initialization Tests ---

    @Test
    fun `init loads search history`() =
        runTest {
            val history = listOf("query1", "query2", "query3")
            coEvery { localRepository.getSearchHistory() } returns history

            val newViewModel =
                SearchViewModel(
                    searchRepository,
                    photoSelectionRepository,
                    localRepository,
                    imageBrowserRepository,
                    tokenRepository,
                    tagStateRepository,
                )
            advanceUntilIdle()

            assertEquals(history, newViewModel.searchHistory.value)
        }

    @Test
    fun `init subscribes to isLoggedIn and clears history on logout`() =
        runTest {
            val history = listOf("query1", "query2")
            coEvery { localRepository.getSearchHistory() } returns history
            coEvery { localRepository.clearSearchHistory() } just Runs

            val newViewModel =
                SearchViewModel(
                    searchRepository,
                    photoSelectionRepository,
                    localRepository,
                    imageBrowserRepository,
                    tokenRepository,
                    tagStateRepository,
                )
            advanceUntilIdle()

            assertEquals(history, newViewModel.searchHistory.value)

            // Simulate logout
            isLoggedInFlow.value = null
            advanceUntilIdle()

            coVerify { localRepository.clearSearchHistory() }
            assertTrue(newViewModel.searchHistory.value.isEmpty())
        }

    @Test
    fun `init with empty history`() =
        runTest {
            coEvery { localRepository.getSearchHistory() } returns emptyList()

            val newViewModel =
                SearchViewModel(
                    searchRepository,
                    photoSelectionRepository,
                    localRepository,
                    imageBrowserRepository,
                    tokenRepository,
                    tagStateRepository,
                )
            advanceUntilIdle()

            assertTrue(newViewModel.searchHistory.value.isEmpty())
        }

    // --- Selection Mode Tests ---

    @Test
    fun `setSelectionMode updates state`() {
        assertFalse(viewModel.isSelectionMode.value)

        viewModel.setSelectionMode(true)
        assertTrue(viewModel.isSelectionMode.value)

        viewModel.setSelectionMode(false)
        assertFalse(viewModel.isSelectionMode.value)
    }

    // --- Search History Tests ---

    @Test
    fun `loadSearchHistory loads from repository`() =
        runTest {
            val history = listOf("dogs", "cats", "sunset")
            coEvery { localRepository.getSearchHistory() } returns history

            viewModel.loadSearchHistory()
            advanceUntilIdle()

            assertEquals(history, viewModel.searchHistory.value)
            coVerify { localRepository.getSearchHistory() }
        }

    @Test
    fun `removeSearchHistory removes query and reloads`() =
        runTest {
            val initialHistory = listOf("query1", "query2", "query3")
            val updatedHistory = listOf("query1", "query3")

            coEvery { localRepository.getSearchHistory() } returnsMany listOf(initialHistory, updatedHistory)
            coEvery { localRepository.removeSearchHistory("query2") } just Runs

            viewModel.loadSearchHistory()
            advanceUntilIdle()
            assertEquals(initialHistory, viewModel.searchHistory.value)

            viewModel.removeSearchHistory("query2")
            advanceUntilIdle()

            coVerify { localRepository.removeSearchHistory("query2") }
            assertEquals(updatedHistory, viewModel.searchHistory.value)
        }

    // --- Search Text Tests ---

    @Test
    fun `onSearchTextChanged updates search text`() {
        assertEquals("", viewModel.searchText.value)

        viewModel.onSearchTextChanged("test query")
        assertEquals("test query", viewModel.searchText.value)

        viewModel.onSearchTextChanged("")
        assertEquals("", viewModel.searchText.value)
    }

    // --- Search Tests ---

    @Test
    fun `search with blank query sets error`() =
        runTest {
            viewModel.search("")
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Error)
            val errorState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Error
            assertEquals(SearchViewModel.SearchError.EmptyQuery, errorState.error)
        }

    @Test
    fun `search with whitespace-only query sets error`() =
        runTest {
            viewModel.search("   ")
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Error)
            val errorState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Error
            assertEquals(SearchViewModel.SearchError.EmptyQuery, errorState.error)
        }

    @Test
    fun `search success returns photos and updates state`() =
        runTest {
            val query = "beautiful sunset"
            val photoResponses = listOf(createMockPhotoResponse("1"), createMockPhotoResponse("2"))
            val photos = listOf(createMockPhoto("1"), createMockPhoto("2"))

            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Success)
            val successState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Success
            assertEquals(photos, successState.photos)
            assertEquals(query, successState.query)
            assertEquals(query, viewModel.searchText.value)

            coVerify { localRepository.addSearchHistory(query) }
            verify { imageBrowserRepository.setSearchResults(photos, query) }

            // Check pagination state
            assertTrue(viewModel.hasMore.value)
            assertFalse(viewModel.isLoadingMore.value)
        }

    @Test
    fun `search success with empty results sets hasMore to false`() =
        runTest {
            val query = "test"
            val photoResponses = emptyList<PhotoResponse>()
            val photos = emptyList<Photo>()

            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Success)
            assertFalse(viewModel.hasMore.value)
        }

    @Test
    fun `search empty result updates state`() =
        runTest {
            val query = "nonexistent"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Empty(query)
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Empty)
            val emptyState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Empty
            assertEquals(query, emptyState.query)

            verify { imageBrowserRepository.clear() }
            assertFalse(viewModel.hasMore.value)
        }

    @Test
    fun `search bad request sets error`() =
        runTest {
            val query = "test"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.BadRequest("Bad request")
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Error)
            val errorState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Error
            assertEquals(SearchViewModel.SearchError.UnknownError, errorState.error)
        }

    @Test
    fun `search unauthorized sets error`() =
        runTest {
            val query = "test"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Unauthorized("Unauthorized")
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Error)
            val errorState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Error
            assertEquals(SearchViewModel.SearchError.Unauthorized, errorState.error)
        }

    @Test
    fun `search network error sets error`() =
        runTest {
            val query = "test"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.NetworkError("Network error")
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Error)
            val errorState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Error
            assertEquals(SearchViewModel.SearchError.NetworkError, errorState.error)
        }

    @Test
    fun `search generic error sets error`() =
        runTest {
            val query = "test"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Error("Server error")
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Error)
            val errorState = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Error
            assertEquals(SearchViewModel.SearchError.UnknownError, errorState.error)
        }

    @Test
    fun `search resets pagination state`() =
        runTest {
            val query1 = "first query"
            val query2 = "second query"
            val photoResponses = listOf(createMockPhotoResponse("1"))
            val photos = listOf(createMockPhoto("1"))

            coEvery { searchRepository.semanticSearch(any(), any()) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            coEvery { localRepository.addSearchHistory(any()) } just Runs

            // First search
            viewModel.search(query1)
            advanceUntilIdle()

            // Second search should reset pagination
            viewModel.search(query2)
            advanceUntilIdle()

            // Verify second search was called with offset 0
            coVerify { searchRepository.semanticSearch(query2, 0) }
        }

    // --- Load More Tests ---

    @Test
    fun `loadMore can be called multiple times sequentially`() =
        runTest {
            val query = "test"
            val photoResponses = listOf(createMockPhotoResponse("1"))
            val photos = listOf(createMockPhoto("1"))

            coEvery { searchRepository.semanticSearch(query, any()) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            // Call loadMore and advance to completion
            viewModel.loadMore()
            advanceUntilIdle()

            // Call loadMore again - should work since first one completed
            viewModel.loadMore()
            advanceUntilIdle()

            // Initial search + 2 loadMore calls = 3 total calls
            coVerify(exactly = 3) { searchRepository.semanticSearch(any(), any()) }
        }

    @Test
    fun `loadMore does nothing when hasMore is false`() =
        runTest {
            val query = "test"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Empty(query)
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertFalse(viewModel.hasMore.value)

            viewModel.loadMore()
            advanceUntilIdle()

            // Should not call search again
            coVerify(exactly = 1) { searchRepository.semanticSearch(any(), any()) }
        }

    @Test
    fun `loadMore does nothing when state is not Success`() =
        runTest {
            assertEquals(SearchViewModel.SemanticSearchState.Idle, viewModel.searchState.value)

            viewModel.loadMore()
            advanceUntilIdle()

            coVerify(exactly = 0) { searchRepository.semanticSearch(any(), any()) }
        }

    @Test
    fun `loadMore appends new photos to existing results`() =
        runTest {
            val query = "test"
            val initialPhotoResponses = listOf(createMockPhotoResponse("1"), createMockPhotoResponse("2"))
            val initialPhotos = listOf(createMockPhoto("1"), createMockPhoto("2"))
            val newPhotoResponses = listOf(createMockPhotoResponse("3"), createMockPhotoResponse("4"))
            val newPhotos = listOf(createMockPhoto("3"), createMockPhoto("4"))

            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(initialPhotoResponses)
            coEvery { searchRepository.semanticSearch(query, 2) } returns
                SearchRepository.SearchResult.Success(newPhotoResponses)
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { localRepository.toPhotos(newPhotoResponses) } returns newPhotos
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            val successState1 = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Success
            assertEquals(2, successState1.photos.size)

            viewModel.loadMore()
            advanceUntilIdle()

            val successState2 = viewModel.searchState.value as SearchViewModel.SemanticSearchState.Success
            assertEquals(4, successState2.photos.size)
            assertTrue(viewModel.hasMore.value)
            assertFalse(viewModel.isLoadingMore.value)
        }

    @Test
    fun `loadMore with empty results sets hasMore to false`() =
        runTest {
            val query = "test"
            val initialPhotoResponses = listOf(createMockPhotoResponse("1"))
            val initialPhotos = listOf(createMockPhoto("1"))

            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(initialPhotoResponses)
            coEvery { searchRepository.semanticSearch(query, 1) } returns
                SearchRepository.SearchResult.Success(emptyList())
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertFalse(viewModel.hasMore.value)
            assertFalse(viewModel.isLoadingMore.value)
        }

    @Test
    fun `loadMore with Empty result sets hasMore to false`() =
        runTest {
            val query = "test"
            val initialPhotoResponses = listOf(createMockPhotoResponse("1"))
            val initialPhotos = listOf(createMockPhoto("1"))

            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(initialPhotoResponses)
            coEvery { searchRepository.semanticSearch(query, 1) } returns
                SearchRepository.SearchResult.Empty(query)
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            assertFalse(viewModel.hasMore.value)
            assertFalse(viewModel.isLoadingMore.value)
        }

    @Test
    fun `loadMore with error keeps hasMore true for retry`() =
        runTest {
            val query = "test"
            val initialPhotoResponses = listOf(createMockPhotoResponse("1"))
            val initialPhotos = listOf(createMockPhoto("1"))

            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(initialPhotoResponses)
            coEvery { searchRepository.semanticSearch(query, 1) } returns
                SearchRepository.SearchResult.NetworkError("Network error")
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            // hasMore should remain true to allow retry
            assertTrue(viewModel.hasMore.value)
            assertFalse(viewModel.isLoadingMore.value)
        }

    // --- Photo Selection Tests ---

    @Test
    fun `togglePhoto delegates to photoSelectionRepository`() {
        val photo = createMockPhoto("1")
        every { photoSelectionRepository.togglePhoto(photo) } just Runs

        viewModel.togglePhoto(photo)

        verify { photoSelectionRepository.togglePhoto(photo) }
    }

    @Test
    fun `resetSelection clears photo selection`() {
        every { photoSelectionRepository.clear() } just Runs

        viewModel.resetSelection()

        verify { photoSelectionRepository.clear() }
    }

    @Test
    fun `getPhotosToShare returns selected photos as list`() {
        val photo1 = createMockPhoto("1")
        val photo2 = createMockPhoto("2")
        val selectedPhotosMap = mapOf("1" to photo1, "2" to photo2)

        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(selectedPhotosMap)

        val newViewModel =
            SearchViewModel(
                searchRepository,
                photoSelectionRepository,
                localRepository,
                imageBrowserRepository,
                tokenRepository,
                tagStateRepository,
            )

        val photosToShare = newViewModel.getPhotosToShare()

        assertEquals(2, photosToShare.size)
        assertTrue(photosToShare.contains(photo1))
        assertTrue(photosToShare.contains(photo2))
    }

    @Test
    fun `getPhotosToShare returns empty list when no photos selected`() {
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyMap())

        val newViewModel =
            SearchViewModel(
                searchRepository,
                photoSelectionRepository,
                localRepository,
                imageBrowserRepository,
                tokenRepository,
                tagStateRepository,
            )

        val photosToShare = newViewModel.getPhotosToShare()

        assertTrue(photosToShare.isEmpty())
    }

    // --- Search State Reset Tests ---

    @Test
    fun `resetSearchState sets state to Idle and clears browser`() =
        runTest {
            val query = "test"
            val photoResponses = listOf(createMockPhotoResponse("1"))
            val photos = listOf(createMockPhoto("1"))

            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            coEvery { localRepository.addSearchHistory(query) } just Runs

            viewModel.search(query)
            advanceUntilIdle()

            assertTrue(viewModel.searchState.value is SearchViewModel.SemanticSearchState.Success)

            viewModel.resetSearchState()

            assertEquals(SearchViewModel.SemanticSearchState.Idle, viewModel.searchState.value)
            verify(atLeast = 1) { imageBrowserRepository.clear() }
        }

    // --- Scroll Position Tests ---

    @Test
    fun `setScrollToIndex updates scroll index`() {
        assertNull(viewModel.scrollToIndex.value)

        viewModel.setScrollToIndex(10)
        assertEquals(10, viewModel.scrollToIndex.value)

        viewModel.setScrollToIndex(null)
        assertNull(viewModel.scrollToIndex.value)
    }

    @Test
    fun `clearScrollToIndex sets scroll index to null`() {
        viewModel.setScrollToIndex(15)
        assertEquals(15, viewModel.scrollToIndex.value)

        viewModel.clearScrollToIndex()
        assertNull(viewModel.scrollToIndex.value)
    }

    @Test
    fun `restoreScrollPosition gets index from imageBrowserRepository`() {
        every { imageBrowserRepository.getCurrentIndex() } returns 5

        viewModel.restoreScrollPosition()

        assertEquals(5, viewModel.scrollToIndex.value)
        verify { imageBrowserRepository.getCurrentIndex() }
    }

    @Test
    fun `restoreScrollPosition does nothing when getCurrentIndex returns null`() {
        every { imageBrowserRepository.getCurrentIndex() } returns null

        viewModel.restoreScrollPosition()

        assertNull(viewModel.scrollToIndex.value)
    }

    // --- Tags Flow Tests ---

    @Test
    fun `tags flow exposes tagStateRepository tags`() =
        runTest {
            val mockTags =
                listOf(
                    mockk<TagItem> { every { tagName } returns "Tag1" },
                    mockk<TagItem> { every { tagName } returns "Tag2" },
                )

            tagsFlow.value = mockTags

            advanceUntilIdle()

            assertEquals(mockTags, viewModel.tags.value)
        }

    // --- Initial State Tests ---

    @Test
    fun `initial state is correct`() =
        runTest {
            advanceUntilIdle()

            assertEquals(SearchViewModel.SemanticSearchState.Idle, viewModel.searchState.value)
            assertEquals("", viewModel.searchText.value)
            assertFalse(viewModel.isSelectionMode.value)
            assertNull(viewModel.scrollToIndex.value)
            assertTrue(viewModel.hasMore.value)
            assertFalse(viewModel.isLoadingMore.value)
        }
}
