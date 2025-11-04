package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.TagId
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.viewmodel.AddTagViewModel.SaveState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumViewModel(
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
    private val recommendRepository: RecommendRepository,
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

    sealed class RecommendLoadingState {
        object Idle : RecommendLoadingState()

        object Loading : RecommendLoadingState()

        data class Success(
            val photos: List<Photo>,
        ) : RecommendLoadingState()

        data class Error(
            val message: String,
        ) : RecommendLoadingState()
    }

    sealed class TagDeleteState {
        object Idle : TagDeleteState()

        object Loading : TagDeleteState()

        object Success : TagDeleteState()

        data class Error(
            val message: String,
        ) : TagDeleteState()
    }

    sealed class TagRenameState {
        object Idle : TagRenameState()

        object Loading : TagRenameState()

        object Success : TagRenameState()

        data class Error(
            val message: String,
        ) : TagRenameState()
    }

    sealed class TagAddState {
        object Idle : TagAddState()

        object Loading : TagAddState()

        object Success : TagAddState()

        data class Error(
            val message: String
        ) : TagAddState()
    }

    private val _albumLoadingState = MutableStateFlow<AlbumLoadingState>(AlbumLoadingState.Idle)
    val albumLoadingState = _albumLoadingState.asStateFlow()

    private val _recommendLoadingState = MutableStateFlow<RecommendLoadingState>(RecommendLoadingState.Idle)
    val recommendLoadingState = _recommendLoadingState.asStateFlow()

    private val _selectedRecommendPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val selectedRecommendPhotos = _selectedRecommendPhotos.asStateFlow()

    private val _tagDeleteState = MutableStateFlow<TagDeleteState>(TagDeleteState.Idle)
    val tagDeleteState = _tagDeleteState.asStateFlow()

    private val _tagRenameState = MutableStateFlow<TagRenameState>(TagRenameState.Idle)
    val tagRenameState = _tagRenameState.asStateFlow()

    private val _tagAddState = MutableStateFlow<TagAddState>(TagAddState.Idle)
    val tagAddState = _tagAddState.asStateFlow()

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

                    // Auto-load recommendations after album loads
                    loadRecommendations(tagId)
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

    fun loadRecommendations(tagId: String) {
        viewModelScope.launch {
            _recommendLoadingState.value = RecommendLoadingState.Loading

            when (val result = recommendRepository.recommendPhotosFromTag(tagId)) {
                is RecommendRepository.RecommendResult.Success -> {
                    val photos = localRepository.toPhotos(result.data)
                    _recommendLoadingState.value = RecommendLoadingState.Success(photos)
                }
                is RecommendRepository.RecommendResult.Error -> {
                    _recommendLoadingState.value = RecommendLoadingState.Error(result.message)
                }
                is RecommendRepository.RecommendResult.Unauthorized -> {
                    _recommendLoadingState.value = RecommendLoadingState.Error("Please login again")
                }
                is RecommendRepository.RecommendResult.NetworkError -> {
                    _recommendLoadingState.value = RecommendLoadingState.Error(result.message)
                }
                is RecommendRepository.RecommendResult.BadRequest -> {
                    _recommendLoadingState.value = RecommendLoadingState.Error(result.message)
                }
            }
        }
    }

    fun toggleRecommendPhoto(photo: Photo) {
        val currentSelection = _selectedRecommendPhotos.value.toMutableList()
        if (currentSelection.contains(photo)) {
            currentSelection.remove(photo)
        } else {
            currentSelection.add(photo)
        }
        _selectedRecommendPhotos.value = currentSelection
    }

    fun resetRecommendSelection() {
        _selectedRecommendPhotos.value = emptyList()
    }

    // same function with ImageDetailViewModel
    // but rewrite to make view & viewmodel 1-1 mapping
    fun deleteTagFromPhoto(
        photoId: String,
        tagId: String,
    ) {
        viewModelScope.launch {
            _tagDeleteState.value = TagDeleteState.Loading

            // If photoId is numeric (photo_path_id from local album), find the actual UUID
            val actualPhotoId =
                if (photoId.toLongOrNull() != null) {
                    findPhotoIdByPathId(photoId.toLong())
                } else {
                    photoId
                }

            if (actualPhotoId == null) {
                _tagDeleteState.value = TagDeleteState.Error("Photo not found in backend")
                return@launch
            }

            when (val result = remoteRepository.removeTagFromPhoto(actualPhotoId, tagId)) {
                is RemoteRepository.Result.Success -> {
                    _tagDeleteState.value = TagDeleteState.Success

                    // Update the album state locally instead of reloading
                    val currentAlbumState = _albumLoadingState.value
                    if (currentAlbumState is AlbumLoadingState.Success) {
                        val updatedPhotos = currentAlbumState.photos.filterNot { it.photoId == actualPhotoId }
                        _albumLoadingState.value = AlbumLoadingState.Success(updatedPhotos)
                    }
                }

                is RemoteRepository.Result.Error -> {
                    _tagDeleteState.value = TagDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.Unauthorized -> {
                    _tagDeleteState.value = TagDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.BadRequest -> {
                    _tagDeleteState.value = TagDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.NetworkError -> {
                    _tagDeleteState.value = TagDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.Exception -> {
                    _tagDeleteState.value =
                        TagDeleteState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun resetDeleteState() {
        _tagDeleteState.value = TagDeleteState.Idle
    }

    // also duplicated with ImageDetailViewModel
    private suspend fun findPhotoIdByPathId(photoPathId: Long): String? =
        try {
            val allPhotosResult = remoteRepository.getAllPhotos()
            when (allPhotosResult) {
                is RemoteRepository.Result.Success -> {
                    val photo = allPhotosResult.data.find { it.photoPathId == photoPathId }
                    photo?.photoId
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }

    fun renameTag(
        tagId: String,
        tagName: String,
    ) {
        viewModelScope.launch {
            _tagRenameState.value = TagRenameState.Loading

            val returnTagId: String

            when (val result = remoteRepository.renameTag(tagId, tagName)) {
                is RemoteRepository.Result.Success -> {
                    returnTagId = result.data.id
                    if (returnTagId != tagId) {
                        _tagRenameState.value = TagRenameState.Error("Rename failed: Returned tag id is different")
                    } else {
                        _tagRenameState.value = TagRenameState.Success
                    }
                }

                is RemoteRepository.Result.Error -> {
                    _tagRenameState.value = TagRenameState.Error(result.message)
                }

                is RemoteRepository.Result.Unauthorized -> {
                    _tagRenameState.value = TagRenameState.Error(result.message)
                }

                is RemoteRepository.Result.BadRequest -> {
                    _tagRenameState.value = TagRenameState.Error(result.message)
                }

                is RemoteRepository.Result.NetworkError -> {
                    _tagRenameState.value = TagRenameState.Error(result.message)
                }

                is RemoteRepository.Result.Exception -> {
                    _tagRenameState.value = TagRenameState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun resetRenameState() {
        _tagRenameState.value = TagRenameState.Idle
    }

    fun addRecommendedPhotosToTagAlbum(photos: List<Photo>, tagId: String) {
        viewModelScope.launch {
            _tagAddState.value = TagAddState.Loading

            val addedPhotos = mutableListOf<Photo>()

            for (photo in photos) {
                val actualPhotoId =
                    if (photo.photoId.toLongOrNull() != null) {
                        findPhotoIdByPathId(photo.photoId.toLong())
                    } else {
                        photo.photoId
                    }

                if (actualPhotoId == null) {
                    _tagAddState.value = TagAddState.Error("Convert Photo Id Error")
                    continue
                }

                when (val result = remoteRepository.postTagsToPhoto(actualPhotoId, tagId)) {
                    is RemoteRepository.Result.Success -> {
                        addedPhotos.add(photo)
                        (_albumLoadingState.value as? AlbumLoadingState.Success)?.let {
                            val updatedPhotos = (it.photos + photo).distinct()
                            _albumLoadingState.value = AlbumLoadingState.Success(updatedPhotos)
                        }
                    }

                    is RemoteRepository.Result.Error -> {
                        _tagAddState.value = TagAddState.Error(result.message)
                    }

                    is RemoteRepository.Result.Unauthorized -> {
                        _tagAddState.value = TagAddState.Error(result.message)
                    }

                    is RemoteRepository.Result.BadRequest -> {
                        _tagAddState.value = TagAddState.Error(result.message)
                    }

                    is RemoteRepository.Result.NetworkError -> {
                        _tagAddState.value = TagAddState.Error(result.message)
                    }

                    is RemoteRepository.Result.Exception -> {
                        _tagAddState.value = TagAddState.Error(result.e.message ?: "Unknown error")
                    }
                }
            }

            if (_tagAddState.value == TagAddState.Loading) {
                _tagAddState.value = TagAddState.Success

                _recommendLoadingState.value = RecommendLoadingState.Loading
                kotlinx.coroutines.delay(3000L) // to prevent racing
                loadRecommendations(tagId)
            }
        }
    }

    fun resetAddState() {
        _tagAddState.value = TagAddState.Idle
    }
}
