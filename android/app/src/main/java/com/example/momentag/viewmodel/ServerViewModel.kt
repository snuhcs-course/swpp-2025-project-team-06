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
            try {
                _allTags.value = remoteRepository.getAllTags()
            } catch (e: Exception) {
                /*
                 * TODO : Handle error
                 */
            }
        }
    }

    private val _photoByTag = MutableStateFlow<List<Photo>>(emptyList())
    val photoByTag = _photoByTag.asStateFlow()

    fun getPhotoByTag(tagName: String) {
        viewModelScope.launch {
            try {
                _photoByTag.value = remoteRepository.getPhotosByTag(tagName)
            } catch (e: Exception) {
                /*
                 * TODO : Handle error
                 */
            }
        }
    }
}
