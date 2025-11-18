package com.example.momentag.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon

/**
 * 📦 CreateTagButton — Home / Gallery 등에서 공용으로 쓰는 버튼
 * @param text 버튼에 표시할 텍스트 (기본값: "Create Tag")
 * @param enabled 버튼 활성화 여부
 * @param modifier 위치 및 여백용 Modifier
 * @param onClick 클릭 시 동작 (화면별로 다르게 주입)
 */
@Composable
fun CreateTagButton(
    modifier: Modifier = Modifier,
    text: String = "Create Tag",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        StandardIcon.Icon(
            imageVector = Icons.Default.AddCircle,
            contentDescription = text,
            sizeRole = IconSizeRole.Navigation,
            intent = IconIntent.Inverse,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
