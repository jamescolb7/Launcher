package com.jamescolb.launcher.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

enum class MediaType(val label: String) { Series("TV Show"), Movie("Movie") }

/** Placeholder for a Jellyfin "resume" item. Artwork is a two-color gradient until real images. */
data class MediaItem(
    val id: String,
    val type: MediaType,
    val title: String,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val runtime: String? = null,
    val overview: String,
    val progress: Float,
    val artColors: List<Color>,
    /** Backdrop art for the hero. Null until images come from Jellyfin. */
    val backdrop: ImageBitmap? = null,
) {
    /** "Desmond's Big Day Out • Season 1 • Episode 1" or "2023 • 1h 54m". */
    val subtitle: String
        get() = if (type == MediaType.Series) {
            listOfNotNull(episodeTitle, season?.let { "Season $it" }, episode?.let { "Episode $it" })
        } else {
            listOfNotNull(year?.toString(), runtime)
        }.joinToString(" • ")
}

object PlaceholderMedia {
    val continueWatching = listOf(
        MediaItem(
            id = "sf-s1e1",
            type = MediaType.Series,
            title = "Smiling Friends",
            episodeTitle = "Desmond's Big Day Out",
            season = 1,
            episode = 1,
            overview = "A small company dedicated to bringing happiness to the world receives a simple " +
                "request to help a woman's unhappy son smile again, but the job turns out to be more " +
                "complicated than it seems.",
            progress = 0.65f,
            artColors = listOf(Color(0xFFF2C14E), Color(0xFFE2725B)),
        ),
        MediaItem(
            id = "placeholder-movie",
            type = MediaType.Movie,
            title = "The Long Weekend",
            year = 2023,
            runtime = "1h 54m",
            overview = "Placeholder movie. Once Jellyfin is connected, this row will show items from " +
                "your server's Continue Watching list with real artwork and progress.",
            progress = 0.4f,
            artColors = listOf(Color(0xFF3A6EA5), Color(0xFF16213E)),
        ),
        MediaItem(
            id = "placeholder-series-2",
            type = MediaType.Series,
            title = "Northern Lights",
            episodeTitle = "The Quiet Part",
            season = 2,
            episode = 4,
            overview = "Placeholder episode. A research crew wintering above the Arctic Circle starts " +
                "receiving radio messages that seem to come from their own future.",
            progress = 0.22f,
            artColors = listOf(Color(0xFF2BB673), Color(0xFF0B3D2E)),
        ),
        MediaItem(
            id = "placeholder-series-3",
            type = MediaType.Series,
            title = "Night Shift Diner",
            episodeTitle = "Pilot",
            season = 1,
            episode = 1,
            overview = "Placeholder episode. The regulars of a 24-hour diner trade stories between " +
                "midnight and sunrise.",
            progress = 0.85f,
            artColors = listOf(Color(0xFFB83B5E), Color(0xFF2D132C)),
        ),
    )
}
