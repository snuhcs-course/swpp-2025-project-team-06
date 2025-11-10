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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Expose repository state as read-only flows
    val tagName: StateFlow<String> = photoSelectionRepository.tagName
    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos: StateFlow<List<Photo>> = _allPhotos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _recommendState = MutableStateFlow<RecommendState>(RecommendState.Idle)
    val recommendState: StateFlow<RecommendState> = _recommendState.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(true)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    private val _recommendedPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val recommendedPhotos: StateFlow<List<Photo>> = _recommendedPhotos.asStateFlow()

    val lazyGridState = LazyGridState()

    private var currentOffset = 0
    private val pageSize = 100
    private var hasMorePages = true

    fun getAllPhotos() {
        if (_isLoading.value) return

        // Launch in IO dispatcher for better performance
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoading.value = true
            }
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
                        withContext(Dispatchers.Main) {
                            _allPhotos.value = selected + unselected
                        }
                        currentOffset = pageSize
                        hasMorePages = serverPhotos.size == pageSize
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            _allPhotos.value = emptyList()
                        }
                        hasMorePages = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _allPhotos.value = emptyList()
                }
                hasMorePages = false
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadMorePhotos() {
        if (_isLoadingMore.value || !hasMorePages || _isLoading.value) return

        // Launch in IO dispatcher for better performance
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isLoadingMore.value = true
            }

            try {
                when (val result = remoteRepository.getAllPhotos(limit = pageSize, offset = currentOffset)) {
                    is RemoteRepository.Result.Success -> {
                        val newPhotos = localRepository.toPhotos(result.data)

                        if (newPhotos.isNotEmpty()) {
                            // Existing photo IDs (including selected photos)
                            val existingIds = _allPhotos.value.map { it.photoId }.toSet()

                            // Remove duplicates before adding
                            val uniqueNewPhotos = newPhotos.filter { it.photoId !in existingIds }

                            withContext(Dispatchers.Main) {
                                _allPhotos.value = _allPhotos.value + uniqueNewPhotos
                            }

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
                withContext(Dispatchers.Main) {
                    _isLoadingMore.value = false
                }
            }
        }
    }

    /**
     * Recommend photos based on currently selected photos
     */
    fun recommendPhoto() {
        // Launch in IO dispatcher for background execution (non-blocking)
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _recommendState.value = RecommendState.Loading
            }

            when (
                val result =
                    recommendRepository.recommendPhotosFromPhotos(
                        selectedPhotos.value.map { it.photoId },
                    )
            ) {
                is RecommendRepository.RecommendResult.Success -> {
                    val photos = localRepository.toPhotos(result.data)
                    withContext(Dispatchers.Main) {
                        _recommendState.value = RecommendState.Success(photos = photos)
                    }

                    // Update recommended photos, filtering out already selected ones
                    updateRecommendedPhotos(photos)
                }
                is RecommendRepository.RecommendResult.Error -> {
                    withContext(Dispatchers.Main) {
                        _recommendState.value = RecommendState.Error(result.message)
                    }
                }
                is RecommendRepository.RecommendResult.Unauthorized -> {
                    withContext(Dispatchers.Main) {
                        _recommendState.value = RecommendState.Error("Please login again")
                    }
                }
                is RecommendRepository.RecommendResult.NetworkError -> {
                    withContext(Dispatchers.Main) {
                        _recommendState.value = RecommendState.NetworkError(result.message)
                    }
                }
                is RecommendRepository.RecommendResult.BadRequest -> {
                    withContext(Dispatchers.Main) {
                        _recommendState.value = RecommendState.Error(result.message)
                    }
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
}
