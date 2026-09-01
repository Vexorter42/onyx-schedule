package com.vexorter.onyx.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseAssetDto(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String = "",
)

@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tag: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val pageUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAssetDto> = emptyList(),
)
