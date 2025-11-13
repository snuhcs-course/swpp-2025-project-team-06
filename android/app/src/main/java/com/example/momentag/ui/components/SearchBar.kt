package com.example.momentag.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * 검색바 컴포넌트 (내부 상태 관리 버전)
 * HomeScreen, SearchResultScreen 등에서 재사용
 *
 * @param onSearch 검색 실행 콜백 (검색어를 파라미터로 받음)
 * @param modifier Modifier
 * @param placeholder 플레이스홀더 텍스트 (기본: "Search Anything...")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search Photos",
) {
    var searchText by remember { mutableStateOf("") }

    TextField(
        value = searchText,
        onValueChange = { searchText = it },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(searchText) }),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * 검색바 컴포넌트 (외부 상태 관리 버전)
 * 검색어를 외부에서 제어해야 할 때 사용
 *
 * @param value 현재 검색어
 * @param onValueChange 검색어 변경 콜백
 * @param onSearch 검색 실행 콜백
 * @param modifier Modifier
 * @param placeholder 플레이스홀더 텍스트 (기본: "Search Anything...")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarControlledCustom(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search Anything...",
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
            }
        },
    )
}
