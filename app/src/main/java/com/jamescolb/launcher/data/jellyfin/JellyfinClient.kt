package com.jamescolb.launcher.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class JellyfinException(message: String, val code: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

/** Identifies this launcher to the server; shown in Jellyfin's dashboard under devices. */
data class ClientInfo(
    val clientName: String,
    val deviceName: String,
    val deviceId: String,
    val version: String,
)

/**
 * Minimal Jellyfin REST client. Every call suspends on [Dispatchers.IO] and throws
 * [JellyfinException] with a user-presentable message on failure.
 */
class JellyfinClient(
    val http: OkHttpClient,
    private val clientInfo: ClientInfo,
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun authHeader(token: String?): String = buildString {
        append("MediaBrowser ")
        append("Client=\"").append(clientInfo.clientName.headerSafe()).append("\", ")
        append("Device=\"").append(clientInfo.deviceName.headerSafe()).append("\", ")
        append("DeviceId=\"").append(clientInfo.deviceId).append("\", ")
        append("Version=\"").append(clientInfo.version.headerSafe()).append('"')
        if (token != null) append(", Token=\"").append(token).append('"')
    }

    private fun String.headerSafe() = replace("\"", "").filter { it.code in 0x20..0x7E }

    private fun url(base: String, path: String, query: Map<String, Any?> = emptyMap()): HttpUrl {
        val builder = (base.trimEnd('/') + path).toHttpUrlOrNull()?.newBuilder()
            ?: throw JellyfinException("That doesn't look like a valid server address.")
        query.forEach { (k, v) -> if (v != null) builder.addQueryParameter(k, v.toString()) }
        return builder.build()
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401 -> "Not authorized. Check the username and password."
                        403 -> "The server refused the request."
                        404 -> "Not found on this server."
                        else -> "Server error (${response.code})."
                    }
                    throw JellyfinException(message, response.code)
                }
                body
            }
        } catch (e: IOException) {
            throw JellyfinException("Couldn't reach the server: ${e.message ?: e.javaClass.simpleName}", cause = e)
        }
    }

    private suspend inline fun <reified T> get(
        base: String,
        path: String,
        token: String? = null,
        query: Map<String, Any?> = emptyMap(),
    ): T {
        val req = Request.Builder()
            .url(url(base, path, query))
            .header("Authorization", authHeader(token))
            .header("Accept", "application/json")
            .build()
        return decode(call(req))
    }

    private suspend inline fun <reified T> post(
        base: String,
        path: String,
        token: String? = null,
        body: String = "{}",
        query: Map<String, Any?> = emptyMap(),
    ): T {
        val req = Request.Builder()
            .url(url(base, path, query))
            .header("Authorization", authHeader(token))
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return decode(call(req))
    }

    private inline fun <reified T> decode(text: String): T = try {
        if (T::class == Unit::class) Unit as T else json.decodeFromString<T>(text)
    } catch (e: Exception) {
        throw JellyfinException("Unexpected response from server.", cause = e)
    }

    // --- Server & auth ---------------------------------------------------------------------------

    suspend fun publicInfo(baseUrl: String): PublicSystemInfo =
        get(baseUrl, "/System/Info/Public")

    suspend fun publicUsers(baseUrl: String): List<UserDto> =
        runCatching { get<List<UserDto>>(baseUrl, "/Users/Public") }.getOrDefault(emptyList())

    suspend fun authenticateByName(baseUrl: String, username: String, password: String): AuthResult {
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }.toString()
        return post(baseUrl, "/Users/AuthenticateByName", body = body)
    }

    suspend fun quickConnectEnabled(baseUrl: String): Boolean =
        runCatching { get<Boolean>(baseUrl, "/QuickConnect/Enabled") }.getOrDefault(false)

    suspend fun quickConnectInitiate(baseUrl: String): QuickConnectResult =
        post(baseUrl, "/QuickConnect/Initiate")

    suspend fun quickConnectState(baseUrl: String, secret: String): QuickConnectResult =
        get(baseUrl, "/QuickConnect/Connect", query = mapOf("secret" to secret))

    suspend fun authenticateWithQuickConnect(baseUrl: String, secret: String): AuthResult {
        val body = buildJsonObject { put("Secret", secret) }.toString()
        return post(baseUrl, "/Users/AuthenticateWithQuickConnect", body = body)
    }

    suspend fun logout(baseUrl: String, token: String) {
        runCatching { post<Unit>(baseUrl, "/Sessions/Logout", token = token) }
    }

    // --- Library ---------------------------------------------------------------------------------

    private val itemFields = "Overview,PrimaryImageAspectRatio"

    /** Items the user is part-way through. */
    suspend fun resume(baseUrl: String, token: String, userId: String, limit: Int = 12): List<BaseItemDto> {
        val query = mapOf(
            "userId" to userId,
            "limit" to limit,
            "mediaTypes" to "Video",
            "fields" to itemFields,
            "enableUserData" to true,
            "enableImageTypes" to "Primary,Backdrop,Thumb",
        )
        return withLegacyFallback(
            modern = { get<ItemsResult>(baseUrl, "/UserItems/Resume", token, query) },
            legacy = { get<ItemsResult>(baseUrl, "/Users/$userId/Items/Resume", token, query) },
        ).items
    }

    /** Next episode to watch for each series in progress. */
    suspend fun nextUp(
        baseUrl: String,
        token: String,
        userId: String,
        rewatching: Boolean,
        limit: Int = 12,
    ): List<BaseItemDto> = get<ItemsResult>(
        baseUrl,
        "/Shows/NextUp",
        token,
        mapOf(
            "userId" to userId,
            "limit" to limit,
            "enableRewatching" to rewatching,
            "enableResumable" to false,
            "fields" to itemFields,
            "enableUserData" to true,
            "enableImageTypes" to "Primary,Backdrop,Thumb",
        ),
    ).items

    /**
     * Recently watched episodes, newest first, without images: used to date each series' latest
     * activity so Next Up can be ordered alongside in-progress items.
     */
    suspend fun recentlyPlayedEpisodes(
        baseUrl: String,
        token: String,
        userId: String,
        limit: Int = 100,
    ): List<BaseItemDto> {
        val query = mapOf(
            "userId" to userId,
            "includeItemTypes" to "Episode",
            "recursive" to true,
            "filters" to "IsPlayed",
            "sortBy" to "DatePlayed",
            "sortOrder" to "Descending",
            "limit" to limit,
            "fields" to "SeriesId",
            "enableUserData" to true,
            "enableImages" to false,
        )
        return withLegacyFallback(
            modern = { get<ItemsResult>(baseUrl, "/Items", token, query) },
            legacy = { get<ItemsResult>(baseUrl, "/Users/$userId/Items", token, query - "userId") },
        ).items
    }

    /** Latest library items of one type, newest first. */
    suspend fun latest(
        baseUrl: String,
        token: String,
        userId: String,
        itemType: String,
        sortBy: String,
        limit: Int = 16,
    ): List<BaseItemDto> {
        val query = mapOf(
            "userId" to userId,
            "includeItemTypes" to itemType,
            "recursive" to true,
            "sortBy" to sortBy,
            "sortOrder" to "Descending",
            "limit" to limit,
            "fields" to itemFields,
            "enableUserData" to true,
            "enableImageTypes" to "Primary,Backdrop,Thumb",
        )
        return withLegacyFallback(
            modern = { get<ItemsResult>(baseUrl, "/Items", token, query) },
            legacy = { get<ItemsResult>(baseUrl, "/Users/$userId/Items", token, query - "userId") },
        ).items
    }

    /** Servers before 10.9 only have the /Users/{id}/... routes. */
    private suspend fun <T> withLegacyFallback(modern: suspend () -> T, legacy: suspend () -> T): T =
        try {
            modern()
        } catch (e: JellyfinException) {
            if (e.code == 404) legacy() else throw e
        }

    // --- Images ----------------------------------------------------------------------------------

    fun imageUrl(baseUrl: String, itemId: String, type: String, tag: String?, maxWidth: Int): String =
        url(
            baseUrl,
            "/Items/$itemId/Images/$type",
            mapOf("tag" to tag, "maxWidth" to maxWidth, "quality" to 90),
        ).toString()

    fun userImageUrl(baseUrl: String, userId: String, tag: String?): String =
        url(baseUrl, "/Users/$userId/Images/Primary", mapOf("tag" to tag, "maxWidth" to 240)).toString()

    companion object {
        private val JSON = "application/json".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        /** "192.168.1.5:8096/" -> "http://192.168.1.5:8096"; also strips a trailing "/web". */
        fun normalizeUrl(input: String): String {
            var url = input.trim().trimEnd('/')
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "http://$url"
            }
            if (url.endsWith("/web", ignoreCase = true)) url = url.dropLast(4)
            return url.trimEnd('/')
        }
    }
}
