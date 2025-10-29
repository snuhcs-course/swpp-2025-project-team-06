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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalViewModel(
    private val localRepository: LocalRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
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

    private val _imagesInAlbum = MutableStateFlow<List<Uri>>(emptyList())
    val imagesInAlbum = _imagesInAlbum.asStateFlow()

    fun getImagesForAlbum(albumId: Long) {
        viewModelScope.launch {
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
                    photoId = "", // Local images don't have backend photoId
                    contentUri = uri,
                )
            }
        imageBrowserRepository.setTagAlbum(photos, tagName)
    }

    /**
     * Set local album browsing session
     * Converts URIs to Photos and stores in ImageBrowserRepository
     */
    fun setLocalAlbumBrowsingSession(
        uris: List<Uri>,
        albumName: String,
    ) {
        val photos =
            uris.map { uri ->
                Photo(
                    photoId = "", // Local images don't have backend photoId
                    contentUri = uri,
                )
            }
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
                    photoId = "", // Local images don't have backend photoId
                    contentUri = uri,
                )
            }
        imageBrowserRepository.setGallery(photos)
    }
}
