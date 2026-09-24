/*
 * pybridge.c —— DevTerminal 原生 Python 引擎的 JNI 桥
 *
 * 设计原则（贴合正常人思维，不重复造轮子）：
 * 1. stdin/stdout/stderr 全部在 Python 层桥接：Python 侧挂自定义 file-like 对象，
 *    其 write/readline 方法调用本桥暴露的 C 函数，C 函数再回调 Java。
 *    不碰 C 管道、不碰文件描述符，语义清晰且无泄漏。
 * 2. 中断走 CPython 官方的 Py_AddPendingCall（线程安全、对死循环有效——
 *    解释器每 N 条字节码检查一次 pending call，回调里抛 KeyboardInterrupt）。
 *    这是 Chaquopy 同款机制，不自己造轮子。
 * 3. 解释器在专用线程里跑（Py_Initialize 必须在持 GIL 的线程完成）；
 *    Kotlin 侧通过 JNI 调用时用 PyGILState_Ensure/Release 临时拿 GIL。
 *
 * 运行时约束：libpython3.13.so 与 libpybridge.so 都放 jniLibs/arm64-v8a，
 * 由 Android linker 自动解析 libpython 依赖（--no-undefined 链接保证）。
 */
#include <jni.h>
#include <Python.h>
#include <string.h>
#include <stdio.h>

/* ---------------- 与 Java 侧的回调（见 NativeBridge.kt） ---------------- */

typedef struct {
    JavaVM *jvm;
    jobject bridge;          /* NativeBridge 实例（全局引用） */
    jmethodID onOutput;      /* (String stream, String text) -> void */
    jmethodID onInputLine;   /* () -> String；阻塞等待用户输入，返回 null 表示 EOF */
    jmethodID onState;       /* (String state, String msg) -> void */
} BridgeCtx;

static BridgeCtx g_ctx = {0};

/* ---------------- Python 侧桥接函数 ---------------- */

static PyObject *bridge_output(PyObject *self, PyObject *args) {
    const char *text;
    const char *stream;
    if (!PyArg_ParseTuple(args, "ss", &stream, &text)) return NULL;
    if (g_ctx.jvm && g_ctx.bridge) {
        JNIEnv *env = NULL;
        if ((*g_ctx.jvm)->GetEnv(g_ctx.jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
            (*g_ctx.jvm)->AttachCurrentThread(g_ctx.jvm, &env, NULL);
        }
        if (env) {
            jstring jStream = (*env)->NewStringUTF(env, stream);
            jstring jText = (*env)->NewStringUTF(env, text);
            (*env)->CallVoidMethod(env, g_ctx.bridge, g_ctx.onOutput, jStream, jText);
            (*env)->DeleteLocalRef(env, jStream);
            (*env)->DeleteLocalRef(env, jText);
        }
    }
    Py_RETURN_NONE;
}

static PyObject *bridge_input(PyObject *self, PyObject *args) {
    (void)args;
    if (!g_ctx.jvm || !g_ctx.bridge) {
        PyErr_SetString(PyExc_EOFError, "bridge not ready");
        return NULL;
    }
    JNIEnv *env = NULL;
    jboolean needDetach = JNI_FALSE;
    if ((*g_ctx.jvm)->GetEnv(g_ctx.jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_ctx.jvm)->AttachCurrentThread(g_ctx.jvm, &env, NULL) == JNI_OK) {
            needDetach = JNI_TRUE;
        }
    }
    if (!env) {
        PyErr_SetString(PyExc_RuntimeError, "cannot attach JVM");
        return NULL;
    }
    /* 阻塞等待 Kotlin 侧返回一行输入（内部用协程 + 输入队列） */
    jstring line = (jstring)(*env)->CallObjectMethod(env, g_ctx.bridge, g_ctx.onInputLine);
    if (needDetach) (*g_ctx.jvm)->DetachCurrentThread(g_ctx.jvm);
    if (line == NULL) {
        PyErr_SetString(PyExc_EOFError, "EOF when reading a line");
        return NULL;
    }
    const char *utf = (*env)->GetStringUTFChars(env, line, NULL);
    PyObject *result = PyUnicode_FromString(utf ? utf : "");
    (*env)->ReleaseStringUTFChars(env, line, utf);
    (*env)->DeleteLocalRef(env, line);
    return result;
}

static PyMethodDef bridge_methods[] = {
    {"_output", bridge_output, METH_VARARGS, "forward output to Kotlin"},
    {"_input",  bridge_input,  METH_VARARGS, "read one line from Kotlin"},
    {NULL, NULL, 0, NULL}
};

static struct PyModuleDef bridge_module = {
    PyModuleDef_HEAD_INIT, "dtbridge", NULL, -1, bridge_methods
};

static PyObject *module_init(void) {
    return PyModule_Create(&bridge_module);
}

/* ---------------- 中断：Py_AddPendingCall 抛 KeyboardInterrupt ---------------- */

static int interrupt_callback(void *unused) {
    (void)unused;
    PyErr_SetInterrupt();
    return 0;
}

/* ---------------- JNI 入口 ---------------- */

static JavaVM *g_jvm = NULL;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_devterminal_engine_NativeBridge_attach(
    JNIEnv *env, jobject thiz, jobject bridge)
{
    if (g_ctx.bridge) {
        (*env)->DeleteGlobalRef(env, g_ctx.bridge);
        g_ctx.bridge = NULL;
    }
    g_ctx.jvm = g_jvm;
    g_ctx.bridge = (*env)->NewGlobalRef(env, bridge);
    jclass cls = (*env)->GetObjectClass(env, bridge);
    g_ctx.onOutput   = (*env)->GetMethodID(env, cls, "onOutput",   "(Ljava/lang/String;Ljava/lang/String;)V");
    g_ctx.onInputLine = (*env)->GetMethodID(env, cls, "onInputLine", "()Ljava/lang/String;");
    g_ctx.onState    = (*env)->GetMethodID(env, cls, "onState",    "(Ljava/lang/String;Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, cls);
}

JNIEXPORT void JNICALL
Java_com_devterminal_engine_NativeBridge_detach(JNIEnv *env, jobject thiz)
{
    (void)thiz;
    if (g_ctx.bridge) {
        (*env)->DeleteGlobalRef(env, g_ctx.bridge);
        g_ctx.bridge = NULL;
    }
    memset(&g_ctx, 0, sizeof(g_ctx));
}

/* 初始化解释器：必须在持有 GIL 的线程调用（专用 Python 线程） */
JNIEXPORT jboolean JNICALL
Java_com_devterminal_engine_NativeBridge_initialize(JNIEnv *env, jobject thiz, jstring stdlibPath)
{
    (void)env; (void)thiz;
    if (Py_IsInitialized()) return JNI_TRUE;
    const char *sp = stdlibPath ? (*env)->GetStringUTFChars(env, stdlibPath, NULL) : NULL;
    if (sp) {
        /* 让标准库从 APK 解压目录加载：/data/user/0/.../files/python-stdlib */
        setenv("PYTHONHOME", sp, 1);
        setenv("PYTHONPATH", sp, 1);
        (*env)->ReleaseStringUTFChars(env, stdlibPath, sp);
    }
    Py_Initialize();
    PyEval_SaveThread();  /* 释放 GIL，允许其他线程进入 */

    /* 注入桥模块与 stdio 桥接 */
    if (PyImport_AppendInittab("dtbridge", module_init) != 0) return JNI_FALSE;
    PyGILState_STATE gil = PyGILState_Ensure();
    PyObject *mod = PyImport_ImportModule("dtbridge");
    if (!mod) {
        PyErr_Print();
        PyGILState_Release(gil);
        return JNI_FALSE;
    }
    /* 挂载自定义 stdin/stdout/stderr（纯 Python，不碰 C 层） */
    PyRun_SimpleString(
        "import sys, io\n"
        "class _DtOut(io.TextIOBase):\n"
        "    def write(self, s):\n"
        "        import dtbridge as _d\n"
        "        _d._output('stdout', s)\n"
        "        return len(s)\n"
        "    def flush(self):\n"
        "        pass\n"
        "class _DtErr(io.TextIOBase):\n"
        "    def write(self, s):\n"
        "        import dtbridge as _d\n"
        "        _d._output('stderr', s)\n"
        "        return len(s)\n"
        "    def flush(self):\n"
        "        pass\n"
        "class _DtIn(io.TextIOBase):\n"
        "    def readline(self, size=-1):\n"
        "        import dtbridge as _d\n"
        "        line = _d._input()\n"
        "        return line + '\\n'\n"
        "    def read(self, size=-1):\n"
        "        import dtbridge as _d\n"
        "        return _d._input() + '\\n'\n"
        "sys.stdout = _DtOut()\n"
        "sys.stderr = _DtErr()\n"
        "sys.stdin = _DtIn()\n"
        "sys.stdout.flush()\n");
    PyGILState_Release(gil);
    return Py_IsInitialized() ? JNI_TRUE : JNI_FALSE;
}

/* 在专用线程里执行一段脚本文件（调用前无需持有 GIL，内部自管） */
JNIEXPORT jstring JNICALL
Java_com_devterminal_engine_NativeBridge_execFile(JNIEnv *env, jobject thiz, jstring path)
{
    (void)thiz;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    PyGILState_STATE gil = PyGILState_Ensure();
    jstring result = NULL;
    if (p) {
        FILE *fp = fopen(p, "r");
        if (!fp) {
            char buf[512];
            snprintf(buf, sizeof(buf), "无法打开文件：%s", p);
            result = (*env)->NewStringUTF(env, buf);
        } else {
            /* FILE 模式：保持用户代码的 __file__ / traceback 行号语义 */
            PyObject *globals = PyDict_New();
            PyDict_SetItemString(globals, "__name__", PyUnicode_FromString("__main__"));
            PyObject *builtins = PyImport_ImportModule("builtins");
            PyDict_SetItemString(globals, "__builtins__", builtins);
            PyObject *rv = PyRun_FileExFlags(fp, p, Py_file_input, globals, globals, 0, NULL);
            if (rv) {
                Py_DECREF(rv);
            } else {
                /* 取异常文本（含 traceback），交给 Kotlin 侧展示 */
                PyObject *ptype, *pvalue, *ptb;
                PyErr_Fetch(&ptype, &pvalue, &ptb);
                PyErr_NormalizeException(&ptype, &pvalue, &ptb);
                PyObject *str = PyObject_Str(pvalue);
                const char *msg = str ? PyUnicode_AsUTF8(str) : "Unknown error";
                result = (*env)->NewStringUTF(env, msg ? msg : "Unknown error");
                Py_XDECREF(str);
                Py_XDECREF(ptype); Py_XDECREF(pvalue); Py_XDECREF(ptb);
            }
            Py_XDECREF(builtins);
            Py_DECREF(globals);
            fclose(fp);
        }
        (*env)->ReleaseStringUTFChars(env, path, p);
    }
    PyGILState_Release(gil);
    return result;  /* NULL = 执行成功；非 NULL = 错误消息 */
}

/* 请求中断：Py_AddPendingCall 在解释器字节码检查点抛 KeyboardInterrupt */
JNIEXPORT void JNICALL
Java_com_devterminal_engine_NativeBridge_requestInterrupt(JNIEnv *env, jobject thiz)
{
    (void)env; (void)thiz;
    PyGILState_STATE gil = PyGILState_Ensure();
    Py_AddPendingCall(interrupt_callback, NULL);
    PyGILState_Release(gil);
}

/* 关闭解释器 */
JNIEXPORT void JNICALL
Java_com_devterminal_engine_NativeBridge_shutdown(JNIEnv *env, jobject thiz)
{
    (void)env; (void)thiz;
    if (Py_IsInitialized()) {
        PyGILState_STATE gil = PyGILState_Ensure();
        Py_FinalizeEx();
        PyGILState_Release(gil);
    }
}
