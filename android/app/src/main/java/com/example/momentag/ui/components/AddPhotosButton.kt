package com.example.momentag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.momentag.R
import com.example.momentag.ui.theme.Dimen

@Composable
fun AddPhotosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.Companion,
) {
    val addPhotosText = stringResource(R.string.tag_add_photos)
    val contentDesc = stringResource(R.string.cd_add_photos)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = contentDesc
                },
        contentAlignment = Alignment.Companion.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.Companion.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = addPhotosText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
