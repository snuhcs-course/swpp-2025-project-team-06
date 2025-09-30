package com.example.momentag

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewModel : ViewModel() {

    private val _imageUris = MutableStateFlow<List<Uri>>(emptyList())
    val imageUris = _imageUris.asStateFlow()

    fun loadImages(context: Context) {
        viewModelScope.launch {
            val uris = withContext(Dispatchers.IO) {
                val imageUriList = mutableListOf<Uri>()
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        imageUriList.add(contentUri)
                    }
                }
                imageUriList
            }
            _imageUris.value = uris
        }
    }

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums = _albums.asStateFlow()

    fun loadAlbums(context: Context) {
        viewModelScope.launch {
            val albumList = withContext(Dispatchers.IO) {
                val albums = mutableMapOf<Long, Album>()
                val projection = arrayOf(
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media._ID
                )
                val sortOrder = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} ASC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val imageIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

                    while (cursor.moveToNext()) {
                        val bucketId = cursor.getLong(bucketIdColumn)
                        if (!albums.containsKey(bucketId)) {
                            val bucketName = cursor.getString(bucketNameColumn)
                            val imageId = cursor.getLong(imageIdColumn)
                            val thumbnailUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                imageId
                            )
                            albums[bucketId] = Album(id = bucketId, name = bucketName, thumbnailUri = thumbnailUri)
                        }
                    }
                }
                albums.values.toList()
            }
            _albums.value = albumList
        }
    }

    fun loadImagesForAlbum(context: Context, bucketId: Long) {
        viewModelScope.launch {
            val imageList = withContext(Dispatchers.IO) {
                val images = mutableListOf<Uri>()
                val projection = arrayOf(MediaStore.Images.Media._ID)

                val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"

                val selectionArgs = arrayOf(bucketId.toString())

                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        images.add(contentUri)
                    }
                }
                images
            }
            _imageUris.value = imageList
        }
    }
}

