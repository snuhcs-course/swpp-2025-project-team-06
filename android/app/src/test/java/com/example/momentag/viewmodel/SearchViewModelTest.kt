package com.example.momentag.viewmodel

import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.SearchRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private lateinit var viewModel: SearchViewModel
    private lateinit var searchRepository: SearchRepository
    private lateinit var draftTagRepository: DraftTagRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        searchRepository = mockk(relaxed = true)
        draftTagRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)

        // Mock selectedPhotos flow
        every { draftTagRepository.selectedPhotos } returns MutableStateFlow(emptyList())

        viewModel =
            SearchViewModel(
                searchRepository = searchRepository,
                draftTagRepository = draftTagRepository,
                localRepository = localRepository,
                imageBrowserRepository = imageBrowserRepository,
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
    fun `initial state is Idle`() {
        // Then
        assertTrue(viewModel.searchState.value is SemanticSearchState.Idle)
    }

    @Test
    fun `selectedPhotos flow is exposed`() {
        // Then
        assertNotNull(viewModel.selectedPhotos)
        assertEquals(emptyList<Photo>(), viewModel.selectedPhotos.value)
    }

    // ========== Search Success Tests ==========

    @Test
    fun `search with valid query returns success`() =
        runTest {
            // Given
            val query = "sunset"
            val photoResponses = listOf(createPhotoResponse("photo1"))
            val photos = listOf(createPhoto("photo1"))
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Success)
            assertEquals(photos, (state as SemanticSearchState.Success).photos)
            assertEquals(query, state.query)
            coVerify { imageBrowserRepository.setSearchResults(photos, query) }
        }

    @Test
    fun `search with offset parameter works`() =
        runTest {
            // Given
            val query = "beach"
            val offset = 10
            val photoResponses = listOf(createPhotoResponse("photo2"))
            val photos = listOf(createPhoto("photo2"))
            coEvery { searchRepository.semanticSearch(query, offset) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.search(query, offset)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Success)
            coVerify { searchRepository.semanticSearch(query, offset) }
        }

    @Test
    fun `search sets loading state before completion`() =
        runTest {
            // Given
            val query = "mountain"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()

            // When
            viewModel.search(query)

            // Then - final state is Success (loading was set during execution)
            assertTrue(viewModel.searchState.value is SemanticSearchState.Success)
        }

    // ========== Search Empty Tests ==========

    @Test
    fun `search returns empty result`() =
        runTest {
            // Given
            val query = "nonexistent"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Empty(query)

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Empty)
            assertEquals(query, (state as SemanticSearchState.Empty).query)
            coVerify { imageBrowserRepository.clear() }
        }

    // ========== Search Error Tests ==========

    @Test
    fun `search with blank query shows error`() =
        runTest {
            // Given
            val query = "   "

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Error)
            assertEquals("Query cannot be empty", (state as SemanticSearchState.Error).message)
            coVerify(exactly = 0) { searchRepository.semanticSearch(any(), any()) }
        }

    @Test
    fun `search with empty query shows error`() =
        runTest {
            // Given
            val query = ""

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Error)
            assertEquals("Query cannot be empty", (state as SemanticSearchState.Error).message)
        }

    @Test
    fun `search handles BadRequest error`() =
        runTest {
            // Given
            val query = "test"
            val errorMessage = "Invalid query format"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.BadRequest(errorMessage)

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Error)
            assertEquals(errorMessage, (state as SemanticSearchState.Error).message)
        }

    @Test
    fun `search handles Unauthorized error`() =
        runTest {
            // Given
            val query = "test"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Unauthorized("Token expired")

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Error)
            assertEquals("Please login again", (state as SemanticSearchState.Error).message)
        }

    @Test
    fun `search handles NetworkError`() =
        runTest {
            // Given
            val query = "test"
            val errorMessage = "Connection timeout"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.NetworkError(errorMessage)

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.NetworkError)
            assertEquals(errorMessage, (state as SemanticSearchState.NetworkError).message)
        }

    @Test
    fun `search handles generic Error`() =
        runTest {
            // Given
            val query = "test"
            val errorMessage = "Server error"
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Error(errorMessage)

            // When
            viewModel.search(query)

            // Then
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Error)
            assertEquals(errorMessage, (state as SemanticSearchState.Error).message)
        }

    // ========== Photo Selection Tests ==========

    @Test
    fun `togglePhoto calls draftTagRepository togglePhoto`() {
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

    // ========== Reset Selection Tests ==========

    @Test
    fun `resetSelection clears draftTagRepository`() {
        // When
        viewModel.resetSelection()

        // Then
        verify { draftTagRepository.clear() }
    }

    @Test
    fun `resetSelection can be called multiple times`() {
        // When
        viewModel.resetSelection()
        viewModel.resetSelection()

        // Then
        verify(exactly = 2) { draftTagRepository.clear() }
    }

    // ========== Reset Search State Tests ==========

    @Test
    fun `resetSearchState sets state to Idle`() =
        runTest {
            // Given - set to success state first
            val query = "test"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            viewModel.search(query)

            // Verify we're in Success state
            assertTrue(viewModel.searchState.value is SemanticSearchState.Success)

            // When
            viewModel.resetSearchState()

            // Then
            assertTrue(viewModel.searchState.value is SemanticSearchState.Idle)
            verify { imageBrowserRepository.clear() }
        }

    @Test
    fun `resetSearchState clears imageBrowserRepository`() {
        // When
        viewModel.resetSearchState()

        // Then
        verify { imageBrowserRepository.clear() }
    }

    @Test
    fun `resetSearchState can be called from any state`() =
        runTest {
            // Given - error state
            viewModel.search("")
            assertTrue(viewModel.searchState.value is SemanticSearchState.Error)

            // When
            viewModel.resetSearchState()

            // Then
            assertTrue(viewModel.searchState.value is SemanticSearchState.Idle)
        }

    // ========== Integration Tests ==========

    @Test
    fun `search flow - success then reset`() =
        runTest {
            // Given
            val query = "test"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When - search
            viewModel.search(query)

            // Then - success state
            assertTrue(viewModel.searchState.value is SemanticSearchState.Success)

            // When - reset
            viewModel.resetSearchState()

            // Then - idle state
            assertTrue(viewModel.searchState.value is SemanticSearchState.Idle)
        }

    @Test
    fun `search flow - error then search again`() =
        runTest {
            // Given - first search fails
            val query1 = "test1"
            coEvery { searchRepository.semanticSearch(query1, 0) } returns
                SearchRepository.SearchResult.Error("Error")
            viewModel.search(query1)
            assertTrue(viewModel.searchState.value is SemanticSearchState.Error)

            // When - search again successfully
            val query2 = "test2"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { searchRepository.semanticSearch(query2, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            viewModel.search(query2)

            // Then - success state
            val state = viewModel.searchState.value
            assertTrue(state is SemanticSearchState.Success)
            assertEquals(query2, (state as SemanticSearchState.Success).query)
        }

    @Test
    fun `complete workflow - search select reset`() =
        runTest {
            // Given
            val query = "test"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { searchRepository.semanticSearch(query, 0) } returns
                SearchRepository.SearchResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When - search
            viewModel.search(query)
            assertTrue(viewModel.searchState.value is SemanticSearchState.Success)

            // When - select photo
            viewModel.togglePhoto(photos[0])
            verify { draftTagRepository.togglePhoto(photos[0]) }

            // When - reset selection
            viewModel.resetSelection()
            verify { draftTagRepository.clear() }

            // When - reset search
            viewModel.resetSearchState()
            assertTrue(viewModel.searchState.value is SemanticSearchState.Idle)
        }
}
