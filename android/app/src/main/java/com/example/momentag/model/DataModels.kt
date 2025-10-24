package com.example.momentag.model

import android.net.Uri
import okhttp3.MultipartBody
import okhttp3.RequestBody

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

// ========== Semantic Search Models ==========

/**
 * Semantic Search 응답 모델
 * Backend: {"photos": [1, 2, 3, ...]}
 */
data class SemanticSearchResponse(
    val photos: List<Int>,
)

data class PhotoMeta(
    val filename: String,
    val photo_path_id: Int,
    val created_at: String,
    val lat: Double,
    val lng: Double,
)

data class PhotoUploadData(
    val photo: List<MultipartBody.Part>,
    val metadata: RequestBody,
)

// ========== Story Models =================
data class Story(
    val id: String,
    val images: List<String>,
    val date: String,
    val location: String,
    val suggestedTags: List<String>
)