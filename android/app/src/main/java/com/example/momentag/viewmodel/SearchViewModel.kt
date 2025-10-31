package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SearchViewModel
 *
 * 역할: Semantic Search UI 상태 관리
 * - SearchRepository에 비즈니스 로직 위임
 * - ImageBrowserRepository로 검색 결과 세션 관리
 * - UI 상태만 관리
 */
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val draftTagRepository: DraftTagRepository,
    private val localRepository: LocalRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
) : ViewModel() {
    private val _searchState = MutableStateFlow<SemanticSearchState>(SemanticSearchState.Idle)
    val searchState = _searchState.asStateFlow()

    val selectedPhotos: StateFlow<List<Photo>> = draftTagRepository.selectedPhotos

    /**
     * Semantic Search 수행 (GET 방식)
     * @param query 검색 쿼리
     * @param offset 오프셋
     */
    fun search(
        query: String,
        offset: Int = 0,
    ) {
        if (query.isBlank()) {
            _searchState.value = SemanticSearchState.Error("Query cannot be empty")
            return
        }

        viewModelScope.launch {
            _searchState.value = SemanticSearchState.Loading

            when (val result = searchRepository.semanticSearch(query, offset)) {
                is SearchRepository.SearchResult.Success -> {
                    val photos = localRepository.toPhotos(result.photos)
                    imageBrowserRepository.setSearchResults(photos, query)
                    _searchState.value =
                        SemanticSearchState.Success(
                            photos = photos,
                            query = query,
                        )
                }

                is SearchRepository.SearchResult.Empty -> {
                    _searchState.value = SemanticSearchState.Empty(query)
                    imageBrowserRepository.clear()
                }

                is SearchRepository.SearchResult.BadRequest -> {
                    _searchState.value = SemanticSearchState.Error(result.message)
                }

                is SearchRepository.SearchResult.Unauthorized -> {
                    _searchState.value = SemanticSearchState.Error("Please login again")
                }

                is SearchRepository.SearchResult.NetworkError -> {
                    _searchState.value = SemanticSearchState.NetworkError(result.message)
                }

                is SearchRepository.SearchResult.Error -> {
                    _searchState.value = SemanticSearchState.Error(result.message)
                }
            }
        }
    }

    fun togglePhoto(photo: Photo) {
        draftTagRepository.togglePhoto(photo)
    }

    fun resetSelection() {
        draftTagRepository.clear()
    }

    /**
     * 검색 상태 초기화
     */
    fun resetSearchState() {
        _searchState.value = SemanticSearchState.Idle
        imageBrowserRepository.clear()
    }
}
