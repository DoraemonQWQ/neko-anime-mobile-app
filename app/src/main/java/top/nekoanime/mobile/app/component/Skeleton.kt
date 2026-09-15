package top.nekoanime.mobile.app.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 骨架屏。
 *
 * 目的：数据还没回来时先摆出「内容大致长什么样」的灰块，而不是只给一个转圈——
 * 用户知道接下来会出现什么，也不会出现「转圈停住 → 一大块 UI 突然蹦出来」的突兀感。
 * 灰块用一个很轻的呼吸动画（透明度 0.35 ~ 0.65），不抢视线也不额外耗电。
 */

/** 骨架块呼吸动画的半周期（毫秒） */
private const val SkeletonPulseMillis = 700

/**
 * 一个骨架灰块。
 *
 * @param modifier 尺寸约束（宽高由调用方给）
 * @param shape 形状（默认 8dp 圆角，跟卡片一致）
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SkeletonPulseMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
    )
}

/**
 * 番剧海报卡片的骨架（封面 + 两行标题）。
 *
 * @param width 卡片宽度
 */
@Composable
fun AnimePosterSkeleton(width: Dp = PosterCardWidth) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.39f),
            shape = RoundedCornerShape(0.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(10.dp), shape = RoundedCornerShape(4.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp), shape = RoundedCornerShape(4.dp))
        }
    }
}

/**
 * 番剧网格骨架（「全部动漫」/ 搜索结果的加载态）。
 *
 * @param rows 占位行数
 * @param columns 每行几个（跟真实网格一致）
 */
@Composable
fun AnimeGridSkeleton(rows: Int = 3, columns: Int = 3) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(columns) {
                    Box(modifier = Modifier.weight(1f)) {
                        AnimePosterSkeleton(width = PosterCardWidth)
                    }
                }
            }
        }
    }
}

/**
 * 首页骨架：头图 + 两个题材行。
 */
@Composable
fun HomeSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 轮播头图（16:9）
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )

        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(modifier = Modifier.width(120.dp).height(16.dp), shape = RoundedCornerShape(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { AnimePosterSkeleton() }
                }
            }
        }
    }
}

/**
 * 观看历史骨架（封面 + 右侧两行文字）。
 *
 * @param rows 占位条数
 */
@Composable
fun HistorySkeleton(rows: Int = 5) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SkeletonBlock(modifier = Modifier.width(160.dp).height(90.dp), shape = RoundedCornerShape(0.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp), shape = RoundedCornerShape(4.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f).height(10.dp), shape = RoundedCornerShape(4.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.7f).height(10.dp), shape = RoundedCornerShape(4.dp))
                }
            }
        }
    }
}

/**
 * 播放页骨架。
 *
 * @description 每个尺寸都对着真实组件抄（改真实组件时这里要一起改）：
 * - 上半部分是**固定黑色**区域：状态栏留白 + 16:9 播放器（`PlayScreen.playerModifier`）；
 * - 下面是主题背景，第一块是**选集卡片**（`EpisodeSection`）：surface 底 + 上下 12dp 内边距，
 *   标题行是 4×16 主色条 + 「选集」+「共 N 集」+ 右侧「展开」，选集条是 48×48 的按钮、间距 8dp、左右内边距 12dp；
 * - 再往下是**番剧信息卡片**（`AnimeInfoCard`）：96dp 竖版封面（比例 0.72 → 高 133dp）+ 右侧信息 + 右下追番按钮，
 *   下面还有题材标签与简介占位；
 * - 最后是**推荐卡片**（`RecommendSection`）：标题 + 120dp 横滑卡片。
 */
@Composable
fun PlaySkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 播放器黑底：状态栏那一条也在黑底里（跟真实播放页一致）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(0.dp),
            )
        }

        // ===== 固定区：选集卡片（对外是 12dp 左右 + 12dp 上边距，内部 surface 底 + 上下 12dp）=====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
        ) {
            // 标题行：4×16 主色条 + 「选集」+「共 N 集」+ 右侧「展开」
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(width = 4.dp, height = 16.dp),
                    shape = RoundedCornerShape(2.dp),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 32.dp, height = 14.dp),
                    shape = RoundedCornerShape(4.dp),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(width = 48.dp, height = 12.dp),
                    shape = RoundedCornerShape(4.dp),
                )

                Box(modifier = Modifier.weight(1f))

                SkeletonBlock(
                    modifier = Modifier.size(width = 56.dp, height = 18.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }

            // 选集条：48×48 的按钮，间距 8dp，左右内边距 12dp，距标题 10dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(5) {
                    SkeletonBlock(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
        }

        // ===== 可滚动区：番剧信息卡片 + 推荐卡片 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 番剧信息卡片：surface 底 + 12dp 内边距
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Row {
                    // 竖版封面：96dp，比例 0.72 → 高 133dp
                    SkeletonBlock(
                        modifier = Modifier.size(width = 96.dp, height = 133.dp),
                        shape = RoundedCornerShape(6.dp),
                    )

                    // 右层：上边是标题/评分/更新日，下边是追番按钮（贴着封面底部，跟真实卡片一致）
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(133.dp)
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonBlock(
                                modifier = Modifier.fillMaxWidth(0.9f).height(16.dp),
                                shape = RoundedCornerShape(4.dp),
                            )
                            SkeletonBlock(
                                modifier = Modifier.width(80.dp).height(12.dp),
                                shape = RoundedCornerShape(4.dp),
                            )
                            SkeletonBlock(
                                modifier = Modifier.width(120.dp).height(12.dp),
                                shape = RoundedCornerShape(4.dp),
                            )
                        }

                        SkeletonBlock(
                            modifier = Modifier
                                .align(Alignment.End)
                                .size(width = 76.dp, height = 32.dp),
                            shape = RoundedCornerShape(6.dp),
                        )
                    }
                }

                // 题材标签
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(4) {
                        SkeletonBlock(
                            modifier = Modifier.size(width = 52.dp, height = 22.dp),
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                }

                // 简介（几行）
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth().height(12.dp), shape = RoundedCornerShape(4.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth().height(12.dp), shape = RoundedCornerShape(4.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.55f).height(12.dp), shape = RoundedCornerShape(4.dp))
                }
            }

            // 推荐卡片：标题（只有「推荐」，没有副标题）+ 120dp 横滑卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(
                        modifier = Modifier.size(width = 4.dp, height = 16.dp),
                        shape = RoundedCornerShape(2.dp),
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(width = 40.dp, height = 16.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 横向可滚 + 只留左边内边距：卡片保持 120dp 原宽，
                        // 最后一张被视口裁掉一部分（跟真实 LazyRow 的观感一致），
                        // 不然 Row 会把放不下的卡片压窄，看着就不是那个卡片了
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) { AnimePosterSkeleton() }
                }
            }
        }
    }
}
