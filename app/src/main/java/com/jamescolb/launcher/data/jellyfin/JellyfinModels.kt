package com.jamescolb.launcher.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Subsets of the Jellyfin REST DTOs; only the fields the launcher uses. Unknown keys are ignored.

@Serializable
data class PublicSystemInfo(
    @SerialName("Id") val id: String,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
)

@Serializable
data class UserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("ServerId") val serverId: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean = true,
)

@Serializable
data class AuthResult(
    @SerialName("User") val user: UserDto,
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String,
)

@Serializable
data class QuickConnectResult(
    @SerialName("Secret") val secret: String,
    @SerialName("Code") val code: String? = null,
    @SerialName("Authenticated") val authenticated: Boolean = false,
)

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
)

@Serializable
data class UserItemData(
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
)

@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbImageTag: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("UserData") val userData: UserItemData? = null,
)
