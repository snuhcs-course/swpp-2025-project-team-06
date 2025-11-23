package com.example.momentag.viewmodel

import com.example.momentag.data.SessionExpirationManager
import com.example.momentag.repository.TokenRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: AuthViewModel
    private lateinit var tokenRepository: TokenRepository

    private lateinit var sessionExpirationManager: SessionExpirationManager

    @Before
    fun setUp() {
        tokenRepository = mockk()
        sessionExpirationManager = mockk()

        // Mock the StateFlows from TokenRepository
        every { tokenRepository.isLoggedIn } returns MutableStateFlow(null)
        every { tokenRepository.isSessionLoaded } returns MutableStateFlow(false)

        viewModel = AuthViewModel(tokenRepository, sessionExpirationManager)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // Login tests
    @Test
    fun `login success updates state to Success`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            coEvery { tokenRepository.login(username, password) } returns
                TokenRepository.LoginResult.Success
            coEvery { sessionExpirationManager.resetSessionExpiration() } returns Unit

            // When
            viewModel.login(username, password)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.loginState.value is AuthViewModel.LoginState.Success)
            coVerify { tokenRepository.login(username, password) }
        }

    @Test
    fun `login with BadRequest updates state to BadRequest`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            val errorMessage = "Invalid request"
            coEvery { tokenRepository.login(username, password) } returns
                TokenRepository.LoginResult.BadRequest(errorMessage)

            // When
            viewModel.login(username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is AuthViewModel.LoginState.BadRequest)
            assertEquals(errorMessage, (state as AuthViewModel.LoginState.BadRequest).message)
        }

    @Test
    fun `login with Unauthorized updates state to Unauthorized`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "wrongpassword"
            val errorMessage = "Incorrect credentials"
            coEvery { tokenRepository.login(username, password) } returns
                TokenRepository.LoginResult.Unauthorized(errorMessage)

            // When
            viewModel.login(username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is AuthViewModel.LoginState.Unauthorized)
            assertEquals(errorMessage, (state as AuthViewModel.LoginState.Unauthorized).message)
        }

    @Test
    fun `login with NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            val errorMessage = "Network unreachable"
            coEvery { tokenRepository.login(username, password) } returns
                TokenRepository.LoginResult.NetworkError(errorMessage)

            // When
            viewModel.login(username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is AuthViewModel.LoginState.NetworkError)
            assertEquals(errorMessage, (state as AuthViewModel.LoginState.NetworkError).message)
        }

    @Test
    fun `login with Error updates state to Error`() =
        runTest {
            // Given
            val username = "testuser"
            val password = "password123"
            val errorMessage = "Server error"
            coEvery { tokenRepository.login(username, password) } returns
                TokenRepository.LoginResult.Error(errorMessage)

            // When
            viewModel.login(username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is AuthViewModel.LoginState.Error)
            assertEquals(errorMessage, (state as AuthViewModel.LoginState.Error).message)
        }

    @Test
    fun `resetLoginState resets state to Idle`() =
        runTest {
            // Given - set to a non-Idle state first
            val username = "testuser"
            val password = "password123"
            coEvery { tokenRepository.login(username, password) } returns
                TokenRepository.LoginResult.Success
            coEvery { sessionExpirationManager.resetSessionExpiration() } returns Unit
            viewModel.login(username, password)
            advanceUntilIdle()

            // When
            viewModel.resetLoginState()

            // Then
            assertTrue(viewModel.loginState.value is AuthViewModel.LoginState.Idle)
        }

    // Register tests
    @Test
    fun `register success updates state to Success`() =
        runTest {
            // Given
            val email = "test@example.com"
            val username = "testuser"
            val password = "password123"
            val userId = 123
            coEvery { tokenRepository.register(email, username, password) } returns
                TokenRepository.RegisterResult.Success(userId)

            // When
            viewModel.register(email, username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.Success)
            assertEquals(123, (state as AuthViewModel.RegisterState.Success).id)
        }

    @Test
    fun `register with BadRequest updates state to BadRequest`() =
        runTest {
            // Given
            val email = "test@example.com"
            val username = "testuser"
            val password = "password123"
            val errorMessage = "Invalid email format"
            coEvery { tokenRepository.register(email, username, password) } returns
                TokenRepository.RegisterResult.BadRequest(errorMessage)

            // When
            viewModel.register(email, username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.BadRequest)
            assertEquals(errorMessage, (state as AuthViewModel.RegisterState.BadRequest).message)
        }

    @Test
    fun `register with Conflict updates state to Conflict`() =
        runTest {
            // Given
            val email = "test@example.com"
            val username = "existinguser"
            val password = "password123"
            val errorMessage = "Username already exists"
            coEvery { tokenRepository.register(email, username, password) } returns
                TokenRepository.RegisterResult.Conflict(errorMessage)

            // When
            viewModel.register(email, username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.Conflict)
            assertEquals(errorMessage, (state as AuthViewModel.RegisterState.Conflict).message)
        }

    @Test
    fun `register with NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            val email = "test@example.com"
            val username = "testuser"
            val password = "password123"
            val errorMessage = "No internet connection"
            coEvery { tokenRepository.register(email, username, password) } returns
                TokenRepository.RegisterResult.NetworkError(errorMessage)

            // When
            viewModel.register(email, username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.NetworkError)
            assertEquals(errorMessage, (state as AuthViewModel.RegisterState.NetworkError).message)
        }

    @Test
    fun `register with Error updates state to Error`() =
        runTest {
            // Given
            val email = "test@example.com"
            val username = "testuser"
            val password = "password123"
            val errorMessage = "Server error"
            coEvery { tokenRepository.register(email, username, password) } returns
                TokenRepository.RegisterResult.Error(errorMessage)

            // When
            viewModel.register(email, username, password)
            advanceUntilIdle()

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.Error)
            assertEquals(errorMessage, (state as AuthViewModel.RegisterState.Error).message)
        }

    @Test
    fun `resetRegisterState resets state to Idle`() =
        runTest {
            // Given
            val email = "test@example.com"
            val username = "testuser"
            val password = "password123"
            coEvery { tokenRepository.register(email, username, password) } returns
                TokenRepository.RegisterResult.Success(1)
            viewModel.register(email, username, password)
            advanceUntilIdle()

            // When
            viewModel.resetRegisterState()

            // Then
            assertTrue(viewModel.registerState.value is AuthViewModel.RegisterState.Idle)
        }

    // Refresh tests
    @Test
    fun `refreshTokens success updates state to Success`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Success

            // When
            viewModel.refreshTokens()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.refreshState.value is AuthViewModel.RefreshState.Success)
        }

    @Test
    fun `refreshTokens with Unauthorized updates state to Unauthorized`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Unauthorized

            // When
            viewModel.refreshTokens()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.refreshState.value is AuthViewModel.RefreshState.Unauthorized)
        }

    @Test
    fun `refreshTokens with NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            val errorMessage = "Network error"
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.NetworkError(errorMessage)

            // When
            viewModel.refreshTokens()
            advanceUntilIdle()

            // Then
            val state = viewModel.refreshState.value
            assertTrue(state is AuthViewModel.RefreshState.NetworkError)
            assertEquals(errorMessage, (state as AuthViewModel.RefreshState.NetworkError).message)
        }

    @Test
    fun `refreshTokens with Error updates state to Error`() =
        runTest {
            // Given
            val errorMessage = "Server error"
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Error(errorMessage)

            // When
            viewModel.refreshTokens()
            advanceUntilIdle()

            // Then
            val state = viewModel.refreshState.value
            assertTrue(state is AuthViewModel.RefreshState.Error)
            assertEquals(errorMessage, (state as AuthViewModel.RefreshState.Error).message)
        }

    @Test
    fun `resetRefreshState resets state to Idle`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Success
            viewModel.refreshTokens()
            advanceUntilIdle()

            // When
            viewModel.resetRefreshState()

            // Then
            assertTrue(viewModel.refreshState.value is AuthViewModel.RefreshState.Idle)
        }

    // Logout tests
    @Test
    fun `logout updates state to Success`() =
        runTest {
            // Given
            coEvery { tokenRepository.logout() } returns Unit

            // When
            viewModel.logout()
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.logoutState.value is AuthViewModel.LogoutState.Success)
            coVerify { tokenRepository.logout() }
        }

    @Test
    fun `resetLogoutState resets state to Idle`() =
        runTest {
            // Given
            coEvery { tokenRepository.logout() } returns Unit
            viewModel.logout()
            advanceUntilIdle()

            // When
            viewModel.resetLogoutState()

            // Then
            assertTrue(viewModel.logoutState.value is AuthViewModel.LogoutState.Idle)
        }

    // StateFlow exposure tests
    @Test
    fun `isLoggedIn exposes TokenRepository isLoggedIn flow`() {
        // Given
        val mockFlow = MutableStateFlow("test-token")
        every { tokenRepository.isLoggedIn } returns mockFlow

        // When
        val newViewModel = AuthViewModel(tokenRepository, sessionExpirationManager)

        // Then
        assertEquals(mockFlow, newViewModel.isLoggedIn)
    }

    @Test
    fun `isSessionLoaded exposes TokenRepository isSessionLoaded flow`() {
        // Given
        val mockFlow = MutableStateFlow(true)
        every { tokenRepository.isSessionLoaded } returns mockFlow

        // When
        val newViewModel = AuthViewModel(tokenRepository, sessionExpirationManager)

        // Then
        assertEquals(mockFlow, newViewModel.isSessionLoaded)
    }
}
