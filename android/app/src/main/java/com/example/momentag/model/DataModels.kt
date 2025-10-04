package com.example.momentag.model

import android.net.Uri

data class Tag(
    val tagName: String,
    val thumbnailId: Long
)

data class Photo(
    val photoId: Long,
    val tags: List<String>
)

data class Album(
    val albumId: Long,
    val albumName: String,
    val thumbnailUri: Uri
)