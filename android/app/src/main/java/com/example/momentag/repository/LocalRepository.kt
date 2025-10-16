package com.example.momentag.repository

import android.content.ContentUris
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import com.example.momentag.model.Album
import com.example.momentag.model.PhotoMeta
import com.example.momentag.model.PhotoUploadData
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class LocalRepository(
    private val context: Context,
) {
    fun getImages(): List<Uri> {
        val imageUriList = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri =
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                    imageUriList.add(contentUri)
                }
            }
        return imageUriList
    }

    // Metadata 를 포함한 사진을 upload request 형태로 반환
    // test 시 일부만 upload 하게 수정 : 3개 정도
    // 인자로 album id list를 받아 selection, selectionArgs에 대입하면 album 별 사진 upload 하게 가능 (현재는 전체 upload)
    fun getPhotoUploadRequest(): PhotoUploadData {
        val photoParts = mutableListOf<MultipartBody.Part>()
        val metadataList = mutableListOf<PhotoMeta>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 3" // for test, use "LIMIT 3"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                // 파일 정보 가져오기
                val filename = cursor.getString(nameColumn) ?: "unknown.jpg"
                val createdAt = cursor.getLong(dateColumn).toString()

                // EXIF에서 위치 정보 가져오기 (변수 스코프 버그 수정)
                var finalLat = 0.0
                var finalLng = 0.0
                try {
                    context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                        val exif = ExifInterface(inputStream)
                        val lat = exif.getAttributeDouble(ExifInterface.TAG_GPS_LATITUDE, 0.0)
                        val lng = exif.getAttributeDouble(ExifInterface.TAG_GPS_LONGITUDE, 0.0)
                        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
                        val lngRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

                        // 남/서반구 보정
                        finalLat = if (latRef == "S") -lat else lat
                        finalLng = if (lngRef == "W") -lng else lng
                    }
                } catch (e: Exception) {
                    // EXIF 정보가 없거나 오류 발생 시 기본값(0.0, 0.0) 사용
                }

                // 1. 이미지 파일로 MultipartBody.Part 생성
                context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("photo", filename, requestBody)
                    photoParts.add(part)
                }

                // 2. 메타데이터 객체 생성 후 리스트에 추가
                metadataList.add(
                    PhotoMeta(
                        filename = filename,
                        photo_path_id = id.toInt(),
                        created_at = createdAt,
                        lat = finalLat,
                        lng = finalLng
                    )
                )
            }
        }

        // 3. 메타데이터 리스트를 하나의 JSON 문자열로 변환
        val gson = Gson()
        val metadataJson = gson.toJson(metadataList)
        val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())

        return PhotoUploadData(photoParts, metadataBody)
    }


    fun getAlbums(): List<Album> {
        val albums = mutableMapOf<Long, Album>()
        val projection =
            arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media._ID,
            )
        val sortOrder = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

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

    fun getImagesForAlbum(albumId: Long): List<Uri> {
        val images = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri =
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                    images.add(contentUri)
                }
            }
        return images
    }
}
