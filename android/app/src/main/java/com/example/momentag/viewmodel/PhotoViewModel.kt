package com.example.momentag.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.momentag.model.Photo
import com.example.momentag.model.UploadJobState
import com.example.momentag.model.UploadStatus
import com.example.momentag.model.UploadType
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.UploadStateRepository
import com.example.momentag.worker.AlbumUploadWorker
import com.example.momentag.worker.SelectedPhotoUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PhotoViewModel
    @Inject
    constructor(
        private val remoteRepository: RemoteRepository,
        private val localRepository: LocalRepository,
        private val uploadStateRepository: UploadStateRepository,
        private val albumUploadJobCount: StateFlow<Int>,
    ) : ViewModel() {
        // 1. state data class 정의
        data class HomeScreenUiState(
            val isLoading: Boolean = false,
            val userMessage: String? = null,
            val error: PhotoError? = null,
            val isUploadSuccess: Boolean = false,
        )

        sealed class PhotoError {
            object NoPhotosSelected : PhotoError()
        }

        // 1. Private MutableStateFlow
        private val _uiState = MutableStateFlow(HomeScreenUiState())

        // 2. Public StateFlow (exposed state)
        val uiState = _uiState.asStateFlow()

        // 3. init 블록
        init {
            viewModelScope.launch {
                albumUploadJobCount.collect { count ->
                    _uiState.update { it.copy(isLoading = count > 0) }
                }
            }
        }

        fun uploadPhotosForAlbums(
            albumIds: Set<Long>,
            context: Context,
        ) {
            if (albumIds.isEmpty()) return

            albumIds.forEach { albumId ->
                viewModelScope.launch {
                    // Check for existing paused upload for this album
                    val existingPausedJob =
                        uploadStateRepository
                            .getAllActiveStates()
                            .find { it.albumId == albumId && it.status == UploadStatus.PAUSED }

                    val jobId = existingPausedJob?.jobId ?: UUID.randomUUID().toString()

                    if (existingPausedJob != null) {
                        // Resume existing upload
                        uploadStateRepository.saveState(existingPausedJob.copy(status = UploadStatus.RUNNING))
                    } else {
                        // Query all photo IDs for this album
                        val allPhotoIds = getAllPhotoIdsForAlbum(context, albumId)

                        // Initialize new upload state
                        uploadStateRepository.saveState(
                            UploadJobState(
                                jobId = jobId,
                                type = UploadType.ALBUM,
                                albumId = albumId,
                                status = UploadStatus.RUNNING,
                                totalPhotoIds = allPhotoIds,
                                failedPhotoIds = emptyList(),
                                currentChunkIndex = 0,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }

                    val inputData =
                        Data
                            .Builder()
                            .putLong(AlbumUploadWorker.KEY_ALBUM_ID, albumId)
                            .putString(AlbumUploadWorker.KEY_JOB_ID, jobId)
                            .build()

                    val uploadWorkRequest =
                        OneTimeWorkRequest
                            .Builder(AlbumUploadWorker::class.java)
                            .setInputData(inputData)
                            .addTag("upload-$jobId")
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .build()

                    WorkManager.getInstance(context).enqueue(uploadWorkRequest)

                    val message =
                        if (existingPausedJob != null) {
                            "Resuming upload..."
                        } else {
                            "Background upload started."
                        }
                    _uiState.update { it.copy(userMessage = message) }
                }
            }
        }

        private fun getAllPhotoIdsForAlbum(
            context: Context,
            albumId: Long,
        ): List<Long> {
            val photoIds = mutableListOf<Long>()
            val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
            val selection = "${android.provider.MediaStore.Images.Media.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(albumId.toString())

            context.contentResolver
                .query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        photoIds.add(cursor.getLong(idColumn))
                    }
                }
            return photoIds
        }

        fun uploadSelectedPhotos(
            photos: Set<Photo>,
            context: Context,
        ) {
            if (photos.isEmpty()) return

            val photoIds =
                photos
                    .mapNotNull {
                        it.contentUri.lastPathSegment?.toLongOrNull()
                    }.toLongArray()

            if (photoIds.isEmpty()) {
                _uiState.update { it.copy(error = PhotoError.NoPhotosSelected) }
                return
            }

            viewModelScope.launch {
                // Check for existing paused upload with matching photo IDs
                val existingPausedJob =
                    uploadStateRepository
                        .getAllActiveStates()
                        .find {
                            it.type == UploadType.SELECTED_PHOTOS &&
                                it.status == UploadStatus.PAUSED &&
                                it.totalPhotoIds.toSet() == photoIds.toSet()
                        }

                val jobId = existingPausedJob?.jobId ?: UUID.randomUUID().toString()

                if (existingPausedJob != null) {
                    // Resume existing upload
                    uploadStateRepository.saveState(existingPausedJob.copy(status = UploadStatus.RUNNING))
                } else {
                    // Initialize new upload state
                    uploadStateRepository.saveState(
                        UploadJobState(
                            jobId = jobId,
                            type = UploadType.SELECTED_PHOTOS,
                            albumId = null,
                            status = UploadStatus.RUNNING,
                            totalPhotoIds = photoIds.toList(),
                            failedPhotoIds = emptyList(),
                            currentChunkIndex = 0,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }

                val inputData =
                    Data
                        .Builder()
                        .putLongArray(SelectedPhotoUploadWorker.KEY_PHOTO_IDS, photoIds)
                        .putString(SelectedPhotoUploadWorker.KEY_JOB_ID, jobId)
                        .build()

                val uploadWorkRequest =
                    OneTimeWorkRequest
                        .Builder(SelectedPhotoUploadWorker::class.java)
                        .setInputData(inputData)
                        .addTag("upload-$jobId")
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()

                WorkManager.getInstance(context).enqueue(uploadWorkRequest)

                val message =
                    if (existingPausedJob != null) {
                        "Resuming upload..."
                    } else {
                        "Background upload started."
                    }
                _uiState.update { it.copy(userMessage = message) }
            }
        }

        fun infoMessageShown() {
            _uiState.update { it.copy(userMessage = null) }
        }

        fun errorMessageShown() {
            _uiState.update { it.copy(error = null) }
        }
    }
