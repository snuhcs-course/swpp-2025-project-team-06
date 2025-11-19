package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.TagCntData
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyTagsViewModel
    @Inject
    constructor(
        private val remoteRepository: RemoteRepository,
        private val photoSelectionRepository: PhotoSelectionRepository,
    ) : ViewModel() {
        // 1. state class 정의
        sealed class MyTagsUiState {
            object Loading : MyTagsUiState()

            data class Success(
                val tags: List<TagCntData>,
            ) : MyTagsUiState()

            data class Error(
                val error: MyTagsError,
            ) : MyTagsUiState()
        }

        sealed class TagActionState {
            object Idle : TagActionState()

            object Loading : TagActionState()

            data class Success(
                val message: String,
            ) : TagActionState()

            data class Error(
                val error: MyTagsError,
            ) : TagActionState()
        }

        sealed class SaveState {
            object Idle : SaveState()

            object Loading : SaveState()

            object Success : SaveState()

            data class Error(
                val error: MyTagsError,
            ) : SaveState()
        }

        sealed class MyTagsError {
            object NetworkError : MyTagsError()

            object Unauthorized : MyTagsError()

            object DeleteFailed : MyTagsError()

            object RenameFailed : MyTagsError()

            object UnknownError : MyTagsError()

            fun toMessageResId(): Int =
                when (this) {
                    is NetworkError -> com.example.momentag.R.string.error_message_network
                    is Unauthorized -> com.example.momentag.R.string.error_message_login
                    is DeleteFailed -> com.example.momentag.R.string.error_message_delete_tag
                    is RenameFailed -> com.example.momentag.R.string.error_message_rename_tag
                    is UnknownError -> com.example.momentag.R.string.error_message_unknown
                }
        }

        // 2. Private MutableStateFlow
        private val _uiState = MutableStateFlow<MyTagsUiState>(MyTagsUiState.Loading)
        private val _isEditMode = MutableStateFlow(false)
        private val _selectedTagsForBulkEdit = MutableStateFlow<Set<String>>(emptySet())
        private val _sortOrder = MutableStateFlow(TagSortOrder.CREATED_DESC)
        private val _tagActionState = MutableStateFlow<TagActionState>(TagActionState.Idle)
        private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)

        // 3. Private 변수
        private var allTags: List<TagCntData> = emptyList()

        // 4. Public StateFlow (exposed state)
        val uiState: StateFlow<MyTagsUiState> = _uiState.asStateFlow()
        val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()
        val selectedTagsForBulkEdit: StateFlow<Set<String>> = _selectedTagsForBulkEdit.asStateFlow()
        val sortOrder: StateFlow<TagSortOrder> = _sortOrder.asStateFlow()
        val tagActionState: StateFlow<TagActionState> = _tagActionState.asStateFlow()
        val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos
        val saveState = _saveState.asStateFlow()

        // 5. init 블록
        init {
            loadTags()
        }

        // 6. Public functions
        fun toggleEditMode() {
            _isEditMode.value = !_isEditMode.value
            // Clear selections when toggling edit mode
            _selectedTagsForBulkEdit.value = emptySet()
        }

        fun toggleTagSelection(tagId: String) {
            _selectedTagsForBulkEdit.value =
                if (_selectedTagsForBulkEdit.value.contains(tagId)) {
                    _selectedTagsForBulkEdit.value - tagId
                } else {
                    _selectedTagsForBulkEdit.value + tagId
                }
        }

        fun clearTagSelection() {
            _selectedTagsForBulkEdit.value = emptySet()
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
                        _uiState.value = MyTagsUiState.Error(MyTagsError.UnknownError)
                    }

                    is RemoteRepository.Result.Unauthorized -> {
                        println("MyTagsViewModel: Unauthorized: ${result.message}")
                        _uiState.value = MyTagsUiState.Error(MyTagsError.Unauthorized)
                    }

                    is RemoteRepository.Result.BadRequest -> {
                        println("MyTagsViewModel: BadRequest: ${result.message}")
                        _uiState.value = MyTagsUiState.Error(MyTagsError.UnknownError)
                    }

                    is RemoteRepository.Result.NetworkError -> {
                        println("MyTagsViewModel: NetworkError: ${result.message}")
                        _uiState.value = MyTagsUiState.Error(MyTagsError.NetworkError)
                    }

                    is RemoteRepository.Result.Exception -> {
                        println("MyTagsViewModel: Exception: ${result.e.message}")
                        _uiState.value = MyTagsUiState.Error(MyTagsError.UnknownError)
                    }
                }
            }
        }

        fun setSortOrder(order: TagSortOrder) {
            _sortOrder.value = order
            applySorting()
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
                        _tagActionState.value = TagActionState.Success("Deleted")
                        loadTags() // 성공 시에만 태그 목록 새로고침
                    }
                    is RemoteRepository.Result.Error -> {
                        _tagActionState.value = TagActionState.Error(MyTagsError.DeleteFailed)
                        println("MyTagsViewModel: Failed to delete tag - ${result.message}")
                    }
                    is RemoteRepository.Result.Unauthorized -> {
                        _tagActionState.value = TagActionState.Error(MyTagsError.Unauthorized)
                        println("MyTagsViewModel: Unauthorized - ${result.message}")
                    }
                    is RemoteRepository.Result.NetworkError -> {
                        _tagActionState.value = TagActionState.Error(MyTagsError.NetworkError)
                        println("MyTagsViewModel: Network error - ${result.message}")
                    }
                    else -> { // BadRequest, Exception 등
                        _tagActionState.value = TagActionState.Error(MyTagsError.DeleteFailed)
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
                        _tagActionState.value = TagActionState.Success("Updated")
                        loadTags() // 성공 시에만 태그 목록 새로고침
                    }
                    is RemoteRepository.Result.Error -> {
                        _tagActionState.value = TagActionState.Error(MyTagsError.RenameFailed)
                        println("MyTagsViewModel: Failed to rename tag - ${result.message}")
                    }
                    is RemoteRepository.Result.Unauthorized -> {
                        _tagActionState.value = TagActionState.Error(MyTagsError.Unauthorized)
                        println("MyTagsViewModel: Unauthorized - ${result.message}")
                    }
                    is RemoteRepository.Result.NetworkError -> {
                        _tagActionState.value = TagActionState.Error(MyTagsError.NetworkError)
                        println("MyTagsViewModel: Network error - ${result.message}")
                    }
                    else -> { // BadRequest, Exception 등
                        _tagActionState.value = TagActionState.Error(MyTagsError.RenameFailed)
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
                _saveState.value = SaveState.Error(MyTagsError.UnknownError)
                return
            }

            viewModelScope.launch {
                _saveState.value = SaveState.Loading

                var isAllSucceeded = true
                for (photo in selectedPhotos.value) {
                    when (val result = remoteRepository.postTagsToPhoto(photo.photoId, tagId)) {
                        is RemoteRepository.Result.Success -> {
                        }
                        else -> {
                            _saveState.value = SaveState.Error(getErrorType(result))
                            isAllSucceeded = false
                            break
                        }
                    }
                }

                if (isAllSucceeded) {
                    _saveState.value = SaveState.Success
                }
            }
        }

        private fun getErrorType(result: RemoteRepository.Result<*>): MyTagsError =
            when (result) {
                is RemoteRepository.Result.BadRequest -> MyTagsError.UnknownError
                is RemoteRepository.Result.Unauthorized -> MyTagsError.Unauthorized
                is RemoteRepository.Result.Error -> MyTagsError.UnknownError
                is RemoteRepository.Result.Exception -> MyTagsError.UnknownError
                is RemoteRepository.Result.Success -> MyTagsError.UnknownError
                is RemoteRepository.Result.NetworkError -> MyTagsError.NetworkError
            }

        fun clearActionState() {
            _tagActionState.value = TagActionState.Idle
        }

        // 7. Private functions (helpers)
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
    }
