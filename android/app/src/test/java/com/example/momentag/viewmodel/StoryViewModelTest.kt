package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.StoryResponse
import com.example.momentag.model.StoryStateResponse
import com.example.momentag.model.Tag
import com.example.momentag.model.TagId
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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
class StoryViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: StoryViewModel
    private lateinit var recommendRepository: RecommendRepository
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository

    @Before
    fun setUp() {
        recommendRepository = mockk(relaxed = true)
        localRepository = mockk(relaxed = true)
        remoteRepository = mockk(relaxed = true)
        imageBrowserRepository = mockk(relaxed = true)

        viewModel =
            StoryViewModel(
                recommendRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
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

    private fun createMockStoryResponse(
        photoId: String,
        photoPathId: Long = 1L,
        tags: List<String> = emptyList(),
    ): StoryResponse =
        StoryResponse(
            photoId = photoId,
            photoPathId = photoPathId,
            tags = tags,
        )

    private fun createMockStoryStateResponse(
        status: String = "SUCCESS",
        stories: List<StoryResponse>,
    ): StoryStateResponse =
        StoryStateResponse(
            status = status,
            stories = stories,
        )

    // --- loadStories Tests ---

    @Test
    fun `loadStories success loads stories immediately`() =
        runTest {
            val storyResponses =
                listOf(
                    createMockStoryResponse("1", tags = listOf("tag1", "tag2")),
                    createMockStoryResponse("2", tags = listOf("tag3")),
                )
            val photos = listOf(createMockPhoto("1"), createMockPhoto("2"))
            val storyStateResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyStateResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(5)
            advanceUntilIdle()

            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Success)
            val successState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            assertEquals(2, successState.stories.size)
            assertEquals(0, successState.currentIndex)
            assertTrue(successState.hasMore)
        }

    @Test
    fun `loadStories polls when status is PROCESSING`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val processingResponse = createMockStoryStateResponse("PROCESSING", emptyList())
            val successResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            // First call returns PROCESSING, second call returns SUCCESS
            coEvery { recommendRepository.getStories(5) } returnsMany
                listOf(
                    RecommendRepository.StoryResult.Success(processingResponse),
                    RecommendRepository.StoryResult.Success(successResponse),
                )
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceTimeBy(1100) // Advance past the 1 second delay
            advanceUntilIdle()

            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Success)
            coVerify(atLeast = 2) { recommendRepository.getStories(5) }
        }

    @Test
    fun `loadStories network error sets error state`() =
        runTest {
            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.NetworkError("Network error")

            viewModel.loadStories(5)
            advanceUntilIdle()

            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Error)
            val errorState = viewModel.storyState.value as StoryViewModel.StoryState.Error
            assertEquals(StoryViewModel.StoryError.NetworkError, errorState.error)
        }

    @Test
    fun `loadStories unauthorized sets error state`() =
        runTest {
            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Unauthorized("Unauthorized")

            viewModel.loadStories(5)
            advanceUntilIdle()

            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Error)
            val errorState = viewModel.storyState.value as StoryViewModel.StoryState.Error
            assertEquals(StoryViewModel.StoryError.Unauthorized, errorState.error)
        }

    @Test
    fun `loadStories bad request sets error state`() =
        runTest {
            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.BadRequest("Bad request")

            viewModel.loadStories(5)
            advanceUntilIdle()

            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Error)
            val errorState = viewModel.storyState.value as StoryViewModel.StoryState.Error
            assertEquals(StoryViewModel.StoryError.UnknownError, errorState.error)
        }

    @Test
    fun `loadStories generic error sets error state`() =
        runTest {
            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Error("Server error")

            viewModel.loadStories(5)
            advanceUntilIdle()

            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Error)
            val errorState = viewModel.storyState.value as StoryViewModel.StoryState.Error
            assertEquals(StoryViewModel.StoryError.UnknownError, errorState.error)
        }

    @Test
    fun `loadStories with empty stories creates empty success state`() =
        runTest {
            val storyStateResponse = createMockStoryStateResponse("SUCCESS", emptyList())

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyStateResponse)
            coEvery { localRepository.toPhotos(any()) } returns emptyList()

            viewModel.loadStories(5)
            advanceUntilIdle()

            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Success)
            val successState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            assertTrue(successState.stories.isEmpty())
            assertFalse(successState.hasMore)
        }

    @Test
    fun `loadStories cancels previous polling job`() =
        runTest {
            val processingResponse = createMockStoryStateResponse("PROCESSING", emptyList())
            val successResponse = createMockStoryStateResponse("SUCCESS", emptyList())

            // First loadStories call gets PROCESSING, second loadStories call gets SUCCESS
            coEvery { recommendRepository.getStories(any()) } returnsMany
                listOf(
                    RecommendRepository.StoryResult.Success(processingResponse),
                    RecommendRepository.StoryResult.Success(successResponse),
                )
            coEvery { localRepository.toPhotos(any()) } returns emptyList()

            viewModel.loadStories(5)
            advanceTimeBy(500) // Advance partway through first polling

            // Start a new load - should cancel previous
            viewModel.loadStories(5)
            advanceUntilIdle()

            // Verify at least 2 calls were made (first job and second job)
            coVerify(atLeast = 2) { recommendRepository.getStories(5) }
            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Success)
        }

    // --- loadMoreStories Tests ---

    @Test
    fun `loadMoreStories appends stories to existing list`() =
        runTest {
            // Setup initial stories
            val initialStories = listOf(createMockStoryResponse("1"))
            val initialPhotos = listOf(createMockPhoto("1"))
            val initialResponse = createMockStoryStateResponse("SUCCESS", initialStories)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(initialResponse)
            coEvery { localRepository.toPhotos(any()) } returns initialPhotos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            // Load more stories
            val moreStories = listOf(createMockStoryResponse("2"))
            val morePhotos = listOf(createMockPhoto("2"))
            val moreResponse = createMockStoryStateResponse("SUCCESS", moreStories)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(moreResponse)
            coEvery { localRepository.toPhotos(any()) } returns morePhotos

            viewModel.loadMoreStories(5)
            advanceUntilIdle()

            val successState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            assertEquals(2, successState.stories.size)
        }

    @Test
    fun `loadMoreStories does nothing when state is not Success`() =
        runTest {
            assertEquals(StoryViewModel.StoryState.Idle, viewModel.storyState.value)

            viewModel.loadMoreStories(5)
            advanceUntilIdle()

            assertEquals(StoryViewModel.StoryState.Idle, viewModel.storyState.value)
        }

    @Test
    fun `loadMoreStories silently fails on error`() =
        runTest {
            // Setup initial stories
            val initialStories = listOf(createMockStoryResponse("1"))
            val initialPhotos = listOf(createMockPhoto("1"))
            val initialResponse = createMockStoryStateResponse("SUCCESS", initialStories)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(initialResponse)
            coEvery { localRepository.toPhotos(any()) } returns initialPhotos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            // Load more fails
            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.NetworkError("Network error")

            viewModel.loadMoreStories(5)
            advanceUntilIdle()

            // State should remain Success with initial stories
            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Success)
            val successState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            assertEquals(1, successState.stories.size)
        }

    // --- loadTagsForStory Tests ---

    @Test
    fun `loadTagsForStory loads tags and updates story`() =
        runTest {
            // Setup initial story
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            // Load tags for story
            val tags =
                listOf(
                    Tag(tagName = "NewTag1", tagId = "newtag1"),
                    Tag(tagName = "NewTag2", tagId = "newtag2"),
                )
            coEvery { recommendRepository.recommendTagFromPhoto("1") } returns
                RecommendRepository.RecommendResult.Success(tags)

            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            val updatedState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val updatedStory = updatedState.stories[0]
            assertTrue(updatedStory.suggestedTags.contains("NewTag1"))
            assertTrue(updatedStory.suggestedTags.contains("NewTag2"))
        }

    @Test
    fun `loadTagsForStory does nothing when story already has tags`() =
        runTest {
            // Setup story with pre-populated tags
            val storyResponses = listOf(createMockStoryResponse("1", tags = listOf("ExistingTag")))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            // Should not call recommendTagFromPhoto since story already has tags
            coVerify(exactly = 0) { recommendRepository.recommendTagFromPhoto(any()) }
        }

    @Test
    fun `loadTagsForStory uses cache if available`() =
        runTest {
            // Setup initial story
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            // First load - fetches from repository
            val tags = listOf(Tag(tagName = "CachedTag", tagId = "cachedtag"))
            coEvery { recommendRepository.recommendTagFromPhoto("1") } returns
                RecommendRepository.RecommendResult.Success(tags)

            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            // Second load - should use cache
            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            // Should only be called once (first time)
            coVerify(exactly = 1) { recommendRepository.recommendTagFromPhoto("1") }
        }

    @Test
    fun `loadTagsForStory silently fails on network error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            coEvery { recommendRepository.recommendTagFromPhoto("1") } returns
                RecommendRepository.RecommendResult.NetworkError("Network error")

            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            // Should silently fail - tags remain empty
            val updatedState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val updatedStory = updatedState.stories[0]
            assertTrue(updatedStory.suggestedTags.isEmpty())
        }

    @Test
    fun `loadTagsForStory silently fails on unauthorized error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            coEvery { recommendRepository.recommendTagFromPhoto("1") } returns
                RecommendRepository.RecommendResult.Unauthorized("Unauthorized")

            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            val updatedState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val updatedStory = updatedState.stories[0]
            assertTrue(updatedStory.suggestedTags.isEmpty())
        }

    @Test
    fun `loadTagsForStory silently fails on bad request error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            coEvery { recommendRepository.recommendTagFromPhoto("1") } returns
                RecommendRepository.RecommendResult.BadRequest("Bad request")

            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            val updatedState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val updatedStory = updatedState.stories[0]
            assertTrue(updatedStory.suggestedTags.isEmpty())
        }

    @Test
    fun `loadTagsForStory silently fails on generic error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            coEvery { recommendRepository.recommendTagFromPhoto("1") } returns
                RecommendRepository.RecommendResult.Error("Server error")

            viewModel.loadTagsForStory(storyId, "1")
            advanceUntilIdle()

            val updatedState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val updatedStory = updatedState.stories[0]
            assertTrue(updatedStory.suggestedTags.isEmpty())
        }

    // --- addCustomTagToStory Tests ---

    @Test
    fun `addCustomTagToStory adds tag to story and toggles selection`() =
        runTest {
            // Setup story
            val storyResponses = listOf(createMockStoryResponse("1", tags = listOf("Tag1")))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.addCustomTagToStory(storyId, "CustomTag")

            val updatedState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val updatedStory = updatedState.stories[0]
            assertTrue(updatedStory.suggestedTags.contains("CustomTag"))
            assertTrue(viewModel.getSelectedTags(storyId).contains("CustomTag"))
        }

    // --- toggleTag Tests ---

    @Test
    fun `toggleTag adds tag when not selected`() {
        val storyId = "story1"

        viewModel.toggleTag(storyId, "Tag1")

        assertTrue(viewModel.getSelectedTags(storyId).contains("Tag1"))
    }

    @Test
    fun `toggleTag removes tag when already selected`() {
        val storyId = "story1"

        viewModel.toggleTag(storyId, "Tag1")
        assertTrue(viewModel.getSelectedTags(storyId).contains("Tag1"))

        viewModel.toggleTag(storyId, "Tag1")
        assertFalse(viewModel.getSelectedTags(storyId).contains("Tag1"))
    }

    @Test
    fun `toggleTag handles multiple tags`() {
        val storyId = "story1"

        viewModel.toggleTag(storyId, "Tag1")
        viewModel.toggleTag(storyId, "Tag2")
        viewModel.toggleTag(storyId, "Tag3")

        val selected = viewModel.getSelectedTags(storyId)
        assertEquals(3, selected.size)
        assertTrue(selected.contains("Tag1"))
        assertTrue(selected.contains("Tag2"))
        assertTrue(selected.contains("Tag3"))
    }

    // --- submitTagsForStory Tests ---

    @Test
    fun `submitTagsForStory creates new tags successfully`() =
        runTest {
            // Setup story
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Success(TagId("tagId1"))
            coEvery { remoteRepository.postTagsToPhoto("1", "tagId1") } returns
                RemoteRepository.Result.Success(Unit)

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            assertEquals(
                StoryViewModel.StoryTagSubmissionState.Success,
                viewModel.storyTagSubmissionStates.value[storyId],
            )
        }

    @Test
    fun `submitTagsForStory handles tag creation error`() =
        runTest {
            // Setup story
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Error(500, "Server error")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            assertTrue(
                viewModel.storyTagSubmissionStates.value[storyId] is StoryViewModel.StoryTagSubmissionState.Error,
            )
        }

    @Test
    fun `submitTagsForStory handles tag creation unauthorized error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            val errorState =
                viewModel.storyTagSubmissionStates.value[storyId] as StoryViewModel.StoryTagSubmissionState.Error
            assertEquals(StoryViewModel.StoryError.Unauthorized, errorState.error)
        }

    @Test
    fun `submitTagsForStory handles tag creation network error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.NetworkError("Network error")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            val errorState =
                viewModel.storyTagSubmissionStates.value[storyId] as StoryViewModel.StoryTagSubmissionState.Error
            assertEquals(StoryViewModel.StoryError.NetworkError, errorState.error)
        }

    @Test
    fun `submitTagsForStory handles tag creation exception error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Exception(RuntimeException("Exception"))

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            assertTrue(
                viewModel.storyTagSubmissionStates.value[storyId] is StoryViewModel.StoryTagSubmissionState.Error,
            )
        }

    @Test
    fun `submitTagsForStory handles tag association error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Success(TagId("tagId1"))
            coEvery { remoteRepository.postTagsToPhoto("1", "tagId1") } returns
                RemoteRepository.Result.Error(500, "Server error")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            assertTrue(
                viewModel.storyTagSubmissionStates.value[storyId] is StoryViewModel.StoryTagSubmissionState.Error,
            )
        }

    @Test
    fun `submitTagsForStory handles tag association unauthorized error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Success(TagId("tagId1"))
            coEvery { remoteRepository.postTagsToPhoto("1", "tagId1") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            val errorState =
                viewModel.storyTagSubmissionStates.value[storyId] as StoryViewModel.StoryTagSubmissionState.Error
            assertEquals(StoryViewModel.StoryError.Unauthorized, errorState.error)
        }

    @Test
    fun `submitTagsForStory handles tag association network error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Success(TagId("tagId1"))
            coEvery { remoteRepository.postTagsToPhoto("1", "tagId1") } returns
                RemoteRepository.Result.NetworkError("Network error")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            val errorState =
                viewModel.storyTagSubmissionStates.value[storyId] as StoryViewModel.StoryTagSubmissionState.Error
            assertEquals(StoryViewModel.StoryError.NetworkError, errorState.error)
        }

    @Test
    fun `submitTagsForStory handles tag association exception error`() =
        runTest {
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "NewTag")

            coEvery { remoteRepository.postTags("NewTag") } returns
                RemoteRepository.Result.Success(TagId("tagId1"))
            coEvery { remoteRepository.postTagsToPhoto("1", "tagId1") } returns
                RemoteRepository.Result.Exception(RuntimeException("Exception"))

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            assertTrue(
                viewModel.storyTagSubmissionStates.value[storyId] is StoryViewModel.StoryTagSubmissionState.Error,
            )
        }

    @Test
    fun `submitTagsForStory handles tag removal error`() =
        runTest {
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags = listOf(Tag(tagName = "ExistingTag", tagId = "tag1")),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail)

            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.enterEditMode(storyId, "1")
            advanceUntilIdle()

            // Remove the existing tag
            viewModel.toggleTag(storyId, "ExistingTag")

            coEvery { remoteRepository.removeTagFromPhoto("1", "tag1") } returns
                RemoteRepository.Result.Error(500, "Server error")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            assertTrue(
                viewModel.storyTagSubmissionStates.value[storyId] is StoryViewModel.StoryTagSubmissionState.Error,
            )
        }

    @Test
    fun `submitTagsForStory handles tag removal unauthorized error`() =
        runTest {
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags = listOf(Tag(tagName = "ExistingTag", tagId = "tag1")),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail)

            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.enterEditMode(storyId, "1")
            advanceUntilIdle()

            viewModel.toggleTag(storyId, "ExistingTag")

            coEvery { remoteRepository.removeTagFromPhoto("1", "tag1") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            val errorState =
                viewModel.storyTagSubmissionStates.value[storyId] as StoryViewModel.StoryTagSubmissionState.Error
            assertEquals(StoryViewModel.StoryError.Unauthorized, errorState.error)
        }

    @Test
    fun `submitTagsForStory handles tag removal network error`() =
        runTest {
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags = listOf(Tag(tagName = "ExistingTag", tagId = "tag1")),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail)

            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.enterEditMode(storyId, "1")
            advanceUntilIdle()

            viewModel.toggleTag(storyId, "ExistingTag")

            coEvery { remoteRepository.removeTagFromPhoto("1", "tag1") } returns
                RemoteRepository.Result.NetworkError("Network error")

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            val errorState =
                viewModel.storyTagSubmissionStates.value[storyId] as StoryViewModel.StoryTagSubmissionState.Error
            assertEquals(StoryViewModel.StoryError.NetworkError, errorState.error)
        }

    @Test
    fun `submitTagsForStory handles tag removal exception error`() =
        runTest {
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags = listOf(Tag(tagName = "ExistingTag", tagId = "tag1")),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail)

            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id

            viewModel.enterEditMode(storyId, "1")
            advanceUntilIdle()

            viewModel.toggleTag(storyId, "ExistingTag")

            coEvery { remoteRepository.removeTagFromPhoto("1", "tag1") } returns
                RemoteRepository.Result.Exception(RuntimeException("Exception"))

            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            assertTrue(
                viewModel.storyTagSubmissionStates.value[storyId] is StoryViewModel.StoryTagSubmissionState.Error,
            )
        }

    // --- setCurrentIndex Tests ---

    @Test
    fun `setCurrentIndex updates index`() =
        runTest {
            // Setup story
            val storyResponses = listOf(createMockStoryResponse("1"), createMockStoryResponse("2"))
            val photos = listOf(createMockPhoto("1"), createMockPhoto("2"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            viewModel.setCurrentIndex(1)

            val successState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            assertEquals(1, successState.currentIndex)
        }

    // --- resetSubmissionState Tests ---

    @Test
    fun `resetSubmissionState resets to Idle`() {
        val storyId = "story1"

        viewModel.resetSubmissionState(storyId)

        assertEquals(
            StoryViewModel.StoryTagSubmissionState.Idle,
            viewModel.storyTagSubmissionStates.value[storyId],
        )
    }

    // --- markStoryAsViewed Tests ---

    @Test
    fun `markStoryAsViewed adds story to viewed set`() {
        assertTrue(viewModel.viewedStories.value.isEmpty())

        viewModel.markStoryAsViewed("story1")
        assertTrue(viewModel.viewedStories.value.contains("story1"))

        viewModel.markStoryAsViewed("story2")
        assertTrue(viewModel.viewedStories.value.contains("story2"))
        assertEquals(2, viewModel.viewedStories.value.size)
    }

    // --- enterEditMode Tests ---

    @Test
    fun `enterEditMode loads existing tags and enters edit mode`() =
        runTest {
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags =
                        listOf(
                            Tag(tagName = "ExistingTag1", tagId = "tag1"),
                            Tag(tagName = "ExistingTag2", tagId = "tag2"),
                        ),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail)

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            assertEquals("story1", viewModel.editModeStory.value)
            assertTrue(viewModel.getSelectedTags("story1").contains("ExistingTag1"))
            assertTrue(viewModel.getSelectedTags("story1").contains("ExistingTag2"))
            assertEquals(2, viewModel.originalTags.value["story1"]?.size)
        }

    @Test
    fun `enterEditMode exits previous edit mode`() =
        runTest {
            val photoDetail1 =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags = listOf(Tag(tagName = "Tag1", tagId = "tag1")),
                )
            val photoDetail2 =
                PhotoDetailResponse(
                    photoPathId = 2L,
                    address = null,
                    tags = listOf(Tag(tagName = "Tag2", tagId = "tag2")),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail1)
            coEvery { remoteRepository.getPhotoDetail("2") } returns
                RemoteRepository.Result.Success(photoDetail2)

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            assertEquals("story1", viewModel.editModeStory.value)

            viewModel.enterEditMode("story2", "2")
            advanceUntilIdle()

            assertEquals("story2", viewModel.editModeStory.value)
        }

    @Test
    fun `enterEditMode handles error when fetching photo details`() =
        runTest {
            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Error(500, "Server error")

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            // Edit mode should not be entered on error
            assertNull(viewModel.editModeStory.value)
        }

    @Test
    fun `enterEditMode handles unauthorized error`() =
        runTest {
            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            assertNull(viewModel.editModeStory.value)
        }

    @Test
    fun `enterEditMode handles network error`() =
        runTest {
            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.NetworkError("Network error")

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            assertNull(viewModel.editModeStory.value)
        }

    @Test
    fun `enterEditMode handles exception error`() =
        runTest {
            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Exception(RuntimeException("Exception"))

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            assertNull(viewModel.editModeStory.value)
        }

    // --- exitEditMode Tests ---

    @Test
    fun `exitEditMode restores original tags`() =
        runTest {
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags = listOf(Tag(tagName = "OriginalTag", tagId = "tag1")),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail)

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            // Modify tags
            viewModel.toggleTag("story1", "NewTag")
            assertTrue(viewModel.getSelectedTags("story1").contains("NewTag"))

            viewModel.exitEditMode("story1")

            assertNull(viewModel.editModeStory.value)
            // Should restore to original tags
            assertTrue(viewModel.getSelectedTags("story1").contains("OriginalTag"))
            assertFalse(viewModel.getSelectedTags("story1").contains("NewTag"))
        }

    // --- clearEditMode Tests ---

    @Test
    fun `clearEditMode clears edit mode without restoring`() =
        runTest {
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    address = null,
                    tags = listOf(Tag(tagName = "OriginalTag", tagId = "tag1")),
                )

            coEvery { remoteRepository.getPhotoDetail("1") } returns
                RemoteRepository.Result.Success(photoDetail)

            viewModel.enterEditMode("story1", "1")
            advanceUntilIdle()

            viewModel.toggleTag("story1", "NewTag")

            viewModel.clearEditMode()

            assertNull(viewModel.editModeStory.value)
            // Tags should NOT be restored
            assertTrue(viewModel.getSelectedTags("story1").contains("NewTag"))
        }

    // --- setStoryBrowsingSession Tests ---

    @Test
    fun `setStoryBrowsingSession sets story in repository`() {
        val photo = createMockPhoto("1")

        viewModel.setStoryBrowsingSession(photo)

        verify { imageBrowserRepository.setStory(photo) }
    }

    // --- stopPolling Tests ---

    @Test
    fun `stopPolling cancels active jobs`() =
        runTest {
            val processingResponse = createMockStoryStateResponse("PROCESSING", emptyList())

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(processingResponse)

            viewModel.loadStories(5)
            advanceTimeBy(500) // Start polling

            viewModel.stopPolling()
            advanceUntilIdle()

            // State should be Loading since polling was cancelled
            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Loading)
        }

    // --- resetState Tests ---

    @Test
    fun `resetState clears all state`() =
        runTest {
            // Setup some state
            val storyResponses = listOf(createMockStoryResponse("1"))
            val photos = listOf(createMockPhoto("1"))
            val storyResponse = createMockStoryStateResponse("SUCCESS", storyResponses)

            coEvery { recommendRepository.getStories(5) } returns
                RecommendRepository.StoryResult.Success(storyResponse)
            coEvery { localRepository.toPhotos(any()) } returns photos
            coEvery { localRepository.getPhotoDate(any()) } returns "2023-01-01"
            coEvery { localRepository.getPhotoLocation(any()) } returns ""

            viewModel.loadStories(5)
            advanceUntilIdle()

            val storyId = (viewModel.storyState.value as StoryViewModel.StoryState.Success).stories[0].id
            viewModel.toggleTag(storyId, "Tag1")
            viewModel.markStoryAsViewed(storyId)

            // Reset
            viewModel.resetState()

            assertEquals(StoryViewModel.StoryState.Idle, viewModel.storyState.value)
            assertTrue(viewModel.selectedTags.value.isEmpty())
            assertTrue(viewModel.storyTagSubmissionStates.value.isEmpty())
            assertTrue(viewModel.viewedStories.value.isEmpty())
            assertNull(viewModel.editModeStory.value)
            assertTrue(viewModel.originalTags.value.isEmpty())
        }

    // --- Initial State Tests ---

    @Test
    fun `initial state is Idle`() {
        assertEquals(StoryViewModel.StoryState.Idle, viewModel.storyState.value)
        assertTrue(viewModel.selectedTags.value.isEmpty())
        assertTrue(viewModel.storyTagSubmissionStates.value.isEmpty())
        assertTrue(viewModel.viewedStories.value.isEmpty())
        assertNull(viewModel.editModeStory.value)
        assertTrue(viewModel.originalTags.value.isEmpty())
    }
}
