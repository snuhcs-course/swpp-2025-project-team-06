package com.example.momentag.repository

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.momentag.model.Album
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoMeta
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoUploadData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PhotoInfoForUpload(
    val uri: Uri,
    val meta: PhotoMeta,
)

class LocalRepository(
    private val context: Context,
) {
    private val gson: Gson = Gson()

    companion object {
        private const val SEARCH_PREFS_NAME = "SearchHistoryPrefs"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_HISTORY_SIZE = 10
    }

    private val searchPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(SEARCH_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 이미지 리사이즈 & JPEG 압축
    // - contentUri 이미지를 비율 유지하며 maxWidth/maxHeight 안으로 줄이고
    // - JPEG로 압축한 ByteArray를 반환 (실패 시 null)
    // - API 28+ : ImageDecoder (EXIF 자동 반영) + 정수 샘플링 우선
    // - API <28 : BitmapFactory + inSampleSize(OR 루프) + EXIF 수동 회전 보정
    fun resizeImage(
        contentUri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        quality: Int = 85,
    ): ByteArray? {
        var bitmap: Bitmap? = null
        var sampled: Bitmap? = null
        var rotated: Bitmap? = null
        var finalBitmap: Bitmap? = null
        var baos: ByteArrayOutputStream? = null

        try {
            val cr = context.contentResolver

            // API 28 이상: ImageDecoder 사용
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(cr, contentUri)
                bitmap =
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val ow = info.size.width
                        val oh = info.size.height

                        if (ow > 0 && oh > 0) {
                            // 크게 줄일 때 정수 샘플링으로 효율 올림.
                            val sample =
                                maxOf(
                                    (ow + maxWidth - 1) / maxWidth,
                                    (oh + maxHeight - 1) / maxHeight,
                                ).coerceAtLeast(1)

                            if (sample > 1) {
                                decoder.setTargetSampleSize(sample)
                            } else if (ow > maxWidth || oh > maxHeight) {
                                val ratio =
                                    minOf(
                                        maxWidth.toFloat() / ow.toFloat(),
                                        maxHeight.toFloat() / oh.toFloat(),
                                    )
                                val tw = (ow * ratio).toInt().coerceAtLeast(1)
                                val th = (oh * ratio).toInt().coerceAtLeast(1)
                                decoder.setTargetSize(tw, th)
                            }
                        }

                        // JPEG 압축 안정성을 위해 SW 비트맵으로
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                    }

                baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val bytes = baos.toByteArray()
                baos.close()
                bitmap.recycle()
                return bytes
            }

            // API 28 미만일 때
            // 먼저 원본 크기 파악
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(contentUri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null

            val ow = bounds.outWidth
            val oh = bounds.outHeight
            if (ow <= 0 || oh <= 0) return null

            // inSampleSize 계산: OR 조건으로 한쪽이라도 넘으면 계속 2배로
            var inSampleSize = 1
            while ((oh / inSampleSize) > maxHeight || (ow / inSampleSize) > maxWidth) {
                inSampleSize *= 2
            }

            // Sampling Decode
            val decodeOpts =
                BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
            sampled =
                cr.openInputStream(contentUri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return null

            // EXIF 회전/플립 보정
            rotated = applyExifRotation(cr, contentUri, sampled)

            // 아직도 큰 경우, 비율 유지 Scale (최소 1픽셀 보장)
            val cw = rotated.width
            val ch = rotated.height
            finalBitmap =
                if (cw > maxWidth || ch > maxHeight) {
                    val ratio =
                        minOf(
                            maxWidth.toFloat() / cw.toFloat(),
                            maxHeight.toFloat() / ch.toFloat(),
                        )
                    val tw = (cw * ratio).toInt().coerceAtLeast(1)
                    val th = (ch * ratio).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(rotated, tw, th, true)
                    if (scaled !== rotated) rotated.recycle()
                    scaled
                } else {
                    rotated
                }

            // JPEG 압축
            baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            baos.close()
            finalBitmap.recycle()
            return bytes
        } catch (t: Throwable) {
            // OOM 포함 모든 치명적 오류 시 리소스 정리
            try {
                baos?.close()
            } catch (_: Exception) {
            }

            // 생성된 비트맵들을 안전하게 recycle
            try {
                bitmap?.recycle()
            } catch (_: Exception) {
            }
            try {
                if (sampled != null && sampled !== rotated) sampled.recycle()
            } catch (_: Exception) {
            }
            try {
                if (rotated != null && rotated !== finalBitmap) rotated.recycle()
            } catch (_: Exception) {
            }
            try {
                finalBitmap?.recycle()
            } catch (_: Exception) {
            }

            return null
        }
    }

    // EXIF 회전/플립 보정 API < 28 전용
    // 새 비트맵을 만들면 원본은 recycle() 처리
    private fun applyExifRotation(
        cr: android.content.ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap =
        try {
            val exif = cr.openInputStream(uri)?.use { ExifInterface(it) }
            val o =
                exif?.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                ) ?: ExifInterface.ORIENTATION_NORMAL

            val m = android.graphics.Matrix()
            when (o) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                else -> { /* no-op */ }
            }

            if (m.isIdentity) {
                bitmap
            } else {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                if (rotated !== bitmap) bitmap.recycle()
                rotated
            }
        } catch (_: Throwable) {
            bitmap
        }

    fun getAlbums(): List<Album> {
        val albums = mutableMapOf<Long, Album>()
        val projection =
            arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media._ID,
            )
        val sortOrder = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC, ${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val imageIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getLong(bucketIdColumn)
                    if (!albums.containsKey(bucketId)) {
                        val bucketName = cursor.getString(bucketNameColumn)
                        val imageId = cursor.getLong(imageIdColumn)
                        val thumbnailUri =
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                imageId,
                            )
                        albums[bucketId] = Album(albumId = bucketId, albumName = bucketName, thumbnailUri = thumbnailUri)
                    }
                }
            }
        return albums.values.toList()
    }

    fun getImagesForAlbum(albumId: Long): List<Photo> {
        val images = mutableListOf<Photo>()
        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED, // <<< [수정] DATE_ADDED 추가
            )
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED) // <<< [수정] 인덱스 가져오기

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)

                    // --- [수정] DATE_TAKEN이 없으면 DATE_ADDED 사용 ---
                    var dateValue = cursor.getLong(dateTakenColumn)
                    if (dateValue == 0L) { // DATE_TAKEN이 0이거나 없는 경우
                        val dateAddedSeconds = cursor.getLong(dateAddedColumn)
                        if (dateAddedSeconds > 0L) {
                            dateValue = dateAddedSeconds * 1000L // DATE_ADDED는 초(second) 단위이므로 밀리초로 변환
                        }
                    }
                    // --- [수정] 끝 ---

                    val contentUri =
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                    val createdAt =
                        try {
                            val date = Date(dateValue)
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            sdf.format(date)
                        } catch (e: Exception) {
                            ""
                        }
                    images.add(
                        Photo(
                            photoId = id.toString(),
                            contentUri = contentUri,
                            createdAt = createdAt,
                        ),
                    )
                }
            }
        return images
    }

    fun toPhotos(photoResponses: List<PhotoResponse>): List<Photo> {
        return photoResponses.mapNotNull { photoResponse ->
            try {
                // Filter out invalid MediaStore IDs
                if (photoResponse.photoPathId <= 0) {
                    return@mapNotNull null
                }

                val contentUri =
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        photoResponse.photoPathId,
                    )

                // Verify the image still exists in MediaStore
                val exists =
                    context.contentResolver
                        .query(
                            contentUri,
                            arrayOf(MediaStore.Images.Media._ID),
                            null,
                            null,
                            null,
                        )?.use { cursor ->
                            cursor.moveToFirst()
                        } ?: false

                if (exists) {
                    Photo(
                        photoId = photoResponse.photoId,
                        contentUri = contentUri,
                        createdAt = photoResponse.createdAt,
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                // Skip photos that cause errors during URI conversion or validation
                null
            }
        }
    }

    /**
     * Extract formatted date from MediaStore for a given photo path ID
     * @param photoPathId MediaStore ID
     * @return Formatted date string (e.g., "2024.10.15") or "Unknown date"
     */
    fun getPhotoDate(photoPathId: Long): String {
        return try {
            val contentUri =
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    photoPathId,
                )

            // --- [수정] DATE_ADDED를 projection에 추가 ---
            val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
            context.contentResolver
                .query(contentUri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                        val dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED) // <<< [수정]

                        if (dateTakenIndex != -1) {
                            // --- [수정] DATE_TAKEN이 없으면 DATE_ADDED 사용 ---
                            var dateValue = cursor.getLong(dateTakenIndex)
                            if (dateValue == 0L && dateAddedIndex != -1) {
                                val dateAddedSeconds = cursor.getLong(dateAddedIndex)
                                if (dateAddedSeconds > 0L) {
                                    dateValue = dateAddedSeconds * 1000L // 밀리초로 변환
                                }
                            }
                            // --- [수정] 끝 ---

                            if (dateValue > 0L) { // 유효한 날짜 값이 있을 때만 포맷
                                val date = Date(dateValue)
                                val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                                return sdf.format(date)
                            }
                        }
                    }
                    "Unknown date"
                } ?: "Unknown date"
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    /**
     * Extract GPS location from EXIF data for a given photo path ID
     * @param photoPathId MediaStore ID
     * @return Formatted location string (e.g., "37.123, 127.456") or "Unknown location"
     */
    fun getPhotoLocation(photoPathId: Long): String =
        try {
            val contentUri =
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    photoPathId,
                )

            context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = FloatArray(2)

                if (exif.getLatLong(latLong)) {
                    val lat = String.format(Locale.US, "%.3f", latLong[0])
                    val lng = String.format(Locale.US, "%.3f", latLong[1])
                    "$lat, $lng"
                } else {
                    "Unknown location"
                }
            } ?: "Unknown location"
        } catch (e: Exception) {
            "Unknown location"
        }

    fun getAlbumPhotoInfo(albumId: Long): List<PhotoInfoForUpload> {
        val photoInfoList = mutableListOf<PhotoInfoForUpload>()
        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED, // <<< [수정] DATE_ADDED 추가
            )
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED) // <<< [수정] 인덱스 가져오기

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val filename = cursor.getString(nameColumn) ?: "unknown.jpg"

                    // --- [수정] DATE_TAKEN이 없으면 DATE_ADDED 사용 ---
                    var dateValue = cursor.getLong(dateTakenColumn)
                    if (dateValue == 0L) { // DATE_TAKEN이 0이거나 없는 경우
                        val dateAddedSeconds = cursor.getLong(dateAddedColumn)
                        if (dateAddedSeconds > 0L) {
                            dateValue = dateAddedSeconds * 1000L // DATE_ADDED는 초(second) 단위이므로 밀리초로 변환
                        }
                    }
                    // --- [수정] 끝 ---

                    val createdAt =
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                            .apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
                            .format(Date(dateValue))

                    var finalLat = 0.0
                    var finalLng = 0.0
                    try {
                        context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
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
                    photoInfoList.add(PhotoInfoForUpload(contentUri, meta))
                }
            }
        return photoInfoList
    }

    fun createUploadDataFromChunk(chunk: List<PhotoInfoForUpload>): PhotoUploadData {
        val photoParts = mutableListOf<MultipartBody.Part>()
        val metadataList = mutableListOf<PhotoMeta>()

        chunk.forEach { photoInfo ->
            // (기존 getPhotoUploadRequest의 리사이즈/변환 로직 재사용)
            val resizedBytes = resizeImage(photoInfo.uri, maxWidth = 224, maxHeight = 224, quality = 85)
            val (bytes, mime) =
                if (resizedBytes != null) {
                    resizedBytes to "image/jpeg"
                } else {
                    val raw = context.contentResolver.openInputStream(photoInfo.uri)?.use { it.readBytes() }
                    val type = context.contentResolver.getType(photoInfo.uri) ?: "application/octet-stream"
                    raw to type
                }

            bytes?.let { b ->
                val requestBody = b.toRequestBody(mime.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("photo", photoInfo.meta.filename, requestBody)
                photoParts.add(part)
            }
            metadataList.add(photoInfo.meta)
        }

        val gson = Gson()
        val metadataJson = gson.toJson(metadataList)
        val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())

        return PhotoUploadData(photoParts, metadataBody)
    }

    fun getSearchHistory(): List<String> {
        val json = searchPrefs.getString(KEY_SEARCH_HISTORY, null)
        if (json.isNullOrEmpty()) {
            return emptyList()
        }

        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            searchPrefs.edit().remove(KEY_SEARCH_HISTORY).apply()
            emptyList()
        }
    }

    /**
     * 새 검색어를 최근 검색 기록에 추가합니다.
     * - 이미 존재하면, 맨 위로 이동시킵니다.
     * - 최대 10(MAX_HISTORY_SIZE)개까지만 유지합니다.
     *
     * @param query 추가할 검색어
     */
    fun addSearchHistory(query: String) {
        val currentHistory = getSearchHistory().toMutableList()

        currentHistory.remove(query)
        currentHistory.add(0, query)

        val updatedHistory = currentHistory.take(MAX_HISTORY_SIZE)
        val json = gson.toJson(updatedHistory)

        searchPrefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
    }

    /**
     * 특정 검색어를 기록에서 삭제합니다.
     *
     * @param query 삭제할 검색어
     */
    fun removeSearchHistory(query: String) {
        val currentHistory = getSearchHistory().toMutableList()

        if (currentHistory.remove(query)) {
            val json = gson.toJson(currentHistory)
            searchPrefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
        }
    }

    fun clearSearchHistory() {
        searchPrefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }
}
