package com.example.momentag

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.momentag.model.RegisterState
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Blue_word
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.viewmodel.AuthViewModel
import com.example.momentag.viewmodel.ViewModelFactory

// TODO:Register보내고 돌아오는 거 기다릴 동안 Loading 화면 띄워 줘야 할 듯
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory(context))
    val registerState by authViewModel.registerState.collectAsState()

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
    var emailTouched by remember { mutableStateOf(false) }
    var usernameTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var passwordCheckTouched by remember { mutableStateOf(false) }

    // Email validation helper
    fun isValidEmail(email: String): Boolean =
        android.util.Patterns
            .EMAIL_ADDRESS
            .matcher(email)
            .matches()

    val clearAllErrors = {
        isEmailError = false
        isUsernameError = false
        isPasswordError = false
        isPasswordCheckError = false
        errorMessage = null
    }

    LaunchedEffect(registerState) {
        when (val state = registerState) {
            is RegisterState.Success -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Register.route) { inclusive = true }
                }
            }
            is RegisterState.BadRequest -> {
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                authViewModel.resetRegisterState()
            }
            is RegisterState.Conflict -> {
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                authViewModel.resetRegisterState()
            }
            is RegisterState.NetworkError -> {
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
                authViewModel.resetRegisterState()
            }
            is RegisterState.Error -> {
                errorMessage = state.message
                isEmailError = true
                isUsernameError = true
                isPasswordError = true
                isPasswordCheckError = true
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
                // Sign Up title
                Text(
                    text = "Sign Up",
                    style =
                        TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // "Already have an account? Login"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Already have an account? ", color = Color.Gray)
                    Text(
                        text = "Login",
                        modifier =
                            Modifier.clickable {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Register.route) { inclusive = true }
                                }
                            },
                        style = TextStyle(color = Blue_word, fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // email input
                Text(text = "Email", modifier = Modifier.fillMaxWidth(), color = Color.Gray)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    emailTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isEmailError = false
                                    }
                                } else {
                                    if (emailTouched && (email.isEmpty() || !isValidEmail(email))) {
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
                    placeholder = { Text("Email", color = Temp_word) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = isEmailError || (emailTouched && email.isNotEmpty() && !isValidEmail(email)),
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
                    if (isEmailError && email.isEmpty()) {
                        Text(
                            text = "Please enter your email",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (emailTouched && email.isNotEmpty() && !isValidEmail(email)) {
                        Text(
                            text = "Please enter a valid email address",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

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
                    if (isPasswordError && password.isEmpty()) {
                        Text(
                            text = "Please enter your password",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // password check input
                Text(text = "Password Check", modifier = Modifier.fillMaxWidth(), color = Color.Gray)
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    passwordCheckTouched = true
                                    if (errorMessage != null) {
                                        clearAllErrors()
                                    } else {
                                        isPasswordCheckError = false
                                    }
                                } else {
                                    if (passwordCheckTouched && passwordCheck.isEmpty()) {
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
                    placeholder = { Text("Password Check", color = Temp_word) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordCheckVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordCheckVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordCheckVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordCheckVisible = !passwordCheckVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    isError = isPasswordCheckError || (passwordCheck.isNotEmpty() && password != passwordCheck),
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
                    } else if (isPasswordCheckError && passwordCheck.isEmpty()) {
                        Text(
                            text = "Please check your password",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (passwordCheck.isNotEmpty() && password != passwordCheck) {
                        Text(
                            text = "Password does not match",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            containerColor = com.example.momentag.ui.theme.Button,
                            contentColor = Color.White,
                        ),
                ) {
                    Text(text = "Register", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
