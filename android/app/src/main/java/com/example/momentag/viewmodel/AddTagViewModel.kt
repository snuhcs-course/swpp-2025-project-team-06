package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AddTagViewModel
 *
 * ViewModel for AddTagScreen
 * - Delegates state management to PhotoSelectionRepository
 * - Provides convenient methods for tag creation workflow
 * - Each screen instance gets its own ViewModel, but they share the Repository
 */
class AddTagViewModel(
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
            val message: String,
        ) : SaveState()
    }

    // 2. Private MutableStateFlow
    private val _existingTags = MutableStateFlow<List<String>>(emptyList())
    private val _isTagNameDuplicate = MutableStateFlow(false)
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)

    // 3. Public StateFlow (exposed state)
    val tagName: StateFlow<String> = photoSelectionRepository.tagName
    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos
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

        if (tagName.value.isBlank() || selectedPhotos.value.isEmpty()) {
            _saveState.value = SaveState.Error("Please enter a tag name and select at least one photo")
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
                else -> {
                    _saveState.value = SaveState.Error("Couldn't create tag. Please try again")
                    return@launch
                }
            }

            var isAllSucceeded = true
            for (photo in selectedPhotos.value) {
                when (val result = remoteRepository.postTagsToPhoto(photo.photoId, tagId)) {
                    is RemoteRepository.Result.Success -> {
                    }
                    else -> {
                        _saveState.value = SaveState.Error(getErrorMessage(result))
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

    private fun getErrorMessage(result: RemoteRepository.Result<*>): String =
        when (result) {
            is RemoteRepository.Result.BadRequest -> "Something went wrong. Please try again"
            is RemoteRepository.Result.Unauthorized -> "Your session expired. Please sign in again"
            is RemoteRepository.Result.Error -> "Something went wrong. Please try again"
            is RemoteRepository.Result.Exception -> "Connection lost. Check your internet and try again"
            is RemoteRepository.Result.Success -> "Something went wrong. Please try again"
            is RemoteRepository.Result.NetworkError -> "Connection lost. Check your internet and try again"
        }
}
