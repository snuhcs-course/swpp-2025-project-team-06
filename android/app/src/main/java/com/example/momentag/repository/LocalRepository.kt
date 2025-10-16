package com.example.momentag.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.momentag.model.Album

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
