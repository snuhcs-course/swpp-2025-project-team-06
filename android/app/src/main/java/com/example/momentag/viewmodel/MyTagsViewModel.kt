package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.TagCntData
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyTagsViewModel(
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MyTagsUiState>(MyTagsUiState.Loading)
    val uiState: StateFlow<MyTagsUiState> = _uiState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    init {
        loadTags()
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun loadTags() {
        viewModelScope.launch {
            _uiState.value = MyTagsUiState.Loading
            println("MyTagsViewModel: loadTags() started")

            when (val result = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: getAllTags() Success - ${result.data.size} tags")
                    // Backend에서 photo_count를 제공하는지 확인
                    val hasPhotoCount = result.data.firstOrNull()?.photoCount != null
                    println("MyTagsViewModel: hasPhotoCount = $hasPhotoCount")

                    val tags =
                        if (hasPhotoCount) {
                            // Backend에서 count를 제공하는 경우: 직접 사용
                            result.data.map { tagResponse ->
                                TagCntData(
                                    tagId = tagResponse.tagId,
                                    tagName = tagResponse.tagName,
                                    count = tagResponse.photoCount ?: 0,
                                )
                            }
                        } else {
                            // Backend에서 count를 제공하지 않는 경우: 각 태그의 사진 개수를 병렬로 가져오기
                            val tagsDeferred =
                                result.data.map { tagResponse ->
                                    async {
                                        val photoCount =
                                            when (val photosResult = remoteRepository.getPhotosByTag(tagResponse.tagId)) {
                                                is RemoteRepository.Result.Success -> photosResult.data.size
                                                else -> 0 // 에러 시 0으로 표시
                                            }

                                        TagCntData(
                                            tagId = tagResponse.tagId,
                                            tagName = tagResponse.tagName,
                                            count = photoCount,
                                        )
                                    }
                                }
                            tagsDeferred.awaitAll()
                        }

                    println("MyTagsViewModel: Final tags count = ${tags.size}")
                    _uiState.value = MyTagsUiState.Success(tags)
                }

                is RemoteRepository.Result.Error -> {
                    println("MyTagsViewModel: Error ${result.code}: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Error ${result.code}: ${result.message}")
                }

                is RemoteRepository.Result.Unauthorized -> {
                    println("MyTagsViewModel: Unauthorized: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Unauthorized: ${result.message}")
                }

                is RemoteRepository.Result.BadRequest -> {
                    println("MyTagsViewModel: BadRequest: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Bad Request: ${result.message}")
                }

                is RemoteRepository.Result.NetworkError -> {
                    println("MyTagsViewModel: NetworkError: ${result.message}")
                    _uiState.value = MyTagsUiState.Error("Network Error: ${result.message}")
                }

                is RemoteRepository.Result.Exception -> {
                    println("MyTagsViewModel: Exception: ${result.e.message}")
                    _uiState.value = MyTagsUiState.Error("Exception: ${result.e.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun refreshTags() {
        loadTags()
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            println("MyTagsViewModel: deleteTag($tagId)")
            when (val result = remoteRepository.removeTag(tagId)) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: Tag deleted successfully")
                    loadTags() // 태그 목록 새로고침
                }
                is RemoteRepository.Result.Error -> {
                    println("MyTagsViewModel: Failed to delete tag - ${result.message}")
                }
                else -> {
                    println("MyTagsViewModel: Failed to delete tag")
                }
            }
        }
    }

    fun showEditDialog(
        tagId: String,
        tagName: String,
    ) {
        // TODO: 다이얼로그 표시하여 태그 이름 수정
        println("MyTagsViewModel: showEditDialog($tagId, $tagName)")
    }

    fun renameTag(
        tagId: String,
        newName: String,
    ) {
        viewModelScope.launch {
            println("MyTagsViewModel: renameTag($tagId, $newName)")
            when (val result = remoteRepository.renameTag(tagId, newName)) {
                is RemoteRepository.Result.Success -> {
                    println("MyTagsViewModel: Tag renamed successfully")
                    loadTags() // 태그 목록 새로고침
                }
                is RemoteRepository.Result.Error -> {
                    println("MyTagsViewModel: Failed to rename tag - ${result.message}")
                }
                else -> {
                    println("MyTagsViewModel: Failed to rename tag")
                }
            }
        }
    }
}
