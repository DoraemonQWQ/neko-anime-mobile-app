package top.nekoanime.mobile.app.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/*
 * 页面状态保存工具。
 *
 * 换成官方 Navigation 之后，页面离开组合树时 `remember` 的状态会丢；
 * 要让「返回上一页数据还在」，状态必须能被系统保存 —— 也就是 rememberSaveable。
 * 这些实体都是 @Serializable，所以统一用 JSON 字符串当中间表示：
 * - 单个对象：{@link jsonSaver}
 * - 列表：{@link jsonListSaver}
 */

/** 状态保存用的 JSON（只在本机存取，配置保持一致即可） */
@PublishedApi
internal val stateJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * 可序列化对象的 Saver（存成 JSON 字符串）。
 *
 * @param T 对象类型（必须 @Serializable）
 * @returns Saver
 */
inline fun <reified T> jsonSaver(): Saver<T, Any> = Saver(
    save = { value -> stateJson.encodeToString(serializer<T>(), value) as Any },
    restore = { saved -> stateJson.decodeFromString(serializer<T>(), saved as String) },
)

/**
 * 可序列化对象列表的 Saver（每一项存成 JSON 字符串）。
 *
 * @param T 元素类型（必须 @Serializable）
 * @returns Saver
 */
inline fun <reified T> jsonListSaver(): Saver<SnapshotStateList<T>, Any> = listSaver(
    save = { list -> list.map { item -> stateJson.encodeToString(serializer<T>(), item) } },
    restore = { saved ->
        saved
            .map { text -> stateJson.decodeFromString(serializer<T>(), text) }
            .toMutableStateList()
    },
)

/**
 * 可保存的「可能为空的对象」状态（页面返回时值还在）。
 *
 * @description 用空串表示「还没有值」，避免把 null 塞进 Bundle。
 *
 * @param key 区分不同状态的 key
 * @returns 可变状态（初始为 null）
 */
@Composable
inline fun <reified T : Any> rememberSaveableState(key: String): MutableState<T?> {
    val saver = Saver<MutableState<T?>, Any>(
        save = { state ->
            state.value?.let { value -> stateJson.encodeToString(serializer<T>(), value) } ?: ""
        },
        restore = { saved ->
            val text = saved as? String

            mutableStateOf(
                text
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { value -> stateJson.decodeFromString(serializer<T>(), value) },
            )
        },
    )

    return rememberSaveable(key = key, saver = saver) { mutableStateOf(null) }
}

/**
 * 可保存的列表状态（页面返回时列表还在，不用重新请求）。
 *
 * @param key 区分不同状态的 key
 * @param inputs 额外的重建触发条件（变化时重置）
 * @returns 可变的列表状态
 */
@Composable
inline fun <reified T> rememberSaveableList(key: String, vararg inputs: Any?): SnapshotStateList<T> =
    rememberSaveable(key = key, saver = jsonListSaver<T>(), inputs = inputs) { mutableStateListOf<T>() }
