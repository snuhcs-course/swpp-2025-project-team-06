package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
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
    private val recommendRepository: RecommendRepository,
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    // Expose repository state as read-only flows
    val tagName: StateFlow<String> = photoSelectionRepository.tagName
    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos
    private val _recommendState = MutableStateFlow<RecommendState>(RecommendState.Idle)
    val recommendState = _recommendState.asStateFlow()

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

    fun recommendPhoto() {
        viewModelScope.launch {
            _recommendState.value = RecommendState.Loading

            when (val result = recommendRepository.recommendPhotosFromPhotos(selectedPhotos.value.map { it.photoId })) {
                is RecommendRepository.RecommendResult.Success -> {
                    _recommendState.value =
                        RecommendState.Success(
                            photos = localRepository.toPhotos(result.data),
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
            }
        }
    }

    fun saveTagAndPhotos() {
        // Reset error state on retry
        if (_saveState.value is SaveState.Error) {
            _saveState.value = SaveState.Idle
        }

        if (tagName.value.isBlank() || selectedPhotos.value.isEmpty()) {
            _saveState.value = SaveState.Error("Tag cannot be empty and photos must be selected")
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
                    _saveState.value = SaveState.Error("Error creating tag")
                    return@launch
                }
            }

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
            is RemoteRepository.Result.BadRequest -> "Bad Request: ${result.message}"
            is RemoteRepository.Result.Unauthorized -> "Login error: ${result.message}"
            is RemoteRepository.Result.Error -> "Server Error (${result.code}): ${result.message}"
            is RemoteRepository.Result.Exception -> "Network Error: ${result.e.message}"
            is RemoteRepository.Result.Success -> "An unknown error occurred (Success was passed to error handler)"
            is RemoteRepository.Result.NetworkError -> "Network Error: ${result.message}"
        }
}
