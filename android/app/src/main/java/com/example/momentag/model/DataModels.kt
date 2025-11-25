package com.example.momentag.model

import android.net.Uri
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody

// ========== Tag Models ==========

data class Tag(
    @SerializedName("tag")
    val tagName: String,
    @SerializedName("tag_id")
    val tagId: String,
)

data class TagName(
    @SerializedName("tag")
    val name: String,
)

data class TagId(
    @SerializedName("tag_id")
    val id: String,
)

data class TagItem(
    val tagName: String,
    val coverImageId: Long?,
    val tagId: String,
    val createdAt: String?,
    val updatedAt: String?,
    val photoCount: Int,
)

data class TagCntData(
    val tagId: String,
    val tagName: String,
    val count: Int,
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

// ========== Photo Models ==========

data class Photo(
    val photoId: String,
    val contentUri: Uri,
    val createdAt: String,
)

data class Photos(
    val photos: List<Photo>,
)

data class PhotoMeta(
    val filename: String,
    val photo_path_id: Long,
    val created_at: String,
    val lat: Double,
    val lng: Double,
)

data class PhotoUploadData(
    val photo: List<MultipartBody.Part>,
    val metadata: RequestBody,
)

data class PhotoToPhotoRequest(
    val photos: List<String>,
)

data class PhotoResponse(
    @SerializedName("photo_id") val photoId: String,
    @SerializedName("photo_path_id") val photoPathId: Long,
    @SerializedName("created_at") val createdAt: String,
)

data class PhotoDetailResponse(
    @SerializedName("photo_path_id") val photoPathId: Long,
    @SerializedName("address") val address: String?,
    val tags: List<Tag>,
)

data class ImageContext(
    val images: List<Photo>,
    val currentIndex: Int,
    val contextType: ContextType,
) {
    sealed class ContextType {
        data class Album(
            val albumName: String,
        ) : ContextType()

        data class TagAlbum(
            val tagName: String,
        ) : ContextType()

        data class SearchResult(
            val query: String,
        ) : ContextType()

        object Gallery : ContextType()

        object Story : ContextType()
    }
}

// ========== Album Models ==========

data class Album(
    val albumId: Long,
    val albumName: String,
    val thumbnailUri: Uri,
)

data class TagAlbum(
    val tagName: String,
    val photos: List<String>,
)

// ========== Search Models ==========

data class SearchResultItem(
    val query: String,
    val photo: Photo,
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

data class StoryResponse(
    @SerializedName("photo_id") val photoId: String,
    @SerializedName("photo_path_id") val photoPathId: Long,
    val tags: List<String>,
)

// ========== Auth Models ==========

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val access_token: String,
    val refresh_token: String,
)

data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
)

data class RegisterResponse(
    val id: Int,
)

data class RefreshRequest(
    val refresh_token: String,
)

data class RefreshResponse(
    val access_token: String,
)

// ========== Task Models =================

data class TaskInfo(
    @SerializedName("task_id")
    val taskId: String,
    @SerializedName("photo_path_ids")
    val photoPathIds: List<Long>,
)

data class TaskStatus(
    @SerializedName("task_id")
    val taskId: String,
    @SerializedName("status")
    val status: String,
)

// ========== Upload Job Models =================

data class UploadJobState(
    val jobId: String, // Unique ID for this upload job
    val type: UploadType, // ALBUM or SELECTED_PHOTOS
    val albumId: Long?, // For album uploads
    val status: UploadStatus, // RUNNING, PAUSED, COMPLETED, FAILED
    val totalPhotoIds: List<Long>, // Fixed list of photos to upload (never re-query)
    val failedPhotoIds: List<Long>, // Failed photos (for retry)
    val currentChunkIndex: Int, // Resume point (chunks before this are done)
    val createdAt: Long, // Timestamp for sorting
)

enum class UploadType {
    ALBUM,
    SELECTED_PHOTOS,
}

enum class UploadStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}
