package com.example.momentag.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.TagItem
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.TagStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

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

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val localRepository: LocalRepository,
        private val remoteRepository: RemoteRepository,
        private val recommendRepository: RecommendRepository,
        private val photoSelectionRepository: PhotoSelectionRepository,
        private val imageBrowserRepository: ImageBrowserRepository,
        private val tagStateRepository: TagStateRepository,
        private val sortPreferences: com.example.momentag.data.SortPreferences,
    ) : ViewModel() {
        // 1. Companion object
        companion object {
            @Volatile
            private var hasGeneratedStoriesThisSession = false

            fun resetStoriesGeneratedFlag() {
                hasGeneratedStoriesThisSession = false
            }
        }

        // 2. state class 정의
        sealed class HomeLoadingState {
            object Idle : HomeLoadingState()

            object Loading : HomeLoadingState()

            data class Success(
                val tags: List<TagItem>,
            ) : HomeLoadingState()

            data class Error(
                val error: HomeError,
            ) : HomeLoadingState()
        }

        sealed class HomeDeleteState {
            object Idle : HomeDeleteState()

            object Loading : HomeDeleteState()

            object Success : HomeDeleteState()

            data class Error(
                val error: HomeError,
            ) : HomeDeleteState()
        }

        sealed class HomeError {
            object NetworkError : HomeError()

            object Unauthorized : HomeError()

            object DeleteFailed : HomeError()

            object UnknownError : HomeError()
        }

        // 3. Private MutableStateFlow
        private val _isSelectionMode = MutableStateFlow(false)
        private val _isShowingAllPhotos = MutableStateFlow(false)
        private val _sortOrder = MutableStateFlow(sortPreferences.getSortOrder())
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
        private val _scrollToIndex = MutableStateFlow<Int?>(null)

        // 4. Public StateFlow (exposed state)
        val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
        val isShowingAllPhotos: StateFlow<Boolean> = _isShowingAllPhotos.asStateFlow()
        val sortOrder = _sortOrder.asStateFlow()
        val homeDeleteState = _homeDeleteState.asStateFlow()
        val selectedPhotos: StateFlow<Map<String, Photo>> = photoSelectionRepository.selectedPhotos

        // Observe sorted tags from TagStateRepository
        val homeLoadingState: StateFlow<HomeLoadingState> =
            tagStateRepository.loadingState
                .combine(_sortOrder) { tagState, sortOrder ->
                    when (tagState) {
                        is TagStateRepository.LoadingState.Idle -> HomeLoadingState.Idle
                        is TagStateRepository.LoadingState.Loading -> HomeLoadingState.Loading
                        is TagStateRepository.LoadingState.Success -> {
                            val sortedTags = sortTags(tagState.tags, sortOrder)
                            HomeLoadingState.Success(sortedTags)
                        }
                        is TagStateRepository.LoadingState.Error -> {
                            val homeError =
                                when (tagState.error) {
                                    TagStateRepository.TagError.NetworkError -> HomeError.NetworkError
                                    TagStateRepository.TagError.Unauthorized -> HomeError.Unauthorized
                                    TagStateRepository.TagError.UnknownError -> HomeError.UnknownError
                                }
                            HomeLoadingState.Error(homeError)
                        }
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = HomeLoadingState.Idle,
                )
        val allPhotos = _allPhotos.asStateFlow()
        val isLoadingPhotos = _isLoadingPhotos.asStateFlow()
        val isLoadingMorePhotos = _isLoadingMorePhotos.asStateFlow()
        val shouldReturnToAllPhotos: StateFlow<Boolean> = _shouldReturnToAllPhotos.asStateFlow()
        val allPhotosScrollIndex: StateFlow<Int> = _allPhotosScrollIndex.asStateFlow()
        val allPhotosScrollOffset: StateFlow<Int> = _allPhotosScrollOffset.asStateFlow()
        val tagAlbumScrollIndex: StateFlow<Int> = _tagAlbumScrollIndex.asStateFlow()
        val tagAlbumScrollOffset: StateFlow<Int> = _tagAlbumScrollOffset.asStateFlow()
        val groupedPhotos = _groupedPhotos.asStateFlow()
        val scrollToIndex = _scrollToIndex.asStateFlow()

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

        fun getPhotosToShare() = selectedPhotos.value.values.toList()

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
                                _allPhotos.update { it + newPhotos }
                                updateGroupedPhotosIncremental(newPhotos)
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
                tagStateRepository.loadTags()
            }
        }

        fun deleteTag(tagId: String) {
            viewModelScope.launch {
                _homeDeleteState.value = HomeDeleteState.Loading

                when (val result = tagStateRepository.deleteTag(tagId)) {
                    is RemoteRepository.Result.Success -> {
                        _homeDeleteState.value = HomeDeleteState.Success
                    }

                    is RemoteRepository.Result.Error -> {
                        _homeDeleteState.value = HomeDeleteState.Error(HomeError.UnknownError)
                    }

                    is RemoteRepository.Result.Unauthorized -> {
                        _homeDeleteState.value = HomeDeleteState.Error(HomeError.Unauthorized)
                    }

                    is RemoteRepository.Result.BadRequest -> {
                        _homeDeleteState.value = HomeDeleteState.Error(HomeError.UnknownError)
                    }

                    is RemoteRepository.Result.NetworkError -> {
                        _homeDeleteState.value = HomeDeleteState.Error(HomeError.NetworkError)
                    }

                    is RemoteRepository.Result.Exception -> {
                        _homeDeleteState.value =
                            HomeDeleteState.Error(HomeError.UnknownError)
                    }
                }
            }
        }

        fun setSortOrder(newOrder: TagSortOrder) {
            if (_sortOrder.value == newOrder) return
            _sortOrder.value = newOrder
            sortPreferences.setSortOrder(newOrder)
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
                recommendRepository.getStories(20)
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

        private fun updateGroupedPhotosIncremental(newPhotos: List<Photo>) {
            // Group new photos by date
            val newPhotosByDate = newPhotos.groupBy { formatISODate(it.createdAt) }

            // Create a mutable map from existing groups
            val existingGroupMap = _groupedPhotos.value.associateBy { it.date }.toMutableMap()

            // Merge new photos into existing groups or create new groups
            newPhotosByDate.forEach { (date, photos) ->
                existingGroupMap[date] = existingGroupMap[date]?.let { existingGroup ->
                    // Append to existing group
                    DatedPhotoGroup(date, existingGroup.photos + photos)
                } ?: DatedPhotoGroup(date, photos) // Create new group
            }

            // Convert back to list and sort by date (descending)
            _groupedPhotos.value = existingGroupMap.values.sortedByDescending { it.date }
            imageBrowserRepository.setGallery(_allPhotos.value)
        }

        private fun getGridIndexForPhotoIndex(photoIndex: Int): Int? {
            if (photoIndex < 0 || photoIndex >= _allPhotos.value.size) {
                return null
            }

            val targetPhoto = _allPhotos.value[photoIndex]
            var gridIndex = 0

            for (group in _groupedPhotos.value) {
                val photoIndexInGroup = group.photos.indexOf(targetPhoto)

                if (photoIndexInGroup != -1) {
                    // Found the photo in this group
                    gridIndex += 1 // For the date header
                    gridIndex += photoIndexInGroup
                    return gridIndex
                } else {
                    // This group is before the target photo's group
                    gridIndex += 1 // For the date header
                    gridIndex += group.photos.size
                }
            }

            return null // Should not happen if photo is in _allPhotos
        }

        private fun sortTags(
            tags: List<TagItem>,
            sortOrder: TagSortOrder,
        ): List<TagItem> {
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

            return when (sortOrder) {
                TagSortOrder.NAME_ASC -> tags.sortedBy { it.tagName }
                TagSortOrder.NAME_DESC -> tags.sortedByDescending { it.tagName }
                TagSortOrder.CREATED_DESC -> tags.sortedByDescending { parseDate(it.updatedAt) }
                TagSortOrder.COUNT_ASC -> tags.sortedBy { it.photoCount }
                TagSortOrder.COUNT_DESC -> tags.sortedByDescending { it.photoCount }
            }
        }

        /**
         * Set scroll position to restore after returning from ImageDetailScreen
         */
        fun setScrollToIndex(index: Int?) {
            _scrollToIndex.value = index
        }

        /**
         * Clear scroll position after restoration
         */
        fun clearScrollToIndex() {
            _scrollToIndex.value = null
        }

        /**
         * Restore scroll position from ImageBrowserRepository (when returning from ImageDetailScreen)
         */
        fun restoreScrollPosition() {
            val lastViewedPhotoIndex = imageBrowserRepository.getCurrentIndex()
            lastViewedPhotoIndex?.let { photoIndex ->
                val gridIndex = getGridIndexForPhotoIndex(photoIndex)
                gridIndex?.let {
                    setScrollToIndex(it)
                }
            }
        }
    }
