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
