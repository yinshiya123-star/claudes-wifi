# Source Generated with Decompyle++
# File: isp_parse.pyc (Python 3.11)

"""ISP 凭据的宽松解析。

为什么要这个模块：用户不会按我们规定的格式贴。实际见过的形态至少有——
  1. proxy-seller 标准行：  203.0.113.7:50101:user:pass
  2. 代理 URL：            socks5://user:pass@203.0.113.7:50101
  3. 反过来的：            user:pass@203.0.113.7:50101
  4. 网站上复制的多行标签： IP: 203.0.113.7 / HTTP port: 50100 / Login: user / Password: pass
  5. 中文标签：            IP地址：… 端口：… 账号：… 密码：…
  6. SOP 里的 YAML 片段：   server: '203.0.113.7'  port: 50101  username: 'user' …
  7. 空格/Tab 分隔：       203.0.113.7 50101 user pass

设计（三层保险，PLAN §4 M3）：
  - 尽力解析：多种格式挨个试
  - **回显确认**：解析完把结果念给用户听，让他核对（任何解析器都会出错，这是安全网）
  - 兜底：认不出来就退回逐项询问

端口归属（哪个是 HTTP、哪个是 SOCKS5）不靠猜，由 wizard 实连探测（见 wizard.probe_port）。
"""
from __future__ import annotations
import re
from dataclasses import dataclass, field
_IPV4 = re.compile('\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b')
_HOSTNAME = re.compile('\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b', re.I)
_LABELS = {
    'user': 'username',
    'username': 'username',
    'login': 'username',
    '账号': 'username',
    '用户名': 'username',
    '帐号': 'username',
    'pass': 'password',
    'password': 'password',
    'passwd': 'password',
    '密码': 'password' }
_SCHEME_RE = re.compile('^(socks5h?|https?)://', re.I)
ParsedIsp = <NODE:12>()

def _valid_port(v):
    
    try:
        n = int(str(v).strip())
    except (TypeError, ValueError):
        pass

    if  <= 1, n or 1, n <= 65535:
        pass
    


def _looks_like_ip(s):
    if not _IPV4.fullmatch(s):
        return False
    return (lambda .0: 