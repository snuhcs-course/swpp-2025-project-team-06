package com.example.momentag.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momentag.ui.theme.Background

/**
 * 공통 TopAppBar 컴포넌트
 *
 * 모든 화면에서 재사용 가능한 TopBar 컴포넌트
 * 뒤로가기, 로그아웃, 커스텀 액션 버튼 지원
 *
 * @param title 화면 제목
 * @param showBackButton 뒤로가기 버튼 표시 여부
 * @param onBackClick 뒤로가기 클릭 핸들러
 * @param showLogout 로그아웃 버튼 표시 여부
 * @param onLogoutClick 로그아웃 클릭 핸들러
 * @param isLogoutLoading 로그아웃 로딩 상태
 * @param actions 추가 액션 버튼들 (커스텀)
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    showLogout: Boolean = false,
    onLogoutClick: (() -> Unit)? = null,
    isLogoutLoading: Boolean = false,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                modifier =
                    if (onTitleClick != null) {
                        Modifier.clickable { onTitleClick() }
                    } else {
                        Modifier
                    },
            )
        },
        navigationIcon = {
            if (showBackButton && onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        actions = {
            // 로그아웃 버튼
            if (showLogout && onLogoutClick != null) {
                if (isLogoutLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    TextButton(onClick = onLogoutClick) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Logout",
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // 커스텀 액션 버튼들
            actions()
        },
        colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Background,
            ),
        modifier = modifier,
    )
}

/**
 * 뒤로가기만 있는 간단한 TopBar
 *
 * @param title 화면 제목
 * @param onBackClick 뒤로가기 클릭 핸들러
 * @param modifier Modifier
 */
@Composable
fun BackTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CommonTopBar(
        title = title,
        showBackButton = true,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

/**
 * 홈 화면용 TopBar (로그아웃 포함)
 *
 * @param onLogoutClick 로그아웃 클릭 핸들러
 * @param isLogoutLoading 로그아웃 로딩 상태
 * @param onTitleClick 타이틀 클릭 핸들러 (LocalGallery로 이동)
 * @param modifier Modifier
 */
@Composable
fun HomeTopBar(
    modifier: Modifier = Modifier,
    onLogoutClick: () -> Unit,
    isLogoutLoading: Boolean = false,
    onTitleClick: (() -> Unit)? = null,
) {
    CommonTopBar(
        title = "MomenTag",
        showLogout = true,
        onLogoutClick = onLogoutClick,
        isLogoutLoading = isLogoutLoading,
        onTitleClick = onTitleClick,
        modifier = modifier,
    )
}
