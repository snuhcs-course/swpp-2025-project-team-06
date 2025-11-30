package com.example.momentag.ui.login

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.LoginScreen
import com.example.momentag.viewmodel.AuthViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hilt 환경에서 동작
 * - Hilt가 ViewModel을 생성하게 됨 (hiltRule.inject())
 * - 생성된 ViewModel 인스턴스를 가져와 reflection으로 내부 MutableStateFlow 값을 설정
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    // Hilt rule
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Compose rule using HiltTestActivity so Hilt VM factory is available
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var vm: AuthViewModel

    @Before
    fun setup() {
        // must inject Hilt BEFORE requesting the ViewModel
        hiltRule.inject()

        // get the Hilt-created ViewModel from the activity's ViewModelProvider
        vm = ViewModelProvider(composeTestRule.activity)[AuthViewModel::class.java]

        // For example, to set initial states for tests:
        // setFlow("_username", "")
        // setFlow("_password", "")
        // setFlow("_isLoggedIn", false)
    }

    /**
     * reflection으로 ViewModel 내부 private MutableStateFlow 필드 값을 바꿔서
     * UI에 데이터/상태를 주입
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(
        name: String,
        value: T,
    ) {
        try {
            val field = AuthViewModel::class.java.getDeclaredField(name)
            field.isAccessible = true
            val flow = field.get(vm) as MutableStateFlow<T>
            flow.value = value
        } catch (e: NoSuchFieldException) {
            // This can happen if the ViewModel's internal fields change.
            // For this test setup, we can ignore it, but in a real scenario,
            // this would indicate the test needs to be updated.
        }
    }
    // ---------- 1. 초기 화면 상태 ----------

    @Test
    fun loginScreen_initialState_displaysCorrectUI() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController, showExpirationWarning = false)
            }
        }

        // 문자열 리소스 가져오기
        val appTitleWithHash = composeTestRule.activity.getString(R.string.app_title_with_hash)
        val loginTitle = composeTestRule.activity.getString(R.string.login_title)
        val signUp = composeTestRule.activity.getString(R.string.login_sign_up)
        val username = composeTestRule.activity.getString(R.string.field_username)
        val password = composeTestRule.activity.getString(R.string.field_password)
        val logIn = composeTestRule.activity.getString(R.string.action_login)

        // 주요 UI 요소 확인
        composeTestRule.onNodeWithText(appTitleWithHash).assertIsDisplayed()
        composeTestRule.onNodeWithText(loginTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(signUp).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(username).assertCountEquals(2)
        composeTestRule.onAllNodesWithText(password).assertCountEquals(2)
        composeTestRule.onNodeWithText(logIn).assertIsDisplayed()
        composeTestRule.onNodeWithText(logIn).assertIsEnabled()
    }

    // ---------- 2. 입력 필드 상호작용 ----------

    @Test
    fun loginScreen_inputFields_acceptInput() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController, showExpirationWarning = false)
            }
        }

        // 문자열 리소스 가져오기
        val username = composeTestRule.activity.getString(R.string.field_username)
        val password = composeTestRule.activity.getString(R.string.field_password)

        // Username 입력
        composeTestRule
            .onAllNodesWithText(username)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("testuser")

        composeTestRule.onNodeWithText("testuser").assertIsDisplayed()

        // Password 입력
        composeTestRule
            .onAllNodesWithText(password)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("password123")
    }

    // ---------- 3. 비밀번호 표시/숨김 토글 ----------

    @Test
    fun loginScreen_passwordVisibilityToggle_works() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController, showExpirationWarning = false)
            }
        }

        // 문자열 리소스 가져오기
        val showPassword = composeTestRule.activity.getString(R.string.cd_password_show)
        val hidePassword = composeTestRule.activity.getString(R.string.cd_password_hide)

        // 초기에는 "Show password"
        composeTestRule.onNodeWithContentDescription(showPassword).assertIsDisplayed()

        // 토글 클릭
        composeTestRule.onNodeWithContentDescription(showPassword).performClick()

        // 클릭 후에는 "Hide password"로 변경
        composeTestRule.onNodeWithContentDescription(hidePassword).assertIsDisplayed()
    }

    // ---------- 4. 유효성 검증 에러 ----------

    @Test
    fun loginScreen_emptyFields_showErrors() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController, showExpirationWarning = false)
            }
        }

        // 문자열 리소스 가져오기
        val logIn = composeTestRule.activity.getString(R.string.action_login)
        val errorUsername = composeTestRule.activity.getString(R.string.validation_required_username)
        val errorPassword = composeTestRule.activity.getString(R.string.validation_required_password)

        // 아무것도 입력하지 않고 Log In 버튼 클릭
        composeTestRule.onNodeWithText(logIn).performClick()

        composeTestRule.waitForIdle()

        // 에러 메시지 확인
        composeTestRule.onNodeWithText(errorUsername).assertIsDisplayed()
        composeTestRule.onNodeWithText(errorPassword).assertIsDisplayed()
    }

    // ---------- 5. 전체 플로우 ----------

    @Test
    fun loginScreen_completeFlow_withValidInputs() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController, showExpirationWarning = false)
            }
        }

        // 문자열 리소스 가져오기
        val username = composeTestRule.activity.getString(R.string.field_username)
        val password = composeTestRule.activity.getString(R.string.field_password)
        val logIn = composeTestRule.activity.getString(R.string.action_login)

        // 모든 필드 입력
        composeTestRule
            .onAllNodesWithText(username)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("validuser")

        composeTestRule
            .onAllNodesWithText(password)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("validpassword")

        composeTestRule.waitForIdle()

        // Log In 버튼 활성화 확인
        composeTestRule.onNodeWithText(logIn).assertIsEnabled()

        // Log In 버튼 클릭
        composeTestRule.onNodeWithText(logIn).performClick()
    }
}
