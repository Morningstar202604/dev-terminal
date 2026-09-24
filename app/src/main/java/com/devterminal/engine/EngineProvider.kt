package com.devterminal.engine

import android.content.Context
import android.util.Log

/**
 * 引擎工厂：优先使用原生 CPython（NativeEngine，PEP 738 官方支持），
 * 若 APK 未打包原生运行时（libpybridge.so / python-stdlib.zip 缺失）则回退 Pyodide。
 *
 * 判定方式简单直接，不靠配置文件：
 * - assets 里有 python-stdlib.zip（标准库打包随 APK 存在）→ 原生可用
 * - System.loadLibrary("pybridge") 成功（JNI 桥与 libpython 都在 jniLibs）→ 原生可用
 *
 * 过渡期两种 APK 形态都能跑；原生引擎上线后，Pyodide（72MB assets）可整体下线。
 */
object EngineProvider {

    private const val TAG = "EngineProvider"

    fun create(context: Context): RunEngine {
        return if (nativeAvailable(context)) {
            Log.i(TAG, "使用原生 CPython 引擎")
            NativeEngine(context)
        } else {
            Log.i(TAG, "原生运行时缺失，回退 Pyodide 引擎")
            PyodideEngine(context)
        }
    }

    /** 原生运行时是否已随 APK 打包 */
    private fun nativeAvailable(context: Context): Boolean {
        val hasStdlib = runCatching {
            context.assets.open("python-stdlib.zip").use { }
        }.isSuccess
        if (!hasStdlib) return false
        // NativeBridge.companion 的 System.loadLibrary 若失败会在初始化时报错，
        // 这里显式探测一次，避免运行时才炸
        return runCatching {
            // 触发 NativeBridge 静态初始化（loadLibrary）
            Class.forName("com.devterminal.engine.NativeBridge")
        }.isSuccess
    }
}
