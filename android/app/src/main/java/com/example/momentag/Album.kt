package com.example.momentag

import android.net.Uri

data class Album(
    val albumId: Long,
    val albumName: String,
    val thumbnailUri: Uri
)