package top.nekoanime.mobile.app.component

import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * 追加新一页数据，并按唯一键去重。
 *
 * @description 后端分页排序如果存在并列值（例如"全部动漫"只按评分排序，而评分大量重复），
 * 翻页边界上的记录会被相邻两页重复返回；而 LazyGrid / LazyColumn 的 key 必须唯一，
 * 一旦重复就会抛 `IllegalArgumentException: Key ... was already used` 直接把 App 打崩
 * （现象就是"往下滑加载下一页时闪退"）。
 *
 * 服务端已经给排序补了 tie-breaker（Id）从源头避免重复，这里再挡一道：
 * 老版本服务端、或者将来别的接口出现重复时，界面也不会崩。
 *
 * @param newItems 新一页数据
 * @param keyOf 取唯一键的方法（要和列表 items(...) 的 key 保持一致）
 * @returns 实际追加进列表的条数
 */
fun <T> SnapshotStateList<T>.appendDistinct(newItems: List<T>, keyOf: (T) -> Any): Int {
    if (newItems.isEmpty()) {
        return 0
    }

    // 现有 key 先装进集合，新数据逐条判重，整体 O(n)
    val seen = HashSet<Any>(size + newItems.size)

    forEach { seen.add(keyOf(it)) }

    var added = 0

    newItems.forEach { item ->
        if (seen.add(keyOf(item))) {
            add(item)
            added++
        }
    }

    return added
}
