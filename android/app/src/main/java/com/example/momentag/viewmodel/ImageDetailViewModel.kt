package com.example.momentag.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.momentag.model.ImageContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ImageDetailViewModel
 *
 * 이미지 상세보기에서 사용할 ViewModel
 * 이미지 목록과 현재 인덱스를 관리
 */
class ImageDetailViewModel : ViewModel() {
    private val _imageContext = MutableStateFlow<ImageContext?>(null)
    val imageContext = _imageContext.asStateFlow()

    /**
     * 이미지 컨텍스트 설정
     */
    fun setImageContext(context: ImageContext) {
        _imageContext.value = context
    }

    /**
     * 이미지 컨텍스트 초기화
     */
    fun clearImageContext() {
        _imageContext.value = null
    }

    /**
     * 단일 이미지로 컨텍스트 설정
     */
    fun setSingleImage(uri: Uri) {
        _imageContext.value =
            ImageContext(
                images = listOf(uri),
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )
    }
}
