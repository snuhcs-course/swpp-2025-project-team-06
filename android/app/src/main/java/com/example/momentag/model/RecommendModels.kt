package com.example.momentag.model

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
