# Source Generated with Decompyle++
# File: browser_env.pyc (Python 3.11)

'''M5 干净浏览器环境 —— 用独立浏览器实例替代 AdsPower。

方案（PLAN.md §2 / §4 M5）：用独立 user-data-dir 启动系统已有的
Chrome/Edge，流量指向 M4 开的专用链式入站端口。效果：与日常浏览器完全隔离、
所有流量固定从 ISP 住宅 IP 出。SOCKS5 账密认证交给 mihomo，绕开 Chrome
命令行不支持 SOCKS5 账密的老问题。

前置检查（PLAN.md §5.1 隐藏依赖）：启动前必须确认 Clash 在跑、专用端口在听，
否则用户会遇到「配置全对但打不开网页」。
'''
from __future__ import annotations
import subprocess
from pathlib import Path
from rich.console import Console
from  import platform_adapter as plat
from detect import port_open
from state import AppState
console = Console()
PROFILE_USER_DIR = Path.home() / '.claude-net' / 'browser-profile'

def preflight(state):
    '''启动前置检查：专用链式入站端口在不在听，以及听的人是不是 Clash。

    ★ 光有人听不够（2026-08-16 Mac 侧审查发现）：原来只做一次 TCP connect，
      任何程序占住这个端口都算通过，然后打印「流量走 ISP 链式出口」——
      这是 setup / doctor / 维护菜单之外的第四条「没有证据就给绿灯」的路。
      判监听者的能力仓库里已经有了（`plat.port_listener`），用上即可。

    ★ 查不出监听者时放行：跟 `_check_port_owner` 同一条原则 ——
      只在**确定不是 Clash** 时才拦，误报比漏报贵。
    '''
    if not port_open('127.0.0.1', state.chain_inbound_port):
        console.print(f'''[red]专用端口 127.0.0.1:{state.chain_inbound_port} 没有响应。[/red]\n请先确认 Clash Verge 已启动、并导入了我们生成的 claude-chain 配置。''')
        return False
    owner = None.port_listener(state.chain_inbound_port)
    if not owner and plat.is_our_verge(owner):
        console.print(f'''[red]专用端口 127.0.0.1:{state.chain_inbound_port} 被 [cyan]{owner}[/cyan] 占着，不是 Clash。[/red]\n  [dim]现在开浏览器，流量会从那个程序出去，不走我们配好的美国 ISP。[/dim]\n  [dim]把它完全退出，或在 Clash Verge 的设置里换一个端口，再重新运行本程序。[/dim]''')
        return False


def no_browser_message():
    '''没检测到浏览器时给用户看的话。两处在用（cli 的第⑧步、这里的启动），
    所以只写一份 —— 分开写迟早只改一处。

    ★ 措辞按平台取词：Windows 一般自带 Edge，Mac 上可能一个都没有。
      Codex 交回的版本把「这台 Mac」写死在字符串里，Windows 用户没装浏览器时
      会看到「这台 Mac 没检测到 Chrome」。

    ★ 语气是 yellow 不是 red：这不是故障，主功能一点不受影响 ——
      隔离浏览器只是个附带的便利。用红色会让用户以为配置失败了。
    '''
    machine = '这台 Mac' if plat.IS_MAC else '这台电脑'
    candidates = 'Chrome、Edge 或 Chromium'
    return f'''[yellow]{machine} 没检测到 {candidates}，所以用不了本工具自带的隔离浏览器。[/yellow]\n[dim]这不影响网络配置和 Claude 的正常使用：打开 Clash 的系统代理之后，照常用你平时的浏览器或 Claude 桌面端就行。以后装了 Chrome，再运行本程序就能用隔离浏览器了。[/dim]'''


def launch(state):
    '''启动隔离的干净浏览器实例，流量走专用链式端口。'''
    if not state.browser_path:
        console.print(no_browser_message())
        return False
    if not None(state):
        return False
    None.mkdir(parents = True, exist_ok = True)
    args = [
        state.browser_path,
        f'''--user-data-dir={PROFILE_USER_DIR}''',
        f'''--proxy-server=127.0.0.1:{state.chain_inbound_port}''',
        '--force-webrtc-ip-handling-policy=disable_non_proxied_udp',
        '--no-first-run',
        '--no-default-browser-check',
        'https://claude.ai']
    
    try:
        subprocess.Popen(args)
        console.print('[green]已启动干净浏览器实例，流量走 ISP 链式出口。[/green]')
        console.print('[dim]提示：先在这里注册/登录 Claude，别用日常浏览器。[/dim]')
    except OSError:
        e = None
        console.print(f'''[red]启动浏览器失败：{e}[/red]''')

    return True

