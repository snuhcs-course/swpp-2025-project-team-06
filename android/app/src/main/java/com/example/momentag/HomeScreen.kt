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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.momentag.model.LogoutState
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.AuthViewModel
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory(context))
    val logoutState by authViewModel.logoutState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                "#work",
            ),
        )
    }
    var imageTagPairs = imageUris.take(13).zip(tags) // NOTE: 기존 로직 그대로 유지

    var onlyTag by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted -> if (isGranted) hasPermission = true },
        )

    // 권한 요청 및 이미지 로드
    LaunchedEffect(Unit) {
        val permission = requiredImagePermission()
        permissionLauncher.launch(permission)
    }
    if (hasPermission) {
        LaunchedEffect(Unit) {
            localViewModel.getImages()
        }
    }
    // 성공 시 로그인 화면으로 이동(백스택 초기화)
    LaunchedEffect(logoutState) {
        when (logoutState) {
            is LogoutState.Success -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0)
                    launchSingleTop = true
                }
            }
            is LogoutState.Error -> {
                val msg = (logoutState as LogoutState.Error).message ?: "Logout failed"
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MomenTag",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        modifier =
                            Modifier.clickable {
                                navController.navigate(Screen.LocalGallery.route)
                            },
                    )
                },
                actions = {
                    when (logoutState) {
                        is LogoutState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        else -> {
                            TextButton(
                                onClick = { authViewModel.logout() },
                            ) {
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
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Background,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { },
        containerColor = Background,
        floatingActionButton = {
            CreateTagButton(
                modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
                text = "Create Tag",
                onClick = { },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            SearchHeader()

            Spacer(modifier = Modifier.height(8.dp))
            SearchBar(
                onSearch = { query ->
                    if (query.isNotEmpty()) {
                        navController.navigate(Screen.SearchResult.createRoute(query))
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            ViewToggle(
                onlyTag = onlyTag,
                onToggle = { onlyTag = it },
            )

            Spacer(modifier = Modifier.height(16.dp))
            MainContent(
                hasPermission = hasPermission,
                onlyTag = onlyTag,
                imageTagPairs = imageTagPairs,
                onRemoveTagPair = { pair -> imageTagPairs = imageTagPairs - pair },
                navController = navController,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// -------------------- Helpers --------------------
@Composable
private fun SearchHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Search for Photo", fontSize = 18.sp, fontFamily = FontFamily.Serif)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Camera Icon")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(onSearch: (String) -> Unit) {
    var searchText by remember { mutableStateOf("") }

    TextField(
        value = searchText,
        onValueChange = { searchText = it },
        placeholder = { Text("Search Anything...", color = Temp_word) },
        modifier = Modifier.fillMaxWidth(),
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
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
        keyboardActions =
            androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSearch(searchText) },
            ),
        trailingIcon = {
            IconButton(onClick = { onSearch(searchText) }) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "검색 실행")
            }
        },
    )
}

@Composable
private fun ViewToggle(
    onlyTag: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier =
                Modifier
                    .background(Semi_background, RoundedCornerShape(8.dp))
                    .padding(4.dp),
        ) {
            Row {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = "Grid View",
                    tint = if (!onlyTag) Color.White else Color.Gray,
                    modifier = Modifier.clickable { onToggle(false) },
                )
                Icon(
                    Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "List View",
                    tint = if (onlyTag) Color.White else Color.Gray,
                    modifier = Modifier.clickable { onToggle(true) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainContent(
    hasPermission: Boolean,
    onlyTag: Boolean,
    imageTagPairs: List<Pair<Uri, String>>,
    onRemoveTagPair: (Pair<Uri, String>) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (!onlyTag) {
        if (hasPermission) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(imageTagPairs) { (uri, tag) ->
                    TagGridItem(tag, uri, navController)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(imageTagPairs) { (_, tag) ->
                    TagGridItem(tag)
                }
            }
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            imageTagPairs.forEach { pair ->
                val (_, tag) = pair
                tagX(
                    text = tag,
                    onDismiss = { onRemoveTagPair(pair) },
                )
            }
        }
    }
}

@Composable
private fun Deprecated_CreateTagRow() { /* replaced by CreateTagButton component */ }

// 권한 헬퍼: 기존 분기 로직 그대로 함수로만 분리
private fun requiredImagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/*
* TODO : change code with imageUrl
 */
@Composable
fun TagGridItem(
    tagName: String,
    imageUri: Uri?,
    navController: NavController,
) {
    Box(modifier = Modifier) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = tagName,
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .align(Alignment.BottomCenter)
                        .clickable {
                            navController.navigate(Screen.Album.createRoute(tagName))
                        },
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .aspectRatio(1f)
                        .background(
                            color = Picture,
                            shape = RoundedCornerShape(16.dp),
                        ).align(Alignment.BottomCenter)
                        .clickable { /* TODO */ },
            )
        }

        Text(
            text = tagName,
            color = Word,
            fontSize = 12.sp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp)
                    .background(
                        color = TagColor,
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun TagGridItem(tagName: String) {
    Box(modifier = Modifier) {
        Spacer(
            modifier =
                Modifier
                    .padding(top = 12.dp)
                    .aspectRatio(1f)
                    .background(
                        color = Picture,
                        shape = RoundedCornerShape(16.dp),
                    ).align(Alignment.BottomCenter)
                    .clickable { /* TODO */ },
        )

        Text(
            text = tagName,
            color = Word,
            fontSize = 12.sp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp)
                    .background(
                        color = TagColor,
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
