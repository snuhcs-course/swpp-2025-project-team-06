package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.TagItem
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.SearchRepository
import com.example.momentag.repository.TagStateRepository
import com.example.momentag.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SearchViewModel
 *
 * Simplified ViewModel focused on search business logic only.
 * - Delegates search bar UI state to SearchBarState
 * - Uses TagStateRepository for tag data
 * - Manages semantic search, search history, and pagination
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
        private val photoSelectionRepository: PhotoSelectionRepository,
        private val localRepository: LocalRepository,
        private val imageBrowserRepository: ImageBrowserRepository,
        private val tokenRepository: TokenRepository,
        private val tagStateRepository: TagStateRepository,
    ) : ViewModel() {
        // 1. state class 정의
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

            data class Error(
                val error: SearchError,
            ) : SemanticSearchState()
        }

        sealed class SearchError {
            object NetworkError : SearchError()

            object Unauthorized : SearchError()

            object EmptyQuery : SearchError()

            object UnknownError : SearchError()
        }

        // 2. Private MutableStateFlow
        private val _searchState = MutableStateFlow<SemanticSearchState>(SemanticSearchState.Idle)
        private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
        private val _searchText = MutableStateFlow("")
        private val _isSelectionMode = MutableStateFlow(false)
        private val _scrollToIndex = MutableStateFlow<Int?>(null)

        // Pagination state
        private val _isLoadingMore = MutableStateFlow(false)
        private val _hasMore = MutableStateFlow(true)
        private var currentOffset = 0
        private var currentQuery = ""

        // 3. Public StateFlow (exposed state)
        val searchState = _searchState.asStateFlow()
        val selectedPhotos: StateFlow<Map<String, Photo>> = photoSelectionRepository.selectedPhotos
        val searchHistory = _searchHistory.asStateFlow()
        val searchText: StateFlow<String> = _searchText.asStateFlow()
        val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
        val scrollToIndex = _scrollToIndex.asStateFlow()

        val isLoadingMore = _isLoadingMore.asStateFlow()
        val hasMore = _hasMore.asStateFlow()

        // Expose tags from TagStateRepository
        val tags: StateFlow<List<TagItem>> = tagStateRepository.tags

        // 4. Private 변수
        private val isLoggedInFlow = tokenRepository.isLoggedIn

        // 5. init 블록
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
                _searchState.value = SemanticSearchState.Error(SearchError.EmptyQuery)
                return
            }

            // Reset pagination state for new search
            currentQuery = query
            currentOffset = 0
            _hasMore.value = true

            viewModelScope.launch {
                localRepository.addSearchHistory(query)
                loadSearchHistory()

                _searchState.value = SemanticSearchState.Loading
                _searchText.value = query

                when (val result = searchRepository.semanticSearch(query, offset)) {
                    is SearchRepository.SearchResult.Success -> {
                        val photos = localRepository.toPhotos(result.photos)
                        val distinctPhotos = photos.distinctBy(Photo::photoId)
                        imageBrowserRepository.setSearchResults(distinctPhotos, query)
                        _searchState.value =
                            SemanticSearchState.Success(
                                photos = distinctPhotos,
                                query = query,
                            )

                        // Update pagination state based on actual response
                        currentOffset += photos.size
                        // If we got 0 photos, there's nothing more to load
                        _hasMore.value = photos.isNotEmpty()
                    }

                    is SearchRepository.SearchResult.Empty -> {
                        _searchState.value = SemanticSearchState.Empty(query)
                        imageBrowserRepository.clear()
                        _hasMore.value = false
                    }

                    is SearchRepository.SearchResult.BadRequest -> {
                        _searchState.value = SemanticSearchState.Error(SearchError.UnknownError)
                    }

                    is SearchRepository.SearchResult.Unauthorized -> {
                        _searchState.value = SemanticSearchState.Error(SearchError.Unauthorized)
                    }

                    is SearchRepository.SearchResult.NetworkError -> {
                        _searchState.value = SemanticSearchState.Error(SearchError.NetworkError)
                    }

                    is SearchRepository.SearchResult.Error -> {
                        _searchState.value = SemanticSearchState.Error(SearchError.UnknownError)
                    }
                }
            }
        }

        /**
         * Load more search results (infinite scroll)
         */
        fun loadMore() {
            // Prevent multiple simultaneous loads
            if (_isLoadingMore.value || !_hasMore.value) return

            val currentState = _searchState.value
            if (currentState !is SemanticSearchState.Success) return

            viewModelScope.launch {
                _isLoadingMore.value = true

                when (val result = searchRepository.semanticSearch(currentQuery, currentOffset)) {
                    is SearchRepository.SearchResult.Success -> {
                        val newPhotos = localRepository.toPhotos(result.photos)
                        val distinctNewPhotos = newPhotos.distinctBy(Photo::photoId)

                        if (newPhotos.isNotEmpty()) {
                            // Append new photos to existing results
                            val existingPhotos = currentState.photos
                            val updatedPhotos =
                                (existingPhotos + distinctNewPhotos).distinctBy(Photo::photoId)

                            imageBrowserRepository.setSearchResults(updatedPhotos, currentQuery)
                            _searchState.value =
                                SemanticSearchState.Success(
                                    photos = updatedPhotos,
                                    query = currentQuery,
                                )

                            // Update pagination state based on actual response
                            currentOffset += newPhotos.size
                            // If we got results, there might be more
                            _hasMore.value = true
                        } else {
                            // No more results
                            _hasMore.value = false
                        }
                    }

                    is SearchRepository.SearchResult.Empty -> {
                        _hasMore.value = false
                    }

                    is SearchRepository.SearchResult.BadRequest,
                    is SearchRepository.SearchResult.Unauthorized,
                    is SearchRepository.SearchResult.NetworkError,
                    is SearchRepository.SearchResult.Error,
                    -> {
                        // Keep hasMore true to allow retry
                        _hasMore.value = true
                    }
                }

                _isLoadingMore.value = false
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
        fun getPhotosToShare() = selectedPhotos.value.values.toList()

        /**
         * 검색 상태 초기화
         */
        fun resetSearchState() {
            _searchState.value = SemanticSearchState.Idle
            imageBrowserRepository.clear()
        }

        /**
         * Set scroll position to restore after returning from ImageDetailScreen
         */
        fun setScrollToIndex(index: Int?) {
            _scrollToIndex.value = index
        }

        /**
         * Clear scroll position after restoration
         */
        fun clearScrollToIndex() {
            _scrollToIndex.value = null
        }

        /**
         * Restore scroll position from ImageBrowserRepository (when returning from ImageDetailScreen)
         */
        fun restoreScrollPosition() {
            val lastViewedIndex = imageBrowserRepository.getCurrentIndex()
            lastViewedIndex?.let { setScrollToIndex(it) }
        }
    }
