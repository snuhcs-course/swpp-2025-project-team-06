package com.example.momentag.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
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
 */
class SelectImageViewModel(
    private val photoSelectionRepository: PhotoSelectionRepository,
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
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

    private val _isSelectionMode = MutableStateFlow(true)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    val lazyGridState = LazyGridState()

    private var currentOffset = 0
    private val pageSize = 100
    private var hasMorePages = true

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

                        // 이미 선택된 사진들을 맨 앞에 배치
                        val selected = selectedPhotos.value
                        val selectedIds = selected.map { it.photoId }.toSet()

                        // 서버에서 가져온 사진 중 선택되지 않은 사진만 추가
                        val unselected = serverPhotos.filter { it.photoId !in selectedIds }

                        // 선택된 사진들 + 서버에서 가져온 나머지 사진들
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

        viewModelScope.launch {
            _isLoadingMore.value = true

            try {
                when (val result = remoteRepository.getAllPhotos(limit = pageSize, offset = currentOffset)) {
                    is RemoteRepository.Result.Success -> {
                        val newPhotos = localRepository.toPhotos(result.data)

                        if (newPhotos.isNotEmpty()) {
                            // 이미 리스트에 있는 사진 ID들 (선택된 사진 포함)
                            val existingIds = _allPhotos.value.map { it.photoId }.toSet()

                            // 중복 제거 후 추가
                            val uniqueNewPhotos = newPhotos.filter { it.photoId !in existingIds }
                            _allPhotos.value = _allPhotos.value + uniqueNewPhotos

                            currentOffset += pageSize
                            hasMorePages = newPhotos.size == pageSize

                            // ImageBrowserRepository도 업데이트 (이전/다음 버튼 작동 보장)
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
