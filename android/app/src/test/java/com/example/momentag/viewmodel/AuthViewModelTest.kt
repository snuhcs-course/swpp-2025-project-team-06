package com.example.momentag.viewmodel

import com.example.momentag.model.LoginState
import com.example.momentag.model.LogoutState
import com.example.momentag.model.RefreshState
import com.example.momentag.model.RegisterState
import com.example.momentag.repository.TokenRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private lateinit var tokenRepository: TokenRepository
    private lateinit var viewModel: AuthViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tokenRepository = mockk(relaxed = true)

        // Mock repository flows
        coEvery { tokenRepository.isLoggedIn } returns MutableStateFlow(null)
        coEvery { tokenRepository.isSessionLoaded } returns MutableStateFlow(false)

        viewModel = AuthViewModel(tokenRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Login Tests ==========

    @Test
    fun `login success updates state to Success`() =
        runTest {
            // Given
            coEvery { tokenRepository.login(any(), any()) } returns
                TokenRepository.LoginResult.Success

            // When
            viewModel.login("user", "password")

            // Then
            assertTrue(viewModel.loginState.value is LoginState.Success)
            coVerify { tokenRepository.login("user", "password") }
        }

    @Test
    fun `login BadRequest updates state to BadRequest`() =
        runTest {
            // Given
            coEvery { tokenRepository.login(any(), any()) } returns
                TokenRepository.LoginResult.BadRequest("Invalid input")

            // When
            viewModel.login("user", "password")

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is LoginState.BadRequest)
            assertEquals("Invalid input", (state as LoginState.BadRequest).message)
        }

    @Test
    fun `login Unauthorized updates state to Unauthorized`() =
        runTest {
            // Given
            coEvery { tokenRepository.login(any(), any()) } returns
                TokenRepository.LoginResult.Unauthorized("Wrong credentials")

            // When
            viewModel.login("user", "password")

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is LoginState.Unauthorized)
            assertEquals("Wrong credentials", (state as LoginState.Unauthorized).message)
        }

    @Test
    fun `login NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            coEvery { tokenRepository.login(any(), any()) } returns
                TokenRepository.LoginResult.NetworkError("No internet")

            // When
            viewModel.login("user", "password")

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is LoginState.NetworkError)
            assertEquals("No internet", (state as LoginState.NetworkError).message)
        }

    @Test
    fun `login Error updates state to Error`() =
        runTest {
            // Given
            coEvery { tokenRepository.login(any(), any()) } returns
                TokenRepository.LoginResult.Error("Unknown error")

            // When
            viewModel.login("user", "password")

            // Then
            val state = viewModel.loginState.value
            assertTrue(state is LoginState.Error)
            assertEquals("Unknown error", (state as LoginState.Error).message)
        }

    @Test
    fun `resetLoginState resets to Idle`() =
        runTest {
            // Given
            coEvery { tokenRepository.login(any(), any()) } returns
                TokenRepository.LoginResult.Success
            viewModel.login("user", "password")

            // When
            viewModel.resetLoginState()

            // Then
            assertTrue(viewModel.loginState.value is LoginState.Idle)
        }

    // ========== Register Tests ==========

    @Test
    fun `register success updates state to Success`() =
        runTest {
            // Given
            coEvery { tokenRepository.register(any(), any(), any()) } returns
                TokenRepository.RegisterResult.Success(123)

            // When
            viewModel.register("email@test.com", "user", "password")

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is RegisterState.Success)
            assertEquals(123, (state as RegisterState.Success).id)
            coVerify { tokenRepository.register("email@test.com", "user", "password") }
        }

    @Test
    fun `register BadRequest updates state to BadRequest`() =
        runTest {
            // Given
            coEvery { tokenRepository.register(any(), any(), any()) } returns
                TokenRepository.RegisterResult.BadRequest("Invalid email")

            // When
            viewModel.register("invalid", "user", "password")

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is RegisterState.BadRequest)
            assertEquals("Invalid email", (state as RegisterState.BadRequest).message)
        }

    @Test
    fun `register Conflict updates state to Conflict`() =
        runTest {
            // Given
            coEvery { tokenRepository.register(any(), any(), any()) } returns
                TokenRepository.RegisterResult.Conflict("User already exists")

            // When
            viewModel.register("email@test.com", "user", "password")

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is RegisterState.Conflict)
            assertEquals("User already exists", (state as RegisterState.Conflict).message)
        }

    @Test
    fun `register NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            coEvery { tokenRepository.register(any(), any(), any()) } returns
                TokenRepository.RegisterResult.NetworkError("No connection")

            // When
            viewModel.register("email@test.com", "user", "password")

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is RegisterState.NetworkError)
            assertEquals("No connection", (state as RegisterState.NetworkError).message)
        }

    @Test
    fun `register Error updates state to Error`() =
        runTest {
            // Given
            coEvery { tokenRepository.register(any(), any(), any()) } returns
                TokenRepository.RegisterResult.Error("Server error")

            // When
            viewModel.register("email@test.com", "user", "password")

            // Then
            val state = viewModel.registerState.value
            assertTrue(state is RegisterState.Error)
            assertEquals("Server error", (state as RegisterState.Error).message)
        }

    @Test
    fun `resetRegisterState resets to Idle`() =
        runTest {
            // Given
            coEvery { tokenRepository.register(any(), any(), any()) } returns
                TokenRepository.RegisterResult.Success(123)
            viewModel.register("email@test.com", "user", "password")

            // When
            viewModel.resetRegisterState()

            // Then
            assertTrue(viewModel.registerState.value is RegisterState.Idle)
        }

    // ========== Refresh Tests ==========

    @Test
    fun `refreshTokens success updates state to Success`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Success

            // When
            viewModel.refreshTokens()

            // Then
            assertTrue(viewModel.refreshState.value is RefreshState.Success)
            coVerify { tokenRepository.refreshTokens() }
        }

    @Test
    fun `refreshTokens Unauthorized updates state to Unauthorized`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Unauthorized

            // When
            viewModel.refreshTokens()

            // Then
            assertTrue(viewModel.refreshState.value is RefreshState.Unauthorized)
        }

    @Test
    fun `refreshTokens NetworkError updates state to NetworkError`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.NetworkError("Connection timeout")

            // When
            viewModel.refreshTokens()

            // Then
            val state = viewModel.refreshState.value
            assertTrue(state is RefreshState.NetworkError)
            assertEquals("Connection timeout", (state as RefreshState.NetworkError).message)
        }

    @Test
    fun `refreshTokens Error updates state to Error`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Error("Unknown error")

            // When
            viewModel.refreshTokens()

            // Then
            val state = viewModel.refreshState.value
            assertTrue(state is RefreshState.Error)
            assertEquals("Unknown error", (state as RefreshState.Error).message)
        }

    @Test
    fun `resetRefreshState resets to Idle`() =
        runTest {
            // Given
            coEvery { tokenRepository.refreshTokens() } returns
                TokenRepository.RefreshResult.Success
            viewModel.refreshTokens()

            // When
            viewModel.resetRefreshState()

            // Then
            assertTrue(viewModel.refreshState.value is RefreshState.Idle)
        }

    // ========== Logout Tests ==========

    @Test
    fun `logout calls repository and updates state to Success`() =
        runTest {
            // Given
            coEvery { tokenRepository.logout() } returns Unit

            // When
            viewModel.logout()

            // Then
            assertTrue(viewModel.logoutState.value is LogoutState.Success)
            coVerify { tokenRepository.logout() }
        }

    @Test
    fun `resetLogoutState resets to Idle`() =
        runTest {
            // Given
            viewModel.logout()

            // When
            viewModel.resetLogoutState()

            // Then
            assertTrue(viewModel.logoutState.value is LogoutState.Idle)
        }

    // ========== Flow Exposure Tests ==========

    @Test
    fun `isLoggedIn exposes tokenRepository flow`() =
        runTest {
            // Given
            val testFlow = MutableStateFlow("test_token")
            coEvery { tokenRepository.isLoggedIn } returns testFlow

            // When
            val newViewModel = AuthViewModel(tokenRepository)

            // Then
            assertEquals(testFlow, newViewModel.isLoggedIn)
        }

    @Test
    fun `isSessionLoaded exposes tokenRepository flow`() =
        runTest {
            // Given
            val testFlow = MutableStateFlow(true)
            coEvery { tokenRepository.isSessionLoaded } returns testFlow

            // When
            val newViewModel = AuthViewModel(tokenRepository)

            // Then
            assertEquals(testFlow, newViewModel.isSessionLoaded)
        }
}
