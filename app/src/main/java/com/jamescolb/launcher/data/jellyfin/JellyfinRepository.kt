package com.jamescolb.launcher.data.jellyfin

import com.jamescolb.launcher.data.JellyfinAccount
import com.jamescolb.launcher.data.JellyfinServer
import com.jamescolb.launcher.data.MediaItem
import com.jamescolb.launcher.data.MediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One home-screen row: its items, or why it couldn't load. */
data class MediaRow(val items: List<MediaItem> = emptyList(), val error: String? = null)

data class HomeRows(
    val continueWatching: MediaRow = MediaRow(),
    val latestShows: MediaRow = MediaRow(),
    val latestMovies: MediaRow = MediaRow(),
)

/** Loads the home rows for an account and maps Jellyfin DTOs to [MediaItem]s. */
class JellyfinRepository(private val client: JellyfinClient) {

    suspend fun loadHome(server: JellyfinServer, account: JellyfinAccount): HomeRows = coroutineScope {
        val url = server.url
        val token = account.token
        val userId = account.userId

        val resume = async { runCatching { client.resume(url, token, userId) } }
        val nextUp = async { runCatching { client.nextUp(url, token, userId, account.rewatching, limit = 24) } }
        val recentlyPlayed = async { runCatching { client.recentlyPlayedEpisodes(url, token, userId) } }
        val shows = async {
            runCatching { client.latest(url, token, userId, "Series", "DateLastContentAdded,SortName") }
        }
        val movies = async {
            runCatching { client.latest(url, token, userId, "Movie", "DateCreated,SortName") }
        }

        val resumeResult = resume.await()
        val nextUpResult = nextUp.await()
        val continueWatching = if (resumeResult.isFailure && nextUpResult.isFailure) {
            MediaRow(error = resumeResult.exceptionOrNull()?.message)
        } else {
            MediaRow(
                mergeContinueWatching(
                    resume = resumeResult.getOrDefault(emptyList()),
                    nextUp = nextUpResult.getOrDefault(emptyList()),
                    recentlyPlayed = recentlyPlayed.await().getOrDefault(emptyList()),
                ).map { it.toMediaItem(url) },
            )
        }

        HomeRows(
            continueWatching = continueWatching,
            latestShows = shows.await().toRow(url),
            latestMovies = movies.await().toRow(url),
        )
    }

    private fun Result<List<BaseItemDto>>.toRow(url: String): MediaRow = fold(
        onSuccess = { list -> MediaRow(list.map { it.toMediaItem(url) }) },
        onFailure = { MediaRow(error = it.message) },
    )

    /**
     * In-progress items and Next Up episodes in one list, newest activity first.
     *
     * In-progress items are dated by when they were last played. A Next Up episode is dated by
     * the last time anything in its series was watched (from [recentlyPlayed]), so a show you
     * just finished, and are now rewatching, sorts by how recently you watched it rather than
     * landing after every paused item. Series already in progress are not repeated, and each
     * series gets one Next Up card, preferring an unwatched episode over a rewatch.
     */
    private fun mergeContinueWatching(
        resume: List<BaseItemDto>,
        nextUp: List<BaseItemDto>,
        recentlyPlayed: List<BaseItemDto>,
    ): List<BaseItemDto> {
        val seriesLastPlayed = HashMap<String, String>()
        for (episode in recentlyPlayed) {
            val series = episode.seriesId ?: continue
            val date = episode.userData?.lastPlayedDate ?: continue
            seriesLastPlayed.putIfAbsent(series, date) // newest first, so keep the first seen
        }

        val resumeIds = resume.map { it.id }.toSet()
        val resumeSeries = resume.mapNotNull { it.seriesId }.toSet()
        val nextUpPerSeries = nextUp
            .filter { it.id !in resumeIds && (it.seriesId == null || it.seriesId !in resumeSeries) }
            .groupBy { it.seriesId ?: it.id }
            .map { (_, episodes) -> episodes.firstOrNull { it.userData?.played != true } ?: episodes.first() }

        // ISO-8601 timestamps sort correctly as strings; undated items go last.
        val dated = resume.map { (it.userData?.lastPlayedDate ?: "") to it } +
            nextUpPerSeries.map { (seriesLastPlayed[it.seriesId] ?: "") to it }
        return dated
            .sortedByDescending { it.first }
            .map { it.second }
            .take(MAX_CONTINUE_WATCHING)
    }

    private fun BaseItemDto.toMediaItem(url: String): MediaItem {
        val mediaType = when (type) {
            "Episode" -> MediaType.Episode
            "Series" -> MediaType.Series
            "Movie" -> MediaType.Movie
            else -> MediaType.Other
        }
        fun image(itemId: String?, kind: String, tag: String?, width: Int): String? =
            if (itemId != null && tag != null) client.imageUrl(url, itemId, kind, tag, width) else null

        val ownThumb = image(id, "Thumb", imageTags["Thumb"], THUMB_WIDTH)
        val ownPrimary = image(id, "Primary", imageTags["Primary"], THUMB_WIDTH)
        val ownBackdrop = image(id, "Backdrop", backdropImageTags.firstOrNull(), THUMB_WIDTH)
        val parentThumb = image(parentThumbItemId, "Thumb", parentThumbImageTag, THUMB_WIDTH)
        val parentBackdrop = image(parentBackdropItemId, "Backdrop", parentBackdropImageTags.firstOrNull(), THUMB_WIDTH)

        // Same preference order as Jellyfin web's Continue Watching cards.
        val thumb = when (mediaType) {
            MediaType.Episode -> parentThumb ?: ownPrimary ?: parentBackdrop
            else -> ownThumb ?: ownBackdrop ?: ownPrimary
        }
        val poster = when (mediaType) {
            MediaType.Episode -> image(seriesId, "Primary", seriesPrimaryImageTag, POSTER_WIDTH)
            else -> image(id, "Primary", imageTags["Primary"], POSTER_WIDTH)
        }
        val backdrop = image(id, "Backdrop", backdropImageTags.firstOrNull(), BACKDROP_WIDTH)
            ?: image(parentBackdropItemId, "Backdrop", parentBackdropImageTags.firstOrNull(), BACKDROP_WIDTH)

        return MediaItem(
            id = id,
            type = mediaType,
            title = if (mediaType == MediaType.Episode) seriesName ?: name.orEmpty() else name.orEmpty(),
            episodeTitle = if (mediaType == MediaType.Episode) name else null,
            season = parentIndexNumber,
            episode = indexNumber,
            year = productionYear,
            runtimeMinutes = runTimeTicks?.let { (it / TICKS_PER_MINUTE).toInt() }?.takeIf { it > 0 },
            rating = officialRating,
            overview = overview,
            progress = ((userData?.playedPercentage ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
            played = userData?.played == true,
            unplayedCount = userData?.unplayedItemCount,
            thumbUrl = thumb,
            posterUrl = poster,
            backdropUrl = backdrop,
        )
    }

    private companion object {
        const val MAX_CONTINUE_WATCHING = 16
        const val THUMB_WIDTH = 560
        const val POSTER_WIDTH = 320
        const val BACKDROP_WIDTH = 1600
        const val TICKS_PER_MINUTE = 600_000_000L
    }
}
