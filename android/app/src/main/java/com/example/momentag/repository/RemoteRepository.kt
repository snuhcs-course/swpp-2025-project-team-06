package com.example.momentag.repository

import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.model.Tag
import com.example.momentag.model.TagId
import com.example.momentag.model.TagName
import com.example.momentag.network.ApiService
import retrofit2.HttpException
import java.io.IOException

class RemoteRepository(
    private val apiService: ApiService,
) {
    sealed class Result<T> {
        data class Success<T>(
            val data: T,
        ) : Result<T>()

        data class Error<T>(
            val code: Int,
            val message: String,
        ) : Result<T>()

        data class Unauthorized<T>(
            val message: String,
        ) : Result<T>()

        data class BadRequest<T>(
            val message: String,
        ) : Result<T>()

        data class NetworkError<T>(
            val message: String,
        ) : Result<T>()

        data class Exception<T>(
            val e: kotlin.Exception,
        ) : Result<T>()
    }

    suspend fun getAllTags(): Result<List<Tag>> =
        try {
            val response = apiService.getAllTags()

            if (response.isSuccessful) {
                response.body()?.let { tags ->
                    Result.Success(tags)
                } ?: Result.Error(response.code(), "Response body is null")
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: HttpException) {
            Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun getAllPhotos(): Result<List<PhotoResponse>> =
        try {
            val response = apiService.getAllPhotos()

            if (response.isSuccessful) {
                response.body()?.let { photos ->
                    Result.Success(photos)
                } ?: Result.Error(response.code(), "Response body is null")
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: HttpException) {
            Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun getPhotoDetail(photoId: String): Result<PhotoDetailResponse> =
        try {
            val response = apiService.getPhotoDetail(photoId)

            if (response.isSuccessful) {
                val detail = response.body()!!
                Result.Success(detail)
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            Result.NetworkError("Network error: ${e.message}")
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun getPhotosByTag(tagId: String): Result<List<PhotoResponse>> =
        try {
            val response = apiService.getPhotosByTag(tagId)

            if (response.isSuccessful) {
                response.body()?.let { photos ->
                    Result.Success(photos)
                } ?: Result.Error(response.code(), "Response body is null")
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: HttpException) {
            Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun postTags(tagName: String): Result<TagId> =
        try {
            val request = TagName(name = tagName)
            val response = apiService.postTags(request)

            if (response.isSuccessful) {
                response.body()?.let { TagId ->
                    Result.Success(TagId)
                } ?: Result.Error(response.code(), "Response body is null")
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: HttpException) {
            Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun uploadPhotos(photoUploadData: PhotoUploadData): Result<Int> { // 성공 시 반환값이 없다면 Unit 사용
        return try {
            val response =
                apiService.uploadPhotos(
                    photo = photoUploadData.photo,
                    metadata = photoUploadData.metadata,
                )
            if (response.isSuccessful) {
                Result.Success(response.code())
            } else {
                // 다양한 에러 코드에 맞게 처리
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request: ${response.message()}")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: HttpException) {
            Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }
    }

    suspend fun removeTagFromPhoto(
        photoId: String,
        tagId: String,
    ): Result<Unit> =
        try {
            val response = apiService.removeTagFromPhoto(photoId, tagId)

            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    404 -> Result.Error(response.code(), "Photo or tag not found")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun postTagsToPhoto(
        photoId: String,
        tagId: String,
    ): Result<Unit> =
        try {
            val requestBody = listOf(TagId(id = tagId))

            val response = apiService.postTagsToPhoto(photoId, requestBody)

            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    404 -> Result.Error(response.code(), "Photo or tag not found")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: HttpException) {
            Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun removeTag(tagId: String): Result<Unit> =
        try {
            val response = apiService.removeTag(tagId)

            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    404 -> Result.Error(response.code(), "Photo or tag not found")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }

    suspend fun renameTag(
        tagId: String,
        tagName: String,
    ): Result<TagId> =
        try {
            val request = TagName(name = tagName)
            val response = apiService.renameTag(tagId, request)

            if (response.isSuccessful) {
                response.body()?.let { TagId ->
                    Result.Success(TagId)
                } ?: Result.Error(response.code(), "Response body is null")
            } else {
                when (response.code()) {
                    401 -> Result.Unauthorized("Authentication failed")
                    400 -> Result.BadRequest("Bad request")
                    403 -> Result.Error(response.code(), "Tag is forbidden")
                    404 -> Result.Error(response.code(), "Tag not found")
                    else -> Result.Error(response.code(), "An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: HttpException) {
            Result.Error(e.code(), e.message())
        } catch (e: IOException) {
            Result.Exception(e)
        } catch (e: Exception) {
            Result.Exception(e)
        }
}
