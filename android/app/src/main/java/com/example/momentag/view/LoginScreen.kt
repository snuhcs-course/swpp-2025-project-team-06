package com.example.momentag.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.momentag.Screen
import com.example.momentag.model.LoginState
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current

    // 2. ViewModel 인스턴스
    val authViewModel: AuthViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val loginState by authViewModel.loginState.collectAsState()

    // 4. 로컬 상태 변수
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isUsernameError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUsernameTouched by remember { mutableStateOf(false) }
    var isPasswordTouched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }

    // 9. 콜백 함수 정의
    val clearAllErrors = {
        isUsernameError = false
        isPasswordError = false
        errorMessage = null
        isErrorBannerVisible = false
    }

    // 10. LaunchedEffect
    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginState.Loading -> {
                isLoading = true
                isErrorBannerVisible = false
            }
            is LoginState.Success -> {
                isLoading = false
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is LoginState.BadRequest -> {
                isLoading = false
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                isErrorBannerVisible = true
                authViewModel.resetLoginState()
            }
            is LoginState.Unauthorized -> {
                isLoading = false
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                isErrorBannerVisible = true
                authViewModel.resetLoginState()
            }
            is LoginState.NetworkError -> {
                isLoading = false
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                isErrorBannerVisible = true
                authViewModel.resetLoginState()
            }
            is LoginState.Error -> {
                isLoading = false
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                isErrorBannerVisible = true
                authViewModel.resetLoginState()
            }
            else -> {}
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0.5f to MaterialTheme.colorScheme.surface,
                                        1.0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    ),
                            ),
                    ).padding(paddingValues)
                    .padding(horizontal = Dimen.FormScreenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Dimen.SectionSpacing))
            // MomenTag title
            Text(
                text = "#MomenTag",
                style = MaterialTheme.typography.displayLarge,
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.95f)
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                // Login title
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.displayLarge,
                )

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                // "Don't have an account? Sign Up"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Don't have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Sign Up",
                        modifier =
                            Modifier.clickable {
                                navController.navigate(Screen.Register.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            },
                        style = TextStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(Dimen.SectionSpacing))

                // username input
                Text(text = "Username", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isUsernameTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isUsernameError = false
                                    }
                                } else {
                                    if (isUsernameTouched && username.isEmpty()) {
                                        isUsernameError = true
                                    }
                                }
                            },
                    value = username,
                    onValueChange = {
                        username = it
                        if (errorMessage != null) {
                            clearAllErrors()
                        } else {
                            isUsernameError = false
                        }
                    },
                    placeholder = { Text("Username", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = isUsernameError,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            errorContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (isUsernameError && username.isEmpty()) {
                        Text(
                            text = "Please enter your username",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // password input
                Text(text = "Password", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isPasswordTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isPasswordError = false
                                    }
                                } else {
                                    if (isPasswordTouched && password.isEmpty()) {
                                        isPasswordError = true
                                    }
                                }
                            },
                    value = password,
                    onValueChange = {
                        password = it
                        if (errorMessage != null) {
                            clearAllErrors()
                        } else {
                            isPasswordError = false
                        }
                    },
                    placeholder = { Text("Password", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            StandardIcon.Icon(
                                imageVector = image,
                                contentDescription = description,
                            )
                        }
                    },
                    isError = isPasswordError,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            errorContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                )

                // --- 수정: 로컬 에러만 여기에 표시 ---
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (!isErrorBannerVisible && isPasswordError && password.isEmpty()) {
                        Text(
                            text = "Please enter your password",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                AnimatedVisibility(visible = isErrorBannerVisible && errorMessage != null) {
                    WarningBanner(
                        title = "Login Failed",
                        message = errorMessage ?: "Unknown error",
                        onActionClick = { isErrorBannerVisible = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = { isErrorBannerVisible = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                Button(
                    onClick = {
                        val usernameEmpty = username.isEmpty()
                        val passwordEmpty = password.isEmpty()
                        isUsernameError = usernameEmpty
                        isPasswordError = passwordEmpty
                        if (!usernameEmpty && !passwordEmpty) {
                            authViewModel.login(username, password)
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    enabled = !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Log In", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
