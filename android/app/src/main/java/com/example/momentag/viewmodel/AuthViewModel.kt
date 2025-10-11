package com.example.momentag.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.data.SessionManager
import com.example.momentag.model.LoginRegisterRequest
import com.example.momentag.model.LoginState
import com.example.momentag.model.LogoutState
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RefreshState
import com.example.momentag.model.RegisterState
import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class AuthViewModel(
    private val authRepository: RemoteRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    // login
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val request = LoginRegisterRequest(username, password)
                val response = authRepository.login(request)

                when {
                    response.isSuccessful && response.body() != null -> { // 200 OK
                        _loginState.value = LoginState.Success
                        val accessToken = response.body()!!.access_token
                        val refreshToken = response.body()!!.refresh_token
                        sessionManager.saveTokens(accessToken, refreshToken)
                    }

                    response.code() == 400 -> {
                        _loginState.value = LoginState.BadRequest("Request form mismatch or No such user")
                    }

                    response.code() == 401 -> {
                        _loginState.value = LoginState.Unauthorized ("Wrong username or password")
                    }

                    else -> {
                        _loginState.value = LoginState.Error("Unexpected error: ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                _loginState.value = LoginState.NetworkError("Network error")
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Unknown error")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }


    // register
    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState = _registerState.asStateFlow()

    fun register(username: String, password: String) {
        viewModelScope.launch {
            try {
                val request = LoginRegisterRequest(username, password)
                val response = authRepository.register(request)

                when {
                    response.isSuccessful -> { // 201 Created
                        _registerState.value = RegisterState.Success(response.body()!!.id )
                    }

                    response.code() == 400 -> {
                        _registerState.value = RegisterState.BadRequest("Request form mismatch")
                    }

                    response.code() == 409 -> {
                        _registerState.value = RegisterState.Conflict("Username already in use")
                    }

                    else -> {
                        _registerState.value = RegisterState.Error("Unexpected error: ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                _registerState.value = RegisterState.NetworkError("Network error")
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error("Unknown error")
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
            val refreshToken = sessionManager.getRefreshToken()
            if (refreshToken == null) {
                _refreshState.value = RefreshState.Unauthorized
                return@launch
            }

            try {
                val request = RefreshRequest(refreshToken)
                val response = authRepository.refreshToken(request)

                when {
                    response.isSuccessful && response.body() != null -> {
                        // 200 OK: restore new access token
                        val newAccessToken = response.body()!!.access_token
                        sessionManager.saveTokens(newAccessToken, refreshToken)
                        _refreshState.value = RefreshState.Success
                    }

                    response.code() == 400 -> {
                        // Bad Request
                        sessionManager.clearTokens()
                        _refreshState.value = RefreshState.Success
                    }

                    response.code() == 401 -> {
                        // Unauthorized: expired refresh token
                        sessionManager.clearTokens()
                        _refreshState.value = RefreshState.Unauthorized
                    }

                    else -> {
                        // else
                        _refreshState.value =
                            RefreshState.Error("Unexpected error: ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                _refreshState.value =
                    RefreshState.NetworkError("Network error while refreshing tokens")
            } catch (e: Exception) {
                _refreshState.value = RefreshState.Error("Unknown error while refreshing tokens")
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
            val refreshToken = sessionManager.getRefreshToken()
            if (refreshToken == null) {
                return@launch
            }

            try {
                val request = RefreshRequest(refreshToken)
                val response = authRepository.logout(request)

                when (response.code()) {
                    204 -> {
                        // 204 No Content
                    }
                    400, 401, 403 -> {
                        // BadRequest / Unauthorized / Forbidden
                    }
                    else -> {
                        // else
                    }
                }
            } catch (e: Exception) {
                // else
            }
            sessionManager.clearTokens()
            _logoutState.value = LogoutState.Success
        }
    }

    fun resetLogoutState() {
        _logoutState.value = LogoutState.Idle
    }
}