package com.example.momentag.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Album
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalViewModel
    @Inject
    constructor(
        private val localRepository: LocalRepository,
        private val imageBrowserRepository: ImageBrowserRepository,
        private val albumUploadSuccessEvent: SharedFlow<Long>,
    ) : ViewModel() {
        // 1. Private MutableStateFlow
        private val _image = MutableStateFlow<List<Uri>>(emptyList())
        private val _albums = MutableStateFlow<List<Album>>(emptyList())
        private val _imagesInAlbum = MutableStateFlow<List<Photo>>(emptyList())
        private val _selectedPhotosInAlbum = MutableStateFlow<Set<Photo>>(emptySet())
        private val _selectedAlbumIds = MutableStateFlow<Set<Long>>(emptySet())
        private val _scrollToIndex = MutableStateFlow<Int?>(null)

        // Pagination state
        private var currentAlbumId: Long? = null
        private var currentAlbumName: String? = null
        private var currentOffset = 0
        private val pageSize = 66
        private var hasMorePhotos = true

        private val _isLoadingMorePhotos = MutableStateFlow(false)

        // 2. Public StateFlow (exposed state)
        val image = _image.asStateFlow()
        val albums = _albums.asStateFlow()
        val imagesInAlbum = _imagesInAlbum.asStateFlow()
        val selectedPhotosInAlbum = _selectedPhotosInAlbum.asStateFlow()
        val selectedAlbumIds = _selectedAlbumIds.asStateFlow()
        val scrollToIndex = _scrollToIndex.asStateFlow()
        val isLoadingMorePhotos = _isLoadingMorePhotos.asStateFlow()

        // 3. init 블록
        init {
            viewModelScope.launch {
                albumUploadSuccessEvent.collect { successfulAlbumId ->
                    if (successfulAlbumId == 0L) {
                        clearPhotoSelection()
                    } else {
                        _selectedAlbumIds.update { currentSet ->
                            currentSet - successfulAlbumId
                        }
                    }
                }
            }
        }

        // 4. Public functions
        fun togglePhotoSelection(photo: Photo) {
            _selectedPhotosInAlbum.update { currentSet ->
                if (currentSet.any { it.photoId == photo.photoId }) {
                    currentSet.filter { it.photoId != photo.photoId }.toSet()
                } else {
                    currentSet + photo
                }
            }
        }

        fun clearPhotoSelection() {
            _selectedPhotosInAlbum.value = emptySet()
        }

        fun getAlbums() {
            viewModelScope.launch {
                _albums.value = localRepository.getAlbums()
            }
        }

        fun getImagesForAlbum(albumId: Long) {
            viewModelScope.launch {
                clearPhotoSelection()
                _imagesInAlbum.value = localRepository.getImagesForAlbum(albumId)
            }
        }

        fun loadAlbumPhotos(
            albumId: Long,
            albumName: String,
        ) {
            viewModelScope.launch {
                clearPhotoSelection()
                currentAlbumId = albumId
                currentAlbumName = albumName
                currentOffset = 0
                hasMorePhotos = true

                // Load first page
                val firstPage =
                    localRepository.getImagesForAlbumPaginated(
                        albumId = albumId,
                        limit = pageSize,
                        offset = 0,
                    )

                _imagesInAlbum.value = firstPage
                currentOffset = pageSize
                hasMorePhotos = firstPage.size == pageSize

                // Update ImageBrowserRepository with initial photos
                if (firstPage.isNotEmpty()) {
                    imageBrowserRepository.setLocalAlbum(firstPage, albumName)
                }
            }
        }

        fun loadMorePhotos() {
            val albumId = currentAlbumId ?: return
            val albumName = currentAlbumName ?: return

            if (_isLoadingMorePhotos.value || !hasMorePhotos) return

            viewModelScope.launch {
                _isLoadingMorePhotos.value = true

                try {
                    val nextPage =
                        localRepository.getImagesForAlbumPaginated(
                            albumId = albumId,
                            limit = pageSize,
                            offset = currentOffset,
                        )

                    if (nextPage.isNotEmpty()) {
                        _imagesInAlbum.value = _imagesInAlbum.value + nextPage
                        currentOffset += pageSize
                        hasMorePhotos = nextPage.size == pageSize

                        // Critical: Update ImageBrowserRepository with expanded list
                        imageBrowserRepository.setLocalAlbum(_imagesInAlbum.value, albumName)
                    } else {
                        hasMorePhotos = false
                    }
                } finally {
                    _isLoadingMorePhotos.value = false
                }
            }
        }

        /**
         * Set tag album browsing session
         * Converts URIs to Photos and stores in ImageBrowserRepository
         */
        fun setTagAlbumBrowsingSession(
            uris: List<Uri>,
            tagName: String,
        ) {
            val photos =
                uris.map { uri ->
                    Photo(
                        photoId = uri.lastPathSegment ?: uri.toString(),
                        contentUri = uri,
                        createdAt = "",
                    )
                }
            imageBrowserRepository.setTagAlbum(photos, tagName)
        }

        /**
         * Set local album browsing session
         * Converts URIs to Photos and stores in ImageBrowserRepository
         */
        fun setLocalAlbumBrowsingSession(
            photos: List<Photo>,
            albumName: String,
        ) {
            imageBrowserRepository.setLocalAlbum(photos, albumName)
        }

        /**
         * Set gallery browsing session
         * Converts URIs to Photos and stores in ImageBrowserRepository
         */
        fun setGalleryBrowsingSession(uris: List<Uri>) {
            val photos =
                uris.map { uri ->
                    Photo(
                        photoId = uri.lastPathSegment ?: uri.toString(),
                        contentUri = uri,
                        createdAt = "",
                    )
                }
            imageBrowserRepository.setGallery(photos)
        }

        fun clearAlbumSelection() {
            _selectedAlbumIds.value = emptySet()
        }

        fun selectAllAlbums(albums: List<Album>) {
            _selectedAlbumIds.value = albums.map { it.albumId }.toSet()
        }

        fun toggleAlbumSelection(albumId: Long) {
            _selectedAlbumIds.update { currentSet ->
                if (albumId in currentSet) {
                    currentSet - albumId // 이미 있으면 제거 (선택 해제)
                } else {
                    currentSet + albumId // 없으면 추가 (선택)
                }
            }
        }

        /**
         * Set scroll position to restore after returning from ImageDetailScreen
         */
        fun setScrollToIndex(index: Int?) {
            _scrollToIndex.value = index
        }

        /**
         * Clear scroll position after restoration
         */
        fun clearScrollToIndex() {
            _scrollToIndex.value = null
        }

        /**
         * Restore scroll position from ImageBrowserRepository (when returning from ImageDetailScreen)
         */
        fun restoreScrollPosition() {
            val lastViewedIndex = imageBrowserRepository.getCurrentIndex()
            lastViewedIndex?.let { setScrollToIndex(it) }
        }
    }
