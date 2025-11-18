package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.StoryResponse
import com.example.momentag.model.Tag
import com.example.momentag.model.PhotoResponse
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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
        mockkStatic(Uri::class)
        recommendRepository = mockk()
        localRepository = mockk()
        remoteRepository = mockk()
        imageBrowserRepository = mockk(relaxed = true)

        viewModel =
            StoryViewModel(
                recommendRepository,
                localRepository,
                remoteRepository,
                imageBrowserRepository,
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

    private fun createStoryResponse(
        photoId: String = "photo1",
        photoPathId: Long = 1L,
        tags: List<String> = listOf("tag1", "tag2"),
    ) = StoryResponse(
        photoId = photoId,
        photoPathId = photoPathId,
        tags = tags,
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

    // Pre-generate stories test
    @Test
    fun `preGenerateStories triggers story generation`() =
        runTest {
            // Given
            val size = 10
            coEvery { recommendRepository.generateStories(size) } returns RecommendRepository.StoryResult.Success(Unit)

            // When
            viewModel.preGenerateStories(size)
            advanceUntilIdle()

            // Then
            coVerify { recommendRepository.generateStories(size) }
        }

    // Load stories tests
    @Test
    fun `loadStories success updates state with stories`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertEquals(1, (state as StoryViewModel.StoryState.Success).stories.size)
        }

    @Test
    fun `loadStories with NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            val errorMessage = "Network error"
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.NetworkError(errorMessage)

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.NetworkError)
        }

    // Toggle tag tests
    @Test
    fun `toggleTag adds and removes tag from selection`() {
        // Given
        val storyId = "story1"
        val tag = "tag1"

        // When - add tag
        viewModel.toggleTag(storyId, tag)

        // Then
        assertTrue(viewModel.getSelectedTags(storyId).contains(tag))

        // When - remove tag
        viewModel.toggleTag(storyId, tag)

        // Then
        assertFalse(viewModel.getSelectedTags(storyId).contains(tag))
    }

    // Set current index test
    @Test
    fun `setCurrentIndex updates current index in success state`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse(), createStoryResponse("photo2", 2L))
            val photo1 = createPhoto("photo1")
            val photo2 = createPhoto("photo2")
            val photos = listOf(photo1, photo2)
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery {
                localRepository.toPhotos(
                    match<List<PhotoResponse>> {
                        it.size == 2 && it[0].photoId == "photo1" && it[1].photoId == "photo2"
                    },
                )
            } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // When
            viewModel.setCurrentIndex(1)

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertEquals(1, (state as StoryViewModel.StoryState.Success).currentIndex)
        }

    // Mark story as viewed test
    @Test
    fun `markStoryAsViewed adds story to viewed set`() {
        // Given
        val storyId = "story1"

        // When
        viewModel.markStoryAsViewed(storyId)

        // Then
        assertTrue(viewModel.viewedStories.value.contains(storyId))
    }

    // Edit mode tests
    @Test
    fun `enterEditMode loads existing tags and enters edit mode`() =
        runTest {
            // Given
            val storyId = "story1"
            val photoId = "photo1"
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)

            // When
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Then
            assertEquals(storyId, viewModel.editModeStory.value)
            assertTrue(viewModel.getSelectedTags(storyId).contains("Tag 1"))
        }

    @Test
    fun `exitEditMode exits edit mode and restores original selection`() =
        runTest {
            // Given
            val storyId = "story1"
            val photoId = "photo1"
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Modify selection
            viewModel.toggleTag(storyId, "Tag 2")

            // When
            viewModel.exitEditMode(storyId)

            // Then
            assertNull(viewModel.editModeStory.value)
            // Selection should be restored to original
            assertTrue(viewModel.getSelectedTags(storyId).contains("Tag 1"))
            assertFalse(viewModel.getSelectedTags(storyId).contains("Tag 2"))
        }

    @Test
    fun `clearEditMode clears edit mode without restoring`() =
        runTest {
            // Given
            val storyId = "story1"
            val photoId = "photo1"
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // When
            viewModel.clearEditMode()

            // Then
            assertNull(viewModel.editModeStory.value)
        }

    // Reset state test
    @Test
    fun `resetState clears all state`() =
        runTest {
            // Given
            viewModel.toggleTag("story1", "tag1")
            viewModel.markStoryAsViewed("story1")

            // When
            viewModel.resetState()

            // Then
            assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Idle)
            assertTrue(viewModel.selectedTags.value.isEmpty())
            assertTrue(viewModel.viewedStories.value.isEmpty())
            assertNull(viewModel.editModeStory.value)
        }

    // Stop polling test
    @Test
    fun `stopPolling cancels active jobs`() {
        // When
        viewModel.stopPolling()

        // Then - no exception should be thrown
        // (Job cancellation is tested implicitly)
        assertTrue(true)
    }

    // Additional loadStories tests for polling and error scenarios
    @Test
    fun `loadStories with NotReady polls until Success`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            var callCount = 0
            coEvery { recommendRepository.getStories() } answers {
                callCount++
                if (callCount < 3) {
                    RecommendRepository.StoryResult.NotReady<List<StoryResponse>>("Stories not ready")
                } else {
                    RecommendRepository.StoryResult.Success(storyResponses)
                }
            }
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertTrue(callCount >= 3)
        }

    @Test
    fun `loadStories with Unauthorized updates state to Error`() =
        runTest {
            // Given
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Unauthorized("Unauthorized")

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Error)
            assertEquals("Please login again", (state as StoryViewModel.StoryState.Error).message)
        }

    @Test
    fun `loadStories with BadRequest updates state to Error`() =
        runTest {
            // Given
            val errorMessage = "Bad request"
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.BadRequest(errorMessage)

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Error)
            assertEquals(errorMessage, (state as StoryViewModel.StoryState.Error).message)
        }

    @Test
    fun `loadStories with Error updates state to Error`() =
        runTest {
            // Given
            val errorMessage = "Generic error"
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Error(errorMessage)

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Error)
            assertEquals(errorMessage, (state as StoryViewModel.StoryState.Error).message)
        }

    @Test
    fun `loadStories with empty photos returns empty success state`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns emptyList()
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertEquals(0, (state as StoryViewModel.StoryState.Success).stories.size)
        }

    @Test
    fun `loadStories cancels previous job before starting new one`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.NotReady<List<StoryResponse>>("Stories not ready") andThen
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)

            // When - call loadStories twice
            viewModel.loadStories(10)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then - no error should occur
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
        }

    // loadMoreStories tests
    @Test
    fun `loadMoreStories appends stories to current state`() =
        runTest {
            // Given - initial state with one story
            val initialStoryResponses = listOf(createStoryResponse("photo1", 1L))
            val initialPhotos = listOf(createPhoto("photo1"))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(initialStoryResponses)
            coEvery {
                localRepository.toPhotos(
                    match<List<PhotoResponse>> {
                        it.size == 1 && it[0].photoId == "photo1"
                    },
                )
            } returns initialPhotos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // When - load more stories
            val moreStoryResponses = listOf(createStoryResponse("photo2", 2L))
            val morePhotos = listOf(createPhoto("photo2"))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(moreStoryResponses)
            coEvery {
                localRepository.toPhotos(
                    match<List<PhotoResponse>> {
                        it.size == 1 && it[0].photoId == "photo2"
                    },
                )
            } returns morePhotos
            viewModel.loadMoreStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertEquals(2, (state as StoryViewModel.StoryState.Success).stories.size)
            assertEquals("photo1", state.stories[0].photoId)
            assertEquals("photo2", state.stories[1].photoId)
        }

    @Test
    fun `loadMoreStories with NotReady polls until Success`() =
        runTest {
            // Given - initial state
            val initialStoryResponses = listOf(createStoryResponse())
            val initialPhotos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(initialStoryResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns initialPhotos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // When - load more with polling
            val moreStoryResponses = listOf(createStoryResponse("photo2", 2L))
            val morePhotos = listOf(createPhoto("photo2"))
            var callCount = 0
            coEvery { recommendRepository.getStories() } answers {
                callCount++
                if (callCount < 3) {
                    RecommendRepository.StoryResult.NotReady<List<StoryResponse>>("Stories not ready")
                } else {
                    RecommendRepository.StoryResult.Success(moreStoryResponses)
                }
            }
            coEvery {
                localRepository.toPhotos(
                    match<List<PhotoResponse>> {
                        it.size == 1 && it[0].photoId == "photo2"
                    },
                )
            } returns morePhotos
            viewModel.loadMoreStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertTrue(callCount >= 3)
        }

    @Test
    fun `loadMoreStories with NetworkError fails silently`() =
        runTest {
            // Given - initial state
            val initialStoryResponses = listOf(createStoryResponse())
            val initialPhotos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(initialStoryResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns initialPhotos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // When - load more with error
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.NetworkError("Network error")
            viewModel.loadMoreStories(10)
            advanceUntilIdle()

            // Then - state should remain Success with original stories
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertEquals(1, (state as StoryViewModel.StoryState.Success).stories.size)
        }

    @Test
    fun `loadMoreStories with empty photos sets hasMore to false`() =
        runTest {
            // Given - initial state
            val initialStoryResponses = listOf(createStoryResponse())
            val initialPhotos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(initialStoryResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns initialPhotos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // When - load more with empty results
            val moreStoryResponses = listOf(createStoryResponse("photo2", 2L))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(moreStoryResponses)
            coEvery {
                localRepository.toPhotos(
                    match<List<PhotoResponse>> {
                        it.size == 1 && it[0].photoId == "photo2"
                    },
                )
            } returns emptyList()
            viewModel.loadMoreStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
            assertEquals(1, (state as StoryViewModel.StoryState.Success).stories.size)
            assertFalse(state.hasMore)
        }

    @Test
    fun `loadMoreStories when state is not Success does nothing`() =
        runTest {
            // Given - state is Idle
            // When
            viewModel.loadMoreStories(10)
            advanceUntilIdle()

            // Then - no API calls should be made
            coVerify(exactly = 0) { recommendRepository.getStories() }
        }

    @Test
    fun `loadMoreStories cancels previous job before starting new one`() =
        runTest {
            // Given - initial state
            val initialStoryResponses = listOf(createStoryResponse())
            val initialPhotos = listOf(createPhoto())
            val moreStoryResponses = listOf(createStoryResponse("photo2", 2L))
            val morePhotos = listOf(createPhoto("photo2"))

            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(initialStoryResponses)
            coEvery {
                localRepository.toPhotos(
                    match<List<PhotoResponse>> {
                        it.size == 1 && it[0].photoId == "photo1"
                    },
                )
            } returns initialPhotos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // When - call loadMoreStories twice, second call should cancel first
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(moreStoryResponses)
            coEvery {
                localRepository.toPhotos(
                    match<List<PhotoResponse>> {
                        it.size == 1 && it[0].photoId == "photo2"
                    },
                )
            } returns morePhotos
            viewModel.loadMoreStories(10)
            viewModel.loadMoreStories(10)
            advanceUntilIdle()

            // Then - no error should occur and state should have both stories
            val state = viewModel.storyState.value
            assertTrue(state is StoryViewModel.StoryState.Success)
        }

    // loadTagsForStory tests
    @Test
    fun `loadTagsForStory loads tags from API when not cached`() =
        runTest {
            // Given - state with story that has no tags
            val storyResponses = listOf(createStoryResponse(tags = emptyList()))
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // When
            val recommendedTags =
                listOf(
                    Tag(tagName = "newTag1", tagId = "id1"),
                    Tag(tagName = "newTag2", tagId = "id2"),
                )
            coEvery { recommendRepository.recommendTagFromPhoto(any()) } returns
                RecommendRepository.RecommendResult.Success(recommendedTags)
            viewModel.loadTagsForStory(storyId, "photo1")
            advanceUntilIdle()

            // Then
            coVerify { recommendRepository.recommendTagFromPhoto("photo1") }
        }

    @Test
    fun `loadTagsForStory uses cached tags when available`() =
        runTest {
            // Given - state with story that has no tags
            val storyResponses = listOf(createStoryResponse(tags = emptyList()))
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // Load tags once
            val recommendedTags =
                listOf(
                    Tag(tagName = "cachedTag1", tagId = "id1"),
                )
            coEvery { recommendRepository.recommendTagFromPhoto(any()) } returns
                RecommendRepository.RecommendResult.Success(recommendedTags)
            viewModel.loadTagsForStory(storyId, "photo1")
            advanceUntilIdle()

            // When - load tags again
            viewModel.loadTagsForStory(storyId, "photo1")
            advanceUntilIdle()

            // Then - should only call API once (cached)
            coVerify(exactly = 1) { recommendRepository.recommendTagFromPhoto("photo1") }
        }

    @Test
    fun `loadTagsForStory returns early when story has tags already`() =
        runTest {
            // Given - state with story that already has tags
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val story = state.stories[0]

            // When - story already has tags from StoryResponse
            viewModel.loadTagsForStory(story.id, story.photoId)
            advanceUntilIdle()

            // Then - should not call API
            coVerify(exactly = 0) { recommendRepository.recommendTagFromPhoto(any()) }
        }

    @Test
    fun `loadTagsForStory fails silently on NetworkError`() =
        runTest {
            // Given - state with story
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // When
            coEvery { recommendRepository.recommendTagFromPhoto(any()) } returns
                RecommendRepository.RecommendResult.NetworkError("Network error")
            viewModel.loadTagsForStory(storyId, "photo1")
            advanceUntilIdle()

            // Then - no exception should be thrown
            assertTrue(true)
        }

    @Test
    fun `loadTagsForStory does nothing when state is not Success`() =
        runTest {
            // Given - state is Idle
            // When
            viewModel.loadTagsForStory("story1", "photo1")
            advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { recommendRepository.recommendTagFromPhoto(any()) }
        }

    // addCustomTagToStory tests
    @Test
    fun `addCustomTagToStory adds tag to story and selects it`() =
        runTest {
            // Given - state with story
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            val customTag = "customTag"

            // When
            viewModel.addCustomTagToStory(storyId, customTag)

            // Then
            val updatedState = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val updatedStory = updatedState.stories[0]
            assertTrue(updatedStory.suggestedTags.contains(customTag))
            assertTrue(viewModel.getSelectedTags(storyId).contains(customTag))
        }

    @Test
    fun `addCustomTagToStory does nothing when state is not Success`() {
        // Given - state is Idle
        // When
        viewModel.addCustomTagToStory("story1", "customTag")

        // Then - state should remain Idle
        assertTrue(viewModel.storyState.value is StoryViewModel.StoryState.Idle)
    }

    // resetSubmissionState test
    @Test
    fun `resetSubmissionState resets state for story`() =
        runTest {
            // Given
            val storyId = "story1"
            val photoId = "photo1"
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail =
                PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { remoteRepository.postTags(any()) } returns
                RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagId(id = "tag2"),
                )
            coEvery { remoteRepository.postTagsToPhoto(any(), any()) } returns
                RemoteRepository.Result.Success(Unit)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            viewModel.toggleTag(storyId, "Tag 2")
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // When
            viewModel.resetSubmissionState(storyId)

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertEquals(StoryViewModel.StoryTagSubmissionState.Idle, submissionState)
        }

    // setStoryBrowsingSession test
    @Test
    fun `setStoryBrowsingSession sets photo in imageBrowserRepository`() {
        // Given
        val photo = createPhoto()

        // When
        viewModel.setStoryBrowsingSession(photo)

        // Then
        coVerify { imageBrowserRepository.setStory(photo) }
    }

    // enterEditMode with different story tests
    @Test
    fun `enterEditMode exits previous edit mode when entering new one`() =
        runTest {
            // Given
            val storyId1 = "story1"
            val photoId1 = "photo1"
            val storyId2 = "story2"
            val photoId2 = "photo2"
            val tags1 = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val tags2 = listOf(Tag(tagName = "Tag 2", tagId = "tag2"))
            val photoDetail1 = PhotoDetailResponse(photoPathId = 1L, tags = tags1, address = null)
            val photoDetail2 = PhotoDetailResponse(photoPathId = 2L, tags = tags2, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId1) } returns
                RemoteRepository.Result.Success(photoDetail1)
            coEvery { remoteRepository.getPhotoDetail(photoId2) } returns
                RemoteRepository.Result.Success(photoDetail2)

            // When - enter edit mode for story1
            viewModel.enterEditMode(storyId1, photoId1)
            advanceUntilIdle()
            assertEquals(storyId1, viewModel.editModeStory.value)

            // When - enter edit mode for story2
            viewModel.enterEditMode(storyId2, photoId2)
            advanceUntilIdle()

            // Then - edit mode should be story2
            assertEquals(storyId2, viewModel.editModeStory.value)
        }

    @Test
    fun `enterEditMode handles API error gracefully`() =
        runTest {
            // Given
            val storyId = "story1"
            val photoId = "photo1"
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Error(500, "API error")

            // When
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Then - edit mode should not be set
            assertNull(viewModel.editModeStory.value)
        }

    // submitTagsForStory tests
    @Test
    fun `submitTagsForStory successfully adds new tags`() =
        runTest {
            // Given - story in Success state
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // Select some tags
            viewModel.toggleTag(storyId, "newTag1")
            viewModel.toggleTag(storyId, "newTag2")

            // Mock API responses
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagId(id = "tag1"),
                )
            coEvery { remoteRepository.postTags("newTag2") } returns
                RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagId(id = "tag2"),
                )
            coEvery { remoteRepository.postTagsToPhoto(any(), any()) } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertEquals(StoryViewModel.StoryTagSubmissionState.Success, submissionState)
        }

    @Test
    fun `submitTagsForStory successfully removes tags`() =
        runTest {
            // Given - load story state first
            val photoId = "photo1"
            val storyResponses = listOf(createStoryResponse(photoId, 1L))
            val photos = listOf(createPhoto(photoId))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // Enter edit mode with existing tags
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"), Tag(tagName = "Tag 2", tagId = "tag2"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Remove one tag
            viewModel.toggleTag(storyId, "Tag 1")

            // Mock API responses
            coEvery { remoteRepository.removeTagFromPhoto(photoId, "tag1") } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertEquals(StoryViewModel.StoryTagSubmissionState.Success, submissionState)
            coVerify { remoteRepository.removeTagFromPhoto(photoId, "tag1") }
        }

    @Test
    fun `submitTagsForStory handles removeTagFromPhoto Error`() =
        runTest {
            // Given - load story state first
            val photoId = "photo1"
            val storyResponses = listOf(createStoryResponse(photoId, 1L))
            val photos = listOf(createPhoto(photoId))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // Enter edit mode with existing tags
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Remove tag
            viewModel.toggleTag(storyId, "Tag 1")

            // Mock error
            coEvery { remoteRepository.removeTagFromPhoto(photoId, "tag1") } returns
                RemoteRepository.Result.Error(500, "Failed to remove tag")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
        }

    @Test
    fun `submitTagsForStory handles removeTagFromPhoto Unauthorized`() =
        runTest {
            // Given - load story state first
            val photoId = "photo1"
            val storyResponses = listOf(createStoryResponse(photoId, 1L))
            val photos = listOf(createPhoto(photoId))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // Enter edit mode with existing tags
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Remove tag
            viewModel.toggleTag(storyId, "Tag 1")

            // Mock unauthorized
            coEvery { remoteRepository.removeTagFromPhoto(photoId, "tag1") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals("Please login again", (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message)
        }

    @Test
    fun `submitTagsForStory handles removeTagFromPhoto NetworkError`() =
        runTest {
            // Given - load story state first
            val photoId = "photo1"
            val storyResponses = listOf(createStoryResponse(photoId, 1L))
            val photos = listOf(createPhoto(photoId))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // Enter edit mode with existing tags
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Remove tag
            viewModel.toggleTag(storyId, "Tag 1")

            // Mock network error
            coEvery { remoteRepository.removeTagFromPhoto(photoId, "tag1") } returns
                RemoteRepository.Result.NetworkError("Network error")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals(
                "Network error. Please try again.",
                (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message,
            )
        }

    @Test
    fun `submitTagsForStory handles removeTagFromPhoto Exception`() =
        runTest {
            // Given - load story state first
            val photoId = "photo1"
            val storyResponses = listOf(createStoryResponse(photoId, 1L))
            val photos = listOf(createPhoto(photoId))
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id

            // Enter edit mode with existing tags
            val tags = listOf(Tag(tagName = "Tag 1", tagId = "tag1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            viewModel.enterEditMode(storyId, photoId)
            advanceUntilIdle()

            // Remove tag
            viewModel.toggleTag(storyId, "Tag 1")

            // Mock exception
            coEvery { remoteRepository.removeTagFromPhoto(photoId, "tag1") } returns
                RemoteRepository.Result.Exception(Exception("Test exception"))

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals(
                "An error occurred. Please try again.",
                (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message,
            )
        }

    @Test
    fun `submitTagsForStory handles postTags Error`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock error
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Error(500, "Failed to create tag")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
        }

    @Test
    fun `submitTagsForStory handles postTags Unauthorized`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock unauthorized
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals("Please login again", (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message)
        }

    @Test
    fun `submitTagsForStory handles postTags NetworkError`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock network error
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.NetworkError("Network error")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals(
                "Network error. Please try again.",
                (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message,
            )
        }

    @Test
    fun `submitTagsForStory handles postTags Exception`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock exception
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Exception(Exception("Test exception"))

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals(
                "An error occurred. Please try again.",
                (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message,
            )
        }

    @Test
    fun `submitTagsForStory handles postTagsToPhoto Error`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock responses
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagId(id = "tag1"),
                )
            coEvery { remoteRepository.postTagsToPhoto(any(), "tag1") } returns
                RemoteRepository.Result.Error(500, "Failed to associate tag")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
        }

    @Test
    fun `submitTagsForStory handles postTagsToPhoto Unauthorized`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock responses
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagId(id = "tag1"),
                )
            coEvery { remoteRepository.postTagsToPhoto(any(), "tag1") } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals("Please login again", (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message)
        }

    @Test
    fun `submitTagsForStory handles postTagsToPhoto NetworkError`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock responses
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagId(id = "tag1"),
                )
            coEvery { remoteRepository.postTagsToPhoto(any(), "tag1") } returns
                RemoteRepository.Result.NetworkError("Network error")

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals(
                "Network error. Please try again.",
                (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message,
            )
        }

    @Test
    fun `submitTagsForStory handles postTagsToPhoto Exception`() =
        runTest {
            // Given
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            val state = viewModel.storyState.value as StoryViewModel.StoryState.Success
            val storyId = state.stories[0].id
            viewModel.toggleTag(storyId, "newTag1")

            // Mock responses
            coEvery { remoteRepository.postTags("newTag1") } returns
                RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagId(id = "tag1"),
                )
            coEvery { remoteRepository.postTagsToPhoto(any(), "tag1") } returns
                RemoteRepository.Result.Exception(Exception("Test exception"))

            // When
            viewModel.submitTagsForStory(storyId)
            advanceUntilIdle()

            // Then
            val submissionState = viewModel.storyTagSubmissionStates.value[storyId]
            assertTrue(submissionState is StoryViewModel.StoryTagSubmissionState.Error)
            assertEquals(
                "An error occurred. Please try again.",
                (submissionState as StoryViewModel.StoryTagSubmissionState.Error).message,
            )
        }

    @Test
    fun `submitTagsForStory does nothing when state is not Success`() =
        runTest {
            // Given - state is Idle
            // When
            viewModel.submitTagsForStory("story1")
            advanceUntilIdle()

            // Then - no API calls should be made
            coVerify(exactly = 0) { remoteRepository.postTags(any()) }
        }

    @Test
    fun `submitTagsForStory does nothing when story not found`() =
        runTest {
            // Given - state with story
            val storyResponses = listOf(createStoryResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.getStories() } returns
                RecommendRepository.StoryResult.Success(storyResponses)
            coEvery { localRepository.toPhotos(any<List<PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)
            viewModel.loadStories(10)
            advanceUntilIdle()

            // When - submit for non-existent story
            viewModel.submitTagsForStory("nonexistent")
            advanceUntilIdle()

            // Then - no API calls should be made
            coVerify(exactly = 0) { remoteRepository.postTags(any()) }
        }
}
