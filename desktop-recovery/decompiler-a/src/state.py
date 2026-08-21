# Source Generated with Decompyle++
# File: state.pyc (Python 3.11)

'''断点续跑状态管理。

整个工具是一个状态机：每完成一步就落盘，中途退出（装完软件要重启、
用户跑去买 ISP）后重跑能自动跳过已完成环节。见 PLAN.md §3。

state.json 存放位置：用户主目录下 ~/.claude-net/state.json
（不放项目目录，避免打包成 exe 后目录只读的问题）。

安全模型（PLAN.md §8.1 红线一，工具确定要对外分发）：
- state.json 是**每个用户在自己机器上运行时生成的**，存自己的凭据。谁用谁的，
  不同用户互不影响。
- 分发包里**绝不能**带任何真实 state.json / 凭据；仓库 .gitignore 已兜底，
  Codex 打包时也要确认排除。
- 凭据对当前用户仍是明文存本机（和一般本地软件一样）。可选增强：
  TODO(Codex): 对 ISP 账密做本机加密（如 DPAPI / keyring），防同机他人窥看。
  非红线，做不做不影响主流程。
'''
from __future__ import annotations
import json
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Optional
STATE_DIR = Path.home() / '.claude-net'
STATE_FILE = STATE_DIR / 'state.json'
STEPS = [
    'detect',
    'install',
    'collect_proxy',
    'collect_isp',
    'clash_config',
    'browser_env',
    'verify',
    'tips']
IspCredential = <NODE:12>()
AppState = <NODE:12>()

def load():
