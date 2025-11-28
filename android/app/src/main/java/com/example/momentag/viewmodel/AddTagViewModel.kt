package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AddTagViewModel
 *
 * ViewModel for AddTagScreen
 * - Delegates state management to PhotoSelectionRepository
 * - Provides convenient methods for tag creation workflow
 * - Each screen instance gets its own ViewModel, but they share the Repository
 */
@HiltViewModel
class AddTagViewModel
    @Inject
    constructor(
        private val photoSelectionRepository: PhotoSelectionRepository,
        private val localRepository: LocalRepository,
        private val remoteRepository: RemoteRepository,
    ) : ViewModel() {
        // 1. state 클래스 및 sealed class 정의
        sealed class SaveState {
            object Idle : SaveState()

            object Loading : SaveState()

            object Success : SaveState()

            data class Error(
                val error: AddTagError,
            ) : SaveState()
        }

        sealed class AddTagError {
            object NetworkError : AddTagError()

            object Unauthorized : AddTagError()

            object EmptyName : AddTagError()

            object NoPhotos : AddTagError()

            object UnknownError : AddTagError()
        }

        // 2. Private MutableStateFlow
        private val _existingTags = MutableStateFlow<List<String>>(emptyList())
        private val _isTagNameDuplicate = MutableStateFlow(false)
        private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)

        // 3. Public StateFlow (exposed state)
        val tagName: StateFlow<String> = photoSelectionRepository.tagName
        val selectedPhotos: StateFlow<Map<String, Photo>> = photoSelectionRepository.selectedPhotos
        val existingTags = _existingTags.asStateFlow()
        val isTagNameDuplicate = _isTagNameDuplicate.asStateFlow()
        val saveState = _saveState.asStateFlow()

        // 4. init 블록
        init {
            // Load existing tags
            viewModelScope.launch {
                when (val result = remoteRepository.getAllTags()) {
                    is RemoteRepository.Result.Success -> {
                        _existingTags.value = result.data.map { it.tagName }
                    }
                    else -> {
                        // Ignore errors for now
                    }
                }
            }

            // Check for duplicate tag name
            viewModelScope.launch {
                tagName.collect { name ->
                    _isTagNameDuplicate.value = name.isNotBlank() && _existingTags.value.contains(name)
                }
            }
        }

        /**
         * Initialize the draft with data from another screen
         * (e.g., photos selected from search results)
         */
        fun initialize(
            initialTagName: String?,
            initialPhotos: List<Photo>,
        ) {
            photoSelectionRepository.initialize(initialTagName, initialPhotos)
        }

        /**
         * Update the tag name
         */
        fun updateTagName(name: String) {
            photoSelectionRepository.updateTagName(name)
        }

        /**
         * Add a photo to the selection
         */
        fun addPhoto(photo: Photo) {
            photoSelectionRepository.addPhoto(photo)
        }

        /**
         * Remove a photo from the selection
         */
        fun removePhoto(photo: Photo) {
            photoSelectionRepository.removePhoto(photo)
        }

        /**
         * Clear the draft when workflow is complete or cancelled
         */
        fun clearDraft() {
            photoSelectionRepository.clear()
        }

        fun saveTagAndPhotos() {
            // Reset error state on retry
            if (_saveState.value is SaveState.Error) {
                _saveState.value = SaveState.Idle
            }

            if (tagName.value.isBlank()) {
                _saveState.value = SaveState.Error(AddTagError.EmptyName)
                return
            }

            if (selectedPhotos.value.isEmpty()) {
                _saveState.value = SaveState.Error(AddTagError.NoPhotos)
                return
            }

            viewModelScope.launch {
                _saveState.value = SaveState.Loading

                val tagResult = remoteRepository.postTags(tagName.value)
                val tagId: String

                when (tagResult) {
                    is RemoteRepository.Result.Success -> {
                        tagId = tagResult.data.id
                    }
                    is RemoteRepository.Result.Unauthorized -> {
                        _saveState.value = SaveState.Error(AddTagError.Unauthorized)
                        return@launch
                    }
                    is RemoteRepository.Result.NetworkError -> {
                        _saveState.value = SaveState.Error(AddTagError.NetworkError)
                        return@launch
                    }
                    is RemoteRepository.Result.Exception -> {
                        _saveState.value = SaveState.Error(AddTagError.NetworkError)
                        return@launch
                    }
                    else -> {
                        _saveState.value = SaveState.Error(AddTagError.UnknownError)
                        return@launch
                    }
                }

                var isAllSucceeded = true
                for (photo in selectedPhotos.value.values) {
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

        /**
         * Check if there are unsaved changes
         */
        fun hasChanges(): Boolean = photoSelectionRepository.hasChanges()

        /**
         * Reset save state (e.g., after showing error to user)
         */
        fun resetSaveState() {
            _saveState.value = SaveState.Idle
        }

        private fun getErrorType(result: RemoteRepository.Result<*>): AddTagError =
            when (result) {
                is RemoteRepository.Result.BadRequest -> AddTagError.UnknownError
                is RemoteRepository.Result.Unauthorized -> AddTagError.Unauthorized
                is RemoteRepository.Result.Error -> AddTagError.UnknownError
                is RemoteRepository.Result.Exception -> AddTagError.NetworkError
                is RemoteRepository.Result.Success -> AddTagError.UnknownError
                is RemoteRepository.Result.NetworkError -> AddTagError.NetworkError
            }
    }
