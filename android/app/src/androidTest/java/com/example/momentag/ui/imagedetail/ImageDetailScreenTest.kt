package com.example.momentag.ui.imagedetail

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.ImageDetailScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ImageDetailScreenTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        } else {
            GrantPermissionRule.grant(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Test constants - use a valid-looking URI
    private val testImageId = "test_image_123"
    private val testImageUri = Uri.parse("content://media/external/images/media/1")

    @Test
    fun imageDetailScreen_initialState_displaysCorrectUI() {
        // Given: ImageDetailScreen is displayed with valid URI
        composeTestRule.setContent {
            MomenTagTheme {
                ImageDetailScreen(
                    imageUri = testImageUri,
                    imageId = testImageId,
                    onNavigateBack = {},
                )
            }
        }

        // Wait for UI to fully render
        composeTestRule.waitForIdle()

        // Then: Verify Top Bar is displayed
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()

        // Then: Verify Back button is displayed
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_backButton_hasClickAction() {
        // Given: ImageDetailScreen is displayed with valid URI
        composeTestRule.setContent {
            MomenTagTheme {
                ImageDetailScreen(
                    imageUri = testImageUri,
                    imageId = testImageId,
                    onNavigateBack = {},
                )
            }
        }

        // Wait for UI to fully render
        composeTestRule.waitForIdle()

        // Then: Verify Back button is clickable
        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun imageDetailScreen_topBarTitle_isDisplayed() {
        // Given: ImageDetailScreen is displayed with valid URI
        composeTestRule.setContent {
            MomenTagTheme {
                ImageDetailScreen(
                    imageUri = testImageUri,
                    imageId = testImageId,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Then: Verify the title "MomenTag" is displayed
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_scaffold_rendersCorrectly() {
        // Given: ImageDetailScreen is displayed with valid URI
        composeTestRule.setContent {
            MomenTagTheme {
                ImageDetailScreen(
                    imageUri = testImageUri,
                    imageId = testImageId,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Then: Verify scaffold components are rendered
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_pagerInitializes_withoutCrash() {
        // Given: ImageDetailScreen is displayed with valid URI
        composeTestRule.setContent {
            MomenTagTheme {
                ImageDetailScreen(
                    imageUri = testImageUri,
                    imageId = testImageId,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Then: Screen renders without crash
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }
}
