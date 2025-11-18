package com.example.momentag.view

import android.util.Patterns
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
import com.example.momentag.model.RegisterState
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.viewmodel.AuthViewModel

// TODO:Register보내고 돌아오는 거 기다릴 동안 Loading 화면 띄워 줘야 할 듯
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current

    // 2. ViewModel 인스턴스
    val authViewModel: AuthViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val registerState by authViewModel.registerState.collectAsState()

    // 4. 로컬 상태 변수
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordCheck by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var passwordCheckVisible by rememberSaveable { mutableStateOf(false) }
    var isEmailError by remember { mutableStateOf(false) }
    var isUsernameError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var isPasswordCheckError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isEmailTouched by remember { mutableStateOf(false) }
    var isUsernameTouched by remember { mutableStateOf(false) }
    var isPasswordTouched by remember { mutableStateOf(false) }
    var isPasswordCheckTouched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }

    // 9. 콜백 함수 정의
    // Email validation helper
    fun isValidEmail(email: String): Boolean =
        Patterns
            .EMAIL_ADDRESS
            .matcher(email)
            .matches()

    val clearAllErrors = {
        isEmailError = false
        isUsernameError = false
        isPasswordError = false
        isPasswordCheckError = false
        errorMessage = null
        isErrorBannerVisible = false
    }

    // 10. LaunchedEffect
    LaunchedEffect(registerState) {
        when (val state = registerState) {
            is RegisterState.Loading -> {
                isLoading = true
                isErrorBannerVisible = false
            }
            is RegisterState.Success -> {
                isLoading = false
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Register.route) { inclusive = true }
                }
            }
            is RegisterState.BadRequest -> {
                isLoading = false
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                isErrorBannerVisible = true
                authViewModel.resetRegisterState()
            }
            is RegisterState.Conflict -> {
                isLoading = false
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                isErrorBannerVisible = true
                authViewModel.resetRegisterState()
            }
            is RegisterState.NetworkError -> {
                isLoading = false
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                isErrorBannerVisible = true
                authViewModel.resetRegisterState()
            }
            is RegisterState.Error -> {
                isLoading = false
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                isErrorBannerVisible = true
                authViewModel.resetRegisterState()
            }
            else -> {}
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
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
                    .padding(horizontal = Dimen.FormScreenHorizontalPadding)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Dimen.SectionSpacing))
            // MomenTag title
            Text(
                text = "MomenTag",
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
                // Sign Up title
                Text(
                    text = "Sign Up",
                    style = MaterialTheme.typography.displayLarge,
                )

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                // "Already have an account? Login"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Login",
                        modifier =
                            Modifier.clickable {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Register.route) { inclusive = true }
                                }
                            },
                        style = TextStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(Dimen.SectionSpacing))

                // email input
                Text(text = "Email", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isEmailTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isEmailError = false
                                    }
                                } else {
                                    if (isEmailTouched && (email.isEmpty() || !isValidEmail(email))) {
                                        isEmailError = true
                                    }
                                }
                            },
                    value = email,
                    onValueChange = {
                        email = it
                        if (errorMessage != null) {
                            clearAllErrors()
                        } else {
                            isEmailError = false
                        }
                    },
                    placeholder = { Text("Email", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = isEmailError || (isEmailTouched && email.isNotEmpty() && !isValidEmail(email)),
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

                // --- 추가: Email 로컬 에러 Box ---
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (isEmailError && email.isEmpty()) {
                        Text(
                            text = "Please enter your email",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (isEmailTouched && email.isNotEmpty() && !isValidEmail(email)) {
                        Text(
                            text = "Please enter a valid email address",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                // --- 추가 끝 ---

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
                            StandardIcon.Icon(imageVector = image, contentDescription = description)
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
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (isPasswordError && password.isEmpty()) {
                        Text(
                            text = "Please enter your password",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // password check input
                Text(text = "Password Check", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isPasswordCheckTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isPasswordCheckError = false
                                    }
                                } else {
                                    if (isPasswordCheckTouched && passwordCheck.isEmpty()) {
                                        isPasswordCheckError = true
                                    }
                                }
                            },
                    value = passwordCheck,
                    onValueChange = {
                        passwordCheck = it
                        if (errorMessage != null) {
                            clearAllErrors()
                        } else {
                            isPasswordCheckError = false
                        }
                    },
                    placeholder = { Text("Password Check", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordCheckVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordCheckVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordCheckVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordCheckVisible = !passwordCheckVisible }) {
                            StandardIcon.Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    isError = isPasswordCheckError || (passwordCheck.isNotEmpty() && password != passwordCheck),
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

                // --- 수정: 중복 에러 로직 정리 및 로컬 에러만 표시 ---
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (!isErrorBannerVisible) { // 서버 에러(배너)가 없을 때만 로컬 에러 표시
                        if (isPasswordCheckError && passwordCheck.isEmpty()) {
                            Text(
                                text = "Please check your password",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (passwordCheck.isNotEmpty() && password != passwordCheck) {
                            Text(
                                text = "Password does not match",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                // --- 수정 끝 (기존 Box, AnimatedVisibility, if 블록 모두 삭제 후 Box로 대체) ---

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                // --- 추가: WarningBanner를 버튼 바로 위로 이동 ---
                AnimatedVisibility(visible = isErrorBannerVisible && errorMessage != null) {
                    WarningBanner(
                        title = "Registration Failed",
                        message = errorMessage ?: "Unknown error",
                        onActionClick = { isErrorBannerVisible = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = { isErrorBannerVisible = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall)) // 배너와 버튼 사이 간격
                // --- 추가 끝 ---

                // register button
                Button(
                    onClick = {
                        val emailEmpty = email.isEmpty()
                        val emailInvalid = !isValidEmail(email)
                        val usernameEmpty = username.isEmpty()
                        val passwordEmpty = password.isEmpty()
                        val passwordCheckEmpty = passwordCheck.isEmpty()
                        isEmailError = emailEmpty || emailInvalid
                        isUsernameError = usernameEmpty
                        isPasswordError = passwordEmpty
                        isPasswordCheckError = passwordCheckEmpty

                        if (password != passwordCheck) {
                            isPasswordCheckError = true
                        } else if (!emailEmpty && !emailInvalid && !usernameEmpty && !passwordEmpty && !passwordCheckEmpty) {
                            authViewModel.register(email, username, password)
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
                        Text(text = "Register", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
