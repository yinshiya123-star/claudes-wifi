# Source Generated with Decompyle++
# File: platform_adapter.pyc (Python 3.11)

'''平台适配层 —— 把 Windows / macOS 的差异全部集中到这一个文件。

设计原则：
  - 其它模块（detect/install/verge_profile/browser_env/cli）只调用这里的函数，
    不直接碰任何平台专属 API。
  - Windows 分支 = 严格照搬已在真机验证过的行为，别改坏它。
  - macOS 分支 = 已在 Apple Silicon 真机逐项验证；仍不确定的地方继续标
    ⚠️ MAC-TODO，见 MAC-TODO.md。没有证据的标记不许删。
  - 路径不写死、按特征探测（对冲 Mac 路径名不确定性），跟「不依赖机场组名」同思路。
'''
from __future__ import annotations
import re
import subprocess
import sys
from pathlib import Path
from typing import Iterable, Optional
IS_WINDOWS = sys.platform == 'win32'
IS_MAC = sys.platform == 'darwin'
_NO_WINDOW = getattr(subprocess, 'CREATE_NO_WINDOW', 0)

def verge_config_dir():
    '''Clash Verge 存 profiles.yaml / clash-verge.yaml 的目录。'''
    if IS_WINDOWS:
        return Path.home() / 'AppData/Roaming/io.github.clash-verge-rev.clash-verge-rev'
    base = None.home() / 'Library/Application Support'
    exact = base / 'io.github.clash-verge-rev.clash-verge-rev'
    if (exact / 'profiles.yaml').exists():
        return exact
    if None.exists():
        for d in base.iterdir():
            name = d.name.lower()
            if 'clash' in name and 'verge' in name and (d / 'profiles.yaml').exists():
                
                return None, d
            return exact


def clash_candidates():
    '''Clash Verge 可执行文件的候选路径（按优先级）。'''
    if IS_WINDOWS:
        return [
            Path.home() / 'AppData/Local/Programs/clash-verge/Clash Verge.exe',
            Path('C:/Program Files/Clash Verge/Clash Verge.exe'),
            Path('C:/Program Files/Clash Verge/clash-verge.exe')]
    return [
        None('/Applications/Clash Verge.app/Contents/MacOS/Clash Verge'),
        Path('/Applications/Clash Verge.app/Contents/MacOS/clash-verge'),
        Path.home() / 'Applications/Clash Verge.app/Contents/MacOS/Clash Verge',
        Path.home() / 'Applications/Clash Verge.app/Contents/MacOS/clash-verge']


def clash_app_bundle():
    '''macOS 专用：返回 Clash Verge.app 的路径（用 open -a 启动时用）。'''
    if not IS_MAC:
        return None
    for p in (None('/Applications/Clash Verge.app'), Path.home() / 'Applications/Clash Verge.app'):
        if p.exists():
            
            return None, p
        return None


def browser_candidates():
    '''干净浏览器实例用的 Chromium 系浏览器（优先 Chrome，其次 Edge）。'''
    if IS_WINDOWS:
        return [
            Path('C:/Program Files/Google/Chrome/Application/chrome.exe'),
            Path('C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'),
            Path.home() / 'AppData/Local/Google/Chrome/Application/chrome.exe',
            Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'),
            Path('C:/Program Files/Microsoft/Edge/Application/msedge.exe')]
    return [
        None('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'),
        Path('/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'),
        Path('/Applications/Chromium.app/Contents/MacOS/Chromium')]


def browser_path_names():
    '''shutil.which 兜底用的命令名。'''
    return ('chrome', 'msedge') if IS_WINDOWS else ('google-chrome', 'chromium')


def open_installer(path):
    '''用系统默认方式打开安装包（Windows: 双击；macOS: `open <dmg>`）。

    ★ 为什么不能复用 launch_detached（2026-08-16 Mac 侧审查发现）：
      那个函数是用来**启动应用**的，Mac 分支要么 `open -a <目标>`
      （把参数当应用名），要么直接 exec。拿 dmg 走这两条都不对 ——
      前者报「找不到这个应用」，后者是把磁盘映像当可执行文件跑。
      dmg 要的是 `open <文件>`，让 Finder 挂载它。

      这条路径只在「自动安装失败、请用户自己拖进 Applications」时走到，
      也就是**用户已经出问题的时候**，结果这里再失败一次。
    '''
    if IS_WINDOWS:
        launch_detached(path)
        return None
    None.Popen([
        'open',
        path])


def launch_detached(exe):
    '''像「双击图标」那样启动程序，而不是当调用者的子进程。

    ★ 为什么重要（Windows 实测，PLAN §…）：作为子进程启动的 Clash Verge，
      单实例 IPC 会失灵，clash:// 深链导入收不到。Mac 上同理，用 open 启动。
    '''
    if IS_WINDOWS:
        
        try:
            import os
            os.startfile(exe)

        return None
    flags = getattr(subprocess, 'DETACHED_PROCESS', 0) | getattr(subprocess, 'CREATE_NEW_PROCESS_GROUP', 0)
    subprocess.Popen([
        exe], cwd = str(Path(exe).parent), creationflags = flags, stdin = subprocess.DEVNULL, stdout = subprocess.DEVNULL, stderr = subprocess.DEVNULL)
    return None
    bundle = clash_app_bundle()
    target = str(bundle) if bundle and str(exe).endswith(('Clash Verge', 'clash-verge')) else exe
    if target.endswith('.app'):
        subprocess.Popen([
            'open',
            target])
        return None
    subprocess.Popen([
        'open',
        '-a',
        target]) if None if (AttributeError, OSError) else None else subprocess.Popen([
        exe])


def launch_browser(exe, args):
    '''启动浏览器（带命令行参数）。Mac 和 Win 都直接 exec 可执行文件即可。'''
    None(subprocess.Popen)


def open_deeplink(url):
    '''让系统用已注册的处理器打开 clash:// 深链（转发给运行中的 Verge）。'''
    if IS_WINDOWS:
        subprocess.run([
            'cmd',
            '/c',
            'start',
            '',
            url], check = False, creationflags = _NO_WINDOW)
        return None
    None.run([
        'open',
        url], check = False)


def clash_scheme_owner():
