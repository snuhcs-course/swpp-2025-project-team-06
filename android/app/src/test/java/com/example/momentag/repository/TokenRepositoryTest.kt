package com.example.momentag.repository

import com.example.momentag.data.SessionStore
import com.example.momentag.model.LoginResponse
import com.example.momentag.model.RefreshResponse
import com.example.momentag.model.RegisterResponse
import com.example.momentag.network.ApiService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TokenRepositoryTest {
    private lateinit var repository: TokenRepository
    private lateinit var apiService: ApiService
    private lateinit var sessionStore: SessionStore

    // StateFlows for SessionStore
    private lateinit var accessTokenFlow: MutableStateFlow<String?>
    private lateinit var refreshTokenFlow: MutableStateFlow<String?>
    private lateinit var isLoadedFlow: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        apiService = mockk()
        sessionStore = mockk(relaxed = true)

        // Initialize StateFlows
        accessTokenFlow = MutableStateFlow(null)
        refreshTokenFlow = MutableStateFlow(null)
        isLoadedFlow = MutableStateFlow(true)

        // Mock SessionStore StateFlows
        every { sessionStore.accessTokenFlow } returns accessTokenFlow
        every { sessionStore.refreshTokenFlow } returns refreshTokenFlow
        every { sessionStore.isLoaded } returns isLoadedFlow

        repository = TokenRepository(apiService, sessionStore)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Helper Functions ==========

    private fun createLoginResponse(
        accessToken: String = "access_token_123",
        refreshToken: String = "refresh_token_456",
    ) = LoginResponse(
        access_token = accessToken,
        refresh_token = refreshToken,
    )

    private fun createRegisterResponse(userId: Int = 1) =
        RegisterResponse(
            id = userId,
        )

    private fun createRefreshResponse(accessToken: String = "new_access_token_789") =
        RefreshResponse(
            access_token = accessToken,
        )

    // ========== login Tests ==========

    @Test
    fun `login returns Success and saves tokens on successful response`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            val loginResponse = createLoginResponse()
            coEvery { apiService.login(any()) } returns Response.success(loginResponse)

            // When
            val result = repository.login(username, password)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Success)
            coVerify {
                apiService.login(match { it.username == username && it.password == password })
                sessionStore.saveTokens(loginResponse.access_token, loginResponse.refresh_token)
            }
        }

    @Test
    fun `login returns BadRequest on 400`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            coEvery { apiService.login(any()) } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.login(username, password)

            // Then
            assertTrue(result is TokenRepository.LoginResult.BadRequest)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    @Test
    fun `login returns Unauthorized on 401`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "wrongpassword"
            coEvery { apiService.login(any()) } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.login(username, password)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Unauthorized)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    @Test
    fun `login returns Error on 500`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            coEvery { apiService.login(any()) } returns Response.error(500, "".toResponseBody())

            // When
            val result = repository.login(username, password)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Error)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    @Test
    fun `login returns Error when response body is null`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            coEvery { apiService.login(any()) } returns Response.success(null)

            // When
            val result = repository.login(username, password)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Error)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    @Test
    fun `login returns NetworkError on IOException`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            coEvery { apiService.login(any()) } throws IOException("Network error")

            // When
            val result = repository.login(username, password)

            // Then
            assertTrue(result is TokenRepository.LoginResult.NetworkError)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    @Test
    fun `login returns Error on generic Exception`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            coEvery { apiService.login(any()) } throws RuntimeException("Unknown error")

            // When
            val result = repository.login(username, password)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Error)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    // ========== register Tests ==========

    @Test
    fun `register returns Success with userId on successful response`() =
        runTest {
            // Given
            val username = "newuser"
            val password = "password123"
            val registerResponse = createRegisterResponse(userId = 42)
            coEvery { apiService.register(any()) } returns Response.success(registerResponse)

            // When
            val result = repository.register(username, password)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Success)
            assertEquals(42, (result as TokenRepository.RegisterResult.Success).userId)
            coVerify {
                apiService.register(match { it.username == username && it.password == password })
            }
        }

    @Test
    fun `register returns BadRequest on 400`() =
        runTest {
            // Given
            val username = "newuser"
            val password = "short"
            coEvery { apiService.register(any()) } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.register(username, password)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.BadRequest)
            assertEquals("Request form mismatch", (result as TokenRepository.RegisterResult.BadRequest).message)
        }

    @Test
    fun `register returns Conflict on 409`() =
        runTest {
            // Given
            val username = "existinguser"
            val password = "password123"
            coEvery { apiService.register(any()) } returns Response.error(409, "".toResponseBody())

            // When
            val result = repository.register(username, password)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Conflict)
            assertEquals("Username already in use", (result as TokenRepository.RegisterResult.Conflict).message)
        }

    @Test
    fun `register returns Error on 500`() =
        runTest {
            // Given
            val username = "newuser"
            val password = "password123"
            coEvery { apiService.register(any()) } returns Response.error(500, "".toResponseBody())

            // When
            val result = repository.register(username, password)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Error)
            assertTrue((result as TokenRepository.RegisterResult.Error).message.contains("Unexpected error"))
        }

    @Test
    fun `register returns Error when response body is null`() =
        runTest {
            // Given
            val username = "newuser"
            val password = "password123"
            coEvery { apiService.register(any()) } returns Response.success(null)

            // When
            val result = repository.register(username, password)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Error)
        }

    @Test
    fun `register returns NetworkError on IOException`() =
        runTest {
            // Given
            val username = "newuser"
            val password = "password123"
            coEvery { apiService.register(any()) } throws IOException("Network error")

            // When
            val result = repository.register(username, password)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.NetworkError)
            assertEquals("Network error", (result as TokenRepository.RegisterResult.NetworkError).message)
        }

    @Test
    fun `register returns Error on generic Exception`() =
        runTest {
            // Given
            val username = "newuser"
            val password = "password123"
            coEvery { apiService.register(any()) } throws RuntimeException("Unknown error")

            // When
            val result = repository.register(username, password)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Error)
            assertTrue((result as TokenRepository.RegisterResult.Error).message.contains("Unknown error"))
        }

    // ========== refreshTokens Tests ==========

    @Test
    fun `refreshTokens returns Success and saves new access token on successful response`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            val refreshResponse = createRefreshResponse("new_access_token_789")
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.refreshToken(any()) } returns Response.success(refreshResponse)

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Success)
            coVerify {
                apiService.refreshToken(match { it.refresh_token == refreshToken })
                sessionStore.saveTokens(refreshResponse.access_token, refreshToken)
            }
        }

    @Test
    fun `refreshTokens returns Unauthorized when refresh token is null`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns null

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)
            coVerify(exactly = 0) { apiService.refreshToken(any()) }
        }

    @Test
    fun `refreshTokens returns Unauthorized and clears tokens on 400`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.refreshToken(any()) } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `refreshTokens returns Unauthorized and clears tokens on 401`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.refreshToken(any()) } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `refreshTokens returns Error on 500`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.refreshToken(any()) } returns Response.error(500, "".toResponseBody())

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Error)
            assertTrue((result as TokenRepository.RefreshResult.Error).message.contains("Unexpected error"))
        }

    @Test
    fun `refreshTokens returns Error when response body is null`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.refreshToken(any()) } returns Response.success(null)

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Error)
        }

    @Test
    fun `refreshTokens returns NetworkError on IOException`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.refreshToken(any()) } throws IOException("Network error")

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.NetworkError)
            assertTrue((result as TokenRepository.RefreshResult.NetworkError).message.contains("Network error"))
        }

    @Test
    fun `refreshTokens returns Error on generic Exception`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.refreshToken(any()) } throws RuntimeException("Unknown error")

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Error)
            assertTrue((result as TokenRepository.RefreshResult.Error).message.contains("Unknown error"))
        }

    // ========== logout Tests ==========

    @Test
    fun `logout calls API and clears tokens on success`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.logout(any()) } returns Response.success(Unit)

            // When
            repository.logout()

            // Then
            coVerify {
                apiService.logout(match { it.refresh_token == refreshToken })
                sessionStore.clearTokens()
            }
        }

    @Test
    fun `logout clears tokens even when API call fails`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.logout(any()) } returns Response.error(500, "".toResponseBody())

            // When
            repository.logout()

            // Then
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `logout clears tokens even when API throws exception`() =
        runTest {
            // Given
            val refreshToken = "refresh_token_456"
            every { sessionStore.getRefreshToken() } returns refreshToken
            coEvery { apiService.logout(any()) } throws IOException("Network error")

            // When
            repository.logout()

            // Then
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `logout clears tokens when refresh token is null`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns null

            // When
            repository.logout()

            // Then
            coVerify(exactly = 0) { apiService.logout(any()) }
            coVerify { sessionStore.clearTokens() }
        }

    // ========== getCurrentAccessToken Tests ==========

    @Test
    fun `getCurrentAccessToken returns access token from sessionStore`() {
        // Given
        val accessToken = "access_token_123"
        every { sessionStore.getAccessToken() } returns accessToken

        // When
        val result = repository.getCurrentAccessToken()

        // Then
        assertEquals(accessToken, result)
    }

    @Test
    fun `getCurrentAccessToken returns null when no token exists`() {
        // Given
        every { sessionStore.getAccessToken() } returns null

        // When
        val result = repository.getCurrentAccessToken()

        // Then
        assertNull(result)
    }

    // ========== getCurrentRefreshToken Tests ==========

    @Test
    fun `getCurrentRefreshToken returns refresh token from sessionStore`() {
        // Given
        val refreshToken = "refresh_token_456"
        every { sessionStore.getRefreshToken() } returns refreshToken

        // When
        val result = repository.getCurrentRefreshToken()

        // Then
        assertEquals(refreshToken, result)
    }

    @Test
    fun `getCurrentRefreshToken returns null when no token exists`() {
        // Given
        every { sessionStore.getRefreshToken() } returns null

        // When
        val result = repository.getCurrentRefreshToken()

        // Then
        assertNull(result)
    }

    // ========== StateFlow Tests ==========

    @Test
    fun `isLoggedIn flow reflects sessionStore accessTokenFlow`() =
        runTest {
            // Given
            val token = "access_token_123"

            // When
            accessTokenFlow.value = token

            // Then
            assertEquals(token, repository.isLoggedIn.value)
        }

    @Test
    fun `isLoggedIn flow is null when no token exists`() =
        runTest {
            // Given
            accessTokenFlow.value = null

            // When/Then
            assertNull(repository.isLoggedIn.value)
        }

    @Test
    fun `isSessionLoaded flow reflects sessionStore isLoaded`() =
        runTest {
            // Given
            isLoadedFlow.value = true

            // Then
            assertTrue(repository.isSessionLoaded.value)
        }

    @Test
    fun `isSessionLoaded flow updates when sessionStore changes`() =
        runTest {
            // Given
            isLoadedFlow.value = false

            // When
            val result = repository.isSessionLoaded.value

            // Then
            assertEquals(false, result)
        }

    // ========== Integration Tests ==========

    @Test
    fun `workflow - register then login`() =
        runTest {
            // Given - register
            val username = "newuser"
            val password = "password123"
            val registerResponse = createRegisterResponse(userId = 1)
            coEvery { apiService.register(any()) } returns Response.success(registerResponse)

            // When - register
            val registerResult = repository.register(username, password)
            assertTrue(registerResult is TokenRepository.RegisterResult.Success)

            // Given - login
            val loginResponse = createLoginResponse()
            coEvery { apiService.login(any()) } returns Response.success(loginResponse)

            // When - login
            val loginResult = repository.login(username, password)

            // Then
            assertTrue(loginResult is TokenRepository.LoginResult.Success)
            coVerify { sessionStore.saveTokens(loginResponse.access_token, loginResponse.refresh_token) }
        }

    @Test
    fun `workflow - login then refresh then logout`() =
        runTest {
            // Given - login
            val loginResponse = createLoginResponse()
            coEvery { apiService.login(any()) } returns Response.success(loginResponse)

            // When - login
            val loginResult = repository.login("user", "pass")
            assertTrue(loginResult is TokenRepository.LoginResult.Success)

            // Given - refresh
            every { sessionStore.getRefreshToken() } returns loginResponse.refresh_token
            val refreshResponse = createRefreshResponse()
            coEvery { apiService.refreshToken(any()) } returns Response.success(refreshResponse)

            // When - refresh
            val refreshResult = repository.refreshTokens()
            assertTrue(refreshResult is TokenRepository.RefreshResult.Success)

            // Given - logout
            coEvery { apiService.logout(any()) } returns Response.success(Unit)

            // When - logout
            repository.logout()

            // Then
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `workflow - failed refresh triggers logout`() =
        runTest {
            // Given - login successful
            val loginResponse = createLoginResponse()
            coEvery { apiService.login(any()) } returns Response.success(loginResponse)
            repository.login("user", "pass")

            // Given - refresh fails with 401
            every { sessionStore.getRefreshToken() } returns loginResponse.refresh_token
            coEvery { apiService.refreshToken(any()) } returns Response.error(401, "".toResponseBody())

            // When - refresh
            val refreshResult = repository.refreshTokens()

            // Then - tokens are cleared
            assertTrue(refreshResult is TokenRepository.RefreshResult.Unauthorized)
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `all login error scenarios return correct result types`() =
        runTest {
            // 400 BadRequest
            coEvery { apiService.login(any()) } returns Response.error(400, "".toResponseBody())
            var result = repository.login("user", "pass")
            assertTrue(result is TokenRepository.LoginResult.BadRequest)

            // 401 Unauthorized
            coEvery { apiService.login(any()) } returns Response.error(401, "".toResponseBody())
            result = repository.login("user", "pass")
            assertTrue(result is TokenRepository.LoginResult.Unauthorized)

            // 500 Error
            coEvery { apiService.login(any()) } returns Response.error(500, "".toResponseBody())
            result = repository.login("user", "pass")
            assertTrue(result is TokenRepository.LoginResult.Error)

            // IOException NetworkError
            coEvery { apiService.login(any()) } throws IOException("Network error")
            result = repository.login("user", "pass")
            assertTrue(result is TokenRepository.LoginResult.NetworkError)

            // Generic Exception Error
            coEvery { apiService.login(any()) } throws RuntimeException("Unknown")
            result = repository.login("user", "pass")
            assertTrue(result is TokenRepository.LoginResult.Error)
        }

    @Test
    fun `all register error scenarios return correct result types`() =
        runTest {
            // 400 BadRequest
            coEvery { apiService.register(any()) } returns Response.error(400, "".toResponseBody())
            var result = repository.register("user", "pass")
            assertTrue(result is TokenRepository.RegisterResult.BadRequest)

            // 409 Conflict
            coEvery { apiService.register(any()) } returns Response.error(409, "".toResponseBody())
            result = repository.register("user", "pass")
            assertTrue(result is TokenRepository.RegisterResult.Conflict)

            // 500 Error
            coEvery { apiService.register(any()) } returns Response.error(500, "".toResponseBody())
            result = repository.register("user", "pass")
            assertTrue(result is TokenRepository.RegisterResult.Error)

            // IOException NetworkError
            coEvery { apiService.register(any()) } throws IOException("Network error")
            result = repository.register("user", "pass")
            assertTrue(result is TokenRepository.RegisterResult.NetworkError)

            // Generic Exception Error
            coEvery { apiService.register(any()) } throws RuntimeException("Unknown")
            result = repository.register("user", "pass")
            assertTrue(result is TokenRepository.RegisterResult.Error)
        }

    @Test
    fun `all refresh error scenarios return correct result types`() =
        runTest {
            // No refresh token - Unauthorized
            every { sessionStore.getRefreshToken() } returns null
            var result = repository.refreshTokens()
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)

            // 400 Unauthorized (and clears tokens)
            every { sessionStore.getRefreshToken() } returns "token"
            coEvery { apiService.refreshToken(any()) } returns Response.error(400, "".toResponseBody())
            result = repository.refreshTokens()
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)

            // 401 Unauthorized (and clears tokens)
            coEvery { apiService.refreshToken(any()) } returns Response.error(401, "".toResponseBody())
            result = repository.refreshTokens()
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)

            // 500 Error
            coEvery { apiService.refreshToken(any()) } returns Response.error(500, "".toResponseBody())
            result = repository.refreshTokens()
            assertTrue(result is TokenRepository.RefreshResult.Error)

            // IOException NetworkError
            coEvery { apiService.refreshToken(any()) } throws IOException("Network error")
            result = repository.refreshTokens()
            assertTrue(result is TokenRepository.RefreshResult.NetworkError)

            // Generic Exception Error
            coEvery { apiService.refreshToken(any()) } throws RuntimeException("Unknown")
            result = repository.refreshTokens()
            assertTrue(result is TokenRepository.RefreshResult.Error)
        }
}
