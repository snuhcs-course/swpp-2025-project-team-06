package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.Photo
import com.example.momentag.model.TagCntData
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyTagsViewModel(
    private val remoteRepository: RemoteRepository,
    private val photoSelectionRepository: PhotoSelectionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MyTagsUiState>(MyTagsUiState.Loading)
    val uiState: StateFlow<MyTagsUiState> = _uiState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(TagSortOrder.CREATED_DESC)
    val sortOrder: StateFlow<TagSortOrder> = _sortOrder.asStateFlow()

    private var allTags: List<TagCntData> = emptyList()

    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos

    sealed class SaveState {
        object Idle : SaveState()

        object Loading : SaveState()

        object Success : SaveState()

        data class Error(
            val message: String,
        ) : SaveState()
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

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
            println("MyTagsViewModel: deleteTag($tagId)")
            when (val result = remoteRepository.removeTag(tagId)) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: Tag deleted successfully")
                    loadTags() // 태그 목록 새로고침
                }
                is RemoteRepository.Result.Error -> {
                    println("MyTagsViewModel: Failed to delete tag - ${result.message}")
                }
                else -> {
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
            println("MyTagsViewModel: renameTag($tagId, $newName)")
            when (val result = remoteRepository.renameTag(tagId, newName)) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: Tag renamed successfully")
                    loadTags() // 태그 목록 새로고침
                }
                is RemoteRepository.Result.Error -> {
                    println("MyTagsViewModel: Failed to rename tag - ${result.message}")
                }
                else -> {
                    println("MyTagsViewModel: Failed to rename tag")
                }
            }
        }
    }

    fun isSelectedPhotosEmpty(): Boolean = selectedPhotos.value.isEmpty()

    fun clearDraft() {
        photoSelectionRepository.clear()
    }

    fun savePhotosToExistingTag(tagId: String) {
        // Reset error state on retry
        if (_saveState.value is SaveState.Error) {
            _saveState.value = SaveState.Idle
        }

        if (selectedPhotos.value.isEmpty()) {
            _saveState.value = SaveState.Error("Tag cannot be empty and photos must be selected")
            return
        }

        viewModelScope.launch {
            _saveState.value = SaveState.Loading

            var allSucceeded = true
            for (photo in selectedPhotos.value) {
                when (val result = remoteRepository.postTagsToPhoto(photo.photoId, tagId)) {
                    is RemoteRepository.Result.Success -> {
                    }
                    else -> {
                        _saveState.value = SaveState.Error(getErrorMessage(result))
                        allSucceeded = false
                        break
                    }
                }
            }

            if (allSucceeded) {
                _saveState.value = SaveState.Success
            }
        }
    }

    private fun getErrorMessage(result: RemoteRepository.Result<*>): String =
        when (result) {
            is RemoteRepository.Result.BadRequest -> "Bad Request: ${result.message}"
            is RemoteRepository.Result.Unauthorized -> "Login error: ${result.message}"
            is RemoteRepository.Result.Error -> "Server Error (${result.code}): ${result.message}"
            is RemoteRepository.Result.Exception -> "Network Error: ${result.e.message}"
            is RemoteRepository.Result.Success -> "An unknown error occurred (Success was passed to error handler)"
            is RemoteRepository.Result.NetworkError -> "Network Error: ${result.message}"
        }
}
