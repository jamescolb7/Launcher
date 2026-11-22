package com.jamescolb.launcher.data

enum class MediaType(val label: String) { Episode("TV Show"), Series("TV Show"), Movie("Movie"), Other("Video") }

/** A Jellyfin library item, flattened for the home screen. Image fields are full URLs. */
data class MediaItem(
    val id: String,
    val type: MediaType,
    /** Series name for episodes; the item's own name otherwise. */
    val title: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val rating: String? = null,
    val overview: String? = null,
    /** Playback progress 0..1, or 0 when not started. */
    val progress: Float = 0f,
    val played: Boolean = false,
    val unplayedCount: Int? = null,
    /** 16:9 image for Continue Watching cards. */
    val thumbUrl: String? = null,
    /** 2:3 poster. */
    val posterUrl: String? = null,
    /** Wide background art for the hero. */
    val backdropUrl: String? = null,
) {
    /**
     * The accent line under the hero title:
     * episodes "S1 • E1 • Episode name", series "2019 • 12 unwatched", movies "2023 • 1h 54m • PG-13".
     */
    val subtitle: String
        get() = when (type) {
            MediaType.Episode -> listOfNotNull(
                season?.let { "S$it" },
                episode?.let { "E$it" },
                episodeTitle,
            )
            MediaType.Series -> listOfNotNull(
                year?.toString(),
                unplayedCount?.takeIf { it > 0 }?.let { "$it unwatched" },
            )
            else -> listOfNotNull(year?.toString(), runtimeMinutes?.let(::formatRuntime), rating)
        }.joinToString(" • ")
}

private fun formatRuntime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
