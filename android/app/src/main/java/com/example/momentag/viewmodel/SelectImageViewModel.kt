package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SelectImageViewModel
 *
 * ViewModel for SelectImageScreen
 * - Delegates state management to DraftTagRepository
 * - Provides convenient methods for photo selection
 * - Shares draft tag state with AddTagScreen through repository
 */
class SelectImageViewModel(
    private val draftTagRepository: DraftTagRepository,
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    // Expose repository state as read-only flows
    val tagName: StateFlow<String> = draftTagRepository.tagName
    val selectedPhotos: StateFlow<List<Photo>> = draftTagRepository.selectedPhotos

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos = _allPhotos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun getAllPhotos() {
        viewModelScope.launch {
            _isLoading.value = true
            // Only get photos from server (uploaded photos)
            when (val result = remoteRepository.getAllPhotos()) {
                is RemoteRepository.Result.Success -> {
                    val serverPhotos = localRepository.toPhotos(result.data)
                    _allPhotos.value = serverPhotos
                }
                else -> {
                    // If server fails, show empty list
                    _allPhotos.value = emptyList()
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Toggle photo selection
     */
    fun togglePhoto(photo: Photo) {
        draftTagRepository.togglePhoto(photo)
    }

    /**
     * Add a photo to the selection
     */
    fun addPhoto(photo: Photo) {
        draftTagRepository.addPhoto(photo)
    }

    /**
     * Remove a photo from the selection
     */
    fun removePhoto(photo: Photo) {
        draftTagRepository.removePhoto(photo)
    }

    /**
     * Check if a photo is selected
     */
    fun isPhotoSelected(photo: Photo): Boolean = selectedPhotos.value.any { it.photoId == photo.photoId }
}
