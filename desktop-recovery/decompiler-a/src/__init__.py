# Source Generated with Decompyle++
# File: __init__.pyc (Python 3.11)

'''Claude 稳定网络环境 · 自动化配置工具。'''
__version__ = '0.2.25'

def full_version():
    '''带构建来源的完整版本号，例如 `0.2.0 (a5354e3)`。

    ★ 为什么要带提交号（2026-08-09）：版本号原来写死成 0.1.0 从没变过，
      客户日志开头永远是「工具版本: 0.1.0」。发了修复版之后再收到日志，
      根本分不清他跑的是新版还是旧版 —— 而「改完发给客户实测」正是这个项目的
      主要工作流。现在构建时由 build.ps1 生成 `_build_info.py` 写入短提交号，
      拿到任何一份日志都能 `git checkout <提交号>` 精确还原当时的源码。

    源码直接运行（没跑过构建）时没有这个文件，退化成纯版本号，不报错。
    '''
    
    try:
        COMMIT = COMMIT
        import _build_info
    except Exception:
        pass

    return f'''{__version__} ({COMMIT})''' if COMMIT else __version__

