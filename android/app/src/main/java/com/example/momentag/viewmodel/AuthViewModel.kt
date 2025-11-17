package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.LoginState
import com.example.momentag.model.LogoutState
import com.example.momentag.model.RefreshState
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
    // 1. Private MutableStateFlow
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)

    // 2. Public StateFlow (exposed state)
    /**
     * 로그인 상태 관찰
     * TokenRepository의 isLoggedIn Flow를 노출
     */
    val isLoggedIn: StateFlow<String?> = tokenRepository.isLoggedIn

    /**
     * 세션 로딩 상태 관찰
     */
    val isSessionLoaded: StateFlow<Boolean> = tokenRepository.isSessionLoaded

    val loginState = _loginState.asStateFlow()
    val registerState = _registerState.asStateFlow()
    val refreshState = _refreshState.asStateFlow()
    val logoutState = _logoutState.asStateFlow()

    // 3. Public functions
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
