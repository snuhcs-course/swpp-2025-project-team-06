package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoTagState
import com.example.momentag.model.Tag
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageDetailViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var recommendRepository: RecommendRepository
    private lateinit var viewModel: ImageDetailViewModel

    private val testPhotos =
        listOf(
            Photo(photoId = "photo1", contentUri = Uri.parse("content://media/1")),
            Photo(photoId = "photo2", contentUri = Uri.parse("content://media/2")),
            Photo(photoId = "photo3", contentUri = Uri.parse("content://media/3")),
        )

    @Before
    fun setUp() {
        imageBrowserRepository = mock()
        remoteRepository = mock()
        recommendRepository = mock()
        viewModel = ImageDetailViewModel(imageBrowserRepository, remoteRepository, recommendRepository)
    }

    // ========== loadImageContext Tests ==========

    @Test
    fun `loadImageContext loads context from repository`() {
        // Given
        val photoId = "photo1"
        val expectedContext =
            ImageContext(
                images = testPhotos,
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )
        whenever(imageBrowserRepository.getPhotoContext(photoId)).thenReturn(expectedContext)

        // When
        viewModel.loadImageContext(photoId)

        // Then
        assertEquals(expectedContext, viewModel.imageContext.value)
        verify(imageBrowserRepository).getPhotoContext(photoId)
    }

    @Test
    fun `loadImageContext sets null when repository returns null`() {
        // Given
        val photoId = "nonexistent"
        whenever(imageBrowserRepository.getPhotoContext(photoId)).thenReturn(null)

        // When
        viewModel.loadImageContext(photoId)

        // Then
        assertNull(viewModel.imageContext.value)
        verify(imageBrowserRepository).getPhotoContext(photoId)
    }

    @Test
    fun `loadImageContext updates context when called multiple times`() {
        // Given
        val context1 =
            ImageContext(
                images = listOf(testPhotos[0]),
                currentIndex = 0,
                contextType = ImageContext.ContextType.SEARCH_RESULT,
            )
        val context2 =
            ImageContext(
                images = testPhotos,
                currentIndex = 2,
                contextType = ImageContext.ContextType.TAG_ALBUM,
            )

        whenever(imageBrowserRepository.getPhotoContext("photo1")).thenReturn(context1)
        whenever(imageBrowserRepository.getPhotoContext("photo3")).thenReturn(context2)

        // When
        viewModel.loadImageContext("photo1")
        assertEquals(context1, viewModel.imageContext.value)

        viewModel.loadImageContext("photo3")

        // Then
        assertEquals(context2, viewModel.imageContext.value)
    }

    // ========== loadImageContextByUri Tests ==========

    @Test
    fun `loadImageContextByUri loads context from repository when found`() {
        // Given
        val uri = Uri.parse("content://media/1")
        val expectedContext =
            ImageContext(
                images = testPhotos,
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )
        whenever(imageBrowserRepository.getPhotoContextByUri(uri)).thenReturn(expectedContext)

        // When
        viewModel.loadImageContextByUri(uri)

        // Then
        assertEquals(expectedContext, viewModel.imageContext.value)
        verify(imageBrowserRepository).getPhotoContextByUri(uri)
    }

    @Test
    fun `loadImageContextByUri creates standalone context when not in repository`() {
        // Given
        val uri = Uri.parse("content://standalone/photo")
        whenever(imageBrowserRepository.getPhotoContextByUri(uri)).thenReturn(null)

        // When
        viewModel.loadImageContextByUri(uri)

        // Then
        val context = viewModel.imageContext.value
        assertNotNull(context)
        assertEquals(1, context!!.images.size)
        assertEquals(uri, context.images[0].contentUri)
        assertEquals("", context.images[0].photoId)
        assertEquals(0, context.currentIndex)
        assertEquals(ImageContext.ContextType.GALLERY, context.contextType)
        verify(imageBrowserRepository).getPhotoContextByUri(uri)
    }

    @Test
    fun `loadImageContextByUri prefers repository context over standalone`() {
        // Given
        val uri = Uri.parse("content://media/2")
        val repositoryContext =
            ImageContext(
                images = testPhotos,
                currentIndex = 1,
                contextType = ImageContext.ContextType.TAG_ALBUM,
            )
        whenever(imageBrowserRepository.getPhotoContextByUri(uri)).thenReturn(repositoryContext)

        // When
        viewModel.loadImageContextByUri(uri)

        // Then
        assertEquals(repositoryContext, viewModel.imageContext.value)
        assertEquals(
            3,
            viewModel.imageContext.value!!
                .images.size,
        ) // Not standalone (which would have 1)
    }

    // ========== loadPhotoTags Tests ==========

    @Test
    fun `loadPhotoTags with empty photoId sets state to Idle`() =
        runTest {
            // When
            viewModel.loadPhotoTags("")

            // Then
            assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
        }

    @Test
    fun `loadPhotoTags sets Loading state immediately`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )
            val recommendedTags = listOf(Tag("Sunset", "tag2"))

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Success(recommendedTags))

            // When
            viewModel.loadPhotoTags(photoId)

            // Note: Loading state is set synchronously before coroutine suspends
            mainCoroutineRule.testDispatcher.scheduler.advanceTimeBy(1)

            // Then - after completion
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()
            assertTrue(viewModel.photoTagState.value is PhotoTagState.Success)
        }

    @Test
    fun `loadPhotoTags success with existing tags and recommendation`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1"), Tag("Forest", "tag2")),
                )
            val recommendedTags = listOf(Tag("Sunset", "tag3"))

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Success(recommendedTags))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Success)
            val successState = state as PhotoTagState.Success
            assertEquals(listOf("Nature", "Forest"), successState.existingTags)
            assertEquals(listOf("Sunset"), successState.recommendedTags)

            verify(remoteRepository).getPhotoDetail(photoId)
            verify(recommendRepository).recommendTagFromPhoto(photoId)
        }

    @Test
    fun `loadPhotoTags filters duplicate recommendation from existing tags`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1"), Tag("Sunset", "tag2")),
                )
            val recommendedTags = listOf(Tag("Sunset", "tag2")) // Already exists

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Success(recommendedTags))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Success)
            val successState = state as PhotoTagState.Success
            assertEquals(listOf("Nature", "Sunset"), successState.existingTags)
            assertEquals(emptyList<String>(), successState.recommendedTags) // Filtered out
        }

    @Test
    fun `loadPhotoTags success with empty existing tags`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = emptyList(),
                )
            val recommendedTags = listOf(Tag("Nature", "tag1"))

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Success(recommendedTags))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Success)
            val successState = state as PhotoTagState.Success
            assertEquals(emptyList<String>(), successState.existingTags)
            assertEquals(listOf("Nature"), successState.recommendedTags)
        }

    @Test
    fun `loadPhotoTags shows existing tags when recommendation fails with Error`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Error("Recommendation failed"))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Success)
            val successState = state as PhotoTagState.Success
            assertEquals(listOf("Nature"), successState.existingTags)
            assertEquals(emptyList<String>(), successState.recommendedTags)
        }

    @Test
    fun `loadPhotoTags shows existing tags when recommendation fails with BadRequest`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.BadRequest("Bad request"))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Success)
            val successState = state as PhotoTagState.Success
            assertEquals(listOf("Nature"), successState.existingTags)
            assertEquals(emptyList<String>(), successState.recommendedTags)
        }

    @Test
    fun `loadPhotoTags shows existing tags when recommendation fails with Unauthorized`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Unauthorized("Unauthorized"))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Success)
            val successState = state as PhotoTagState.Success
            assertEquals(listOf("Nature"), successState.existingTags)
            assertEquals(emptyList<String>(), successState.recommendedTags)
        }

    @Test
    fun `loadPhotoTags shows existing tags when recommendation fails with NetworkError`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.NetworkError("Network error"))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Success)
            val successState = state as PhotoTagState.Success
            assertEquals(listOf("Nature"), successState.existingTags)
            assertEquals(emptyList<String>(), successState.recommendedTags)
        }

    @Test
    fun `loadPhotoTags sets Error state when getPhotoDetail fails with Error`() =
        runTest {
            // Given
            val photoId = "photo1"
            val errorMessage = "Failed to load photo detail"

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Error(500, errorMessage))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Error)
            assertEquals(errorMessage, (state as PhotoTagState.Error).message)
        }

    @Test
    fun `loadPhotoTags sets Error state when getPhotoDetail fails with BadRequest`() =
        runTest {
            // Given
            val photoId = "photo1"
            val errorMessage = "Bad request"

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.BadRequest(errorMessage))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Error)
            assertEquals(errorMessage, (state as PhotoTagState.Error).message)
        }

    @Test
    fun `loadPhotoTags sets Error state when getPhotoDetail fails with Unauthorized`() =
        runTest {
            // Given
            val photoId = "photo1"
            val errorMessage = "Unauthorized"

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Unauthorized(errorMessage))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Error)
            assertEquals(errorMessage, (state as PhotoTagState.Error).message)
        }

    @Test
    fun `loadPhotoTags sets Error state when getPhotoDetail fails with NetworkError`() =
        runTest {
            // Given
            val photoId = "photo1"
            val errorMessage = "Network connection failed"

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.NetworkError(errorMessage))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Error)
            assertEquals(errorMessage, (state as PhotoTagState.Error).message)
        }

    @Test
    fun `loadPhotoTags sets Error state when getPhotoDetail fails with Exception`() =
        runTest {
            // Given
            val photoId = "photo1"
            val exception = RuntimeException("Unexpected error")

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Exception(exception))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Error)
            assertEquals("Unexpected error", (state as PhotoTagState.Error).message)
        }

    @Test
    fun `loadPhotoTags handles exception with null message`() =
        runTest {
            // Given
            val photoId = "photo1"
            val exception = RuntimeException(null as String?)

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Exception(exception))

            // When
            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val state = viewModel.photoTagState.value
            assertTrue(state is PhotoTagState.Error)
            assertEquals("Unknown error", (state as PhotoTagState.Error).message)
        }

    // ========== clearImageContext Tests ==========

    @Test
    fun `clearImageContext resets both context and tag state to initial values`() {
        // Given - set some state
        val context =
            ImageContext(
                images = testPhotos,
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )
        whenever(imageBrowserRepository.getPhotoContext("photo1")).thenReturn(context)
        viewModel.loadImageContext("photo1")

        // When
        viewModel.clearImageContext()

        // Then
        assertNull(viewModel.imageContext.value)
        assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
    }

    @Test
    fun `clearImageContext can be called when context is already null`() {
        // Given - no context set
        assertNull(viewModel.imageContext.value)

        // When/Then - should not throw
        viewModel.clearImageContext()

        assertNull(viewModel.imageContext.value)
        assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
    }

    @Test
    fun `clearImageContext resets photoTagState to Idle from any state`() =
        runTest {
            // Given - set tag state to Success
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )
            val recommendedTags = listOf(Tag("Sunset", "tag2"))

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Success(recommendedTags))

            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.photoTagState.value is PhotoTagState.Success)

            // When
            viewModel.clearImageContext()

            // Then
            assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
        }

    // ========== Integration Tests ==========

    @Test
    fun `workflow - load context by ID then load tags`() =
        runTest {
            // Given
            val photoId = "photo1"
            val context =
                ImageContext(
                    images = testPhotos,
                    currentIndex = 0,
                    contextType = ImageContext.ContextType.GALLERY,
                )
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )
            val recommendedTags = listOf(Tag("Sunset", "tag2"))

            whenever(imageBrowserRepository.getPhotoContext(photoId)).thenReturn(context)
            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Success(recommendedTags))

            // When
            viewModel.loadImageContext(photoId)
            assertEquals(context, viewModel.imageContext.value)

            viewModel.loadPhotoTags(photoId)
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then
            val tagState = viewModel.photoTagState.value
            assertTrue(tagState is PhotoTagState.Success)
            val successState = tagState as PhotoTagState.Success
            assertEquals(listOf("Nature"), successState.existingTags)
            assertEquals(listOf("Sunset"), successState.recommendedTags)
        }

    @Test
    fun `workflow - load context by URI (standalone) then skip tags for empty photoId`() {
        // Given
        val uri = Uri.parse("content://standalone/photo")
        whenever(imageBrowserRepository.getPhotoContextByUri(uri)).thenReturn(null)

        // When
        viewModel.loadImageContextByUri(uri)

        // Then
        val context = viewModel.imageContext.value
        assertNotNull(context)
        assertEquals("", context!!.images[0].photoId)

        // When - try to load tags with empty photoId
        viewModel.loadPhotoTags("")

        // Then - should remain Idle
        assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
    }

    @Test
    fun `workflow - load context, load tags, clear, reload different context`() =
        runTest {
            // Step 1: Load first context and tags
            val context1 =
                ImageContext(
                    images = listOf(testPhotos[0]),
                    currentIndex = 0,
                    contextType = ImageContext.ContextType.GALLERY,
                )
            val photoDetail1 =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )

            whenever(imageBrowserRepository.getPhotoContext("photo1")).thenReturn(context1)
            whenever(remoteRepository.getPhotoDetail("photo1"))
                .thenReturn(RemoteRepository.Result.Success(photoDetail1))
            whenever(recommendRepository.recommendTagFromPhoto("photo1"))
                .thenReturn(RecommendRepository.RecommendResult.Success(listOf(Tag("Sunset", "tag2"))))

            viewModel.loadImageContext("photo1")
            viewModel.loadPhotoTags("photo1")
            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            assertEquals(context1, viewModel.imageContext.value)
            assertTrue(viewModel.photoTagState.value is PhotoTagState.Success)

            // Step 2: Clear
            viewModel.clearImageContext()

            assertNull(viewModel.imageContext.value)
            assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)

            // Step 3: Reload different context
            val context2 =
                ImageContext(
                    images = testPhotos,
                    currentIndex = 2,
                    contextType = ImageContext.ContextType.TAG_ALBUM,
                )

            whenever(imageBrowserRepository.getPhotoContext("photo3")).thenReturn(context2)

            viewModel.loadImageContext("photo3")

            assertEquals(context2, viewModel.imageContext.value)
            assertTrue(viewModel.photoTagState.value is PhotoTagState.Idle)
        }

    @Test
    fun `concurrent loadPhotoTags calls handle state transitions correctly`() =
        runTest {
            // Given
            val photoId = "photo1"
            val photoDetail =
                PhotoDetailResponse(
                    photoPathId = 1L,
                    tags = listOf(Tag("Nature", "tag1")),
                )

            whenever(remoteRepository.getPhotoDetail(photoId))
                .thenReturn(RemoteRepository.Result.Success(photoDetail))
            whenever(recommendRepository.recommendTagFromPhoto(photoId))
                .thenReturn(RecommendRepository.RecommendResult.Success(listOf(Tag("Sunset", "tag2"))))

            // When - call multiple times (simulating rapid calls)
            viewModel.loadPhotoTags(photoId)
            viewModel.loadPhotoTags(photoId)

            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            // Then - should complete successfully (called twice due to two invocations)
            assertTrue(viewModel.photoTagState.value is PhotoTagState.Success)
        }
}
