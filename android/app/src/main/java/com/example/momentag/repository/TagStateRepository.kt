package com.example.momentag.repository

import com.example.momentag.model.TagItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TagStateRepository
 *
 * Single source of truth for tag state across the application.
 * Manages tag CRUD operations and maintains synchronized state for all observers.
 */
@Singleton
class TagStateRepository
    @Inject
    constructor(
        private val remoteRepository: RemoteRepository,
    ) {
        sealed class LoadingState {
            object Idle : LoadingState()

            object Loading : LoadingState()

            data class Success(
                val tags: List<TagItem>,
            ) : LoadingState()

            data class Error(
                val error: TagError,
            ) : LoadingState()
        }

        sealed class TagError {
            object NetworkError : TagError()

            object Unauthorized : TagError()

            object UnknownError : TagError()
        }

        private val _tags = MutableStateFlow<List<TagItem>>(emptyList())
        val tags: StateFlow<List<TagItem>> = _tags.asStateFlow()

        private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
        val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

        /**
         * Load all tags from server and update state
         */
        suspend fun loadTags() {
            _loadingState.value = LoadingState.Loading

            when (val result = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    val tagItems =
                        result.data.map { tag ->
                            TagItem(
                                tagName = tag.tagName,
                                coverImageId = tag.thumbnailPhotoPathId,
                                tagId = tag.tagId,
                                createdAt = tag.createdAt,
                                updatedAt = tag.updatedAt,
                                photoCount = tag.photoCount,
                            )
                        }
                    _tags.value = tagItems
                    _loadingState.value = LoadingState.Success(tagItems)
                }

                is RemoteRepository.Result.Error -> {
                    _loadingState.value = LoadingState.Error(TagError.UnknownError)
                }

                is RemoteRepository.Result.Unauthorized -> {
                    _loadingState.value = LoadingState.Error(TagError.Unauthorized)
                }

                is RemoteRepository.Result.BadRequest -> {
                    _loadingState.value = LoadingState.Error(TagError.UnknownError)
                }

                is RemoteRepository.Result.NetworkError -> {
                    _loadingState.value = LoadingState.Error(TagError.NetworkError)
                }

                is RemoteRepository.Result.Exception -> {
                    _loadingState.value = LoadingState.Error(TagError.UnknownError)
                }
            }
        }

        /**
         * Delete a tag and update state
         */
        suspend fun deleteTag(tagId: String): RemoteRepository.Result<Unit> {
            val result = remoteRepository.removeTag(tagId)

            if (result is RemoteRepository.Result.Success) {
                // Update local state after successful deletion
                _tags.value = _tags.value.filter { it.tagId != tagId }
                _loadingState.value = LoadingState.Success(_tags.value)
            }

            return result
        }

        /**
         * Add a new tag to state (after creation)
         * Note: This should be called after the tag is created on the server
         */
        fun addTag(tag: TagItem) {
            _tags.value = _tags.value + tag
            _loadingState.value = LoadingState.Success(_tags.value)
        }

        /**
         * Update an existing tag in state
         */
        fun updateTag(updatedTag: TagItem) {
            _tags.value =
                _tags.value.map {
                    if (it.tagId == updatedTag.tagId) updatedTag else it
                }
            _loadingState.value = LoadingState.Success(_tags.value)
        }

        /**
         * Reset loading state to idle
         */
        fun resetLoadingState() {
            _loadingState.value = LoadingState.Idle
        }
    }
