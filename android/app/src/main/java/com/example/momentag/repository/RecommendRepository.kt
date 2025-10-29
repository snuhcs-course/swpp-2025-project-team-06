package com.example.momentag.repository

import com.example.momentag.model.PhotoResponse
import com.example.momentag.network.ApiService
import java.io.IOException

class RecommendRepository(
    private val apiService: ApiService,
) {
    sealed class RecommendResult {
        data class Success(
            val photos: List<PhotoResponse>,
        ) : RecommendResult()

        data class Empty(
            val query: String,
        ) : RecommendResult()

        data class BadRequest(
            val message: String,
        ) : RecommendResult()

        data class Unauthorized(
            val message: String,
        ) : RecommendResult()

        data class NetworkError(
            val message: String,
        ) : RecommendResult()

        data class Error(
            val message: String,
        ) : RecommendResult()
    }

    // TODO sync with spec
    suspend fun recommendPhotos(tagId: String): RecommendResult =
        try {
            val response = apiService.recommendPhotos(tagId)

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
}
