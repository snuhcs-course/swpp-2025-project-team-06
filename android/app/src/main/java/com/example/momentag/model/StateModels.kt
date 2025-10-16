package com.example.momentag.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

@OptIn(ExperimentalUuidApi::class)
sealed class RegisterState {
    object Idle : RegisterState()

    data class Success(
        val id: Uuid,
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

    object Success : LogoutState
}

/* UI state by screen */
data class HomeScreenUiState(
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val isUploadSuccess: Boolean = false
)