#!/usr/bin/env python3.11
"""
DevTerminal 设计稿本地静态服务器（纯 ThreadingHTTPServer，无 COOP/COEP）。

用途：给 Playwright 测试与本地预览提供 /workspace/dev-terminal/design 目录的静态服务。
Pyodide 以普通 module worker 方式从同目录加载，无需跨源隔离（SharedArrayBuffer 不可用时
运行器用主线程看门狗兜底终止死循环）。
"""
import sys, os, http.server, socketserver, functools, socket

ROOT = os.path.join(os.path.dirname(__file__), "..", "design")

class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".mjs": "text/javascript",
        ".js": "text/javascript",
        ".wasm": "application/wasm",
        ".json": "application/json",
        ".zip": "application/zip",
    }
    def log_message(self, *a):  # 静默，避免刷屏
        pass

def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8190
    os.chdir(os.path.abspath(ROOT))
    socketserver.ThreadingHTTPServer.allow_reuse_address = True
    httpd = socketserver.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"Serving {os.getcwd()} on http://127.0.0.1:{port}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    main()
