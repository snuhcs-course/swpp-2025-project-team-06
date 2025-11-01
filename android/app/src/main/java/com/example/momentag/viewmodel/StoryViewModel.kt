package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.StoryModel
import com.example.momentag.model.StoryState
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * StoryViewModel
 *
 * Manages story feature UI state:
 * - Fetches stories from backend using RecommendRepository
 * - Converts PhotoResponse to StoryModel with metadata
 * - Lazy-loads recommended tags for each story
 * - Tracks user's tag selections per story
 * - Handles pagination for infinite scroll
 */
class StoryViewModel(
    private val recommendRepository: RecommendRepository,
    private val localRepository: LocalRepository,
) : ViewModel() {
    private val _storyState = MutableStateFlow<StoryState>(StoryState.Idle)
    val storyState = _storyState.asStateFlow()

    // Track selected tags per story: Map<storyId, Set<tagName>>
    private val _selectedTags = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val selectedTags = _selectedTags.asStateFlow()

    // Cache for story tags to avoid duplicate API calls: Map<storyId, List<tagName>>
    private val tagCache = mutableMapOf<String, List<String>>()

    // Track current stories list for pagination
    private val currentStories = mutableListOf<StoryModel>()

    /**
     * Load initial stories
     * @param size Number of stories to fetch
     */
    fun loadStories(size: Int) {
        viewModelScope.launch {
            _storyState.value = StoryState.Loading

            when (val result = recommendRepository.getStories(size)) {
                is RecommendRepository.RecommendResult.Success -> {
                    val photoResponses = result.data
                    val photos = localRepository.toPhotos(photoResponses)

                    if (photos.isEmpty()) {
                        _storyState.value = StoryState.Success(emptyList(), 0, false)
                        return@launch
                    }

                    // Convert photos to StoryModels with metadata
                    val stories =
                        photos.mapIndexed { index, photo ->
                            val photoResponse = photoResponses[index]
                            val date = localRepository.getPhotoDate(photoResponse.photoPathId)
                            val location = localRepository.getPhotoLocation(photoResponse.photoPathId)

                            StoryModel(
                                id = photo.photoId,
                                photoId = photo.photoId,
                                images = listOf(photo.contentUri.toString()),
                                date = date,
                                location = location,
                                suggestedTags = emptyList(),
                            )
                        }

                    currentStories.clear()
                    currentStories.addAll(stories)

                    _storyState.value =
                        StoryState.Success(
                            stories = stories,
                            currentIndex = 0,
                            hasMore = stories.size == size,
                        )
                }

                is RecommendRepository.RecommendResult.NetworkError -> {
                    _storyState.value = StoryState.NetworkError(result.message)
                }

                is RecommendRepository.RecommendResult.Unauthorized -> {
                    _storyState.value = StoryState.Error("Please login again")
                }

                is RecommendRepository.RecommendResult.BadRequest -> {
                    _storyState.value = StoryState.Error(result.message)
                }

                is RecommendRepository.RecommendResult.Error -> {
                    _storyState.value = StoryState.Error(result.message)
                }
            }
        }
    }

    /**
     * Load more stories for pagination
     * @param size Number of additional stories to fetch
     */
    fun loadMoreStories(size: Int) {
        val currentState = _storyState.value
        if (currentState !is StoryState.Success) return

        viewModelScope.launch {
            when (val result = recommendRepository.getStories(size)) {
                is RecommendRepository.RecommendResult.Success -> {
                    val photoResponses = result.data
                    val photos = localRepository.toPhotos(photoResponses)

                    if (photos.isEmpty()) {
                        _storyState.value =
                            currentState.copy(
                                hasMore = false,
                            )
                        return@launch
                    }

                    // Convert photos to StoryModels with metadata
                    val newStories =
                        photos.mapIndexed { index, photo ->
                            val photoResponse = photoResponses[index]
                            val date = localRepository.getPhotoDate(photoResponse.photoPathId)
                            val location = localRepository.getPhotoLocation(photoResponse.photoPathId)

                            StoryModel(
                                id = photo.photoId,
                                photoId = photo.photoId,
                                images = listOf(photo.contentUri.toString()),
                                date = date,
                                location = location,
                                suggestedTags = emptyList(),
                            )
                        }

                    currentStories.addAll(newStories)

                    _storyState.value =
                        currentState.copy(
                            stories = currentStories.toList(),
                            hasMore = newStories.size == size,
                        )
                }

                is RecommendRepository.RecommendResult.NetworkError,
                is RecommendRepository.RecommendResult.Unauthorized,
                is RecommendRepository.RecommendResult.BadRequest,
                is RecommendRepository.RecommendResult.Error,
                -> {
                    // Silently fail for pagination - don't disrupt current state
                }
            }
        }
    }

    /**
     * Load recommended tags for a story (lazy loading)
     * @param storyId Story ID
     * @param photoId Photo ID for the story
     */
    fun loadTagsForStory(
        storyId: String,
        photoId: String,
    ) {
        val currentState = _storyState.value
        if (currentState !is StoryState.Success) return

        // Check if story already has tags populated
        val story = currentState.stories.find { it.id == storyId }
        if (story != null && story.suggestedTags.isNotEmpty()) {
            return
        }

        // Check cache first
        if (tagCache.containsKey(storyId)) {
            // Use cached tags to update the story
            val cachedTags = tagCache[storyId] ?: emptyList()
            updateStoryTags(storyId, cachedTags)
            return
        }

        viewModelScope.launch {
            when (val result = recommendRepository.recommendTagFromPhoto(photoId)) {
                is RecommendRepository.RecommendResult.Success -> {
                    val tags = result.data.map { it.tagName }
                    tagCache[storyId] = tags
                    updateStoryTags(storyId, tags)
                }

                is RecommendRepository.RecommendResult.NetworkError,
                is RecommendRepository.RecommendResult.Unauthorized,
                is RecommendRepository.RecommendResult.BadRequest,
                is RecommendRepository.RecommendResult.Error,
                -> {
                    // Silently fail - tag loading is not critical
                    tagCache[storyId] = emptyList()
                }
            }
        }
    }

    /**
     * Update a story's suggested tags in the state
     * @param storyId Story ID
     * @param tags List of tag names
     */
    private fun updateStoryTags(
        storyId: String,
        tags: List<String>,
    ) {
        val currentState = _storyState.value
        if (currentState is StoryState.Success) {
            val updatedStories =
                currentState.stories.map { story ->
                    if (story.id == storyId) {
                        story.copy(suggestedTags = tags + "+")
                    } else {
                        story
                    }
                }

            // Update currentStories list
            currentStories.clear()
            currentStories.addAll(updatedStories)

            _storyState.value =
                currentState.copy(
                    stories = updatedStories,
                )
        }
    }

    /**
     * Toggle tag selection for a story
     * @param storyId Story ID
     * @param tag Tag name to toggle
     */
    fun toggleTag(
        storyId: String,
        tag: String,
    ) {
        val currentTags = _selectedTags.value[storyId] ?: emptySet()
        val updatedTags =
            if (currentTags.contains(tag)) {
                currentTags - tag
            } else {
                currentTags + tag
            }

        _selectedTags.value =
            _selectedTags.value.toMutableMap().apply {
                this[storyId] = updatedTags
            }
    }

    /**
     * Get selected tags for a story
     * @param storyId Story ID
     * @return Set of selected tag names
     */
    fun getSelectedTags(storyId: String): Set<String> = _selectedTags.value[storyId] ?: emptySet()

    /**
     * Submit tags for a story (save to backend)
     * This is called when user clicks "Done" on a story
     * TODO: Implement backend API call to save tags
     * @param storyId Story ID
     */
    fun submitTagsForStory(storyId: String) {
        val selectedTagsForStory = _selectedTags.value[storyId] ?: emptySet()

        // TODO: Call backend API to save tags
        // For now, just log the tags
        android.util.Log.d("StoryViewModel", "Submitting tags for story $storyId: $selectedTagsForStory")
    }

    /**
     * Update current story index
     * @param index New index
     */
    fun setCurrentIndex(index: Int) {
        val currentState = _storyState.value
        if (currentState is StoryState.Success) {
            _storyState.value = currentState.copy(currentIndex = index)
        }
    }

    /**
     * Reset story state
     */
    fun resetState() {
        _storyState.value = StoryState.Idle
        _selectedTags.value = emptyMap()
        tagCache.clear()
        currentStories.clear()
    }
}
