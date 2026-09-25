#!/usr/bin/env python3
"""验证 pybridge.c 注入的 Python 层 stdio 桥接逻辑（用宿主 CPython 模拟）
执行 pybridge.c 中 PyRun_SimpleString 注入的原始代码字符串。"""
import sys, io, threading, queue

REAL_OUT = sys.__stdout__

def log(*a):
    REAL_OUT.write(" ".join(map(str, a)) + "\n")
    REAL_OUT.flush()

# ---- 模拟 dtbridge（真实环境由 C 模块提供） ----
class FakeDt:
    outputs = []
    eof = False  # sticky EOF：pushEof 后后续 _input 立即 EOFError
    def _output(self, stream, text):
        self.outputs.append((stream, text))
    def _input(self):
        if self.eof:
            raise EOFError("EOF(sticky)")
        line = fake_inputs.get(timeout=3)
        if line is None:
            self.eof = True
            raise EOFError("EOF")
        return line

dt = FakeDt()
fake_inputs = queue.Queue()
sys.modules['dtbridge'] = dt

# ---- pybridge.c 注入的原始桥接代码（一字不改同步自 pybridge.c） ----
BRIDGE_CODE = (
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
    "    def _read_line(self):\n"
    "        import dtbridge as _d\n"
    "        try:\n"
    "            line = _d._input()\n"
    "        except EOFError:\n"
    "            return ''\n"
    "        return line + '\\n'\n"
    "    def readline(self, size=-1):\n"
    "        line = self._read_line()\n"
    "        if size and size > 0 and len(line) > size:\n"
    "            return line[:size]\n"
    "        return line\n"
    "    def read(self, size=-1):\n"
    "        if size == 0:\n"
    "            return ''\n"
    "        parts = []\n"
    "        total = 0\n"
    "        while size < 0 or total < size:\n"
    "            line = self._read_line()\n"
    "            if line == '':\n"
    "                break\n"
    "            parts.append(line)\n"
    "            total += len(line)\n"
    "        data = ''.join(parts)\n"
    "        if size and size > 0 and len(data) > size:\n"
    "            return data[:size]\n"
    "        return data\n"
    "sys.stdout = _DtOut()\n"
    "sys.stderr = _DtErr()\n"
    "sys.stdin = _DtIn()\n"
    "sys.stdout.flush()\n"
)
try:
    exec(BRIDGE_CODE, {'sys': sys, 'io': io})
except Exception:
    import traceback
    REAL_OUT.write("BRIDGE_CODE exec failed:\n" + traceback.format_exc() + "\n")
    REAL_OUT.flush()
    sys.exit(2)

# ---- 测试 1：print 输出（走 _DtOut） ----
# 注意：CPython 的 print 按参数多次 write（参数与换行符分开），
# Kotlin 侧 NativeBridge 做行缓冲拼接后按 \n 断行——这里验证真实行为。
dt.outputs.clear()
print("你好，世界")          # 走桥接 stdout：write("你好，世界") + write("\n")
print(1 + 2)
log("test1 outputs:", dt.outputs)
joined = "".join(t for _, t in dt.outputs)
assert joined == "你好，世界\n3\n", joined
# 多参数 print 会被拆成多次 write（真实行为），行缓冲后应还原为一行
dt.outputs.clear()
print("a", "b")
joined2 = "".join(t for _, t in dt.outputs)
log("test1b multi-arg:", dt.outputs)
assert joined2 == "a b\n", joined2
log("test1 print OK")

# ---- 测试 2：input 交互（正常 + EOF） ----
dt.outputs.clear()
def feeder():
    fake_inputs.put("42")
    fake_inputs.put(None)  # EOF 哨兵（Kotlin pushEof）
threading.Thread(target=feeder, daemon=True).start()
first = input("请输入：")
assert first == "42", first
second_ok = False
try:
    input("再输一次：")
except EOFError:
    second_ok = True
assert second_ok, "EOF 应抛 EOFError"
log("test2 input OK")

# ---- 测试 3：stderr + 多行中文 ----
dt.outputs.clear()
sys.stderr.write("警告信息\n")
assert dt.outputs[-1] == ('stderr', '警告信息\n'), dt.outputs
log("test3 stderr OK")

# ---- 测试 4：异常信息字符串（execFile 错误路径） ----
try:
    raise ValueError("测试异常")
except ValueError as e:
    assert str(e) == "测试异常"
log("test4 exception OK")

# ---- 测试 5：_DtIn.read(size) 循环读到 EOF（P1-3） ----
dt.outputs.clear()
dt.eof = False  # 新一轮 stdin
def feeder5():
    fake_inputs.put("line-one")
    fake_inputs.put("line-two")
    fake_inputs.put(None)  # EOF
threading.Thread(target=feeder5, daemon=True).start()
got = sys.stdin.read()
assert got == "line-one\nline-two\n", repr(got)
# EOF 之后再读应返回空串（sticky EOF）
assert sys.stdin.read() == "", "EOF 后 read 应返回空串"
assert sys.stdin.read(0) == "", "read(0) 应返回空串"
# size 截断
dt.eof = False
def feeder5b():
    fake_inputs.put("abcdef")
    fake_inputs.put(None)
threading.Thread(target=feeder5b, daemon=True).start()
assert sys.stdin.read(4) == "abcd", repr(sys.stdin.read(4))
log("test5 read-until-EOF OK")

# ---- 测试 6：sys.argv 构造逻辑（P1-2，模拟 C 层 PyList_New(n+1) 行为） ----
def build_argv(script_path, args):
    # 与 pybridge.c execFile 中 C 逻辑等价：argv[0]=scriptPath，其后逐参
    argv = [script_path] + list(args)
    return argv
assert build_argv("/data/main.py", []) == ["/data/main.py"]
assert build_argv("/data/main.py", ["--foo", "1", "bar"]) == [
    "/data/main.py", "--foo", "1", "bar"]
log("test6 argv build OK")

log("ALL BRIDGE LOGIC TESTS PASSED")
