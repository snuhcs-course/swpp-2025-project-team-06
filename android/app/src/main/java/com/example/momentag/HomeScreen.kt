package com.example.momentag

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.Tag
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.momentag.viewmodel.ServerViewModel
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import com.example.momentag.model.Tag

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    /*
    * TODO: USE ServerViewModel
     */
    val localViewModel: LocalViewModel = viewModel(factory = ViewModelFactory(context))
    val imageUris by localViewModel.image.collectAsState()
    var tags by remember {
        mutableStateOf(
            listOf(
                "#home",
                "#cozy",
                "#hobby",
                "#study",
                "#tool",
                "#food",
                "#dream",
                "#travel",
                "#nature",
                "#animal",
                "#fashion",
                "#sport",
                "#work"
            )
        )
    }
    var imageTagPairs = imageUris.take(13).zip(tags)
    
    var only_tag by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                hasPermission = true
            }
        }

    )

    if (hasPermission) {
        LaunchedEffect(Unit) {
            localViewModel.getImages()
        }
    }

    LaunchedEffect(key1 = true) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }


    Scaffold(
        topBar = { },
        bottomBar = { },
        floatingActionButton = { },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "MomenTag",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                modifier = Modifier
                    .clickable { navController.navigate(Screen.LocalGallery.route) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Search for Photo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Search for Photo", fontSize = 18.sp, fontFamily = FontFamily.Serif)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Camera Icon")
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            var searchText by remember { mutableStateOf("") }
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search Anything...", color = Temp_word) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Semi_background,
                    unfocusedContainerColor = Semi_background,
                    unfocusedTextColor = Word,
                    disabledTextColor = Word,
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Change View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .background(Semi_background, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    Row {
                        Icon(Icons.Default.GridView, contentDescription = "Grid View", tint = Color.Gray, modifier = Modifier.clickable { only_tag = false })
                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "List View", tint = Color.Gray, modifier = Modifier.clickable { only_tag = true })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))


            // Album
            if(!only_tag) {
                if (hasPermission) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(imageTagPairs) { (uris, tag) ->
                            TagGridItem(tag, uris, navController)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(imageTagPairs) { (uris, tag) ->
                            TagGridItem(tag)
                        }
                    }

                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    imageTagPairs.forEach { pair ->
                        val (uris, tag) = pair
                        TagX(
                            text = tag,
                            onDismiss = {
                                imageTagPairs = imageTagPairs - pair
                            }
                        )
                    }
                }

            }

            // Create Tag
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.clickable { /* TODO */ },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Create Tag",
                    tint = Button
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Create Tag", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


/*
* TODO : change code with imageUrl
 */
@Composable
fun TagGridItem(tagName: String, imageUri: Uri?, navController: NavController) {
    Box(modifier = Modifier) {
        if(imageUri != null){
            AsyncImage(
                model = imageUri,
                contentDescription = tagName,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .align(Alignment.BottomCenter)
                    .clickable {
                        navController.navigate(Screen.Album.createRoute(tagName))
                               },
                contentScale = ContentScale.Crop
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

        Text(
            text = tagName,
            color = Word,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp)
                .background(
                    color = Tag,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TagGridItem(tagName: String) {
    Box(modifier = Modifier) {

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

        Text(
            text = tagName,
            color = Word,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp)
                .background(
                    color = Tag,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}