package com.example.momentag.model

/**
 * 검색 결과 아이템
 */
data class SearchResultItem(
    val query: String,
    val photo: Photo,
)

/**
 * 검색 UI 상태
 */
sealed class SearchUiState {
    object Idle : SearchUiState()

    object Loading : SearchUiState()

    data class Success(
        val results: List<SearchResultItem>,
        val query: String,
    ) : SearchUiState()

    data class Empty(
        val query: String,
    ) : SearchUiState()

    data class Error(
        val message: String,
    ) : SearchUiState()
}

/**
 * Semantic Search 상태
 */
sealed class SemanticSearchState {
    object Idle : SemanticSearchState()

    object Loading : SemanticSearchState()

    data class Success(
        val photos: List<Photo>,
        val query: String,
    ) : SemanticSearchState()

    data class Empty(
        val query: String,
    ) : SemanticSearchState()

    data class NetworkError(
        val message: String,
    ) : SemanticSearchState()

    data class Error(
        val message: String,
    ) : SemanticSearchState()
}
