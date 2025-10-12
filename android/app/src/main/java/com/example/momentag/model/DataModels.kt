package com.example.momentag.model

import android.net.Uri
import okhttp3.MultipartBody
import okhttp3.RequestBody
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    val email: String,
    val password: String,
)

data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

@OptIn(ExperimentalUuidApi::class)
data class RegisterResponse(
    val id: Uuid,
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

data class PhotoMeta(
    val filename: String,
    val photo_path_id: Int,
    val created_at: String,
    val lat: Double,
    val lng: Double
)

data class PhotoUploadData(
    val photo: List<MultipartBody.Part>,
    val metadata: RequestBody
)