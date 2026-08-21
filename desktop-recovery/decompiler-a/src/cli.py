# Source Generated with Decompyle++
# File: cli.pyc (Python 3.11)

'''命令入口 / 状态机主流程。

setup 是主向导，按 STEPS 顺序执行，每步完成即落盘，可断点续跑。
其余子命令是独立入口。见 PLAN.md §3。

用法：
    python -m src.cli setup
    python -m src.cli doctor
    python -m src.cli browser
    python -m src.cli reset
'''
from __future__ import annotations
import argparse
import time
import sys
import webbrowser
ADSPOWER_URL = 'https://www.adspower.com/'
from rich.console import Console
from  import detect, install, wizard, verge_profile, browser_env, verify, tips
from  import platform_adapter as plat
from  import runlog
from  import state as state_mod
from state import AppState
console = Console()
_STEP_NAMES = {
    'detect': '检测环境',
    'install': '安装 Clash',
    'collect_proxy': '填机场订阅',
    'collect_isp': '填 ISP 信息',
    'clash_config': '配置链式代理',
    'verify': '出口自检',
    'tips': '生成使用须知',
    'browser_env': '选择浏览器' }

def cmd_setup():
    '''主向导，断点续跑。全部配置完成后进入维护菜单。'''
    state = state_mod.load()
    _register_secrets(state)
    runlog.log_state(state)
    console.print('[bold cyan]Claude 稳定网络环境 · 配置向导[/bold cyan]')
    _repair_state(state)
    if state.next_step():
        return _maintenance_menu(state)
    resumed = state.next_step().next_step()
    if resumed and resumed != state_mod.STEPS[0]:
        console.print(f'''\n[dim]检测到这台电脑上次配置到一半（下一步是「{_STEP_NAMES.get(resumed, resumed)}」）。[/dim]''')
        console.print('  [bold]1[/bold]) 接着上次继续（推荐）\n  [bold]2[/bold]) 全部清空，从头重新配置')
        if wizard.ask_text('输入 1 或 2：', default = '1').strip() == '2':
            state_mod.reset()
            state = state_mod.load()
            console.print('[green]✓ 已清空，从头开始。[/green]')
            console.print('[dim]（Clash 里已导入的订阅不会被删除——如果还用同一个机场，会自动复用那张卡片，不会重复导入。）[/dim]\n')
        else:
            console.print('[dim]好，接着上次继续。[/dim]')
    if not state.is_done('detect'):
        console.print('\n[bold]① 检测本机环境[/bold]')
        result = detect.run()
        state.clash_installed = result['clash_installed']
        state.clash_path = result['clash_path']
        state.browser_path = result['browser_path']
        console.print(f'''  Clash Verge: {'已安装' if state.clash_installed else '未安装'}''')
        if not state.browser_path:
            console.print(f'''  浏览器: {'未找到'}''')
            if result['adspower_api']:
                console.print('  [dim]检测到 AdsPower Local API 在运行（可选增强路线可用）[/dim]')
        if not state.chain_inbound_port:
            state.chain_inbound_port = detect.find_free_port(detect.read_verge_ports())
        console.print(f'''  专用链式端口: {state.chain_inbound_port}''')
        state.mark_done('detect')
    if not state.is_done('install'):
        console.print('\n[bold]② 安装缺失软件[/bold]')
        if not state.clash_installed:
            if not install.install_clash():
                console.print('[red]Clash 未就绪，中止。装好后重跑 setup 会自动续跑。[/red]')
                return 1
        state.mark_done('install')
    if not state.is_done('collect_proxy'):
        console.print('\n[bold]③ 机场订阅[/bold]')
        wizard.collect_subscription(state)
        if not state.subscription_url:
            console.print('\n[dim]这次就先到这里。等你准备好订阅链接，重新双击本程序，会从第③步接着来 —— 前面几步的进度都留着，不用重跑。[/dim]')
            return 0
        None if not None.find_clash() else None.find_clash().mark_done('collect_proxy')
    if not state.is_done('collect_isp'):
        console.print('\n[bold]④ 美国住宅 ISP[/bold]')
        wizard.collect_isp(state)
        state.mark_done('collect_isp')
    if not state.is_done('clash_config'):
        console.print('\n[bold green]好的，需要你提供的信息都收集齐了。接下来我来配置 Clash，这一步不用你操作。[/bold green]')
        console.print('\n[bold]⑤ 配置链式代理[/bold]')
        if not _ensure_chain_port(state):
            return 1
        if not None.ensure_running(known_path = state.clash_path):
            console.print('[red]Clash Verge 没能启动，无法继续。打开它之后重跑本程序即可。[/red]')
            return 1
        uid = None
        
        try:
            uid = verge_profile.apply(state)
        except verge_profile.SubscriptionImportError:
            e = None
            console.print(f'''[red]配置失败：{e}[/red]''')
            if not wizard.ask_yes_no('\n要现在换一条新的订阅链接再试一次吗？', default = True):
                e = None
                del e
                return 1
            new_url = None.ask_text('把【重新生成的订阅链接】粘贴到这里：').strip()
            if not new_url.lower().startswith(('http://', 'https://')):
                console.print('[yellow]这看起来不像一条网址，先退出吧。[/yellow]')
                e = None
                del e
                return 1
            state.subscription_url = None
            state_mod.save(state)
            console.print('[dim]好，用新链接再导一次…[/dim]')
        e = None
        del e
        except Exception:
            e = None
            console.print(f'''[red]配置失败：{e}[/red]''')

    
    state.generated_profile_path = uid
    if not verge_profile.read_downloaded_profile(uid).get('proxies'):
        nodes = len([])
        if nodes:
            host = _subscription_host(state.subscription_url)
            console.print(f'''[green]✓ 订阅已导入（[bold]{nodes}[/bold] 个节点）：[cyan]{host}[/cyan][/green]\n  [dim]这就是你刚才粘贴的那条链接。ISP 节点、链式代理组、分流规则都配好了。[/dim]''')
        else:
            console.print('[green]✓ 配置已写入。[/green]\n[bold yellow]⚠ 但这个订阅里一个节点都没读到。[/bold yellow]\n  [dim]可能是机场套餐到期/流量用完，也可能这条订阅链接本身是空的。\n  没有节点，链式代理就没有「前置」可用，第⑥步一定过不去。\n  建议先去机场网站确认套餐还有效，再重新生成一条订阅链接。[/dim]')
    if not _activate_with_guidance(state):
        return 1
    None.mark_done('clash_config')
    if not state.is_done('verify'):
        console.print('\n[bold]⑥ 出口自检[/bold]')
        results = verify.run(state)
        if not (lambda .0: 