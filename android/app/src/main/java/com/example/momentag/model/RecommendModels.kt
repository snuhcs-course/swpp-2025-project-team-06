package com.example.momentag.model

data class TagAlbum(
    val tagName : String,
    val photos : List<Photo>
)

sealed interface RecommendState {
    object Idle : RecommendState

    object Loading : RecommendState

    data class Success(
        val photos: List<Photo>,
    ) : RecommendState

    data class Error(
        val message: String,
    ) : RecommendState

    data class NetworkError(
        val message: String,
    ) : RecommendState
}