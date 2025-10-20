package com.example.momentag.repository

import com.example.momentag.model.Photo
import com.example.momentag.model.TagAlbum
import com.example.momentag.network.ApiService
import com.example.momentag.repository.SearchRepository.SearchResult

class RecommendRepository(
    private val apiService: ApiService,
) {
    sealed class RecommendResult {
        data class Success(
            val photos: List<Photo>,
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


    suspend fun recommendPhotos(tagAlbum: TagAlbum) = apiService.recommendPhotos(tagAlbum)

}