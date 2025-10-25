package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.ImageOfTagLoadState
import com.example.momentag.model.TagItem
import com.example.momentag.model.TagLoadState
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TagViewModel(
    private val remoteRepository: RemoteRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _tagLoadState = MutableStateFlow<TagLoadState>(TagLoadState.Idle)
    val tagLoadState = _tagLoadState.asStateFlow()

    private val _imageOfTagLoadState = MutableStateFlow<ImageOfTagLoadState>(ImageOfTagLoadState.Idle)
    val imageOfTagLoadState = _imageOfTagLoadState.asStateFlow()

    fun loadServerTags() {
        viewModelScope.launch {
            _tagLoadState.value = TagLoadState.Loading

            when (val tagsResult = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    try {
                        val tags = tagsResult.data.tags

                        val tagItems: List<TagItem> =
                            tags
                                .map { tag ->
                                    async(ioDispatcher) {
                                        when (val photosResult = remoteRepository.getPhotosByTag(tag.tagName)) {
                                            is RemoteRepository.Result.Success -> {
                                                val coverId =
                                                    photosResult.data.photos
                                                        .firstOrNull()
                                                        ?.photoId

                                                TagItem(tagName = tag.tagName, coverImageId = coverId)
                                            }
                                            else -> {
                                                TagItem(tagName = tag.tagName, coverImageId = null)
                                            }
                                        }
                                    }
                                }.awaitAll()

                        _tagLoadState.value = TagLoadState.Success(tagItems = tagItems)
                    } catch (e: Exception) {
                        _tagLoadState.value = TagLoadState.Error(e.message ?: "Failed to load tag images")
                    }
                }

                is RemoteRepository.Result.Error -> {
                    _tagLoadState.value = TagLoadState.Error(tagsResult.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _tagLoadState.value = TagLoadState.Error(tagsResult.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _tagLoadState.value = TagLoadState.Error(tagsResult.message)
                }
                is RemoteRepository.Result.NetworkError -> {
                    _tagLoadState.value = TagLoadState.NetworkError(tagsResult.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _tagLoadState.value = TagLoadState.Error(tagsResult.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun loadImagesOfTag(tagName: String) {
        viewModelScope.launch {
            _imageOfTagLoadState.value = ImageOfTagLoadState.Loading

            when (val result = remoteRepository.getPhotosByTag(tagName)) {
                is RemoteRepository.Result.Success -> {
                    _imageOfTagLoadState.value =
                        ImageOfTagLoadState.Success(
                            photos = result.data,
                        )
                }
                is RemoteRepository.Result.Error -> {
                    _imageOfTagLoadState.value = ImageOfTagLoadState.Error(result.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _imageOfTagLoadState.value = ImageOfTagLoadState.Error("Please login again")
                }
                is RemoteRepository.Result.NetworkError -> {
                    _imageOfTagLoadState.value = ImageOfTagLoadState.NetworkError(result.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _imageOfTagLoadState.value = ImageOfTagLoadState.Error(result.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _imageOfTagLoadState.value = ImageOfTagLoadState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun resetState() {
        _tagLoadState.value = TagLoadState.Idle
        _imageOfTagLoadState.value = ImageOfTagLoadState.Idle
    }
}
