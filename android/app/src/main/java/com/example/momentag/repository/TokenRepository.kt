package com.example.momentag.repository

import com.example.momentag.data.SessionStore
import com.example.momentag.model.LoginRequest
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RegisterRequest
import com.example.momentag.network.ApiService
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TokenRepository
 *
 * 역할: 로그인/로그아웃/리프레시 등 세션 비즈니스 로직 담당
 * - 인증 관련 모든 비즈니스 로직을 캡슐화
 * - SessionStore를 사용하여 토큰 저장/조회
 * - API 호출 후 결과를 sealed class로 반환
 * - UI 계층은 이 Repository의 결과만 구독
 */
@Singleton
class TokenRepository
    @Inject
    constructor(
        private val apiService: ApiService,
        private val sessionStore: SessionStore,
    ) {
        /**
         * 로그인 상태를 관찰할 수 있는 Flow
         * accessToken이 있으면 로그인 상태로 간주
         */
        val isLoggedIn: StateFlow<String?> = sessionStore.accessTokenFlow

        /**
         * 세션 데이터 로딩 완료 여부
         */
        val isSessionLoaded: StateFlow<Boolean> = sessionStore.isLoaded

        /**
         * 로그인 결과를 나타내는 Sealed Class
         */
        sealed class LoginResult {
            data object Success : LoginResult()

            data object BadRequest : LoginResult()

            data object Unauthorized : LoginResult()

            data object NetworkError : LoginResult()

            data object Error : LoginResult()
        }

        /**
         * 회원가입 결과를 나타내는 Sealed Class
         */
        sealed class RegisterResult {
            data class Success(
                val userId: Int,
            ) : RegisterResult()

            data class BadRequest(
                val message: String,
            ) : RegisterResult()

            data class NetworkError(
                val message: String,
            ) : RegisterResult()

            data class Error(
                val message: String,
            ) : RegisterResult()
        }

        /**
         * 토큰 리프레시 결과를 나타내는 Sealed Class
         */
        sealed class RefreshResult {
            data object Success : RefreshResult()

            data object Unauthorized : RefreshResult()

            data class NetworkError(
                val message: String,
            ) : RefreshResult()

            data class Error(
                val message: String,
            ) : RefreshResult()
        }

        /**
         * 로그인 수행
         * @param username 사용자명
         * @param password 비밀번호
         * @return LoginResult
         */
        suspend fun login(
            username: String,
            password: String,
        ): LoginResult =
            try {
                val request = LoginRequest(username, password)
                val response = apiService.login(request)

                when {
                    response.isSuccessful && response.body() != null -> {
                        val accessToken = response.body()!!.access_token
                        val refreshToken = response.body()!!.refresh_token
                        sessionStore.saveTokens(accessToken, refreshToken)
                        LoginResult.Success
                    }

                    response.code() == 400 -> {
                        LoginResult.BadRequest
                    }

                    response.code() == 401 -> {
                        LoginResult.Unauthorized
                    }

                    else -> {
                        LoginResult.Error
                    }
                }
            } catch (e: IOException) {
                LoginResult.NetworkError
            } catch (e: Exception) {
                LoginResult.Error
            }

        /**
         * 회원가입 수행
         * @param username 사용자명
         * @param password 비밀번호
         * @return RegisterResult
         */
        suspend fun register(
            username: String,
            password: String,
        ): RegisterResult =
            try {
                val request = RegisterRequest(username, password)
                val response = apiService.register(request)

                when {
                    response.isSuccessful && response.body() != null -> {
                        val userId = response.body()!!.id
                        RegisterResult.Success(userId)
                    }

                    response.code() == 400 -> {
                        RegisterResult.BadRequest("Request form mismatch")
                    }

                    else -> {
                        RegisterResult.Error("Unexpected error: ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                RegisterResult.NetworkError("Network error")
            } catch (e: Exception) {
                RegisterResult.Error("Unknown error: ${e.message}")
            }

        /**
         * 토큰 리프레시 수행
         * @return RefreshResult
         */
        suspend fun refreshTokens(): RefreshResult {
            val refreshToken = sessionStore.getRefreshToken()
            if (refreshToken == null) {
                return RefreshResult.Unauthorized
            }

            return try {
                val request = RefreshRequest(refreshToken)
                val response = apiService.refreshToken(request)

                when {
                    response.isSuccessful && response.body() != null -> {
                        val newAccessToken = response.body()!!.access_token
                        sessionStore.saveTokens(newAccessToken, refreshToken)
                        RefreshResult.Success
                    }

                    response.code() == 400 -> {
                        sessionStore.clearTokens()
                        RefreshResult.Unauthorized
                    }

                    response.code() == 401 -> {
                        sessionStore.clearTokens()
                        RefreshResult.Unauthorized
                    }

                    else -> {
                        RefreshResult.Error("Unexpected error: ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                RefreshResult.NetworkError("Network error while refreshing tokens")
            } catch (e: Exception) {
                RefreshResult.Error("Unknown error while refreshing tokens: ${e.message}")
            }
        }

        /**
         * 로그아웃 수행
         * 서버에 로그아웃 요청 후, 로컬 토큰 삭제
         */
        suspend fun logout() {
            val refreshToken = sessionStore.getRefreshToken()
            if (refreshToken != null) {
                try {
                    val request = RefreshRequest(refreshToken)
                    apiService.logout(request)
                    // 서버 응답에 관계없이 로컬 토큰 삭제
                } catch (e: Exception) {
                    // 네트워크 오류가 발생해도 로컬 토큰 삭제
                }
            }
            sessionStore.clearTokens()
        }

        /**
         * 현재 Access Token 조회
         * (주로 테스트나 디버깅 용도)
         */
        fun getCurrentAccessToken(): String? = sessionStore.getAccessToken()

        /**
         * 현재 Refresh Token 조회
         * (주로 테스트나 디버깅 용도)
         */
        fun getCurrentRefreshToken(): String? = sessionStore.getRefreshToken()
    }
