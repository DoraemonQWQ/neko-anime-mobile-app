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
    }
}

rootProject.name = "NekoAnimeMobileApp"

include(":app")
