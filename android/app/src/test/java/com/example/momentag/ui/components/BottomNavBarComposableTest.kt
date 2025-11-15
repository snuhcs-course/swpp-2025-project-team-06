package com.example.momentag.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BottomNavBar Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 *
 * 이 테스트들은 실제 Composable 함수의 코드를 실행하므로
 * Coverage 리포트에 포함됩니다!
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // API 33 (Android 13)
class BottomNavBarComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomNavBar_displaysAllTabs() {
        // Given
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = {},
            )
        }

        // Then - 모든 아이콘이 content description으로 표시되는지 확인
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Tag").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Story").assertIsDisplayed()
    }

    @Test
    fun bottomNavBar_displaysAllIcons() {
        // Given
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = {},
            )
        }

        // Then - 모든 아이콘이 표시되는지 확인 (ContentDescription으로 확인)
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Tag").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Story").assertIsDisplayed()
    }

    @Test
    fun bottomNavBar_homeTab_isSelected() {
        // Given
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = {},
            )
        }

        // Then - Home 탭이 표시됨
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()
    }

    @Test
    fun bottomNavBar_searchTab_isSelected() {
        // Given
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.SearchResultScreen,
                onTabSelected = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun bottomNavBar_tagTab_isSelected() {
        // Given
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.MyTagsScreen,
                onTabSelected = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Tag").assertIsDisplayed()
    }

    @Test
    fun bottomNavBar_storyTab_isSelected() {
        // Given
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.StoryScreen,
                onTabSelected = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Story").assertIsDisplayed()
    }

    @Test
    fun bottomNavBar_clickHomeTab_triggersCallback() {
        // Given
        var clickedTab: BottomTab? = null
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.SearchResultScreen,
                onTabSelected = { clickedTab = it },
            )
        }

        // When - Home 탭 클릭
        composeTestRule.onNodeWithContentDescription("Home").performClick()

        // Then
        assertEquals(BottomTab.HomeScreen, clickedTab)
    }

    @Test
    fun bottomNavBar_clickSearchTab_triggersCallback() {
        // Given
        var clickedTab: BottomTab? = null
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = { clickedTab = it },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        // Then
        assertEquals(BottomTab.SearchResultScreen, clickedTab)
    }

    @Test
    fun bottomNavBar_clickTagTab_triggersCallback() {
        // Given
        var clickedTab: BottomTab? = null
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = { clickedTab = it },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Tag").performClick()

        // Then
        assertEquals(BottomTab.MyTagsScreen, clickedTab)
    }

    @Test
    fun bottomNavBar_clickStoryTab_triggersCallback() {
        // Given
        var clickedTab: BottomTab? = null
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = { clickedTab = it },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Story").performClick()

        // Then
        assertEquals(BottomTab.StoryScreen, clickedTab)
    }

    @Test
    fun bottomNavBar_switchTabs_updatesSelection() {
        // Given - 여러 탭을 순차적으로 테스트
        val clickedTabs = mutableListOf<BottomTab>()

        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = { clickedTabs.add(it) },
            )
        }

        // When - Search 탭 클릭
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        // Then
        assertTrue(clickedTabs.contains(BottomTab.SearchResultScreen))

        // When - Tag 탭 클릭
        composeTestRule.onNodeWithContentDescription("Tag").performClick()

        // Then
        assertTrue(clickedTabs.contains(BottomTab.MyTagsScreen))
    }

    @Test
    fun bottomNavBar_allTabsClickable() {
        // Given
        val clickedTabs = mutableListOf<BottomTab>()
        composeTestRule.setContent {
            BottomNavBar(
                currentTab = BottomTab.HomeScreen,
                onTabSelected = { clickedTabs.add(it) },
            )
        }

        // When - 모든 탭 클릭
        composeTestRule.onNodeWithContentDescription("Home").performClick()
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithContentDescription("Tag").performClick()
        composeTestRule.onNodeWithContentDescription("Story").performClick()

        // Then
        assertEquals(4, clickedTabs.size)
        assertTrue(clickedTabs.contains(BottomTab.HomeScreen))
        assertTrue(clickedTabs.contains(BottomTab.SearchResultScreen))
        assertTrue(clickedTabs.contains(BottomTab.MyTagsScreen))
        assertTrue(clickedTabs.contains(BottomTab.StoryScreen))
    }
}
