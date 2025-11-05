package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.TagItem
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
    private val draftTagRepository: DraftTagRepository,
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
    val selectedPhotos: StateFlow<List<Photo>> = draftTagRepository.selectedPhotos

    // Server photos for All Photos view
    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos = _allPhotos.asStateFlow()

    private val _isLoadingPhotos = MutableStateFlow(false)
    val isLoadingPhotos = _isLoadingPhotos.asStateFlow()

    fun togglePhoto(photo: Photo) {
        draftTagRepository.togglePhoto(photo)
    }

    fun resetSelection() {
        draftTagRepository.clear()
    }

    fun loadAllPhotos() {
        viewModelScope.launch {
            _isLoadingPhotos.value = true
            when (val result = remoteRepository.getAllPhotos()) {
                is RemoteRepository.Result.Success -> {
                    val serverPhotos = localRepository.toPhotos(result.data)
                    _allPhotos.value = serverPhotos
                }
                else -> {
                    _allPhotos.value = emptyList()
                }
            }
            _isLoadingPhotos.value = false
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

                    // 각 태그의 첫 번째 사진을 썸네일로 가져오기
                    val tagItems =
                        tags.map { tag ->
                            // 각 태그의 사진 목록을 가져와서 첫 번째 사진의 photoPathId를 사용
                            val photosResult = remoteRepository.getPhotosByTag(tag.tagId)
                            val coverImageId =
                                when (photosResult) {
                                    is RemoteRepository.Result.Success -> {
                                        photosResult.data.firstOrNull()?.photoPathId
                                    }
                                    else -> null
                                }

                            TagItem(tag.tagName, coverImageId, tag.tagId)
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
