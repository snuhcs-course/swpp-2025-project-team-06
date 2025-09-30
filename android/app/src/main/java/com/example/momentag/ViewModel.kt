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
}