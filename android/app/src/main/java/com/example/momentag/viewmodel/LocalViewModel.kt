package com.example.momentag.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Album
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalViewModel(
    private val localRepository: LocalRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
    private val albumUploadSuccessEvent: SharedFlow<Long>,
) : ViewModel() {
    private val _image = MutableStateFlow<List<Uri>>(emptyList())
    val image = _image.asStateFlow()

    fun getImages() {
        viewModelScope.launch {
            _image.value =
                withContext(Dispatchers.IO) {
                    localRepository.getImages()
                }
        }
    }

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums = _albums.asStateFlow()

    fun getAlbums() {
        viewModelScope.launch {
            _albums.value =
                withContext(Dispatchers.IO) {
                    localRepository.getAlbums()
                }
        }
    }

    private val _imagesInAlbum = MutableStateFlow<List<Photo>>(emptyList())
    val imagesInAlbum = _imagesInAlbum.asStateFlow()

    private val _selectedPhotosInAlbum = MutableStateFlow<Set<Photo>>(emptySet())
    val selectedPhotosInAlbum = _selectedPhotosInAlbum.asStateFlow()

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

    fun getImagesForAlbum(albumId: Long) {
        viewModelScope.launch {
            clearPhotoSelection()
            _imagesInAlbum.value =
                withContext(Dispatchers.IO) {
                    localRepository.getImagesForAlbum(albumId)
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

    private val _selectedAlbumIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedAlbumIds = _selectedAlbumIds.asStateFlow()

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
