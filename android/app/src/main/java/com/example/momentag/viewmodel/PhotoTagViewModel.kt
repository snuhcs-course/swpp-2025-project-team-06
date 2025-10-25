package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoTagViewModel(
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    private val _tagName = MutableStateFlow("")
    val tagName = _tagName.asStateFlow()

    private val _selectedPhotos = MutableStateFlow<List<Long>>(emptyList())
    val selectedPhotos = _selectedPhotos.asStateFlow()

    sealed class SaveState {
        object Idle : SaveState()
        object Loading : SaveState()
        object Success : SaveState()
        data class Error(val message: String) : SaveState()
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    fun setInitialData(
        initialTagName: String?,
        initialSelectedPhotos: List<Long>,
    ) {
        _tagName.value = initialTagName ?: ""
        _selectedPhotos.value = initialSelectedPhotos
    }

    fun updateTagName(newTagName: String) {
        _tagName.value = newTagName
    }

    fun addPhoto(photoId: Long) {
        _selectedPhotos.update { currentList -> currentList + photoId }
    }

    fun removePhoto(photoId: Long) {
        _selectedPhotos.update { currentList -> currentList - photoId }
    }

    fun saveTagAndPhotos() {
        if (_tagName.value.isBlank() || _selectedPhotos.value.isEmpty()) {
            _saveState.value = SaveState.Error("Tag cannot be empty and photos must be selected")
            return
        }

        viewModelScope.launch {
            _saveState.value = SaveState.Loading


            val tagResult = remoteRepository.postTags(_tagName.value)
            val tagId: Long

            when (tagResult) {
                is RemoteRepository.Result.Success -> {
                    tagId = tagResult.data
                }
                else -> {
                    _saveState.value = SaveState.Error("Error creating tag")
                    return@launch
                }
            }

            var allSucceeded = true
            for (photoId in _selectedPhotos.value) {
                when (val result = remoteRepository.postTagsToPhoto(photoId, tagId)) {
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

    private fun getErrorMessage(result: RemoteRepository.Result<*>): String {
        return when (result) {
            is RemoteRepository.Result.BadRequest -> "Bad Request: ${result.message}"
            is RemoteRepository.Result.Unauthorized -> "Login error: ${result.message}"
            is RemoteRepository.Result.Error -> "Server Error (${result.code}): ${result.message}"
            is RemoteRepository.Result.Exception -> "Network Error: ${result.e.message}"
            is RemoteRepository.Result.Success -> "An unknown error occurred (Success was passed to error handler)"
            is RemoteRepository.Result.NetworkError -> "Network Error: ${result.message}"
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
