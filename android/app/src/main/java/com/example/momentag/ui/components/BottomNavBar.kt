package com.example.momentag.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Temp_word


/**
 * 하단 Bottom Navigation
 * (시안 느낌: 심플한 아이콘/라벨, 현재 탭만 코랄색)
 */
@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = Color.White,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentTab == BottomTab.HomeScreen,
            ) { onTabSelected(BottomTab.HomeScreen) }

            BottomNavItem(
                icon = Icons.Default.Search,
                label = "Search",
                isSelected = currentTab == BottomTab.SearchScreen,
            ) { onTabSelected(BottomTab.SearchScreen) }

            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.Label, // or Icons.Default.AddCircle
                label = "Tag",
                isSelected = currentTab == BottomTab.TagScreen,
            ) { onTabSelected(BottomTab.TagScreen) }

            BottomNavItem(
                icon = Icons.Default.AutoStories, // or Icons.Default.Collections
                label = "Story",
                isSelected = currentTab == BottomTab.StoryScreen,
            ) { onTabSelected(BottomTab.StoryScreen) }
        }
    }
}


// 어떤 탭이 선택돼 있는지 표현할 enum
enum class BottomTab {
    HomeScreen, SearchScreen, TagScreen, StoryScreen
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) Button else Temp_word
    Column(
        modifier =
            Modifier
                .clickable { onClick() }
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
