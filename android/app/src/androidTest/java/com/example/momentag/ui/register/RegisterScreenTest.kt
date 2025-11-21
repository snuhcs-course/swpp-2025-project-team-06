package com.example.momentag.ui.register

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.RegisterScreen
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
 * - Hilt가 ViewModel을 생성하게 둠 (hiltRule.inject())
 * - 생성된 ViewModel 인스턴스를 가져와 reflection으로 내부 MutableStateFlow 값을 설정
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RegisterScreenTest {
    // Hilt rule
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Compose rule using HiltTestActivity so Hilt VM factory is available
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var vm: AuthViewModel

    @Before
    fun setup() {
        // must inject Hilt BEFORE requesting the ViewModel
        hiltRule.inject()

        // get the Hilt-created ViewModel from the activity's ViewModelProvider
        vm = ViewModelProvider(composeRule.activity)[AuthViewModel::class.java]

        // For example, to set initial states for tests:
        // setFlow("_email", "")
        // setFlow("_username", "")
        // setFlow("_password", "")
    }

    /**
     * reflection으로 ViewModel 내부 private MutableStateFlow 필드 값을 바꿔서
     * UI에 데이터/상태를 주입
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(name: String, value: T) {
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
    fun registerScreen_initialState_displaysCorrectUI() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 문자열 리소스 가져오기
        val appName = composeRule.activity.getString(R.string.app_name)
        val signUp = composeRule.activity.getString(R.string.login_sign_up)
        val logIn = composeRule.activity.getString(R.string.register_log_in)
        val emailAddress = composeRule.activity.getString(R.string.field_email)
        val username = composeRule.activity.getString(R.string.field_username)

        // 화면이 완전히 로드될 때까지 기다림
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodes(hasText(signUp)).fetchSemanticsNodes().size >= 2
        }

        // 주요 UI 요소 확인
        composeRule.onNodeWithText(appName).assertIsDisplayed()
        composeRule.onAllNodesWithText(signUp).assertCountEquals(2) // 제목과 버튼
        composeRule.onNodeWithText(logIn).assertIsDisplayed()
        composeRule.onAllNodesWithText(emailAddress).assertCountEquals(2) // 라벨과 placeholder
        composeRule.onAllNodesWithText(username).assertCountEquals(2)
        composeRule.onAllNodesWithText(signUp)[1].assertIsDisplayed() // 버튼
        composeRule.onAllNodesWithText(signUp)[1].assertIsEnabled()
    }

    // ---------- 2. 입력 필드 상호작용 ----------

    @Test
    fun registerScreen_inputFields_acceptInput() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 문자열 리소스 가져오기
        val signUp = composeRule.activity.getString(R.string.login_sign_up)
        val emailAddress = composeRule.activity.getString(R.string.field_email)
        val username = composeRule.activity.getString(R.string.field_username)

        // 화면이 완전히 로드될 때까지 기다림
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodes(hasText(signUp)).fetchSemanticsNodes().size >= 2
        }

        // 모든 필드 입력 테스트
        composeRule
            .onAllNodesWithText(emailAddress)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("test@example.com")

        composeRule.waitForIdle()
        composeRule.onNodeWithText("test@example.com").assertIsDisplayed()

        composeRule
            .onAllNodesWithText(username)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("testuser")

        composeRule.waitForIdle()
        composeRule.onNodeWithText("testuser").assertIsDisplayed()
    }

    // ---------- 3. 비밀번호 표시/숨김 토글 ----------

    @Test
    fun registerScreen_passwordVisibilityToggle_works() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 문자열 리소스 가져오기
        val signUp = composeRule.activity.getString(R.string.login_sign_up)
        val showPassword = composeRule.activity.getString(R.string.cd_password_show)
        val hidePassword = composeRule.activity.getString(R.string.cd_password_hide)

        // 화면이 완전히 로드될 때까지 기다림
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodes(hasText(signUp)).fetchSemanticsNodes().size >= 2
        }

        // 초기에는 "Show password"
        composeRule.onAllNodesWithContentDescription(showPassword).onFirst().assertIsDisplayed()

        // 토글 클릭
        composeRule.onAllNodesWithContentDescription(showPassword).onFirst().performClick()

        composeRule.waitForIdle()

        // 클릭 후에는 "Hide password"로 변경
        composeRule.onAllNodesWithContentDescription(hidePassword).onFirst().assertIsDisplayed()
    }

    // ---------- 4. 유효성 검증 에러 ----------

    @Test
    fun registerScreen_emptyFields_showErrors() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 문자열 리소스 가져오기
        val signUp = composeRule.activity.getString(R.string.login_sign_up)
        val errorEmail = composeRule.activity.getString(R.string.validation_required_email)
        val errorUsername = composeRule.activity.getString(R.string.validation_required_username)

        // 화면이 완전히 로드될 때까지 기다림
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodes(hasText(signUp)).fetchSemanticsNodes().size >= 2
        }

        // 아무것도 입력하지 않고 Sign Up 버튼 클릭
        composeRule.onAllNodesWithText(signUp)[1].performClick()

        composeRule.waitForIdle()

        // 에러 메시지 확인
        composeRule.onNodeWithText(errorEmail).assertIsDisplayed()
        composeRule.onNodeWithText(errorUsername).assertIsDisplayed()
    }

    @Test
    fun registerScreen_invalidEmail_showsError() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 문자열 리소스 가져오기
        val signUp = composeRule.activity.getString(R.string.login_sign_up)
        val emailAddress = composeRule.activity.getString(R.string.field_email)
        val username = composeRule.activity.getString(R.string.field_username)
        val errorInvalidEmail = composeRule.activity.getString(R.string.validation_invalid_email)

        // 화면이 완전히 로드될 때까지 기다림
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodes(hasText(signUp)).fetchSemanticsNodes().size >= 2
        }

        // 잘못된 이메일 입력
        val emailField =
            composeRule
                .onAllNodesWithText(emailAddress)
                .filter(hasSetTextAction())
                .onFirst()

        emailField.performTextInput("invalidemail")

        composeRule.waitForIdle()

        // 다른 필드로 포커스 이동
        composeRule
            .onAllNodesWithText(username)
            .filter(hasSetTextAction())
            .onFirst()
            .performClick()

        composeRule.waitForIdle()

        // 이메일 유효성 에러 메시지 확인
        composeRule.onNodeWithText(errorInvalidEmail).assertIsDisplayed()
    }

    @Test
    fun registerScreen_passwordMismatch_showsError() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 문자열 리소스 가져오기
        val signUp = composeRule.activity.getString(R.string.login_sign_up)
        val appName = composeRule.activity.getString(R.string.app_name)
        val password = composeRule.activity.getString(R.string.field_password)
        val confirmPassword = composeRule.activity.getString(R.string.field_confirm_password)
        val errorPasswordMismatch = composeRule.activity.getString(R.string.validation_passwords_dont_match)

        // 화면이 완전히 로드될 때까지 기다림
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodes(hasText(signUp)).fetchSemanticsNodes().size >= 2
        }

        // Password와 Confirm password에 다른 값 입력
        composeRule
            .onAllNodesWithText(password)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("password123")

        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText(confirmPassword)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("password456")

        composeRule.waitForIdle()

        // 키보드를 닫기 위해 다른 곳 클릭 (포커스 이동)
        composeRule.onNodeWithText(appName).performClick()

        composeRule.waitForIdle()

        // 비밀번호 불일치 에러 메시지 확인
        composeRule.onNodeWithText(errorPasswordMismatch).assertIsDisplayed()
    }

    // ---------- 5. 전체 플로우 ----------

    @Test
    fun registerScreen_completeFlow_withValidInputs() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 문자열 리소스 가져오기
        val signUp = composeRule.activity.getString(R.string.login_sign_up)
        val emailAddress = composeRule.activity.getString(R.string.field_email)
        val username = composeRule.activity.getString(R.string.field_username)
        val password = composeRule.activity.getString(R.string.field_password)
        val confirmPassword = composeRule.activity.getString(R.string.field_confirm_password)

        // 화면이 완전히 로드될 때까지 기다림
        composeRule.waitForIdle()
        composeRule.waitUntil(10000) {
            composeRule.onAllNodes(hasText(signUp)).fetchSemanticsNodes().size >= 2
        }

        // 모든 필드 입력
        composeRule
            .onAllNodesWithText(emailAddress)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("newuser@example.com")

        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText(username)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("newuser")

        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText(password)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("securePassword123")

        composeRule.waitForIdle()

        composeRule
            .onAllNodesWithText(confirmPassword)
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("securePassword123")

        composeRule.waitForIdle()

        // Sign Up 버튼 활성화 확인
        composeRule.onAllNodesWithText(signUp)[1].assertIsEnabled()

        // Sign Up 버튼 클릭
        composeRule.onAllNodesWithText(signUp)[1].performClick()

        composeRule.waitForIdle()
    }
}
