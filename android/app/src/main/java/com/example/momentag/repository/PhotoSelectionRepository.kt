package com.example.momentag.repository

import com.example.momentag.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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
 * Managed by Hilt as singleton
 */
@Singleton
class PhotoSelectionRepository
    @Inject
    constructor() {
        private val _tagName = MutableStateFlow("")
        val tagName: StateFlow<String> = _tagName.asStateFlow()

        // Selected photos as Map for O(1) operations (photoId -> Photo)
        private val _selectedPhotos = MutableStateFlow<Map<String, Photo>>(emptyMap())
        val selectedPhotos: StateFlow<Map<String, Photo>> = _selectedPhotos.asStateFlow()

        private val _existingTagId = MutableStateFlow<String?>(null)
        val existingTagId: StateFlow<String?> = _existingTagId.asStateFlow()

        /**
         * Initialize selection with existing data
         * Used when navigating from other screens with pre-selected photos
         */
        fun initialize(
            initialTagName: String?,
            initialPhotos: List<Photo>,
            existingTagId: String? = null,
        ) {
            _tagName.value = initialTagName ?: ""
            _selectedPhotos.value = initialPhotos.associateBy { it.photoId }
            _existingTagId.value = existingTagId
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
            if (!_selectedPhotos.value.containsKey(photo.photoId)) {
                _selectedPhotos.value = _selectedPhotos.value + (photo.photoId to photo)
            }
        }

        /**
         * Remove a photo from the selection
         */
        fun removePhoto(photo: Photo) {
            _selectedPhotos.value = _selectedPhotos.value - photo.photoId
        }

        /**
         * Toggle photo selection
         */
        fun togglePhoto(photo: Photo) {
            if (_selectedPhotos.value.containsKey(photo.photoId)) {
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
            _selectedPhotos.value = emptyMap()
            _existingTagId.value = null
        }

        /**
         * Check if there are unsaved changes
         */
        fun hasChanges(): Boolean = _tagName.value.isNotEmpty() || _selectedPhotos.value.isNotEmpty()
    }
