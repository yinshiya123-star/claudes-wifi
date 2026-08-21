# Source Generated with Decompyle++
# File: detect.pyc (Python 3.11)

'''M1 环境检测 — 判断本机是否已装 Clash Verge / 可用浏览器。

三层探测（见 PLAN.md §4 M1）：
  1. 注册表卸载项
  2. 默认/常见安装路径
  3. 运行中的进程 / 端口

检测到就跳过安装，检测不到进入 M2。
'''
from __future__ import annotations
import shutil
import socket
import re
from pathlib import Path
from typing import Any, Iterator, Optional
from  import platform_adapter as plat

try:
    import winreg
except ImportError:
    winreg = None
except:
    pass

CLASH_VERGE_NAMES = [
    'Clash Verge',
    'Clash Verge Rev',
    'Clash-Verge']

def _registry_uninstall_entries():
    '''枚举卸载项中定位安装目录所需的字段。'''
    if None or winreg:
        return None
    roots = [
        (winreg.HKEY_LOCAL_MACHINE, 'SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall'),
        (winreg.HKEY_LOCAL_MACHINE, 'SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall'),
        (winreg.HKEY_CURRENT_USER, 'SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall')]
    for hive, subkey in roots:
        
        try:
            pass
        except OSError:
            pass

        
        try:
            key = None
            for i in range(winreg.QueryInfoKey(key)[0]):
                
                try:
                    sub = winreg.EnumKey(key, i)
                except OSError:
                    pass

                
                try:
                    app = None
                    entry = { }
                    for field in ('DisplayName', 'InstallLocation', 'DisplayIcon', 'UninstallString'):
                        
                        try:
                            (value, _) = winreg.QueryValueEx(app, field)
                            entry[field] = str(value)
                        except OSError:
                            pass

                with None:
                    if not :
                        pass

                if entry:
                    yield entry
                None(None, None)
        with None:
            if not :
                pass

        
        try:
            app = None
            entry = { }
            for field in ('DisplayName', 'InstallLocation', 'DisplayIcon', 'UninstallString'):
                
                try:
                    (value, _) = winreg.QueryValueEx(app, field)
                    entry[field] = str(value)
                except OSError:
                    pass

        with None:
            if not :
                pass

        except:
            pass
        None(None, None)
    
    try:
        key = None
        for i in range(winreg.QueryInfoKey(key)[0]):
            
            try:
                sub = winreg.EnumKey(key, i)
            except OSError:
                pass

            
            try:
                app = None
                entry = { }
                for field in ('DisplayName', 'InstallLocation', 'DisplayIcon', 'UninstallString'):
                    
                    try:
                        (value, _) = winreg.QueryValueEx(app, field)
                        entry[field] = str(value)
                    except OSError:
                        pass

            with None:
                if not :
                    pass

            if entry:
                yield entry
            None(None, None)
    with None:
        if not :
            pass

    except:
        pass
    continue


def _clean_registry_path(value):
    '''去掉注册表路径外围引号、图标索引或命令行参数。'''
    value = value.strip()
    quoted = re.match('^"([^"]+)"', value)
    if quoted:
        return Path(quoted.group(1))
    return None(value.split(',', 1)[0].strip())


def _clash_path_from_entry(entry):
    '''从一个 Clash Verge 卸载项解析真实 exe 路径。'''
    name = entry.get('DisplayName', '').casefold()
    if not (lambda .0: 