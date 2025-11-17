package com.example.momentag.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Album
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocalViewModel(
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

    // 2. Public StateFlow (exposed state)
    val image = _image.asStateFlow()
    val albums = _albums.asStateFlow()
    val imagesInAlbum = _imagesInAlbum.asStateFlow()
    val selectedPhotosInAlbum = _selectedPhotosInAlbum.asStateFlow()
    val selectedAlbumIds = _selectedAlbumIds.asStateFlow()

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
}
