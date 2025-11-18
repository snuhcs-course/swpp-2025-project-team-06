package com.example.momentag.model

sealed class MyTagsUiState {
    object Loading : MyTagsUiState()

    data class Success(
        val tags: List<TagCntData>,
    ) : MyTagsUiState()

    data class Error(
        val message: String,
    ) : MyTagsUiState()
}

sealed interface StoryState {
    object Idle : StoryState

    object Loading : StoryState

    data class Success(
        val stories: List<StoryModel>,
        val currentIndex: Int = 0,
        val hasMore: Boolean = true,
    ) : StoryState

    data class Error(
        val message: String,
    ) : StoryState

    data class NetworkError(
        val message: String,
    ) : StoryState
}

sealed class StoryTagSubmissionState {
    object Idle : StoryTagSubmissionState()

    object Loading : StoryTagSubmissionState()

    object Success : StoryTagSubmissionState()

    data class Error(
        val message: String,
    ) : StoryTagSubmissionState()
}
