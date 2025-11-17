package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.TagItem
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
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
    private val recommendRepository: RecommendRepository,
    private val photoSelectionRepository: PhotoSelectionRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
) : ViewModel() {
    // 1. Companion object
    companion object {
        @Volatile
        private var hasGeneratedStoriesThisSession = false

        fun resetStoriesGeneratedFlag() {
            hasGeneratedStoriesThisSession = false
        }
    }

    // 2. 중첩 클래스 및 sealed class 정의
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

    // 3. Private MutableStateFlow
    private val _isSelectionMode = MutableStateFlow(false)
    private val _isShowingAllPhotos = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(TagSortOrder.CREATED_DESC)
    private val _rawTagList = MutableStateFlow<List<TagItem>>(emptyList())
    private val _homeLoadingState = MutableStateFlow<HomeLoadingState>(HomeLoadingState.Idle)
    private val _homeDeleteState = MutableStateFlow<HomeDeleteState>(HomeDeleteState.Idle)
    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _isLoadingPhotos = MutableStateFlow(false)
    private val _isLoadingMorePhotos = MutableStateFlow(false)
    private val _shouldReturnToAllPhotos = MutableStateFlow(false)
    private val _allPhotosScrollIndex = MutableStateFlow(0)
    private val _allPhotosScrollOffset = MutableStateFlow(0)
    private val _tagAlbumScrollIndex = MutableStateFlow(0)
    private val _tagAlbumScrollOffset = MutableStateFlow(0)
    private val _groupedPhotos = MutableStateFlow<List<DatedPhotoGroup>>(emptyList())

    // 4. Public StateFlow (exposed state)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    val isShowingAllPhotos: StateFlow<Boolean> = _isShowingAllPhotos.asStateFlow()
    val sortOrder = _sortOrder.asStateFlow()
    val rawTagList = _rawTagList.asStateFlow()
    val homeLoadingState = _homeLoadingState.asStateFlow()
    val homeDeleteState = _homeDeleteState.asStateFlow()
    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos
    val allPhotos = _allPhotos.asStateFlow()
    val isLoadingPhotos = _isLoadingPhotos.asStateFlow()
    val isLoadingMorePhotos = _isLoadingMorePhotos.asStateFlow()
    val shouldReturnToAllPhotos: StateFlow<Boolean> = _shouldReturnToAllPhotos.asStateFlow()
    val allPhotosScrollIndex: StateFlow<Int> = _allPhotosScrollIndex.asStateFlow()
    val allPhotosScrollOffset: StateFlow<Int> = _allPhotosScrollOffset.asStateFlow()
    val tagAlbumScrollIndex: StateFlow<Int> = _tagAlbumScrollIndex.asStateFlow()
    val tagAlbumScrollOffset: StateFlow<Int> = _tagAlbumScrollOffset.asStateFlow()
    val groupedPhotos = _groupedPhotos.asStateFlow()

    // 5. Private 변수
    private var currentOffset = 0
    private val pageSize = 66
    private var hasMorePhotos = true

    // 6. Public 함수 (UI에서 호출하는 함수들)
    fun setSelectionMode(isOn: Boolean) {
        _isSelectionMode.value = isOn
    }

    fun setIsShowingAllPhotos(isAllPhotos: Boolean) {
        _isShowingAllPhotos.value = isAllPhotos
    }

    fun setShouldReturnToAllPhotos(value: Boolean) {
        _shouldReturnToAllPhotos.value = value
    }

    fun setAllPhotosScrollPosition(
        index: Int,
        offset: Int,
    ) {
        _allPhotosScrollIndex.value = index
        _allPhotosScrollOffset.value = offset
    }

    fun setTagAlbumScrollPosition(
        index: Int,
        offset: Int,
    ) {
        _tagAlbumScrollIndex.value = index
        _tagAlbumScrollOffset.value = offset
    }

    fun togglePhoto(photo: Photo) {
        photoSelectionRepository.togglePhoto(photo)
    }

    fun resetSelection() {
        photoSelectionRepository.clear()
    }

    fun getPhotosToShare() = selectedPhotos.value

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

    fun resetDeleteState() {
        _homeDeleteState.value = HomeDeleteState.Idle
    }

    fun preGenerateStoriesOnce() {
        if (hasGeneratedStoriesThisSession) {
            android.util.Log.d("HomeViewModel", "Stories already generated this session, skipping")
            return
        }

        viewModelScope.launch {
            recommendRepository.generateStories(20)
            hasGeneratedStoriesThisSession = true
            android.util.Log.d("HomeViewModel", "Story pre-generation triggered (once per session)")
        }
    }

    // 7. Private 함수 (내부 헬퍼 함수들)
    private fun formatISODate(isoDate: String): String =
        try {
            val datePart = isoDate.substring(0, 10)
            datePart.replace('-', '.')
        } catch (e: Exception) {
            "Unknown Date"
        }

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
                TagSortOrder.CREATED_DESC -> currentList.sortedByDescending { parseDate(it.updatedAt) }
                TagSortOrder.COUNT_ASC -> currentList.sortedBy { it.photoCount }
                TagSortOrder.COUNT_DESC -> currentList.sortedByDescending { it.photoCount }
            }
        _homeLoadingState.value = HomeLoadingState.Success(tags = sortedList)
    }
}
