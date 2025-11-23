package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.data.SessionExpirationManager
import com.example.momentag.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi

/**
 * AuthViewModel
 *
 * 역할: UI 상태/이벤트만 관리
 * - 세션 세부 구현을 모름
 * - TokenRepository의 상태(Flow)를 구독
 * - 비즈니스 로직은 TokenRepository에 위임
 */
@OptIn(ExperimentalUuidApi::class)
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val tokenRepository: TokenRepository,
        private val sessionExpirationManager: SessionExpirationManager,
    ) : ViewModel() {
        // 1. state class 정의
        sealed class LoginState {
            object Idle : LoginState()

            object Loading : LoginState()

            object Success : LoginState()

            data object BadRequest : LoginState()

            data object Unauthorized : LoginState()

            data object NetworkError : LoginState()

            data object Error : LoginState()
        }

        sealed class RegisterState {
            object Idle : RegisterState()

            object Loading : RegisterState()

            data class Success(
                val id: Int,
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

        sealed class RefreshState {
            object Idle : RefreshState()

            object Success : RefreshState()

            object Unauthorized : RefreshState()

            data class Error(
                val message: String,
            ) : RefreshState()

            data class NetworkError(
                val message: String,
            ) : RefreshState()
        }

        sealed class LogoutState {
            object Idle : LogoutState()

            data object Loading : LogoutState()

            data object Success : LogoutState()

            data class Error(
                val message: String? = null,
            ) : LogoutState()
        }

        // 2. Private MutableStateFlow
        private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
        private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
        private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
        private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)

        // 3. Public StateFlow (exposed state)
        val isLoggedIn: StateFlow<String?> = tokenRepository.isLoggedIn
        val isSessionLoaded: StateFlow<Boolean> = tokenRepository.isSessionLoaded
        val loginState = _loginState.asStateFlow()
        val registerState = _registerState.asStateFlow()
        val refreshState = _refreshState.asStateFlow()
        val logoutState = _logoutState.asStateFlow()

        // 4. Public functions
        fun login(
            username: String,
            password: String,
        ) {
            viewModelScope.launch {
                // TokenRepository에 비즈니스 로직 위임
                when (val result = tokenRepository.login(username, password)) {
                    is TokenRepository.LoginResult.Success -> {
                        sessionExpirationManager.resetSessionExpiration()
                        _loginState.value = LoginState.Success
                    }
                    is TokenRepository.LoginResult.BadRequest -> {
                        _loginState.value = LoginState.BadRequest
                    }
                    is TokenRepository.LoginResult.Unauthorized -> {
                        _loginState.value = LoginState.Unauthorized
                    }
                    is TokenRepository.LoginResult.NetworkError -> {
                        _loginState.value = LoginState.NetworkError
                    }
                    is TokenRepository.LoginResult.Error -> {
                        _loginState.value = LoginState.Error
                    }
                }
            }
        }

        fun resetLoginState() {
            _loginState.value = LoginState.Idle
        }

        fun register(
            email: String,
            username: String,
            password: String,
        ) {
            viewModelScope.launch {
                // TokenRepository에 비즈니스 로직 위임
                when (val result = tokenRepository.register(email, username, password)) {
                    is TokenRepository.RegisterResult.Success -> {
                        _registerState.value = RegisterState.Success(result.userId)
                    }
                    is TokenRepository.RegisterResult.BadRequest -> {
                        _registerState.value = RegisterState.BadRequest(result.message)
                    }
                    is TokenRepository.RegisterResult.Conflict -> {
                        _registerState.value = RegisterState.Conflict(result.message)
                    }
                    is TokenRepository.RegisterResult.NetworkError -> {
                        _registerState.value = RegisterState.NetworkError(result.message)
                    }
                    is TokenRepository.RegisterResult.Error -> {
                        _registerState.value = RegisterState.Error(result.message)
                    }
                }
            }
        }

        fun resetRegisterState() {
            _registerState.value = RegisterState.Idle
        }

        fun refreshTokens() {
            viewModelScope.launch {
                // TokenRepository에 비즈니스 로직 위임
                when (val result = tokenRepository.refreshTokens()) {
                    is TokenRepository.RefreshResult.Success -> {
                        _refreshState.value = RefreshState.Success
                    }
                    is TokenRepository.RefreshResult.Unauthorized -> {
                        _refreshState.value = RefreshState.Unauthorized
                    }
                    is TokenRepository.RefreshResult.NetworkError -> {
                        _refreshState.value = RefreshState.NetworkError(result.message)
                    }
                    is TokenRepository.RefreshResult.Error -> {
                        _refreshState.value = RefreshState.Error(result.message)
                    }
                }
            }
        }

        fun resetRefreshState() {
            _refreshState.value = RefreshState.Idle
        }

        fun logout() {
            viewModelScope.launch {
                // TokenRepository에 비즈니스 로직 위임
                tokenRepository.logout()
                _logoutState.value = LogoutState.Success
            }
        }

        fun resetLogoutState() {
            _logoutState.value = LogoutState.Idle
        }
    }
