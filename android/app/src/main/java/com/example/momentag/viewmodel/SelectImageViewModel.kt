package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import com.example.momentag.model.Photo
import com.example.momentag.repository.DraftTagRepository
import kotlinx.coroutines.flow.StateFlow

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
) : ViewModel() {
    // Expose repository state as read-only flows
    val tagName: StateFlow<String> = draftTagRepository.tagName
    val selectedPhotos: StateFlow<List<Photo>> = draftTagRepository.selectedPhotos

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
