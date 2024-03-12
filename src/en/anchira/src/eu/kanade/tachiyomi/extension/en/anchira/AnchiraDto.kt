package eu.kanade.tachiyomi.extension.en.anchira

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    var name: String,
    var namespace: Int? = null,
)

@Serializable
data class LibraryResponse(
    val entries: List<Entry> = emptyList(),
    val total: Int,
    val page: Int,
    val limit: Int,
)

@Serializable
data class Entry(
    val id: Int,
    val key: String,
    @SerialName("published_at") val publishedAt: Long = 0L,
    val title: String,
    @SerialName("thumb_index") val thumbnailIndex: Int = 1,
    val tags: List<Tag> = emptyList(),
    val url: String? = null,
    val pages: Int = 1,
)

@Serializable
data class ImageData(
    val id: Int,
    val key: String,
    val hash: String,
    val names: List<String>,
)

@Serializable
data class EntryKey(
    val id: Int,
    val key: String? = null,
    val hash: String? = null,
    val url: String? = null,
    val names: List<String> = emptyList(),
)
