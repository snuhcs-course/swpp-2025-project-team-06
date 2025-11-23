package com.example.momentag.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    showExpirationWarning: Boolean,
) {
    // Context and platform-related variables
    val context = LocalContext.current
    val activity = LocalContext.current.findActivity()
    val backgroundBrush = rememberAppBackgroundBrush()

    // ViewModel instance
    val authViewModel: AuthViewModel = hiltViewModel()

    // State collected from ViewModel
    val loginState by authViewModel.loginState.collectAsState()

    // Local state variables
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isUsernameError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var errorBannerTitle by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUsernameTouched by remember { mutableStateOf(false) }
    var isPasswordTouched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var backPressedTime by remember { mutableStateOf(0L) }

    // Callback function to clear all errors
    val clearAllErrors = {
        isUsernameError = false
        isPasswordError = false
        errorMessage = null
        isErrorBannerVisible = false
    }

    LaunchedEffect(showExpirationWarning) {
        if (showExpirationWarning) {
            errorBannerTitle = context.getString(R.string.banner_session_expired_title)
            errorMessage = context.getString(R.string.banner_session_expired_message)
            isErrorBannerVisible = true
        }
    }

    // Handle login state changes
    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is AuthViewModel.LoginState.Loading -> {
                isLoading = true
                isErrorBannerVisible = false
            }
            is AuthViewModel.LoginState.Success -> {
                isLoading = false
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is AuthViewModel.LoginState.BadRequest,
            is AuthViewModel.LoginState.Unauthorized,
            is AuthViewModel.LoginState.NetworkError,
            is AuthViewModel.LoginState.Error,
            -> {
                    when (state) {
                        is AuthViewModel.LoginState.BadRequest -> context.getString(R.string.error_message_login_bad_request)
                        is AuthViewModel.LoginState.Unauthorized -> context.getString(R.string.error_message_login_invalid_credentials)
                        is AuthViewModel.LoginState.NetworkError -> context.getString(R.string.error_message_network)
                        is AuthViewModel.LoginState.Error -> context.getString(R.string.error_title_login_failed)
                        else -> context.getString(R.string.error_message_unknown)
                    }
                errorMessage = context.getString(R.string.error_message_unknown)
                isUsernameError = true
                isPasswordError = true
                isErrorBannerVisible = true
                authViewModel.resetLoginState()
            }
            else -> {}
        }
    }

    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, context.getString(R.string.home_exit_prompt), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .background(backgroundBrush)
                    .padding(paddingValues)
                    .padding(horizontal = Dimen.FormScreenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Dimen.SectionSpacing))
            // MomenTag title
            Text(
                text = stringResource(R.string.app_title_with_hash),
                style = MaterialTheme.typography.displayLarge,
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.95f)
                        .weight(1f)
                        .animateContentSize(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        ).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                // Login title
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.displayLarge,
                )

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                // "Don't have an account? Sign Up"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.login_no_account) + " ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(R.string.login_sign_up),
                        modifier =
                            Modifier.clickable {
                                navController.navigate(Screen.Register.route)
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

                // Show local validation error only
                Box(
                    modifier =
                        Modifier
                            .height(Dimen.InputHelperTextHeight)
                            .fillMaxWidth()
                            .padding(top = Dimen.ErrorMessagePadding, start = Dimen.ErrorMessagePadding),
                ) {
                    if (!isErrorBannerVisible && isPasswordError && password.isEmpty()) {
                        Text(
                            text = stringResource(R.string.validation_required_password),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                AnimatedVisibility(visible = isErrorBannerVisible && errorMessage != null) {
                    WarningBanner(
                        title = stringResource(R.string.error_title_login_failed),
                        message = errorMessage ?: stringResource(R.string.error_message_unknown),
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
                            (Animation.QuickFadeIn)
                                .togetherWith(Animation.QuickFadeOut)
                                .using(SizeTransform(clip = false))
                        },
                        label = "LoginButtonContent",
                    ) { loading ->
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(Dimen.IconButtonSizeSmall),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                            )
                        } else {
                            Text(stringResource(R.string.action_login), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
