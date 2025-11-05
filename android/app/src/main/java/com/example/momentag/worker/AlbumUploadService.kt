package com.example.momentag.worker // (AndroidManifest.xml의 .worker. 와 일치)

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.momentag.R
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.Exception

class AlbumUploadService : Service() {
    private lateinit var notificationManager: NotificationManager
    private val notificationId = 12345
    private val channelId = "AlbumUploadChannel"

    // 서비스만의 독립적인 CoroutineScope 생성
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val albumUploadJobCount: MutableStateFlow<Int> by lazy {
        ViewModelFactory.getInstance(applicationContext).albumUploadJobCount
    }

    private val albumUploadSuccessEvent: MutableSharedFlow<Long> by lazy {
        ViewModelFactory.getInstance(applicationContext).albumUploadSuccessEvent
    }

    // 2단계에서 public으로 만든 리포지토리를 가져옵니다.
    private val localRepository: LocalRepository by lazy {
        ViewModelFactory.getInstance(applicationContext).localRepository
    }
    private val remoteRepository: RemoteRepository by lazy {
        ViewModelFactory.getInstance(applicationContext).remoteRepository
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // 1. ViewModel이 넘겨준 앨범 ID를 꺼냅니다.
        val albumId = intent?.getLongExtra("ALBUM_ID_KEY", -1L) ?: -1L
        if (albumId == -1L) {
            stopSelf(startId) // 앨범 ID가 없으면 서비스 종료
            return START_NOT_STICKY
        }

        // 2. 서비스 시작! 알림을 띄우고 포그라운드로 전환
        startForeground(notificationId, createNotification("업로드 준비 중..."))

        albumUploadJobCount.update { it + 1 }

        // 3. 실제 업로드 작업을 백그라운드 스레드에서 시작
        serviceScope.launch {
            try {
                doUploadWork(albumId)
            } catch (e: Exception) {
                // 알 수 없는 에러
                updateNotification("업로드 실패", e.message ?: "오류 발생")
            } finally {
                albumUploadJobCount.update { it - 1 }
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY // OS가 강제로 죽여도 자동 재시작 안 함
    }

    private suspend fun doUploadWork(albumId: Long) {
        // 1. 앨범의 '모든' 사진 정보 가져오기 (3.2에서 만듦)
        val allPhotosInfo = localRepository.getAlbumPhotoInfo(albumId)
        if (allPhotosInfo.isEmpty()) {
            updateNotification("업로드 완료", "업로드할 사진이 없습니다.")
            return // 성공
        }

        // 2. 8장씩 덩어리로 나누기
        val chunks = allPhotosInfo.chunked(8)
        val totalChunks = chunks.size

        // 3. 덩어리(chunk)를 하나씩 업로드
        chunks.forEachIndexed { index, chunk ->
            // UI에 진행 상황을 알림 (예: "3/10 업로드 중")
            updateNotification("앨범 업로드 중", "${index + 1} / $totalChunks 번째 묶음 처리 중")

            // 4. 이 8장짜리 덩어리를 실제 '업로드용 데이터'로 변환 (3.3에서 만듦)
            val uploadData = localRepository.createUploadDataFromChunk(chunk)

            // 5. 서버로 전송
            val response = remoteRepository.uploadPhotos(uploadData)

            // 6. 한 번이라도 실패하면 즉시 중단
            if (response !is RemoteRepository.Result.Success) {
                updateNotification("업로드 실패", "서버 전송 중 오류가 발생했습니다.")
                throw Exception("Upload failed for chunk ${index + 1}")
            }
        }
        albumUploadSuccessEvent.emit(albumId)

        // 7. 모든 덩어리가 성공하면? -> 대성공!
        updateNotification("업로드 완료", "총 ${allPhotosInfo.size}장의 사진이 업로드되었습니다.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // 코루틴 작업 취소
    }

    override fun onBind(intent: Intent?): IBinder? = null // 바인딩 안 함

    // --- 알림(Notification) 관련 헬퍼 함수 ---
    private fun createNotification(text: String): Notification =
        NotificationCompat
            .Builder(this, channelId)
            .setContentTitle("MomenTag 앨범 업로드")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // TODO: 앱 아이콘으로 변경
            .setOngoing(true)
            .build()

    private fun updateNotification(
        title: String,
        text: String,
    ) {
        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher) // TODO: 앱 아이콘으로 변경
                .setOngoing(false) // 완료/실패 시에는 알림을 스와이프해 지울 수 있게
                .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    channelId,
                    "MomenTag 업로드",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "앨범 사진 업로드 진행률 표시"
                }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
