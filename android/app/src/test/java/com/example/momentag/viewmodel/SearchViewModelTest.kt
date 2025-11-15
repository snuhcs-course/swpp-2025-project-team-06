package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.SearchRepository
import com.example.momentag.repository.TokenRepository
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
class SearchViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SearchViewModel
    private lateinit var searchRepository: SearchRepository
    private lateinit var photoSelectionRepository: PhotoSelectionRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var tokenRepository: TokenRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        searchRepository = mockk()
        photoSelectionRepository = mockk()
        localRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)
        tokenRepository = mockk()

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

    // Search tests
    @Test
    fun `search with empty query updates state to Error`() =
        runTest {
            // When
            viewModel.search("")
            advanceUntilIdle()

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Error)
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
            assertTrue(state is SemanticSearchState.Success)
            assertEquals(photos, (state as SemanticSearchState.Success).photos)
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
            assertTrue(state is SemanticSearchState.Empty)
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
            assertTrue(state is SemanticSearchState.Error)
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
            assertTrue(state is SemanticSearchState.NetworkError)
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
        assertTrue(viewModel.searchState.value is SemanticSearchState.Idle)
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
                )

            // When - simulate logout
            isLoggedInFlow.value = null
            advanceUntilIdle()

            // Then
            coVerify { localRepository.clearSearchHistory() }
        }
}
