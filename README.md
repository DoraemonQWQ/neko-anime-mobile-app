# Neko Anime 移动端（Android）

Neko Anime 的安卓客户端，接口全部来自 [Neko Anime 后端](../../Server/NekoAnime)。当前版本 **1.0.0**。

## 技术栈

- **Kotlin 2.0 + Jetpack Compose**（Material 3，自研主题：白 / 淡灰 / 粉，深浅色跟随系统）
- **Media3 ExoPlayer**：播放页（手势、选集抽屉、锁屏、全屏、续播）
- **Coil**：封面与头像；**OkHttp + kotlinx.serialization**：接口调用
- 单 Activity + 自研路由（`AnimatedContent` 转场），不引导航库

## 目录结构

```
app/src/main/
├── assets/config/                 # 运行期配置（debug / release 各一份）
├── java/top/nekoanime/mobile/app/
│   ├── component/                 # 公共 UI（含 theme/、弹框、卡片、搜索框）
│   ├── config/                    # AppConfig 映射类 + 配置加载器
│   ├── data/                      # api（接口）/ local（本机存储）/ repository
│   ├── model/                     # dto（接口结构）与 entity（界面实体）
│   ├── page/                      # 页面：splash / home / search / play / history / settings / user
│   ├── service/                   # 业务服务（版本更新、播放埋点）
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
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 正式包
./gradlew assembleRelease      # 产物：app/build/outputs/apk/release/app-release.apk
```

签名：`keystore.properties` 指向 `keystore/nekoanime-release.jks`（两者都**不在仓库里**，需要单独备份）。
没有该文件时 `assembleRelease` 仍可编译，只是产出未签名的包。**密钥丢失后无法再给已安装用户发更新包**。

## 功能一览

- **启动页**：初始化（拉用户资料 + 检查更新，并清理更新目录里的残留），完成后进首页再弹更新窗
- **首页**：轮播、继续观看、按配置的题材推荐、全部动漫（分页 20/页，滑到底加载、到底提示、下拉刷新）
- **搜索**：关键词搜索，分页加载
- **播放页**：手势（双击暂停 / 左右快进 / 上下音量亮度）、选集抽屉、锁定、全屏、片尾自动下一集、续播（本地优先）、按题材推荐
- **历史记录**：分页列表（封面与行等高、进度条独立一行），下拉刷新，点击续播
- **用户中心 / 设置**：登录、更换头像（圆形裁剪 + 直传对象存储）、修改昵称、修改密码、清除缓存
- **检查更新**：启动自动检查（可勾选 7 天免打扰）、设置里手动检查（不带免打扰）、下载进度、下载完成自动调起安装、已下载不重复下载

## 与后端的接口

- 番剧：`/api/anime/hot`、`/list`、`/search`、`/recommend/tag`、`/recommend/anime`、`/{id}`、`/episode/play/{episodeId}`
- 用户：`/api/user/auth/login`、`/api/user/data/{info,history,profile/nickname,avatar/*}`
- 更新：`/api/app/update`

后端的权限、字段与部署方式见 `Server/NekoAnime/docs/development-guide.md`。
