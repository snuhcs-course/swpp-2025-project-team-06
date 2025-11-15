package com.example.momentag.viewmodel

import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.Photo
import com.example.momentag.model.TagCntData
import com.example.momentag.model.TagResponse
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class MyTagsViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: MyTagsViewModel
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var photoSelectionRepository: PhotoSelectionRepository

    @Before
    fun setUp() {
        remoteRepository = mockk()
        photoSelectionRepository = mockk()

        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyList())

        // Mock initial loadTags call in init block
        coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

        viewModel = MyTagsViewModel(remoteRepository, photoSelectionRepository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createTagResponse(
        id: String = "tag1",
        name: String = "Test Tag",
        count: Int = 5,
    ) = TagResponse(
        tagId = id,
        tagName = name,
        photoCount = count,
        thumbnailPhotoPathId = 1L,
        createdAt = "2025-01-01T00:00:00Z",
        updatedAt = "2025-01-01T00:00:00Z",
    )

    // Load tags tests
    @Test
    fun `loadTags success updates uiState with tags`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "Tag 1", 5),
                    createTagResponse("tag2", "Tag 2", 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            // When
            viewModel.loadTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MyTagsUiState.Success)
            assertEquals(2, (state as MyTagsUiState.Success).tags.size)
        }

    @Test
    fun `loadTags error updates uiState with error message`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns
                RemoteRepository.Result.Error(500, "Server error")

            // When
            viewModel.loadTags()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MyTagsUiState.Error)
        }

    // Sort order tests
    @Test
    fun `setSortOrder NAME_ASC sorts tags by name ascending`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "B Tag", 5),
                    createTagResponse("tag2", "A Tag", 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)
            viewModel.loadTags()
            advanceUntilIdle()

            // When
            viewModel.setSortOrder(TagSortOrder.NAME_ASC)

            // Then
            assertEquals(TagSortOrder.NAME_ASC, viewModel.sortOrder.value)
            val state = viewModel.uiState.value as MyTagsUiState.Success
            assertEquals("A Tag", state.tags[0].tagName)
            assertEquals("B Tag", state.tags[1].tagName)
        }

    @Test
    fun `setSortOrder COUNT_DESC sorts tags by count descending`() =
        runTest {
            // Given
            val tags =
                listOf(
                    createTagResponse("tag1", "Tag 1", 3),
                    createTagResponse("tag2", "Tag 2", 10),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)
            viewModel.loadTags()
            advanceUntilIdle()

            // When
            viewModel.setSortOrder(TagSortOrder.COUNT_DESC)

            // Then
            val state = viewModel.uiState.value as MyTagsUiState.Success
            assertEquals(10, state.tags[0].count)
            assertEquals(3, state.tags[1].count)
        }

    // Edit mode tests
    @Test
    fun `toggleEditMode toggles isEditMode and clears selection`() {
        // When
        viewModel.toggleEditMode()

        // Then
        assertTrue(viewModel.isEditMode.value)
        assertTrue(viewModel.selectedTagsForBulkEdit.value.isEmpty())

        // When
        viewModel.toggleEditMode()

        // Then
        assertFalse(viewModel.isEditMode.value)
    }

    // Tag selection tests
    @Test
    fun `toggleTagSelection adds and removes tag from selection`() {
        // When - add to selection
        viewModel.toggleTagSelection("tag1")

        // Then
        assertTrue(viewModel.selectedTagsForBulkEdit.value.contains("tag1"))

        // When - remove from selection
        viewModel.toggleTagSelection("tag1")

        // Then
        assertFalse(viewModel.selectedTagsForBulkEdit.value.contains("tag1"))
    }

    @Test
    fun `clearTagSelection clears all selections`() {
        // Given
        viewModel.toggleTagSelection("tag1")
        viewModel.toggleTagSelection("tag2")

        // When
        viewModel.clearTagSelection()

        // Then
        assertTrue(viewModel.selectedTagsForBulkEdit.value.isEmpty())
    }

    // Delete tag tests
    @Test
    fun `deleteTag success updates state and reloads tags`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { remoteRepository.removeTag(tagId) } returns RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.deleteTag(tagId)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagActionState.value is TagActionState.Success)
            coVerify { remoteRepository.getAllTags() }
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
            val state = viewModel.tagActionState.value
            assertTrue(state is TagActionState.Error)
        }

    // Rename tag tests
    @Test
    fun `renameTag success updates state and reloads tags`() =
        runTest {
            // Given
            val tagId = "tag1"
            val newName = "New Tag Name"
            coEvery { remoteRepository.renameTag(tagId, newName) } returns
                RemoteRepository.Result.Success(com.example.momentag.model.TagId(tagId))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.renameTag(tagId, newName)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagActionState.value is TagActionState.Success)
        }

    // Refresh tags test
    @Test
    fun `refreshTags reloads tags`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.refreshTags()
            advanceUntilIdle()

            // Then
            coVerify(atLeast = 2) { remoteRepository.getAllTags() } // init + refresh
        }

    // Clear action state test
    @Test
    fun `clearActionState resets to Idle`() =
        runTest {
            // Given
            coEvery { remoteRepository.removeTag(any()) } returns RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            // When
            viewModel.clearActionState()

            // Then
            assertTrue(viewModel.tagActionState.value is TagActionState.Idle)
        }
}
