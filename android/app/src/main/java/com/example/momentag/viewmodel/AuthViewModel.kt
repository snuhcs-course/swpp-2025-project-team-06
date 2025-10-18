package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.LoginState
import com.example.momentag.model.LogoutState
import com.example.momentag.model.RefreshState
import com.example.momentag.model.RegisterRequest
import com.example.momentag.model.RegisterState
import com.example.momentag.repository.TokenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
class AuthViewModel(
    private val tokenRepository: TokenRepository,
) : ViewModel() {
    /**
     * 로그인 상태 관찰
     * TokenRepository의 isLoggedIn Flow를 노출
     */
    val isLoggedIn: StateFlow<String?> = tokenRepository.isLoggedIn

    /**
     * 세션 로딩 상태 관찰
     */
    val isSessionLoaded: StateFlow<Boolean> = tokenRepository.isSessionLoaded

    // login
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    fun login(
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            // TokenRepository에 비즈니스 로직 위임
            when (val result = tokenRepository.login(username, password)) {
                is TokenRepository.LoginResult.Success -> {
                    _loginState.value = LoginState.Success
                }
                is TokenRepository.LoginResult.BadRequest -> {
                    _loginState.value = LoginState.BadRequest(result.message)
                }
                is TokenRepository.LoginResult.Unauthorized -> {
                    _loginState.value = LoginState.Unauthorized(result.message)
                }
                is TokenRepository.LoginResult.NetworkError -> {
                    _loginState.value = LoginState.NetworkError(result.message)
                }
                is TokenRepository.LoginResult.Error -> {
                    _loginState.value = LoginState.Error(result.message)
                }
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    // register
    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState = _registerState.asStateFlow()

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

    // refresh tokens
    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refreshState = _refreshState.asStateFlow()

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

    // logout
    private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val logoutState = _logoutState.asStateFlow()

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
