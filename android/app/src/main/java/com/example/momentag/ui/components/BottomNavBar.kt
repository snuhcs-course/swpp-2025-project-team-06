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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momentag.ui.theme.IconBlueprints
import com.example.momentag.R

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
                text = stringResource(R.string.nav_home),
                contentDescription = stringResource(R.string.cd_nav_home),
                isSelected = currentTab == BottomTab.HomeScreen,
            ) { onTabSelected(BottomTab.HomeScreen) }

            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.Label,
                text = stringResource(R.string.nav_my_tags),
                contentDescription = stringResource(R.string.cd_nav_my_tags),
                isSelected = currentTab == BottomTab.MyTagsScreen,
            ) { onTabSelected(BottomTab.MyTagsScreen) }

            BottomNavItem(
                icon = Icons.Default.AutoStories,
                text = stringResource(R.string.nav_moment),
                contentDescription = stringResource(R.string.cd_nav_moment),
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
    contentDescription: String,
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
                .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        iconBlueprint.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = text,
            color = iconStyle.tint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))
    }
}
