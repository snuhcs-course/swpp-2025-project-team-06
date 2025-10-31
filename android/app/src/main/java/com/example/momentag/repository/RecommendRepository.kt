package com.example.momentag.repository

import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoToPhotoRequest
import com.example.momentag.model.Tag
import com.example.momentag.network.ApiService
import java.io.IOException

class RecommendRepository(
    private val apiService: ApiService,
) {
    sealed class RecommendResult<T> {
        data class Success<T>(
            val data: T,
        ) : RecommendResult<T>()

        data class BadRequest<T>(
            val message: String,
        ) : RecommendResult<T>()

        data class Unauthorized<T>(
            val message: String,
        ) : RecommendResult<T>()

        data class NetworkError<T>(
            val message: String,
        ) : RecommendResult<T>()

        data class Error<T>(
            val message: String,
        ) : RecommendResult<T>()
    }

    suspend fun recommendTagFromPhoto(photoId: String): RecommendResult<List<Tag>> =
        try {
            val response = apiService.recommendTagFromPhoto(photoId)

            if (response.isSuccessful) {
                val tags = response.body()!!
                RecommendResult.Success(tags)
            } else {
                when (response.code()) {
                    401 -> RecommendResult.Unauthorized("Authentication failed")
                    400 -> RecommendResult.BadRequest("Bad request")
                    else -> RecommendResult.Error("An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            RecommendResult.NetworkError("Network error: ${e.message}")
        } catch (e: Exception) {
            RecommendResult.Error("An unexpected error occurred: ${e.message}")
        }

    suspend fun recommendPhotosFromTag(tagId: String): RecommendResult<List<PhotoResponse>> =
        try {
            val response = apiService.recommendPhotosFromTag(tagId)

            if (response.isSuccessful) {
                val photos = response.body()!!
                RecommendResult.Success(photos)
            } else {
                when (response.code()) {
                    401 -> RecommendResult.Unauthorized("Authentication failed")
                    400 -> RecommendResult.BadRequest("Bad request")
                    else -> RecommendResult.Error("An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            RecommendResult.NetworkError("Network error: ${e.message}")
        } catch (e: Exception) {
            RecommendResult.Error("An unexpected error occurred: ${e.message}")
        }

    suspend fun recommendPhotosFromPhotos(photoIds: List<String>): RecommendResult<List<PhotoResponse>> =
        try {
            val response = apiService.recommendPhotosFromPhotos(PhotoToPhotoRequest(photoIds))
            if (response.isSuccessful) {
                val photos = response.body()!!
                RecommendResult.Success(photos)
            } else {
                when (response.code()) {
                    401 -> RecommendResult.Unauthorized("Authentication failed")
                    400 -> RecommendResult.BadRequest("Bad request")
                    else -> RecommendResult.Error("An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            RecommendResult.NetworkError("Network error: ${e.message}")
        } catch (e: Exception) {
            RecommendResult.Error("An unexpected error occurred: ${e.message}")
        }

    suspend fun getStories(size: Int): RecommendResult<List<PhotoResponse>> =
        try {
            val response = apiService.getStories(size)

            if (response.isSuccessful) {
                val resp = response.body()!!
                RecommendResult.Success(resp.recs)
            } else {
                when (response.code()) {
                    401 -> RecommendResult.Unauthorized("Authentication failed")
                    400 -> RecommendResult.BadRequest("Bad request")
                    else -> RecommendResult.Error("An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            RecommendResult.NetworkError("Network error: ${e.message}")
        } catch (e: Exception) {
            RecommendResult.Error("An unexpected error occurred: ${e.message}")
        }
}
