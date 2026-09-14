package top.nekoanime.mobile.app.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 接口相关配置。
 *
 * @param baseUrl 后端地址（不带结尾斜杠）
 * @param connectTimeoutSeconds 连接超时（秒）
 * @param readTimeoutSeconds 读取超时（秒）
 * @param mockDelayMillis 模拟网络延迟（毫秒）：本地开发时给每个请求加一点延迟，
 * 方便肉眼确认「加载中」这类状态是正常的；线上配置写 0 即可关掉
 */
@Serializable
data class ApiConfig(
    val baseUrl: String = "",
    val connectTimeoutSeconds: Long = 10,
    val readTimeoutSeconds: Long = 20,
    val mockDelayMillis: Long = 0,
)

/**
 * 首页相关配置。
 *
 * @param carouselMax 轮播最多几张
 * @param carouselIntervalMillis 轮播自动切换间隔（毫秒）
 * @param recommendTags 首页按题材推荐的板块（顺序即展示顺序，几组就配几个）
 * @param recommendTitlePrefix 题材板块的标题前缀（例如「番剧推荐 - 」+ 题材）
 * @param continueWatchingMax 继续观看最多几条
 * @param continueWatchingFinishedPercent 进度到多少百分比算看完（继续观看会过滤掉）
 * @param listPageSize 「全部动漫」每页条数
 */
@Serializable
data class HomeConfig(
    val carouselMax: Int = 10,
    val carouselIntervalMillis: Long = 3000,
    val recommendTags: List<String> = emptyList(),
    val recommendTitlePrefix: String = "",
    val continueWatchingMax: Int = 10,
    val continueWatchingFinishedPercent: Int = 90,
    val listPageSize: Int = 24,
)

/**
 * 播放页相关配置。
 *
 * @param recommendCount 底部推荐几个
 */
@Serializable
data class PlayConfig(
    val recommendCount: Int = 10,
)

/**
 * 检查更新配置。
 *
 * @description 版本信息由后端接口提供（`/api/app/update`），这里只描述"客户端是谁"：
 * 平台与渠道决定后端返回哪条发布记录。App 启动初始化时会自动检查一次。
 *
 * @param enabled 是否开启检查更新（关掉就不会在启动时请求接口）
 * @param platform 平台标识，Android 固定 android
 * @param channel 渠道（空表示通用包；将来做灰度时填 beta 之类）
 */
@Serializable
data class UpdateConfig(
    val enabled: Boolean = true,
    val platform: String = "android",
    val channel: String = "",
)

/**
 * 应用配置（对应 assets/config/config.<模式>.json）。
 *
 * @description 跟后端一样按「模式」分文件：debug 连本机后端，release 连线上域名。
 * 所有可调数值都放这里，代码里不再写死；加配置只改数据类 + json 文件。
 * 未知字段会被忽略，所以老版本 App 遇到新加的配置项也不会崩。
 *
 * @param api 接口配置
 * @param home 首页配置
 * @param play 播放页配置
 */
@Serializable
data class AppConfig(
    @SerialName("api") val api: ApiConfig = ApiConfig(),
    @SerialName("home") val home: HomeConfig = HomeConfig(),
    @SerialName("play") val play: PlayConfig = PlayConfig(),
    @SerialName("update") val update: UpdateConfig = UpdateConfig(),
)
