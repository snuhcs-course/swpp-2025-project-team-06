package com.example.momentag

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage

@Composable
fun ImageGridUriItem(imageUri: Uri, navController: NavController) {
    Box(modifier = Modifier) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 12.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .align(Alignment.BottomCenter)
                .clickable {
                    navController.navigate(Screen.Image.createRoute(imageUri))
                },
            contentScale = ContentScale.Crop,
        )
    }
}
