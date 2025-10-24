package com.example.momentag.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PhotoTagViewModel : ViewModel() {
    private val _tagName = MutableStateFlow("")
    val tagName = _tagName.asStateFlow()

    private val _selectedPhotos = MutableStateFlow<List<Long>>(emptyList())
    val selectedPhotos = _selectedPhotos.asStateFlow()

    fun setInitialData(
        initialTagName: String?,
        initialSelectedPhotos: List<Long>,
    ) {
        _tagName.value = initialTagName ?: ""
        _selectedPhotos.value = initialSelectedPhotos
    }

    fun updateTagName(newTagName: String) {
        _tagName.value = newTagName
    }

    fun addPhoto(photoId: Long) {
        _selectedPhotos.update { currentList -> currentList + photoId }
    }

    fun removePhoto(photoId: Long) {
        _selectedPhotos.update { currentList -> currentList - photoId }
    }
}
