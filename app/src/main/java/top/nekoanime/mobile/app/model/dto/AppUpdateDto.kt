package top.nekoanime.mobile.app.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 检查更新结果（后端 `/api/app/update` 的响应）。
 *
 * @description 版本信息由后端维护（app_release 表 + 服务器上的安装包文件），
 * 客户端不再读静态 JSON：接口按「平台 + 当前版本」比较后直接告诉客户端有没有更新。
 * 没有更新时后端只保证 [hasUpdate] 为 false，其余字段可能为空，所以除它以外都给了默认值。
 *
 * @param hasUpdate 是否有新版本
 * @param version 最新版本号（形如 1.0.1）
 * @param versionCode 最新版本序号（APK 的 versionCode）
 * @param force 是否强制更新（true 时不给「稍后」）
 * @param changelog 更新说明（多行文本）
 * @param fileSize 安装包字节数（下载进度用；为 0 表示后端没给）
 * @param fileMd5 安装包内容 MD5（下载完成后校验）
 * @param downloadUrl 下载地址：可能是相对地址（`/api/app/download/...`），需要拼上接口基地址
 * @param publishedAt 发布时间（字符串，仅展示用）
 */
@Serializable
data class AppUpdateDto(
    @SerialName("has_update") val hasUpdate: Boolean = false,
    val version: String = "",
    @SerialName("version_code") val versionCode: Int = 0,
    val force: Boolean = false,
    val changelog: String = "",
    @SerialName("file_size") val fileSize: Long = 0,
    @SerialName("file_md5") val fileMd5: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
)
