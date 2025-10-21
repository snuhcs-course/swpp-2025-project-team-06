package com.example.momentag.repository

import com.example.momentag.model.Photo
import com.example.momentag.model.TagAlbum
import com.example.momentag.network.ApiService
import com.example.momentag.repository.SearchRepository.SearchResult
import java.io.IOException

class RecommendRepository(
    private val apiService: ApiService,
) {
    sealed class RecommendResult {
        data class Success(
            val photos: List<Long>,
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

    suspend fun recommendPhotos(
        tagAlbum: TagAlbum
    ): RecommendResult {
        return try {
            val response = apiService.recommendPhotos(tagAlbum)

            if (response.isSuccessful) {
                val recommendResponse = response.body()!!
                val photos = recommendResponse.photos
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

}