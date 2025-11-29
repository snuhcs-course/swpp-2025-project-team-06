package com.example.momentag.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.momentag.model.UploadJobState
import com.example.momentag.model.UploadStatus
import com.example.momentag.model.UploadType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UploadStateRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: UploadStateRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = UploadStateRepository(context)
    }

    @After
    fun tearDown() =
        runTest {
            // Clean up all states after each test
            val allStates = repository.getAllStatesFlow().first()
            allStates.forEach { state ->
                repository.removeState(state.jobId)
            }
        }

    // ========== Helper Functions ==========

    private fun createUploadJobState(
        jobId: String = "job1",
        type: UploadType = UploadType.SELECTED_PHOTOS,
        albumId: Long? = null,
        status: UploadStatus = UploadStatus.RUNNING,
        totalPhotoIds: List<Long> = listOf(1L, 2L, 3L),
        failedPhotoIds: List<Long> = emptyList(),
        currentChunkIndex: Int = 0,
        createdAt: Long = System.currentTimeMillis(),
    ) = UploadJobState(
        jobId = jobId,
        type = type,
        albumId = albumId,
        status = status,
        totalPhotoIds = totalPhotoIds,
        failedPhotoIds = failedPhotoIds,
        currentChunkIndex = currentChunkIndex,
        createdAt = createdAt,
    )

    // ========== saveState Tests ==========

    @Test
    fun `saveState saves new state successfully`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertEquals("job1", retrieved?.jobId)
            assertEquals(UploadStatus.RUNNING, retrieved?.status)
            assertEquals(UploadType.SELECTED_PHOTOS, retrieved?.type)
        }

    @Test
    fun `saveState updates existing state`() =
        runTest {
            // Given - save initial state
            val initialState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING, currentChunkIndex = 0)
            repository.saveState(initialState)

            // When - update state
            val updatedState = createUploadJobState(jobId = "job1", status = UploadStatus.PAUSED, currentChunkIndex = 5)
            repository.saveState(updatedState)

            // Then - should have only one state with updated values
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(1, states.size)
                assertEquals("job1", states[0].jobId)
                assertEquals(UploadStatus.PAUSED, states[0].status)
                assertEquals(5, states[0].currentChunkIndex)
            }
        }

    @Test
    fun `saveState preserves all properties`() =
        runTest {
            // Given
            val state =
                createUploadJobState(
                    jobId = "job123",
                    type = UploadType.ALBUM,
                    albumId = 456L,
                    status = UploadStatus.FAILED,
                    totalPhotoIds = listOf(1L, 2L, 3L, 4L, 5L),
                    failedPhotoIds = listOf(2L, 4L),
                    currentChunkIndex = 3,
                    createdAt = 1234567890L,
                )

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job123")
            assertNotNull(retrieved)
            assertEquals("job123", retrieved?.jobId)
            assertEquals(UploadType.ALBUM, retrieved?.type)
            assertEquals(456L, retrieved?.albumId)
            assertEquals(UploadStatus.FAILED, retrieved?.status)
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), retrieved?.totalPhotoIds)
            assertEquals(listOf(2L, 4L), retrieved?.failedPhotoIds)
            assertEquals(3, retrieved?.currentChunkIndex)
            assertEquals(1234567890L, retrieved?.createdAt)
        }

    @Test
    fun `saveState with null albumId saves correctly`() =
        runTest {
            // Given
            val state =
                createUploadJobState(
                    jobId = "job1",
                    type = UploadType.SELECTED_PHOTOS,
                    albumId = null,
                )

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertNull(retrieved?.albumId)
        }

    @Test
    fun `saveState with empty failedPhotoIds saves correctly`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1", failedPhotoIds = emptyList())

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertEquals(emptyList<Long>(), retrieved?.failedPhotoIds)
        }

    @Test
    fun `saveState with multiple states saves all`() =
        runTest {
            // Given
            val state1 = createUploadJobState(jobId = "job1")
            val state2 = createUploadJobState(jobId = "job2")
            val state3 = createUploadJobState(jobId = "job3")

            // When
            repository.saveState(state1)
            repository.saveState(state2)
            repository.saveState(state3)

            // Then
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(3, states.size)
                assertTrue(states.any { it.jobId == "job1" })
                assertTrue(states.any { it.jobId == "job2" })
                assertTrue(states.any { it.jobId == "job3" })
            }
        }

    // ========== getState Tests ==========

    @Test
    fun `getState returns null when state does not exist`() =
        runTest {
            // When
            val state = repository.getState("nonexistent")

            // Then
            assertNull(state)
        }

    @Test
    fun `getState returns correct state by jobId`() =
        runTest {
            // Given
            val state1 = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            val state2 = createUploadJobState(jobId = "job2", status = UploadStatus.PAUSED)
            repository.saveState(state1)
            repository.saveState(state2)

            // When
            val retrieved = repository.getState("job2")

            // Then
            assertNotNull(retrieved)
            assertEquals("job2", retrieved?.jobId)
            assertEquals(UploadStatus.PAUSED, retrieved?.status)
        }

    @Test
    fun `getState returns null after state is removed`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1")
            repository.saveState(state)

            // When
            repository.removeState("job1")

            // Then
            assertNull(repository.getState("job1"))
        }

    // ========== removeState Tests ==========

    @Test
    fun `removeState removes existing state`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1")
            repository.saveState(state)

            // When
            repository.removeState("job1")

            // Then
            repository.getAllStatesFlow().test {
                assertEquals(0, awaitItem().size)
            }
        }

    @Test
    fun `removeState does nothing when state does not exist`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1")
            repository.saveState(state)

            // When
            repository.removeState("nonexistent")

            // Then
            repository.getAllStatesFlow().test {
                assertEquals(1, awaitItem().size)
            }
        }

    @Test
    fun `removeState only removes specified state`() =
        runTest {
            // Given
            val state1 = createUploadJobState(jobId = "job1")
            val state2 = createUploadJobState(jobId = "job2")
            val state3 = createUploadJobState(jobId = "job3")
            repository.saveState(state1)
            repository.saveState(state2)
            repository.saveState(state3)

            // When
            repository.removeState("job2")

            // Then
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(2, states.size)
                assertTrue(states.any { it.jobId == "job1" })
                assertTrue(states.none { it.jobId == "job2" })
                assertTrue(states.any { it.jobId == "job3" })
            }
        }

    @Test
    fun `removeState can be called multiple times safely`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1")
            repository.saveState(state)

            // When
            repository.removeState("job1")
            repository.removeState("job1")
            repository.removeState("job1")

            // Then
            repository.getAllStatesFlow().test {
                assertEquals(0, awaitItem().size)
            }
        }

    // ========== getAllActiveStates Tests ==========

    @Test
    fun `getAllActiveStates returns empty list when no states exist`() =
        runTest {
            // When
            val activeStates = repository.getAllActiveStates()

            // Then
            assertEquals(0, activeStates.size)
        }

    @Test
    fun `getAllActiveStates filters out completed states`() =
        runTest {
            // Given
            val runningState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            val completedState = createUploadJobState(jobId = "job2", status = UploadStatus.COMPLETED)
            repository.saveState(runningState)
            repository.saveState(completedState)

            // When
            val activeStates = repository.getAllActiveStates()

            // Then
            assertEquals(1, activeStates.size)
            assertEquals("job1", activeStates[0].jobId)
        }

    @Test
    fun `getAllActiveStates filters out cancelled states`() =
        runTest {
            // Given
            val pausedState = createUploadJobState(jobId = "job1", status = UploadStatus.PAUSED)
            val cancelledState = createUploadJobState(jobId = "job2", status = UploadStatus.CANCELLED)
            repository.saveState(pausedState)
            repository.saveState(cancelledState)

            // When
            val activeStates = repository.getAllActiveStates()

            // Then
            assertEquals(1, activeStates.size)
            assertEquals("job1", activeStates[0].jobId)
        }

    @Test
    fun `getAllActiveStates includes running, paused, and failed states`() =
        runTest {
            // Given
            val runningState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            val pausedState = createUploadJobState(jobId = "job2", status = UploadStatus.PAUSED)
            val failedState = createUploadJobState(jobId = "job3", status = UploadStatus.FAILED)
            val completedState = createUploadJobState(jobId = "job4", status = UploadStatus.COMPLETED)
            val cancelledState = createUploadJobState(jobId = "job5", status = UploadStatus.CANCELLED)

            repository.saveState(runningState)
            repository.saveState(pausedState)
            repository.saveState(failedState)
            repository.saveState(completedState)
            repository.saveState(cancelledState)

            // When
            val activeStates = repository.getAllActiveStates()

            // Then
            assertEquals(3, activeStates.size)
            assertTrue(activeStates.any { it.jobId == "job1" && it.status == UploadStatus.RUNNING })
            assertTrue(activeStates.any { it.jobId == "job2" && it.status == UploadStatus.PAUSED })
            assertTrue(activeStates.any { it.jobId == "job3" && it.status == UploadStatus.FAILED })
        }

    @Test
    fun `getAllActiveStates returns empty when all states are completed or cancelled`() =
        runTest {
            // Given
            val completedState = createUploadJobState(jobId = "job1", status = UploadStatus.COMPLETED)
            val cancelledState = createUploadJobState(jobId = "job2", status = UploadStatus.CANCELLED)
            repository.saveState(completedState)
            repository.saveState(cancelledState)

            // When
            val activeStates = repository.getAllActiveStates()

            // Then
            assertEquals(0, activeStates.size)
        }

    // ========== getAllStatesFlow Tests ==========

    @Test
    fun `getAllStatesFlow emits empty list initially`() =
        runTest {
            // When/Then
            repository.getAllStatesFlow().test {
                assertEquals(0, awaitItem().size)
            }
        }

    @Test
    fun `getAllStatesFlow emits updated list after save`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1")

            // When/Then
            repository.getAllStatesFlow().test {
                assertEquals(0, awaitItem().size)

                repository.saveState(state)
                val states = awaitItem()
                assertEquals(1, states.size)
                assertEquals("job1", states[0].jobId)
            }
        }

    @Test
    fun `getAllStatesFlow emits all states`() =
        runTest {
            // Given
            val state1 = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            val state2 = createUploadJobState(jobId = "job2", status = UploadStatus.PAUSED)
            val state3 = createUploadJobState(jobId = "job3", status = UploadStatus.COMPLETED)

            // When
            repository.saveState(state1)
            repository.saveState(state2)
            repository.saveState(state3)

            // Then
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(3, states.size)
            }
        }

    @Test
    fun `getAllStatesFlow emits updated list after remove`() =
        runTest {
            // Given
            val state1 = createUploadJobState(jobId = "job1")
            val state2 = createUploadJobState(jobId = "job2")
            repository.saveState(state1)
            repository.saveState(state2)

            // When/Then
            repository.getAllStatesFlow().test {
                assertEquals(2, awaitItem().size)

                repository.removeState("job1")
                val states = awaitItem()
                assertEquals(1, states.size)
                assertEquals("job2", states[0].jobId)
            }
        }

    @Test
    fun `getAllStatesFlow emits updated list after update`() =
        runTest {
            // Given
            val initialState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            repository.saveState(initialState)

            // When/Then
            repository.getAllStatesFlow().test {
                var states = awaitItem()
                assertEquals(UploadStatus.RUNNING, states[0].status)

                val updatedState = createUploadJobState(jobId = "job1", status = UploadStatus.COMPLETED)
                repository.saveState(updatedState)

                states = awaitItem()
                assertEquals(1, states.size)
                assertEquals(UploadStatus.COMPLETED, states[0].status)
            }
        }

    // ========== cleanupCompletedStates Tests ==========

    @Test
    fun `cleanupCompletedStates removes completed states`() =
        runTest {
            // Given
            val runningState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            val completedState = createUploadJobState(jobId = "job2", status = UploadStatus.COMPLETED)
            repository.saveState(runningState)
            repository.saveState(completedState)

            // When
            repository.cleanupCompletedStates()

            // Then
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(1, states.size)
                assertEquals("job1", states[0].jobId)
            }
        }

    @Test
    fun `cleanupCompletedStates removes cancelled states`() =
        runTest {
            // Given
            val pausedState = createUploadJobState(jobId = "job1", status = UploadStatus.PAUSED)
            val cancelledState = createUploadJobState(jobId = "job2", status = UploadStatus.CANCELLED)
            repository.saveState(pausedState)
            repository.saveState(cancelledState)

            // When
            repository.cleanupCompletedStates()

            // Then
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(1, states.size)
                assertEquals("job1", states[0].jobId)
            }
        }

    @Test
    fun `cleanupCompletedStates preserves active states`() =
        runTest {
            // Given
            val runningState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            val pausedState = createUploadJobState(jobId = "job2", status = UploadStatus.PAUSED)
            val failedState = createUploadJobState(jobId = "job3", status = UploadStatus.FAILED)
            val completedState = createUploadJobState(jobId = "job4", status = UploadStatus.COMPLETED)
            val cancelledState = createUploadJobState(jobId = "job5", status = UploadStatus.CANCELLED)

            repository.saveState(runningState)
            repository.saveState(pausedState)
            repository.saveState(failedState)
            repository.saveState(completedState)
            repository.saveState(cancelledState)

            // When
            repository.cleanupCompletedStates()

            // Then
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(3, states.size)
                assertTrue(states.any { it.jobId == "job1" && it.status == UploadStatus.RUNNING })
                assertTrue(states.any { it.jobId == "job2" && it.status == UploadStatus.PAUSED })
                assertTrue(states.any { it.jobId == "job3" && it.status == UploadStatus.FAILED })
            }
        }

    @Test
    fun `cleanupCompletedStates removes all when all are completed`() =
        runTest {
            // Given
            val completedState1 = createUploadJobState(jobId = "job1", status = UploadStatus.COMPLETED)
            val completedState2 = createUploadJobState(jobId = "job2", status = UploadStatus.COMPLETED)
            repository.saveState(completedState1)
            repository.saveState(completedState2)

            // When
            repository.cleanupCompletedStates()

            // Then
            repository.getAllStatesFlow().test {
                assertEquals(0, awaitItem().size)
            }
        }

    @Test
    fun `cleanupCompletedStates does nothing when no states exist`() =
        runTest {
            // When
            repository.cleanupCompletedStates()

            // Then
            repository.getAllStatesFlow().test {
                assertEquals(0, awaitItem().size)
            }
        }

    @Test
    fun `cleanupCompletedStates does nothing when only active states exist`() =
        runTest {
            // Given
            val runningState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            repository.saveState(runningState)

            // When
            repository.cleanupCompletedStates()

            // Then
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(1, states.size)
                assertEquals("job1", states[0].jobId)
            }
        }

    // ========== JSON Serialization/Deserialization Tests ==========

    @Test
    fun `serialization handles ALBUM upload type correctly`() =
        runTest {
            // Given
            val state =
                createUploadJobState(
                    jobId = "job1",
                    type = UploadType.ALBUM,
                    albumId = 123L,
                )

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertEquals(UploadType.ALBUM, retrieved?.type)
            assertEquals(123L, retrieved?.albumId)
        }

    @Test
    fun `serialization handles SELECTED_PHOTOS upload type correctly`() =
        runTest {
            // Given
            val state =
                createUploadJobState(
                    jobId = "job1",
                    type = UploadType.SELECTED_PHOTOS,
                    albumId = null,
                )

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertEquals(UploadType.SELECTED_PHOTOS, retrieved?.type)
            assertNull(retrieved?.albumId)
        }

    @Test
    fun `serialization handles all upload statuses correctly`() =
        runTest {
            // Test each status
            val statuses =
                listOf(
                    UploadStatus.RUNNING,
                    UploadStatus.PAUSED,
                    UploadStatus.COMPLETED,
                    UploadStatus.FAILED,
                    UploadStatus.CANCELLED,
                )

            statuses.forEachIndexed { index, status ->
                val state = createUploadJobState(jobId = "job$index", status = status)
                repository.saveState(state)
            }

            // Then
            statuses.forEachIndexed { index, expectedStatus ->
                val retrieved = repository.getState("job$index")
                assertNotNull(retrieved)
                assertEquals(expectedStatus, retrieved?.status)
            }
        }

    @Test
    fun `serialization handles large photo ID lists`() =
        runTest {
            // Given
            val largePhotoList = (1L..1000L).toList()
            val state = createUploadJobState(jobId = "job1", totalPhotoIds = largePhotoList)

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertEquals(1000, retrieved?.totalPhotoIds?.size)
            assertEquals(largePhotoList, retrieved?.totalPhotoIds)
        }

    @Test
    fun `serialization handles empty photo ID lists`() =
        runTest {
            // Given
            val state = createUploadJobState(jobId = "job1", totalPhotoIds = emptyList())

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertEquals(emptyList<Long>(), retrieved?.totalPhotoIds)
        }

    @Test
    fun `serialization preserves timestamps correctly`() =
        runTest {
            // Given
            val timestamp = 1234567890123L
            val state = createUploadJobState(jobId = "job1", createdAt = timestamp)

            // When
            repository.saveState(state)

            // Then
            val retrieved = repository.getState("job1")
            assertNotNull(retrieved)
            assertEquals(timestamp, retrieved?.createdAt)
        }

    // ========== Integration/Workflow Tests ==========

    @Test
    fun `workflow - create, update status, cleanup`() =
        runTest {
            // Step 1: Create running upload
            val runningState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING, currentChunkIndex = 0)
            repository.saveState(runningState)

            var activeStates = repository.getAllActiveStates()
            assertEquals(1, activeStates.size)

            // Step 2: Update to paused
            val pausedState = createUploadJobState(jobId = "job1", status = UploadStatus.PAUSED, currentChunkIndex = 5)
            repository.saveState(pausedState)

            activeStates = repository.getAllActiveStates()
            assertEquals(1, activeStates.size)
            assertEquals(UploadStatus.PAUSED, activeStates[0].status)

            // Step 3: Update to completed
            val completedState = createUploadJobState(jobId = "job1", status = UploadStatus.COMPLETED, currentChunkIndex = 10)
            repository.saveState(completedState)

            activeStates = repository.getAllActiveStates()
            assertEquals(0, activeStates.size) // No active states

            // Step 4: Cleanup
            repository.cleanupCompletedStates()

            repository.getAllStatesFlow().test {
                assertEquals(0, awaitItem().size)
            }
        }

    @Test
    fun `workflow - multiple concurrent uploads`() =
        runTest {
            // Given - three uploads in progress
            val upload1 = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING, currentChunkIndex = 2)
            val upload2 = createUploadJobState(jobId = "job2", status = UploadStatus.RUNNING, currentChunkIndex = 5)
            val upload3 = createUploadJobState(jobId = "job3", status = UploadStatus.RUNNING, currentChunkIndex = 1)

            repository.saveState(upload1)
            repository.saveState(upload2)
            repository.saveState(upload3)

            // When - one completes
            repository.saveState(createUploadJobState(jobId = "job2", status = UploadStatus.COMPLETED))

            // Then - two active uploads remain
            var activeStates = repository.getAllActiveStates()
            assertEquals(2, activeStates.size)
            assertTrue(activeStates.any { it.jobId == "job1" })
            assertTrue(activeStates.any { it.jobId == "job3" })

            // When - one fails
            repository.saveState(createUploadJobState(jobId = "job1", status = UploadStatus.FAILED))

            // Then - still two active (failed is active)
            activeStates = repository.getAllActiveStates()
            assertEquals(2, activeStates.size)

            // When - cleanup completed
            repository.cleanupCompletedStates()

            // Then - only failed and running remain
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(2, states.size)
                assertTrue(states.any { it.jobId == "job1" && it.status == UploadStatus.FAILED })
                assertTrue(states.any { it.jobId == "job3" && it.status == UploadStatus.RUNNING })
            }
        }

    @Test
    fun `workflow - failed upload with retry`() =
        runTest {
            // Step 1: Upload fails with some failed photos
            val failedState =
                createUploadJobState(
                    jobId = "job1",
                    status = UploadStatus.FAILED,
                    totalPhotoIds = listOf(1L, 2L, 3L, 4L, 5L),
                    failedPhotoIds = listOf(2L, 4L),
                    currentChunkIndex = 2,
                )
            repository.saveState(failedState)

            // Verify failed state
            var state = repository.getState("job1")
            assertEquals(UploadStatus.FAILED, state?.status)
            assertEquals(2, state?.failedPhotoIds?.size)

            // Step 2: Retry with only failed photos
            val retryState =
                createUploadJobState(
                    jobId = "job1",
                    status = UploadStatus.RUNNING,
                    totalPhotoIds = listOf(2L, 4L),
                    failedPhotoIds = emptyList(),
                    currentChunkIndex = 0,
                )
            repository.saveState(retryState)

            // Verify retry state
            state = repository.getState("job1")
            assertEquals(UploadStatus.RUNNING, state?.status)
            assertEquals(listOf(2L, 4L), state?.totalPhotoIds)
            assertEquals(emptyList<Long>(), state?.failedPhotoIds)

            // Step 3: Complete
            repository.saveState(createUploadJobState(jobId = "job1", status = UploadStatus.COMPLETED))

            // Step 4: Cleanup
            repository.cleanupCompletedStates()

            assertNull(repository.getState("job1"))
        }

    @Test
    fun `workflow - user cancels upload`() =
        runTest {
            // Step 1: Upload running
            val runningState = createUploadJobState(jobId = "job1", status = UploadStatus.RUNNING)
            repository.saveState(runningState)

            assertEquals(1, repository.getAllActiveStates().size)

            // Step 2: User cancels
            val cancelledState = createUploadJobState(jobId = "job1", status = UploadStatus.CANCELLED)
            repository.saveState(cancelledState)

            // Cancelled is not active
            assertEquals(0, repository.getAllActiveStates().size)

            // Step 3: Cleanup removes cancelled
            repository.cleanupCompletedStates()

            assertNull(repository.getState("job1"))
        }

    @Test
    fun `stress test - many state updates`() =
        runTest {
            // Given
            val jobId = "stressJob"

            // When - perform many rapid updates
            repeat(50) { i ->
                val state =
                    createUploadJobState(
                        jobId = jobId,
                        status = if (i % 2 == 0) UploadStatus.RUNNING else UploadStatus.PAUSED,
                        currentChunkIndex = i,
                    )
                repository.saveState(state)
            }

            // Then - should have only one state with the latest update
            repository.getAllStatesFlow().test {
                val states = awaitItem()
                assertEquals(1, states.size)
                assertEquals(jobId, states[0].jobId)
                assertEquals(49, states[0].currentChunkIndex)
                assertEquals(UploadStatus.PAUSED, states[0].status)
            }
        }
}
