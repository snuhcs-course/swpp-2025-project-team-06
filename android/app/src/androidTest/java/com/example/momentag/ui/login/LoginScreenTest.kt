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
    fun loginScreen_initialState_displaysCorrectUI() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController)
            }
        }

        // 주요 UI 요소 확인
        composeTestRule.onNodeWithText("#MomenTag").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Up").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Username").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Password").assertCountEquals(2)
        composeTestRule.onNodeWithText("Log In").assertIsDisplayed()
        composeTestRule.onNodeWithText("Log In").assertIsEnabled()
    }

    // ---------- 2. 입력 필드 상호작용 ----------

    @Test
    fun loginScreen_inputFields_acceptInput() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController)
            }
        }

        // Username 입력
        composeTestRule
            .onAllNodesWithText("Username")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("testuser")

        composeTestRule.onNodeWithText("testuser").assertIsDisplayed()

        // Password 입력
        composeTestRule
            .onAllNodesWithText("Password")
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
                LoginScreen(navController = navController)
            }
        }

        // 초기에는 "Show password"
        composeTestRule.onNodeWithContentDescription("Show password").assertIsDisplayed()

        // 토글 클릭
        composeTestRule.onNodeWithContentDescription("Show password").performClick()

        // 클릭 후에는 "Hide password"로 변경
        composeTestRule.onNodeWithContentDescription("Hide password").assertIsDisplayed()
    }

    // ---------- 4. 유효성 검증 에러 ----------

    @Test
    fun loginScreen_emptyFields_showErrors() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController)
            }
        }

        // 아무것도 입력하지 않고 Log In 버튼 클릭
        composeTestRule.onNodeWithText("Log In").performClick()

        composeTestRule.waitForIdle()

        // 에러 메시지 확인
        composeTestRule.onNodeWithText("Please enter your username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please enter your password").assertIsDisplayed()
    }

    // ---------- 5. 전체 플로우 ----------

    @Test
    fun loginScreen_completeFlow_withValidInputs() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                LoginScreen(navController = navController)
            }
        }

        // 모든 필드 입력
        composeTestRule
            .onAllNodesWithText("Username")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("validuser")

        composeTestRule
            .onAllNodesWithText("Password")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("validpassword")

        composeTestRule.waitForIdle()

        // Log In 버튼 활성화 확인
        composeTestRule.onNodeWithText("Log In").assertIsEnabled()

        // Log In 버튼 클릭
        composeTestRule.onNodeWithText("Log In").performClick()
    }
}