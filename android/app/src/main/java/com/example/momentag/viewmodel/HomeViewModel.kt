package com.example.momentag.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.TagItem
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
    private val photoSelectionRepository: PhotoSelectionRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
) : ViewModel() {
    sealed class HomeLoadingState {
        object Idle : HomeLoadingState()

        object Loading : HomeLoadingState()

        data class Success(
            val tags: List<TagItem>,
        ) : HomeLoadingState()

        data class Error(
            val message: String,
        ) : HomeLoadingState()
    }

    sealed class HomeDeleteState {
        object Idle : HomeDeleteState()

        object Loading : HomeDeleteState()

        object Success : HomeDeleteState()

        data class Error(
            val message: String,
        ) : HomeDeleteState()
    }

    private val _homeLoadingState = MutableStateFlow<HomeLoadingState>(HomeLoadingState.Idle)
    val homeLoadingState = _homeLoadingState.asStateFlow()

    private val _homeDeleteState = MutableStateFlow<HomeDeleteState>(HomeDeleteState.Idle)
    val homeDeleteState = _homeDeleteState.asStateFlow()

    // Photo selection management (같은 패턴으로 SearchViewModel 처럼!)
    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos

    // Server photos for All Photos view with pagination
    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos = _allPhotos.asStateFlow()

    private val _isLoadingPhotos = MutableStateFlow(false)
    val isLoadingPhotos = _isLoadingPhotos.asStateFlow()

    private val _isLoadingMorePhotos = MutableStateFlow(false)
    val isLoadingMorePhotos = _isLoadingMorePhotos.asStateFlow()

    private val _shouldReturnToAllPhotos = MutableStateFlow(false)
    val shouldReturnToAllPhotos: StateFlow<Boolean> = _shouldReturnToAllPhotos.asStateFlow()

    fun setShouldReturnToAllPhotos(value: Boolean) {
        _shouldReturnToAllPhotos.value = value
    }

    val allPhotosListState = LazyGridState()

    private var currentOffset = 0
    private val pageSize = 66
    private var hasMorePhotos = true

    fun togglePhoto(photo: Photo) {
        photoSelectionRepository.togglePhoto(photo)
    }

    fun resetSelection() {
        photoSelectionRepository.clear()
    }

    /**
     * Get photos ready for sharing
     * Returns list of content URIs to share via Android ShareSheet
     */
    fun getPhotosToShare() = selectedPhotos.value

    // 처음 로드 (초기화)
    fun loadAllPhotos() {
        viewModelScope.launch {
            _isLoadingPhotos.value = true
            currentOffset = 0
            hasMorePhotos = true

            try {
                when (val result = remoteRepository.getAllPhotos(limit = pageSize, offset = 0)) {
                    is RemoteRepository.Result.Success -> {
                        val serverPhotos = localRepository.toPhotos(result.data)
                        _allPhotos.value = serverPhotos
                        currentOffset = pageSize // 다음 요청은 66부터 시작
                        hasMorePhotos = serverPhotos.size == pageSize // 정확히 pageSize개 받았으면 더 있을 가능성
                    }
                    else -> {
                        _allPhotos.value = emptyList()
                        hasMorePhotos = false
                    }
                }
            } catch (e: Exception) {
                _allPhotos.value = emptyList()
                hasMorePhotos = false
            } finally {
                _isLoadingPhotos.value = false
            }
        }
    }

    // 다음 페이지 로드 (무한 스크롤)
    fun loadMorePhotos() {
        if (_isLoadingMorePhotos.value || !hasMorePhotos) return

        viewModelScope.launch {
            _isLoadingMorePhotos.value = true

            try {
                when (val result = remoteRepository.getAllPhotos(limit = pageSize, offset = currentOffset)) {
                    is RemoteRepository.Result.Success -> {
                        val newPhotos = localRepository.toPhotos(result.data)
                        if (newPhotos.isNotEmpty()) {
                            _allPhotos.value = _allPhotos.value + newPhotos
                            currentOffset += pageSize // 다음 요청을 위해 pageSize만큼 증가
                            hasMorePhotos = newPhotos.size == pageSize // 정확히 pageSize개 받았으면 더 있을 가능성

                            // ImageBrowserRepository도 업데이트 (이전/다음 버튼 작동 보장)
                            imageBrowserRepository.setGallery(_allPhotos.value)
                        } else {
                            hasMorePhotos = false
                        }
                    }
                    else -> {
                        hasMorePhotos = false
                    }
                }
            } catch (e: Exception) {
                hasMorePhotos = false
            } finally {
                _isLoadingMorePhotos.value = false
            }
        }
    }

    fun setGalleryBrowsingSession() {
        imageBrowserRepository.setGallery(_allPhotos.value)
    }

    fun loadServerTags() {
        viewModelScope.launch {
            _homeLoadingState.value = HomeLoadingState.Loading

            when (val result = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    val tags = result.data

                    val tagItems =
                        tags.map { tag ->
                            TagItem(
                                tagName = tag.tagName,
                                coverImageId = tag.thumbnailPhotoPathId,
                                tagId = tag.tagId,
                            )
                        }

                    _homeLoadingState.value = HomeLoadingState.Success(tags = tagItems)
                }

                is RemoteRepository.Result.Error -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.NetworkError -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            _homeDeleteState.value = HomeDeleteState.Loading

            when (val result = remoteRepository.removeTag(tagId)) {
                is RemoteRepository.Result.Success -> {
                    _homeDeleteState.value = HomeDeleteState.Success
                }

                is RemoteRepository.Result.Error -> {
                    _homeDeleteState.value = HomeDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.Unauthorized -> {
                    _homeDeleteState.value = HomeDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.BadRequest -> {
                    _homeDeleteState.value = HomeDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.NetworkError -> {
                    _homeDeleteState.value = HomeDeleteState.Error(result.message)
                }

                is RemoteRepository.Result.Exception -> {
                    _homeDeleteState.value =
                        HomeDeleteState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun resetDeleteState() {
        _homeDeleteState.value = HomeDeleteState.Idle
    }
}
