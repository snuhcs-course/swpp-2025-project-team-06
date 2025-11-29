package com.example.momentag.repository

import app.cash.turbine.test
import com.example.momentag.model.TagItem
import com.example.momentag.model.TagResponse
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagStateRepositoryTest {
    private lateinit var repository: TagStateRepository
    private lateinit var remoteRepository: RemoteRepository

    @Before
    fun setUp() {
        remoteRepository = mockk()
        repository = TagStateRepository(remoteRepository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Helper Functions ==========

    private fun createTagResponse(
        id: String = "tag1",
        name: String = "TestTag",
        thumbnailPhotoPathId: Long? = null,
        createdAt: String? = "2024-01-01T00:00:00Z",
        updatedAt: String? = "2024-01-01T00:00:00Z",
        photoCount: Int = 0,
    ) = TagResponse(
        tagId = id,
        tagName = name,
        thumbnailPhotoPathId = thumbnailPhotoPathId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        photoCount = photoCount,
    )

    private fun createTagItem(
        id: String = "tag1",
        name: String = "TestTag",
        coverImageId: Long? = null,
        createdAt: String? = "2024-01-01T00:00:00Z",
        updatedAt: String? = "2024-01-01T00:00:00Z",
        photoCount: Int = 0,
    ) = TagItem(
        tagId = id,
        tagName = name,
        coverImageId = coverImageId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        photoCount = photoCount,
    )

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has empty tags list`() =
        runTest {
            // Then
            repository.tags.test {
                assertEquals(emptyList<TagItem>(), awaitItem())
            }
        }

    @Test
    fun `initial loading state is Idle`() =
        runTest {
            // Then
            repository.loadingState.test {
                assertTrue(awaitItem() is TagStateRepository.LoadingState.Idle)
            }
        }

    // ========== loadTags Tests ==========

    @Test
    fun `loadTags sets loading state to Loading then Success`() =
        runTest {
            // Given
            val tagResponses =
                listOf(
                    createTagResponse("tag1", "Tag1"),
                    createTagResponse("tag2", "Tag2"),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)

            // When
            repository.loadingState.test {
                skipItems(1) // Skip initial Idle state
                repository.loadTags()

                // Then
                val loadingState = awaitItem()
                assertTrue(loadingState is TagStateRepository.LoadingState.Loading)

                val successState = awaitItem()
                assertTrue(successState is TagStateRepository.LoadingState.Success)
                assertEquals(2, (successState as TagStateRepository.LoadingState.Success).tags.size)
            }
        }

    @Test
    fun `loadTags success updates tags state`() =
        runTest {
            // Given
            val tagResponses =
                listOf(
                    createTagResponse("tag1", "Tag1", photoCount = 5),
                    createTagResponse("tag2", "Tag2", thumbnailPhotoPathId = 123L, photoCount = 10),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)

            // When
            repository.loadTags()

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(2, tags.size)

                assertEquals("tag1", tags[0].tagId)
                assertEquals("Tag1", tags[0].tagName)
                assertEquals(null, tags[0].coverImageId)
                assertEquals(5, tags[0].photoCount)

                assertEquals("tag2", tags[1].tagId)
                assertEquals("Tag2", tags[1].tagName)
                assertEquals(123L, tags[1].coverImageId)
                assertEquals(10, tags[1].photoCount)
            }
            coVerify { remoteRepository.getAllTags() }
        }

    @Test
    fun `loadTags maps TagResponse to TagItem correctly`() =
        runTest {
            // Given
            val tagResponse =
                createTagResponse(
                    id = "tag123",
                    name = "Vacation",
                    thumbnailPhotoPathId = 456L,
                    createdAt = "2024-06-15T10:00:00Z",
                    updatedAt = "2024-06-20T15:30:00Z",
                    photoCount = 25,
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(listOf(tagResponse))

            // When
            repository.loadTags()

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(1, tags.size)
                val tag = tags[0]
                assertEquals("tag123", tag.tagId)
                assertEquals("Vacation", tag.tagName)
                assertEquals(456L, tag.coverImageId)
                assertEquals("2024-06-15T10:00:00Z", tag.createdAt)
                assertEquals("2024-06-20T15:30:00Z", tag.updatedAt)
                assertEquals(25, tag.photoCount)
            }
        }

    @Test
    fun `loadTags with Error result sets error state`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Error(500, "Server error")

            // When
            repository.loadTags()

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Error)
                assertTrue((state as TagStateRepository.LoadingState.Error).error is TagStateRepository.TagError.UnknownError)
            }
        }

    @Test
    fun `loadTags with Unauthorized result sets Unauthorized error`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            repository.loadTags()

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Error)
                assertTrue((state as TagStateRepository.LoadingState.Error).error is TagStateRepository.TagError.Unauthorized)
            }
        }

    @Test
    fun `loadTags with BadRequest result sets UnknownError`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.BadRequest("Bad request")

            // When
            repository.loadTags()

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Error)
                assertTrue((state as TagStateRepository.LoadingState.Error).error is TagStateRepository.TagError.UnknownError)
            }
        }

    @Test
    fun `loadTags with NetworkError result sets NetworkError`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.NetworkError("Network error")

            // When
            repository.loadTags()

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Error)
                assertTrue((state as TagStateRepository.LoadingState.Error).error is TagStateRepository.TagError.NetworkError)
            }
        }

    @Test
    fun `loadTags with Exception result sets UnknownError`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Exception(Exception("Unknown"))

            // When
            repository.loadTags()

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Error)
                assertTrue((state as TagStateRepository.LoadingState.Error).error is TagStateRepository.TagError.UnknownError)
            }
        }

    @Test
    fun `loadTags with empty list updates state correctly`() =
        runTest {
            // Given
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(emptyList())

            // When
            repository.loadTags()

            // Then
            repository.tags.test {
                assertEquals(emptyList<TagItem>(), awaitItem())
            }
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Success)
                assertEquals(emptyList<TagItem>(), (state as TagStateRepository.LoadingState.Success).tags)
            }
        }

    // ========== deleteTag Tests ==========

    @Test
    fun `deleteTag success removes tag from state`() =
        runTest {
            // Given - setup initial state
            val tagResponses =
                listOf(
                    createTagResponse("tag1", "Tag1"),
                    createTagResponse("tag2", "Tag2"),
                    createTagResponse("tag3", "Tag3"),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            coEvery { remoteRepository.removeTag("tag2") } returns RemoteRepository.Result.Success(Unit)

            // When
            val result = repository.deleteTag("tag2")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(2, tags.size)
                assertEquals("tag1", tags[0].tagId)
                assertEquals("tag3", tags[1].tagId)
            }
            coVerify { remoteRepository.removeTag("tag2") }
        }

    @Test
    fun `deleteTag success updates loading state`() =
        runTest {
            // Given
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Success(Unit)

            // When
            repository.deleteTag("tag1")

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Success)
                assertEquals(emptyList<TagItem>(), (state as TagStateRepository.LoadingState.Success).tags)
            }
        }

    @Test
    fun `deleteTag failure does not modify state`() =
        runTest {
            // Given
            val tagResponses =
                listOf(
                    createTagResponse("tag1", "Tag1"),
                    createTagResponse("tag2", "Tag2"),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Error(500, "Error")

            // When
            val result = repository.deleteTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(2, tags.size) // Still has both tags
            }
        }

    @Test
    fun `deleteTag with Unauthorized does not modify state`() =
        runTest {
            // Given
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            val result = repository.deleteTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(1, tags.size) // Tag still exists
            }
        }

    @Test
    fun `deleteTag non-existent tag does nothing`() =
        runTest {
            // Given
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            coEvery { remoteRepository.removeTag("nonexistent") } returns RemoteRepository.Result.Success(Unit)

            // When
            repository.deleteTag("nonexistent")

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(1, tags.size)
                assertEquals("tag1", tags[0].tagId) // Original tag still there
            }
        }

    // ========== addTag Tests ==========

    @Test
    fun `addTag adds tag to empty list`() =
        runTest {
            // Given
            val newTag = createTagItem("tag1", "NewTag")

            // When
            repository.addTag(newTag)

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(1, tags.size)
                assertEquals("tag1", tags[0].tagId)
                assertEquals("NewTag", tags[0].tagName)
            }
        }

    @Test
    fun `addTag appends to existing tags`() =
        runTest {
            // Given - setup initial state
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            val newTag = createTagItem("tag2", "NewTag")

            // When
            repository.addTag(newTag)

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(2, tags.size)
                assertEquals("tag1", tags[0].tagId)
                assertEquals("tag2", tags[1].tagId)
            }
        }

    @Test
    fun `addTag updates loading state to Success`() =
        runTest {
            // Given
            val newTag = createTagItem("tag1", "NewTag", photoCount = 5)

            // When
            repository.addTag(newTag)

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Success)
                val tags = (state as TagStateRepository.LoadingState.Success).tags
                assertEquals(1, tags.size)
                assertEquals(5, tags[0].photoCount)
            }
        }

    @Test
    fun `addTag preserves all tag properties`() =
        runTest {
            // Given
            val newTag =
                createTagItem(
                    id = "tag123",
                    name = "Vacation",
                    coverImageId = 789L,
                    createdAt = "2024-06-15T10:00:00Z",
                    updatedAt = "2024-06-20T15:30:00Z",
                    photoCount = 15,
                )

            // When
            repository.addTag(newTag)

            // Then
            repository.tags.test {
                val tags = awaitItem()
                val tag = tags[0]
                assertEquals("tag123", tag.tagId)
                assertEquals("Vacation", tag.tagName)
                assertEquals(789L, tag.coverImageId)
                assertEquals("2024-06-15T10:00:00Z", tag.createdAt)
                assertEquals("2024-06-20T15:30:00Z", tag.updatedAt)
                assertEquals(15, tag.photoCount)
            }
        }

    // ========== updateTag Tests ==========

    @Test
    fun `updateTag updates existing tag`() =
        runTest {
            // Given
            val tagResponses = listOf(createTagResponse("tag1", "OriginalName", photoCount = 5))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            val updatedTag = createTagItem("tag1", "UpdatedName", photoCount = 10)

            // When
            repository.updateTag(updatedTag)

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(1, tags.size)
                assertEquals("tag1", tags[0].tagId)
                assertEquals("UpdatedName", tags[0].tagName)
                assertEquals(10, tags[0].photoCount)
            }
        }

    @Test
    fun `updateTag only updates matching tag`() =
        runTest {
            // Given
            val tagResponses =
                listOf(
                    createTagResponse("tag1", "Tag1", photoCount = 5),
                    createTagResponse("tag2", "Tag2", photoCount = 10),
                    createTagResponse("tag3", "Tag3", photoCount = 15),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            val updatedTag = createTagItem("tag2", "UpdatedTag2", photoCount = 20)

            // When
            repository.updateTag(updatedTag)

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(3, tags.size)
                assertEquals("Tag1", tags[0].tagName)
                assertEquals(5, tags[0].photoCount)
                assertEquals("UpdatedTag2", tags[1].tagName)
                assertEquals(20, tags[1].photoCount)
                assertEquals("Tag3", tags[2].tagName)
                assertEquals(15, tags[2].photoCount)
            }
        }

    @Test
    fun `updateTag with non-existent id does nothing`() =
        runTest {
            // Given
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            val updatedTag = createTagItem("nonexistent", "UpdatedName")

            // When
            repository.updateTag(updatedTag)

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(1, tags.size)
                assertEquals("tag1", tags[0].tagId)
                assertEquals("Tag1", tags[0].tagName) // Unchanged
            }
        }

    @Test
    fun `updateTag updates loading state`() =
        runTest {
            // Given
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            val updatedTag = createTagItem("tag1", "UpdatedTag", coverImageId = 999L)

            // When
            repository.updateTag(updatedTag)

            // Then
            repository.loadingState.test {
                val state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Success)
                val tags = (state as TagStateRepository.LoadingState.Success).tags
                assertEquals(1, tags.size)
                assertEquals("UpdatedTag", tags[0].tagName)
                assertEquals(999L, tags[0].coverImageId)
            }
        }

    @Test
    fun `updateTag updates all properties of matching tag`() =
        runTest {
            // Given
            val tagResponses =
                listOf(
                    createTagResponse(
                        "tag1",
                        "OriginalName",
                        thumbnailPhotoPathId = 100L,
                        photoCount = 5,
                    ),
                )
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            val updatedTag =
                createTagItem(
                    id = "tag1",
                    name = "UpdatedName",
                    coverImageId = 200L,
                    createdAt = "2024-07-01T00:00:00Z",
                    updatedAt = "2024-07-15T12:00:00Z",
                    photoCount = 30,
                )

            // When
            repository.updateTag(updatedTag)

            // Then
            repository.tags.test {
                val tags = awaitItem()
                val tag = tags[0]
                assertEquals("tag1", tag.tagId)
                assertEquals("UpdatedName", tag.tagName)
                assertEquals(200L, tag.coverImageId)
                assertEquals("2024-07-01T00:00:00Z", tag.createdAt)
                assertEquals("2024-07-15T12:00:00Z", tag.updatedAt)
                assertEquals(30, tag.photoCount)
            }
        }

    // ========== resetLoadingState Tests ==========

    @Test
    fun `resetLoadingState resets to Idle`() =
        runTest {
            // Given - set to Success state
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            // When
            repository.resetLoadingState()

            // Then
            repository.loadingState.test {
                assertTrue(awaitItem() is TagStateRepository.LoadingState.Idle)
            }
        }

    @Test
    fun `resetLoadingState from Error state resets to Idle`() =
        runTest {
            // Given - set to Error state
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Error(500, "Error")
            repository.loadTags()

            // When
            repository.resetLoadingState()

            // Then
            repository.loadingState.test {
                assertTrue(awaitItem() is TagStateRepository.LoadingState.Idle)
            }
        }

    @Test
    fun `resetLoadingState does not affect tags`() =
        runTest {
            // Given
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            // When
            repository.resetLoadingState()

            // Then
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(1, tags.size)
                assertEquals("tag1", tags[0].tagId)
            }
        }

    // ========== Integration/Workflow Tests ==========

    @Test
    fun `workflow - load then add then delete`() =
        runTest {
            // Load initial tags
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            repository.tags.test {
                assertEquals(1, awaitItem().size)

                // Add a tag
                repository.addTag(createTagItem("tag2", "Tag2"))
                assertEquals(2, awaitItem().size)

                // Delete a tag
                coEvery { remoteRepository.removeTag("tag1") } returns RemoteRepository.Result.Success(Unit)
                repository.deleteTag("tag1")
                val finalTags = awaitItem()
                assertEquals(1, finalTags.size)
                assertEquals("tag2", finalTags[0].tagId)
            }
        }

    @Test
    fun `workflow - load then update then add`() =
        runTest {
            // Load
            val tagResponses = listOf(createTagResponse("tag1", "OriginalName", photoCount = 5))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            repository.tags.test {
                var tags = awaitItem()
                assertEquals("OriginalName", tags[0].tagName)

                // Update
                repository.updateTag(createTagItem("tag1", "UpdatedName", photoCount = 10))
                tags = awaitItem()
                assertEquals("UpdatedName", tags[0].tagName)
                assertEquals(10, tags[0].photoCount)

                // Add
                repository.addTag(createTagItem("tag2", "NewTag"))
                tags = awaitItem()
                assertEquals(2, tags.size)
                assertEquals("UpdatedName", tags[0].tagName)
                assertEquals("NewTag", tags[1].tagName)
            }
        }

    @Test
    fun `workflow - multiple errors then success`() =
        runTest {
            // First attempt - network error
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.NetworkError("Network error")
            repository.loadTags()

            repository.loadingState.test {
                var state = awaitItem()
                assertTrue((state as TagStateRepository.LoadingState.Error).error is TagStateRepository.TagError.NetworkError)

                // Reset and retry - unauthorized
                repository.resetLoadingState()
                state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Idle)

                coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Unauthorized("Unauthorized")
                repository.loadTags()
                skipItems(1) // Skip Loading
                state = awaitItem()
                assertTrue((state as TagStateRepository.LoadingState.Error).error is TagStateRepository.TagError.Unauthorized)

                // Reset and retry - success
                repository.resetLoadingState()
                skipItems(1) // Skip Idle

                val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
                coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
                repository.loadTags()
                skipItems(1) // Skip Loading
                state = awaitItem()
                assertTrue(state is TagStateRepository.LoadingState.Success)
                assertEquals(1, (state as TagStateRepository.LoadingState.Success).tags.size)
            }
        }

    @Test
    fun `workflow - concurrent operations maintain consistency`() =
        runTest {
            // Load initial state
            val tagResponses = listOf(createTagResponse("tag1", "Tag1"))
            coEvery { remoteRepository.getAllTags() } returns RemoteRepository.Result.Success(tagResponses)
            repository.loadTags()

            // Perform multiple operations
            repository.addTag(createTagItem("tag2", "Tag2"))
            repository.addTag(createTagItem("tag3", "Tag3"))
            repository.updateTag(createTagItem("tag1", "UpdatedTag1", photoCount = 20))

            coEvery { remoteRepository.removeTag("tag2") } returns RemoteRepository.Result.Success(Unit)
            repository.deleteTag("tag2")

            // Verify final state
            repository.tags.test {
                val tags = awaitItem()
                assertEquals(2, tags.size)
                assertEquals("tag1", tags[0].tagId)
                assertEquals("UpdatedTag1", tags[0].tagName)
                assertEquals(20, tags[0].photoCount)
                assertEquals("tag3", tags[1].tagId)
            }
        }
}
