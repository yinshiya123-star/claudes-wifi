# Claude 网络配置工具（社区恢复版）

原项目作者：**Jael**（小红书：4217588599，微信：Jaelane0729）

本仓库根据原作者允许免费使用、分发和编译的声明整理，包含：

- Android 配置助手完整源码；
- 从 macOS ARM64 成品恢复的 Python 源码与原始字节码提取工具；
- Windows/macOS 平台逻辑的恢复资料；
- macOS Apple 芯片、macOS Intel 便携版及 Android APK 的 Release 构建产物。

## 重要说明

桌面端原始源码并不在收到的文件中。仓库内 `desktop-recovery/` 是从
Python 3.11 字节码生成的两套反编译结果，其中部分文件存在控制流缺失、
语法损坏或反编译器警告，**不能视为无损原始源码**。相应警告文件与提取工具
一并保留，方便继续修复和核对。

Android 版是独立实现的配置助手：在本机保存订阅和 ISP 信息，生成符合
Mihomo/Clash Meta `dialer-proxy` 结构的 YAML，并支持复制或导出。它不内置
第三方代理内核，不会自行创建 Android VPN；导出的配置需要导入兼容客户端。

## Release 产物

- `Claude网络配置工具-macOS-Apple-arm64-v0.2.25.zip`
- `Claude网络配置工具-macOS-Intel-x86_64-v0.2.25.zip`
- `Claude网络配置工具-Windows-x86_64-v0.2.13.zip`
- `Claude网络配置助手-Android-debug.apk`

Intel Mac 版使用 x86_64 Python 3.11 运行恢复出的原始字节码，并不是由完整
桌面源码重新编译的单文件程序。Android APK 使用本地调试证书签名，适合测试
和私人安装；正式商店发布应更换长期保管的发布密钥。

Windows 版来自原作者提供的 v0.2.13 x86-64 成品，当前环境无法直接运行
Windows EXE，因此只完成了文件类型、SHA-256 和压缩包完整性校验。

## 隐私与安全

订阅地址、代理账号和密码属于敏感信息。提交 Issue、日志或截图前请先打码。
不要把运行时生成的状态文件、配置文件或真实凭据提交到仓库。

## 授权

原作者提供的授权文字见
[`LICENSE-AUTHORIZATION.zh-CN.txt`](LICENSE-AUTHORIZATION.zh-CN.txt)。发布、修改
或再分发时请保留原作者署名与该声明。
