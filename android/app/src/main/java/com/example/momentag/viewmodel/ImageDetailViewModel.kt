package com.example.momentag.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoTagState
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.viewmodel.HomeViewModel.HomeDeleteState
import kotlinx.coroutines.flow.MutableStateFlow
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
    sealed class TagDeleteState{
        object Idle : TagDeleteState()

        object Loading : TagDeleteState()

        object Success : TagDeleteState()

        data class Error(
            val message: String,
        ) : TagDeleteState()
    }
    private val _imageContext = MutableStateFlow<ImageContext?>(null)
    val imageContext = _imageContext.asStateFlow()

    private val _photoTagState = MutableStateFlow<PhotoTagState>(PhotoTagState.Idle)
    val photoTagState = _photoTagState.asStateFlow()

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
        // Repository에서 URI로 컨텍스트 조회
        val context = imageBrowserRepository.getPhotoContextByUri(uri)

        if (context != null) {
            // Found in browsing session
            _imageContext.value = context
        } else {
            // Not in session - create standalone context for single image
            _imageContext.value =
                ImageContext(
                    images =
                        listOf(
                            Photo(
                                photoId = "", // Standalone image has no backend ID
                                contentUri = uri,
                            ),
                        ),
                    currentIndex = 0,
                    contextType = ImageContext.ContextType.GALLERY,
                )
        }
    }

    /**
     * photoId를 기반으로 사진의 태그와 추천 태그를 로드
     * @param photoId 사진 ID
     */
    fun loadPhotoTags(photoId: String) {
        if (photoId.isEmpty()) {
            _photoTagState.value = PhotoTagState.Idle
            return
        }

        viewModelScope.launch {
            _photoTagState.value = PhotoTagState.Loading

            // Load existing tags from photo detail
            val photoDetailResult = remoteRepository.getPhotoDetail(photoId)

            when (photoDetailResult) {
                is RemoteRepository.Result.Success -> {
                    val existingTags = photoDetailResult.data.tags

                    val existingTagNames = existingTags.map { it.tagName }

                    // Load recommended tags
                    val recommendResult = recommendRepository.recommendTagFromPhoto(photoId)

                    when (recommendResult) {
                        is RecommendRepository.RecommendResult.Success -> {
                            val recommendedTag = recommendResult.data.tagName
                            // Only include recommendation if not already in existing tags
                            val recommendedTags =
                                if (recommendedTag !in existingTagNames) {
                                    listOf(recommendedTag)
                                } else {
                                    emptyList()
                                }

                            _photoTagState.value =
                                PhotoTagState.Success(
                                    existingTags = existingTags,
                                    recommendedTags = recommendedTags,
                                )
                        }
                        is RecommendRepository.RecommendResult.Error -> {
                            // If recommendation fails, still show existing tags
                            _photoTagState.value =
                                PhotoTagState.Success(
                                    existingTags = existingTags,
                                    recommendedTags = emptyList(),
                                )
                        }
                        is RecommendRepository.RecommendResult.BadRequest,
                        is RecommendRepository.RecommendResult.Unauthorized,
                        is RecommendRepository.RecommendResult.NetworkError,
                        -> {
                            // If recommendation fails, still show existing tags
                            _photoTagState.value =
                                PhotoTagState.Success(
                                    existingTags = existingTags,
                                    recommendedTags = emptyList(),
                                )
                        }
                    }
                }
                is RemoteRepository.Result.Error -> {
                    _photoTagState.value = PhotoTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _photoTagState.value = PhotoTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _photoTagState.value = PhotoTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.NetworkError -> {
                    _photoTagState.value = PhotoTagState.Error(photoDetailResult.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _photoTagState.value = PhotoTagState.Error(photoDetailResult.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun deleteTagFromPhoto(photoId: String, tagId: String) {
        viewModelScope.launch {
            _tagDeleteState.value = TagDeleteState.Loading

            when (val result = remoteRepository.removeTagFromPhoto(photoId, tagId)) {
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
     * 이미지 컨텍스트 초기화
     */
    fun clearImageContext() {
        _imageContext.value = null
        _photoTagState.value = PhotoTagState.Idle
    }
}
