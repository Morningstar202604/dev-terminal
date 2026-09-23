// DevTerminal 离线 Python 运行器（Web Worker）
// 在独立线程里加载 Pyodide，避免大计算或死循环卡死 UI；
// 超时通过 Pyodide 的中断缓冲（SharedArrayBuffer）取消运行，worker 不终止、解释器保持温热。
import { loadPyodide } from './pyodide/full/pyodide.mjs';

let pyodide = null;
let _indexURL = null;
let interruptBuffer = null;

function post(type, data) {
  self.postMessage(Object.assign({ type }, data || {}));
}

// 清洗 Pyodide 注入的内部栈帧（_pyodide/_base.py、CodeRunner 等），
// 否则它们会混进 traceback，既干扰阅读，又会被 parseErrorLine 误判为可跳转行。
function cleanTraceback(tb) {
  return tb.split('\n').filter((line) => {
    if (/_pyodide/.test(line)) return false;
    if (/python312\.zip/.test(line)) return false;
    if (/^\s*await CodeRunner\(/.test(line)) return false;
    if (/^\s*coroutine = eval/.test(line)) return false;
    if (/^\s*\^+/.test(line)) return false; // 错误位置指示符 ^^^^
    return true;
  }).join('\n');
}

async function ensure() {
  if (pyodide) return pyodide;
  post('status', { msg: '正在加载离线 Python 解释器…' });
  pyodide = await loadPyodide({ indexURL: _indexURL });
  // 中断缓冲：超时取消死循环用。需页面开启 COOP/COEP（跨源隔离）才有 SharedArrayBuffer。
  if (typeof SharedArrayBuffer !== 'undefined') {
    interruptBuffer = new Int32Array(new SharedArrayBuffer(4));
    pyodide.setInterruptBuffer(interruptBuffer);
  }
  post('ready', {});
  return pyodide;
}

self.onmessage = async (e) => {
  const msg = e.data;

  if (msg.type === 'init') {
    _indexURL = msg.indexURL;
    try { await ensure(); }
    catch (err) { post('error', { text: String((err && err.stack) || err) }); }
    return;
  }

  if (msg.type === 'run') {
    try { await ensure(); }
    catch (err) {
      post('error', { text: '解释器加载失败：' + String(err) });
      post('done', { exitCode: 1 });
      return;
    }

    const { code, files, inputs, mainFile, timeout } = msg;

    // 把全部真实文件写入虚拟文件系统，保证 import 能解析
    try { pyodide.FS.mkdir('/workspace'); } catch (_) {}
    pyodide.FS.chdir('/workspace');
    for (const name of Object.keys(files || {})) {
      try { pyodide.FS.unlink('/workspace/' + name); } catch (_) {}
    }
    for (const name of Object.keys(files || {})) {
      try { pyodide.FS.writeFile('/workspace/' + name, files[name]); } catch (err) {
        post('error', { text: '写入 ' + name + ' 失败：' + String(err) });
      }
    }

    // 逐字节捕获 stdout / stderr：raw 模式给的是 UTF-8 字节码，必须用 TextDecoder
    // 流式解码（它会缓存跨块的半截多字节字符），否则中文/emoji 会被拆成 Latin-1 乱码。
    const td = { stdout: new TextDecoder('utf-8'), stderr: new TextDecoder('utf-8') };
    const pending = { stdout: '', stderr: '' };
    const raw = (stream) => (charCode) => {
      pending[stream] += td[stream].decode(new Uint8Array([charCode & 0xff]), { stream: true });
      let idx;
      while ((idx = pending[stream].indexOf('\n')) >= 0) {
        const line = pending[stream].slice(0, idx);
        pending[stream] = pending[stream].slice(idx + 1);
        post('out', { text: line, stream });
      }
    };
    pyodide.setStdout({ raw: raw('stdout') });
    pyodide.setStderr({ raw: raw('stderr') });

    // 用预喂队列覆盖 input()，让交互程序在无头环境也能跑。
    // 关键：setup 与用户代码必须分两次 runPythonAsync 执行，
    // 否则用户代码的报错行号会被 setup 撑偏移，导致点击跳转跳错行。
    const setup = [
      'import builtins, sys, os',
      'sys.path.insert(0, os.getcwd())',
      '__IQ__ = list(' + JSON.stringify(inputs || []) + ')',
      'def __dt_input__(prompt=""):',
      '    if prompt:',
      '        sys.stdout.write(str(prompt)); sys.stdout.flush()',
      '    if not __IQ__:',
      '        raise EOFError("没有更多输入（输入队列为空）")',
      '    return __IQ__.pop(0)',
      'builtins.input = __dt_input__',
      // v0.8.0: 离线数据科学 —— 拦截 matplotlib 显示，转 base64 回传，避免阻塞 wasm 后端
      'try:',
      '    import matplotlib',
      '    matplotlib.use("agg")',
      '    import matplotlib.pyplot as plt',
      '    __DT_PLOTS__ = []',
      '    def __dt_show__(*a, **k):',
      '        import io, base64',
      '        buf = io.BytesIO()',
      '        plt.savefig(buf, format="png", dpi=110, bbox_inches="tight")',
      '        plt.close("all")',
      '        __DT_PLOTS__.append(base64.b64encode(buf.getvalue()).decode())',
      '    plt.show = __dt_show__',
      'except Exception:',
      '    pass',
      ''
    ].join('\n');

    // worker 内超时：到时写入中断缓冲，Pyodide 会以 KeyboardInterrupt 取消运行。
    // 不终止 worker，解释器保持温热，后续运行无需重新加载。
    const sec = timeout || 8;
    let killed = false;
    const to = setTimeout(() => {
      killed = true;
      if (interruptBuffer) interruptBuffer[0] = 2; // SIGINT
      post('timeout', { sec });
    }, sec * 1000);

    let exitCode = 0;
    try {
      // v0.8.0: 按 import 自动装载本地 wheel（numpy/pandas/matplotlib 等）
      try {
        await pyodide.loadPackagesFromImports(code + '\n' + setup);
      } catch (e) {
        post('status', { msg: '依赖装载跳过：' + String(e).slice(0, 80) });
      }
      await pyodide.runPythonAsync(setup);
      await pyodide.runPythonAsync(code, { filename: mainFile || '<exec>' });
    } catch (err) {
      exitCode = 1;
      const rawMsg = (err && err.message) ? err.message : String(err);
      const tb = cleanTraceback(rawMsg);
      // KeyboardInterrupt（超时取消）单独给一句友好提示，仍附精简 traceback
      if (killed && /KeyboardInterrupt/.test(rawMsg)) {
        post('out', { text: '⏱ 已超时取消（' + sec + 's）：死循环被安全终止，应用未卡死', stream: 'stderr' });
      }
      for (const ln of tb.split('\n')) post('out', { text: ln, stream: 'stderr' });
    } finally {
      clearTimeout(to);
      if (interruptBuffer) interruptBuffer[0] = 0; // 复位，避免影响下一次运行
    }

    if (pending.stdout) post('out', { text: pending.stdout, stream: 'stdout' });
    if (pending.stderr) post('out', { text: pending.stderr, stream: 'stderr' });
    // v0.8.0: 回传 matplotlib 生成的图（若有）
    try {
      const plots = pyodide.globals.get('__DT_PLOTS__');
      if (plots) {
        const arr = plots.toJs();
        plots.destroy();
        for (const b64 of arr) post('plot', { b64 });
      }
    } catch (_) {}
    post('done', { exitCode });
  }
};
