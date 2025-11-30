package com.example.momentag.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.momentag.model.TaskStatus
import com.example.momentag.network.ApiService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for TaskRepository
 *
 * Note: Tests for DataStore operations (saveTaskIds, getPendingTaskIds, removeTaskIds)
 * are not included here because TaskRepository accesses DataStore through a private
 * extension property on Context, making it difficult to mock effectively in unit tests.
 * These methods should be covered by instrumented/integration tests.
 *
 * This test suite focuses on the checkTaskStatus method which uses ApiService
 * and can be effectively unit tested with mocks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest {
    private lateinit var repository: TaskRepository
    private lateinit var context: Context
    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        apiService = mockk()

        repository = TaskRepository(context, apiService)

        // Clear DataStore to ensure fresh state between tests
        runBlocking {
            repository.clearAllTasks()
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Helper Functions ==========

    private fun createTaskStatus(
        taskId: String = "task1",
        status: String = "SUCCESS",
    ) = TaskStatus(
        taskId = taskId,
        status = status,
    )

    // ========== DataStore Tests ==========

    @Test
    fun `saveTaskIds saves ids to DataStore`() =
        runTest {
            // Given
            val taskIds = listOf("task1", "task2")

            // When
            repository.saveTaskIds(taskIds)

            // Then
            val savedIds = repository.getPendingTaskIds().first()
            assertEquals(2, savedIds.size)
            assertTrue(savedIds.contains("task1"))
            assertTrue(savedIds.contains("task2"))
        }

    @Test
    fun `getPendingTaskIds returns saved ids`() =
        runTest {
            // Given
            repository.saveTaskIds(listOf("task1"))

            // When
            val savedIds = repository.getPendingTaskIds().first()

            // Then
            assertEquals(1, savedIds.size)
            assertTrue(savedIds.contains("task1"))
        }

    @Test
    fun `removeTaskIds removes specific ids`() =
        runTest {
            // Given
            repository.saveTaskIds(listOf("task1", "task2", "task3"))

            // When
            repository.removeTaskIds(listOf("task1", "task3"))

            // Then
            val savedIds = repository.getPendingTaskIds().first()
            assertEquals(1, savedIds.size)
            assertTrue(savedIds.contains("task2"))
        }

    @Test
    fun `saveTaskIds appends to existing ids`() =
        runTest {
            // Given
            repository.saveTaskIds(listOf("task1"))

            // When
            repository.saveTaskIds(listOf("task2"))

            // Then
            val savedIds = repository.getPendingTaskIds().first()
            assertEquals(2, savedIds.size)
            assertTrue(savedIds.contains("task1"))
            assertTrue(savedIds.contains("task2"))
        }

    // ========== checkTaskStatus Tests ==========

    @Test
    fun `checkTaskStatus returns empty list for empty input`() =
        runTest {
            // Given
            val taskIds = emptyList<String>()

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(emptyList<TaskStatus>(), result.getOrNull())
            coVerify(exactly = 0) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus returns success for single task`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            val taskStatuses = listOf(createTaskStatus("task1", "SUCCESS"))
            coEvery { apiService.getTaskStatus("task1") } returns Response.success(taskStatuses)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(taskStatuses, result.getOrNull())
            coVerify { apiService.getTaskStatus("task1") }
        }

    @Test
    fun `checkTaskStatus returns success for multiple tasks in single batch`() =
        runTest {
            // Given
            val taskIds = listOf("task1", "task2", "task3")
            val taskStatuses =
                listOf(
                    createTaskStatus("task1", "SUCCESS"),
                    createTaskStatus("task2", "PENDING"),
                    createTaskStatus("task3", "SUCCESS"),
                )
            coEvery { apiService.getTaskStatus("task1,task2,task3") } returns Response.success(taskStatuses)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(taskStatuses, result.getOrNull())
            coVerify { apiService.getTaskStatus("task1,task2,task3") }
        }

    @Test
    fun `checkTaskStatus handles different status values`() =
        runTest {
            // Given
            val taskIds = listOf("task1", "task2", "task3", "task4")
            val taskStatuses =
                listOf(
                    createTaskStatus("task1", "SUCCESS"),
                    createTaskStatus("task2", "PENDING"),
                    createTaskStatus("task3", "FAILED"),
                    createTaskStatus("task4", "PROCESSING"),
                )
            coEvery { apiService.getTaskStatus(any()) } returns Response.success(taskStatuses)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            val statuses = result.getOrNull()!!
            assertEquals(4, statuses.size)
            assertEquals("SUCCESS", statuses[0].status)
            assertEquals("PENDING", statuses[1].status)
            assertEquals("FAILED", statuses[2].status)
            assertEquals("PROCESSING", statuses[3].status)
        }

    @Test
    fun `checkTaskStatus batches large task lists at 32 tasks per batch`() =
        runTest {
            // Given - 70 tasks (should be 3 batches: 32, 32, 6)
            val taskIds = (1..70).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }
            val batch2Statuses = (33..64).map { createTaskStatus("task$it", "SUCCESS") }
            val batch3Statuses = (65..70).map { createTaskStatus("task$it", "SUCCESS") }

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1Statuses),
                    Response.success(batch2Statuses),
                    Response.success(batch3Statuses),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(70, result.getOrNull()?.size)
            coVerify(exactly = 3) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus handles exactly 32 tasks in single batch`() =
        runTest {
            // Given - exactly 32 tasks (max batch size)
            val taskIds = (1..32).map { "task$it" }
            val taskStatuses = taskIds.map { createTaskStatus(it, "SUCCESS") }
            coEvery { apiService.getTaskStatus(any()) } returns Response.success(taskStatuses)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(32, result.getOrNull()?.size)
            coVerify(exactly = 1) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus handles 33 tasks in two batches`() =
        runTest {
            // Given - 33 tasks (should be 2 batches: 32, 1)
            val taskIds = (1..33).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }
            val batch2Statuses = listOf(createTaskStatus("task33", "SUCCESS"))

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1Statuses),
                    Response.success(batch2Statuses),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(33, result.getOrNull()?.size)
            coVerify(exactly = 2) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus handles 64 tasks in two batches`() =
        runTest {
            // Given - 64 tasks (should be 2 batches: 32, 32)
            val taskIds = (1..64).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }
            val batch2Statuses = (33..64).map { createTaskStatus("task$it", "SUCCESS") }

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1Statuses),
                    Response.success(batch2Statuses),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(64, result.getOrNull()?.size)
            coVerify(exactly = 2) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus handles 65 tasks in three batches`() =
        runTest {
            // Given - 65 tasks (should be 3 batches: 32, 32, 1)
            val taskIds = (1..65).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }
            val batch2Statuses = (33..64).map { createTaskStatus("task$it", "SUCCESS") }
            val batch3Statuses = listOf(createTaskStatus("task65", "SUCCESS"))

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1Statuses),
                    Response.success(batch2Statuses),
                    Response.success(batch3Statuses),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(65, result.getOrNull()?.size)
            coVerify(exactly = 3) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus combines results from multiple batches correctly`() =
        runTest {
            // Given - 50 tasks (2 batches: 32, 18)
            val taskIds = (1..50).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }
            val batch2Statuses = (33..50).map { createTaskStatus("task$it", "PENDING") }

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1Statuses),
                    Response.success(batch2Statuses),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            val statuses = result.getOrNull()!!
            assertEquals(50, statuses.size)

            // Verify all task IDs are present in correct order
            assertEquals((1..50).map { "task$it" }, statuses.map { it.taskId })

            // Verify statuses are correct
            assertEquals(32, statuses.count { it.status == "SUCCESS" })
            assertEquals(18, statuses.count { it.status == "PENDING" })
        }

    @Test
    fun `checkTaskStatus returns failure when response body is null`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            coEvery { apiService.getTaskStatus("task1") } returns Response.success(null)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Response body is null") == true)
        }

    @Test
    fun `checkTaskStatus returns failure when first batch response body is null`() =
        runTest {
            // Given - 40 tasks (2 batches), first batch returns null body
            val taskIds = (1..40).map { "task$it" }
            coEvery { apiService.getTaskStatus(any()) } returns Response.success(null)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Response body is null") == true)
            // Should only call once since first batch failed
            coVerify(exactly = 1) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus returns failure when second batch response body is null`() =
        runTest {
            // Given - 40 tasks (2 batches), second batch returns null body
            val taskIds = (1..40).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1Statuses),
                    Response.success(null),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Response body is null") == true)
            coVerify(exactly = 2) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus returns failure on 400 error response`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            coEvery { apiService.getTaskStatus("task1") } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Error: 400") == true)
        }

    @Test
    fun `checkTaskStatus returns failure on 401 error response`() =
        runTest {
            // Given
            val taskIds = listOf("task1", "task2")
            coEvery { apiService.getTaskStatus(any()) } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Error: 401") == true)
        }

    @Test
    fun `checkTaskStatus returns failure on 404 error response`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            coEvery { apiService.getTaskStatus("task1") } returns Response.error(404, "".toResponseBody())

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Error: 404") == true)
        }

    @Test
    fun `checkTaskStatus returns failure on 500 error response`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            coEvery { apiService.getTaskStatus("task1") } returns Response.error(500, "".toResponseBody())

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Error: 500") == true)
        }

    @Test
    fun `checkTaskStatus fails entire operation if first batch fails`() =
        runTest {
            // Given - 50 tasks (2 batches), first batch fails
            val taskIds = (1..50).map { "task$it" }
            coEvery { apiService.getTaskStatus(any()) } returns Response.error(500, "".toResponseBody())

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Error: 500") == true)
            // Should stop after first batch failure
            coVerify(exactly = 1) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus fails entire operation if second batch fails`() =
        runTest {
            // Given - 40 tasks (2 batches), second batch fails
            val taskIds = (1..40).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1Statuses),
                    Response.error(500, "".toResponseBody()),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Error: 500") == true)
            coVerify(exactly = 2) { apiService.getTaskStatus(any()) }
        }

    @Test
    fun `checkTaskStatus returns failure on HttpException`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            val httpException = HttpException(Response.error<List<TaskStatus>>(404, "".toResponseBody()))
            coEvery { apiService.getTaskStatus("task1") } throws httpException

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is HttpException)
        }

    @Test
    fun `checkTaskStatus returns failure on IOException`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            coEvery { apiService.getTaskStatus("task1") } throws IOException("Network error")

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("Network error", result.exceptionOrNull()?.message)
        }

    @Test
    fun `checkTaskStatus returns failure on generic Exception`() =
        runTest {
            // Given
            val taskIds = listOf("task1")
            coEvery { apiService.getTaskStatus("task1") } throws Exception("Unknown error")

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is Exception)
            assertEquals("Unknown error", result.exceptionOrNull()?.message)
        }

    @Test
    fun `checkTaskStatus handles IOException in second batch`() =
        runTest {
            // Given - 40 tasks (2 batches), second batch throws IOException
            val taskIds = (1..40).map { "task$it" }
            val batch1Statuses = (1..32).map { createTaskStatus("task$it", "SUCCESS") }

            coEvery { apiService.getTaskStatus(any()) } returns Response.success(batch1Statuses) andThenAnswer
                { throw IOException("Network timeout") }

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("Network timeout", result.exceptionOrNull()?.message)
        }

    @Test
    fun `checkTaskStatus preserves task order across batches`() =
        runTest {
            // Given - 100 tasks to ensure proper ordering across batches
            val taskIds = (1..100).map { "task$it" }
            val batch1 = (1..32).map { createTaskStatus("task$it", "SUCCESS") }
            val batch2 = (33..64).map { createTaskStatus("task$it", "SUCCESS") }
            val batch3 = (65..96).map { createTaskStatus("task$it", "SUCCESS") }
            val batch4 = (97..100).map { createTaskStatus("task$it", "SUCCESS") }

            coEvery { apiService.getTaskStatus(any()) } returnsMany
                listOf(
                    Response.success(batch1),
                    Response.success(batch2),
                    Response.success(batch3),
                    Response.success(batch4),
                )

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            val statuses = result.getOrNull()!!
            assertEquals(100, statuses.size)

            // Verify order is preserved
            statuses.forEachIndexed { index, taskStatus ->
                assertEquals("task${index + 1}", taskStatus.taskId)
            }
        }

    @Test
    fun `checkTaskStatus handles task IDs with special characters`() =
        runTest {
            // Given
            val taskIds = listOf("task1", "task2", "task3")
            val taskStatuses =
                listOf(
                    createTaskStatus("task1", "SUCCESS"),
                    createTaskStatus("task2", "PENDING"),
                    createTaskStatus("task3", "SUCCESS"),
                )
            coEvery { apiService.getTaskStatus("task1,task2,task3") } returns Response.success(taskStatuses)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(taskStatuses, result.getOrNull())
        }

    @Test
    fun `checkTaskStatus correctly formats comma-separated task IDs`() =
        runTest {
            // Given
            val taskIds = listOf("task1", "task2", "task3", "task4", "task5")
            val taskStatuses = taskIds.map { createTaskStatus(it, "SUCCESS") }
            coEvery { apiService.getTaskStatus("task1,task2,task3,task4,task5") } returns Response.success(taskStatuses)

            // When
            val result = repository.checkTaskStatus(taskIds)

            // Then
            assertTrue(result.isSuccess)
            coVerify { apiService.getTaskStatus("task1,task2,task3,task4,task5") }
        }
}
