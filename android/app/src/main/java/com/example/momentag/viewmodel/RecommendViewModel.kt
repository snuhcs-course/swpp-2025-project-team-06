package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.RecommendState
import com.example.momentag.model.TagAlbum
import com.example.momentag.repository.RecommendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecommendViewModel(
    private val RecommendRepository: RecommendRepository,
) : ViewModel() {
    private val _recommendState = MutableStateFlow<RecommendState>(RecommendState.Idle)
    val recommendState = _recommendState.asStateFlow()

    // TODO sync with spec
    fun recommend(tagAlbum: TagAlbum) {
        if (tagAlbum.tagName.isBlank() && tagAlbum.photos.isEmpty()) {
            _recommendState.value = RecommendState.Error("Query cannot be empty")
            return
        }

        viewModelScope.launch {
            _recommendState.value = RecommendState.Loading

            when (val result = RecommendRepository.recommendPhotos(tagAlbum)) {
                is RecommendRepository.RecommendResult.Success -> {
                    _recommendState.value =
                        RecommendState.Success(
                            photos = result.photos,
                        )
                }
                is RecommendRepository.RecommendResult.Error -> {
                    _recommendState.value = RecommendState.Error(result.message)
                }

                is RecommendRepository.RecommendResult.Unauthorized -> {
                    _recommendState.value = RecommendState.Error("Please login again")
                }

                is RecommendRepository.RecommendResult.NetworkError -> {
                    _recommendState.value = RecommendState.NetworkError(result.message)
                }

                is RecommendRepository.RecommendResult.BadRequest -> {
                    _recommendState.value = RecommendState.Error(result.message)
                }

                is RecommendRepository.RecommendResult.Empty -> {
                    _recommendState.value = RecommendState.Error("Query cannot be empty")
                }
            }
        }
    }

    /**
     * 검색 상태 초기화
     */
    fun resetSearchState() {
        _recommendState.value = RecommendState.Idle
    }
}
