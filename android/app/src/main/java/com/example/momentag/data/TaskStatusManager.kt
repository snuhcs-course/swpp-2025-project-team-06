package com.example.momentag.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.momentag.R
import com.example.momentag.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskStatusManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val taskRepository: TaskRepository,
    ) : DefaultLifecycleObserver {
        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        private val scope = CoroutineScope(Dispatchers.IO)
        private var pollingJob: Job? = null

        companion object {
            private const val CHANNEL_ID = "TaskStatusChannel"
            private const val NOTIFICATION_ID = 54321
            private const val POLL_INTERVAL_MS = 5000L // 5 seconds
        }

        fun initialize() {
            createNotificationChannel()
            ProcessLifecycleOwner.Companion
                .get()
                .lifecycle
                .addObserver(this)
        }

        override fun onStart(owner: LifecycleOwner) {
            // App came to foreground - start polling
            Log.d("TaskStatusManager", "App came to foreground - starting polling")
            startPolling()
        }

        override fun onStop(owner: LifecycleOwner) {
            // App went to background - stop polling and clear notification
            Log.d("TaskStatusManager", "App went to background - stopping polling")
            stopPolling()
            notificationManager.cancel(NOTIFICATION_ID)
        }

        private fun startPolling() {
            pollingJob?.cancel()
            pollingJob =
                scope.launch {
                    while (true) {
                        checkTaskStatus()
                        delay(POLL_INTERVAL_MS)
                    }
                }
        }

        private fun stopPolling() {
            pollingJob?.cancel()
            pollingJob = null
        }

        private suspend fun checkTaskStatus() {
            val pendingTaskIds = taskRepository.getPendingTaskIds().first()
            Log.d("TaskStatusManager", "checkTaskStatus called. Pending task IDs: ${pendingTaskIds.size}")

            if (pendingTaskIds.isEmpty()) {
                Log.d("TaskStatusManager", "No pending tasks, canceling notification")
                notificationManager.cancel(NOTIFICATION_ID)
                return
            }

            Log.d("TaskStatusManager", "Checking status for ${pendingTaskIds.size} tasks")

            updateNotification(
                context.getString(R.string.notification_processing_title),
                context.getString(R.string.notification_processing_text, pendingTaskIds.size),
                true,
            )

            val result = taskRepository.checkTaskStatus(pendingTaskIds.toList())

            if (result.isSuccess) {
                val statuses = result.getOrNull() ?: emptyList()
                val completedTaskIds = statuses.filter { it.status == "SUCCESS" }.map { it.taskId }
                val failedTaskIds = statuses.filter { it.status == "FAILURE" }.map { it.taskId }

                val doneTaskIds = completedTaskIds + failedTaskIds

                if (doneTaskIds.isNotEmpty()) {
                    taskRepository.removeTaskIds(doneTaskIds)
                }

                val remainingCount = pendingTaskIds.size - doneTaskIds.size

                if (remainingCount == 0) {
                    updateNotification(
                        context.getString(R.string.notification_processing_complete_title),
                        context.getString(R.string.notification_processing_complete_text),
                        false,
                    )
                    stopPolling()
                } else {
                    updateNotification(
                        context.getString(R.string.notification_processing_title),
                        context.getString(R.string.notification_processing_remaining, remainingCount),
                        true,
                    )
                }
            }
        }

        private fun updateNotification(
            title: String,
            text: String,
            ongoing: Boolean,
        ) {
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.mipmap.ic_launcher_foreground)
                    .setOngoing(ongoing)
                    .setAutoCancel(!ongoing)
                    .setOnlyAlertOnce(true)
                    .build()
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_processing_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.notification_channel_processing_description)
                    }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
