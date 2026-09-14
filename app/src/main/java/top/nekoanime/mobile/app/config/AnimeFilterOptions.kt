package top.nekoanime.mobile.app.config

/**
 * 一个筛选项。
 *
 * @param label 展示文案（例如「全部」「2024」「1 月」）
 * @param value 传给后端的值（例如 all / 2024 / 1）
 */
data class AnimeFilterOption(val label: String, val value: String)

/**
 * 番剧列表的筛选项（与 Flutter 版 `anime_filter_const` 一一对应）。
 *
 * @description 只放常量，改筛选范围只动这里；后端 `/api/anime/list` 支持
 * year / month / type / source / category 五个参数，值就是这里的 value。
 */
object AnimeFilterOptions {
    /** 年份 */
    val year: List<AnimeFilterOption> = listOf(
        AnimeFilterOption("全部", "all"),
        AnimeFilterOption("2026", "2026"),
        AnimeFilterOption("2025", "2025"),
        AnimeFilterOption("2024", "2024"),
        AnimeFilterOption("2023", "2023"),
        AnimeFilterOption("2022", "2022"),
        AnimeFilterOption("2021", "2021"),
        AnimeFilterOption("2020", "2020"),
        AnimeFilterOption("2019", "2019"),
        AnimeFilterOption("2018", "2018"),
        AnimeFilterOption("2017", "2017"),
        AnimeFilterOption("2016", "2016"),
        AnimeFilterOption("2015 以前", "2015 以前"),
    )

    /** 月份 */
    val month: List<AnimeFilterOption> = listOf(AnimeFilterOption("全部", "all")) +
        (1..12).map { AnimeFilterOption("$it 月", it.toString()) }

    /** 类型（题材） */
    val type: List<AnimeFilterOption> = listOf("全部", "科幻", "喜剧", "同人", "百合", "校园", "惊悚", "后宫", "机战")
        .plus(listOf("悬疑", "恋爱", "奇幻", "推理", "运动", "耽美", "音乐", "战斗", "冒险"))
        .plus(listOf("萌系", "穿越", "玄幻", "乙女", "恐怖", "历史", "日常", "剧情", "武俠", "美食", "职场"))
        .map { AnimeFilterOption(it, if (it == "全部") "all" else it) }

    /** 来源 */
    val source: List<AnimeFilterOption> = listOf("全部", "原创", "漫画改", "游戏改", "小说改")
        .map { AnimeFilterOption(it, if (it == "全部") "all" else it) }

    /** 分类 */
    val category: List<AnimeFilterOption> = listOf("全部", "TV", "剧场版")
        .map { AnimeFilterOption(it, if (it == "全部") "all" else it) }
}
