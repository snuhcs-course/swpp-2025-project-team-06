package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import com.example.momentag.model.Photo
import com.example.momentag.repository.DraftTagRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * AddTagViewModel
 *
 * ViewModel for AddTagScreen
 * - Delegates state management to DraftTagRepository
 * - Provides convenient methods for tag creation workflow
 * - Each screen instance gets its own ViewModel, but they share the Repository
 */
class AddTagViewModel(
    private val draftTagRepository: DraftTagRepository,
) : ViewModel() {
    // Expose repository state as read-only flows
    val tagName: StateFlow<String> = draftTagRepository.tagName
    val selectedPhotos: StateFlow<List<Photo>> = draftTagRepository.selectedPhotos

    /**
     * Initialize the draft with data from another screen
     * (e.g., photos selected from search results)
     */
    fun initialize(
        initialTagName: String?,
        initialPhotos: List<Photo>,
    ) {
        draftTagRepository.initialize(initialTagName, initialPhotos)
    }

    /**
     * Update the tag name
     */
    fun updateTagName(name: String) {
        draftTagRepository.updateTagName(name)
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
     * Clear the draft when workflow is complete or cancelled
     */
    fun clearDraft() {
        draftTagRepository.clear()
    }

    /**
     * Check if there are unsaved changes
     */
    fun hasChanges(): Boolean = draftTagRepository.hasChanges()
}
