package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.SearchRepository
import com.example.momentag.repository.TokenRepository
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
    private val photoSelectionRepository: PhotoSelectionRepository,
    private val localRepository: LocalRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
    private val tokenRepository: TokenRepository,
) : ViewModel() {
    private val _searchState = MutableStateFlow<SemanticSearchState>(SemanticSearchState.Idle)
    val searchState = _searchState.asStateFlow()

    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // TokenRepository의 로그인 상태 Flow 구독
    private val isLoggedInFlow = tokenRepository.isLoggedIn

    init {
        loadSearchHistory()

        viewModelScope.launch {
            isLoggedInFlow.collect { accessToken ->
                if (accessToken == null) {
                    clearHistoryAndReload()
                }
            }
        }
    }

    fun setSelectionMode(isOn: Boolean) {
        _isSelectionMode.value = isOn
    }

    fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = localRepository.getSearchHistory()
        }
    }

    private fun clearHistoryAndReload() {
        viewModelScope.launch {
            localRepository.clearSearchHistory()
            _searchHistory.value = emptyList()
        }
    }

    fun onSearchTextChanged(query: String) {
        _searchText.value = query
    }

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
            localRepository.addSearchHistory(query)
            loadSearchHistory()

            _searchState.value = SemanticSearchState.Loading
            _searchText.value = query

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

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            localRepository.removeSearchHistory(query)
            loadSearchHistory()
        }
    }

    fun togglePhoto(photo: Photo) {
        photoSelectionRepository.togglePhoto(photo)
    }

    fun resetSelection() {
        photoSelectionRepository.clear()
    }

    /**
     * Get photos ready for sharing
     * Returns list of content URIs to share via Android ShareSheet
     */
    fun getPhotosToShare() = selectedPhotos.value

    /**
     * 검색 상태 초기화
     */
    fun resetSearchState() {
        _searchState.value = SemanticSearchState.Idle
        imageBrowserRepository.clear()
    }
}
