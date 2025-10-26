package com.example.momentag.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import com.example.momentag.repository.ImageBrowserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ImageDetailViewModel
 *
 * 이미지 상세보기에서 사용할 ViewModel
 * ImageBrowserRepository로부터 이미지 컨텍스트를 조회
 */
class ImageDetailViewModel(
    private val imageBrowserRepository: ImageBrowserRepository,
) : ViewModel() {
    private val _imageContext = MutableStateFlow<ImageContext?>(null)
    val imageContext = _imageContext.asStateFlow()

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
     * 이미지 컨텍스트 초기화
     */
    fun clearImageContext() {
        _imageContext.value = null
    }
}
