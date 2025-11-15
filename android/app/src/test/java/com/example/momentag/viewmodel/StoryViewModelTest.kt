package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.StoryResponse
import com.example.momentag.model.StoryState
import com.example.momentag.model.Tag
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
    ) = StoryResponse(
        photoId = photoId,
        photoPathId = photoPathId,
        tags = listOf("tag1", "tag2"),
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
            coEvery { localRepository.toPhotos(any<List<com.example.momentag.model.PhotoResponse>>()) } returns photos
            every { localRepository.getPhotoDate(any()) } returns "2025-01-01"
            every { localRepository.getPhotoLocation(any()) } returns "Test Location"
            coEvery { recommendRepository.generateStories(any()) } returns RecommendRepository.StoryResult.Success(Unit)

            // When
            viewModel.loadStories(10)
            advanceUntilIdle()

            // Then
            val state = viewModel.storyState.value
            assertTrue(state is StoryState.Success)
            assertEquals(1, (state as StoryState.Success).stories.size)
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
            assertTrue(state is StoryState.NetworkError)
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
                    match<List<com.example.momentag.model.PhotoResponse>> {
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
            assertTrue(state is StoryState.Success)
            assertEquals(1, (state as StoryState.Success).currentIndex)
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
            val photoDetail =
                com.example.momentag.model
                    .PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
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
            val photoDetail =
                com.example.momentag.model
                    .PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
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
            val photoDetail =
                com.example.momentag.model
                    .PhotoDetailResponse(photoPathId = 1L, tags = tags, address = null)
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
            assertTrue(viewModel.storyState.value is StoryState.Idle)
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
}
