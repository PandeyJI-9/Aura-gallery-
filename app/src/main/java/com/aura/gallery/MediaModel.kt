package com.pandeyji.aura.gallery

data class Media(
    val uri: String,
    val mediaId: Long = 0L,
    val isVideo: Boolean,
    val bucketName: String = "Camera",
    val bucketId: Long = 0L,
    val isFavorite: Boolean = false,
    val timestamp: Long = 0L,
    val displayName: String = "",
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val duration: Long = 0L
)

data class Album(
    val bucketId: Long,
    val bucketName: String,
    val thumbnailUri: String,
    val count: Int,
    val lastModified: Long
)

enum class ViewMode {
    ALL_MEDIA,
    ALBUMS,
    ALBUM_DETAIL,
    FAVORITES
}

enum class SortMode(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

// Date group labels for timeline headers
data class DateGroup(
    val label: String,
    val timestamp: Long
)