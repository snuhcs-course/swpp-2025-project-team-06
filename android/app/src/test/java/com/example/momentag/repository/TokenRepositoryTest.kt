package com.example.momentag.repository

import com.example.momentag.data.SessionStore
import com.example.momentag.model.LoginRequest
import com.example.momentag.model.LoginResponse
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RefreshResponse
import com.example.momentag.model.RegisterResponse
import com.example.momentag.network.ApiService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class TokenRepositoryTest {
    private lateinit var repository: TokenRepository
    private lateinit var apiService: ApiService
    private lateinit var sessionStore: SessionStore

    private val testAccessToken = "test_access_token"
    private val testRefreshToken = "test_refresh_token"
    private val testUsername = "testuser"
    private val testPassword = "testpass"
    private val testEmail = "test@example.com"

    @Before
    fun setUp() {
        apiService = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)

        // SessionStore StateFlow setup
        every { sessionStore.accessTokenFlow } returns MutableStateFlow(null)
        every { sessionStore.isLoaded } returns MutableStateFlow(true)

        repository = TokenRepository(apiService, sessionStore)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ==================== Login Tests ====================

    @Test
    fun `login should return Success when API returns 200 with tokens`() =
        runTest {
            // Given
            val loginResponse = LoginResponse(testAccessToken, testRefreshToken)
            coEvery {
                apiService.login(LoginRequest(testUsername, testPassword))
            } returns Response.success(loginResponse)

            // When
            val result = repository.login(testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Success)
            coVerify { sessionStore.saveTokens(testAccessToken, testRefreshToken) }
        }

    @Test
    fun `login should return BadRequest when API returns 400`() =
        runTest {
            // Given
            coEvery {
                apiService.login(any())
            } returns Response.error(400, mockk(relaxed = true))

            // When
            val result = repository.login(testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.LoginResult.BadRequest)
            assertEquals("Request form mismatch or No such user", (result as TokenRepository.LoginResult.BadRequest).message)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    @Test
    fun `login should return Unauthorized when API returns 401`() =
        runTest {
            // Given
            coEvery {
                apiService.login(any())
            } returns Response.error(401, mockk(relaxed = true))

            // When
            val result = repository.login(testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Unauthorized)
            assertEquals("Wrong username or password", (result as TokenRepository.LoginResult.Unauthorized).message)
            coVerify(exactly = 0) { sessionStore.saveTokens(any(), any()) }
        }

    @Test
    fun `login should return Error when API returns 500`() =
        runTest {
            // Given
            coEvery {
                apiService.login(any())
            } returns Response.error(500, mockk(relaxed = true))

            // When
            val result = repository.login(testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Error)
            assertTrue((result as TokenRepository.LoginResult.Error).message.contains("Unexpected error: 500"))
        }

    @Test
    fun `login should return NetworkError when IOException occurs`() =
        runTest {
            // Given
            coEvery {
                apiService.login(any())
            } throws IOException("Network error")

            // When
            val result = repository.login(testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.LoginResult.NetworkError)
            assertEquals("Network error", (result as TokenRepository.LoginResult.NetworkError).message)
        }

    @Test
    fun `login should return Error when unknown Exception occurs`() =
        runTest {
            // Given
            coEvery {
                apiService.login(any())
            } throws RuntimeException("Unknown error")

            // When
            val result = repository.login(testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.LoginResult.Error)
            assertTrue((result as TokenRepository.LoginResult.Error).message.contains("Unknown error"))
        }

    @Test
    fun `login should send correct LoginRequest to API`() =
        runTest {
            // Given
            val loginResponse = LoginResponse(testAccessToken, testRefreshToken)
            coEvery { apiService.login(any()) } returns Response.success(loginResponse)

            // When
            repository.login(testUsername, testPassword)

            // Then
            coVerify {
                apiService.login(
                    match {
                        it.username == testUsername && it.password == testPassword
                    },
                )
            }
        }

    // ==================== Register Tests ====================

    @Test
    fun `register should return Success with userId when API returns 200`() =
        runTest {
            // Given
            val userId = 12345
            val registerResponse = RegisterResponse(userId)
            coEvery {
                apiService.register(any())
            } returns Response.success(registerResponse)

            // When
            val result = repository.register(testEmail, testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Success)
            assertEquals(userId, (result as TokenRepository.RegisterResult.Success).userId)
        }

    @Test
    fun `register should return BadRequest when API returns 400`() =
        runTest {
            // Given
            coEvery {
                apiService.register(any())
            } returns Response.error(400, mockk(relaxed = true))

            // When
            val result = repository.register(testEmail, testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.BadRequest)
            assertEquals("Request form mismatch", (result as TokenRepository.RegisterResult.BadRequest).message)
        }

    @Test
    fun `register should return Conflict when API returns 409`() =
        runTest {
            // Given
            coEvery {
                apiService.register(any())
            } returns Response.error(409, mockk(relaxed = true))

            // When
            val result = repository.register(testEmail, testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Conflict)
            assertEquals("Email already in use", (result as TokenRepository.RegisterResult.Conflict).message)
        }

    @Test
    fun `register should return Error when API returns 500`() =
        runTest {
            // Given
            coEvery {
                apiService.register(any())
            } returns Response.error(500, mockk(relaxed = true))

            // When
            val result = repository.register(testEmail, testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Error)
            assertTrue((result as TokenRepository.RegisterResult.Error).message.contains("Unexpected error: 500"))
        }

    @Test
    fun `register should return NetworkError when IOException occurs`() =
        runTest {
            // Given
            coEvery {
                apiService.register(any())
            } throws IOException("Network error")

            // When
            val result = repository.register(testEmail, testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.NetworkError)
            assertEquals("Network error", (result as TokenRepository.RegisterResult.NetworkError).message)
        }

    @Test
    fun `register should return Error when unknown Exception occurs`() =
        runTest {
            // Given
            coEvery {
                apiService.register(any())
            } throws RuntimeException("Unknown error")

            // When
            val result = repository.register(testEmail, testUsername, testPassword)

            // Then
            assertTrue(result is TokenRepository.RegisterResult.Error)
            assertTrue((result as TokenRepository.RegisterResult.Error).message.contains("Unknown error"))
        }

    @Test
    fun `register should send correct RegisterRequest to API`() =
        runTest {
            // Given
            val registerResponse = RegisterResponse(1)
            coEvery { apiService.register(any()) } returns Response.success(registerResponse)

            // When
            repository.register(testEmail, testUsername, testPassword)

            // Then
            coVerify {
                apiService.register(
                    match {
                        it.email == testEmail && it.username == testUsername && it.password == testPassword
                    },
                )
            }
        }

    // ==================== RefreshTokens Tests ====================

    @Test
    fun `refreshTokens should return Success when API returns 200 with new token`() =
        runTest {
            // Given
            val newAccessToken = "new_access_token"
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            val refreshResponse = RefreshResponse(newAccessToken)
            coEvery {
                apiService.refreshToken(RefreshRequest(testRefreshToken))
            } returns Response.success(refreshResponse)

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Success)
            coVerify { sessionStore.saveTokens(newAccessToken, testRefreshToken) }
        }

    @Test
    fun `refreshTokens should return Unauthorized when refresh token is null`() =
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
    fun `refreshTokens should return Unauthorized and clear tokens when API returns 400`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery {
                apiService.refreshToken(any())
            } returns Response.error(400, mockk(relaxed = true))

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `refreshTokens should return Unauthorized and clear tokens when API returns 401`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery {
                apiService.refreshToken(any())
            } returns Response.error(401, mockk(relaxed = true))

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Unauthorized)
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `refreshTokens should return Error when API returns 500`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery {
                apiService.refreshToken(any())
            } returns Response.error(500, mockk(relaxed = true))

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Error)
            assertTrue((result as TokenRepository.RefreshResult.Error).message.contains("Unexpected error: 500"))
        }

    @Test
    fun `refreshTokens should return NetworkError when IOException occurs`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery {
                apiService.refreshToken(any())
            } throws IOException("Network error")

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.NetworkError)
            assertTrue((result as TokenRepository.RefreshResult.NetworkError).message.contains("Network error"))
        }

    @Test
    fun `refreshTokens should return Error when unknown Exception occurs`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery {
                apiService.refreshToken(any())
            } throws RuntimeException("Unknown error")

            // When
            val result = repository.refreshTokens()

            // Then
            assertTrue(result is TokenRepository.RefreshResult.Error)
            assertTrue((result as TokenRepository.RefreshResult.Error).message.contains("Unknown error"))
        }

    // ==================== Logout Tests ====================

    @Test
    fun `logout should call API and clear tokens when refresh token exists`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery { apiService.logout(any()) } returns Response.success(Unit)

            // When
            repository.logout()

            // Then
            coVerify { apiService.logout(match { it.refresh_token == testRefreshToken }) }
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `logout should only clear tokens when refresh token is null`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns null

            // When
            repository.logout()

            // Then
            coVerify(exactly = 0) { apiService.logout(any()) }
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `logout should clear tokens even when API call fails`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery { apiService.logout(any()) } throws IOException("Network error")

            // When
            repository.logout()

            // Then
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `logout should clear tokens even when API returns error`() =
        runTest {
            // Given
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery { apiService.logout(any()) } returns Response.error(500, mockk(relaxed = true))

            // When
            repository.logout()

            // Then
            coVerify { sessionStore.clearTokens() }
        }

    // ==================== Token Getter Tests ====================

    @Test
    fun `getCurrentAccessToken should return token from SessionStore`() {
        // Given
        every { sessionStore.getAccessToken() } returns testAccessToken

        // When
        val result = repository.getCurrentAccessToken()

        // Then
        assertEquals(testAccessToken, result)
        verify { sessionStore.getAccessToken() }
    }

    @Test
    fun `getCurrentAccessToken should return null when no token exists`() {
        // Given
        every { sessionStore.getAccessToken() } returns null

        // When
        val result = repository.getCurrentAccessToken()

        // Then
        assertNull(result)
    }

    @Test
    fun `getCurrentRefreshToken should return token from SessionStore`() {
        // Given
        every { sessionStore.getRefreshToken() } returns testRefreshToken

        // When
        val result = repository.getCurrentRefreshToken()

        // Then
        assertEquals(testRefreshToken, result)
        verify { sessionStore.getRefreshToken() }
    }

    @Test
    fun `getCurrentRefreshToken should return null when no token exists`() {
        // Given
        every { sessionStore.getRefreshToken() } returns null

        // When
        val result = repository.getCurrentRefreshToken()

        // Then
        assertNull(result)
    }

    // ==================== StateFlow Tests ====================

    @Test
    fun `isLoggedIn should return accessTokenFlow from SessionStore`() {
        // Given
        val mockFlow = MutableStateFlow("token")
        every { sessionStore.accessTokenFlow } returns mockFlow

        // When
        val newRepository = TokenRepository(apiService, sessionStore)
        val result = newRepository.isLoggedIn

        // Then
        assertEquals(mockFlow, result)
    }

    @Test
    fun `isSessionLoaded should return isLoaded from SessionStore`() {
        // Given
        val mockFlow = MutableStateFlow(true)
        every { sessionStore.isLoaded } returns mockFlow

        // When
        val newRepository = TokenRepository(apiService, sessionStore)
        val result = newRepository.isSessionLoaded

        // Then
        assertEquals(mockFlow, result)
    }

    // ==================== Integration Tests ====================

    @Test
    fun `successful login followed by logout should clear tokens`() =
        runTest {
            // Given
            val loginResponse = LoginResponse(testAccessToken, testRefreshToken)
            coEvery { apiService.login(any()) } returns Response.success(loginResponse)
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            coEvery { apiService.logout(any()) } returns Response.success(Unit)

            // When
            val loginResult = repository.login(testUsername, testPassword)
            repository.logout()

            // Then
            assertTrue(loginResult is TokenRepository.LoginResult.Success)
            coVerify { sessionStore.saveTokens(testAccessToken, testRefreshToken) }
            coVerify { sessionStore.clearTokens() }
        }

    @Test
    fun `successful register followed by login should work correctly`() =
        runTest {
            // Given
            val registerResponse = RegisterResponse(1)
            coEvery { apiService.register(any()) } returns Response.success(registerResponse)
            val loginResponse = LoginResponse(testAccessToken, testRefreshToken)
            coEvery { apiService.login(any()) } returns Response.success(loginResponse)

            // When
            val registerResult = repository.register(testEmail, testUsername, testPassword)
            val loginResult = repository.login(testUsername, testPassword)

            // Then
            assertTrue(registerResult is TokenRepository.RegisterResult.Success)
            assertTrue(loginResult is TokenRepository.LoginResult.Success)
            coVerify { sessionStore.saveTokens(testAccessToken, testRefreshToken) }
        }

    @Test
    fun `refreshTokens followed by logout should work correctly`() =
        runTest {
            // Given
            val newAccessToken = "new_access_token"
            every { sessionStore.getRefreshToken() } returns testRefreshToken
            val refreshResponse = RefreshResponse(newAccessToken)
            coEvery { apiService.refreshToken(any()) } returns Response.success(refreshResponse)
            coEvery { apiService.logout(any()) } returns Response.success(Unit)

            // When
            val refreshResult = repository.refreshTokens()
            repository.logout()

            // Then
            assertTrue(refreshResult is TokenRepository.RefreshResult.Success)
            coVerify { sessionStore.saveTokens(newAccessToken, testRefreshToken) }
            coVerify { sessionStore.clearTokens() }
        }
}
