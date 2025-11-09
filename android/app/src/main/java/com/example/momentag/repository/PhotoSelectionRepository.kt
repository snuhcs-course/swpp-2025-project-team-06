package com.example.momentag.repository

import com.example.momentag.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PhotoSelectionRepository
 *
 * Manages the state of photo selections across different features:
 * - Tag creation workflow (tag name + selected photos)
 * - Photo sharing (selected photos)
 * - Batch operations on photos
 *
 * This repository serves as the single source of truth for photo selection state,
 * allowing multiple screens to access and modify the same selection state
 * without tight coupling through shared ViewModels.
 *
 * Instance is shared via singleton ViewModelFactory
 */
class PhotoSelectionRepository {
    private val _tagName = MutableStateFlow("")
    val tagName: StateFlow<String> = _tagName.asStateFlow()

    private val _selectedPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val selectedPhotos: StateFlow<List<Photo>> = _selectedPhotos.asStateFlow()

    /**
     * Initialize selection with existing data
     * Used when navigating from other screens with pre-selected photos
     */
    fun initialize(
        initialTagName: String?,
        initialPhotos: List<Photo>,
    ) {
        _tagName.value = initialTagName ?: ""
        _selectedPhotos.value = initialPhotos
    }

    /**
     * Update the tag name (used in tag creation workflow)
     */
    fun updateTagName(name: String) {
        _tagName.value = name
    }

    /**
     * Add a photo to the selection
     */
    fun addPhoto(photo: Photo) {
        val current = _selectedPhotos.value
        if (!current.any { it.photoId == photo.photoId }) {
            _selectedPhotos.value = current + photo
        }
    }

    /**
     * Remove a photo from the selection
     */
    fun removePhoto(photo: Photo) {
        _selectedPhotos.value = _selectedPhotos.value.filter { it.photoId != photo.photoId }
    }

    /**
     * Toggle photo selection
     */
    fun togglePhoto(photo: Photo) {
        val current = _selectedPhotos.value
        if (current.any { it.photoId == photo.photoId }) {
            removePhoto(photo)
        } else {
            addPhoto(photo)
        }
    }

    /**
     * Clear all selection data
     * Should be called when workflow is completed or cancelled
     */
    fun clear() {
        _tagName.value = ""
        _selectedPhotos.value = emptyList()
    }

    /**
     * Check if there are unsaved changes
     */
    fun hasChanges(): Boolean = _tagName.value.isNotEmpty() || _selectedPhotos.value.isNotEmpty()
}
