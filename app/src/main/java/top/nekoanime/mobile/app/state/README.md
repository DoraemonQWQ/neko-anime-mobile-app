# state

跨页面共享的**运行时状态**放这里，比如：

- `SessionState.kt`：登录态（用户 id、昵称、头像、角色），登录/登出后所有页面都读它
- `PlaybackSessionState.kt`：当前正在看什么（首页"继续观看"与播放页共用）

注意：持久化读写的实现（SharedPreferences / DataStore）放 `data/local/`，
这里只放"内存里的那份状态 + 变更入口"。
