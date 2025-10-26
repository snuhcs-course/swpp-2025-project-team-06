package com.example.momentag.model

// TODO : tag name 보내지 않기
data class TagAlbum(
    val tagName: String,
    val photos: List<String>,
)

sealed interface RecommendState {
    object Idle : RecommendState

    object Loading : RecommendState

    data class Success(
        val photos: List<String>,
    ) : RecommendState

    data class Error(
        val message: String,
    ) : RecommendState

    data class NetworkError(
        val message: String,
    ) : RecommendState
}
