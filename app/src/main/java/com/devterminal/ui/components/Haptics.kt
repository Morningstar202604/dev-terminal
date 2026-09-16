package com.devterminal.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 触觉反馈。
 *
 * 为什么移动端编程工具需要它：用户敲完代码点「运行」，眼睛往往还在编辑器上，
 * 不会盯着底部输出面板。运行结果如果只有视觉反馈，用户要主动低头看一眼才知道
 * 跑完没有。一次轻震动能把这个信息直接传达，省掉一次视线转移——
 * 这类"不打断心流"的反馈在长时间写码场景里价值很高。
 *
 * 三种强度，按语义而非按强度命名，避免调用方纠结该用哪一档：
 *  - [tick]    ：普通状态变化（切换文件、插入符号）
 *  - [success] ：一次成功的操作收尾（运行结束且退出码为 0）
 *  - [failure] ：需要用户注意的问题（运行失败、校验不通过）
 *
 * 全部包了版本判断与 runCatching：不同 ROM 对手势常量的支持不一致，
 * 触觉反馈属于"锦上添花"，绝不能因为它让主流程崩掉。
 */
class Haptics(private val view: android.view.View) {

    /** 轻触感：普通确认 */
    fun tick() {
        perform(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** 成功收尾：运行完成、保存成功 */
    fun success() {
        perform(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        )
    }

    /**
     * 失败提示：用 [HapticFeedbackConstants.LONG_PRESS] 而非 REJECT。
     * REJECT 在部分 ROM 上被实现为强震动，连续失败时体感很吵；
     * LONG_PRESS 更钝、更短，刚好够"硌一下"而不刺耳。
     */
    fun failure() {
        perform(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun perform(constant: Int) {
        runCatching { view.performHapticFeedback(constant) }
    }
}

/** 取当前界面的触觉反馈入口 */
@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
