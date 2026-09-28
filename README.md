# Neko Anime 移动端（Android）

Neko Anime 的安卓客户端，接口全部来自 [Neko Anime 后端](../../Server/NekoAnime)。当前发布版本 **1.4.0**。

## 技术栈

- **Kotlin 2.0 + Jetpack Compose**（Material 3，自研主题：白 / 淡灰 / 粉，深浅色跟随系统）
- **Media3 ExoPlayer**：播放页（手势、选集 / 字幕抽屉、锁定、全屏、续播）
- **Coil**：封面与头像；**OkHttp + kotlinx.serialization**：接口调用
- 单 Activity + **Navigation Compose 类型安全路由**（`@Serializable` 路由类 + 自定义转场），返回策略见下文「导航与返回」

## 目录结构

```
app/src/main/
├── assets/config/                 # 运行期配置（debug / release 各一份）
├── java/top/nekoanime/mobile/app/
│   ├── AppRoute.kt                # 路由定义（@Serializable 数据类 / 数据对象）
│   ├── MainActivity.kt            # 单 Activity：NavHost、全屏与返回键、系统栏配色
│   ├── component/                 # 公共 UI（含 theme/、弹框、卡片、搜索框）
│   ├── config/                    # AppConfig 映射类 + 配置加载器
│   ├── data/                      # api（接口 + ApiRoutes 路由表）/ local（本机存储）/ repository
│   ├── model/                     # dto（接口结构）与 entity（界面实体）
│   ├── page/                      # 页面：splash / home / search / play / history / settings / user
│   ├── service/                   # 业务服务（版本更新、续播起播解析）
│   └── state/                     # 全局登录态
└── res/                           # 图标（自适应 + 各密度）、主题色、FileProvider 路径
```

约定：每个类/函数都有中文 KDoc，说明用途、参数与可能抛出的异常；页面私有组件放在该页面目录下的 `component/`。

## 配置

所有可调数值都在 `app/src/main/assets/config/`：

| 文件 | 用途 |
| --- | --- |
| `config.debug.json` | 本地开发（默认 `http://127.0.0.1:8080`，配合 `adb reverse`） |
| `config.release.json` | 线上（`https://api.nekoanime.top`） |

主要配置项：

- `api.baseUrl / connectTimeoutSeconds / readTimeoutSeconds`
- `api.mockDelayMillis`：给每个请求加模拟延迟，方便肉眼确认「加载中」等状态，线上保持 `0`
- `home.carouselMax / carouselIntervalMillis / recommendTags / continueWatchingFinishedPercent / listPageSize`（列表每页固定 20）
- `update.enabled / platform / channel`：客户端检查更新走的后端 `/api/app/update`

构建时用哪个文件由 `BuildConfig.CONFIG_FILE` 决定（`app/build.gradle.kts` 里按 buildType 注入）。

## 构建与运行

```bash
# 本地调试（需要后端跑在 8080）
adb reverse tcp:8080 tcp:8080
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 正式包（正式签名，连线上接口）
./gradlew assembleRelease      # 产物：app/build/outputs/apk/release/app-release.apk

# 测试包（测试签名，连线上接口，用来发给别人装）
./gradlew assembleBeta         # 产物：app/build/outputs/apk/beta/app-beta.apk
```

三种包的 applicationId 不同（正式包无后缀、dev 加 `.dev`、测试包加 `.test`），可以同时装在一台手机上互不干扰。
dev / beta 的版本号由 `versionNameSuffix` 自动挂 `-dev.<git 提交数>` / `-test.<git 提交数>`；
正式包的 `versionName` / `versionCode` 只在发版时手动改（约定见 `AGENTS.md`）。

签名：`keystore.properties` 指向 `keystore/nekoanime-release.jks`（两者都**不在仓库里**，需要单独备份）。
没有该文件时 `assembleRelease` 仍可编译，只是产出未签名的包。**密钥丢失后无法再给已安装用户发更新包**。

## 功能一览

- **启动页**：初始化（拉用户资料 + 检查更新，并清理更新目录里的残留），完成后进首页再弹更新窗
- **首页**：轮播、继续观看、按配置的题材推荐；「番剧」Tab 是全部动漫列表（筛选：年份 / 月份 / 类型 / 来源 / 分类 + 排序，用底部滚轮面板选，分页 20/页，滑到底加载、到底提示、下拉刷新）
- **搜索**：关键词搜索，分页加载
- **播放页**：手势（单击显隐控制栏、双击播放暂停 / 快进快退、左右竖滑调亮度音量、长按 2 倍速、锁定后除解锁外忽略）、全屏选集抽屉、字幕抽屉（文件带内嵌字幕轨才有入口）、全屏底部栏的上一集 / 下一集、续播（本地优先）、按题材推荐；番剧卡片右下角的**追番按钮**（登录后可追番 / 取消追番，未登录时置灰）
- **历史记录**：分页列表（封面与行等高、进度条独立一行），下拉刷新，点击续播
- **用户中心 / 设置**：登录（账号密码 / Bangumi 一键登录）、更换头像（圆形裁剪 + 直传对象存储）、修改昵称、修改密码、Bangumi 绑定与解绑、清除缓存
- **检查更新**：启动自动检查（可勾选 7 天免打扰）、设置里手动检查（不带免打扰）、下载进度、下载完成自动调起安装、已下载不重复下载

## 与后端的接口

- 番剧（公开）：`/api/anime/hot`、`/list`、`/search`、`/recommend/tag`、`/recommend/anime`、`/{id}`、`/episode/play/{episodeId}`
- 账号：`/api/user/auth/login`、`/api/user/auth/password`、`/api/user/auth/register/bangumi`、`/api/auth/oauth/login/bangumi`（+`/status`）
- 用户数据（需登录）：`/api/user/data/info`、`/history`、`/profile/nickname`、`/avatar/upload-url`、`/avatar/commit`、`/episode/{id}/progress`、`/anime/{id}/subscribe`（GET 查状态 / POST 改状态）、`/bangumi/binding`、`/bangumi/bind`、`/bangumi/unbind`
- 更新（公开）：`/api/app/update`

接口路径全部集中在 `data/api/ApiRoutes.kt`：固定路径是常量，带动态参数的（`animeDetail(id)`、`episodePlay(id)`）是函数，调用方不会手写字符串拼接。

## 导航与返回

页面用官方 **Navigation Compose 的类型安全路由**：`AppRoute.kt` 里每个页面是一个 `@Serializable` 数据类 / 数据对象
（`HomeRoute`、`PlayRoute(animeId, startEpisodeId, startPositionMs)`…），`MainActivity` 里 `NavHost` 用 `composable<T>` 注册。
换成官方路由的原因：早先的自研路由只有一个 `route` 状态，**没有返回栈也没有状态保存**，从二级页返回时列表、滚动位置、
搜索关键字都得重新来一遍；NavHost 会给每个目的地保存 `rememberSaveable` 状态，返回栈也天然支持层级返回。

- **层级返回**：播放页底部推荐点进去会再压一层播放页，返回回到上一个番剧；搜索页 → 播放页 返回回到搜索页，并且**保留上次的关键字**（关键字由外层保存）。
- **Tab 页返回**：在首页这一层按返回，如果当前不在「主页」Tab，先切回主页；已经在主页则提示「再按一次退出应用」，2 秒内再按一次才真的退出（对应 B 站那种返回习惯）。
- **一键回主页**：播放页顶栏的主页按钮 `popBackStack(HomeRoute)`，直接回到栈里那个首页实例，不再逐层回退，首页原有的数据与滚动位置都还在。
- **状态保留**：二级页返回不会重新请求（详情、推荐、播放地址、首页各 Tab 的列表都用 `rememberSaveable` 存着）；离开搜索页会主动把关键字与整页状态清掉（`searchResetToken`），保证下次进来是干净的「待搜索」。
- 转场动画区分前进/后退方向（搜索页用缩放、启动页到首页是纯淡入淡出）。

播放器实例（ExoPlayer）挂在 `PlaybackViewModel` 上，跟着播放页的返回栈条目存活，所以「A → 推荐 → B → 返回 A」时 A 的播放器还是原来那个，直接续播。
目前还没有 deep link（`NavHost` 里没注册 `deepLinks`）——等要做「分享某集链接」时再补。

后端的权限、字段与部署方式见 `Server/NekoAnime/docs/development-guide.md`。
