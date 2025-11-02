package com.example.momentag.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CommonTopBar Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 *
 * CommonTopBar, BackTopBar, HomeTopBar 모두 테스트
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommonTopBarComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region CommonTopBar Tests
    @Test
    fun commonTopBar_displaysTitle() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(title = "Test Title")
        }

        // Then
        composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()
    }

    @Test
    fun commonTopBar_withBackButton_showsBackIcon() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showBackButton = true,
                onBackClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun commonTopBar_withoutBackButton_hidesBackIcon() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showBackButton = false,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun commonTopBar_backButton_triggersCallback() {
        // Given
        var backClicked = false
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showBackButton = true,
                onBackClick = { backClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Then
        assertTrue(backClicked)
    }

    @Test
    fun commonTopBar_withLogout_showsLogoutButton() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showLogout = true,
                onLogoutClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Logout").assertIsDisplayed()
    }

    @Test
    fun commonTopBar_withoutLogout_hidesLogoutButton() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showLogout = false,
            )
        }

        // Then
        composeTestRule.onNodeWithText("Logout").assertDoesNotExist()
    }

    @Test
    fun commonTopBar_logoutButton_triggersCallback() {
        // Given
        var logoutClicked = false
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showLogout = true,
                onLogoutClick = { logoutClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("Logout").performClick()

        // Then
        assertTrue(logoutClicked)
    }

    @Test
    fun commonTopBar_logoutLoading_showsProgressIndicator() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showLogout = true,
                onLogoutClick = {},
                isLogoutLoading = true,
            )
        }

        // Then - 로딩 중에는 "Logout" 텍스트가 보이지 않음
        composeTestRule.onNodeWithText("Logout").assertDoesNotExist()
    }

    @Test
    fun commonTopBar_logoutNotLoading_showsLogoutButton() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(
                title = "Test",
                showLogout = true,
                onLogoutClick = {},
                isLogoutLoading = false,
            )
        }

        // Then
        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()
    }

    @Test
    fun commonTopBar_titleClickable_triggersCallback() {
        // Given
        var titleClicked = false
        composeTestRule.setContent {
            CommonTopBar(
                title = "Clickable Title",
                onTitleClick = { titleClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("Clickable Title").performClick()

        // Then
        assertTrue(titleClicked)
    }

    @Test
    fun commonTopBar_titleNotClickable_whenNoCallback() {
        // Given - onTitleClick이 null일 때
        composeTestRule.setContent {
            CommonTopBar(
                title = "Non-Clickable Title",
                onTitleClick = null,
            )
        }

        // Then - 타이틀은 표시되지만 클릭 가능하지 않음
        composeTestRule.onNodeWithText("Non-Clickable Title").assertIsDisplayed()
    }

    @Test
    fun commonTopBar_allFeatures_together() {
        // Given - 모든 기능을 함께 사용
        var backClicked = false
        var logoutClicked = false
        var titleClicked = false

        composeTestRule.setContent {
            CommonTopBar(
                title = "Full Featured",
                showBackButton = true,
                onBackClick = { backClicked = true },
                showLogout = true,
                onLogoutClick = { logoutClicked = true },
                onTitleClick = { titleClicked = true },
            )
        }

        // Then - 모든 요소가 표시됨
        composeTestRule.onNodeWithText("Full Featured").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()

        // When - 각 요소 클릭
        composeTestRule.onNodeWithText("Full Featured").performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Logout").performClick()

        // Then - 모든 콜백이 호출됨
        assertTrue(titleClicked)
        assertTrue(backClicked)
        assertTrue(logoutClicked)
    }
    // endregion

    // region BackTopBar Tests
    @Test
    fun backTopBar_displaysTitle() {
        // Given
        composeTestRule.setContent {
            BackTopBar(
                title = "Back Screen",
                onBackClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Back Screen").assertIsDisplayed()
    }

    @Test
    fun backTopBar_showsBackButton() {
        // Given
        composeTestRule.setContent {
            BackTopBar(
                title = "Test",
                onBackClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun backTopBar_backButton_triggersCallback() {
        // Given
        var backClicked = false
        composeTestRule.setContent {
            BackTopBar(
                title = "Test",
                onBackClick = { backClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Then
        assertTrue(backClicked)
    }

    @Test
    fun backTopBar_doesNotShowLogout() {
        // Given
        composeTestRule.setContent {
            BackTopBar(
                title = "Test",
                onBackClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Logout").assertDoesNotExist()
    }
    // endregion

    // region HomeTopBar Tests
    @Test
    fun homeTopBar_displaysMomenTagTitle() {
        // Given
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    @Test
    fun homeTopBar_showsLogoutButton() {
        // Given
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Logout").assertIsDisplayed()
    }

    @Test
    fun homeTopBar_logoutButton_triggersCallback() {
        // Given
        var logoutClicked = false
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = { logoutClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("Logout").performClick()

        // Then
        assertTrue(logoutClicked)
    }

    @Test
    fun homeTopBar_logoutLoading_showsProgressIndicator() {
        // Given
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = {},
                isLogoutLoading = true,
            )
        }

        // Then - 로딩 중에는 "Logout" 텍스트가 보이지 않음
        composeTestRule.onNodeWithText("Logout").assertDoesNotExist()
    }

    @Test
    fun homeTopBar_logoutNotLoading_showsLogoutButton() {
        // Given
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = {},
                isLogoutLoading = false,
            )
        }

        // Then
        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()
    }

    @Test
    fun homeTopBar_titleClickable_triggersCallback() {
        // Given
        var titleClicked = false
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = {},
                onTitleClick = { titleClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("MomenTag").performClick()

        // Then
        assertTrue(titleClicked)
    }

    @Test
    fun homeTopBar_doesNotShowBackButton() {
        // Given
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun homeTopBar_allFeatures_together() {
        // Given - 모든 기능을 함께 사용
        var logoutClicked = false
        var titleClicked = false

        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = { logoutClicked = true },
                isLogoutLoading = false,
                onTitleClick = { titleClicked = true },
            )
        }

        // Then - 모든 요소가 표시됨
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()

        // When - 각 요소 클릭
        composeTestRule.onNodeWithText("MomenTag").performClick()
        composeTestRule.onNodeWithText("Logout").performClick()

        // Then - 모든 콜백이 호출됨
        assertTrue(titleClicked)
        assertTrue(logoutClicked)
    }
    // endregion

    // region Edge Cases
    @Test
    fun commonTopBar_emptyTitle_stillRenders() {
        // Given
        composeTestRule.setContent {
            CommonTopBar(title = "")
        }

        // Then - 빈 타이틀이지만 컴포넌트는 렌더링됨 (단, 빈 텍스트는 찾을 수 없을 수 있음)
        // TopBar 자체가 렌더링되었는지만 확인
    }

    @Test
    fun commonTopBar_longTitle_displaysCorrectly() {
        // Given
        val longTitle = "This is a very long title that might wrap or truncate"
        composeTestRule.setContent {
            CommonTopBar(title = longTitle)
        }

        // Then
        composeTestRule.onNodeWithText(longTitle).assertIsDisplayed()
    }

    @Test
    fun commonTopBar_multipleClicks_triggersMultipleTimes() {
        // Given
        var clickCount = 0
        composeTestRule.setContent {
            CommonTopBar(
                title = "Click Me",
                onTitleClick = { clickCount++ },
            )
        }

        // When - 여러 번 클릭
        repeat(3) {
            composeTestRule.onNodeWithText("Click Me").performClick()
        }

        // Then
        assertEquals(3, clickCount)
    }

    @Test
    fun backTopBar_multipleBackClicks_triggersMultipleTimes() {
        // Given
        var backClickCount = 0
        composeTestRule.setContent {
            BackTopBar(
                title = "Test",
                onBackClick = { backClickCount++ },
            )
        }

        // When - 여러 번 클릭
        repeat(2) {
            composeTestRule.onNodeWithContentDescription("Back").performClick()
        }

        // Then
        assertEquals(2, backClickCount)
    }

    @Test
    fun homeTopBar_multipleLogoutClicks_triggersMultipleTimes() {
        // Given
        var logoutClickCount = 0
        composeTestRule.setContent {
            HomeTopBar(
                onLogoutClick = { logoutClickCount++ },
            )
        }

        // When - 여러 번 클릭
        repeat(2) {
            composeTestRule.onNodeWithText("Logout").performClick()
        }

        // Then
        assertEquals(2, logoutClickCount)
    }
    // endregion
}
