package com.example.momentag.ui.login

import androidx.activity.ComponentActivity
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
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.LoginScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
