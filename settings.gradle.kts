// NekoAnime 移动端（Jetpack Compose + Media3）
//
// 目录说明：正式移动端工程。播放页（含播放器本体）已完成，
// 首页、搜索、用户中心等页面后续按 page/ 下的结构继续加。

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // VLog 官方发布在 JitPack，仅允许该日志库坐标使用此仓库，避免影响其他依赖解析。
        maven("https://jitpack.io") { content { includeGroup("com.github.oooo7777777") } }
    }
}

rootProject.name = "NekoAnimeMobileApp"

include(":app")
