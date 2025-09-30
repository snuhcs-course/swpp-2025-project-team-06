package com.example.momentag

import android.net.Uri

data class Album(
    val id: Long,
    val name: String,
    val thumbnailUri: Uri
)