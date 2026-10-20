# 工作日志（当前状态）

> 这份文件是**给下一次会话的自己看的**：开工先读完，收工顺手更新。
> 只记这个项目自己的状态、待办与约定；环境运维之类的琐事不写在这里。

**最后更新**：2026-10-19

## 一句话现状

移动端 **1.4.1 已发版**；播放页外挂字幕已接入并完成真机验证，`dev` 工作区保留本轮待提交改动。

## 版本与分支

| 位置 | 提交 / 版本 | 说明 |
| --- | --- | --- |
| `dev` | 与 `origin/dev` 同步 | 日常开发分支；最新提交以 `git log -1 dev` 为准 |
| `main` | 与 `origin/main` 同步 | 发布分支，已 `--no-ff` 合入 1.4.0 发版与文档更新；最新提交以 `git log -1 main` 为准 |
| 线上更新通道 | `1.4.1` / versionCode `1040100` | `mobile_app/NekoAnime-1.4.1.apk`，14857370 字节，md5 `31efdb6ea8159dbcccb8bdc5e4ae6cea` |

## 刚做完（2026-10-19 实机验证）

1. 本地 APK 与手机已安装 APK 均确认 `top.nekoanime.mobile.app.dev` / `1.4.0-dev.59`，提取安装包后 SHA256 与本地 APK 一致；本轮未操作正式包。
2. 从现有历史进入《在超市后门吸烟的二人》第 1 集，简体外挂显示“真是乱花钱”，繁体外挂显示“真是亂花錢”；关闭字幕后同帧文字消失，恢复简体后重新显示。
3. 暂停时切换外挂，新轨当前字幕需要推进播放时钟才出现，留待后续处理；初始内嵌轨仍观察到重复叠行，外挂单行样本正常，未覆盖全部双语样本。
4. 截图、包身份、APK 哈希、播放器日志和详细结果保存在工作区 `TEMP/dev-subtitle-20261019/RESULT.md`；本轮未修改播放器实现、未提交或发布。

## 发版记录（2026-09-28）

1. **播放页追番按钮接上后端** — `edccd46`
   - 补全 `GET/POST /api/user/data/anime/{id}/subscribe` 链路：`ApiRoutes` → `UserApi` → `UserRepository` → DTO（响应 `is_subscribed`、请求体 `status`，跟后端 `UserLibraryController` 对齐）。
   - 播放页登录后才查状态；未登录 / 退出登录复位成未追番，按钮禁用；点追番以服务端返回的状态为准刷新，成功失败各提示一句，busy 挡住连点。
   - `PlayRoute` 把 `loggedIn` 从 `SessionState` 传进 `PlayScreen`（之前漏了这一处，按钮永远点不动）。
2. **正式发版 1.4.0** — `5214a1c`（`versionName` 1.3.0 → 1.4.0，`versionCode` 1030000 → 1040000）。
3. **合并进 main** — `8b9ec36`（`--no-ff`，用户明确放行后才合）。
4. K80 上用 dev 包（`1.3.0-dev.54`）实机验证过：首页 / 播放页数据正常，未登录时追番按钮按设计置灰不可点。

## 发版记录（2026-10-19）

1. **播放页外挂字幕**
   - 保留字幕原始元数据，异步下载与解析，和内嵌字幕统一进入 Media3 选择菜单。
   - 下载或解析失败时只隐藏对应轨道，不影响视频播放；播放器释放时取消字幕任务。
   - 修复 Media3 外挂 WebVTT 的 legacy 解码兼容，并完成真机切换、关闭、倍速、全屏验证。
2. **正式发版 1.4.1** — `versionCode 1040100`，已上传通用 Android 更新通道。

## 下一步可以做的

0. **外挂暂停切轨行为**：验证并处理暂停切轨后当前 cue 不立即显示；扩展双语 cue 渲染样本验证。
1. **401 自动登出**（优先）
   - 现状：token 过期时只会在各自的调用点静默失败（首页「继续观看」变空、点追番弹一句提示），但登录态还显示成已登录。
   - 做法：让 `ApiClient` 的失败带上 HTTP 状态码（例如加 `ApiException(code, message)`，把现在统一的 `error(failureMessage(...))` 换成它），调用方遇到 401 就 `SessionState.logout()`。
   - 涉及：`data/api/ApiClient.kt`、`state/SessionState.kt`，以及首页继续观看、追番按钮、历史、昵称 / 头像这些调用点。
2. **「我的追番」列表页**
   - `page/user/UserTab.kt` 里已经预留了入口位置（`EntryRow`）。先确认后端有没有「订阅列表」接口，没有就先加后端。
3. **手机上升级流程验证**：K80 上现在只有 dev 包，1.4.0 正式包的下载 → 安装 → 覆盖还没实测过。
4. **README 还有点过期**：根目录 `README.md` 里「单 Activity + 自研路由（AnimatedContent 转场 + 自己的返回栈），不引导航库」那节已经不对了，现在是 Navigation Compose，有空顺手改。

## 开发与发布备忘

- **dev 包连的地址**：`app/src/main/assets/config/config.debug.json` 固定写 `http://127.0.0.1:8080`，所以要先用 `adb reverse tcp:8080 tcp:8080` 把手机的这个端口转回本机。8080 上的后端从哪来（本地那套还是线上）按当次需要问用户，别写死在这里。
- **发版三步**：改 `app/build.gradle.kts` 的 `versionName` / `versionCode` → `.\gradlew.bat :app:assembleRelease`（`app/build/outputs/apk/release/app-release.apk`，正式签名，`keystore.properties` 在本地不入库）→ 调管理端接口上传：
  `POST /api/admin/app/publish`（multipart：`platform` / `version` / `versionCode` / `channel` / `changelog` / `force` / `file`）。
  管理端账号密码**当面问用户要，不要写进仓库**。
- **发布完要核对**：`GET /api/app/update?platform=android&version=<旧版本>` 能返回新版本 + changelog，并把 `download_url` 真下下来对一次 md5。
- **安装包落点**：assets 桶 `mobile_app/NekoAnime-{版本号}.apk`，发布新版本时旧包由后端自动清理。

## 约定速查

- 日常开发在 `dev`；**合并 `main` 必须先拿到用户明确同意**，用 `git merge --no-ff dev`。
- 版本号只在发版时动，dev 期靠 `-dev.<提交数>` 区分构建（细节见 `AGENTS.md`）。
- 每次收工更新本文件：改「最后更新」「一句话现状」「刚做完」「下一步」。
