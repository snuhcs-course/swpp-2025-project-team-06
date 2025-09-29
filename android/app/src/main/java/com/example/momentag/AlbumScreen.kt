package com.example.momentag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Picture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    tagName: String,
    navController: NavController,
    onNavigateBack: () -> Unit
) {
    var images by remember {
        mutableStateOf(
            listOf(
                "/storage/emulated/0/Download/Quick Share/20250920_173818.jpg",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (4).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (21).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (6).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (22).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (8).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (23).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker.png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (13).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (11).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (16).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (5).png",
                "/storage/emulated/0/Download/KakaoTalk/fox_all/fox1/sticker (17).png"
            )
        )
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MomenTag",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = tagName,
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                color = Color.Black.copy(alpha = 0.5f)
            )


            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(images) { path ->
                    ImageGridItem(path, navController)
                }
            }
        }
    }
}

@Composable
fun ImageGridItem(imagePath: String, navController: NavController) {
    val context = LocalContext.current

    val imageUri = getUriFromPath(context, imagePath)
    Box(modifier = Modifier) {
        if(imageUri != null){
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .align(Alignment.BottomCenter)
                    .clickable {
                        navController.navigate(Screen.Image.createRoute(imagePath))
                    },
                contentScale = ContentScale.Crop,

            )
        } else {
            Spacer(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .aspectRatio(1f)
                    .background(
                        color = Picture,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .align(Alignment.BottomCenter)
                    .clickable { /* TODO */ }
            )
        }
    }
}
