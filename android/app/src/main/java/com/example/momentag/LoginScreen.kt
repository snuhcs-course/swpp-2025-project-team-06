package com.example.momentag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.LoginState
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Blue_word
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.viewmodel.AuthViewModel
import com.example.momentag.viewmodel.ViewModelFactory

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory(context))
    val loginState by authViewModel.loginState.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var isUsernameError by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usernameTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }

    val clearAllErrors = {
        isUsernameError = false
        isPasswordError = false
        errorMessage = null
    }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginState.Success -> {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is LoginState.BadRequest -> {
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                authViewModel.resetLoginState()
            }
            is LoginState.Unauthorized -> {
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                authViewModel.resetLoginState()
            }
            is LoginState.NetworkError -> {
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
                authViewModel.resetLoginState()
            }
            is LoginState.Error -> {
                errorMessage = state.message
                isUsernameError = true
                isPasswordError = true
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
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0.5f to Background,
                                        1.0f to TagColor.copy(alpha = 0.7f),
                                    ),
                            ),
                    ).padding(paddingValues)
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            // MomenTag title
            Text(
                text = "MomenTag",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.75f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Login title
                Text(
                    text = "Login",
                    style =
                        TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // "Don't have an account? Sign Up"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Don't have an account? ", color = Color.Gray)
                    Text(
                        text = "Sign Up",
                        modifier =
                            Modifier.clickable {
                                navController.navigate(Screen.Register.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            },
                        style = TextStyle(color = Blue_word, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // username input
                Text(text = "Username", modifier = Modifier.fillMaxWidth(), color = Color.Gray)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    usernameTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isUsernameError = false
                                    }
                                } else {
                                    if (usernameTouched && username.isEmpty()) {
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
                    placeholder = { Text("Username", color = Temp_word) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = isUsernameError,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color.LightGray,
                            errorBorderColor = Color.Red,
                            errorContainerColor = Color.White,
                        ),
                )
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp),
                ) {
                    if (isUsernameError && username.isEmpty()) {
                        Text(
                            text = "Please enter your username",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // password input
                Text(text = "Password", modifier = Modifier.fillMaxWidth(), color = Color.Gray)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    passwordTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isPasswordError = false
                                    }
                                } else {
                                    if (passwordTouched && password.isEmpty()) {
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
                    placeholder = { Text("Password", color = Temp_word) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    isError = isPasswordError,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color.LightGray,
                            errorBorderColor = Color.Red,
                            errorContainerColor = Color.White,
                        ),
                )

                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp),
                ) {
                    val serverErr = errorMessage
                    if (serverErr != null) {
                        Text(
                            text = serverErr,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (isPasswordError && password.isEmpty()) {
                        Text(
                            text = "Please enter your password",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // "Remember me" checkbox & "Forgot Password?"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.offset(x = (-8).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(checkedColor = Button),
                        )
                        Text("Remember me", color = Color.Gray)
                    }
                    Text(
                        text = "Forgot Password?",
                        modifier =
                            Modifier
                                .clickable {
                                    // TODO: finding password
                                },
                        style = TextStyle(color = Blue_word, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // login button
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
                            containerColor = Button,
                            contentColor = Color.White,
                        ),
                    border = BorderStroke(0.dp, Color.Transparent),
                ) {
                    Text("Log In", fontSize = 18.sp)
                }
            }
        }
    }
}
