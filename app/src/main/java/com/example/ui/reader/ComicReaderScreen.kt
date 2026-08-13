package com.example.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComicReaderScreen(
    viewModel: ReaderViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showThumbnailSheet by remember { mutableStateOf(false) }
    var showBookmarkSheet by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkTitleInput by remember { mutableStateOf("") }

    val activityContext = LocalContext.current
    DisposableEffect(uiState.isControlsVisible) {
        val window = (activityContext as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (!uiState.isControlsVisible) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        val window = (activityContext as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Extracting Comic Pages...", color = Color.White)
            }
        }
        return
    }

    if (uiState.errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.errorMessage ?: "Error opening comic",
                    color = Color.Red,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBackClick) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
        return
    }

    val pageCount = uiState.pages.size
    if (pageCount == 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No image pages found in comic.", color = Color.White)
        }
        return
    }

    // Pager state
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    // LazyColumn state for Webtoon mode
    val webtoonListState = rememberLazyListState(
        initialFirstVisibleItemIndex = uiState.currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    )

    // Image preloader for adjacent pages
    val imageLoader = Coil.imageLoader(activityContext)
    LaunchedEffect(uiState.currentPageIndex, uiState.pages) {
        val pages = uiState.pages
        val cur = uiState.currentPageIndex
        if (pages.isNotEmpty()) {
            val toPrefetch = listOf(cur - 1, cur + 1, cur + 2, cur + 3)
            for (idx in toPrefetch) {
                if (idx in pages.indices) {
                    val request = ImageRequest.Builder(activityContext)
                        .data(pages[idx])
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }

    // Bidirectional Sync 1: External ViewModel state changes (initial saved page, slider, grid picker) -> UI Scroll
    LaunchedEffect(uiState.currentPageIndex, uiState.scrollMode) {
        val target = uiState.currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (uiState.scrollMode == "PAGER") {
            if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
        } else if (uiState.scrollMode == "WEBTOON") {
            if (!webtoonListState.isScrollInProgress && webtoonListState.firstVisibleItemIndex != target) {
                webtoonListState.scrollToItem(target)
            }
        }
    }

    // Bidirectional Sync 2: User touch gesture swipe in Pager -> ViewModel state
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (uiState.scrollMode == "PAGER" && page != uiState.currentPageIndex) {
                    viewModel.onPageChanged(page)
                }
            }
    }

    // Bidirectional Sync 3: User touch gesture scroll in Webtoon -> ViewModel state
    LaunchedEffect(webtoonListState) {
        snapshotFlow { webtoonListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (uiState.scrollMode == "WEBTOON" && index != uiState.currentPageIndex) {
                    viewModel.onPageChanged(index)
                }
            }
    }

    val bgColor = Color(uiState.backgroundColor)

    val handlePageTap: (Offset, IntSize, Float) -> Unit = remember(
        uiState.isControlsVisible,
        uiState.scrollMode,
        uiState.readingDirection,
        uiState.currentPageIndex,
        pageCount
    ) {
        { offset, containerSize, currentScale ->
            if (uiState.isControlsVisible) {
                // When controls are visible, tapping ANYWHERE on the image dismisses controls
                viewModel.toggleControls()
            } else if (currentScale > 1.2f) {
                viewModel.toggleControls()
            } else {
                val width = containerSize.width.toFloat()
                val height = containerSize.height.toFloat()

                if (uiState.scrollMode == "WEBTOON") {
                    val topThreshold = height * 0.25f
                    val bottomThreshold = height * 0.75f
                    when {
                        offset.y < topThreshold -> {
                            val prev = (uiState.currentPageIndex - 1).coerceAtLeast(0)
                            scope.launch { webtoonListState.animateScrollToItem(prev) }
                        }
                        offset.y > bottomThreshold -> {
                            val next = (uiState.currentPageIndex + 1).coerceAtMost(pageCount - 1)
                            scope.launch { webtoonListState.animateScrollToItem(next) }
                        }
                        else -> {
                            viewModel.toggleControls()
                        }
                    }
                } else { // PAGER
                    val leftThreshold = width * 0.35f
                    val rightThreshold = width * 0.65f
                    val isRtl = uiState.readingDirection == "RTL"

                    when {
                        offset.x < leftThreshold -> {
                            val target = if (isRtl) uiState.currentPageIndex + 1 else uiState.currentPageIndex - 1
                            if (target in 0 until pageCount) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        }
                        offset.x > rightThreshold -> {
                            val target = if (isRtl) uiState.currentPageIndex - 1 else uiState.currentPageIndex + 1
                            if (target in 0 until pageCount) {
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        }
                        else -> {
                            viewModel.toggleControls()
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = bgColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgColor)
        ) {
            // MAIN READER CANVAS
            if (uiState.scrollMode == "WEBTOON") {
                // Continuous Vertical Webtoon Scroll
                LazyColumn(
                    state = webtoonListState,
                    contentPadding = PaddingValues(bottom = 0.dp),
                    verticalArrangement = Arrangement.Top,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = uiState.pages,
                        key = { index, pageFile -> pageFile.absolutePath },
                        contentType = { _, _ -> "comic_page" }
                    ) { index, pageFile ->
                        ZoomablePageImage(
                            pageFile = pageFile,
                            scaleType = if (uiState.scaleType == "FIT_SCREEN") "FIT_WIDTH" else uiState.scaleType,
                            isCropMarginsEnabled = uiState.isCropMarginsEnabled,
                            colorFilterType = uiState.colorFilter,
                            onTap = handlePageTap,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Page-by-Page Pager Mode
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = uiState.readingDirection == "RTL",
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ZoomablePageImage(
                            pageFile = uiState.pages[page],
                            scaleType = uiState.scaleType,
                            isCropMarginsEnabled = uiState.isCropMarginsEnabled,
                            colorFilterType = uiState.colorFilter,
                            onTap = handlePageTap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // BRIGHTNESS OVERLAY
            if (uiState.brightness < 0.99f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (1.0f - uiState.brightness).coerceIn(0f, 0.85f)))
                )
            }

            // FLOATING PAGE BADGE (Shown when control bars are hidden)
            AnimatedVisibility(
                visible = !uiState.isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${uiState.currentPageIndex + 1} / ${uiState.totalPages}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }


            // TOP TOOLBAR OVERLAY
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Page ${uiState.currentPageIndex + 1} of ${uiState.totalPages} • ${uiState.format}",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.toggleFavorite() }) {
                                Icon(
                                    if (uiState.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "Favorite",
                                    tint = if (uiState.isFavorite) Color(0xFFFFC107) else Color.White
                                )
                            }
                            IconButton(onClick = {
                                bookmarkTitleInput = "Page ${uiState.currentPageIndex + 1}"
                                showAddBookmarkDialog = true
                            }) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = "Add Bookmark", tint = Color.White)
                            }
                            IconButton(onClick = { showBookmarkSheet = true }) {
                                Icon(Icons.Default.Bookmark, contentDescription = "Bookmarks List", tint = Color.White)
                            }
                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Reader Settings", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }

            // BOTTOM TOOLBAR OVERLAY
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Slider seekbar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${uiState.currentPageIndex + 1}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(36.dp)
                            )

                            Slider(
                                value = uiState.currentPageIndex.toFloat(),
                                onValueChange = { valIdx ->
                                    val target = valIdx.toInt().coerceIn(0, pageCount - 1)
                                    viewModel.onPageChanged(target)
                                    scope.launch {
                                        if (uiState.scrollMode == "PAGER") {
                                            pagerState.scrollToPage(target)
                                        } else {
                                            webtoonListState.scrollToItem(target)
                                        }
                                    }
                                },
                                valueRange = 0f..(pageCount - 1).toFloat().coerceAtLeast(1f),
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "$pageCount",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(36.dp)
                            )
                        }

                        // Quick Actions Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Prev / Next buttons
                            Row {
                                IconButton(
                                    onClick = {
                                        if (uiState.currentPageIndex > 0) {
                                            val prev = uiState.currentPageIndex - 1
                                            viewModel.onPageChanged(prev)
                                            scope.launch {
                                                if (uiState.scrollMode == "PAGER") pagerState.animateScrollToPage(prev)
                                                else webtoonListState.animateScrollToItem(prev)
                                            }
                                        }
                                    },
                                    enabled = uiState.currentPageIndex > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Prev Page", tint = Color.White)
                                }

                                IconButton(
                                    onClick = {
                                        if (uiState.currentPageIndex < pageCount - 1) {
                                            val next = uiState.currentPageIndex + 1
                                            viewModel.onPageChanged(next)
                                            scope.launch {
                                                if (uiState.scrollMode == "PAGER") pagerState.animateScrollToPage(next)
                                                else webtoonListState.animateScrollToItem(next)
                                            }
                                        }
                                    },
                                    enabled = uiState.currentPageIndex < pageCount - 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Page", tint = Color.White)
                                }
                            }

                            // Mode Pills & Crop Toggle
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uiState.scrollMode == "PAGER",
                                    onClick = { viewModel.setScrollMode("PAGER") },
                                    label = { Text("Pager", color = Color.White) }
                                )
                                FilterChip(
                                    selected = uiState.scrollMode == "WEBTOON",
                                    onClick = { viewModel.setScrollMode("WEBTOON") },
                                    label = { Text("Webtoon", color = Color.White) }
                                )
                                FilterChip(
                                    selected = uiState.isCropMarginsEnabled,
                                    onClick = { viewModel.toggleCropMargins() },
                                    label = { Text("Crop", color = Color.White) }
                                )
                            }


                            // Thumbnail Overview Sheet Trigger
                            IconButton(onClick = { showThumbnailSheet = true }) {
                                Icon(Icons.Default.GridOn, contentDescription = "Page Grid", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // THUMBNAIL OVERVIEW BOTTOM SHEET
    if (showThumbnailSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThumbnailSheet = false }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Page Thumbnails (${uiState.pages.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(350.dp)
                ) {
                    itemsIndexed(uiState.pages) { index, file ->
                        Card(
                            modifier = Modifier
                                .clickable {
                                    viewModel.onPageChanged(index)
                                    scope.launch {
                                        if (uiState.scrollMode == "PAGER") pagerState.scrollToPage(index)
                                        else webtoonListState.scrollToItem(index)
                                    }
                                    showThumbnailSheet = false
                                }
                                .clip(RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == uiState.currentPageIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(file)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.7f)
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // READER SETTINGS BOTTOM SHEET
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Reader Customization",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reading Direction
                Text("Reading Direction", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    FilterChip(
                        selected = uiState.readingDirection == "LTR",
                        onClick = { viewModel.setReadingDirection("LTR") },
                        label = { Text("LTR (Western)") }
                    )
                    FilterChip(
                        selected = uiState.readingDirection == "RTL",
                        onClick = { viewModel.setReadingDirection("RTL") },
                        label = { Text("RTL (Manga)") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scale Mode
                Text("Image Fit Scale Mode", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    FilterChip(
                        selected = uiState.scaleType == "FIT_SCREEN",
                        onClick = { viewModel.setScaleType("FIT_SCREEN") },
                        label = { Text("Fit Screen") }
                    )
                    FilterChip(
                        selected = uiState.scaleType == "FIT_WIDTH",
                        onClick = { viewModel.setScaleType("FIT_WIDTH") },
                        label = { Text("Fit Width") }
                    )
                    FilterChip(
                        selected = uiState.scaleType == "FIT_HEIGHT",
                        onClick = { viewModel.setScaleType("FIT_HEIGHT") },
                        label = { Text("Fit Height") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Smart Crop Margins
                Text("Border Cropping", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    FilterChip(
                        selected = !uiState.isCropMarginsEnabled,
                        onClick = { if (uiState.isCropMarginsEnabled) viewModel.toggleCropMargins() },
                        label = { Text("Off (Original)") }
                    )
                    FilterChip(
                        selected = uiState.isCropMarginsEnabled,
                        onClick = { if (!uiState.isCropMarginsEnabled) viewModel.toggleCropMargins() },
                        label = { Text("Crop Borders / Fill") }
                    )
                }


                Spacer(modifier = Modifier.height(12.dp))

                // Color Filter Options
                Text("Color Effect Filter", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    FilterChip(
                        selected = uiState.colorFilter == "DEFAULT",
                        onClick = { viewModel.setColorFilter("DEFAULT") },
                        label = { Text("Normal") }
                    )
                    FilterChip(
                        selected = uiState.colorFilter == "INVERT",
                        onClick = { viewModel.setColorFilter("INVERT") },
                        label = { Text("Invert (Night Mode)") }
                    )
                    FilterChip(
                        selected = uiState.colorFilter == "GRAYSCALE",
                        onClick = { viewModel.setColorFilter("GRAYSCALE") },
                        label = { Text("Grayscale") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reader Dim Brightness
                Text("Screen Overlay Brightness", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Brightness6,
                        contentDescription = "Brightness",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Slider(
                        value = uiState.brightness,
                        onValueChange = { viewModel.setBrightness(it) },
                        valueRange = 0.2f..1.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(uiState.brightness * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Background Color
                Text("Background Theme", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    val bgOptions = listOf(
                        0xFF000000 to "Pitch Black",
                        0xFF1E1E1E to "Charcoal",
                        0xFFFAF0E6 to "Sepia",
                        0xFFFFFFFF to "White"
                    )

                    bgOptions.forEach { (colorVal, name) ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .clickable { viewModel.setBackgroundColor(colorVal) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.backgroundColor == colorVal) {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = name,
                                    tint = if (colorVal == 0xFFFFFFFF || colorVal == 0xFFFAF0E6) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // BOOKMARKS LIST BOTTOM SHEET
    if (showBookmarkSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarkSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Saved Bookmarks",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.bookmarks.isEmpty()) {
                    Text("No bookmarks saved yet. Tap bookmark icon on top to save current page.")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(260.dp)
                    ) {
                        itemsIndexed(uiState.bookmarks) { _, bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onPageChanged(bookmark.pageIndex)
                                        scope.launch {
                                            if (uiState.scrollMode == "PAGER") pagerState.scrollToPage(bookmark.pageIndex)
                                            else webtoonListState.scrollToItem(bookmark.pageIndex)
                                        }
                                        showBookmarkSheet = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(bookmark.title, style = MaterialTheme.typography.titleMedium)
                                    Text("Page ${bookmark.pageIndex + 1}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Bookmark")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ADD BOOKMARK DIALOG
    if (showAddBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text("Add Bookmark") },
            text = {
                Column {
                    Text("Bookmark title for Page ${uiState.currentPageIndex + 1}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bookmarkTitleInput,
                        onValueChange = { bookmarkTitleInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addBookmark(bookmarkTitleInput)
                        showAddBookmarkDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ZoomablePageImage(
    pageFile: File,
    scaleType: String,
    isCropMarginsEnabled: Boolean = false,
    colorFilterType: String = "DEFAULT",
    onTap: (Offset, IntSize, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var scale by remember(pageFile) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageFile) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageFile) { mutableFloatStateOf(0f) }

    val contentScale = when {
        isCropMarginsEnabled -> ContentScale.Crop
        scaleType == "FIT_WIDTH" -> ContentScale.FillWidth
        scaleType == "FIT_HEIGHT" -> ContentScale.FillHeight
        else -> ContentScale.Fit
    }

    val colorFilter = remember(colorFilterType) {
        when (colorFilterType) {
            "INVERT" -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f
                    )
                )
            )
            "GRAYSCALE" -> ColorFilter.colorMatrix(
                ColorMatrix().apply { setToSaturation(0f) }
            )
            else -> null
        }
    }

    val context = LocalContext.current
    val imageRequest = remember(pageFile) {
        val displayMetrics = context.resources.displayMetrics
        val widthPx = displayMetrics.widthPixels
        val heightPx = displayMetrics.heightPixels
        ImageRequest.Builder(context)
            .data(pageFile)
            .size(widthPx, heightPx)
            .crossfade(false)
            .allowHardware(true)
            .precision(coil.size.Precision.INEXACT)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier
            .pointerInput(pageFile) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (scale > 1.05f) {
                        val maxOffsetX = (size.width * (scale - 1f)) / 2f
                        val maxOffsetY = (size.height * (scale - 1f)) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    } else {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(pageFile) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = { offset ->
                        onTap(offset, size, scale)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Comic Page",
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scaleType == "FIT_HEIGHT") Modifier.fillMaxHeight() else Modifier)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

