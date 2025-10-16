package com.example.momentag.model

import android.net.Uri

data class Tag(
    val tagName: String,
    val thumbnailId: Long,
)

data class Photo(
    val photoId: Long,
    val tags: List<String>,
)

data class Album(
    val albumId: Long,
    val albumName: String,
    val thumbnailUri: Uri,
)

data class LoginRequest(
    val username: String,
    val password: String,
)

data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

data class RegisterResponse(
    val id: Int,
    // Todo: Uuid로 안 받고 Int로 받음
)

data class LoginResponse(
    val access_token: String,
    val refresh_token: String,
)

data class RefreshRequest(
    val refresh_token: String,
)

data class RefreshResponse(
    val access_token: String,
)
