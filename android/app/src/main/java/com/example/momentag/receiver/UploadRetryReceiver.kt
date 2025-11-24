package com.example.momentag.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.momentag.worker.SelectedPhotoUploadWorker

class UploadRetryReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RETRY_UPLOAD = "com.example.momentag.action.RETRY_UPLOAD"
        const val EXTRA_FAILED_PHOTO_IDS = "FAILED_PHOTO_IDS"
        const val EXTRA_NOTIFICATION_ID = "NOTIFICATION_ID"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == ACTION_RETRY_UPLOAD) {
            val failedIds = intent.getLongArrayExtra(EXTRA_FAILED_PHOTO_IDS)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            if (failedIds != null && failedIds.isNotEmpty()) {
                val workRequest =
                    OneTimeWorkRequestBuilder<SelectedPhotoUploadWorker>()
                        .setInputData(workDataOf(SelectedPhotoUploadWorker.KEY_PHOTO_IDS to failedIds))
                        .build()

                WorkManager.getInstance(context).enqueue(workRequest)
            }

            if (notificationId != -1) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
        }
    }
}
