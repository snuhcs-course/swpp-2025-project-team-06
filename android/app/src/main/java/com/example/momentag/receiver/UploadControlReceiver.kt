package com.example.momentag.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.momentag.R
import com.example.momentag.di.UploadCancelRequestQualifier
import com.example.momentag.di.UploadPauseRequestQualifier
import com.example.momentag.model.UploadStatus
import com.example.momentag.model.UploadType
import com.example.momentag.repository.UploadStateRepository
import com.example.momentag.worker.AlbumUploadWorker
import com.example.momentag.worker.SelectedPhotoUploadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class UploadControlReceiver : BroadcastReceiver() {
    @Inject
    lateinit var uploadStateRepository: UploadStateRepository

    @Inject
    @UploadPauseRequestQualifier
    lateinit var uploadPauseRequestFlow: MutableSharedFlow<String>

    @Inject
    @UploadCancelRequestQualifier
    lateinit var uploadCancelRequestFlow: MutableSharedFlow<String>

    companion object {
        const val ACTION_PAUSE = "com.example.momentag.ACTION_PAUSE_UPLOAD"
        const val ACTION_RESUME = "com.example.momentag.ACTION_RESUME_UPLOAD"
        const val ACTION_CANCEL = "com.example.momentag.ACTION_CANCEL_UPLOAD"
        const val ACTION_RETRY = "com.example.momentag.ACTION_RETRY_UPLOAD"
        const val EXTRA_JOB_ID = "EXTRA_JOB_ID"
        const val EXTRA_FAILED_PHOTO_IDS = "FAILED_PHOTO_IDS"
        const val EXTRA_NOTIFICATION_ID = "NOTIFICATION_ID"

        private const val PAUSED_NOTIFICATION_ID = 12347
        private const val CHANNEL_ID = "AlbumUploadChannel"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // Use goAsync for coroutine operations
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> {
                        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return@launch
                        pauseUpload(jobId, context)
                    }
                    ACTION_RESUME -> {
                        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return@launch
                        resumeUpload(jobId, context)
                    }
                    ACTION_CANCEL -> {
                        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return@launch
                        cancelUpload(jobId, context)
                    }
                    ACTION_RETRY -> {
                        retryUpload(intent, context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun pauseUpload(
        jobId: String,
        context: Context,
    ) {
        // Emit pause request to worker (so it stops processing and saves state)
        uploadPauseRequestFlow.emit(jobId)

        // Worker will detect pause, save state with correct currentChunkIndex, and exit gracefully
        // Show paused notification with current state (will be updated by worker)
        val state = uploadStateRepository.getState(jobId)
        if (state != null) {
            val text = getNotificationText(context, state)
            showPausedNotification(context, jobId, text)
        }
    }

    private suspend fun resumeUpload(
        jobId: String,
        context: Context,
    ) {
        val state = uploadStateRepository.getState(jobId)
        if (state != null && state.status == UploadStatus.PAUSED) {
            // Dismiss the paused notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(PAUSED_NOTIFICATION_ID)

            // Enqueue new worker based on upload type
            when (state.type) {
                UploadType.ALBUM -> {
                    if (state.albumId != null) {
                        enqueueAlbumUpload(context, state.albumId, jobId)
                    }
                }
                UploadType.SELECTED_PHOTOS -> {
                    enqueueSelectedPhotosUpload(context, state.totalPhotoIds.toLongArray(), jobId)
                }
            }
        }
    }

    private suspend fun cancelUpload(
        jobId: String,
        context: Context,
    ) {
        // Emit cancel request to worker (so it stops processing and exits)
        uploadCancelRequestFlow.emit(jobId)

        // Worker will detect cancel and exit gracefully
        // We can remove the state immediately since user explicitly cancelled
        uploadStateRepository.removeState(jobId)

        // Cancel the paused notification if it exists
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(PAUSED_NOTIFICATION_ID)
    }

    private fun retryUpload(
        intent: Intent,
        context: Context,
    ) {
        val failedIds = intent.getLongArrayExtra(EXTRA_FAILED_PHOTO_IDS)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (failedIds != null && failedIds.isNotEmpty()) {
            // Generate a new job ID for the retry upload
            val retryJobId = "retry-${UUID.randomUUID()}"

            val workRequest =
                OneTimeWorkRequestBuilder<SelectedPhotoUploadWorker>()
                    .setInputData(
                        workDataOf(
                            SelectedPhotoUploadWorker.KEY_PHOTO_IDS to failedIds,
                            SelectedPhotoUploadWorker.KEY_JOB_ID to retryJobId,
                        ),
                    ).addTag("upload-$retryJobId")
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(
                    "upload-$retryJobId",
                    ExistingWorkPolicy.REPLACE,
                    workRequest,
                )
        }

        if (notificationId != -1) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }

    private fun showPausedNotification(
        context: Context,
        jobId: String,
        text: String,
    ) {
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val resumeIntent =
            Intent(context, UploadControlReceiver::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_JOB_ID, jobId)
            }
        val resumePendingIntent =
            android.app.PendingIntent.getBroadcast(
                context,
                jobId.hashCode(),
                resumeIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

        val cancelIntent =
            Intent(context, UploadControlReceiver::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_JOB_ID, jobId)
            }
        val cancelPendingIntent =
            android.app.PendingIntent.getBroadcast(
                context,
                jobId.hashCode() + 1,
                cancelIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_upload_title))
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setOngoing(false)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
                .addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
                .build()

        notificationManager.notify(PAUSED_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getNotificationText(
        context: Context,
        state: com.example.momentag.model.UploadJobState,
    ): String =
        when (state.type) {
            UploadType.ALBUM -> {
                val albumName = state.albumId?.let { getAlbumName(context, it) } ?: context.getString(R.string.fallback_unknown_album)
                "'$albumName': Upload paused"
            }
            UploadType.SELECTED_PHOTOS -> "Upload paused"
        }

    private fun getAlbumName(
        context: Context,
        albumId: Long,
    ): String {
        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())

        context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                        ?: context.getString(R.string.fallback_unknown_album)
                }
            }
        return context.getString(R.string.fallback_unknown_album)
    }

    private fun enqueueAlbumUpload(
        context: Context,
        albumId: Long,
        jobId: String,
    ) {
        val inputData =
            Data
                .Builder()
                .putLong(AlbumUploadWorker.KEY_ALBUM_ID, albumId)
                .putString(AlbumUploadWorker.KEY_JOB_ID, jobId)
                .build()

        val workRequest =
            OneTimeWorkRequestBuilder<AlbumUploadWorker>()
                .setInputData(inputData)
                .addTag("upload-$jobId")
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                "upload-$jobId",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
    }

    private fun enqueueSelectedPhotosUpload(
        context: Context,
        photoIds: LongArray,
        jobId: String,
    ) {
        val inputData =
            Data
                .Builder()
                .putLongArray(SelectedPhotoUploadWorker.KEY_PHOTO_IDS, photoIds)
                .putString(SelectedPhotoUploadWorker.KEY_JOB_ID, jobId)
                .build()

        val workRequest =
            OneTimeWorkRequestBuilder<SelectedPhotoUploadWorker>()
                .setInputData(inputData)
                .addTag("upload-$jobId")
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                "upload-$jobId",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
    }
}
