package com.pandeyji.aura.gallery

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        onPermissionResult?.invoke(isGranted)
    }

    private var onPermissionResult: ((Boolean) -> Unit)? = null

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
    val hazeState = remember { HazeState() }
    val context = LocalContext.current

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

    // Load media on background thread to avoid UI freeze
    LaunchedEffect(hasPerm) {
        if (hasPerm) {
            withContext(Dispatchers.IO) {
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
                        MediaStore.Files.FileColumns.DISPLAY_NAME
                    ),
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?",
                    arrayOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                    ),
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )

                cursor?.use {
                    val idCol = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val typeCol = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val bucketIdCol = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                    val bucketNameCol = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                    val dateCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val nameCol = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)

                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val type = it.getInt(typeCol)
                        val bucketId = it.getLong(bucketIdCol)
                        val bucketName = it.getString(bucketNameCol) ?: "Camera"
                        val timestamp = it.getLong(dateCol)
                        val displayName = it.getString(nameCol) ?: ""

                        val uri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build().toString()
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build().toString()
                        }

                        media.add(
                            Media(
                                uri = uri,
                                isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                                bucketName = bucketName,
                                bucketId = bucketId,
                                timestamp = timestamp,
                                displayName = displayName
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

    // Compute filtered media based on search query
    val filteredMedia = remember(mediaList, searchQuery) {
        if (searchQuery.isBlank()) mediaList
        else mediaList.filter { media ->
            media.displayName.contains(searchQuery, ignoreCase = true) ||
            media.bucketName.contains(searchQuery, ignoreCase = true)
        }
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
                ) {
                    Text("Grant Access", color = Color.Black)
                }
            },
            containerColor = Color(0xFF1A1A1A),
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
            if (isFullScreen && filteredMedia.isNotEmpty()) {
                MediaPagerScreen(
                    mediaList = filteredMedia,
                    initialIndex = selIndex,
                    onBack = { isFullScreen = false },
                    onFavoriteToggle = { index, isFav ->
                        // Find the actual index in the original mediaList
                        val actualMedia = filteredMedia[index]
                        val actualIndex = mediaList.indexOf(actualMedia)
                        if (actualIndex >= 0) {
                            mediaList = mediaList.toMutableList().apply {
                                this[actualIndex] = this[actualIndex].copy(isFavorite = isFav)
                            }
                        }
                    }
                )
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF050505)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (screen == "GALLERY" && viewMode != ViewMode.ALBUM_DETAIL) {
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
                                mediaList = filteredMedia,
                                hasPerm = hasPerm,
                                hazeState = hazeState,
                                gridState = gridState,
                                viewMode = viewMode,
                                albums = albumList,
                                selectedAlbum = selectedAlbum,
                                onMediaClick = { idx ->
                                    selIndex = idx
                                    isFullScreen = true
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
                            "SETTINGS" -> SettingsHub(blurVal, { blurVal = it }, hazeState)
                        }
                        TopBar(
                            screen = screen,
                            hazeState = hazeState,
                            blurVal = blurVal,
                            isScrolling = gridState.isScrollInProgress,
                            modifier = Modifier.align(Alignment.TopCenter),
                            onProfileClick = { screen = if (screen == "SETTINGS") "GALLERY" else "SETTINGS" },
                            onSearchChange = { query -> searchQuery = query }
                        )
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
                            }
                        )
                        if (screen == "GALLERY" && viewMode != ViewMode.ALBUM_DETAIL) {
                            KSUStyleFloatingButton(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 90.dp),
                                onClick = { }
                            )
                        }
                    }
                }
            }
        }
    }
}
