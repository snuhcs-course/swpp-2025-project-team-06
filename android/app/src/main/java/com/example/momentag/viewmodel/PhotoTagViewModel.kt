package com.example.momentag.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class PhotoTagViewModel : ViewModel() {
    var tagName: String = ""
    var initSelectedPhotos = mutableStateListOf<Long>()

    fun setInitialData(
        TagName: String?,
        InitSelectedPhotos: List<Long>,
    ) {
        tagName = TagName ?: ""

        initSelectedPhotos.clear()
        initSelectedPhotos.addAll(InitSelectedPhotos)
    }

    fun updateTagName(newTagName: String) {
        tagName = newTagName
    }

    fun updateSelectedPhotos(newSelectedPhotos: List<Long>) {
        initSelectedPhotos.clear()
        initSelectedPhotos.addAll(newSelectedPhotos)
    }
}
