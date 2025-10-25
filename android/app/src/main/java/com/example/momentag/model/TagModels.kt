package com.example.momentag.model

sealed interface TagLoadState {
    object Idle : TagLoadState

    object Loading : TagLoadState

    data class Success(
        val tagItems: List<TagItem>,
    ) : TagLoadState

    data class Error(
        val message: String,
    ) : TagLoadState

    data class NetworkError(
        val message: String,
    ) : TagLoadState
}

sealed interface ImageOfTagLoadState {
    object Idle : ImageOfTagLoadState

    object Loading : ImageOfTagLoadState

    data class Success(
        val photos: Photos,
    ) : ImageOfTagLoadState

    data class Error(
        val message: String,
    ) : ImageOfTagLoadState

    data class NetworkError(
        val message: String,
    ) : ImageOfTagLoadState
}

