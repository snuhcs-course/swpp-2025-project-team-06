package com.example.momentag.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.momentag.model.HomeScreenUiState
import com.example.momentag.model.Photo
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.worker.AlbumUploadWorker
import com.example.momentag.worker.SelectedPhotoUploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class PhotoViewModel(
    private val remoteRepository: RemoteRepository,
    private val localRepository: LocalRepository,
    private val albumUploadJobCount: StateFlow<Int>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            albumUploadJobCount.collect { count ->
                _uiState.update { it.copy(isLoading = count > 0) }
            }
        }
    }

    fun uploadPhotos() {
        viewModelScope.launch {
            // set to loading state
            _uiState.update { it.copy(isLoading = true, userMessage = null, isUploadSuccess = false) }

            try {
                val photoUploadData = localRepository.getPhotoUploadRequest()
                val response = remoteRepository.uploadPhotos(photoUploadData)

                // update ui state
                var message: String? = null
                var error: String? = null
                when (response) {
                    is RemoteRepository.Result.Success -> {
                        when (response.data) {
                            202 -> {
                                _uiState.update { it.copy(isUploadSuccess = true) }
                                "Success"
                            }
                            else -> "Upload successful (Code: ${response.data})"
                        }
                    }
                    is RemoteRepository.Result.BadRequest -> "Request form mismatch"
                    is RemoteRepository.Result.Unauthorized -> "The refresh token is expired"
                    is RemoteRepository.Result.Error -> "Unexpected error: ${response.code}"
                    is RemoteRepository.Result.Exception -> "Unknown error: ${response.e.message}"
                    is RemoteRepository.Result.NetworkError -> "Network error"
                }

                _uiState.update { it.copy(isLoading = false, userMessage = message) }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Network error") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Unknown error: ${e.message}") }
            }
        }
    }

    fun uploadPhotosForAlbums(
        albumIds: Set<Long>,
        context: Context,
    ) {
        if (albumIds.isEmpty()) return

        albumIds.forEach { albumId ->

            val inputData =
                Data
                    .Builder()
                    .putLong(AlbumUploadWorker.KEY_ALBUM_ID, albumId)
                    .build()

            val uploadWorkRequest =
                OneTimeWorkRequest
                    .Builder(AlbumUploadWorker::class.java)
                    .setInputData(inputData)
                    .addTag("album-upload-$albumId")
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager.getInstance(context).enqueue(uploadWorkRequest)
        }

        _uiState.update { it.copy(userMessage = "Background upload started.") }
    }

    fun uploadSelectedPhotos(
        photos: Set<Photo>,
        context: Context,
    ) {
        if (photos.isEmpty()) return

        val photoIds = photos.mapNotNull { it.photoId.toLongOrNull() }.toLongArray()

        if (photoIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No photo IDs to upload.") }
            return
        }

        val inputData =
            Data
                .Builder()
                .putLongArray(SelectedPhotoUploadWorker.KEY_PHOTO_IDS, photoIds)
                .build()

        val uploadWorkRequest =
            OneTimeWorkRequest
                .Builder(SelectedPhotoUploadWorker::class.java)
                .setInputData(inputData)
                .addTag("selected-photo-upload")
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        WorkManager.getInstance(context).enqueue(uploadWorkRequest)

        _uiState.update { it.copy(userMessage = "Background upload started.") }
    }

    fun infoMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun errorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
