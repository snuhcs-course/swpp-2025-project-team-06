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
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.momentag.R
import com.example.momentag.model.PhotoMeta
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoInfoForUpload // [추가] 3단계에서 만든 클래스 재사용
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.viewmodel.ViewModelFactory
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.Exception
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

class SelectedPhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val localRepository: LocalRepository
    private val remoteRepository: RemoteRepository
    private val albumUploadJobCount: MutableStateFlow<Int>
    private val albumUploadSuccessEvent: MutableSharedFlow<Long>
    private val gson = Gson()

    init {
        val factory = ViewModelFactory.getInstance(applicationContext)
        localRepository = factory.localRepository
        remoteRepository = factory.remoteRepository
        albumUploadJobCount = factory.albumUploadJobCount
        albumUploadSuccessEvent = factory.albumUploadSuccessEvent
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val KEY_PHOTO_IDS = "PHOTO_IDS"
        const val KEY_PROGRESS = "PROGRESS"

        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "AlbumUploadChannel"
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        createNotificationChannel()

        val notification = createNotification(progress)

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

    private fun createNotification(text: String): Notification =
        NotificationCompat
            .Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("MomenTag Album Upload")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "MomenTag Uploads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows album photo upload progress"
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
                updateNotification("Upload Complete", "Album upload completed successfully.")
                return Result.success()
            } else {
                updateNotification("Upload Failed", "Failed to upload some files.")
                return Result.failure()
            }
        } catch (e: Exception) {
            updateNotification("Upload Error", "An unknown error occurred.")
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
            updateNotification("앨범 업로드 중", progressText)

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
        var createdAt = ""
        var finalLat = 0.0
        var finalLng = 0.0

        val projection =
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        applicationContext.contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "unknown.jpg"
                var dateValue = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
                if (dateValue == 0L) {
                    val dateAddedSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    if (dateAddedSeconds > 0L) {
                        dateValue = dateAddedSeconds * 1000L
                    }
                }
                createdAt =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                        .apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
                        .format(Date(dateValue))
            }
        }

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

    private fun updateNotification(
        title: String,
        text: String,
    ) {
        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setOngoing(false)
                .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
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

                if (resizedBytes != null) {
                    val mime = "image/jpeg"
                    val requestBody = resizedBytes.toRequestBody(mime.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("photo", photoInfo.meta.filename, requestBody)

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
