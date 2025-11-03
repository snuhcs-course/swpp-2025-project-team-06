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
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word

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
        placeholder = { Text(placeholder, color = Color.Gray) },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color(0xFFFBC4AB).copy(alpha = 0.3f),
                unfocusedContainerColor = Color(0xFFFBC4AB).copy(alpha = 0.3f),
                unfocusedTextColor = Color.Black,
                focusedTextColor = Color.Black,
                disabledTextColor = Color.Black,
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(searchText) }),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.Gray,
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
        placeholder = { Text(placeholder, color = Temp_word) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Semi_background,
                unfocusedContainerColor = Semi_background,
                unfocusedTextColor = Word,
                disabledTextColor = Word,
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "검색 실행")
            }
        },
    )
}
