package com.devterminal.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 动效体系（Motion）。
 *
 * 为什么单独立一个文件：动效是「同一套视觉语言」里最容易被写散的部分——
 * 各处随手 `tween(300)` 会让界面节奏忽快忽慢，用户说不上哪里怪，就是觉得不精致。
 * 集中定义后，所有动画共享同一组时长与缓动曲线，观感才会一致。
 *
 * 三条准则，与 Theme.kt 的「静谧」一脉相承：
 *  1. **快**。移动端手势驱动的动画超过 250ms 就会觉得"黏"。面板类用 [medium]，
 *     微交互（按下、状态圆点）用 [fast]。
 *  2. **缓出**。入场用 [EmphasizedDecelerate]（快进慢出），符合"物体受推力后减速"的直觉；
 *     退场用 [EmphasizedAccelerate]（慢进快出），让消失干脆。
 *  3. **克制**。位移一律不超过 16dp。大幅滑入滑出在长时间使用的工具类 App 里是负担，
 *     用户一天要开几十次面板。
 */
object Motion {

    // ---------- 时长 ----------
    /** 微交互：按下反馈、圆点变色 */
    const val fast = 120
    /** 主时长：面板淡入、内容切换 */
    const val medium = 220
    /** 较大的层级变化：全屏转场 */
    const val slow = 320

    // ---------- 缓动 ----------
    /** 入场：起步快、收尾慢，读起来「稳」 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 退场：起步慢、收尾快，读起来「干脆」 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 标准曲线（Material 默认节奏），用于不需要强调方向的变化 */
    val Standard: Easing = FastOutSlowInEasing

    /** 线性缓出：进度条一类「匀速推进」的场景 */
    val Linear: Easing = LinearOutSlowInEasing

    // ---------- 预设 Spec ----------

    /** 面板/对话框淡入 */
    fun <T> enterSpec() = tween<T>(
        durationMillis = medium,
        easing = EmphasizedDecelerate
    )

    /** 面板/对话框淡出 */
    fun <T> exitSpec() = tween<T>(
        durationMillis = fast + 40,
        easing = EmphasizedAccelerate
    )

    /** 微交互（颜色、透明度） */
    fun <T> microSpec() = tween<T>(
        durationMillis = fast,
        easing = Standard
    )

    /**
     * 跟手的位移（拖拽把手、滑动切换）。
     * 用弹簧而非 tween：手势中途反向时 tween 会"顿"一下，弹簧不会。
     * 阻尼调得偏高，避免出现过度回弹——工具类 App 不适合俏皮的抖动。
     */
    fun <T> followSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 面板入场位移量：小到几乎察觉不到，只用来表达"从哪来" */
    val enterOffset: Dp = 12.dp

    /** 面板入场位移的像素级 IntOffset（供需要 IntOffset 的动画使用） */
    fun enterOffsetPx(density: Float): IntOffset =
        IntOffset(0, (enterOffset.value * density).toInt())
}
