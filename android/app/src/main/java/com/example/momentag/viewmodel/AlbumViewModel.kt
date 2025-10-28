package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumViewModel(
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    sealed class AlbumLoadingState {
        object Idle : AlbumLoadingState()

        object Loading : AlbumLoadingState()

        data class Success(
            val photos: List<Photo>,
        ) : AlbumLoadingState()

        data class Error(
            val message: String,
        ) : AlbumLoadingState()
    }

    private val _albumLoadingState = MutableStateFlow<AlbumLoadingState>(AlbumLoadingState.Idle)
    val albumLoadingState = _albumLoadingState.asStateFlow()

    fun loadAlbum(
        tagId: String,
        tagName: String,
    ) {
        viewModelScope.launch {
            _albumLoadingState.value = AlbumLoadingState.Loading

            when (val result = remoteRepository.getPhotosByTag(tagId)) {
                is RemoteRepository.Result.Success -> {
                    val photos = localRepository.toPhotos(result.data)
                    _albumLoadingState.value =
                        AlbumLoadingState.Success(
                            photos,
                        )
                    imageBrowserRepository.setTagAlbum(photos, tagName)
                }
                is RemoteRepository.Result.Error -> {
                    _albumLoadingState.value = AlbumLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _albumLoadingState.value = AlbumLoadingState.Error("Please login again")
                }
                is RemoteRepository.Result.NetworkError -> {
                    _albumLoadingState.value = AlbumLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _albumLoadingState.value = AlbumLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _albumLoadingState.value = AlbumLoadingState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }
}
