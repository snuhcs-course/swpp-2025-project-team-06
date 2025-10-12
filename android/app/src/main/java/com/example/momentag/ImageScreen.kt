package com.example.momentag

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import com.example.momentag.ui.theme.Background
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun imageScreen(
    imageUri: Uri?,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var tagSet by remember {
        mutableStateOf(
            listOf("#home", "#cozy", "#hobby", "#study", "#tool"),
        )
    }
    var isDeleteMode by remember { mutableStateOf(false) }

    val sheetState =
        rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded

    val overlappingTagsAlpha by animateFloatAsState(targetValue = if (isSheetExpanded) 0f else 1f, label = "tagsAlpha")
    val metadataAlpha by animateFloatAsState(targetValue = if (isSheetExpanded) 1f else 0f, label = "metadataAlpha")

    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    LaunchedEffect(key1 = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permission: String = Manifest.permission.ACCESS_MEDIA_LOCATION
            permissionLauncher.launch(permission)
        }
    }

    var dateTime: String? = null
    var latLong: DoubleArray? = null
    if (hasPermission) {
        try {
            if (imageUri != null) {
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    latLong = exifInterface.latLong
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    BackHandler(enabled = isDeleteMode) {
        isDeleteMode = false
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MomenTag",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Background,
                    ),
            )
        },
        sheetContent = {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "$dateTime\n(${latLong?.get(0)}, ${latLong?.get(1)})",
                    modifier = Modifier.alpha(metadataAlpha).padding(vertical = 16.dp),
                    lineHeight = 22.sp,
                )
                tagsSection(
                    tagSet = tagSet,
                )
                Spacer(modifier = Modifier.height(200.dp))
            }
        },
        sheetPeekHeight = 100.dp,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Box(modifier = Modifier) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(top = 50.dp)
                            .aspectRatio(0.7f)
                            .clickable {
                                if (isDeleteMode) isDeleteMode = false
                            }.align(Alignment.BottomCenter),
                    contentScale = ContentScale.Crop,
                )

                val scrollState = rememberScrollState()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp)
                            .padding(bottom = 8.dp)
                            .alpha(overlappingTagsAlpha),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tagSet.forEach { tagName ->
                        Box(
                            modifier =
                                Modifier.combinedClickable(
                                    onLongClick = { isDeleteMode = true },
                                    onClick = { /* TODO */ },
                                ),
                        ) {
                            tagXMode(
                                text = tagName,
                                isDeleteMode = isDeleteMode,
                                onDismiss = {
                                    tagSet = tagSet - tagName
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = { /* TODO */ },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tag", tint = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun tagsSection(
    tagSet: List<String>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tagSet.forEach { tagName ->
            Box {
                tag(
                    text = tagName,
                )
            }
        }
        IconButton(onClick = { /* TODO */ }, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tag", tint = Color.Gray)
        }
    }
}
