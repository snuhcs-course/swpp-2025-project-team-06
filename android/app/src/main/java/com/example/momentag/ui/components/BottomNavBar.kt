package com.example.momentag.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

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
                .height(56.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                isSelected = currentTab == BottomTab.HomeScreen,
            ) { onTabSelected(BottomTab.HomeScreen) }

            BottomNavItem(
                icon = Icons.Default.Search,
                isSelected = currentTab == BottomTab.SearchResultScreen,
            ) { onTabSelected(BottomTab.SearchResultScreen) }

            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.Label,
                isSelected = currentTab == BottomTab.MyTagsScreen,
            ) { onTabSelected(BottomTab.MyTagsScreen) }

            BottomNavItem(
                icon = Icons.Default.AutoStories,
                isSelected = currentTab == BottomTab.StoryScreen,
            ) { onTabSelected(BottomTab.StoryScreen) }
        }
    }
}

enum class BottomTab {
    HomeScreen,
    SearchResultScreen,
    MyTagsScreen,
    StoryScreen,
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    val indicatorWidth = animateDpAsState(targetValue = if (isSelected) 16.dp else 0.dp, label = "")
    val indicatorAlpha = animateFloatAsState(targetValue = if (isSelected) 1f else 0f, label = "")

    Column(
        modifier =
            Modifier
                .clickable { onClick() }
                .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 아이콘 아래 둥근 막대 인디케이터
        Box(
            modifier =
                Modifier
                    .width(indicatorWidth.value)
                    .height(4.dp)
                    .clip(CircleShape)
                    .alpha(indicatorAlpha.value)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}
