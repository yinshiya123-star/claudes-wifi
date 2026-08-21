# Source Generated with Decompyle++
# File: verify.pyc (Python 3.11)

'''M6 出口自检 —— 自动跑 SOP 第 5.3/6.3 节的三项检查。

三项：
  1. 出口 IP == ISP IP，且类型为 ISP/住宅（ipinfo.io/json）
  2. IP 质量：主源 net.coffee（判机房/住宅、VPN/代理/滥用标记），
     查不成回落 scamalytics（只有欺诈分，判不了机房还是住宅）
  3. DNS 泄露（归属地应在美国，不能出现中国电信/联通/移动）

分流陷阱与我们的解法（PLAN.md §4 M6）：
分流规则只把 anthropic/claude 指向链式出口，ipinfo 等检测站不在规则里，
若走系统代理直接测，会测到普通梯子出口而非 ISP。
本工具不需要调 mihomo API 切组——检测流量直接走 M4 开的**专用链式入站端口**，
该入口用 listener.proxy 绑死了链式出口，天然经过 ISP，和干净浏览器走同一条链。
（这也是选「独立入站端口」方案顺带拿到的红利。）

M7 doctor 是本模块的轻量独立版（日常快速复检）。
'''
from __future__ import annotations
import random
import re
import time
from dataclasses import dataclass
from typing import Callable
import requests
from rich.console import Console
from  import platform_adapter as plat, runlog
from state import AppState
console = Console()
CN_ISP_KEYWORDS = [
    'china',
    'chinanet',
    'unicom',
    'mobile',
    'telecom',
    'cnc',
    'cernet']
IP_CHECK_SITE = 'https://ip.net.coffee'
_BROWSER_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
CheckResult = <NODE:12>()
_RETRY_DELAYS = (0, 8)

def _with_health_retry(check, label = ''):
    '''仅重试链路尚未就绪/远端暂时失败，不重复业务性不通过结果。

    ★ 等待期间必须出声（用户 2026-08-09 反馈「一直卡在出口自检」）：
      链路断掉时三项检查会各自走满重试，加起来最坏要 7 分多钟，而原来这段时间
      屏幕上一个字都不出，用户只看到「⑥ 出口自检」不动，以为程序死了。
      偏偏这个最坏情况恰好就是「配置有问题」的情况 —— 最需要反馈的时候最没反馈。
    '''
    last = None
    total = len(_RETRY_DELAYS)
    for i, delay in enumerate(_RETRY_DELAYS, start = 1):
        if delay:
            if label:
                console.print(f'''  [dim]{label} 还没通，等 {delay} 秒再试（第 {i}/{total} 次）…[/dim]''')
            time.sleep(delay)
        last = check()
        if not last.passed or last.retryable:
            
            return None, last
        if last:
            raise last
        return last


def _via_chain_port(state):
    '''构造走「专用链式入站端口」的 proxies 字典。

    直接走专用端口，天然经过链式出口，省去切组——这是用独立入站端口方案的红利：
    检测流量和浏览器流量走同一条链，不受分流规则影响，绕开了分流陷阱。
    '''
    p = f'''http://127.0.0.1:{state.chain_inbound_port}'''
    return {
        'http': p,
        'https': p }


def _check_exit_ip_once(state):
    '''出口 IP 是否等于 ISP IP、类型是否为 ISP。'''
    
    try:
        resp = requests.get('https://ipinfo.io/json', proxies = _via_chain_port(state), timeout = 10)
        resp.raise_for_status()
        data = resp.json()
        exit_ip = data.get('ip', '')
        org = data.get('org', '')
        ok = exit_ip == state.isp.ip
        detail = f'''出口 IP={exit_ip} org={org}（期望 {state.isp.ip}）'''
    except Exception:
        e = None



def check_exit_ip(state):
    '''出口检查，链路健康检查尚未完成时自动等待重试。'''
    return None((lambda : _check_exit_ip_once(state)), '出口 IP')

_LOOKUP_MARKERS = ('company_type', 'is_datacenter', 'isResidential')

def _looks_like_lookup(d):
    '''这份 JSON 是不是一条真的 lookup 结果（而不是错误响应）。'''
    return (lambda .0: 