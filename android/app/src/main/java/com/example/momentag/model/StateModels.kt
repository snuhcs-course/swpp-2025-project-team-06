package com.example.momentag.model

sealed interface ImageDetailTagState {
    // 공통 상태: 초기 상태, 에러 상태
    data object Idle : ImageDetailTagState

    data class Error(
        val message: String,
    ) : ImageDetailTagState

    // 데이터 상태: 기존 태그와 추천 태그 목록을 각각 관리
    data class Success(
        val existingTags: List<Tag> = emptyList(),
        val recommendedTags: List<String> = emptyList(),
        val isExistingLoading: Boolean = true, // 기존 태그 로딩 중 여부
        val isRecommendedLoading: Boolean = true, // 추천 태그 로딩 중 여부
    ) : ImageDetailTagState
}

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
