package top.nekoanime.mobile.app.model.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * 一集数据。
 *
 * @param id 剧集 id
 * @param number 集数（展示用）
 * @param title 剧集标题
 */
@Serializable
data class Episode(
    val id: Int,
    val number: Int,
    val title: String,
)

/**
 * 一集的播放信息，字幕失败不影响视频地址。
 *
 * @param episode 对应的剧集
 * @param videoUrl 可直接播放的地址
 * @param subtitles 外挂字幕的完整元数据
 */
@Serializable
data class EpisodePlay(
    val episode: Episode,
    val videoUrl: String,
    val subtitles: List<EpisodeSubtitle> = emptyList(),
)

/**
 * 外挂字幕描述；保留整个响应对象，不丢弃 id、format 及未来扩展字段。
 *
 * @param metadata 后端原始字幕对象；当前契约为 label/language/default/url，未声明 format 时是 WebVTT
 */
@Serializable
data class EpisodeSubtitle(val metadata: JsonObject) {
    val id: JsonElement? get() = metadata["id"]
    val label: String get() = text("label").orEmpty()
    val language: String? get() = text("language")
    val format: String? get() = text("format")
    val url: String get() = text("url").orEmpty()
    val isDefault: Boolean get() = (metadata["default"] as? JsonPrimitive)?.booleanOrNull == true

    /** 仅读取字符串字段；错误类型仍保留在元数据中，但不作为下载地址或格式使用。 */
    private fun text(name: String): String? =
        (metadata[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

/**
 * 独立解析字幕资源列表，忽略畸形条目并完整保留有效对象。
 *
 * @param value 接口字幕字段；null、非数组或缺失时没有外挂字幕
 */
fun parseEpisodeSubtitles(value: JsonElement): List<EpisodeSubtitle> =
    (value as? JsonArray).orEmpty().mapNotNull { entry ->
        (entry as? JsonObject)?.let(::EpisodeSubtitle)?.takeIf { it.url.isNotBlank() }
    }
