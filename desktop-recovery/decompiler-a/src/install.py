# Source Generated with Decompyle++
# File: install.pyc (Python 3.11)

'''M2 半自动安装 —— 征得同意后安装 Clash Verge。

自举悖论（PLAN.md §4 M2）：用户此刻还没代理，可能下不动 GitHub 上的
Clash Verge 安装包。解法：随包分发（assets/），本地安装；找不到就引导手动下载。

跨平台：所有平台专属操作（安装包格式、静默安装、进程管理、启动方式）都走
platform_adapter，本模块只保留业务流程。
'''
from __future__ import annotations
import time
from pathlib import Path
from typing import Optional
from  import wizard
from  import platform_adapter as plat
from rich.console import Console
from detect import find_clash, port_open, read_verge_ports
console = Console()
ASSETS_DIR = Path(__file__).resolve().parent.parent / 'assets'
CLASH_DOWNLOAD_URL = 'https://github.com/clash-verge-rev/clash-verge-rev/releases'
VERGE_PROCESS_NAMES = plat.verge_all_process_names()
VERGE_GUI_PROCESSES = plat.verge_gui_process_names()

def find_local_installer():
    '''在 assets/ 里找随包分发的 Clash 安装包（Win 的 .exe / Mac 的 .dmg）。'''
    if not ASSETS_DIR.exists():
        return None
    hits = None(ASSETS_DIR.glob(plat.installer_glob()))
    return hits[0] if hits else None


def install_clash():
    '''安装 Clash Verge。返回是否安装成功（或用户已手动完成）。'''
    agree = wizard.ask_yes_no('未检测到 Clash Verge。是否现在安装？')
    if not agree:
        console.print('[yellow]已跳过安装。没有 Clash 无法继续配置。[/yellow]')
        return False
    installer = None()
    if installer:
        kind = 'dmg' if plat.IS_MAC else 'exe'
    console.print(f'''下载后把 .{kind} 放到：{ASSETS_DIR}''')
    wizard.pause('放好后按回车继续…')
    installer = find_local_installer()
    if installer:
        return False
    installer.print(f'''正在安装：{installer.name}''')
    if plat.install_package(installer):
        for _ in range(15):
            if find_clash():
                console.print('[green]安装完成。[/green]')
                installer
                return True
            installer.sleep(1)
    console.print('[yellow]自动安装未完成，改为打开安装包，请手动完成安装。[/yellow]')
    if plat.IS_MAC:
        console.print('[dim]把 Clash Verge 拖进「应用程序」文件夹即可。[/dim]')
    else:
        console.print('[dim]照着安装向导点「下一步」完成即可。[/dim]')
    
    try:
        plat.open_installer(str(installer))
        wizard.pause('装好后按回车继续…')
        if find_clash():
            return True
    except OSError:
        e = None
        console.print(f'''[red]无法打开安装包：{e}[/red]''')

    None.print('[red]仍未检测到 Clash Verge，请确认安装是否完成。[/red]')
    return False


def is_clash_running():
    '''Verge 是否在跑：先看进程，再看内核端口（端口通说明内核真的起来了）。'''
    procs = plat.running_process_names()
    if procs and (lambda .0: 