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

data class TagResponse(
    @SerializedName("tag")
    val tagName: String,
    @SerializedName("tag_id")
    val tagId: String,
    @SerializedName("thumbnail_path_id")
    val thumbnailPhotoPathId: Long?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?,
    @SerializedName("photo_count")
    val photoCount: Int,
)

data class TagItem(
    val tagName: String,
    val coverImageId: Long?,
    val tagId: String,
    val createdAt: String?,
    val updatedAt: String?,
    val photoCount: Int,
)

data class TagName(
    @SerializedName("tag")
    val name: String,
)

data class TagId(
    @SerializedName("tag_id")
    val id: String,
)

data class TagCntData(
    val tagId: String,
    val tagName: String,
    val count: Int,
)

data class PhotoDetailResponse(
    @SerializedName("photo_path_id") val photoPathId: Long,
    @SerializedName("address") val address: String?,
    val tags: List<Tag>,
)

data class PhotoResponse(
    @SerializedName("photo_id") val photoId: String,
    @SerializedName("photo_path_id") val photoPathId: Long,
    @SerializedName("created_at") val createdAt: String,
)

data class StoryResponse(
    @SerializedName("photo_id") val photoId: String,
    @SerializedName("photo_path_id") val photoPathId: Long,
    val tags: List<Tag>,
)

data class Photo(
    val photoId: String,
    val contentUri: Uri,
    val createdAt: String,
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

data class PhotoToPhotoRequest(
    val photos: List<String>,
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

// ========== Story Models =================
data class StoryModel(
    val id: String,
    val photoId: String,
    val images: List<Uri>,
    val date: String,
    val location: String,
    val suggestedTags: List<String>,
)
