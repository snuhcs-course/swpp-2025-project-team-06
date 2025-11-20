package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        private val localRepository: LocalRepository,
        private val remoteRepository: RemoteRepository,
        private val recommendRepository: RecommendRepository,
        private val imageBrowserRepository: ImageBrowserRepository,
        private val photoSelectionRepository: PhotoSelectionRepository,
    ) : ViewModel() {
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

        // 1. state class 정의
        sealed class AlbumLoadingState {
            object Idle : AlbumLoadingState()

            object Loading : AlbumLoadingState()

            data class Success(
                val photos: List<Photo>,
            ) : AlbumLoadingState()

            data class Error(
                val error: AlbumError,
            ) : AlbumLoadingState()
        }

        sealed class RecommendLoadingState {
            object Idle : RecommendLoadingState()

            object Loading : RecommendLoadingState()

            data class Success(
                val photos: List<Photo>,
            ) : RecommendLoadingState()

            data class Error(
                val error: AlbumError,
            ) : RecommendLoadingState()
        }

        sealed class TagDeleteState {
            object Idle : TagDeleteState()

            object Loading : TagDeleteState()

            object Success : TagDeleteState()

            data class Error(
                val error: AlbumError,
            ) : TagDeleteState()
        }

        sealed class TagRenameState {
            object Idle : TagRenameState()

            object Loading : TagRenameState()

            object Success : TagRenameState()

            data class Error(
                val error: AlbumError,
            ) : TagRenameState()
        }

        sealed class TagAddState {
            object Idle : TagAddState()

            object Loading : TagAddState()

            object Success : TagAddState()

            data class Error(
                val error: AlbumError,
            ) : TagAddState()
        }

        sealed class AlbumError {
            object NetworkError : AlbumError()

            object Unauthorized : AlbumError()

            object NotFound : AlbumError()

            object UnknownError : AlbumError()
        }

        // 2. Private MutableStateFlow
        private val _albumLoadingState = MutableStateFlow<AlbumLoadingState>(AlbumLoadingState.Idle)
        private val _recommendLoadingState = MutableStateFlow<RecommendLoadingState>(RecommendLoadingState.Idle)
        private val _selectedRecommendPhotos = MutableStateFlow<List<Photo>>(emptyList())
        private val _selectedTagAlbumPhotos = MutableStateFlow<List<Photo>>(emptyList())
        private val _tagDeleteState = MutableStateFlow<TagDeleteState>(TagDeleteState.Idle)
        private val _tagRenameState = MutableStateFlow<TagRenameState>(TagRenameState.Idle)
        private val _tagAddState = MutableStateFlow<TagAddState>(TagAddState.Idle)

        // 3. Public StateFlow (exposed state)
        val albumLoadingState = _albumLoadingState.asStateFlow()
        val recommendLoadingState = _recommendLoadingState.asStateFlow()
        val selectedRecommendPhotos = _selectedRecommendPhotos.asStateFlow()
        val selectedTagAlbumPhotos = _selectedTagAlbumPhotos.asStateFlow()
        val tagDeleteState = _tagDeleteState.asStateFlow()
        val tagRenameState = _tagRenameState.asStateFlow()
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
                        _albumLoadingState.value = AlbumLoadingState.Error(AlbumError.UnknownError)
                    }
                    is RemoteRepository.Result.Unauthorized -> {
                        _albumLoadingState.value = AlbumLoadingState.Error(AlbumError.Unauthorized)
                    }
                    is RemoteRepository.Result.NetworkError -> {
                        _albumLoadingState.value = AlbumLoadingState.Error(AlbumError.NetworkError)
                    }
                    is RemoteRepository.Result.BadRequest -> {
                        _albumLoadingState.value = AlbumLoadingState.Error(AlbumError.UnknownError)
                    }
                    is RemoteRepository.Result.Exception -> {
                        _albumLoadingState.value = AlbumLoadingState.Error(AlbumError.UnknownError)
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
                        _recommendLoadingState.value = RecommendLoadingState.Error(AlbumError.UnknownError)
                    }
                    is RecommendRepository.RecommendResult.Unauthorized -> {
                        _recommendLoadingState.value = RecommendLoadingState.Error(AlbumError.Unauthorized)
                    }
                    is RecommendRepository.RecommendResult.NetworkError -> {
                        _recommendLoadingState.value = RecommendLoadingState.Error(AlbumError.NetworkError)
                    }
                    is RecommendRepository.RecommendResult.BadRequest -> {
                        _recommendLoadingState.value = RecommendLoadingState.Error(AlbumError.UnknownError)
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

        fun toggleTagAlbumPhoto(photo: Photo) {
            val currentSelection = _selectedTagAlbumPhotos.value.toMutableList()
            if (currentSelection.contains(photo)) {
                currentSelection.remove(photo)
            } else {
                currentSelection.add(photo)
            }
            _selectedTagAlbumPhotos.value = currentSelection
        }

        fun resetTagAlbumPhotoSelection() {
            _selectedTagAlbumPhotos.value = emptyList()
        }

        // logic duplicated with ImageDetailViewModel
        // but rewrite to make view & viewmodel 1-1 mapping
        fun deleteTagFromPhotos(
            photos: List<Photo>,
            tagId: String,
        ) {
            viewModelScope.launch {
                _tagDeleteState.value = TagDeleteState.Loading

                val removedPhotoIds = mutableListOf<String>()

                for (photo in photos) {
                    // If photoId is numeric (photo_path_id from local album), find the actual UUID
                    val actualPhotoId =
                        if (photo.photoId.toLongOrNull() != null) {
                            findPhotoIdByPathId(photo.photoId.toLong())
                        } else {
                            photo.photoId
                        }

                    if (actualPhotoId == null) {
                        _tagDeleteState.value = TagDeleteState.Error(AlbumError.NotFound)
                        return@launch // exit for one or more error
                    }

                    when (val result = remoteRepository.removeTagFromPhoto(actualPhotoId, tagId)) {
                        is RemoteRepository.Result.Success -> {
                            removedPhotoIds.add(actualPhotoId)
                        }

                        is RemoteRepository.Result.Error -> {
                            _tagDeleteState.value = TagDeleteState.Error(AlbumError.UnknownError)
                            return@launch
                        }

                        is RemoteRepository.Result.Unauthorized -> {
                            _tagDeleteState.value = TagDeleteState.Error(AlbumError.Unauthorized)
                            return@launch
                        }

                        is RemoteRepository.Result.BadRequest -> {
                            _tagDeleteState.value = TagDeleteState.Error(AlbumError.UnknownError)
                            return@launch
                        }

                        is RemoteRepository.Result.NetworkError -> {
                            _tagDeleteState.value = TagDeleteState.Error(AlbumError.NetworkError)
                            return@launch
                        }

                        is RemoteRepository.Result.Exception -> {
                            _tagDeleteState.value =
                                TagDeleteState.Error(AlbumError.UnknownError)
                            return@launch
                        }
                    }
                }

                _tagDeleteState.value = TagDeleteState.Success

                // Update the album state locally instead of reloading
                val currentAlbumState = _albumLoadingState.value
                if (currentAlbumState is AlbumLoadingState.Success) {
                    val updatedPhotos =
                        currentAlbumState.photos.filterNot {
                            removedPhotoIds.contains(it.photoId)
                        }
                    _albumLoadingState.value = AlbumLoadingState.Success(updatedPhotos)
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
                            _tagRenameState.value = TagRenameState.Error(AlbumError.UnknownError)
                        } else {
                            _tagRenameState.value = TagRenameState.Success
                        }
                    }

                    is RemoteRepository.Result.Error -> {
                        _tagRenameState.value = TagRenameState.Error(AlbumError.UnknownError)
                    }

                    is RemoteRepository.Result.Unauthorized -> {
                        _tagRenameState.value = TagRenameState.Error(AlbumError.Unauthorized)
                    }

                    is RemoteRepository.Result.BadRequest -> {
                        _tagRenameState.value = TagRenameState.Error(AlbumError.UnknownError)
                    }

                    is RemoteRepository.Result.NetworkError -> {
                        _tagRenameState.value = TagRenameState.Error(AlbumError.NetworkError)
                    }

                    is RemoteRepository.Result.Exception -> {
                        _tagRenameState.value = TagRenameState.Error(AlbumError.UnknownError)
                    }
                }
            }
        }

        fun resetRenameState() {
            _tagRenameState.value = TagRenameState.Idle
        }

        fun addRecommendedPhotosToTagAlbum(
            photos: List<Photo>,
            tagId: String,
            tagName: String,
        ) {
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
                        _tagAddState.value = TagAddState.Error(AlbumError.UnknownError)
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
                            _tagAddState.value = TagAddState.Error(AlbumError.UnknownError)
                        }

                        is RemoteRepository.Result.Unauthorized -> {
                            _tagAddState.value = TagAddState.Error(AlbumError.Unauthorized)
                        }

                        is RemoteRepository.Result.BadRequest -> {
                            _tagAddState.value = TagAddState.Error(AlbumError.UnknownError)
                        }

                        is RemoteRepository.Result.NetworkError -> {
                            _tagAddState.value = TagAddState.Error(AlbumError.NetworkError)
                        }

                        is RemoteRepository.Result.Exception -> {
                            _tagAddState.value = TagAddState.Error(AlbumError.UnknownError)
                        }
                    }
                }

                if (_tagAddState.value == TagAddState.Loading) {
                    _tagAddState.value = TagAddState.Success
                }

                loadAlbum(tagId, tagName)
            }
        }

        fun resetAddState() {
            _tagAddState.value = TagAddState.Idle
        }

        /**
         * Get photos ready for sharing
         * Returns list of content URIs to share via Android ShareSheet
         */
        fun getPhotosToShare() = selectedTagAlbumPhotos.value

        /**
         * Initialize photo selection for adding photos to existing tag
         */
        fun initializeAddPhotosFlow(
            tagId: String,
            tagName: String,
        ) {
            photoSelectionRepository.initialize(
                initialTagName = tagName,
                initialPhotos = emptyList(),
                existingTagId = tagId,
            )
        }
    }
