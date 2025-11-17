package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.StoryModel
import com.example.momentag.model.StoryResponse
import com.example.momentag.model.StoryState
import com.example.momentag.model.StoryTagSubmissionState
import com.example.momentag.model.Tag
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val remoteRepository: RemoteRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
) : ViewModel() {
    // 1. Private MutableStateFlow
    private val _storyState = MutableStateFlow<StoryState>(StoryState.Idle)
    private val _selectedTags = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val _storyTagSubmissionStates = MutableStateFlow<Map<String, StoryTagSubmissionState>>(emptyMap())
    private val _viewedStories = MutableStateFlow<Set<String>>(emptySet())
    private val _editModeStory = MutableStateFlow<String?>(null)
    private val _originalTags = MutableStateFlow<Map<String, List<Tag>>>(emptyMap())

    // 2. Private 변수
    private val tagCache = mutableMapOf<String, List<String>>()
    private val currentStories = mutableListOf<StoryModel>()
    private var loadStoriesJob: Job? = null
    private var loadMoreStoriesJob: Job? = null

    // 3. Public StateFlow (exposed state)
    val storyState = _storyState.asStateFlow()
    val selectedTags = _selectedTags.asStateFlow()
    val storyTagSubmissionStates = _storyTagSubmissionStates.asStateFlow()
    val viewedStories = _viewedStories.asStateFlow()
    val editModeStory = _editModeStory.asStateFlow()
    val originalTags = _originalTags.asStateFlow()

    // 4. Public functions
    /**
     * Trigger story generation in the background (for pre-loading)
     * This is called from HomeScreen to prepare stories before user navigates to StoryScreen
     * @param size Number of stories to generate
     */
    fun preGenerateStories(size: Int) {
        viewModelScope.launch {
            recommendRepository.generateStories(size)
            android.util.Log.d("StoryViewModel", "Pre-generation of $size stories triggered")
        }
    }

    // 5. Private functions (helpers)
    /**
     * Process story responses and update state
     * @param storyResponses List of story responses from API
     * @param size Number of stories for next batch generation
     * @return true if processing succeeded, false otherwise
     */
    private fun processInitialStories(
        storyResponses: List<StoryResponse>,
        size: Int,
    ): Boolean {
        val photos =
            localRepository.toPhotos(
                storyResponses.map { story ->
                    com.example.momentag.model.PhotoResponse(
                        photoId = story.photoId,
                        photoPathId = story.photoPathId,
                        createdAt = "",
                    )
                },
            )

        if (photos.isEmpty()) {
            _storyState.value = StoryState.Success(emptyList(), 0, false)
            // Still trigger next generation
            preGenerateStories(size)
            return true
        }

        // Convert photos to StoryModels with metadata
        val stories =
            photos.mapIndexed { index, photo ->
                val story = storyResponses[index]
                val date = localRepository.getPhotoDate(story.photoPathId)
                val location = localRepository.getPhotoLocation(story.photoPathId)

                // Pre-populate suggested tags from the API response
                val suggestedTags = story.tags.take(4)

                StoryModel(
                    id = photo.photoId,
                    photoId = photo.photoId,
                    images = listOf(photo.contentUri),
                    date = date,
                    location = location,
                    suggestedTags = suggestedTags,
                )
            }

        currentStories.clear()
        currentStories.addAll(stories)

        _storyState.value =
            StoryState.Success(
                stories = stories,
                currentIndex = 0,
                hasMore = true, // Enable pagination
            )

        // Trigger next batch generation
        preGenerateStories(size)
        return true
    }

    /**
     * Load initial stories - fetches pre-generated stories and triggers next batch generation
     * Polls with 1-second interval if stories are not ready
     * @param size Number of stories to fetch
     */
    fun loadStories(size: Int) {
        // Cancel any existing polling job
        loadStoriesJob?.cancel()

        loadStoriesJob =
            viewModelScope.launch {
                _storyState.value = StoryState.Loading

                // Poll until stories are ready or error occurs
                while (true) {
                    when (val result = recommendRepository.getStories()) {
                        is RecommendRepository.StoryResult.Success -> {
                            processInitialStories(result.data, size)
                            return@launch
                        }

                        is RecommendRepository.StoryResult.NotReady -> {
                            // Wait 1 second before retrying
                            delay(1000)
                            continue
                        }

                        is RecommendRepository.StoryResult.NetworkError -> {
                            _storyState.value = StoryState.NetworkError(result.message)
                            return@launch
                        }

                        is RecommendRepository.StoryResult.Unauthorized -> {
                            _storyState.value = StoryState.Error("Please login again")
                            return@launch
                        }

                        is RecommendRepository.StoryResult.BadRequest -> {
                            _storyState.value = StoryState.Error(result.message)
                            return@launch
                        }

                        is RecommendRepository.StoryResult.Error -> {
                            _storyState.value = StoryState.Error(result.message)
                            return@launch
                        }
                    }
                }
            }
    }

    /**
     * Process additional story responses and append to current state
     * @param storyResponses List of story responses from API
     * @param currentState Current success state
     * @param size Number of stories for next batch generation
     * @return true if processing succeeded, false otherwise
     */
    private fun processAdditionalStories(
        storyResponses: List<StoryResponse>,
        currentState: StoryState.Success,
        size: Int,
    ): Boolean {
        val photos =
            localRepository.toPhotos(
                storyResponses.map { story ->
                    com.example.momentag.model.PhotoResponse(
                        photoId = story.photoId,
                        photoPathId = story.photoPathId,
                        createdAt = "",
                    )
                },
            )

        if (photos.isEmpty()) {
            _storyState.value =
                currentState.copy(
                    hasMore = false,
                )
            return true
        }

        // Convert photos to StoryModels with metadata
        val newStories =
            photos.mapIndexed { index, photo ->
                val storyResponse = storyResponses[index]
                val date = localRepository.getPhotoDate(storyResponse.photoPathId)
                val location = localRepository.getPhotoLocation(storyResponse.photoPathId)

                // Pre-populate suggested tags from the API response
                val suggestedTags = storyResponse.tags.take(4)

                StoryModel(
                    id = photo.photoId,
                    photoId = photo.photoId,
                    images = listOf(photo.contentUri),
                    date = date,
                    location = location,
                    suggestedTags = suggestedTags,
                )
            }

        currentStories.addAll(newStories)

        _storyState.value =
            currentState.copy(
                stories = currentStories.toList(),
                hasMore = true, // Always has more since we keep pre-generating
            )

        // Trigger next batch generation
        preGenerateStories(size)
        return true
    }

    /**
     * Load more stories for pagination - fetches pre-generated stories and triggers next batch
     * Polls with 1-second interval if stories are not ready
     * Does not transition to loading state since stories already exist
     * @param size Number of additional stories to fetch
     */
    fun loadMoreStories(size: Int) {
        val currentState = _storyState.value
        if (currentState !is StoryState.Success) return

        // Cancel any existing pagination polling job
        loadMoreStoriesJob?.cancel()

        loadMoreStoriesJob =
            viewModelScope.launch {
                // Poll until stories are ready or error occurs
                while (true) {
                    when (val result = recommendRepository.getStories()) {
                        is RecommendRepository.StoryResult.Success -> {
                            processAdditionalStories(result.data, currentState, size)
                            return@launch
                        }

                        is RecommendRepository.StoryResult.NotReady -> {
                            // Wait 1 second before retrying
                            delay(1000)
                            continue
                        }

                        is RecommendRepository.StoryResult.NetworkError,
                        is RecommendRepository.StoryResult.Unauthorized,
                        is RecommendRepository.StoryResult.BadRequest,
                        is RecommendRepository.StoryResult.Error,
                        -> {
                            // Silently fail for pagination - don't disrupt current state
                            return@launch
                        }
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
                        story.copy(suggestedTags = tags)
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
     * Add a custom tag to a story's suggested tags
     * @param storyId Story ID
     * @param tagName Custom tag name to add
     */
    fun addCustomTagToStory(
        storyId: String,
        tagName: String,
    ) {
        val currentState = _storyState.value
        if (currentState is StoryState.Success) {
            val updatedStories =
                currentState.stories.map { story ->
                    if (story.id == storyId) {
                        story.copy(suggestedTags = story.suggestedTags + tagName)
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

            toggleTag(storyId, tagName)
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
     * Uses diff logic for both initial submission and edits
     * @param storyId Story ID
     */
    fun submitTagsForStory(storyId: String) {
        val selectedTagsForStory = getSelectedTags(storyId)

        // Get the story to retrieve photoId
        val currentState = _storyState.value
        if (currentState !is StoryState.Success) return

        val story = currentState.stories.find { it.id == storyId }
        if (story == null) {
            return
        }

        val photoId = story.photoId

        // Set loading state
        _storyTagSubmissionStates.value =
            _storyTagSubmissionStates.value.toMutableMap().apply {
                this[storyId] = StoryTagSubmissionState.Loading
            }

        viewModelScope.launch {
            var hasError = false
            var errorMessage = ""

            // Get original tags (empty for new stories, existing tags for edits)
            val originalTags = _originalTags.value[storyId] ?: emptyList()
            val originalTagNames = originalTags.map { it.tagName }.toSet()
            val selectedTagNames = selectedTagsForStory

            // Calculate diff
            // Tags to remove: in original but not in selected
            val tagsToRemove = originalTags.filter { !selectedTagNames.contains(it.tagName) }

            // Tags to add: in selected but not in original
            val tagsToAdd = selectedTagNames.filter { !originalTagNames.contains(it) }

            // Remove tags
            for (tag in tagsToRemove) {
                when (val removeResult = remoteRepository.removeTagFromPhoto(photoId, tag.tagId)) {
                    is RemoteRepository.Result.Success -> {
                        android.util.Log.d("StoryViewModel", "Successfully removed tag ${tag.tagName} from photo $photoId")
                    }
                    is RemoteRepository.Result.Error -> {
                        android.util.Log.e("StoryViewModel", "Failed to remove tag ${tag.tagName}: ${removeResult.message}")
                        hasError = true
                        errorMessage = removeResult.message
                    }
                    is RemoteRepository.Result.Unauthorized -> {
                        android.util.Log.e("StoryViewModel", "Unauthorized: ${removeResult.message}")
                        hasError = true
                        errorMessage = "Please login again"
                        _storyTagSubmissionStates.value =
                            _storyTagSubmissionStates.value.toMutableMap().apply {
                                this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                            }
                        return@launch
                    }
                    is RemoteRepository.Result.NetworkError -> {
                        android.util.Log.e("StoryViewModel", "Network error: ${removeResult.message}")
                        hasError = true
                        errorMessage = "Network error. Please try again."
                        _storyTagSubmissionStates.value =
                            _storyTagSubmissionStates.value.toMutableMap().apply {
                                this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                            }
                        return@launch
                    }
                    is RemoteRepository.Result.Exception -> {
                        android.util.Log.e("StoryViewModel", "Exception: ${removeResult.e.message}")
                        hasError = true
                        errorMessage = "An error occurred. Please try again."
                        _storyTagSubmissionStates.value =
                            _storyTagSubmissionStates.value.toMutableMap().apply {
                                this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                            }
                        return@launch
                    }
                    else -> {
                        android.util.Log.e("StoryViewModel", "Unknown error while removing tag ${tag.tagName}")
                        hasError = true
                        errorMessage = "An error occurred. Please try again."
                    }
                }
            }

            // Add new tags
            for (tagName in tagsToAdd) {
                when (val createResult = remoteRepository.postTags(tagName)) {
                    is RemoteRepository.Result.Success -> {
                        val tagId = createResult.data.id
                        when (val associateResult = remoteRepository.postTagsToPhoto(photoId, tagId)) {
                            is RemoteRepository.Result.Success -> {
                                android.util.Log.d("StoryViewModel", "Successfully added tag $tagName to photo $photoId")
                            }
                            is RemoteRepository.Result.Error -> {
                                android.util.Log.e("StoryViewModel", "Failed to add tag $tagName: ${associateResult.message}")
                                hasError = true
                                errorMessage = associateResult.message
                            }
                            is RemoteRepository.Result.Unauthorized -> {
                                android.util.Log.e("StoryViewModel", "Unauthorized: ${associateResult.message}")
                                hasError = true
                                errorMessage = "Please login again"
                                _storyTagSubmissionStates.value =
                                    _storyTagSubmissionStates.value.toMutableMap().apply {
                                        this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                                    }
                                return@launch
                            }
                            is RemoteRepository.Result.NetworkError -> {
                                android.util.Log.e("StoryViewModel", "Network error: ${associateResult.message}")
                                hasError = true
                                errorMessage = "Network error. Please try again."
                                _storyTagSubmissionStates.value =
                                    _storyTagSubmissionStates.value.toMutableMap().apply {
                                        this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                                    }
                                return@launch
                            }
                            is RemoteRepository.Result.Exception -> {
                                android.util.Log.e("StoryViewModel", "Exception: ${associateResult.e.message}")
                                hasError = true
                                errorMessage = "An error occurred. Please try again."
                                _storyTagSubmissionStates.value =
                                    _storyTagSubmissionStates.value.toMutableMap().apply {
                                        this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                                    }
                                return@launch
                            }
                            else -> {
                                android.util.Log.e("StoryViewModel", "Unknown error while adding tag $tagName")
                                hasError = true
                                errorMessage = "An error occurred. Please try again."
                            }
                        }
                    }
                    is RemoteRepository.Result.Error -> {
                        android.util.Log.e("StoryViewModel", "Failed to create tag $tagName: ${createResult.message}")
                        hasError = true
                        errorMessage = createResult.message
                    }
                    is RemoteRepository.Result.Unauthorized -> {
                        android.util.Log.e("StoryViewModel", "Unauthorized: ${createResult.message}")
                        hasError = true
                        errorMessage = "Please login again"
                        _storyTagSubmissionStates.value =
                            _storyTagSubmissionStates.value.toMutableMap().apply {
                                this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                            }
                        return@launch
                    }
                    is RemoteRepository.Result.NetworkError -> {
                        android.util.Log.e("StoryViewModel", "Network error: ${createResult.message}")
                        hasError = true
                        errorMessage = "Network error. Please try again."
                        _storyTagSubmissionStates.value =
                            _storyTagSubmissionStates.value.toMutableMap().apply {
                                this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                            }
                        return@launch
                    }
                    is RemoteRepository.Result.Exception -> {
                        android.util.Log.e("StoryViewModel", "Exception: ${createResult.e.message}")
                        hasError = true
                        errorMessage = "An error occurred. Please try again."
                        _storyTagSubmissionStates.value =
                            _storyTagSubmissionStates.value.toMutableMap().apply {
                                this[storyId] = StoryTagSubmissionState.Error(errorMessage)
                            }
                        return@launch
                    }
                    else -> {
                        android.util.Log.e("StoryViewModel", "Unknown error while creating tag $tagName")
                        hasError = true
                        errorMessage = "An error occurred. Please try again."
                    }
                }
            }

            // Don't exit edit mode here - let UI handle it after showing checkmark

            // Set final state
            _storyTagSubmissionStates.value =
                _storyTagSubmissionStates.value.toMutableMap().apply {
                    this[storyId] =
                        if (hasError) {
                            StoryTagSubmissionState.Error(errorMessage.ifEmpty { "Failed to save tags" })
                        } else {
                            StoryTagSubmissionState.Success
                        }
                }
        }
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
     * Reset submission state for a story (e.g., when moving to next story)
     * @param storyId Story ID
     */
    fun resetSubmissionState(storyId: String) {
        _storyTagSubmissionStates.value =
            _storyTagSubmissionStates.value.toMutableMap().apply {
                this[storyId] = StoryTagSubmissionState.Idle
            }
    }

    /**
     * Mark a story as viewed (makes it read-only)
     * @param storyId Story ID
     */
    fun markStoryAsViewed(storyId: String) {
        _viewedStories.value = _viewedStories.value + storyId
    }

    /**
     * Enter edit mode for a story (fetches existing tags and enables editing)
     * Only one story can be in edit mode at a time
     * @param storyId Story ID
     * @param photoId Photo ID
     */
    fun enterEditMode(
        storyId: String,
        photoId: String,
    ) {
        // Exit edit mode for any currently editing story
        _editModeStory.value?.let { currentEditingStoryId ->
            if (currentEditingStoryId != storyId) {
                exitEditMode(currentEditingStoryId)
            }
        }

        // Reset submission state so the Done button appears
        resetSubmissionState(storyId)

        viewModelScope.launch {
            when (val result = remoteRepository.getPhotoDetail(photoId)) {
                is RemoteRepository.Result.Success -> {
                    val photoDetail = result.data
                    val existingTags = photoDetail.tags

                    // Store original tags for diff calculation
                    _originalTags.value =
                        _originalTags.value.toMutableMap().apply {
                            this[storyId] = existingTags
                        }

                    // Set selected tags to existing tags
                    _selectedTags.value =
                        _selectedTags.value.toMutableMap().apply {
                            this[storyId] = existingTags.map { it.tagName }.toSet()
                        }

                    // Enter edit mode
                    _editModeStory.value = storyId
                }
                else -> {
                    // Handle error - for now, just log
                    android.util.Log.e("StoryViewModel", "Failed to fetch photo details for edit mode")
                }
            }
        }
    }

    /**
     * Exit edit mode without saving
     * @param storyId Story ID
     */
    fun exitEditMode(storyId: String) {
        if (_editModeStory.value == storyId) {
            _editModeStory.value = null
            // Restore original selection
            val originalTagNames = _originalTags.value[storyId]?.map { it.tagName }?.toSet() ?: emptySet()
            _selectedTags.value =
                _selectedTags.value.toMutableMap().apply {
                    this[storyId] = originalTagNames
                }
        }
    }

    /**
     * Clear edit mode without restoring selection
     * Used after successful submission
     */
    fun clearEditMode() {
        _editModeStory.value = null
    }

    /**
     * Set story browsing session
     * Stores a single photo in ImageBrowserRepository for image detail navigation
     * @param photo Photo to view in detail
     */
    fun setStoryBrowsingSession(photo: com.example.momentag.model.Photo) {
        imageBrowserRepository.setStory(photo)
    }

    /**
     * Stop all active polling jobs
     * Should be called when user navigates away from StoryScreen
     */
    fun stopPolling() {
        loadStoriesJob?.cancel()
        loadStoriesJob = null
        loadMoreStoriesJob?.cancel()
        loadMoreStoriesJob = null
    }

    /**
     * Reset story state
     */
    fun resetState() {
        stopPolling()
        _storyState.value = StoryState.Idle
        _selectedTags.value = emptyMap()
        _storyTagSubmissionStates.value = emptyMap()
        _viewedStories.value = emptySet()
        _editModeStory.value = null
        _originalTags.value = emptyMap()
        tagCache.clear()
        currentStories.clear()
    }
}
