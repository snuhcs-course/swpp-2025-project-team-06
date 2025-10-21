package com.example.momentag.model

/* TODO sync with spec*/
data class TagAlbum(
    val tagName : String,
    val photos : List<Long>
)

sealed interface RecommendState {
    object Idle : RecommendState

    object Loading : RecommendState

    data class Success(
        val photos: List<Long>,
    ) : RecommendState

    data class Error(
        val message: String,
    ) : RecommendState

    data class NetworkError(
        val message: String,
    ) : RecommendState
}