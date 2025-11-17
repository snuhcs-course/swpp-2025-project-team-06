package com.example.momentag.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SelectImageViewModel
 *
 * ViewModel for SelectImageScreen
 * - Delegates state management to PhotoSelectionRepository
 * - Provides convenient methods for photo selection
 * - Shares draft tag state with AddTagScreen through repository
 * - Now includes AI recommendation functionality
 */
class SelectImageViewModel(
    private val photoSelectionRepository: PhotoSelectionRepository,
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
    private val recommendRepository: RecommendRepository,
) : ViewModel() {
    // 1. Sealed class 정의
    sealed class AddPhotosState {
        object Idle : AddPhotosState()

        object Loading : AddPhotosState()

        object Success : AddPhotosState()

        data class Error(
            val message: String,
        ) : AddPhotosState()
    }

    // 2. Private MutableStateFlow
    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _recommendState = MutableStateFlow<RecommendState>(RecommendState.Idle)
    private val _isSelectionMode = MutableStateFlow(true)
    private val _recommendedPhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _addPhotosState = MutableStateFlow<AddPhotosState>(AddPhotosState.Idle)

    // 3. Private 변수
    private var currentOffset = 0
    private val pageSize = 100
    private var hasMorePages = true

    // 4. Public StateFlow (exposed state)
    val tagName: StateFlow<String> = photoSelectionRepository.tagName
    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos
    val existingTagId: StateFlow<String?> = photoSelectionRepository.existingTagId
    val allPhotos: StateFlow<List<Photo>> = _allPhotos.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    val recommendState: StateFlow<RecommendState> = _recommendState.asStateFlow()
    val isSelectionMode = _isSelectionMode.asStateFlow()
    val recommendedPhotos: StateFlow<List<Photo>> = _recommendedPhotos.asStateFlow()
    val addPhotosState: StateFlow<AddPhotosState> = _addPhotosState.asStateFlow()
    val lazyGridState = LazyGridState()

    // 5. Public functions
    fun getAllPhotos() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            currentOffset = 0
            hasMorePages = true

            try {
                when (val result = remoteRepository.getAllPhotos(limit = pageSize, offset = 0)) {
                    is RemoteRepository.Result.Success -> {
                        val serverPhotos = localRepository.toPhotos(result.data)

                        // Place already selected photos at the front
                        val selected = selectedPhotos.value
                        val selectedIds = selected.map { it.photoId }.toSet()

                        // Add only unselected photos from server
                        val unselected = serverPhotos.filter { it.photoId !in selectedIds }

                        // Selected photos + remaining photos from server
                        _allPhotos.value = selected + unselected
                        currentOffset = pageSize
                        hasMorePages = serverPhotos.size == pageSize
                    }
                    else -> {
                        _allPhotos.value = emptyList()
                        hasMorePages = false
                    }
                }
            } catch (e: Exception) {
                _allPhotos.value = emptyList()
                hasMorePages = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMorePhotos() {
        if (_isLoadingMore.value || !hasMorePages || _isLoading.value) return

        // Launch in IO dispatcher for better performance
        viewModelScope.launch {
            _isLoadingMore.value = true

            try {
                when (val result = remoteRepository.getAllPhotos(limit = pageSize, offset = currentOffset)) {
                    is RemoteRepository.Result.Success -> {
                        val newPhotos = localRepository.toPhotos(result.data)

                        if (newPhotos.isNotEmpty()) {
                            // Existing photo IDs (including selected photos)
                            val existingIds = _allPhotos.value.map { it.photoId }.toSet()

                            // Remove duplicates before adding
                            val uniqueNewPhotos = newPhotos.filter { it.photoId !in existingIds }

                            _allPhotos.value = _allPhotos.value + uniqueNewPhotos

                            currentOffset += pageSize
                            hasMorePages = newPhotos.size == pageSize

                            // Update ImageBrowserRepository as well (ensure prev/next buttons work)
                            imageBrowserRepository.setGallery(_allPhotos.value)
                        } else {
                            hasMorePages = false
                        }
                    }
                    else -> {
                        hasMorePages = false
                    }
                }
            } catch (e: Exception) {
                hasMorePages = false
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * Recommend photos based on currently selected photos
     */
    fun recommendPhoto() {
        // Launch in IO dispatcher for background execution (non-blocking)
        viewModelScope.launch {
            _recommendState.value = RecommendState.Loading

            when (
                val result =
                    recommendRepository.recommendPhotosFromPhotos(
                        selectedPhotos.value.map { it.photoId },
                    )
            ) {
                is RecommendRepository.RecommendResult.Success -> {
                    val photos = localRepository.toPhotos(result.data)
                    _recommendState.value = RecommendState.Success(photos = photos)

                    // Update recommended photos, filtering out already selected ones
                    updateRecommendedPhotos(photos)
                }
                is RecommendRepository.RecommendResult.Error -> {
                    _recommendState.value = RecommendState.Error("Something went wrong. Please try again")
                }
                is RecommendRepository.RecommendResult.Unauthorized -> {
                    _recommendState.value = RecommendState.Error("Your session expired. Please sign in again")
                }
                is RecommendRepository.RecommendResult.NetworkError -> {
                    _recommendState.value = RecommendState.NetworkError("Connection lost. Check your internet and try again")
                }
                is RecommendRepository.RecommendResult.BadRequest -> {
                    _recommendState.value = RecommendState.Error("Something went wrong. Please try again")
                }
            }
        }
    }

    /**
     * Update recommended photos, filtering out selected ones
     */
    private fun updateRecommendedPhotos(photos: List<Photo>) {
        val selectedPhotoIds = selectedPhotos.value.map { it.photoId }.toSet()
        _recommendedPhotos.value = photos.filter { it.photoId !in selectedPhotoIds }
    }

    /**
     * Add photo from recommendation and update recommended list
     * Also moves the photo to the front of allPhotos list
     */
    fun addPhotoFromRecommendation(photo: Photo) {
        addPhoto(photo)
        _recommendedPhotos.value = _recommendedPhotos.value.filter { it.photoId != photo.photoId }

        // Move photo to the front of allPhotos list
        val currentPhotos = _allPhotos.value.toMutableList()
        currentPhotos.removeAll { it.photoId == photo.photoId }
        _allPhotos.value = listOf(photo) + currentPhotos
    }

    /**
     * Handle photo click in main grid
     */
    fun handlePhotoClick(
        photo: Photo,
        isSelectionMode: Boolean,
        onNavigate: (Photo) -> Unit,
    ) {
        if (isSelectionMode) {
            togglePhoto(photo)
            // Remove from recommended if it was there
            _recommendedPhotos.value = _recommendedPhotos.value.filter { it.photoId != photo.photoId }
        } else {
            setGalleryBrowsingSession()
            onNavigate(photo)
        }
    }

    /**
     * Handle long click to enter selection mode
     */
    fun handleLongClick(photo: Photo) {
        if (!_isSelectionMode.value) {
            setSelectionMode(true)
            togglePhoto(photo)
        }
    }

    /**
     * Toggle photo selection
     */
    fun togglePhoto(photo: Photo) {
        photoSelectionRepository.togglePhoto(photo)
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

    fun clearDraft() {
        photoSelectionRepository.clear()
    }

    /**
     * Check if a photo is selected
     */
    fun isPhotoSelected(photo: Photo): Boolean = selectedPhotos.value.any { it.photoId == photo.photoId }

    /**
     * Set gallery browsing session for image navigation
     */
    fun setGalleryBrowsingSession() {
        imageBrowserRepository.setGallery(_allPhotos.value)
    }

    fun setSelectionMode(isSelection: Boolean) {
        _isSelectionMode.value = isSelection
    }

    /**
     * Check if we're adding photos to an existing tag
     */
    fun isAddingToExistingTag(): Boolean = existingTagId.value != null

    /**
     * Handle done button click - adds photos to existing tag if in that mode
     */
    fun handleDoneButtonClick() {
        val tagId = existingTagId.value ?: return
        val name = tagName.value
        addPhotosToExistingTag(tagId, name)
    }

    /**
     * Add selected photos to existing tag
     */
    private fun addPhotosToExistingTag(
        tagId: String,
        tagName: String,
    ) {
        viewModelScope.launch {
            _addPhotosState.value = AddPhotosState.Loading

            val photos = selectedPhotos.value

            for (photo in photos) {
                when (remoteRepository.postTagsToPhoto(photo.photoId, tagId)) {
                    is RemoteRepository.Result.Success -> {
                        // Success, continue to next photo
                    }
                    is RemoteRepository.Result.Error -> {
                        _addPhotosState.value = AddPhotosState.Error("Something went wrong. Please try again")
                        return@launch
                    }
                    is RemoteRepository.Result.Unauthorized -> {
                        _addPhotosState.value = AddPhotosState.Error("Your session expired. Please sign in again")
                        return@launch
                    }
                    is RemoteRepository.Result.BadRequest -> {
                        _addPhotosState.value = AddPhotosState.Error("Something went wrong. Please try again")
                        return@launch
                    }
                    is RemoteRepository.Result.NetworkError -> {
                        _addPhotosState.value = AddPhotosState.Error("Connection lost. Check your internet and try again")
                        return@launch
                    }
                    is RemoteRepository.Result.Exception -> {
                        _addPhotosState.value = AddPhotosState.Error("Connection lost. Check your internet and try again")
                        return@launch
                    }
                }
            }

            // Clear selection after success
            photoSelectionRepository.clear()

            _addPhotosState.value = AddPhotosState.Success
        }
    }
}
