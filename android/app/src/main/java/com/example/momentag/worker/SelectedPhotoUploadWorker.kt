package com.example.momentag.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.ExifInterface
import android.net.Uri
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
import com.example.momentag.model.PhotoMeta
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoInfoForUpload
import com.example.momentag.repository.RemoteRepository
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class PhotoMetadataHolder(
    val filename: String,
    val createdAt: String,
    val lat: Double,
    val lng: Double,
)

@HiltWorker
class SelectedPhotoUploadWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val localRepository: LocalRepository,
        private val remoteRepository: RemoteRepository,
        private val gson: Gson,
        @AlbumUploadJobCountQualifier private val albumUploadJobCount: MutableStateFlow<Int>,
        @AlbumUploadSuccessEventQualifier private val albumUploadSuccessEvent: MutableSharedFlow<Long>,
    ) : CoroutineWorker(appContext, params) {
        private val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        companion object {
            const val KEY_PHOTO_IDS = "PHOTO_IDS"
            const val KEY_PROGRESS = "PROGRESS"

            private const val NOTIFICATION_ID = 12345
            private const val CHANNEL_ID = "AlbumUploadChannel"

            private const val RESULT_NOTIFICATION_ID = 12346
        }

        private fun createForegroundInfo(progress: String): ForegroundInfo {
            createNotificationChannel()

            val notification = createNotification(progress, true)

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
        ): Notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(applicationContext.getString(R.string.notification_upload_title))
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .build()

        private fun updateNotification(
            title: String,
            text: String,
            id: Int,
            ongoing: Boolean,
        ) {
            val notification =
                NotificationCompat
                    .Builder(applicationContext, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.mipmap.ic_launcher_foreground)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)
                    .build()
            notificationManager.notify(id, notification)
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

        override suspend fun doWork(): Result {
            val photoIds = inputData.getLongArray(KEY_PHOTO_IDS)
            if (photoIds == null || photoIds.isEmpty()) {
                return Result.failure()
            }

            val initialProgress = "Preparing upload..."
            setForeground(createForegroundInfo(initialProgress))

            albumUploadJobCount.update { it + 1 }

            try {
                val success = processPhotosInChunks(photoIds, 8)

                if (success) {
                    albumUploadSuccessEvent.emit(0L)
                    updateNotification(
                        applicationContext.getString(R.string.notification_upload_complete),
                        applicationContext.getString(R.string.notification_upload_complete_message),
                        RESULT_NOTIFICATION_ID,
                        false,
                    )
                    return Result.success()
                } else {
                    updateNotification(
                        applicationContext.getString(R.string.notification_upload_failed),
                        applicationContext.getString(R.string.error_message_upload_failed),
                        RESULT_NOTIFICATION_ID,
                        false,
                    )
                    return Result.failure()
                }
            } catch (e: Exception) {
                updateNotification(
                    applicationContext.getString(R.string.notification_upload_error),
                    applicationContext.getString(R.string.error_message_generic),
                    RESULT_NOTIFICATION_ID,
                    false,
                )
                return Result.failure()
            } finally {
                albumUploadJobCount.update { it - 1 }
            }
        }

        private suspend fun processPhotosInChunks(
            photoIds: LongArray,
            chunkSize: Int,
        ): Boolean {
            val totalPhotos = photoIds.size
            if (totalPhotos == 0) return true

            val totalChunks = (totalPhotos + chunkSize - 1) / chunkSize
            var chunkCount = 0

            photoIds.asSequence().chunked(chunkSize).forEach { chunkIds ->
                val currentChunk = mutableListOf<PhotoInfoForUpload>()

                chunkIds.forEach { id ->
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val (filename, createdAt, lat, lng) = getMetadataForPhoto(id, contentUri)

                    val meta =
                        PhotoMeta(
                            filename = filename,
                            photo_path_id = id.toInt(),
                            created_at = createdAt,
                            lat = lat,
                            lng = lng,
                        )
                    currentChunk.add(PhotoInfoForUpload(contentUri, meta))
                }

                chunkCount++
                val progressText = "Uploading chunk ($chunkCount / $totalChunks)..."
                setProgress(workDataOf(KEY_PROGRESS to progressText))
                updateNotification(
                    applicationContext.getString(R.string.notification_uploading_photos),
                    progressText,
                    NOTIFICATION_ID,
                    true,
                )

                val uploadData = createUploadDataFromChunk(currentChunk)
                val response = remoteRepository.uploadPhotos(uploadData)

                if (response !is RemoteRepository.Result.Success) {
                    return false
                }
                currentChunk.clear()
            }

            return true
        }

        private fun getMetadataForPhoto(
            id: Long,
            contentUri: Uri,
        ): PhotoMetadataHolder {
            var filename = "unknown.jpg"
            var finalLat = 0.0
            var finalLng = 0.0

            // 1. dateValue를 0L (Epoch)로 기본값 설정
            var dateValue = 0L

            val projection =
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)

            try {
                // 쿼리 실패에 대비해 try-catch 추가
                applicationContext.contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "unknown.jpg"

                        // 2. dateValue를 여기서 덮어씀
                        dateValue = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                        if (dateValue == 0L) {
                            val dateAddedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                            if (dateAddedSeconds > 0L) {
                                dateValue = dateAddedSeconds * 1000L // DATE_ADDED는 초(second) 단위이므로 밀리초로 변환
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SelectedPhotoUploadWorker", "Failed to query ContentResolver for $id. Using default date (Epoch).", e)
            }

            // 3. createdAt 포맷을 *항상* 실행 (cursor.use 밖에서)
            val createdAt =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    .apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
                    .format(Date(dateValue))

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

            return PhotoMetadataHolder(filename, createdAt, finalLat, finalLng)
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
