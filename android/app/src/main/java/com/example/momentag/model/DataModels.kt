package com.example.momentag.model

import android.net.Uri
import okhttp3.MultipartBody
import okhttp3.RequestBody

data class Tag(
    val tagName: String,
    val tagId: Long,
)

data class Tags(
    val tags: List<Tag>,
)

data class TagItem(
    val tagName: String,
    val coverImageId: Long?
)

data class TagCreateRequest(
    val name: String,
)

data class Photo(
    val photoId: Long,
)

data class Photos(
    val photos: List<Photo>,
)

data class PhotoTag(
    val ptId: Long,
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

// TODO : photo name까지 받아오기
data class RecommendPhotosResponse(
    val photos: List<Long>,
)

// ========== Upload Models ==========

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
