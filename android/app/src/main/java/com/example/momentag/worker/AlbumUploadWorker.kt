package com.example.momentag.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.momentag.R
import com.example.momentag.di.AlbumUploadJobCountQualifier
import com.example.momentag.di.AlbumUploadSuccessEventQualifier
import com.example.momentag.di.UploadCancelRequestQualifier
import com.example.momentag.di.UploadPauseRequestQualifier
import com.example.momentag.model.PhotoMeta
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.model.UploadJobState
import com.example.momentag.model.UploadStatus
import com.example.momentag.model.UploadType
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoInfoForUpload
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.UploadStateRepository
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@HiltWorker
class AlbumUploadWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val localRepository: LocalRepository,
        private val remoteRepository: RemoteRepository,
        private val taskRepository: com.example.momentag.repository.TaskRepository,
        private val uploadStateRepository: UploadStateRepository,
        private val gson: Gson,
        @AlbumUploadJobCountQualifier private val albumUploadJobCount: MutableStateFlow<Int>,
        @AlbumUploadSuccessEventQualifier private val albumUploadSuccessEvent: MutableSharedFlow<Long>,
        @UploadPauseRequestQualifier private val uploadPauseRequestFlow: MutableSharedFlow<String>,
        @UploadCancelRequestQualifier private val uploadCancelRequestFlow: MutableSharedFlow<String>,
    ) : CoroutineWorker(appContext, params) {
        private val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        companion object {
            const val KEY_ALBUM_ID = "ALBUM_ID"
            const val KEY_JOB_ID = "JOB_ID"
            const val KEY_PROGRESS = "PROGRESS"

            private const val NOTIFICATION_ID = 12345
            private const val CHANNEL_ID = "AlbumUploadChannel"

            private const val RESULT_NOTIFICATION_ID = 12346
        }

        @Volatile
        private var shouldPause = false

        @Volatile
        private var shouldCancel = false

        private fun createForegroundInfo(
            progress: String,
            jobId: String? = null,
        ): ForegroundInfo {
            createNotificationChannel()

            // 알림 생성 (유지)
            val notification = createNotification(progress, true, jobId)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        }

        private fun createNotification(
            text: String,
            ongoing: Boolean,
            jobId: String? = null,
        ): Notification {
            val builder =
                NotificationCompat
                    .Builder(applicationContext, CHANNEL_ID)
                    .setContentTitle(applicationContext.getString(R.string.notification_upload_title))
                    .setContentText(text)
                    .setSmallIcon(R.mipmap.ic_launcher_foreground)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)

            // Add pause and cancel action buttons if jobId is provided
            if (jobId != null) {
                // Pause button
                val pauseIntent =
                    android.content.Intent(applicationContext, com.example.momentag.receiver.UploadControlReceiver::class.java).apply {
                        action = com.example.momentag.receiver.UploadControlReceiver.ACTION_PAUSE
                        putExtra(com.example.momentag.receiver.UploadControlReceiver.EXTRA_JOB_ID, jobId)
                    }
                val pausePendingIntent =
                    android.app.PendingIntent.getBroadcast(
                        applicationContext,
                        jobId.hashCode(),
                        pauseIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )

                // Cancel button
                val cancelIntent =
                    android.content.Intent(applicationContext, com.example.momentag.receiver.UploadControlReceiver::class.java).apply {
                        action = com.example.momentag.receiver.UploadControlReceiver.ACTION_CANCEL
                        putExtra(com.example.momentag.receiver.UploadControlReceiver.EXTRA_JOB_ID, jobId)
                    }
                val cancelPendingIntent =
                    android.app.PendingIntent.getBroadcast(
                        applicationContext,
                        jobId.hashCode() + 1,
                        cancelIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )

                builder
                    .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                    .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
            }

            return builder.build()
        }

        private fun updateNotification(
            title: String,
            text: String,
            id: Int,
            ongoing: Boolean,
            retryPendingIntent: android.app.PendingIntent? = null,
            jobId: String? = null,
        ) {
            val builder =
                NotificationCompat
                    .Builder(applicationContext, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.mipmap.ic_launcher_foreground)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)

            if (retryPendingIntent != null) {
                builder.addAction(
                    android.R.drawable.ic_menu_rotate,
                    applicationContext.getString(R.string.notification_action_retry),
                    retryPendingIntent,
                )
            }

            // Add pause and cancel action buttons if jobId is provided
            if (jobId != null && ongoing) {
                val pauseIntent =
                    android.content.Intent(applicationContext, com.example.momentag.receiver.UploadControlReceiver::class.java).apply {
                        action = com.example.momentag.receiver.UploadControlReceiver.ACTION_PAUSE
                        putExtra(com.example.momentag.receiver.UploadControlReceiver.EXTRA_JOB_ID, jobId)
                    }
                val pausePendingIntent =
                    android.app.PendingIntent.getBroadcast(
                        applicationContext,
                        jobId.hashCode(),
                        pauseIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )

                val cancelIntent =
                    android.content.Intent(applicationContext, com.example.momentag.receiver.UploadControlReceiver::class.java).apply {
                        action = com.example.momentag.receiver.UploadControlReceiver.ACTION_CANCEL
                        putExtra(com.example.momentag.receiver.UploadControlReceiver.EXTRA_JOB_ID, jobId)
                    }
                val cancelPendingIntent =
                    android.app.PendingIntent.getBroadcast(
                        applicationContext,
                        jobId.hashCode() + 1,
                        cancelIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                    )

                builder
                    .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
                    .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
            }

            notificationManager.notify(id, builder.build())
        }

        private fun createRetryPendingIntent(failedPhotoIds: LongArray): android.app.PendingIntent {
            val intent =
                android.content.Intent(applicationContext, com.example.momentag.receiver.UploadRetryReceiver::class.java).apply {
                    action = com.example.momentag.receiver.UploadRetryReceiver.ACTION_RETRY_UPLOAD
                    putExtra(com.example.momentag.receiver.UploadRetryReceiver.EXTRA_FAILED_PHOTO_IDS, failedPhotoIds)
                    putExtra(com.example.momentag.receiver.UploadRetryReceiver.EXTRA_NOTIFICATION_ID, RESULT_NOTIFICATION_ID)
                }
            return android.app.PendingIntent.getBroadcast(
                applicationContext,
                failedPhotoIds.firstOrNull()?.toInt() ?: 0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        applicationContext.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = applicationContext.getString(R.string.notification_channel_description)
                    }
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun getAlbumName(albumId: Long): String {
            val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(albumId.toString())

            applicationContext.contentResolver
                .query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        // 앨범 이름을 찾아 반환
                        return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                            ?: applicationContext.getString(R.string.fallback_unknown_album)
                    }
                }
            return applicationContext.getString(R.string.fallback_unknown_album)
        }

        override suspend fun doWork(): Result {
            val albumId = inputData.getLong(KEY_ALBUM_ID, -1L)
            if (albumId == -1L) {
                return Result.failure()
            }

            // Get or create job ID
            val jobId = inputData.getString(KEY_JOB_ID) ?: UUID.randomUUID().toString()

            // Listen for pause/cancel requests
            val pauseJob =
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    uploadPauseRequestFlow.collect { requestedJobId ->
                        if (requestedJobId == jobId) {
                            shouldPause = true
                        }
                    }
                }

            val cancelJob =
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    uploadCancelRequestFlow.collect { requestedJobId ->
                        if (requestedJobId == jobId) {
                            shouldCancel = true
                        }
                    }
                }

            val albumName = getAlbumName(albumId)

            val initialProgress = applicationContext.getString(R.string.foreground_preparing_upload)
            setForeground(createForegroundInfo(initialProgress, jobId))

            albumUploadJobCount.update { it + 1 }

            try {
                // Get or create upload state
                val existingState = uploadStateRepository.getState(jobId)
                val startChunkIndex = existingState?.currentChunkIndex ?: 0

                // Use saved photo IDs or query on first run
                val photoIdsToUpload = existingState?.totalPhotoIds ?: getAllPhotoIdsForAlbum(albumId)

                // Initialize state if new
                if (existingState == null) {
                    uploadStateRepository.saveState(
                        UploadJobState(
                            jobId = jobId,
                            type = UploadType.ALBUM,
                            albumId = albumId,
                            status = UploadStatus.RUNNING,
                            totalPhotoIds = photoIdsToUpload,
                            failedPhotoIds = emptyList(),
                            currentChunkIndex = 0,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    // Update to RUNNING status
                    uploadStateRepository.saveState(existingState.copy(status = UploadStatus.RUNNING))
                }

                val (successCount, failCount, taskIds, failedPhotoIds, wasPaused) =
                    processAlbumInChunks(photoIdsToUpload, albumName, 8, jobId, startChunkIndex)

                // Handle pause
                if (wasPaused || shouldPause) {
                    val currentState = uploadStateRepository.getState(jobId)
                    if (currentState != null) {
                        uploadStateRepository.saveState(currentState.copy(status = UploadStatus.PAUSED))
                    }
                    // Note: Task IDs already saved incrementally during processAlbumInChunks
                    return Result.failure()
                }

                // Handle cancel
                if (shouldCancel) {
                    val currentState = uploadStateRepository.getState(jobId)
                    if (currentState != null) {
                        uploadStateRepository.saveState(currentState.copy(status = UploadStatus.CANCELLED))
                    }
                    notificationManager.cancel(NOTIFICATION_ID)
                    // Note: Task IDs already saved incrementally during processAlbumInChunks
                    return Result.failure()
                }

                // Save task IDs for successful uploads (only when completed, not paused/cancelled)
                // This is redundant safety since task IDs are already saved incrementally,
                // but ensures we have all task IDs even if incremental saves failed
                if (taskIds.isNotEmpty()) {
                    taskRepository.saveTaskIds(taskIds)
                }

                // Update final state
                val currentState = uploadStateRepository.getState(jobId)
                if (currentState != null) {
                    val finalStatus = if (failCount > 0 && successCount == 0) UploadStatus.FAILED else UploadStatus.COMPLETED
                    uploadStateRepository.saveState(
                        currentState.copy(
                            status = finalStatus,
                            failedPhotoIds = failedPhotoIds,
                        ),
                    )
                }

                if (successCount > 0) {
                    albumUploadSuccessEvent.emit(albumId)
                    val message =
                        if (failCount > 0) {
                            applicationContext.getString(R.string.notification_partial_success_album, albumName, successCount, failCount)
                        } else {
                            "'$albumName': ${applicationContext.getString(R.string.notification_upload_complete_message)}"
                        }

                    val retryIntent = if (failCount > 0) createRetryPendingIntent(failedPhotoIds.toLongArray()) else null
                    updateNotification(
                        applicationContext.getString(R.string.notification_upload_complete),
                        message,
                        RESULT_NOTIFICATION_ID,
                        false,
                        retryIntent,
                    )
                    return Result.success()
                } else {
                    val retryIntent = createRetryPendingIntent(failedPhotoIds.toLongArray())
                    updateNotification(
                        applicationContext.getString(R.string.notification_upload_failed),
                        "'$albumName': ${applicationContext.getString(R.string.error_message_upload_failed)}",
                        RESULT_NOTIFICATION_ID,
                        false,
                        retryIntent,
                    )
                    return Result.failure()
                }
            } catch (e: Exception) {
                val currentState = uploadStateRepository.getState(jobId)
                if (currentState != null) {
                    uploadStateRepository.saveState(currentState.copy(status = UploadStatus.FAILED))
                }
                updateNotification(
                    applicationContext.getString(R.string.notification_upload_error),
                    "'$albumName': ${applicationContext.getString(R.string.error_message_generic)}",
                    RESULT_NOTIFICATION_ID,
                    false,
                )
                return Result.failure()
            } finally {
                albumUploadJobCount.update { it - 1 }
                pauseJob.cancel()
                cancelJob.cancel()
            }
        }

        private data class UploadResult(
            val successCount: Int,
            val failCount: Int,
            val taskIds: List<String>,
            val failedPhotoIds: List<Long>,
            val wasPaused: Boolean,
        )

        private fun getAllPhotoIdsForAlbum(albumId: Long): List<Long> {
            val photoIds = mutableListOf<Long>()
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(albumId.toString())

            applicationContext.contentResolver
                .query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        photoIds.add(cursor.getLong(idColumn))
                    }
                }
            return photoIds
        }

        private suspend fun processAlbumInChunks(
            photoIds: List<Long>,
            albumName: String,
            chunkSize: Int,
            jobId: String,
            startChunkIndex: Int,
        ): UploadResult {
            // Get metadata for each photo ID
            val allPhotos = mutableListOf<PhotoInfoForUpload>()

            photoIds.forEach { id ->
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                // Query metadata for this specific photo
                val projection =
                    arrayOf(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_TAKEN,
                        MediaStore.Images.Media.DATE_ADDED,
                    )
                val selection = "${MediaStore.Images.Media._ID} = ?"
                val selectionArgs = arrayOf(id.toString())

                var filename = "unknown.jpg"
                var dateValue = 0L

                applicationContext.contentResolver
                    .query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "unknown.jpg"
                            dateValue = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                            if (dateValue == 0L) {
                                val dateAddedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                                if (dateAddedSeconds > 0L) {
                                    dateValue = dateAddedSeconds * 1000L
                                }
                            }
                        }
                    }

                val createdAt =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                        .apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
                        .format(Date(dateValue))

                var finalLat = 0.0
                var finalLng = 0.0
                try {
                    applicationContext.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                        val exif = ExifInterface(inputStream)
                        val latOutput = FloatArray(2)
                        if (exif.getLatLong(latOutput)) {
                            finalLat = latOutput[0].toDouble()
                            finalLng = latOutput[1].toDouble()
                        }
                    }
                } catch (e: Exception) {
                }

                val meta =
                    PhotoMeta(
                        filename = filename,
                        photo_path_id = id,
                        created_at = createdAt,
                        lat = finalLat,
                        lng = finalLng,
                    )
                allPhotos.add(PhotoInfoForUpload(contentUri, meta))
            }

            // Split into chunks
            val chunks = allPhotos.chunked(chunkSize)
            val totalChunks = chunks.size

            var successCount = 0
            var failCount = 0
            val allTaskIds = mutableListOf<String>()
            val failedPhotoIds = mutableListOf<Long>()

            // Process chunks starting from startChunkIndex
            for (i in startChunkIndex until chunks.size) {
                // Check for pause/cancel before processing each chunk
                if (shouldPause || shouldCancel || isStopped) {
                    // Save current state before pausing
                    val currentState = uploadStateRepository.getState(jobId)
                    if (currentState != null) {
                        uploadStateRepository.saveState(
                            currentState.copy(
                                currentChunkIndex = i,
                                failedPhotoIds = failedPhotoIds,
                            ),
                        )
                    }
                    return UploadResult(successCount, failCount, allTaskIds, failedPhotoIds, true)
                }

                val chunk = chunks[i]
                val progressText =
                    applicationContext.getString(
                        R.string.notification_progress_text,
                        albumName,
                        i + 1,
                        totalChunks,
                    )
                setProgress(workDataOf(KEY_PROGRESS to progressText))
                updateNotification(
                    applicationContext.getString(R.string.notification_uploading_albums),
                    progressText,
                    NOTIFICATION_ID,
                    true,
                    jobId = jobId,
                )

                val uploadData = createUploadDataFromChunk(chunk)

                // If chunk is empty (all resize failed), count as fail
                if (uploadData.photo.isEmpty()) {
                    failCount += chunk.size
                    chunk.forEach { photoInfo ->
                        failedPhotoIds.add(photoInfo.meta.photo_path_id)
                    }
                    continue
                }

                val response = remoteRepository.uploadPhotos(uploadData)

                if (response is RemoteRepository.Result.Success) {
                    successCount += uploadData.photo.size
                    val taskIds = response.data.map { it.taskId }
                    allTaskIds.addAll(taskIds)

                    // Save task IDs immediately after each successful chunk
                    if (taskIds.isNotEmpty()) {
                        taskRepository.saveTaskIds(taskIds)
                    }
                } else {
                    failCount += uploadData.photo.size
                    chunk.forEach { photoInfo ->
                        failedPhotoIds.add(photoInfo.meta.photo_path_id)
                    }
                    Log.e("AlbumUploadWorker", "Chunk upload failed: $response")
                }

                // Update state after each chunk
                val currentState = uploadStateRepository.getState(jobId)
                if (currentState != null) {
                    uploadStateRepository.saveState(
                        currentState.copy(
                            currentChunkIndex = i + 1,
                            failedPhotoIds = failedPhotoIds,
                        ),
                    )
                }
            }

            return UploadResult(successCount, failCount, allTaskIds, failedPhotoIds, false)
        }

        private fun createUploadDataFromChunk(chunk: List<PhotoInfoForUpload>): PhotoUploadData {
            val photoParts = mutableListOf<MultipartBody.Part>()
            val metadataList = mutableListOf<PhotoMeta>()

            chunk.forEach { photoInfo ->

                try {
                    val resizedBytes =
                        localRepository.resizeImage(
                            photoInfo.uri,
                            maxWidth = 224,
                            maxHeight = 224,
                            quality = 85,
                        )

                    // 3. 리사이즈 성공 시에만 처리 (실패 시 원본 전송 안 함)
                    if (resizedBytes != null && resizedBytes.isNotEmpty()) {
                        val mime = "image/jpeg"
                        val requestBody = resizedBytes.toRequestBody(mime.toMediaTypeOrNull())
                        val filenameWithoutExtension = photoInfo.meta.filename.substringBeforeLast(".", photoInfo.meta.filename)
                        val newFilename = "$filenameWithoutExtension.jpg"
                        val part = MultipartBody.Part.createFormData("photo", newFilename, requestBody)

                        photoParts.add(part)
                        metadataList.add(photoInfo.meta)
                    } else {
                        Log.w(
                            "AlbumUploadWorker",
                            "Resize failed for ${photoInfo.meta.filename} (unsupported format? corrupted?). SKIPPING file.",
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(
                        "AlbumUploadWorker",
                        "CRITICAL: Failed to process photo. SKIPPING file: ${photoInfo.meta.filename}",
                        t,
                    )
                }
            }

            val metadataJson = gson.toJson(metadataList)
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())

            return PhotoUploadData(photoParts, metadataBody)
        }
    }
