package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.TagItem
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val localRepository: LocalRepository,
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    sealed class HomeLoadingState {
        object Idle : HomeLoadingState()

        object Loading : HomeLoadingState()

        data class Success(
            val tags: List<TagItem>,
        ) : HomeLoadingState()

        data class Error(
            val message: String,
        ) : HomeLoadingState()
    }

    private val _homeLoadingState = MutableStateFlow<HomeLoadingState>(HomeLoadingState.Idle)
    val homeLoadingState = _homeLoadingState.asStateFlow()

    fun loadServerTags() {
        viewModelScope.launch {
            _homeLoadingState.value = HomeLoadingState.Loading

            when (val result = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    val tags = result.data

                    _homeLoadingState.value =
                        HomeLoadingState.Success(
                            tags =
                                tags.map {
                                    TagItem(it.tagName, null, it.tagId)
                                },
                        )
                }

                is RemoteRepository.Result.Error -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.NetworkError -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _homeLoadingState.value = HomeLoadingState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }
}
