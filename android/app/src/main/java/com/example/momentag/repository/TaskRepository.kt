package com.example.momentag.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.momentag.model.TaskStatus
import com.example.momentag.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.taskDataStore: DataStore<Preferences> by preferencesDataStore(name = "tasks")

@Singleton
class TaskRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val apiService: ApiService,
    ) {
        private val dataStore = context.taskDataStore

        companion object {
            private val KEY_PENDING_TASK_IDS = stringPreferencesKey("pending_task_ids")
        }

        suspend fun saveTaskIds(taskIds: List<String>) {
            dataStore.edit { prefs ->
                val currentIds = prefs[KEY_PENDING_TASK_IDS]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                val newIds = currentIds + taskIds
                prefs[KEY_PENDING_TASK_IDS] = newIds.joinToString(",")
            }
        }

        fun getPendingTaskIds(): Flow<Set<String>> =
            dataStore.data.map { prefs ->
                prefs[KEY_PENDING_TASK_IDS]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            }

        suspend fun removeTaskIds(taskIds: List<String>) {
            dataStore.edit { prefs ->
                val currentIds = prefs[KEY_PENDING_TASK_IDS]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                val newIds = currentIds - taskIds.toSet()
                prefs[KEY_PENDING_TASK_IDS] = newIds.joinToString(",")
            }
        }

        suspend fun checkTaskStatus(taskIds: List<String>): Result<List<TaskStatus>> {
            return try {
                if (taskIds.isEmpty()) {
                    return Result.success(emptyList())
                }

                // Batch requests to avoid URL length limits (max 32 task IDs per request)
                val batchSize = 32
                val allStatuses = mutableListOf<TaskStatus>()

                taskIds.chunked(batchSize).forEach { batch ->
                    val taskIdsString = batch.joinToString(",")
                    val response = apiService.getTaskStatus(taskIdsString)

                    if (response.isSuccessful) {
                        response.body()?.let { statuses ->
                            allStatuses.addAll(statuses)
                        } ?: return Result.failure(Exception("Response body is null"))
                    } else {
                        return Result.failure(Exception("Error: ${response.code()} ${response.message()}"))
                    }
                }

                Result.success(allStatuses)
            } catch (e: HttpException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
