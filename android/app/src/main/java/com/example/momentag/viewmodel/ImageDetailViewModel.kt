package com.example.momentag.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.ImageContext
import com.example.momentag.model.ImageDetailTagState
import com.example.momentag.model.PhotoTagState
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ImageDetailViewModel
 *
 * 이미지 상세보기에서 사용할 ViewModel
 * ImageBrowserRepository로부터 이미지 컨텍스트를 조회
 * RemoteRepository와 RecommendRepository를 통해 태그 정보 및 추천 태그 조회
 */
class ImageDetailViewModel(
    private val imageBrowserRepository: ImageBrowserRepository,
    private val remoteRepository: RemoteRepository,
    private val recommendRepository: RecommendRepository,
) : ViewModel() {
    sealed class TagDeleteState {
        object Idle : TagDeleteState()

        object Loading : TagDeleteState()

        object Success : TagDeleteState()

        data class Error(
            val message: String,
        ) : TagDeleteState()
    }

    private val _imageContext = MutableStateFlow<ImageContext?>(null)
    val imageContext = _imageContext.asStateFlow()

    private val _imageDetailTagState = MutableStateFlow<ImageDetailTagState>(ImageDetailTagState.Idle)
    val imageDetailTagState: StateFlow<ImageDetailTagState> = _imageDetailTagState.asStateFlow()

    private val _tagDeleteState = MutableStateFlow<TagDeleteState>(TagDeleteState.Idle)
    val tagDeleteState = _tagDeleteState.asStateFlow()

    /**
     * photoId를 기반으로 ImageContext를 Repository에서 조회하여 설정
     * @param photoId 현재 보고 있는 사진의 ID
     */
    fun loadImageContext(photoId: String) {
        _imageContext.value = imageBrowserRepository.getPhotoContext(photoId)
    }

    /**
     * URI를 기반으로 ImageContext를 Repository에서 조회하여 설정
     * @param uri 현재 보고 있는 사진의 URI
     */
    fun loadImageContextByUri(uri: Uri) {
        val context = imageBrowserRepository.getPhotoContextByUri(uri)
        _imageContext.value = context // may be null when no active browsing session
    }

    /**
     * photoId를 기반으로 사진의 태그와 추천 태그를 로드
     * @param photoId 사진 ID (로컬 사진의 경우 photo_path_id일 수 있음)
     */
    fun loadPhotoTags(photoId: String) {
        if (photoId.isEmpty()) {
            _imageDetailTagState.value = ImageDetailTagState.Idle
            return
        }

        viewModelScope.launch {
            // If photoId is numeric (photo_path_id from local album), find the actual UUID
            val actualPhotoId =
                if (photoId.toLongOrNull() != null) {
                    // This is a photo_path_id, need to find the actual UUID
                    findPhotoIdByPathId(photoId.toLong())
                } else {
                    // Already a UUID
                    photoId
                }

            if (actualPhotoId == null) {
                // Photo not found in backend (not uploaded yet)
                _imageDetailTagState.value = ImageDetailTagState.Success(
                    existingTags = emptyList(),
                    recommendedTags = emptyList(),
                    isExistingLoading = false,
                    isRecommendedLoading = false
                )
                return@launch
            }

            // Load existing tags from photo detail
            val photoDetailResult = remoteRepository.getPhotoDetail(actualPhotoId)

            when (photoDetailResult) {
                is RemoteRepository.Result.Success -> {
                    val existingTags = photoDetailResult.data.tags
                    val existingTagNames = existingTags.map { it.tagName }

                    _imageDetailTagState.value = ImageDetailTagState.Success(
                        existingTags = existingTags,
                        isExistingLoading = false,
                        isRecommendedLoading = true // 추천 태그는 아직 로딩 중
                    )

                    // Load recommended tags
                    val recommendResult = recommendRepository.recommendTagFromPhoto(actualPhotoId)
                    when (recommendResult) {
                        is RecommendRepository.RecommendResult.Success -> {
                            val recommendedTags =
                                recommendResult.data
                                    .map {
                                        it.tagName
                                    }.filter {
                                        it !in existingTagNames
                                    }.take(1)

                            val currentState = _imageDetailTagState.value
                            if (currentState is ImageDetailTagState.Success) {
                                _imageDetailTagState.value = currentState.copy(
                                    recommendedTags = recommendedTags,
                                    isRecommendedLoading = false
                                )
                            }
                        }
                        is RecommendRepository.RecommendResult.BadRequest,
                        is RecommendRepository.RecommendResult.Unauthorized,
                        is RecommendRepository.RecommendResult.NetworkError,
                        is RecommendRepository.RecommendResult.Error -> {
                            // If recommendation fails, still show existing tags
                            val currentState = _imageDetailTagState.value
                            if (currentState is ImageDetailTagState.Success) {
                                _imageDetailTagState.value = currentState.copy(
                                    isRecommendedLoading = false
                                )
                            }
                        }
                    }
                }
                is RemoteRepository.Result.Error -> {
                    _imageDetailTagState.value = ImageDetailTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _imageDetailTagState.value = ImageDetailTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _imageDetailTagState.value = ImageDetailTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.NetworkError -> {
                    _imageDetailTagState.value = ImageDetailTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _imageDetailTagState.value = ImageDetailTagState.Error(photoDetailResult.e.message ?: "Unknown error")
                }
            }
        }
    }

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

    /**
     * photo_path_id로 실제 photo_id (UUID)를 찾는 함수
     * @param photoPathId 로컬 미디어 ID
     * @return 백엔드에 업로드된 photo_id (UUID), 없으면 null
     */
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

    /**
     * 이미지 컨텍스트 초기화
     */
    fun clearImageContext() {
        _imageContext.value = null
        _imageDetailTagState.value = ImageDetailTagState.Idle
    }
}
