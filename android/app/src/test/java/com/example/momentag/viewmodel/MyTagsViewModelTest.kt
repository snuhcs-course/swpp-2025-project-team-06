package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.TagId
import com.example.momentag.model.TagResponse
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        remoteRepository = mockk(relaxed = true)
        photoSelectionRepository = mockk(relaxed = true)

        // Mock photoSelectionRepository.selectedPhotos flow
        every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyMap())
    }

    private fun createViewModel() {
        viewModel =
            MyTagsViewModel(
                remoteRepository,
                photoSelectionRepository,
            )
    }

    private fun createMockTagResponse(
        tagName: String,
        tagId: String = "id_$tagName",
        photoCount: Int = 0,
    ): TagResponse =
        TagResponse(
            tagName = tagName,
            tagId = tagId,
            thumbnailPhotoPathId = null,
            createdAt = null,
            updatedAt = null,
            photoCount = photoCount,
        )

    private fun createMockPhoto(
        id: String,
        contentUri: Uri = mockk(relaxed = true),
    ): Photo =
        Photo(
            photoId = id,
            contentUri = contentUri,
            createdAt = "2023-01-01",
        )

    // --- Initialization Tests ---

    @Test
    fun `init loads tags automatically`() =
        runTest {
            val tags =
                listOf(
                    createMockTagResponse("Tag1", photoCount = 5),
                    createMockTagResponse("Tag2", photoCount = 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Success)
            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(2, successState.tags.size)
        }

    // --- Load Tags Tests ---

    @Test
    fun `loadTags success updates state with tags`() =
        runTest {
            val tags =
                listOf(
                    createMockTagResponse("Vacation", photoCount = 10),
                    createMockTagResponse("Family", photoCount = 5),
                    createMockTagResponse("Work", photoCount = 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Success)
            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(3, successState.tags.size)
            assertEquals("Vacation", successState.tags[0].tagName)
            assertEquals(10, successState.tags[0].count)
        }

    @Test
    fun `loadTags error updates state with error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Error(500, "Server error")

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Error)
            val errorState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Error
            assertEquals(MyTagsViewModel.MyTagsError.UnknownError, errorState.error)
        }

    @Test
    fun `loadTags unauthorized updates state with unauthorized error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Error)
            val errorState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Error
            assertEquals(MyTagsViewModel.MyTagsError.Unauthorized, errorState.error)
        }

    @Test
    fun `loadTags bad request updates state with unknown error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.BadRequest("Bad request")

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Error)
            val errorState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Error
            assertEquals(MyTagsViewModel.MyTagsError.UnknownError, errorState.error)
        }

    @Test
    fun `loadTags network error updates state with network error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.NetworkError("Network error")

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Error)
            val errorState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Error
            assertEquals(MyTagsViewModel.MyTagsError.NetworkError, errorState.error)
        }

    @Test
    fun `loadTags exception updates state with unknown error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Exception(Exception("Error"))

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Error)
            val errorState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Error
            assertEquals(MyTagsViewModel.MyTagsError.UnknownError, errorState.error)
        }

    @Test
    fun `loadTags handles null photoCount`() =
        runTest {
            val tagResponse =
                TagResponse(
                    tagName = "TestTag",
                    tagId = "test_id",
                    thumbnailPhotoPathId = null,
                    createdAt = null,
                    updatedAt = null,
                    photoCount = 0,
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(listOf(tagResponse))

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Success)
            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(0, successState.tags[0].count)
        }

    @Test
    fun `refreshTags reloads tags`() =
        runTest {
            val initialTags = listOf(createMockTagResponse("Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(initialTags)

            createViewModel()
            advanceUntilIdle()

            val newTags =
                listOf(
                    createMockTagResponse("Tag1"),
                    createMockTagResponse("Tag2"),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(newTags)

            viewModel.refreshTags()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is MyTagsViewModel.MyTagsUiState.Success)
            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(2, successState.tags.size)
        }

    // --- Edit Mode Tests ---

    @Test
    fun `toggleEditMode changes state from false to true`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.isEditMode.value)

            viewModel.toggleEditMode()
            assertTrue(viewModel.isEditMode.value)
        }

    @Test
    fun `toggleEditMode changes state from true to false`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            viewModel.toggleEditMode()
            assertTrue(viewModel.isEditMode.value)

            viewModel.toggleEditMode()
            assertFalse(viewModel.isEditMode.value)
        }

    @Test
    fun `toggleEditMode clears tag selections`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            viewModel.toggleTagSelection("tag1")
            viewModel.toggleTagSelection("tag2")
            assertEquals(2, viewModel.selectedTagsForBulkEdit.value.size)

            viewModel.toggleEditMode()
            assertTrue(viewModel.selectedTagsForBulkEdit.value.isEmpty())
        }

    // --- Tag Selection Tests ---

    @Test
    fun `toggleTagSelection adds tag when not selected`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            viewModel.toggleTagSelection("tag1")
            assertTrue(viewModel.selectedTagsForBulkEdit.value.contains("tag1"))
            assertEquals(1, viewModel.selectedTagsForBulkEdit.value.size)
        }

    @Test
    fun `toggleTagSelection removes tag when already selected`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            viewModel.toggleTagSelection("tag1")
            assertTrue(viewModel.selectedTagsForBulkEdit.value.contains("tag1"))

            viewModel.toggleTagSelection("tag1")
            assertFalse(viewModel.selectedTagsForBulkEdit.value.contains("tag1"))
            assertTrue(viewModel.selectedTagsForBulkEdit.value.isEmpty())
        }

    @Test
    fun `toggleTagSelection handles multiple tags`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            viewModel.toggleTagSelection("tag1")
            viewModel.toggleTagSelection("tag2")
            viewModel.toggleTagSelection("tag3")

            assertEquals(3, viewModel.selectedTagsForBulkEdit.value.size)
            assertTrue(viewModel.selectedTagsForBulkEdit.value.contains("tag1"))
            assertTrue(viewModel.selectedTagsForBulkEdit.value.contains("tag2"))
            assertTrue(viewModel.selectedTagsForBulkEdit.value.contains("tag3"))
        }

    @Test
    fun `clearTagSelection clears all selections`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            viewModel.toggleTagSelection("tag1")
            viewModel.toggleTagSelection("tag2")
            assertEquals(2, viewModel.selectedTagsForBulkEdit.value.size)

            viewModel.clearTagSelection()
            assertTrue(viewModel.selectedTagsForBulkEdit.value.isEmpty())
        }

    // --- Sorting Tests ---

    @Test
    fun `setSortOrder NAME_ASC sorts tags by name ascending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagResponse("Zebra", photoCount = 1),
                    createMockTagResponse("Apple", photoCount = 2),
                    createMockTagResponse("Mango", photoCount = 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            createViewModel()
            advanceUntilIdle()

            viewModel.setSortOrder(TagSortOrder.NAME_ASC)

            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(listOf("Apple", "Mango", "Zebra"), successState.tags.map { it.tagName })
        }

    @Test
    fun `setSortOrder NAME_DESC sorts tags by name descending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagResponse("Apple", photoCount = 1),
                    createMockTagResponse("Zebra", photoCount = 2),
                    createMockTagResponse("Mango", photoCount = 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            createViewModel()
            advanceUntilIdle()

            viewModel.setSortOrder(TagSortOrder.NAME_DESC)

            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(listOf("Zebra", "Mango", "Apple"), successState.tags.map { it.tagName })
        }

    @Test
    fun `setSortOrder COUNT_ASC sorts tags by count ascending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagResponse("Tag1", photoCount = 50),
                    createMockTagResponse("Tag2", photoCount = 5),
                    createMockTagResponse("Tag3", photoCount = 25),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            createViewModel()
            advanceUntilIdle()

            viewModel.setSortOrder(TagSortOrder.COUNT_ASC)

            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(listOf(5, 25, 50), successState.tags.map { it.count })
        }

    @Test
    fun `setSortOrder COUNT_DESC sorts tags by count descending`() =
        runTest {
            val tags =
                listOf(
                    createMockTagResponse("Tag1", photoCount = 5),
                    createMockTagResponse("Tag2", photoCount = 50),
                    createMockTagResponse("Tag3", photoCount = 25),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            createViewModel()
            advanceUntilIdle()

            viewModel.setSortOrder(TagSortOrder.COUNT_DESC)

            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(listOf(50, 25, 5), successState.tags.map { it.count })
        }

    @Test
    fun `setSortOrder CREATED_DESC keeps server order`() =
        runTest {
            val tags =
                listOf(
                    createMockTagResponse("NewestTag", photoCount = 5),
                    createMockTagResponse("MiddleTag", photoCount = 10),
                    createMockTagResponse("OldestTag", photoCount = 3),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tags)

            createViewModel()
            advanceUntilIdle()

            viewModel.setSortOrder(TagSortOrder.CREATED_DESC)

            val successState = viewModel.uiState.value as MyTagsViewModel.MyTagsUiState.Success
            assertEquals(listOf("NewestTag", "MiddleTag", "OldestTag"), successState.tags.map { it.tagName })
        }

    @Test
    fun `sortOrder default is CREATED_DESC`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            assertEquals(TagSortOrder.CREATED_DESC, viewModel.sortOrder.value)
        }

    // --- Delete Tag Tests ---

    @Test
    fun `deleteTag success updates action state and reloads tags`() =
        runTest {
            val initialTags = listOf(createMockTagResponse("Tag1"), createMockTagResponse("Tag2"))
            val tagsAfterDelete = listOf(createMockTagResponse("Tag2"))

            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(initialTags)
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagsAfterDelete)

            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Success)
            val successState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Success
            assertEquals("Deleted", successState.message)

            // Verify tags were reloaded
            coVerify(exactly = 2) { remoteRepository.getAllTags() }
        }

    @Test
    fun `deleteTag error updates action state with delete failed`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Error(500, "Error")

            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.DeleteFailed, errorState.error)
        }

    @Test
    fun `deleteTag unauthorized updates action state with unauthorized error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.Unauthorized, errorState.error)
        }

    @Test
    fun `deleteTag network error updates action state with network error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.NetworkError("Network error")

            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.NetworkError, errorState.error)
        }

    @Test
    fun `deleteTag bad request updates action state with delete failed`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.BadRequest("Bad request")

            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.DeleteFailed, errorState.error)
        }

    @Test
    fun `deleteTag exception updates action state with delete failed`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Exception(Exception("Error"))

            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.DeleteFailed, errorState.error)
        }

    // --- Rename Tag Tests ---

    @Test
    fun `renameTag success updates action state and reloads tags`() =
        runTest {
            val initialTags = listOf(createMockTagResponse("OldName"))
            val tagsAfterRename = listOf(createMockTagResponse("NewName"))

            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(initialTags)
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.renameTag("tag1", "NewName") } returns RemoteRepository.Result.Success(TagId("tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagsAfterRename)

            viewModel.renameTag("tag1", "NewName")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Success)
            val successState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Success
            assertEquals("Updated", successState.message)

            // Verify tags were reloaded
            coVerify(exactly = 2) { remoteRepository.getAllTags() }
        }

    @Test
    fun `renameTag error updates action state with rename failed`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.renameTag("tag1", "NewName") } returns RemoteRepository.Result.Error(500, "Error")

            viewModel.renameTag("tag1", "NewName")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.RenameFailed, errorState.error)
        }

    @Test
    fun `renameTag unauthorized updates action state with unauthorized error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.renameTag("tag1", "NewName") } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.renameTag("tag1", "NewName")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.Unauthorized, errorState.error)
        }

    @Test
    fun `renameTag network error updates action state with network error`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.renameTag("tag1", "NewName") } returns RemoteRepository.Result.NetworkError("Network error")

            viewModel.renameTag("tag1", "NewName")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.NetworkError, errorState.error)
        }

    @Test
    fun `renameTag bad request updates action state with rename failed`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.renameTag("tag1", "NewName") } returns RemoteRepository.Result.BadRequest("Bad request")

            viewModel.renameTag("tag1", "NewName")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.RenameFailed, errorState.error)
        }

    @Test
    fun `renameTag exception updates action state with rename failed`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.renameTag("tag1", "NewName") } returns RemoteRepository.Result.Exception(Exception("Error"))

            viewModel.renameTag("tag1", "NewName")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Error)
            val errorState = viewModel.tagActionState.value as MyTagsViewModel.TagActionState.Error
            assertEquals(MyTagsViewModel.MyTagsError.RenameFailed, errorState.error)
        }

    // --- Action State Tests ---

    @Test
    fun `clearActionState resets to Idle`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Success(Unit)

            viewModel.deleteTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.tagActionState.value is MyTagsViewModel.TagActionState.Success)

            viewModel.clearActionState()
            assertEquals(MyTagsViewModel.TagActionState.Idle, viewModel.tagActionState.value)
        }

    // --- Photo Selection Tests ---

    @Test
    fun `isSelectedPhotosEmpty returns true when no photos selected`() =
        runTest {
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyMap())
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.isSelectedPhotosEmpty())
        }

    @Test
    fun `isSelectedPhotosEmpty returns false when photos selected`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            every { photoSelectionRepository.selectedPhotos } returns
                MutableStateFlow(
                    mapOf("photo1" to photo1),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.isSelectedPhotosEmpty())
        }

    @Test
    fun `clearDraft calls photoSelectionRepository clear`() =
        runTest {
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())
            createViewModel()
            advanceUntilIdle()

            viewModel.clearDraft()

            verify { photoSelectionRepository.clear() }
        }

    // --- Save Photos to Tag Tests ---

    @Test
    fun `savePhotosToExistingTag success saves all photos`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            val photo2 = createMockPhoto("photo2")
            every { photoSelectionRepository.selectedPhotos } returns
                MutableStateFlow(
                    mapOf(
                        "photo1" to photo1,
                        "photo2" to photo2,
                    ),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.postTagsToPhoto("photo2", "tag1") } returns RemoteRepository.Result.Success(Unit)

            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertEquals(MyTagsViewModel.SaveState.Success, viewModel.saveState.value)
            coVerify { remoteRepository.postTagsToPhoto("photo1", "tag1") }
            coVerify { remoteRepository.postTagsToPhoto("photo2", "tag1") }
        }

    @Test
    fun `savePhotosToExistingTag fails when no photos selected`() =
        runTest {
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(emptyMap())
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is MyTagsViewModel.SaveState.Error)
            val errorState = viewModel.saveState.value as MyTagsViewModel.SaveState.Error
            assertEquals(MyTagsViewModel.MyTagsError.UnknownError, errorState.error)
        }

    @Test
    fun `savePhotosToExistingTag handles unauthorized error`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(mapOf("photo1" to photo1))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is MyTagsViewModel.SaveState.Error)
            val errorState = viewModel.saveState.value as MyTagsViewModel.SaveState.Error
            assertEquals(MyTagsViewModel.MyTagsError.Unauthorized, errorState.error)
        }

    @Test
    fun `savePhotosToExistingTag handles network error`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(mapOf("photo1" to photo1))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.NetworkError("Network error")

            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is MyTagsViewModel.SaveState.Error)
            val errorState = viewModel.saveState.value as MyTagsViewModel.SaveState.Error
            assertEquals(MyTagsViewModel.MyTagsError.NetworkError, errorState.error)
        }

    @Test
    fun `savePhotosToExistingTag handles bad request error`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(mapOf("photo1" to photo1))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.BadRequest("Bad request")

            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is MyTagsViewModel.SaveState.Error)
            val errorState = viewModel.saveState.value as MyTagsViewModel.SaveState.Error
            assertEquals(MyTagsViewModel.MyTagsError.UnknownError, errorState.error)
        }

    @Test
    fun `savePhotosToExistingTag handles exception error`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(mapOf("photo1" to photo1))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.Exception(Exception("Error"))

            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is MyTagsViewModel.SaveState.Error)
            val errorState = viewModel.saveState.value as MyTagsViewModel.SaveState.Error
            assertEquals(MyTagsViewModel.MyTagsError.UnknownError, errorState.error)
        }

    @Test
    fun `savePhotosToExistingTag stops on first error`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            val photo2 = createMockPhoto("photo2")
            val photo3 = createMockPhoto("photo3")
            every { photoSelectionRepository.selectedPhotos } returns
                MutableStateFlow(
                    linkedMapOf(
                        "photo1" to photo1,
                        "photo2" to photo2,
                        "photo3" to photo3,
                    ),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.postTagsToPhoto("photo2", "tag1") } returns RemoteRepository.Result.Error(500, "Error")
            coEvery { remoteRepository.postTagsToPhoto("photo3", "tag1") } returns RemoteRepository.Result.Success(Unit)

            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is MyTagsViewModel.SaveState.Error)
            // Verify photo1 was called (before the error)
            coVerify(exactly = 1) { remoteRepository.postTagsToPhoto("photo1", "tag1") }
            // Verify photo2 was called (this is the one that fails)
            coVerify(exactly = 1) { remoteRepository.postTagsToPhoto("photo2", "tag1") }
            // photo3 should not be called because we stop after photo2 error
            coVerify(exactly = 0) { remoteRepository.postTagsToPhoto("photo3", "tag1") }
        }

    @Test
    fun `savePhotosToExistingTag resets error state on retry`() =
        runTest {
            val photo1 = createMockPhoto("photo1")
            every { photoSelectionRepository.selectedPhotos } returns MutableStateFlow(mapOf("photo1" to photo1))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            createViewModel()
            advanceUntilIdle()

            // First attempt fails
            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.Error(500, "Error")
            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is MyTagsViewModel.SaveState.Error)

            // Retry succeeds
            coEvery { remoteRepository.postTagsToPhoto("photo1", "tag1") } returns RemoteRepository.Result.Success(Unit)
            viewModel.savePhotosToExistingTag("tag1")
            advanceUntilIdle()

            assertEquals(MyTagsViewModel.SaveState.Success, viewModel.saveState.value)
        }

    // --- Error Message Resource Tests ---

    @Test
    fun `error toMessageResId returns correct resource IDs`() {
        assertEquals(
            com.example.momentag.R.string.error_message_network,
            MyTagsViewModel.MyTagsError.NetworkError.toMessageResId(),
        )
        assertEquals(
            com.example.momentag.R.string.error_message_login,
            MyTagsViewModel.MyTagsError.Unauthorized.toMessageResId(),
        )
        assertEquals(
            com.example.momentag.R.string.error_message_delete_tag,
            MyTagsViewModel.MyTagsError.DeleteFailed.toMessageResId(),
        )
        assertEquals(
            com.example.momentag.R.string.error_message_rename_tag,
            MyTagsViewModel.MyTagsError.RenameFailed.toMessageResId(),
        )
        assertEquals(
            com.example.momentag.R.string.error_message_unknown,
            MyTagsViewModel.MyTagsError.UnknownError.toMessageResId(),
        )
    }
}
