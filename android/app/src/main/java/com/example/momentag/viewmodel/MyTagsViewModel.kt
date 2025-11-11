package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.TagCntData
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TagActionState {
    object Idle : TagActionState
    object Loading : TagActionState
    data class Success(val message: String) : TagActionState
    data class Error(val message: String) : TagActionState
}

class MyTagsViewModel(
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MyTagsUiState>(MyTagsUiState.Loading)
    val uiState: StateFlow<MyTagsUiState> = _uiState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(TagSortOrder.CREATED_DESC)
    val sortOrder: StateFlow<TagSortOrder> = _sortOrder.asStateFlow()

    private val _tagActionState = MutableStateFlow<TagActionState>(TagActionState.Idle)
    val tagActionState: StateFlow<TagActionState> = _tagActionState.asStateFlow()

    private var allTags: List<TagCntData> = emptyList()

    init {
        loadTags()
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun loadTags() {
        viewModelScope.launch {
            _uiState.value = MyTagsUiState.Loading
            println("MyTagsViewModel: loadTags() started")

            when (val result = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: getAllTags() Success - ${result.data.size} tags")

                    allTags =
                        result.data.map { tagResponse ->
                            TagCntData(
                                tagId = tagResponse.tagId,
                                tagName = tagResponse.tagName,
                                count = tagResponse.photoCount ?: 0,
                            )
                        }

                    println("MyTagsViewModel: Final tags count = ${allTags.size}")
                    applySorting()
                }

                is RemoteRepository.Result.Error -> {
                    println("MyTagsViewModel: Error ${result.code}: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Error ${result.code}: ${result.message}")
                }

                is RemoteRepository.Result.Unauthorized -> {
                    println("MyTagsViewModel: Unauthorized: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Unauthorized: ${result.message}")
                }

                is RemoteRepository.Result.BadRequest -> {
                    println("MyTagsViewModel: BadRequest: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Bad Request: ${result.message}")
                }

                is RemoteRepository.Result.NetworkError -> {
                    println("MyTagsViewModel: NetworkError: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Network Error: ${result.message}")
                }

                is RemoteRepository.Result.Exception -> {
                    println("MyTagsViewModel: Exception: ${result.e.message}")
                    _uiState.value = MyTagsUiState.Error("Exception: ${result.e.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun setSortOrder(order: TagSortOrder) {
        _sortOrder.value = order
        applySorting()
    }

    private fun applySorting() {
        val sortedTags =
            when (_sortOrder.value) {
                TagSortOrder.NAME_ASC -> allTags.sortedBy { it.tagName }
                TagSortOrder.NAME_DESC -> allTags.sortedByDescending { it.tagName }
                TagSortOrder.CREATED_DESC -> allTags // 서버에서 최근순으로 옴
                TagSortOrder.COUNT_DESC -> allTags.sortedByDescending { it.count }
                TagSortOrder.COUNT_ASC -> allTags.sortedBy { it.count }
            }
        _uiState.value = MyTagsUiState.Success(sortedTags)
    }

    fun refreshTags() {
        loadTags()
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            _tagActionState.value = TagActionState.Loading
            println("MyTagsViewModel: deleteTag($tagId)")
            when (val result = remoteRepository.removeTag(tagId)) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: Tag deleted successfully")
                    _tagActionState.value = TagActionState.Success("삭제되었습니다")
                    loadTags() // 성공 시에만 태그 목록 새로고침
                }
                is RemoteRepository.Result.Error -> {
                    _tagActionState.value = TagActionState.Error(result.message)
                    println("MyTagsViewModel: Failed to delete tag - ${result.message}")
                }
                else -> { // NetworkError, Exception 등
                    val errorMsg = when (result) {
                        is RemoteRepository.Result.NetworkError -> result.message
                        is RemoteRepository.Result.Exception -> result.e.message ?: "Unknown error"
                        else -> "Failed to delete tag"
                    }
                    _tagActionState.value = TagActionState.Error(errorMsg)
                    println("MyTagsViewModel: Failed to delete tag")
                }
            }
        }
    }

    fun renameTag(
        tagId: String,
        newName: String,
    ) {
        viewModelScope.launch {
            _tagActionState.value = TagActionState.Loading
            println("MyTagsViewModel: renameTag($tagId, $newName)")
            when (val result = remoteRepository.renameTag(tagId, newName)) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: Tag renamed successfully")
                    _tagActionState.value = TagActionState.Success("수정되었습니다")
                    loadTags() // 성공 시에만 태그 목록 새로고침
                }
                is RemoteRepository.Result.Error -> {
                    _tagActionState.value = TagActionState.Error(result.message)
                    println("MyTagsViewModel: Failed to rename tag - ${result.message}")
                }
                else -> { // NetworkError, Exception 등
                    val errorMsg = when (result) {
                        is RemoteRepository.Result.NetworkError -> result.message
                        is RemoteRepository.Result.Exception -> result.e.message ?: "Unknown error"
                        else -> "Failed to rename tag"
                    }
                    _tagActionState.value = TagActionState.Error(errorMsg)
                    println("MyTagsViewModel: Failed to rename tag")
                }
            }
        }
    }

    fun clearActionState() {
        _tagActionState.value = TagActionState.Idle
    }
}
