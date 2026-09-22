@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.pandeyji.aura.gallery

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.chrisbanes.haze.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== PANDEY JI GLOW ====================

@Composable
fun PandeyJiGlow() {
    val anim = rememberInfiniteTransition(label = "glow")
    val alpha by anim.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "alpha"
    )
    Text(
        "Developed By Pandey Ji 👑",
        color = Color(0xFFFFD700).copy(alpha = alpha),
        style = TextStyle(shadow = Shadow(Color(0xFFFFD700), blurRadius = 25f * alpha)),
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

// ==================== TOP BAR ====================

@Composable
fun TopBar(
    screen: String,
    hazeState: HazeState,
    blurVal: Float,
    isScrolling: Boolean,
    modifier: Modifier,
    onProfileClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    mediaCount: Int = 0,
    onSortClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val offsetY by animateDpAsState(
        targetValue = if (isScrolling) (-100).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "topBarSlide"
    )
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolling) 0f else 1f,
        animationSpec = tween(250),
        label = "topBarAlpha"
    )
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .offset(y = offsetY)
            .graphicsLayer(alpha = topBarAlpha)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .height(56.dp)
                .shadow(12.dp, RoundedCornerShape(28.dp))
                .hazeChild(
                    hazeState,
                    RoundedCornerShape(28.dp),
                    HazeStyle(
                        tint = Color.White.copy(alpha = 0.12f),
                        blurRadius = blurVal.dp,
                        noiseFactor = 0.05f
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchChange(it)
                    },
                    placeholder = {
                        Text(
                            if (mediaCount > 0) "Search $mediaCount photos & videos..."
                            else "Search photos, albums...",
                            color = Color.Gray, fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; onSearchChange("") }) {
                        Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
                // Sort button
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSortClick()
                }) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = "Sort",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Settings
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onProfileClick()
                }) {
                    Icon(
                        Icons.Default.Person, null,
                        tint = if (screen == "SETTINGS") Color(0xFFFFD700) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ==================== SELECTION BAR (Multi-Select Mode) ====================

@Composable
fun SelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enterAnim.animateTo(1f, spring(dampingRatio = 0.7f)) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleY = enterAnim.value, alpha = enterAnim.value)
            .background(Color(0xFF1A1A1A))
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Text(
                    text = "$selectedCount selected",
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Select All / Deselect
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectAll()
                }) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Select All",
                        tint = if (selectedCount == totalCount) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f)
                    )
                }
                // Share selected
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShare()
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                // Delete selected
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF4444))
                }
            }
        }
    }
}

// ==================== SORT BOTTOM SHEET ====================

@Composable
fun SortBottomSheet(
    currentSort: SortMode,
    onSortChange: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text("Sort By", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))

            SortMode.values().forEach { mode ->
                val isSelected = currentSort == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSortChange(mode) }
                        .background(
                            if (isSelected) Color(0xFFFFD700).copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (mode) {
                                SortMode.DATE_DESC, SortMode.DATE_ASC -> Icons.Default.DateRange
                                SortMode.NAME_ASC, SortMode.NAME_DESC -> Icons.Default.Edit
                                SortMode.SIZE_DESC, SortMode.SIZE_ASC -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color(0xFFFFD700) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ==================== ALBUM FILTER CHIPS ====================

@Composable
fun AlbumFilterChips(
    currentMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    hazeState: HazeState,
    blurVal: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Pair(ViewMode.ALL_MEDIA, "All Media"),
            Pair(ViewMode.ALBUMS, "Albums"),
            Pair(ViewMode.FAVORITES, "Favorites")
        ).forEach { (mode, label) ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                label = { Text(label, fontSize = 12.sp) },
                modifier = Modifier.hazeChild(
                    hazeState,
                    RoundedCornerShape(20.dp),
                    HazeStyle(
                        tint = if (currentMode == mode) Color(0xFFFFD700).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        blurRadius = blurVal.dp,
                        noiseFactor = 0.03f
                    )
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFD700).copy(alpha = 0.3f),
                    selectedLabelColor = Color(0xFFFFD700),
                    labelColor = Color.White.copy(alpha = 0.7f)
                )
            )
        }
    }
}

// ==================== BOTTOM DOCK ====================

@Composable
fun BottomDock(
    screen: String,
    hazeState: HazeState,
    blurVal: Float,
    isScrolling: Boolean,
    modifier: Modifier,
    onScreenChange: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val offsetY by animateDpAsState(
        targetValue = if (isScrolling) 100.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "bottomDockSlide"
    )
    val dockAlpha by animateFloatAsState(
        targetValue = if (isScrolling) 0f else 1f,
        animationSpec = tween(250),
        label = "dockAlpha"
    )

    Box(
        modifier = modifier
            .offset(y = offsetY)
            .graphicsLayer(alpha = dockAlpha)
            .padding(bottom = 20.dp, start = 24.dp, end = 24.dp)
            .fillMaxWidth()
            .height(68.dp)
            .hazeChild(
                state = hazeState,
                shape = RoundedCornerShape(34.dp),
                style = HazeStyle(
                    tint = Color.White.copy(alpha = 0.15f),
                    blurRadius = blurVal.dp,
                    noiseFactor = 0.05f
                )
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockButton(Icons.Default.Home, "Photos", screen == "GALLERY") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onScreenChange("GALLERY")
            }
            DockButton(Icons.Default.Search, "Explore", screen == "EXPLORE") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onScreenChange("EXPLORE")
            }
            DockButton(Icons.Default.Edit, "Studio", screen == "STUDIO") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onScreenChange("STUDIO")
            }
            DockButton(Icons.Default.Lock, "Vault", screen == "VAULT") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onScreenChange("VAULT")
            }
        }
    }
}

@Composable
fun DockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    val tint by animateColorAsState(
        targetValue = if (isActive) Color(0xFFFFD700) else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(300),
        label = "tint"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            color = tint,
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.offset(y = (-8).dp)
        )
    }
}

// ==================== SETTINGS HUB ====================

@Composable
fun SettingsHub(
    blurVal: Float,
    onBlurChange: (Float) -> Unit,
    gridColumns: Int,
    onGridColumnsChange: (Int) -> Unit,
    mediaCount: Int,
    albumCount: Int,
    totalSize: Long,
    hazeState: HazeState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .haze(state = hazeState)
            .padding(24.dp)
            .padding(top = 90.dp, bottom = 90.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AURA SETTINGS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

        Spacer(Modifier.height(20.dp))

        // Storage Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📊 Library Stats", color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("$mediaCount", "Photos & Videos")
                    StatItem("$albumCount", "Albums")
                    StatItem(formatFileSize(totalSize), "Total Size")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Blur Control
        Text("Liquid Glass Blur: ${blurVal.toInt()} dp", color = Color.Gray)
        Slider(
            value = blurVal,
            onValueChange = onBlurChange,
            valueRange = 10f..80f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD700),
                activeTrackColor = Color(0xFFFFD700)
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Grid Columns Control
        Text("Grid Columns: $gridColumns", color = Color.Gray)
        Slider(
            value = gridColumns.toFloat(),
            onValueChange = { onGridColumnsChange(it.toInt()) },
            valueRange = 3f..6f,
            steps = 2,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD700),
                activeTrackColor = Color(0xFFFFD700)
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("3", color = Color.Gray, fontSize = 11.sp)
            Text("4", color = Color.Gray, fontSize = 11.sp)
            Text("5", color = Color.Gray, fontSize = 11.sp)
            Text("6", color = Color.Gray, fontSize = 11.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E), contentColor = Color.White)
        ) {
            Text("🔒 Setup Secure Vault PIN", fontSize = 15.sp)
        }
        Spacer(Modifier.height(30.dp))
        PandeyJiGlow()
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color(0xFFFFD700), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color.Gray, fontSize = 11.sp)
    }
}

// ==================== GALLERY GRID ====================

@Composable
fun GalleryGrid(
    mediaList: List<Media>,
    hasPerm: Boolean,
    hazeState: HazeState,
    gridState: LazyGridState,
    viewMode: ViewMode,
    albums: List<Album>,
    selectedAlbum: Album?,
    gridColumns: Int = 4,
    isSelectMode: Boolean = false,
    selectedItems: Set<String> = emptySet(),
    onMediaClick: (Int) -> Unit,
    onMediaLongClick: (Int) -> Unit = {},
    onAlbumClick: (Album) -> Unit,
    onBackFromAlbum: () -> Unit
) {
    when (viewMode) {
        ViewMode.ALL_MEDIA -> {
            if (mediaList.isEmpty() && hasPerm) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center), color = Color(0xFFFFD700))
            } else {
                // Group media by date for timeline headers
                val groupedMedia = remember(mediaList) {
                    mediaList.mapIndexed { index, media ->
                        val label = getDateLabel(media.timestamp)
                        Triple(index, media, label)
                    }
                }
                val dateGroups = remember(groupedMedia) {
                    groupedMedia.groupBy { it.third }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize().haze(state = hazeState),
                    contentPadding = PaddingValues(top = 110.dp, bottom = 100.dp)
                ) {
                    dateGroups.forEach { (dateLabel, items) ->
                        // Date header spanning full width
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = dateLabel,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        // Media items for this date group
                        items(items.size) { i ->
                            val (originalIndex, media, _) = items[i]
                            MediaGridItem(
                                media = media,
                                isSelected = selectedItems.contains(media.uri),
                                isSelectMode = isSelectMode,
                                onClick = { onMediaClick(originalIndex) },
                                onLongClick = { onMediaLongClick(originalIndex) }
                            )
                        }
                    }
                }
            }
        }
        ViewMode.ALBUMS -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().haze(state = hazeState),
                contentPadding = PaddingValues(top = 110.dp, bottom = 100.dp, start = 8.dp, end = 8.dp)
            ) {
                items(albums.size) { index ->
                    AlbumCard(album = albums[index], onClick = { onAlbumClick(albums[index]) })
                }
            }
        }
        ViewMode.ALBUM_DETAIL -> {
            val albumMedia = mediaList.filter { it.bucketId == selectedAlbum?.bucketId }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackFromAlbum) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = selectedAlbum?.bucketName ?: "Album",
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                        Text(text = "${albumMedia.size} items", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize().haze(state = hazeState),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(albumMedia) { _, media ->
                        val originalIndex = mediaList.indexOf(media)
                        MediaGridItem(
                            media = media,
                            isSelected = selectedItems.contains(media.uri),
                            isSelectMode = isSelectMode,
                            onClick = { onMediaClick(originalIndex) },
                            onLongClick = { onMediaLongClick(originalIndex) }
                        )
                    }
                }
            }
        }
        ViewMode.FAVORITES -> {
            val favorites = mediaList.filter { it.isFavorite }
            if (favorites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().haze(state = hazeState), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FavoriteBorder, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No favorites yet", color = Color.White.copy(alpha = 0.5f))
                        Text("Tap ❤️ on any photo to add it here", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize().haze(state = hazeState),
                    contentPadding = PaddingValues(top = 110.dp, bottom = 100.dp)
                ) {
                    itemsIndexed(favorites) { _, media ->
                        val originalIndex = mediaList.indexOf(media)
                        MediaGridItem(
                            media = media,
                            isSelected = selectedItems.contains(media.uri),
                            isSelectMode = isSelectMode,
                            onClick = { onMediaClick(originalIndex) },
                            onLongClick = { onMediaLongClick(originalIndex) },
                            showFavoriteBadge = true
                        )
                    }
                }
            }
        }
    }
}

// ==================== MEDIA GRID ITEM ====================

@Composable
private fun MediaGridItem(
    media: Media,
    isSelected: Boolean = false,
    isSelectMode: Boolean = false,
    showFavoriteBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val itemAlpha = remember { Animatable(0f) }
    val itemScale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        launch { itemAlpha.animateTo(1f, tween(350)) }
        launch { itemScale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "selScale"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "borderAlpha"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .graphicsLayer(
                alpha = itemAlpha.value,
                scaleX = itemScale.value * selectionScale,
                scaleY = itemScale.value * selectionScale
            )
            .then(
                if (isSelected) Modifier.border(2.dp, Color(0xFFFFD700).copy(alpha = borderAlpha), RoundedCornerShape(4.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(media.uri).crossfade(true).build(),
            contentDescription = media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Video indicator
        if (media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            // Video duration badge
            if (media.duration > 0) {
                Text(
                    text = formatDuration(media.duration),
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        // Favorite badge
        if (showFavoriteBadge) {
            Icon(
                Icons.Default.Favorite, null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).padding(3.dp)
            )
        }
        // Selection checkbox
        if (isSelectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .background(
                        if (isSelected) Color(0xFFFFD700) else Color.Black.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ==================== ALBUM CARD ====================

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    val cardScale = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { cardScale.animateTo(1f, spring(dampingRatio = 0.7f)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .graphicsLayer(scaleX = cardScale.value, scaleY = cardScale.value)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(album.thumbnailUri).crossfade(true).build(),
                contentDescription = album.bucketName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(album.bucketName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${album.count} items", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

// ==================== MEDIA PAGER SCREEN ====================

@Composable
fun MediaPagerScreen(
    mediaList: List<Media>,
    initialIndex: Int,
    onBack: () -> Unit,
    onFavoriteToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit = {},
    onSetWallpaper: (String) -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaList.size }
    var currentZoom by remember { mutableFloatStateOf(1f) }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = currentZoom <= 1.05f,
        key = { mediaList[it].uri }
    ) { page ->
        MediaViewer(
            media = mediaList[page],
            onBack = onBack,
            isFavorite = mediaList[page].isFavorite,
            onFavoriteToggle = { onFavoriteToggle(page, it) },
            onDelete = { onDelete(page) },
            onSetWallpaper = { onSetWallpaper(mediaList[page].uri) },
            onZoomChanged = { zoom ->
                if (pagerState.currentPage == page) currentZoom = zoom
            }
        )
    }

    // Animated page indicator
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = mediaList.size > 1 && currentZoom <= 1.05f,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(300))
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${mediaList.size}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ==================== MEDIA VIEWER ====================

@Composable
fun MediaViewer(
    media: Media,
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteToggle: (Boolean) -> Unit = {},
    onDelete: () -> Unit = {},
    onSetWallpaper: () -> Unit = {},
    onZoomChanged: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showOverlay by remember { mutableStateOf(true) }
    var isFavoritedLocal by remember { mutableStateOf(isFavorite) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Delete confirmation in viewer
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this ${if (media.isVideo) "video" else "photo"}?", color = Color.White) },
            text = { Text("This action cannot be undone.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color(0xFFFFD700))
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    if (media.isVideo) {
        val exoPlayer = remember(media.uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(media.uri))
                prepare()
                playWhenReady = true
            }
        }

        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, exoPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) exoPlayer.play()
                else if (event == Lifecycle.Event.ON_PAUSE) exoPlayer.pause()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                exoPlayer.release()
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            MediaViewerOverlay(
                media = media,
                onClose = onBack,
                onFavorite = { isFavoritedLocal = it; onFavoriteToggle(it) },
                onInfoClick = { showInfoSheet = true },
                onDelete = { showDeleteConfirm = true },
                onSetWallpaper = onSetWallpaper,
                isFavorite = isFavoritedLocal,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val imageGestureModifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value, translationX = offset.x, translationY = offset.y)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                if (scale.value > 1f) {
                                    scale.animateTo(1f, spring(dampingRatio = 0.8f))
                                    offset = Offset.Zero
                                    onZoomChanged(1f)
                                } else {
                                    scale.animateTo(3f, spring(dampingRatio = 0.8f))
                                    onZoomChanged(3f)
                                }
                            }
                        },
                        onTap = { showOverlay = !showOverlay }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scope.launch {
                            val nextScale = (scale.value * zoom).coerceIn(1f, 5f)
                            scale.snapTo(nextScale)
                            onZoomChanged(nextScale)
                            if (nextScale > 1f) offset += pan else offset = Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (scale.value == 1f && dragAmount < -40f) {
                            change.consume()
                            showInfoSheet = true
                        }
                    }
                }

            AsyncImage(
                model = ImageRequest.Builder(context).data(media.uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = imageGestureModifier
            )

            // Overlay with animated visibility
            AnimatedVisibility(
                visible = showOverlay && scale.value <= 1.05f,
                enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MediaViewerOverlay(
                    media = media,
                    onClose = onBack,
                    onFavorite = { isFavoritedLocal = it; onFavoriteToggle(it) },
                    onInfoClick = { showInfoSheet = true },
                    onDelete = { showDeleteConfirm = true },
                    onSetWallpaper = onSetWallpaper,
                    isFavorite = isFavoritedLocal,
                    modifier = Modifier
                )
            }

            // Back button with animated visibility
            AnimatedVisibility(
                visible = showOverlay && scale.value <= 1.05f,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it },
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(top = 40.dp, start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }
    }

    if (showInfoSheet) {
        MediaInfoBottomSheet(media = media, onDismiss = { showInfoSheet = false })
    }
}

// ==================== MEDIA VIEWER OVERLAY ====================

@Composable
fun MediaViewerOverlay(
    media: Media,
    onClose: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onDelete: () -> Unit = {},
    onSetWallpaper: () -> Unit = {},
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .padding(12.dp)
            .hazeChild(
                state = hazeState,
                shape = RoundedCornerShape(20.dp),
                style = HazeStyle(tint = Color.White.copy(alpha = 0.15f), blurRadius = 30.dp, noiseFactor = 0.05f)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favorite
            OverlayButton(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "Fav",
                tint = if (isFavorite) Color(0xFFFFD700) else Color.White,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onFavorite(!isFavorite)
                }
            )
            // Info
            OverlayButton(icon = Icons.Default.Info, label = "Info", onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onInfoClick()
            })
            // Share
            OverlayButton(icon = Icons.Default.Share, label = "Share", onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (media.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(media.uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
            })
            // Wallpaper (only for images)
            if (!media.isVideo) {
                OverlayButton(icon = Icons.Default.Phone, label = "Wall", onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetWallpaper()
                })
            }
            // Delete
            OverlayButton(icon = Icons.Default.Delete, label = "Delete", tint = Color(0xFFFF6666), onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            })
        }
    }
}

@Composable
private fun OverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(text = label, color = tint.copy(alpha = 0.7f), fontSize = 8.sp)
    }
}

// ==================== MEDIA INFO BOTTOM SHEET ====================

@Composable
fun MediaInfoBottomSheet(media: Media, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateString = remember(media.timestamp) {
        val sdf = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(if (media.timestamp > 1000000000000L) media.timestamp else media.timestamp * 1000L))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Media Details", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFFFFD700).copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = if (media.isVideo) "VIDEO" else "IMAGE",
                        color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            InfoRow(label = "File Name", value = media.displayName.ifBlank { "Unknown" })
            InfoRow(label = "Album", value = media.bucketName)
            InfoRow(label = "Date Modified", value = dateString)
            if (media.fileSize > 0) {
                InfoRow(label = "File Size", value = formatFileSize(media.fileSize))
            }
            if (media.width > 0 && media.height > 0) {
                InfoRow(label = "Resolution", value = "${media.width} × ${media.height}")
                val megapixels = (media.width.toLong() * media.height.toLong()) / 1_000_000.0
                if (megapixels >= 0.1) {
                    InfoRow(label = "Megapixels", value = "%.1f MP".format(megapixels))
                }
            }
            if (media.mimeType.isNotBlank()) {
                InfoRow(label = "Format", value = media.mimeType)
            }
            if (media.isVideo && media.duration > 0) {
                InfoRow(label = "Duration", value = formatDuration(media.duration))
            }
            InfoRow(label = "Type", value = if (media.isVideo) "Video" else "Image")

            Spacer(Modifier.height(20.dp))
            PandeyJiGlow()
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ==================== KSU STYLE FLOATING BUTTON ====================

@Composable
fun KSUStyleFloatingButton(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "fab_glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glowAlpha"
    )
    Box(
        modifier = modifier
            .padding(16.dp)
            .size(56.dp)
            .graphicsLayer(scaleX = glowScale, scaleY = glowScale)
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color(0xFFFFD700).copy(alpha = glowAlpha))
            .background(Color(0xFF1A1A1A), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFFFD700).copy(alpha = glowAlpha), RoundedCornerShape(20.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Star, contentDescription = "Studio", tint = Color(0xFFFFD700), modifier = Modifier.size(26.dp))
    }
}

// ==================== DUMMY SCREEN ====================

@Composable
fun DummyScreen(screen: String, hazeState: HazeState) {
    val infiniteTransition = rememberInfiniteTransition(label = "dummy_float")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -12f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "floatY"
    )
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "iconAlpha"
    )
    Box(modifier = Modifier.fillMaxSize().haze(state = hazeState), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                when (screen) {
                    "EXPLORE" -> Icons.Default.Search
                    "AI STUDIO" -> Icons.Default.Edit
                    "VAULT" -> Icons.Default.Lock
                    else -> Icons.Default.Home
                },
                contentDescription = null,
                tint = Color(0xFFFFD700).copy(alpha = iconAlpha),
                modifier = Modifier.size(48.dp).graphicsLayer(translationY = floatY)
            )
            Spacer(Modifier.height(12.dp))
            Text(screen, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Coming Soon ✨", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        }
    }
}
