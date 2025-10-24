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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.io.ByteArrayOutputStream
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
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

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

    // Resize and compress image from content Uri to a JPEG byte array.
    // Returns null on failure.
    private fun resizeImage(contentUri: Uri, maxWidth: Int, maxHeight: Int, quality: Int = 85): ByteArray? {
        try {
            // Prefer ImageDecoder on P+ for correct orientation, sampling and better memory behavior
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, contentUri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val origW = info.size.width
                    val origH = info.size.height
                    if (origW > 0 && origH > 0 && (origW > maxWidth || origH > maxHeight)) {
                        val ratio = minOf(maxWidth.toFloat() / origW.toFloat(), maxHeight.toFloat() / origH.toFloat())
                        val targetW = (origW * ratio).toInt().coerceAtLeast(1)
                        val targetH = (origH * ratio).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                    // prefer software allocator for reliable compress
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val bytes = baos.toByteArray()
                baos.close()
                bitmap.recycle()
                return bytes
            }

            // Fallback for older devices: use BitmapFactory sampling + scaling
            val cr = context.contentResolver

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(contentUri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) } ?: return null

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var inSampleSize = 1
            if (origHeight > maxHeight || origWidth > maxWidth) {
                val halfHeight = origHeight / 2
                val halfWidth = origWidth / 2
                while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val sampledBitmap = cr.openInputStream(contentUri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: return null

            var finalBitmap = sampledBitmap
            val width = sampledBitmap.width
            val height = sampledBitmap.height
            if (width > maxWidth || height > maxHeight) {
                val ratio = minOf(maxWidth.toFloat() / width.toFloat(), maxHeight.toFloat() / height.toFloat())
                val targetW = (width * ratio).toInt()
                val targetH = (height * ratio).toInt()
                val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetW, targetH, true)
                if (scaled != sampledBitmap) sampledBitmap.recycle()
                finalBitmap = scaled
            }

            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            baos.close()
            finalBitmap.recycle()
            return bytes
        } catch (e: Exception) {
            return null
        }
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
                MediaStore.Images.Media.DATE_TAKEN,
            )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

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

                    val dateValue = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))
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
                    // Resize & compress to JPEG to reduce upload size and memory usage. If resize fails, fall back to raw bytes.
                    val resizedBytes = resizeImage(contentUri, maxWidth = 1280, maxHeight = 1280, quality = 85)
                        ?: context.contentResolver.openInputStream(contentUri)?.use { it.readBytes() }

                    resizedBytes?.let { bytes ->
                        // compressed to JPEG when resizeImage succeeds; if fallback raw bytes, keep generic image/* or prefer jpeg
                        val mediaType = if (resizedBytes === bytes) "image/jpeg" else "image/*"
                        val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
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

    fun getImagesForAlbum(albumId: Long): List<Uri> {
        val images = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
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
