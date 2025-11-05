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

class AlbumUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    // 1. Factory에서 리포지토리와 공유 상태 변수들을 가져옵니다.
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
        const val KEY_ALBUM_ID = "ALBUM_ID"
        const val KEY_PROGRESS = "PROGRESS"

        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "AlbumUploadChannel"
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        // 알림 채널 생성 (유지)
        createNotificationChannel()

        // 알림 생성 (유지)
        val notification = createNotification(progress)

        // 👇 [수T] 3. OS 버전에 따라 다른 생성자를 사용합니다.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29 (Q)부터 타입이 필요
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                // 👇 [핵심] "이건 dataSync 비자입니다"라고 명시
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            // 구형 OS는 낡은 생성자 사용
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    // 👇 [추가] 3. 알림 생성 헬퍼 (Service에서 가져옴)
    private fun createNotification(text: String): Notification =
        NotificationCompat
            .Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("MomenTag 앨범 업로드")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "MomenTag 업로드",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "앨범 사진 업로드 진행률 표시"
                }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val albumId = inputData.getLong(KEY_ALBUM_ID, -1L)
        if (albumId == -1L) {
            return Result.failure()
        }

        val initialProgress = "업로드 준비 중..."
        setForeground(createForegroundInfo(initialProgress))

        albumUploadJobCount.update { it + 1 }

        try {
            val success = processAlbumInChunks(albumId, 8)

            if (success) {
                albumUploadSuccessEvent.emit(albumId)
                updateNotification("업로드 완료", "앨범 업로드가 성공적으로 완료되었습니다.")
                return Result.success()
            } else {
                updateNotification("업로드 실패", "일부 파일 업로드에 실패했습니다.")
                return Result.failure()
            }
        } catch (e: Exception) {
            updateNotification("업로드 오류", "알 수 없는 오류가 발생했습니다.")
            return Result.failure()
        } finally {
            albumUploadJobCount.update { it - 1 }
        }
    }

    private suspend fun processAlbumInChunks(
        albumId: Long,
        chunkSize: Int,
    ): Boolean {
        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
            )
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        // 1. 커서를 연다 (아직 사진을 다 읽지 않음)
        val cursor =
            applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            ) ?: return false // 커서 열기 실패

        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

        var chunkCount = 0
        val totalPhotos = cursor.count // (진행률 표시를 위해 전체 카운트만 가져옴)
        val totalChunks = (totalPhotos + chunkSize - 1) / chunkSize

        val currentChunk = mutableListOf<PhotoInfoForUpload>()

        // 2. 커서를 한 칸씩 이동하며 8장이 모일 때마다 업로드
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val filename = cursor.getString(nameColumn) ?: "unknown.jpg"

            // 메타데이터 추출 (기존 getAlbumPhotoInfo 로직과 동일)
            val dateValue = cursor.getLong(dateTakenColumn)
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
                // 0.0 유지
            }

            val meta =
                PhotoMeta(
                    filename = filename,
                    photo_path_id = id.toInt(),
                    created_at = createdAt,
                    lat = finalLat,
                    lng = finalLng,
                )
            // 3. 8장짜리 묶음에 추가
            currentChunk.add(PhotoInfoForUpload(contentUri, meta))

            // 4. 8장이 찼거나, 마지막 사진이면 업로드!
            if (currentChunk.size == chunkSize || (currentChunk.isNotEmpty() && cursor.isLast)) {
                chunkCount++
                // 진행률 업데이트
                val progressText = "($chunkCount / $totalChunks) 묶음 업로드 중..."
                setProgress(workDataOf(KEY_PROGRESS to progressText))
                updateNotification("앨범 업로드 중", progressText)

                // 8장 묶음을 업로드 데이터로 변환 (이 함수는 LocalRepository에서 복사/이동)
                val uploadData = createUploadDataFromChunk(currentChunk)

                // 업로드
                val response = remoteRepository.uploadPhotos(uploadData)

                if (response !is RemoteRepository.Result.Success) {
                    cursor.close() // 실패 시 커서 닫기
                    return false // 실패!
                }

                // 성공하면 묶음 비우기
                currentChunk.clear()
            }
        }

        cursor.close() // 5. 모든 작업 완료 후 커서 닫기
        return true // 성공!
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
                .setOngoing(false) // 완료/실패 시에는 알림을 스와이프해 지울 수 있게
                .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // (LocalRepository에선 이 함수를 지워도 됩니다)
    private fun createUploadDataFromChunk(chunk: List<PhotoInfoForUpload>): PhotoUploadData {
        val photoParts = mutableListOf<MultipartBody.Part>()
        val metadataList = mutableListOf<PhotoMeta>()

        chunk.forEach { photoInfo ->

            // 1. 'Throwable'로 사진 한 장을 감싸서 OOM 등으로부터 Worker를 보호
            try {
                // 2. 리사이즈 시도
                val resizedBytes =
                    localRepository.resizeImage(
                        photoInfo.uri,
                        maxWidth = 224,
                        maxHeight = 224,
                        quality = 85,
                    )

                // 3. 리사이즈 성공 시에만 처리 (실패 시 원본 전송 안 함)
                if (resizedBytes != null) {
                    val mime = "image/jpeg"
                    val requestBody = resizedBytes.toRequestBody(mime.toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("photo", photoInfo.meta.filename, requestBody)

                    photoParts.add(part)
                    metadataList.add(photoInfo.meta) // 성공한 사진의 메타데이터만 추가
                } else {
                    // 4. 리사이즈 실패 시 (null 반환 시)
                    // 원본을 보내는 대신, 로그만 남기고 이 사진을 '포기(skip)'합니다.
                    Log.w(
                        "AlbumUploadWorker",
                        "Resize failed for ${photoInfo.meta.filename} (unsupported format? corrupted?). SKIPPING file.",
                    )
                }
            } catch (t: Throwable) {
                // 5. OOM 등 심각한 오류가 나면 여기서 잡고 이 사진만 '포기(skip)'
                Log.e(
                    "AlbumUploadWorker",
                    "CRITICAL: Failed to process photo. SKIPPING file: ${photoInfo.meta.filename}",
                    t,
                )
            }
        } // end of forEach

        // 6. 성공적으로 처리된 사진들(photoParts)과 그 짝(metadataList)만으로 요청 생성
        val metadataJson = gson.toJson(metadataList)
        val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())

        return PhotoUploadData(photoParts, metadataBody)
    }
}
