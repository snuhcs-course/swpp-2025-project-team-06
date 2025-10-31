package com.example.momentag.model

sealed class LoginState {
    object Idle : LoginState()

    object Success : LoginState()

    data class BadRequest(
        val message: String,
    ) : LoginState()

    data class Unauthorized(
        val message: String,
    ) : LoginState()

    data class NetworkError(
        val message: String,
    ) : LoginState()

    data class Error(
        val message: String,
    ) : LoginState()
}

sealed class RegisterState {
    object Idle : RegisterState()

    data class Success(
        val id: Int,
        // TODO: UUID 대신 String
    ) : RegisterState()

    data class BadRequest(
        val message: String,
    ) : RegisterState()

    data class Conflict(
        val message: String,
    ) : RegisterState()

    data class NetworkError(
        val message: String,
    ) : RegisterState()

    data class Error(
        val message: String,
    ) : RegisterState()
}

sealed interface RefreshState {
    object Idle : RefreshState

    object Success : RefreshState

    object Unauthorized : RefreshState

    data class Error(
        val message: String,
    ) : RefreshState

    data class NetworkError(
        val message: String,
    ) : RefreshState
}

sealed interface LogoutState {
    object Idle : LogoutState

    data object Loading : LogoutState

    data object Success : LogoutState

    data class Error(
        val message: String? = null,
    ) : LogoutState
}

// UI state by screen
data class HomeScreenUiState(
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val isUploadSuccess: Boolean = false,
)

sealed interface StoryState {
    object Idle : StoryState

    object Loading : StoryState

    data class Success(
        val stories: List<StoryModel>,
        val currentIndex: Int = 0,
        val hasMore: Boolean = true,
    ) : StoryState

    data class Error(
        val message: String,
    ) : StoryState

    data class NetworkError(
        val message: String,
    ) : StoryState
}

sealed class PhotoTagState {
    object Idle : PhotoTagState()

    object Loading : PhotoTagState()

    data class Success(
        val existingTags: List<String>,
        val recommendedTags: List<String>,
    ) : PhotoTagState()

    data class Error(
        val message: String,
    ) : PhotoTagState()
}
