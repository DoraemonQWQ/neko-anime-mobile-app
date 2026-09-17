package top.nekoanime.mobile.app.page.play.component.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 播放器右侧抽屉的共用样式（选集抽屉与字幕抽屉共用）。
 *
 * @description 两个抽屉要长得一样，颜色和宽度就只能有一份定义，否则改了一处另一处会慢慢走样。
 */

/** 选中态的主色：与前台主色一致（白 + 淡灰 + 粉里的那个粉） */
internal val PlayerDrawerActiveColor = Color(0xFFE91E63)

/** 抽屉面板底色：播放器控制栏是深色，抽屉也保持同一套深色，避免半黑半白 */
internal val PlayerDrawerPanelColor = Color(0xF21B1C20)

/**
 * 抽屉面板宽度。
 *
 * @description 播放器那边的遮罩要按这个宽度给抽屉让出右侧（只让阴影接"点击关闭"），
 * 所以宽度放在这里共用，改一处两边都对。
 */
internal val PlayerDrawerWidth = 320.dp

/**
 * 竖屏下字幕抽屉的宽度。
 *
 * @description 竖屏时播放器只有 16:9 那么高的一块，抽屉再按 320dp 展开就把画面盖得只剩一条，
 * 所以收窄到 176dp：够放"关闭字幕 / 简体中文 / 繁体中文"这几行，也不至于糊住画面。
 */
internal val PlayerDrawerNarrowWidth = 176.dp

/**
 * 抽屉里选项名字最多显示几个字。
 *
 * @description 轨道名是压制组自己写的，长度完全不可控（有的会把整条片名塞进去）；
 * 超过这个长度直接截断，宁可看不全也不让一行字把抽屉挤变形。
 */
internal const val PlayerDrawerLabelMaxLength = 6
