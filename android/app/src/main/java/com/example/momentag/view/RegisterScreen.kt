package com.example.momentag.view

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.viewmodel.AuthViewModel

// TODO: Show loading screen while waiting for registration response
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    // Context and platform-related variables
    val context = LocalContext.current
    val backgroundBrush = rememberAppBackgroundBrush()

    // ViewModel instance
    val authViewModel: AuthViewModel = hiltViewModel()

    // State collected from ViewModel
    val registerState by authViewModel.registerState.collectAsState()

    // Local state variables
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordCheck by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var passwordCheckVisible by rememberSaveable { mutableStateOf(false) }
    var isUsernameError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var isPasswordCheckError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUsernameTouched by remember { mutableStateOf(false) }
    var isPasswordTouched by remember { mutableStateOf(false) }
    var isPasswordCheckTouched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }

    // Callback function to clear all errors
    val clearAllErrors = {
        isUsernameError = false
        isPasswordError = false
        isPasswordCheckError = false
        errorMessage = null
        isErrorBannerVisible = false
    }

    // Handle registration state changes
    LaunchedEffect(registerState) {
        when (val state = registerState) {
            is AuthViewModel.RegisterState.Loading -> {
                isLoading = true
                isErrorBannerVisible = false
            }
            is AuthViewModel.RegisterState.Success -> {
                isLoading = false
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Register.route) { inclusive = true }
                }
            }
            is AuthViewModel.RegisterState.BadRequest -> {
                isLoading = false
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                isErrorBannerVisible = true
                authViewModel.resetRegisterState()
            }
            is AuthViewModel.RegisterState.NetworkError -> {
                isLoading = false
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                isErrorBannerVisible = true
                authViewModel.resetRegisterState()
            }
            is AuthViewModel.RegisterState.Error -> {
                isLoading = false
                errorMessage = state.message
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
                    .background(backgroundBrush)
                    .padding(paddingValues)
                    .padding(horizontal = Dimen.FormScreenHorizontalPadding)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Dimen.SectionSpacing))
            // MomenTag title
            Text(
                text = stringResource(R.string.app_name),
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
                    text = stringResource(R.string.login_sign_up),
                    style = MaterialTheme.typography.displayLarge,
                )

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                // "Already have an account? Login"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.register_have_account) + " ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(R.string.register_log_in),
                        modifier =
                            Modifier.clickable {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            },
                        style = TextStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(Dimen.SectionSpacing))

                // username input
                Text(
                    text = stringResource(R.string.field_username),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimen.ButtonHeightLarge)
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
                    placeholder = { Text(stringResource(R.string.field_username), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
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
                            .height(Dimen.InputHelperTextHeight)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (isUsernameError && username.isEmpty()) {
                        Text(
                            text = stringResource(R.string.validation_required_username),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // password input
                Text(
                    text = stringResource(R.string.field_password),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimen.ButtonHeightLarge)
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
                    placeholder = { Text(stringResource(R.string.field_password), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description =
                            if (passwordVisible) {
                                stringResource(
                                    R.string.cd_password_hide,
                                )
                            } else {
                                stringResource(R.string.cd_password_show)
                            }
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
                            .height(Dimen.InputHelperTextHeight)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (isPasswordError && password.isEmpty()) {
                        Text(
                            text = stringResource(R.string.validation_required_password),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // password check input
                Text(
                    text = stringResource(R.string.field_confirm_password),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimen.ButtonHeightLarge)
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
                    placeholder = {
                        Text(
                            stringResource(R.string.field_confirm_password),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
                    visualTransformation = if (passwordCheckVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordCheckVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description =
                            if (passwordCheckVisible) {
                                stringResource(
                                    R.string.cd_password_hide,
                                )
                            } else {
                                stringResource(R.string.cd_password_show)
                            }
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

                // Show local validation error only (not when server error banner is visible)
                Box(
                    modifier =
                        Modifier
                            .height(Dimen.InputHelperTextHeight)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (!isErrorBannerVisible) {
                        if (isPasswordCheckError && passwordCheck.isEmpty()) {
                            Text(
                                text = stringResource(R.string.validation_required_password_check),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (passwordCheck.isNotEmpty() && password != passwordCheck) {
                            Text(
                                text = stringResource(R.string.validation_passwords_dont_match),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                // Warning banner for registration errors
                AnimatedVisibility(visible = isErrorBannerVisible && errorMessage != null) {
                    WarningBanner(
                        title = stringResource(R.string.error_title_register_failed),
                        message = errorMessage ?: stringResource(R.string.error_message_unknown),
                        onActionClick = { isErrorBannerVisible = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = { isErrorBannerVisible = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                // register button
                Button(
                    onClick = {
                        val usernameEmpty = username.isEmpty()
                        val passwordEmpty = password.isEmpty()
                        val passwordCheckEmpty = passwordCheck.isEmpty()
                        isUsernameError = usernameEmpty
                        isPasswordError = passwordEmpty
                        isPasswordCheckError = passwordCheckEmpty

                        if (password != passwordCheck) {
                            isPasswordCheckError = true
                        } else if (!usernameEmpty && !passwordEmpty && !passwordCheckEmpty) {
                            authViewModel.register(username, password)
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimen.ButtonHeightLarge),
                    shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    enabled = !isLoading,
                ) {
                    AnimatedContent(
                        targetState = isLoading,
                        transitionSpec = {
                            (Animation.DefaultFadeIn).togetherWith(Animation.DefaultFadeOut)
                        },
                        label = "RegisterButtonContent",
                    ) { loading ->
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(Dimen.IconButtonSizeSmall),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                            )
                        } else {
                            Text(text = stringResource(R.string.action_register), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
