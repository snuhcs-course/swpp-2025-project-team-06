package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.HomeScreenUiState
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class PhotoViewModel(
    private val remoteRepository: RemoteRepository,
    private val localRepository: LocalRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState = _uiState.asStateFlow()

    fun uploadPhotos() {
        viewModelScope.launch {
            // set to loading state
            _uiState.update { it.copy(isLoading = true, userMessage = null, isUploadSuccess = false) }

            try {
                val photoUploadData = localRepository.getPhotoUploadRequest()
                val response = remoteRepository.uploadPhotos(photoUploadData)

                // update ui state
                val message = when (response.code()) {
                    202 -> { // Accepted
                        _uiState.update { it.copy(isUploadSuccess = true) }
                        "Success"
                    }
                    400 -> "Request form mismatch" // Bad Request
                    401 -> "The refresh token is expired" // Unauthorized
                    else -> "Unexpected error: ${response.code()}"
                }

                _uiState.update { it.copy(isLoading = false, userMessage = message) }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Network error") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Unknown error") }
            }
        }
    }

    // to show message and reset
    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
