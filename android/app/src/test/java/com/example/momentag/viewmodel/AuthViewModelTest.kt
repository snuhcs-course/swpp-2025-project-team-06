package com.example.momentag.viewmodel

import com.example.momentag.data.SessionExpirationManager
import com.example.momentag.repository.TokenRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        tokenRepository = mockk(relaxed = true)
        sessionExpirationManager = mockk(relaxed = true)
        viewModel = AuthViewModel(tokenRepository, sessionExpirationManager)
    }

    // --- Login Tests ---

    @Test
    fun `login success updates state and resets session expiration`() =
        runTest {
            val username = "user"
            val password = "password"
            coEvery { tokenRepository.login(username, password) } returns TokenRepository.LoginResult.Success

            viewModel.login(username, password)
            advanceUntilIdle()

            assertEquals(AuthViewModel.LoginState.Success, viewModel.loginState.value)
            verify { sessionExpirationManager.resetSessionExpiration() }
        }

    @Test
    fun `login bad request updates state`() =
        runTest {
            coEvery { tokenRepository.login(any(), any()) } returns TokenRepository.LoginResult.BadRequest

            viewModel.login("user", "pass")
            advanceUntilIdle()

            assertEquals(AuthViewModel.LoginState.BadRequest, viewModel.loginState.value)
        }

    @Test
    fun `login unauthorized updates state`() =
        runTest {
            coEvery { tokenRepository.login(any(), any()) } returns TokenRepository.LoginResult.Unauthorized

            viewModel.login("user", "pass")
            advanceUntilIdle()

            assertEquals(AuthViewModel.LoginState.Unauthorized, viewModel.loginState.value)
        }

    @Test
    fun `login network error updates state`() =
        runTest {
            coEvery { tokenRepository.login(any(), any()) } returns TokenRepository.LoginResult.NetworkError

            viewModel.login("user", "pass")
            advanceUntilIdle()

            assertEquals(AuthViewModel.LoginState.NetworkError, viewModel.loginState.value)
        }

    @Test
    fun `login error updates state`() =
        runTest {
            coEvery { tokenRepository.login(any(), any()) } returns TokenRepository.LoginResult.Error

            viewModel.login("user", "pass")
            advanceUntilIdle()

            assertEquals(AuthViewModel.LoginState.Error, viewModel.loginState.value)
        }

    @Test
    fun `resetLoginState resets to Idle`() {
        // Set to something else first
        coEvery { tokenRepository.login(any(), any()) } returns TokenRepository.LoginResult.Success
        viewModel.login("u", "p")

        viewModel.resetLoginState()

        assertEquals(AuthViewModel.LoginState.Idle, viewModel.loginState.value)
    }

    // --- Register Tests ---

    @Test
    fun `register success updates state`() =
        runTest {
            val userId = 123
            coEvery { tokenRepository.register(any(), any()) } returns TokenRepository.RegisterResult.Success(userId)

            viewModel.register("user", "pass")
            advanceUntilIdle()

            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.Success)
            assertEquals(userId, (state as AuthViewModel.RegisterState.Success).id)
        }

    @Test
    fun `register bad request updates state`() =
        runTest {
            val msg = "Bad Request"
            coEvery { tokenRepository.register(any(), any()) } returns TokenRepository.RegisterResult.BadRequest(msg)

            viewModel.register("user", "pass")
            advanceUntilIdle()

            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.BadRequest)
            assertEquals(msg, (state as AuthViewModel.RegisterState.BadRequest).message)
        }

    @Test
    fun `register conflict updates state`() =
        runTest {
            val msg = "Conflict"
            coEvery { tokenRepository.register(any(), any()) } returns TokenRepository.RegisterResult.Conflict(msg)

            viewModel.register("user", "pass")
            advanceUntilIdle()

            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.Conflict)
            assertEquals(msg, (state as AuthViewModel.RegisterState.Conflict).message)
        }

    @Test
    fun `register network error updates state`() =
        runTest {
            val msg = "Network Error"
            coEvery { tokenRepository.register(any(), any()) } returns TokenRepository.RegisterResult.NetworkError(msg)

            viewModel.register("user", "pass")
            advanceUntilIdle()

            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.NetworkError)
            assertEquals(msg, (state as AuthViewModel.RegisterState.NetworkError).message)
        }

    @Test
    fun `register error updates state`() =
        runTest {
            val msg = "Error"
            coEvery { tokenRepository.register(any(), any()) } returns TokenRepository.RegisterResult.Error(msg)

            viewModel.register("user", "pass")
            advanceUntilIdle()

            val state = viewModel.registerState.value
            assertTrue(state is AuthViewModel.RegisterState.Error)
            assertEquals(msg, (state as AuthViewModel.RegisterState.Error).message)
        }

    @Test
    fun `resetRegisterState resets to Idle`() {
        viewModel.resetRegisterState()
        assertEquals(AuthViewModel.RegisterState.Idle, viewModel.registerState.value)
    }

    // --- Refresh Tests ---

    @Test
    fun `refreshTokens success updates state`() =
        runTest {
            coEvery { tokenRepository.refreshTokens() } returns TokenRepository.RefreshResult.Success

            viewModel.refreshTokens()
            advanceUntilIdle()

            assertEquals(AuthViewModel.RefreshState.Success, viewModel.refreshState.value)
        }

    @Test
    fun `refreshTokens unauthorized updates state`() =
        runTest {
            coEvery { tokenRepository.refreshTokens() } returns TokenRepository.RefreshResult.Unauthorized

            viewModel.refreshTokens()
            advanceUntilIdle()

            assertEquals(AuthViewModel.RefreshState.Unauthorized, viewModel.refreshState.value)
        }

    @Test
    fun `refreshTokens network error updates state`() =
        runTest {
            val msg = "Network Error"
            coEvery { tokenRepository.refreshTokens() } returns TokenRepository.RefreshResult.NetworkError(msg)

            viewModel.refreshTokens()
            advanceUntilIdle()

            val state = viewModel.refreshState.value
            assertTrue(state is AuthViewModel.RefreshState.NetworkError)
            assertEquals(msg, (state as AuthViewModel.RefreshState.NetworkError).message)
        }

    @Test
    fun `refreshTokens error updates state`() =
        runTest {
            val msg = "Error"
            coEvery { tokenRepository.refreshTokens() } returns TokenRepository.RefreshResult.Error(msg)

            viewModel.refreshTokens()
            advanceUntilIdle()

            val state = viewModel.refreshState.value
            assertTrue(state is AuthViewModel.RefreshState.Error)
            assertEquals(msg, (state as AuthViewModel.RefreshState.Error).message)
        }

    @Test
    fun `resetRefreshState resets to Idle`() {
        viewModel.resetRefreshState()
        assertEquals(AuthViewModel.RefreshState.Idle, viewModel.refreshState.value)
    }

    // --- Logout Tests ---

    @Test
    fun `logout success updates state`() =
        runTest {
            coEvery { tokenRepository.logout() } returns Unit

            viewModel.logout()
            advanceUntilIdle()

            assertEquals(AuthViewModel.LogoutState.Success, viewModel.logoutState.value)
            coVerify { tokenRepository.logout() }
        }

    @Test
    fun `resetLogoutState resets to Idle`() {
        viewModel.resetLogoutState()
        assertEquals(AuthViewModel.LogoutState.Idle, viewModel.logoutState.value)
    }
}
