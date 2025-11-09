package com.example.momentag.viewmodel

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DatedPhotoGroup(
    val date: String,
    val photos: List<Photo>,
)

enum class TagSortOrder {
    NAME_ASC, // 이름 오름차순 (가나다)
    NAME_DESC, // 이름 내림차순
    CREATED_DESC, // 최근 추가 순 (기본값)
    COUNT_ASC, // 항목 적은 순
    COUNT_DESC, // 항목 많은 순
}

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

    private val _sortOrder = MutableStateFlow(TagSortOrder.CREATED_DESC)
    val sortOrder = _sortOrder.asStateFlow()

    private val _rawTagList = MutableStateFlow<List<TagItem>>(emptyList())
    val rawTagList = _rawTagList.asStateFlow()

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

    private var currentOffset = 0
    private val pageSize = 66
    private var hasMorePhotos = true

    fun togglePhoto(photo: Photo) {
        photoSelectionRepository.togglePhoto(photo)
    }

    fun resetSelection() {
        photoSelectionRepository.clear()
    }

    private fun formatISODate(isoDate: String): String =
        try {
            val datePart = isoDate.substring(0, 10) // [수정 후] (YYYY-MM-DD 가정)
            datePart.replace('-', '.')
        } catch (e: Exception) {
            "Unknown Date"
        }

    private val _groupedPhotos = MutableStateFlow<List<DatedPhotoGroup>>(emptyList())
    val groupedPhotos = _groupedPhotos.asStateFlow()

    private fun updateGroupedPhotos() {
        val grouped =
            _allPhotos.value
                .groupBy { formatISODate(it.createdAt) }
                .map { (date, photos) ->
                    DatedPhotoGroup(date, photos)
                }
        _groupedPhotos.value = grouped

        imageBrowserRepository.setGallery(_allPhotos.value)
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
                        updateGroupedPhotos()
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
                            updateGroupedPhotos()
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
                                createdAt = tag.createdAt,
                                updatedAt = tag.updatedAt,
                                photoCount = tag.photoCount,
                            )
                        }
                    _rawTagList.value = tagItems
                    sortAndPublishTags()
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

    fun setSortOrder(newOrder: TagSortOrder) {
        _sortOrder.value = newOrder
        sortAndPublishTags()
    }

    private fun sortAndPublishTags() {
        val currentList = _rawTagList.value

        fun parseDate(dateStr: String?): Date? {
            if (dateStr == null) return null

            val formatStrings =
                listOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss",
                )

            for (format in formatStrings) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    return sdf.parse(dateStr)
                } catch (e: Exception) {
                    continue
                }
            }
            return null
        }

        val sortedList =
            when (_sortOrder.value) {
                TagSortOrder.NAME_ASC -> currentList.sortedBy { it.tagName }
                TagSortOrder.NAME_DESC -> currentList.sortedByDescending { it.tagName }
                TagSortOrder.CREATED_DESC -> currentList.sortedByDescending { parseDate(it.createdAt) }
                TagSortOrder.COUNT_ASC -> currentList.sortedBy { it.photoCount }
                TagSortOrder.COUNT_DESC -> currentList.sortedByDescending { it.photoCount }
            }
        _homeLoadingState.value = HomeLoadingState.Success(tags = sortedList)
    }

    fun resetDeleteState() {
        _homeDeleteState.value = HomeDeleteState.Idle
    }
}
