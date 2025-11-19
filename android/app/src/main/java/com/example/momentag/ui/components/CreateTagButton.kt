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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momentag.R
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon

/**
 * CreateTagButton — Shared button component used across Home / Gallery screens
 * @param text Button text to display (default: "Create Tag")
 * @param enabled Whether the button is enabled
 * @param modifier Modifier for positioning and spacing
 * @param onClick Click action (injected differently per screen)
 */
@Composable
fun CreateTagButton(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.button_create_tag),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        StandardIcon.Icon(
            imageVector = Icons.Default.AddCircle,
            contentDescription = stringResource(R.string.cd_create_tag),
            sizeRole = IconSizeRole.Navigation,
            intent = IconIntent.Inverse,
        )
        Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
        Text(text = text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}
