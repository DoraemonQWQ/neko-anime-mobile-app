package top.nekoanime.mobile.app.state

import android.os.Looper
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import top.nekoanime.mobile.app.config.ApiConfig
import top.nekoanime.mobile.app.config.AppConfig
import top.nekoanime.mobile.app.config.ConfigLoader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 使用 Robolectric 与本地 HTTP 服务验证登录态历史隔离，不访问线上接口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinueWatchingStateTest {
    private lateinit var server: MockWebServer
    private lateinit var context: android.content.Context
    private var originalConfig: AppConfig? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        server = MockWebServer()
        server.start()
        replaceConfig(AppConfig(api = ApiConfig(baseUrl = server.url("/").toString().trimEnd('/'))))
        context.getSharedPreferences("neko_user_session", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        SessionState.init(context)
    }

    @After
    fun tearDown() {
        SessionState.logout()
        server.shutdown()
        replaceConfig(originalConfig)
    }

    /** 未登录刷新应同步保持空列表，且完全不发历史接口请求。 */
    @Test
    fun unauthenticatedRefreshKeepsEmptyListWithoutRequest() {
        assertFalse(SessionState.loggedIn)
        ContinueWatchingState.refresh()
        drainMainLooper()

        assertTrue(ContinueWatchingState.items.isEmpty())
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    /** 登录成功后同时发起历史和资料请求，历史结果应显示为当前列表。 */
    @Test
    fun loginLoadsHistoryWithAuthenticatedRequest() {
        server.dispatcher = apiDispatcher(historyResponse("已登录历史"))

        login()
        await { ContinueWatchingState.items.singleOrNull()?.animeTitle == "已登录历史" }

        val requests = takeRequests(2)
        assertTrue(requests.any { it.path?.startsWith("/api/user/data/history") == true })
        assertTrue(requests.any { it.path == "/api/user/data/info" })
        assertTrue(requests.all { it.getHeader("Authorization") == "Bearer test-token" })
        assertEquals("已登录历史", ContinueWatchingState.items.single().animeTitle)
    }

    /** 退出必须立即清空列表，并丢弃退出前已经发出的延迟历史响应。 */
    @Test
    fun logoutClearsImmediatelyAndDiscardsLateHistoryResponse() {
        val historyReturned = java.util.concurrent.CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/api/user/data/history" -> historyResponse("退出后不应回填")
                    .setBodyDelay(700, TimeUnit.MILLISECONDS)
                    .also { historyReturned.countDown() }
                "/api/user/data/info" -> profileResponse()
                else -> MockResponse().setResponseCode(404)
            }
        }
        login()
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        assertTrue("历史请求未进入服务端", historyReturned.await(3, TimeUnit.SECONDS))

        SessionState.logout()
        assertTrue(ContinueWatchingState.items.isEmpty())
        // 再等迟到响应完成，确认被清理的历史没有复活。
        waitForRequestsToFinish(900)
        drainMainLooper()
        assertTrue(ContinueWatchingState.items.isEmpty())
    }

    /** 并发刷新时后发请求先返回，先发的迟到结果不能覆盖较新的历史。 */
    @Test
    fun newerConcurrentRefreshWinsOverLateOlderResponse() {
        val historyCalls = AtomicInteger()
        val olderRefreshStarted = java.util.concurrent.CountDownLatch(1)
        val newerRefreshStarted = java.util.concurrent.CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                "/api/user/data/history" -> {
                    val index = historyCalls.incrementAndGet()
                    when (index) {
                        1 -> historyResponse("初始历史")
                        2 -> {
                            olderRefreshStarted.countDown()
                            assertTrue("旧刷新未进入 Dispatcher", newerRefreshStarted.await(3, TimeUnit.SECONDS))
                            historyResponse("旧刷新").setBodyDelay(700, TimeUnit.MILLISECONDS)
                        }
                        else -> {
                            newerRefreshStarted.countDown()
                            historyResponse("新刷新")
                        }
                    }
                }
                "/api/user/data/info" -> profileResponse()
                else -> MockResponse().setResponseCode(404)
            }
        }
        login()
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        await { ContinueWatchingState.items.singleOrNull()?.animeTitle == "初始历史" }
        ContinueWatchingState.refresh()
        assertTrue("旧刷新未进入服务端", olderRefreshStarted.await(3, TimeUnit.SECONDS))
        ContinueWatchingState.refresh()
        await { ContinueWatchingState.items.singleOrNull()?.animeTitle == "新刷新" }
        waitForRequestsToFinish(900)
        drainMainLooper()

        assertEquals("新刷新", ContinueWatchingState.items.single().animeTitle)
    }

    /** 上报在网络延迟期间退出登录，也必须始终使用发起请求的会话令牌。 */
    @Test
    fun progressRequestKeepsOriginalTokenAcrossSessionChange() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.requestUrl?.encodedPath == "/api/user/data/info" -> profileResponse()
                request.requestUrl?.encodedPath == "/api/user/data/history" -> historyResponse("原账号历史")
                request.requestUrl?.encodedPath?.endsWith("/progress") == true -> MockResponse().setBody("{}")
                else -> MockResponse().setResponseCode(404)
            }
        }
        login()
        await { ContinueWatchingState.items.isNotEmpty() }
        takeRequests(2)
        // 延迟放在 HTTP 构造之前，主动扩大校验完成到读取鉴权头之间的账号切换窗口。
        replaceConfig(AppConfig(api = ApiConfig(
            baseUrl = server.url("/").toString().trimEnd('/'),
            mockDelayMillis = 400,
        )))
        ContinueWatchingState.reportProgress(70, 45)
        drainMainLooper()
        SessionState.logout()
        top.nekoanime.mobile.app.data.api.ApiClient.authToken = "next-account-token"
        waitForRequestsToFinish(800)

        // 已经发起的旧上报可以结束，但不能借用后来账号的令牌，也不能恢复退出前的历史。
        val progress = server.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull(progress)
        assertEquals("/api/user/data/episode/70/progress", progress?.path)
        assertEquals("Bearer test-token", progress?.getHeader("Authorization"))
        assertTrue(ContinueWatchingState.items.isEmpty())
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    /** 用请求路径分流资料与历史，避免登录资料请求误消费历史响应。 */
    private fun apiDispatcher(history: MockResponse): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
            "/api/user/data/history" -> history
            "/api/user/data/info" -> profileResponse()
            else -> MockResponse().setResponseCode(404)
        }
    }

    /** 执行真实登录状态转换，确保测试覆盖 onLoggedIn 的历史刷新流程。 */
    private fun login() {
        runBlocking { SessionState.onLoggedIn("test-token") }
        drainMainLooper()
        assertTrue(SessionState.loggedIn)
    }

    /** 构造包含一条可辨识历史项目的后端分页响应。 */
    private fun historyResponse(title: String): MockResponse = MockResponse().setBody(
        """{"total":1,"limit":10,"page":1,"data":[{"anime_id":7,"anime_title":"$title","episode_id":70,"episode_number":1,"episode_title":"第一集","view_status":0,"progress":20,"duration":120,"cover":"cover.jpg"}]}""",
    )

    /** 返回资料接口成功响应，允许 onLoggedIn 完整执行资料刷新。 */
    private fun profileResponse(): MockResponse = MockResponse().setBody(
        """{"id":1,"username":"tester","nickname":"测试用户","avatar":"","role":0}""",
    )

    /** 读取给定数量的请求，验证路径和鉴权头且避免遗漏并发历史请求。 */
    private fun takeRequests(count: Int): List<RecordedRequest> =
        (0 until count).mapNotNull { server.takeRequest(3, TimeUnit.SECONDS) }

    /** 等待异步状态变更并主动处理 Robolectric 主线程队列。 */
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            drainMainLooper()
            Thread.sleep(10)
        }
        drainMainLooper()
        assertTrue("继续观看状态未在期限内更新", condition())
    }

    /** 等待延迟响应有机会返回，再处理其主线程回调。 */
    private fun waitForRequestsToFinish(millis: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
        while (System.nanoTime() < deadline) {
            drainMainLooper()
            Thread.sleep(10)
        }
    }

    /** 清空 Robolectric 主 Looper 中已经排队的状态回调。 */
    private fun drainMainLooper() {
        if (Looper.myLooper() == Looper.getMainLooper()) shadowOf(Looper.getMainLooper()).idle()
    }

    /** 测试专用地将私有配置替换为动态本地端口，并在结束后恢复原配置。 */
    private fun replaceConfig(config: AppConfig?) {
        val field = ConfigLoader::class.java.getDeclaredField("loaded")
        field.isAccessible = true
        if (config != null && originalConfig == null) originalConfig = field.get(ConfigLoader) as? AppConfig
        field.set(ConfigLoader, config)
    }
}
