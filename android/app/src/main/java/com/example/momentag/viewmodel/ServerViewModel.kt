package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.Tag
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerViewModel(
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags = _allTags.asStateFlow()

    fun getAllTags() {
        viewModelScope.launch {
            when (val result = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    _allTags.value = result.data
                }
                else -> {
                    // TODO : Handle error
                }
            }
        }
    }

    private val _photoByTag = MutableStateFlow<List<Photo>>(emptyList())
    val photoByTag = _photoByTag.asStateFlow()

    fun getPhotoByTag(tagName: String) {
        viewModelScope.launch {
            when (val result = remoteRepository.getPhotosByTag(tagName)) {
                is RemoteRepository.Result.Success -> {
                    _photoByTag.value = result.data
                }
                else -> {
                    // TODO : Handle error
                }
            }
        }
    }
}
