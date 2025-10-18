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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    // Returns an 'upload request' with photo metadata
    // Currently uploads only 3 photos for testing
    // Can be adapted to upload by album by providing album ID list and modifying selection/selectionArgs
    fun getPhotoUploadRequest(): PhotoUploadData {
        val photoParts = mutableListOf<MultipartBody.Part>()
        val metadataList = mutableListOf<PhotoMeta>()

        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
            )
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
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                var count = 3 // for testing

                while (cursor.moveToNext() && count-- > 0) {
                    val id = cursor.getLong(idColumn)
                    val contentUri =
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )

                    val filename = cursor.getString(nameColumn) ?: "unknown.jpg"

                    val dateValue = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)) * 1000L // 초 -> 밀리초
                    val date = Date(dateValue)

                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
                    val createdAt = sdf.format(date)

                    var finalLat = 0.0
                    var finalLng = 0.0
                    try {
                        context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                            val exif = ExifInterface(inputStream)
                            val latOutput = FloatArray(2)

                            val hasLatLong: Boolean = exif.getLatLong(latOutput)

                            if (hasLatLong) {
                                finalLat = latOutput[0].toDouble()
                                finalLng = latOutput[1].toDouble()
                            }
                        }
                    } catch (e: Exception) {
                        // 0.0 for no EXIF info
                    }

                    // generate MultipartBody.Part
                    context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("photo", filename, requestBody)
                        photoParts.add(part)
                    }

                    // generate metadata list
                    metadataList.add(
                        PhotoMeta(
                            filename = filename,
                            photo_path_id = id.toInt(),
                            created_at = createdAt,
                            lat = finalLat,
                            lng = finalLng,
                        ),
                    )
                }
            }

        // convert metadata to JSON
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
