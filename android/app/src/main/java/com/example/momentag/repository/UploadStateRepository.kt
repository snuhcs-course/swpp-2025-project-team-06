package com.example.momentag.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.momentag.model.UploadJobState
import com.example.momentag.model.UploadStatus
import com.example.momentag.model.UploadType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.uploadStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "upload_states")

class UploadStateRepository(
    private val context: Context,
) {
    private val uploadStatesKey = stringPreferencesKey("upload_states")

    /**
     * Save or update an upload state
     */
    suspend fun saveState(state: UploadJobState) {
        context.uploadStateDataStore.edit { preferences ->
            val currentStatesJson = preferences[uploadStatesKey] ?: "[]"
            val statesArray = JSONArray(currentStatesJson)

            // Find and remove existing state with same jobId
            var existingIndex = -1
            for (i in 0 until statesArray.length()) {
                val stateObj = statesArray.getJSONObject(i)
                if (stateObj.getString("jobId") == state.jobId) {
                    existingIndex = i
                    break
                }
            }
            if (existingIndex >= 0) {
                statesArray.remove(existingIndex)
            }

            // Add new state
            statesArray.put(stateToJson(state))

            preferences[uploadStatesKey] = statesArray.toString()
        }
    }

    /**
     * Get a specific upload state by job ID
     */
    suspend fun getState(jobId: String): UploadJobState? {
        val states = getAllStates()
        return states.find { it.jobId == jobId }
    }

    /**
     * Remove an upload state
     */
    suspend fun removeState(jobId: String) {
        context.uploadStateDataStore.edit { preferences ->
            val currentStatesJson = preferences[uploadStatesKey] ?: "[]"
            val statesArray = JSONArray(currentStatesJson)

            var indexToRemove = -1
            for (i in 0 until statesArray.length()) {
                val stateObj = statesArray.getJSONObject(i)
                if (stateObj.getString("jobId") == jobId) {
                    indexToRemove = i
                    break
                }
            }

            if (indexToRemove >= 0) {
                statesArray.remove(indexToRemove)
                preferences[uploadStatesKey] = statesArray.toString()
            }
        }
    }

    /**
     * Get all active (non-completed, non-cancelled) upload states
     */
    suspend fun getAllActiveStates(): List<UploadJobState> =
        getAllStates().filter {
            it.status != UploadStatus.COMPLETED && it.status != UploadStatus.CANCELLED
        }

    /**
     * Get all upload states as a Flow
     */
    fun getAllStatesFlow(): Flow<List<UploadJobState>> =
        context.uploadStateDataStore.data.map { preferences ->
            val statesJson = preferences[uploadStatesKey] ?: "[]"
            parseStatesJson(statesJson)
        }

    /**
     * Get all upload states (snapshot)
     */
    private suspend fun getAllStates(): List<UploadJobState> = getAllStatesFlow().first()

    /**
     * Remove all completed or cancelled states
     */
    suspend fun cleanupCompletedStates() {
        context.uploadStateDataStore.edit { preferences ->
            val currentStatesJson = preferences[uploadStatesKey] ?: "[]"
            val statesArray = JSONArray(currentStatesJson)

            val newArray = JSONArray()
            for (i in 0 until statesArray.length()) {
                val stateObj = statesArray.getJSONObject(i)
                val status = UploadStatus.valueOf(stateObj.getString("status"))
                if (status != UploadStatus.COMPLETED && status != UploadStatus.CANCELLED) {
                    newArray.put(stateObj)
                }
            }

            preferences[uploadStatesKey] = newArray.toString()
        }
    }

    private fun stateToJson(state: UploadJobState): JSONObject =
        JSONObject().apply {
            put("jobId", state.jobId)
            put("type", state.type.name)
            put("albumId", state.albumId)
            put("status", state.status.name)
            put("totalPhotoIds", JSONArray(state.totalPhotoIds))
            put("failedPhotoIds", JSONArray(state.failedPhotoIds))
            put("currentChunkIndex", state.currentChunkIndex)
            put("createdAt", state.createdAt)
        }

    private fun parseStatesJson(json: String): List<UploadJobState> {
        val statesArray = JSONArray(json)
        val states = mutableListOf<UploadJobState>()

        for (i in 0 until statesArray.length()) {
            try {
                val stateObj = statesArray.getJSONObject(i)
                states.add(jsonToState(stateObj))
            } catch (e: Exception) {
                // Skip malformed states
                e.printStackTrace()
            }
        }

        return states
    }

    private fun jsonToState(json: JSONObject): UploadJobState =
        UploadJobState(
            jobId = json.getString("jobId"),
            type = UploadType.valueOf(json.getString("type")),
            albumId = if (json.isNull("albumId")) null else json.getLong("albumId"),
            status = UploadStatus.valueOf(json.getString("status")),
            totalPhotoIds = jsonArrayToLongList(json.getJSONArray("totalPhotoIds")),
            failedPhotoIds =
                if (json.has("failedPhotoIds")) {
                    jsonArrayToLongList(json.getJSONArray("failedPhotoIds"))
                } else {
                    emptyList()
                },
            currentChunkIndex = json.getInt("currentChunkIndex"),
            createdAt = json.getLong("createdAt"),
        )

    private fun jsonArrayToLongList(array: JSONArray): List<Long> {
        val list = mutableListOf<Long>()
        for (i in 0 until array.length()) {
            list.add(array.getLong(i))
        }
        return list
    }
}
