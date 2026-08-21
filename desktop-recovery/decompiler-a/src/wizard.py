# Source Generated with Decompyle++
# File: wizard.pyc (Python 3.11)

'''M3 信息收集向导 —— 问答 + 即时校验。

原则（PLAN §4 M3）：每收集一项立刻验证，失败就地重试。
别让用户走完十步才在最后发现订阅链接贴错、ISP 凭据填错。

面向的是不懂网络的小白，所以：
  - 没有机场/ISP 的，直接把购买页打开给他，别让他自己找
  - 凭据不规定格式，宽松解析（isp_parse）+ 回显确认 + 兜底逐项问
  - HTTP / SOCKS5 端口归属靠实连探测，不靠猜
'''
from __future__ import annotations
import webbrowser
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Optional
from urllib.parse import quote
import requests
from rich.console import Console
from  import branding, isp_parse
from  import platform_adapter as plat
from state import AppState, IspCredential, save
console = Console()

def ask_text(prompt, default = ''):
    '''单行输入。支持右键/Ctrl+V 粘贴；用户 Ctrl+C 时返回默认值而不是崩。'''
    
    try:
        console.print(f'''[bold cyan]?[/bold cyan] {prompt}''', end = '')
        if not input().strip():
            return default

    if (EOFError, KeyboardInterrupt):
        console.print('\n[dim]（已取消输入）[/dim]')
        return 


def ask_multiline(prompt):
    '''多行粘贴。用户从网站整段复制的凭据往往是多行的。

    结束方式：直接回车（空行）即结束；粘贴多行时连按两次回车。
    '''
    console.print(f'''[bold cyan]?[/bold cyan] {prompt}''')
    console.print('[dim]  （粘贴后按回车；多行内容粘完再多按一次回车结束）[/dim]')
    lines = []
    
    try:
        line = input()
        if not line.strip():
            pass
        elif line.strip().upper() == 'END':
            pass
        else:
            lines.append(line)
    except (EOFError, KeyboardInterrupt):
        console.print('\n[dim]（已取消输入）[/dim]')
    except:
        pass

    return '\n'.join(lines).strip()


def ask_yes_no(prompt, default = True):
    '''是/否。永不返回 None。'''
    hint = '[Y/n]' if default else '[y/N]'
    ans = ask_text(f'''{prompt} {hint} ''').lower()
    if not ans:
        return default
    if None in ('y', 'yes', '是', '对', '1'):
        return True
    if None in ('n', 'no', '否', '不', '0'):
        return False
    None.print('[dim]  请输入 y 或 n。[/dim]')
    continue


def pause(prompt = '按回车继续…'):
    '''等用户按回车。Ctrl+C 不崩。'''
    
    try:
        console.print(f'''[dim]{prompt}[/dim]''', end = '')
        input()
    except (EOFError, KeyboardInterrupt):
        console.print()


ISP_PURCHASE_URL = 'https://proxy-seller.com/zh/?action=login'
PURCHASE_DOC = 'ProxySeller网站ISP购买问题.txt'
CLI_DOC = '在终端里用ClaudeCode_Mac.txt' if plat.IS_MAC else '在终端里用ClaudeCode.txt'

def shipped_doc(filename):
    '''随包文档的完整路径：打包后跟 exe 同目录，跑源码时在项目根目录。

    找不到就退回只显示文件名 —— 指错路比不指路更糟。
    '''
    import sys
    base = Path(sys.executable).parent if getattr(sys, 'frozen', False) else Path(__file__).resolve().parents[1]
    p = base / filename
    return str(p) if p.exists() else filename


def collect_subscription(state):
    if state.subscription_url:
        console.print('[dim]已有订阅链接，跳过。[/dim]')
        return None
    has = None('你已经有机场（梯子）了吗？')
    if not has:
        console.print('\n[bold]本工具是一个「配置工具」，不提供、也不销售任何网络服务。[/bold]\n  [dim]它做的事只有一件：把你[bold]已经有的[/bold]订阅配置到 Clash 里，并加上分流规则和 DNS 防泄露设置。[/dim]\n\n所以这一步需要你[bold]自己先准备好一条订阅链接[/bold]。\n[dim]准备好之后，重新双击本程序，会从这一步接着来。[/dim]')
        return None
    None.print('[dim]提示：订阅链接在机场网站里，一般叫「订阅」「一键订阅」「Clash 订阅」，是一条很长的网址。[/dim]')
    url = ask_text('请把【订阅链接】粘贴到这里：')
    if not url:
        console.print('[yellow]不能为空，再试一次。[/yellow]')
        continue
    if not url.lower().startswith(('http://', 'https://')):
        console.print('[yellow]这看起来不像一条网址（应该以 http 开头），再试一次。[/yellow]')
        continue
    state.subscription_url = url
    save(state)
    console.print('[green]✓ 记下了。[/green]\n[dim]我不会提前去下载它——有些机场的订阅链接是一次性的，打开一次就失效，那唯一一次得留给 Clash 导入。\n链接对不对，等下第⑤步导入时就知道了。[/dim]')


def collect_isp(state):
    if state.isp.is_complete():
        console.print('[dim]已有 ISP 凭据，跳过。[/dim]')
        return None
    has = None('你已经有美国住宅 ISP 代理了吗？')
    if not has:
        console.print(f'''\n[bold]在这一家买：[/bold]\n    [bold]ProxySeller[/bold]\n    [cyan]{ISP_PURCHASE_URL}[/cyan]\n  [dim]这是我自己在用、也实测验证过的一家。[/dim]\n\n[bold yellow]注意：ProxySeller 的付款方式只支持 Visa / Mastercard 信用卡、PayPal、USDT。[/bold yellow]\n[dim]买的时候选「美国」「ISP / 住宅」类型。买完网站会给你一组IP、端口、账号、密码，复制下来回到这里。[/dim]\n\n[bold]这几种付款方式你一个都没有怎么办？[/bold]\n  · [bold]可以在你购买本工具的平台上私信我[/bold]，我们再商量怎么处理。\n  · 也可以自己换一家买。[bold]本工具不挑服务商[/bold]——只要买到的是\n    [bold]美国的静态住宅 / ISP 代理[/bold]，最后能拿到「IP、端口、账号、密码」就行。\n  [dim]买之前认准三点：① 美国 ② 类型是 ISP / 静态住宅（不是机房/数据中心，也不是按流量计费的动态住宅）③ 支持 SOCKS5 或 HTTP。[/dim]\n  [bold yellow]★ 另外一定要先问清楚：这家允不允许中国大陆的 IP 直连。[/bold yellow]\n  [dim]有的服务商封禁大陆 IP，买之前你没法自己验证，买完了这个工具也测不出来，很容易白花钱。[/dim]\n  [dim]有些服务商用「IP 白名单」授权而不给账号密码，也能用——到时候账号密码留空即可。[/dim]''')
        console.print(f'''\n[bold yellow]★ 买之前先看这份，一步步都写好了：[/bold yellow]\n    [cyan]{shipped_doc(PURCHASE_DOC)}[/cyan]\n  [dim]（就在你解压出来的那个文件夹里，跟本程序放在一起）\n  里面写了：选哪个产品、哪两个选项千万别勾错、买完在后台哪里看 IP 和账号密码。[/dim]''')
        console.print('\n[dim]这就帮你打开 ProxySeller 的购买页…[/dim]')
        webbrowser.open(ISP_PURCHASE_URL)
        console.print('[dim]买 ISP 可能要花点时间（注册、付款、等开通）。这个窗口可以直接关掉，弄好之后重新双击本程序，会从这一步接着来。[/dim]')
        pause('买好并复制到账号信息后，按回车继续…')
    isp = _prompt_isp()
    if isp:
        continue
        result = _probe_isp(isp)
        if result:
            reasons = getattr(isp, '_probe_reasons', [])
            refused = reasons()
            if getattr(isp, '_auth_rejected', False):
                console.print(f'''\n[bold red]这是账号密码的问题，不是网络问题。[/bold red]\n  对方明确回了「认证失败」——说明它收到了你的账号密码，但不认。\n  [dim]这种情况换网络、换梯子、继续往下配都没用，配上去链子也是断的。[/dim]\n\n[bold]回 ProxySeller 后台重新核对一遍：[/bold]\n  · 账号和密码有没有抄漏字符、有没有多带空格\n  · 是不是复制到了别条 ISP 的账号密码\n  · 后台那条 ISP 还在有效期内吗\n  [dim]不确定去哪儿看，翻这份的第三节：{shipped_doc(PURCHASE_DOC)}[/dim]''')
                if not ask_yes_no('重新输入一遍？', default = True):
                    raise SystemExit(1)
                continue
    probable = getattr(isp, '_probable', None)
    if probable:
        (port, kind, why) = probable
        label = 'SOCKS5' if kind == 'socks5' else 'HTTP'
        console.print(f'''\n[bold]不过有个重要情况要告诉你：[/bold]\n  对方是用「[bold]{label} 协议[/bold]」的方式拒绝我的，这说明端口 {port} 确实是个 {label} 代理口，[bold]端口没填错[/bold]。\n\n[bold yellow]而且这多半不影响你最终能用。[/bold yellow]\n  我刚才是[bold]从你这台电脑直连[/bold]它才被拒的。但配好之后，\n  是[bold]机场的境外节点[/bold]去连这个 ISP，不是你的电脑直连——\n  出口 IP 完全不同，很可能就不会被拦了。\n  [dim]有些服务商明确封禁中国大陆 IP 直连，就属于这种情况。[/dim]''')
        if ask_yes_no(f'''\n要不要先按 {label} 配上去？配完第⑥步会走真实链路验收，到时候才知道行不行''', default = True):
            state.isp = isp
            save(state)
            console.print(f'''[green]✓ 好，按 {label} 配（端口 {port}）。[/green]\n[dim]最终成不成，以第⑥步「出口自检」的结果为准。[/dim]''')
            return None
        result if refused else (lambda .0: [ r for r in .0 if '账号或密码' in r ])()
    elif refused:
        _print_refusal_checklist()
    if not ask_yes_no('重新输入？'):
        raise SystemExit(1)
    continue
    (isp, exit_ip) = result
    (isp, exit_ip) = _offer_socks_port(isp, exit_ip)
    state.isp = isp
    save(state)
    parts = []
    if isp.socks_port:
        parts.append(f'''端口 {isp.socks_port} → 按 SOCKS5 配''')
    if isp.http_port:
        parts.append(f'''端口 {isp.http_port} → 按 HTTP 配''')
    console.print(f'''\n[green]✓ ISP 代理可用，出口 IP：{exit_ip}[/green]\n  [bold]最终配置：{'；'.join(parts)}[/bold]''')


def _offer_socks_port(isp, exit_ip):
    '''只探到 HTTP 口时，当场追问 SOCKS5 口。

    ★ 为什么在这里追问（2026-08-11）：ProxySeller 一条 ISP 给两个端口
      （如 50100=HTTP / 50101=SOCKS5），后台里 HTTP 排在前面，
      用户随手只复制第一个是很自然的行为。

    ★ 关于「HTTP 口能不能用」——只说已知的，别下断言：
      已知：作者自用配置、以及唯一一次跑通的客户案例，用的都是 SOCKS5 口。
      也已知：**有客户两个端口都填、出口组选了 SOCKS5，第⑥步照样失败**
      （2026-08-11 实测），所以 HTTP 口并不是唯一病因。
      早先版本在这里断言「HTTP 口一定跑不通、换节点没用」，是超出证据的结论，
      已改成「建议两个都填，脚本优先用 SOCKS5」。
      `test_wording_does_not_overclaim` 锁死这一点，防止断言回潮。
    '''
    if isp.http_port or isp.socks_port:
        return (isp, exit_ip)
    guess = None.http_port + 1
    console.print(f'''\n[bold yellow]⚠ 等一下，你只给了一个端口，而它是 HTTP 口。[/bold yellow]\n  你的服务商一般会给[bold]两个[/bold]端口，另一个是 [bold]SOCKS5[/bold] 口——通常就是这个号 +1，也就是 [bold]{guess}[/bold]。\n  [dim]去后台把鼠标放到那条 ISP 上就能看到两个端口。[/dim]\n  [bold]建议两个都填。[/bold][dim]目前跑通的案例用的都是 SOCKS5 口，两个都给我，我会优先用 SOCKS5，链路更稳。[/dim]''')
    if not ask_yes_no('现在补上另一个端口吗？（建议）', default = True):
        console.print('[yellow]好，那就先这样。要是第⑥步过不去，回来把另一个端口补上再试。[/yellow]')
        return (isp, exit_ip)
    raw = None(f'''另一个端口（直接回车用 {guess}）：''', default = str(guess)).strip()
    if not raw.isdigit():
        console.print('[yellow]不是数字，跳过。[/yellow]')
        return (isp, exit_ip)
    keep_ip = exit_ip
    keep_http = None.http_port
    isp._candidate_ports = [
        keep_http,
        int(raw)]
    console.print('\n两个端口一起重测…')
    again = _probe_isp(isp)
    if again:
        return again
    isp.http_port, isp.socks_port = again, None
    console.print('[yellow]补的这个端口没测通，先按原来的 HTTP 配。[/yellow]')
    return (isp, keep_ip)


def _prompt_isp():
    '''收集 ISP 凭据。用户只管填端口数字，类型由后面实连探测判定（见 _probe_isp）。

    ★ 为什么不问用户端口类型（用户 2026-07-22 实测踩坑）：
      用户根本不知道自己买的端口是 SOCKS5 还是 HTTP，被要求分类填就会填错，
      结果「填的端口」和「脚本按的类型」对不上，一晚上跑不通。
      所以这里绝不问类型、也不相信粘贴里带的类型标签 —— 所有端口一律实测。

    ★ 填完一定回显 + 确认（用户 2026-07-22 实测踩坑）：
      逐项按回车输入时，输错了（比如密码填到 IP 那行）按回车就没法改了。
      回显 + 「对吗？」让用户能整组重来。
    '''
    console.print('\n[bold]请把 ISP 的账号信息贴进来。[/bold]\n[dim]怎么贴都行——网站上整段复制、一行的 IP:端口:账号:密码、或者 socks5://... 这种都能认。贴完按回车。[/dim]')
    raw = ask_multiline('粘贴 ISP 账号信息（直接回车则改为逐项填写）：')
    parsed = isp_parse.parse(raw) if raw.strip() else isp_parse.ParsedIsp()
    if not parsed.host:
        host = ask_text('IP 地址：')
        if not host:
            console.print('[yellow]IP 不能为空。[/yellow]')
            return None
        ports = None(parsed.ports)
        for p in (parsed.http_port, parsed.socks_port):
            if p and p not in ports:
                ports.append(p)
        if not ports:
            raw_ports = ask_text('端口（服务商给几个就填几个，空格隔开，不用管类型）：')
            for tok in raw_ports.replace(',', ' ').split():
                
                try:
                    p = int(tok)
                    if  <= 1, p or 1, p <= 65535:
                        pass
                    
                ports.append(p)
                continue
                if ValueError:
                    continue

                if not ports:
                    console.print('[yellow]至少要有一个端口。[/yellow]')
                    return None
                if not None.username and parsed.password:
                    ask_text('密码（会显示出来，方便你核对；没有就直接回车）：') = ask_text('账号（没有就直接回车）：')
    '\n[bold]你填的是：[/bold]\n  IP  ：'(f'''{host}\n  端口：{' '.join}{(lambda .0: 