package com.example.momentag.model

import android.net.Uri
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody

data class Tag(
    @SerializedName("tag")
    val tagName: String,
    @SerializedName("tag_id")
    val tagId: String,
)

data class TagItem(
    val tagName: String,
    val coverImageId: Long?,
    val tagId: String,
)

data class TagCreateRequest(
    @SerializedName("tag")
    val name: String,
)

data class PhotoDetailResponse(
    @SerializedName("photo_path_id") val photoPathId: Long,
    val tags: List<Tag>,
)

data class PhotoResponse(
    @SerializedName("photo_id") val photoId: String,
    @SerializedName("photo_path_id") val photoPathId: Long,
)

data class Photo(
    val photoId: String,
    val contentUri: Uri,
)

data class Photos(
    val photos: List<Photo>,
)

data class TagIdRequest(
    @SerializedName("tag_id")
    val tagId: String,
)

data class TagCreateResponse(
    @SerializedName("tag_id")
    val tagId: String,
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
