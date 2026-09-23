package com.pandeyji.aura.gallery

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        onPermissionResult?.invoke(isGranted)
    }

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    // For Android 11+ scoped storage delete
    lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    var onDeleteResult: ((Boolean) -> Unit)? = null

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deleteRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            onDeleteResult?.invoke(result.resultCode == Activity.RESULT_OK)
        }

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        setContent {
            AuraGalleryApp(
                hasInitialPermission = hasPermission,
                onRequestPermission = { callback ->
                    onPermissionResult = callback
                    requestPermissions()
                }
            )
        }
    }
}

// ==================== FAVORITES PERSISTENCE ====================

object FavoritesManager {
    private const val PREFS_NAME = "aura_favorites"
    private const val KEY_FAVS = "fav_uris"

    fun loadFavorites(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVS, emptySet()) ?: emptySet()
    }

    fun saveFavorites(context: Context, favUris: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVS, favUris)
            .apply()
    }

    fun toggleFavorite(context: Context, uri: String, isFav: Boolean) {
        val current = loadFavorites(context).toMutableSet()
        if (isFav) current.add(uri) else current.remove(uri)
        saveFavorites(context, current)
    }
}

// ==================== DELETE HELPER ====================

object DeleteHelper {
    fun deleteMedia(context: Context, mediaItems: List<Media>, onResult: (Boolean) -> Unit) {
        try {
            val activity = context as? MainActivity ?: return onResult(false)
            val uris = mediaItems.map { Uri.parse(it.uri) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ uses MediaStore.createDeleteRequest
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                activity.onDeleteResult = onResult
                activity.deleteRequestLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } else {
                // Pre-Android 11: delete directly
                var deleted = 0
                uris.forEach { uri ->
                    deleted += context.contentResolver.delete(uri, null, null)
                }
                onResult(deleted > 0)
            }
        } catch (e: Exception) {
            onResult(false)
        }
    }
}

// ==================== WALLPAPER HELPER ====================

object WallpaperHelper {
    fun setAsWallpaper(context: Context, uri: String) {
        try {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
            val wallpaperManager = WallpaperManager.getInstance(context)
            inputStream?.use { wallpaperManager.setStream(it) }
        } catch (e: Exception) { }
    }
}

// ==================== DATE GROUPING HELPER ====================

fun getDateLabel(timestampSeconds: Long): String {
    val now = Calendar.getInstance()
    val mediaDate = Calendar.getInstance().apply {
        timeInMillis = if (timestampSeconds > 1000000000000L) timestampSeconds else timestampSeconds * 1000L
    }

    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameDay(mediaDate, today) -> "Today"
        isSameDay(mediaDate, yesterday) -> "Yesterday"
        isSameWeek(mediaDate, now) -> "This Week"
        isSameMonth(mediaDate, now) -> "This Month"
        mediaDate.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> {
            val sdf = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault())
            sdf.format(mediaDate.time)
        }
        else -> {
            val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            sdf.format(mediaDate.time)
        }
    }
}

private fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isSameWeek(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.WEEK_OF_YEAR) == b.get(Calendar.WEEK_OF_YEAR)

private fun isSameMonth(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)

// ==================== SORT HELPER ====================

fun sortMedia(media: List<Media>, mode: SortMode): List<Media> = when (mode) {
    SortMode.DATE_DESC -> media.sortedByDescending { it.timestamp }
    SortMode.DATE_ASC -> media.sortedBy { it.timestamp }
    SortMode.NAME_ASC -> media.sortedBy { it.displayName.lowercase() }
    SortMode.NAME_DESC -> media.sortedByDescending { it.displayName.lowercase() }
    SortMode.SIZE_DESC -> media.sortedByDescending { it.fileSize }
    SortMode.SIZE_ASC -> media.sortedBy { it.fileSize }
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
}

fun formatDuration(millis: Long): String {
    val totalSecs = millis / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
    else "%d:%02d".format(mins, secs)
}

// ==================== MAIN APP COMPOSABLE ====================

@Composable
fun AuraGalleryApp(
    hasInitialPermission: Boolean,
    onRequestPermission: ((Boolean) -> Unit) -> Unit
) {
    var hasPerm by remember { mutableStateOf(hasInitialPermission) }
    var mediaList by remember { mutableStateOf<List<Media>>(emptyList()) }
    var albumList by remember { mutableStateOf<List<Album>>(emptyList()) }
    var screen by remember { mutableStateOf("GALLERY") }
    var selIndex by remember { mutableStateOf(0) }
    var isFullScreen by remember { mutableStateOf(false) }
    var blurVal by remember { mutableFloatStateOf(35f) }
    val gridState = rememberLazyGridState()
    var viewMode by remember { mutableStateOf(ViewMode.ALL_MEDIA) }
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.DATE_DESC) }
    var gridColumns by remember { mutableIntStateOf(4) }
    val hazeState = remember { HazeState() }
    val context = LocalContext.current

    // Multi-select state
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Delete confirmation
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Sort bottom sheet
    var showSortSheet by remember { mutableStateOf(false) }

    // Reload trigger
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Immersive fullscreen mode for media viewer
    val view = LocalView.current
    val window = (context as Activity).window
    DisposableEffect(isFullScreen) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        if (isFullScreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Load media on background thread with EXIF data
    LaunchedEffect(hasPerm, reloadTrigger) {
        if (hasPerm) {
            withContext(Dispatchers.IO) {
                val savedFavs = FavoritesManager.loadFavorites(context)
                val media = mutableListOf<Media>()
                val albums = mutableMapOf<Long, Album>()
                val cursor = context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    arrayOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.MEDIA_TYPE,
                        MediaStore.Files.FileColumns.BUCKET_ID,
                        MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                        MediaStore.Files.FileColumns.DATE_MODIFIED,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.WIDTH,
                        MediaStore.MediaColumns.HEIGHT,
                        MediaStore.MediaColumns.MIME_TYPE,
                        MediaStore.MediaColumns.DURATION
                    ),
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
                    arrayOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                    ),
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(MediaStore.MediaColumns._ID)
                    val typeCol = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val bucketIdCol = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameCol = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val dateCol = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val nameCol = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val widthCol = it.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightCol = it.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    val mimeCol = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val durationCol = it.getColumnIndex(MediaStore.MediaColumns.DURATION)

                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val type = it.getInt(typeCol)
                        val bucketId = it.getLong(bucketIdCol)
                        val bucketName = it.getString(bucketNameCol) ?: "Camera"
                        val timestamp = it.getLong(dateCol)
                        val displayName = it.getString(nameCol) ?: ""
                        val fileSize = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                        val width = if (widthCol >= 0) it.getInt(widthCol) else 0
                        val height = if (heightCol >= 0) it.getInt(heightCol) else 0
                        val mimeType = if (mimeCol >= 0) (it.getString(mimeCol) ?: "") else ""
                        val duration = if (durationCol >= 0) it.getLong(durationCol) else 0L

                        val uri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build().toString()
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build().toString()
                        }

                        media.add(
                            Media(
                                uri = uri,
                                mediaId = id,
                                isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                                bucketName = bucketName,
                                bucketId = bucketId,
                                isFavorite = savedFavs.contains(uri),
                                timestamp = timestamp,
                                displayName = displayName,
                                fileSize = fileSize,
                                width = width,
                                height = height,
                                mimeType = mimeType,
                                duration = duration
                            )
                        )

                        if (!albums.containsKey(bucketId)) {
                            albums[bucketId] = Album(bucketId, bucketName, uri, 1, timestamp)
                        } else {
                            albums[bucketId] = albums[bucketId]!!.copy(count = albums[bucketId]!!.count + 1)
                        }
                    }
                }
                mediaList = media
                albumList = albums.values.toList()
            }
        }
    }

    // Sort + filter media
    val processedMedia = remember(mediaList, searchQuery, sortMode) {
        val filtered = if (searchQuery.isBlank()) mediaList
        else mediaList.filter { media ->
            media.displayName.contains(searchQuery, ignoreCase = true) ||
            media.bucketName.contains(searchQuery, ignoreCase = true)
        }
        sortMedia(filtered, sortMode)
    }

    // Delete handler
    if (showDeleteDialog && selectedItems.isNotEmpty()) {
        val count = selectedItems.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete $count item${if (count > 1) "s" else ""}?", color = Color.White) },
            text = { Text("This action cannot be undone. The selected media will be permanently deleted.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        val toDelete = mediaList.filter { selectedItems.contains(it.uri) }
                        DeleteHelper.deleteMedia(context, toDelete) { success ->
                            if (success) {
                                mediaList = mediaList.filter { !selectedItems.contains(it.uri) }
                                selectedItems = emptySet()
                                isSelectMode = false
                                reloadTrigger++
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Color(0xFFFFD700))
                }
            },
            containerColor = Color(0xFF1A1A1A)
        )
    }

    // Sort bottom sheet
    if (showSortSheet) {
        SortBottomSheet(
            currentSort = sortMode,
            onSortChange = { sortMode = it; showSortSheet = false },
            onDismiss = { showSortSheet = false }
        )
    }

    if (!hasPerm) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Permission Required", color = Color.White) },
            text = { Text("Aura Gallery needs access to your photos and videos to display your media.", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = { onRequestPermission { hasPerm = it } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                ) { Text("Grant Access", color = Color.Black) }
            },
            containerColor = Color(0xFF1A1A1A),
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
            if (isFullScreen && processedMedia.isNotEmpty()) {
                MediaPagerScreen(
                    mediaList = processedMedia,
                    initialIndex = selIndex,
                    onBack = { isFullScreen = false },
                    onFavoriteToggle = { index, isFav ->
                        val actualMedia = processedMedia[index]
                        val actualIndex = mediaList.indexOf(actualMedia)
                        if (actualIndex >= 0) {
                            mediaList = mediaList.toMutableList().apply {
                                this[actualIndex] = this[actualIndex].copy(isFavorite = isFav)
                            }
                            FavoritesManager.toggleFavorite(context, actualMedia.uri, isFav)
                        }
                    },
                    onDelete = { index ->
                        val media = processedMedia[index]
                        DeleteHelper.deleteMedia(context, listOf(media)) { success ->
                            if (success) {
                                mediaList = mediaList.filter { it.uri != media.uri }
                                isFullScreen = false
                                reloadTrigger++
                            }
                        }
                    },
                    onSetWallpaper = { uri -> WallpaperHelper.setAsWallpaper(context, uri) }
                )
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF050505)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

                        // Selection bar at top when in select mode
                        if (isSelectMode) {
                            SelectionBar(
                                selectedCount = selectedItems.size,
                                totalCount = processedMedia.size,
                                onSelectAll = {
                                    selectedItems = if (selectedItems.size == processedMedia.size) emptySet()
                                    else processedMedia.map { it.uri }.toSet()
                                },
                                onDelete = { showDeleteDialog = true },
                                onShare = {
                                    val toShare = mediaList.filter { selectedItems.contains(it.uri) }
                                    shareMultipleMedia(context, toShare)
                                },
                                onClose = {
                                    isSelectMode = false
                                    selectedItems = emptySet()
                                },
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }

                        if (screen == "GALLERY" && viewMode != ViewMode.ALBUM_DETAIL && !isSelectMode) {
                            AlbumFilterChips(
                                currentMode = viewMode,
                                onModeChange = { viewMode = it },
                                hazeState = hazeState,
                                blurVal = blurVal,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp)
                            )
                        }
                        when (screen) {
                            "GALLERY" -> GalleryGrid(
                                mediaList = processedMedia,
                                hasPerm = hasPerm,
                                hazeState = hazeState,
                                gridState = gridState,
                                viewMode = viewMode,
                                albums = albumList,
                                selectedAlbum = selectedAlbum,
                                gridColumns = gridColumns,
                                isSelectMode = isSelectMode,
                                selectedItems = selectedItems,
                                onMediaClick = { idx ->
                                    if (isSelectMode) {
                                        val uri = processedMedia[idx].uri
                                        selectedItems = if (selectedItems.contains(uri))
                                            selectedItems - uri else selectedItems + uri
                                        if (selectedItems.isEmpty()) isSelectMode = false
                                    } else {
                                        selIndex = idx
                                        isFullScreen = true
                                    }
                                },
                                onMediaLongClick = { idx ->
                                    if (!isSelectMode) {
                                        isSelectMode = true
                                        selectedItems = setOf(processedMedia[idx].uri)
                                    }
                                },
                                onAlbumClick = { album ->
                                    selectedAlbum = album
                                    viewMode = ViewMode.ALBUM_DETAIL
                                },
                                onBackFromAlbum = {
                                    selectedAlbum = null
                                    viewMode = ViewMode.ALBUMS
                                }
                            )
                            "EXPLORE" -> DummyScreen("EXPLORE", hazeState)
                            "STUDIO" -> DummyScreen("AI STUDIO", hazeState)
                            "VAULT" -> DummyScreen("VAULT", hazeState)
                            "SETTINGS" -> SettingsHub(
                                blurVal = blurVal,
                                onBlurChange = { blurVal = it },
                                gridColumns = gridColumns,
                                onGridColumnsChange = { gridColumns = it },
                                mediaCount = mediaList.size,
                                albumCount = albumList.size,
                                totalSize = mediaList.sumOf { it.fileSize },
                                hazeState = hazeState
                            )
                        }
                        if (!isSelectMode) {
                            TopBar(
                                screen = screen,
                                hazeState = hazeState,
                                blurVal = blurVal,
                                isScrolling = gridState.isScrollInProgress,
                                modifier = Modifier.align(Alignment.TopCenter),
                                onProfileClick = { screen = if (screen == "SETTINGS") "GALLERY" else "SETTINGS" },
                                onSearchChange = { query -> searchQuery = query },
                                mediaCount = processedMedia.size,
                                onSortClick = { showSortSheet = true }
                            )
                        }
                        if (!isSelectMode) {
                            BottomDock(
                                screen = screen,
                                hazeState = hazeState,
                                blurVal = blurVal,
                                isScrolling = gridState.isScrollInProgress,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onScreenChange = {
                                    screen = it
                                    viewMode = ViewMode.ALL_MEDIA
                                    selectedAlbum = null
                                    searchQuery = ""
                                    isSelectMode = false
                                    selectedItems = emptySet()
                                }
                            )
                        }
                        if (screen == "GALLERY" && viewMode != ViewMode.ALBUM_DETAIL && !isSelectMode) {
                            KSUStyleFloatingButton(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 90.dp),
                                onClick = { screen = "STUDIO" }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== SHARE MULTIPLE ====================

fun shareMultipleMedia(context: Context, items: List<Media>) {
    val uris = ArrayList(items.map { Uri.parse(it.uri) })
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (items.all { it.isVideo }) "video/*"
        else if (items.all { !it.isVideo }) "image/*"
        else "*/*"
        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share ${items.size} items"))
}
