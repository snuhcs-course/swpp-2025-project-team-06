package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import com.example.momentag.model.RecommendState
import com.example.momentag.model.TagAlbum
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecommendViewModel(
    private val recommendRepository: RecommendRepository,
    private val localRepository: LocalRepository,
) : ViewModel() {
    private val _recommendState = MutableStateFlow<RecommendState>(RecommendState.Idle)
    val recommendState = _recommendState.asStateFlow()

    // TODO sync with spec
    fun recommend(tagAlbum: TagAlbum) {
        if (tagAlbum.tagName.isBlank() && tagAlbum.photos.isEmpty()) {
            _recommendState.value = RecommendState.Error("Query cannot be empty")
            return
        }
    }

    /**
     * 검색 상태 초기화
     */
    fun resetSearchState() {
        _recommendState.value = RecommendState.Idle
    }
}
