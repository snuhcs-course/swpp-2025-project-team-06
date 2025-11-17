package com.example.momentag.ui.register

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.RegisterScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegisterScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // ---------- 1. 초기 화면 상태 ----------

    @Test
    fun registerScreen_initialState_displaysCorrectUI() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 주요 UI 요소 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithText("Sign Up").assertIsDisplayed()
        composeRule.onNodeWithText("Login").assertIsDisplayed()
        composeRule.onAllNodesWithText("Email").assertCountEquals(2)
        composeRule.onAllNodesWithText("Username").assertCountEquals(2)
        composeRule.onNodeWithText("Register").assertIsDisplayed()
        composeRule.onNodeWithText("Register").assertIsEnabled()
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

        // 모든 필드 입력 테스트
        composeRule
            .onAllNodesWithText("Email")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("test@example.com")

        composeRule.onNodeWithText("test@example.com").assertIsDisplayed()

        composeRule
            .onAllNodesWithText("Username")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("testuser")

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

        // 초기에는 "Show password"
        composeRule.onAllNodesWithContentDescription("Show password").onFirst().assertIsDisplayed()

        // 토글 클릭
        composeRule.onAllNodesWithContentDescription("Show password").onFirst().performClick()

        // 클릭 후에는 "Hide password"로 변경
        composeRule.onAllNodesWithContentDescription("Hide password").onFirst().assertIsDisplayed()
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

        // 아무것도 입력하지 않고 Register 버튼 클릭
        composeRule.onNodeWithText("Register").performClick()

        composeRule.waitForIdle()

        // 에러 메시지 확인
        composeRule.onNodeWithText("Please enter your email").assertIsDisplayed()
        composeRule.onNodeWithText("Please enter your username").assertIsDisplayed()
    }

    @Test
    fun registerScreen_invalidEmail_showsError() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // 잘못된 이메일 입력
        val emailField =
            composeRule
                .onAllNodesWithText("Email")
                .filter(hasSetTextAction())
                .onFirst()

        emailField.performTextInput("invalidemail")

        // 다른 필드로 포커스 이동
        composeRule
            .onAllNodesWithText("Username")
            .filter(hasSetTextAction())
            .onFirst()
            .performClick()

        composeRule.waitForIdle()

        // 이메일 유효성 에러 메시지 확인
        composeRule.onNodeWithText("Please enter a valid email address").assertIsDisplayed()
    }

    @Test
    fun registerScreen_passwordMismatch_showsError() {
        composeRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                RegisterScreen(navController = navController)
            }
        }

        // Password와 Password Check에 다른 값 입력
        composeRule
            .onAllNodesWithText("Password")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("password123")

        composeRule
            .onAllNodesWithText("Password Check")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("password456")

        // 키보드를 닫기 위해 다른 곳 클릭 (포커스 이동)
        composeRule.onNodeWithText("MomenTag").performClick()

        composeRule.waitForIdle()

        // 비밀번호 불일치 에러 메시지 확인
        composeRule.onNodeWithText("Password does not match").assertIsDisplayed()
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

        // 모든 필드 입력
        composeRule
            .onAllNodesWithText("Email")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("newuser@example.com")

        composeRule
            .onAllNodesWithText("Username")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("newuser")

        composeRule
            .onAllNodesWithText("Password")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("securePassword123")

        composeRule
            .onAllNodesWithText("Password Check")
            .filter(hasSetTextAction())
            .onFirst()
            .performTextInput("securePassword123")

        composeRule.waitForIdle()

        // Register 버튼 활성화 확인
        composeRule.onNodeWithText("Register").assertIsEnabled()

        // Register 버튼 클릭
        composeRule.onNodeWithText("Register").performClick()
    }
}
