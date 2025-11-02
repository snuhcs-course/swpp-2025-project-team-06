package com.example.momentag.viewmodel

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.StoryState
import com.example.momentag.model.Tag
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoryViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: StoryViewModel
    private lateinit var mockRecommendRepository: RecommendRepository
    private lateinit var mockLocalRepository: LocalRepository
    private lateinit var mockRemoteRepository: com.example.momentag.repository.RemoteRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockRecommendRepository = mockk(relaxed = true)
        mockLocalRepository = mockk(relaxed = true)
        mockRemoteRepository = mockk(relaxed = true)

        viewModel = StoryViewModel(mockRecommendRepository, mockLocalRepository, mockRemoteRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be Idle`() =
        runTest {
            // Then
            viewModel.storyState.test {
                assertEquals(StoryState.Idle, awaitItem())
            }
        }

    @Test
    fun `loadStories success with stories should update state to Success`() =
        runTest {
            // Given
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                    PhotoResponse(photoId = "photo2", photoPathId = 2L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                    Photo(photoId = "photo2", contentUri = Uri.parse("content://media/2")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            // When
            viewModel.loadStories(2)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertEquals(2, state.stories.size)
                assertEquals("photo1", state.stories[0].photoId)
                assertEquals("photo2", state.stories[1].photoId)
                assertEquals(0, state.currentIndex)
                assertTrue(state.hasMore)
            }

            coVerify { mockRecommendRepository.getStories(2) }
            coVerify { mockLocalRepository.toPhotos(photoResponses) }
        }

    @Test
    fun `loadStories success with empty stories should update state to Success with empty list`() =
        runTest {
            // Given
            val photoResponses = emptyList<PhotoResponse>()
            val photos = emptyList<Photo>()

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.loadStories(2)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertTrue(state.stories.isEmpty())
                assertEquals(0, state.currentIndex)
                assertEquals(false, state.hasMore)
            }
        }

    @Test
    fun `loadStories network error should update state to NetworkError`() =
        runTest {
            // Given
            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.NetworkError("Network error")

            // When
            viewModel.loadStories(2)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.NetworkError
                assertEquals("Network error", state.message)
            }
        }

    @Test
    fun `loadStories unauthorized should update state to Error`() =
        runTest {
            // Given
            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Unauthorized("Unauthorized")

            // When
            viewModel.loadStories(2)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Error
                assertEquals("Please login again", state.message)
            }
        }

    @Test
    fun `loadStories bad request should update state to Error`() =
        runTest {
            // Given
            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.BadRequest("Bad request")

            // When
            viewModel.loadStories(2)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Error
                assertEquals("Bad request", state.message)
            }
        }

    @Test
    fun `loadStories generic error should update state to Error`() =
        runTest {
            // Given
            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Error("Unknown error")

            // When
            viewModel.loadStories(2)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Error
                assertEquals("Unknown error", state.message)
            }
        }

    @Test
    fun `loadMoreStories success should append stories to existing list`() =
        runTest {
            // Given - initial stories loaded
            val initialPhotoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val initialPhotos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(initialPhotoResponses)
            coEvery { mockLocalRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Given - new stories to load
            val newPhotoResponses =
                listOf(
                    PhotoResponse(photoId = "photo2", photoPathId = 2L),
                )
            val newPhotos =
                listOf(
                    Photo(photoId = "photo2", contentUri = Uri.parse("content://media/2")),
                )

            coEvery { mockRecommendRepository.getStories(1) } returns
                RecommendRepository.RecommendResult.Success(newPhotoResponses)
            coEvery { mockLocalRepository.toPhotos(newPhotoResponses) } returns newPhotos

            // When
            viewModel.loadMoreStories(1)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertEquals(2, state.stories.size)
                assertEquals("photo1", state.stories[0].photoId)
                assertEquals("photo2", state.stories[1].photoId)
                assertTrue(state.hasMore)
            }
        }

    @Test
    fun `loadMoreStories when not in Success state should not load`() =
        runTest {
            // Given - state is Idle
            // When
            viewModel.loadMoreStories(2)
            advanceUntilIdle()

            // Then - no API call should be made
            coVerify(exactly = 0) { mockRecommendRepository.getStories(any()) }
        }

    @Test
    fun `loadMoreStories with empty result should set hasMore to false`() =
        runTest {
            // Given - initial stories loaded
            val initialPhotoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val initialPhotos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(initialPhotoResponses)
            coEvery { mockLocalRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Given - no new stories
            coEvery { mockRecommendRepository.getStories(2) } returns
                RecommendRepository.RecommendResult.Success(emptyList())
            coEvery { mockLocalRepository.toPhotos(emptyList()) } returns emptyList()

            // When
            viewModel.loadMoreStories(2)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertEquals(1, state.stories.size) // Only initial story
                assertEquals(false, state.hasMore)
            }
        }

    @Test
    fun `loadMoreStories error should silently fail without changing state`() =
        runTest {
            // Given - initial stories loaded
            val initialPhotoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val initialPhotos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(initialPhotoResponses)
            coEvery { mockLocalRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            val stateBeforeError = viewModel.storyState.value as StoryState.Success

            // Given - error on loading more
            coEvery { mockRecommendRepository.getStories(2) } returns
                RecommendRepository.RecommendResult.NetworkError("Network error")

            // When
            viewModel.loadMoreStories(2)
            advanceUntilIdle()

            // Then - state should remain unchanged
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertEquals(stateBeforeError.stories.size, state.stories.size)
                assertEquals(stateBeforeError.hasMore, state.hasMore)
            }
        }

    @Test
    fun `loadTagsForStory success should update story with suggested tags`() =
        runTest {
            // Given - initial stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Given - tags to recommend
            val tags =
                listOf(
                    Tag(tagName = "sunset", tagId = "tag1"),
                    Tag(tagName = "beach", tagId = "tag2"),
                )

            coEvery { mockRecommendRepository.recommendTagFromPhoto("photo1") } returns
                RecommendRepository.RecommendResult.Success(tags)

            // When
            viewModel.loadTagsForStory(storyId = "photo1", photoId = "photo1")
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                val story = state.stories[0]
                assertEquals(3, story.suggestedTags.size) // sunset, beach, +
                assertEquals("sunset", story.suggestedTags[0])
                assertEquals("beach", story.suggestedTags[1])
                assertEquals("+", story.suggestedTags[2])
            }
        }

    @Test
    fun `loadTagsForStory should not call API if tags already in cache`() =
        runTest {
            // Given - initial stories loaded with tags already loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            val tags =
                listOf(
                    Tag(tagName = "sunset", tagId = "tag1"),
                )

            coEvery { mockRecommendRepository.recommendTagFromPhoto("photo1") } returns
                RecommendRepository.RecommendResult.Success(tags)

            // Load tags first time
            viewModel.loadTagsForStory(storyId = "photo1", photoId = "photo1")
            advanceUntilIdle()

            // When - load tags second time
            viewModel.loadTagsForStory(storyId = "photo1", photoId = "photo1")
            advanceUntilIdle()

            // Then - API should be called only once
            coVerify(exactly = 1) { mockRecommendRepository.recommendTagFromPhoto("photo1") }
        }

    @Test
    fun `loadTagsForStory should use cached tags to update story when tags are cleared`() =
        runTest {
            // Given - initial stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Load tags first time to populate cache
            val tags =
                listOf(
                    Tag(tagName = "sunset", tagId = "tag1"),
                    Tag(tagName = "beach", tagId = "tag2"),
                )

            coEvery { mockRecommendRepository.recommendTagFromPhoto("photo1") } returns
                RecommendRepository.RecommendResult.Success(tags)

            viewModel.loadTagsForStory(storyId = "photo1", photoId = "photo1")
            advanceUntilIdle()

            // Simulate state change that clears story tags (e.g., pagination)
            viewModel.loadStories(1)
            advanceUntilIdle()

            // When - load tags again (should use cache instead of API)
            viewModel.loadTagsForStory(storyId = "photo1", photoId = "photo1")
            advanceUntilIdle()

            // Then - story should have tags from cache
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                val story = state.stories[0]
                assertEquals(3, story.suggestedTags.size) // sunset, beach, +
                assertEquals("sunset", story.suggestedTags[0])
                assertEquals("beach", story.suggestedTags[1])
                assertEquals("+", story.suggestedTags[2])
            }

            // And API should still be called only once (from first load)
            coVerify(exactly = 1) { mockRecommendRepository.recommendTagFromPhoto("photo1") }
        }

    @Test
    fun `loadTagsForStory error should silently fail and cache empty list`() =
        runTest {
            // Given - initial stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Given - error on tag recommendation
            coEvery { mockRecommendRepository.recommendTagFromPhoto("photo1") } returns
                RecommendRepository.RecommendResult.NetworkError("Network error")

            // When - first attempt fails
            viewModel.loadTagsForStory(storyId = "photo1", photoId = "photo1")
            advanceUntilIdle()

            // Then - story should remain unchanged after error
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                val story = state.stories[0]
                assertTrue(story.suggestedTags.isEmpty())
            }

            // When - try loading again (should use cached empty list without calling API)
            viewModel.loadTagsForStory(storyId = "photo1", photoId = "photo1")
            advanceUntilIdle()

            // Then - API should be called only once
            coVerify(exactly = 1) { mockRecommendRepository.recommendTagFromPhoto("photo1") }

            // And story should now have the "+" button from cached empty list
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                val story = state.stories[0]
                assertEquals(1, story.suggestedTags.size)
                assertEquals("+", story.suggestedTags[0])
            }
        }

    @Test
    fun `toggleTag should add tag when not selected`() =
        runTest {
            // When
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            advanceUntilIdle()

            // Then
            viewModel.selectedTags.test {
                val tags = awaitItem()
                assertTrue(tags["story1"]?.contains("sunset") ?: false)
            }
        }

    @Test
    fun `toggleTag should remove tag when already selected`() =
        runTest {
            // Given - tag already selected
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            advanceUntilIdle()

            // When - toggle again to remove
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            advanceUntilIdle()

            // Then
            viewModel.selectedTags.test {
                val tags = awaitItem()
                assertEquals(false, tags["story1"]?.contains("sunset"))
            }
        }

    @Test
    fun `toggleTag should handle multiple tags for same story`() =
        runTest {
            // When
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            viewModel.toggleTag(storyId = "story1", tag = "beach")
            viewModel.toggleTag(storyId = "story1", tag = "summer")
            advanceUntilIdle()

            // Then
            viewModel.selectedTags.test {
                val tags = awaitItem()
                assertEquals(3, tags["story1"]?.size)
                assertTrue(tags["story1"]?.contains("sunset") ?: false)
                assertTrue(tags["story1"]?.contains("beach") ?: false)
                assertTrue(tags["story1"]?.contains("summer") ?: false)
            }
        }

    @Test
    fun `toggleTag should handle multiple stories independently`() =
        runTest {
            // When
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            viewModel.toggleTag(storyId = "story2", tag = "beach")
            advanceUntilIdle()

            // Then
            viewModel.selectedTags.test {
                val tags = awaitItem()
                assertEquals(1, tags["story1"]?.size)
                assertEquals(1, tags["story2"]?.size)
                assertTrue(tags["story1"]?.contains("sunset") ?: false)
                assertTrue(tags["story2"]?.contains("beach") ?: false)
            }
        }

    @Test
    fun `getSelectedTags should return correct tags for story`() =
        runTest {
            // Given
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            viewModel.toggleTag(storyId = "story1", tag = "beach")
            advanceUntilIdle()

            // When
            val selectedTags = viewModel.getSelectedTags("story1")

            // Then
            assertEquals(2, selectedTags.size)
            assertTrue(selectedTags.contains("sunset"))
            assertTrue(selectedTags.contains("beach"))
        }

    @Test
    fun `getSelectedTags should return empty set for story with no tags`() =
        runTest {
            // When
            val selectedTags = viewModel.getSelectedTags("nonExistentStory")

            // Then
            assertTrue(selectedTags.isEmpty())
        }

    @Test
    fun `submitTagsForStory should create tags and associate with photo`() =
        runTest {
            // Given - stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Given - tags selected
            viewModel.toggleTag(storyId = "photo1", tag = "sunset")
            viewModel.toggleTag(storyId = "photo1", tag = "beach")
            advanceUntilIdle()

            // Given - mock API responses
            coEvery { mockRemoteRepository.postTags("sunset") } returns
                com.example.momentag.repository.RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagCreateResponse(tagId = "tag1"),
                )
            coEvery { mockRemoteRepository.postTags("beach") } returns
                com.example.momentag.repository.RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagCreateResponse(tagId = "tag2"),
                )
            coEvery { mockRemoteRepository.postTagsToPhoto("photo1", "tag1") } returns
                com.example.momentag.repository.RemoteRepository.Result
                    .Success(Unit)
            coEvery { mockRemoteRepository.postTagsToPhoto("photo1", "tag2") } returns
                com.example.momentag.repository.RemoteRepository.Result
                    .Success(Unit)

            // When
            viewModel.submitTagsForStory("photo1")
            advanceUntilIdle()

            // Then - verify API calls
            coVerify { mockRemoteRepository.postTags("sunset") }
            coVerify { mockRemoteRepository.postTags("beach") }
            coVerify { mockRemoteRepository.postTagsToPhoto("photo1", "tag1") }
            coVerify { mockRemoteRepository.postTagsToPhoto("photo1", "tag2") }
        }

    @Test
    fun `submitTagsForStory should do nothing when story not found`() =
        runTest {
            // Given - stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // When - submit tags for non-existent story
            viewModel.submitTagsForStory("nonExistentStory")
            advanceUntilIdle()

            // Then - no API calls should be made
            coVerify(exactly = 0) { mockRemoteRepository.postTags(any()) }
            coVerify(exactly = 0) { mockRemoteRepository.postTagsToPhoto(any(), any()) }
        }

    @Test
    fun `submitTagsForStory should do nothing when not in Success state`() =
        runTest {
            // Given - state is Idle
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            advanceUntilIdle()

            // When
            viewModel.submitTagsForStory("story1")
            advanceUntilIdle()

            // Then - no API calls should be made
            coVerify(exactly = 0) { mockRemoteRepository.postTags(any()) }
            coVerify(exactly = 0) { mockRemoteRepository.postTagsToPhoto(any(), any()) }
        }

    @Test
    fun `submitTagsForStory should stop on tag creation error`() =
        runTest {
            // Given - stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Given - tags selected
            viewModel.toggleTag(storyId = "photo1", tag = "sunset")
            advanceUntilIdle()

            // Given - mock error response
            coEvery { mockRemoteRepository.postTags("sunset") } returns
                com.example.momentag.repository.RemoteRepository.Result
                    .NetworkError("Network error")

            // When
            viewModel.submitTagsForStory("photo1")
            advanceUntilIdle()

            // Then - postTagsToPhoto should not be called
            coVerify { mockRemoteRepository.postTags("sunset") }
            coVerify(exactly = 0) { mockRemoteRepository.postTagsToPhoto(any(), any()) }
        }

    @Test
    fun `submitTagsForStory should stop on photo tagging error`() =
        runTest {
            // Given - stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            advanceUntilIdle()

            // Given - tags selected
            viewModel.toggleTag(storyId = "photo1", tag = "sunset")
            advanceUntilIdle()

            // Given - mock responses
            coEvery { mockRemoteRepository.postTags("sunset") } returns
                com.example.momentag.repository.RemoteRepository.Result.Success(
                    com.example.momentag.model
                        .TagCreateResponse(tagId = "tag1"),
                )
            coEvery { mockRemoteRepository.postTagsToPhoto("photo1", "tag1") } returns
                com.example.momentag.repository.RemoteRepository.Result
                    .NetworkError("Network error")

            // When
            viewModel.submitTagsForStory("photo1")
            advanceUntilIdle()

            // Then - both API calls should be made
            coVerify { mockRemoteRepository.postTags("sunset") }
            coVerify { mockRemoteRepository.postTagsToPhoto("photo1", "tag1") }
        }

    @Test
    fun `setCurrentIndex should update current index`() =
        runTest {
            // Given - stories loaded
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                    PhotoResponse(photoId = "photo2", photoPathId = 2L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                    Photo(photoId = "photo2", contentUri = Uri.parse("content://media/2")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(2)
            advanceUntilIdle()

            // When
            viewModel.setCurrentIndex(1)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertEquals(1, state.currentIndex)
            }
        }

    @Test
    fun `setCurrentIndex should not update when not in Success state`() =
        runTest {
            // Given - state is Idle
            // When
            viewModel.setCurrentIndex(1)
            advanceUntilIdle()

            // Then - state should remain Idle
            viewModel.storyState.test {
                assertEquals(StoryState.Idle, awaitItem())
            }
        }

    @Test
    fun `resetState should clear all state`() =
        runTest {
            // Given - stories loaded and tags selected
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            viewModel.loadStories(1)
            viewModel.toggleTag(storyId = "story1", tag = "sunset")
            advanceUntilIdle()

            // When
            viewModel.resetState()
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                assertEquals(StoryState.Idle, awaitItem())
            }

            viewModel.selectedTags.test {
                assertTrue(awaitItem().isEmpty())
            }
        }

    @Test
    fun `loadStories should emit Loading state before success`() =
        runTest {
            // Given
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(any()) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            // When
            viewModel.storyState.test {
                assertEquals(StoryState.Idle, awaitItem()) // Initial state

                viewModel.loadStories(1)

                assertEquals(StoryState.Loading, awaitItem()) // Loading state
                assertTrue(awaitItem() is StoryState.Success) // Success state
            }
        }

    @Test
    fun `loadStories should set hasMore to true when result size equals requested size`() =
        runTest {
            // Given
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                    PhotoResponse(photoId = "photo2", photoPathId = 2L),
                    PhotoResponse(photoId = "photo3", photoPathId = 3L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                    Photo(photoId = "photo2", contentUri = Uri.parse("content://media/2")),
                    Photo(photoId = "photo3", contentUri = Uri.parse("content://media/3")),
                )

            coEvery { mockRecommendRepository.getStories(3) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            // When
            viewModel.loadStories(3)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertTrue(state.hasMore) // size == 3, so hasMore should be true
            }
        }

    @Test
    fun `loadStories should set hasMore to false when result size is less than requested size`() =
        runTest {
            // Given
            val photoResponses =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                )
            val photos =
                listOf(
                    Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
                )

            coEvery { mockRecommendRepository.getStories(3) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { mockLocalRepository.toPhotos(photoResponses) } returns photos
            coEvery { mockLocalRepository.getPhotoDate(any()) } returns "2024-01-01"
            coEvery { mockLocalRepository.getPhotoLocation(any()) } returns "Seoul, Korea"

            // When
            viewModel.loadStories(3)
            advanceUntilIdle()

            // Then
            viewModel.storyState.test {
                val state = awaitItem() as StoryState.Success
                assertEquals(false, state.hasMore) // size == 1, requested 3, so hasMore should be false
            }
        }
}
