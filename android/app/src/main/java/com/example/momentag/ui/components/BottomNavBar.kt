package com.example.momentag.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconBlueprints

@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimen.BottomNavBarHeight),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = Dimen.BottomNavTonalElevation,
        shadowElevation = Dimen.BottomNavShadowElevation,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimen.BottomNavHorizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                text = "Home",
                isSelected = currentTab == BottomTab.HomeScreen,
            ) { onTabSelected(BottomTab.HomeScreen) }

            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.Label,
                text = "My Tags",
                isSelected = currentTab == BottomTab.MyTagsScreen,
            ) { onTabSelected(BottomTab.MyTagsScreen) }

            BottomNavItem(
                icon = Icons.Default.AutoStories,
                text = "Moment",
                isSelected = currentTab == BottomTab.StoryScreen,
            ) { onTabSelected(BottomTab.StoryScreen) }
        }
    }
}

enum class BottomTab {
    HomeScreen,
    MyTagsScreen,
    StoryScreen,
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val iconBlueprint =
        if (isSelected) {
            IconBlueprints.DefaultPrimary
        } else {
            IconBlueprints.DefaultOnSurface
        }
    val iconStyle = iconBlueprint.asStyle()

    Column(
        modifier =
            Modifier
                .clickable { onClick() }
                .padding(vertical = Dimen.BottomNavItemVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        iconBlueprint.Icon(
            imageVector = icon,
            contentDescription = null,
        )

        Spacer(modifier = Modifier.height(Dimen.GridItemSpacing))

        Text(
            text = text,
            color = iconStyle.tint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(Dimen.SpacingXXSmall))
    }
}
