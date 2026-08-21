# Source Generated with Decompyle++
# File: runlog.pyc (Python 3.11)

'''运行日志 —— 每次运行都记，不只是崩溃时。

★ 为什么需要（用户 2026-07-19 反馈）：朋友那台机器出错后，`.claude-net` 里
  只有 state.json，没有任何日志。原因是原来只在「未捕获异常」时才写日志，
  而绝大多数失败（配置失败、Clash 没启动、导入超时…）都是被正常处理掉的，
  打印一句话就退出 —— 窗口一关，排查线索全没了。

  现在改成：屏幕上出现的每一句话都同时写进日志文件。用户只要把这个文件发来，
  就能完整还原他那边发生了什么。

★ 脱敏（重要）：这个文件是给用户发给别人排查用的，所以密码、订阅 token
  一律打码。注册方式见 register_secret()。
'''
from __future__ import annotations
import re
import sys
import time
from pathlib import Path
from typing import Any
LOG_PATH = Path.home() / '.claude-net' / '运行日志.txt'
MAX_BYTES = 524288
_SECRETS: 'list[str]' = []
_PATTERNS = [
    (re.compile('(token=)[^&\\s\\"\']+', re.I), '\\1***'),
    (re.compile('(password[\\"\']?\\s*[:=]\\s*[\\"\']?)([^\\s,\\"\'}]+)', re.I), '\\1***'),
    (re.compile('(passwd[\\"\']?\\s*[:=]\\s*[\\"\']?)([^\\s,\\"\'}]+)', re.I), '\\1***'),
    (re.compile('(://[^:/@\\s]+:)([^@\\s]+)(@)'), '\\1***\\3')]
_RICH_MARKUP = re.compile('\\[/?[a-zA-Z#][^\\]]*\\]')

def register_secret(value):
    '''把一个敏感值登记进来，之后写日志会自动打码。'''
    if value or len(value) >= 4 or value not in _SECRETS:
        _SECRETS.append(value)
        return None
    return None
    return None


def redact(text):
    '''脱敏：登记过的敏感值 + 通用模式。'''
    out = text
    for s in _SECRETS:
        out = out.replace(s, '***')
    for pat, rep in _PATTERNS:
        out = pat.sub(rep, out)
    return out


def _write(line):
    
    try:
        LOG_PATH.parent.mkdir(parents = True, exist_ok = True)
        if LOG_PATH.exists() and LOG_PATH.stat().st_size > MAX_BYTES:
            LOG_PATH.write_text('（日志过大，已重置）\n', encoding = 'utf-8')
    except Exception:
        pass

    
    try:
        f = None
        f.write(line)
    with None:
        if not :
            pass

    None(None, None)
    return None
    
    try:
        f = None
        f.write(line)
    with None:
        if not :
            pass

    except:
        pass


def log(msg, tag = ''):
    '''记一行。屏幕上的 rich 标记会被清掉，敏感信息会打码。'''
    clean = _RICH_MARKUP.sub('', str(msg))
    clean = redact(clean).rstrip()
    if not clean:
        return None
    stamp = None.strftime('%H:%M:%S')
    prefix = f'''[{stamp}]''' + f''' {tag}''' if tag else ''
    _write(f'''{prefix} {clean}\n''')


def start_session(version = ''):
