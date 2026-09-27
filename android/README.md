# Claude 网络配置助手（Android）

这是桌面工具的 Android 移植辅助版，保留 Jael 原项目署名。

原作者允许免费使用、分发和编译的声明见
`LICENSE-AUTHORIZATION.zh-CN.txt`。

它可以：

- 在设备本地保存机场订阅 URL 与住宅 ISP 代理信息；
- 一次输入多个端口，并自动识别 SOCKS5、HTTPS 或 HTTP 代理；
- 生成 Mihomo/Clash Meta 可读取的链式代理 YAML：国外流量先经机场节点、再从美国住宅 IP 出去，国内直连；
  机场订阅未就绪时拒绝连接，不会绕过机场直连住宅 IP；
- 通过本机一次性回环地址和官方 `clashmeta://install-config` 深链一键导入；
- 将配置复制到剪贴板或通过系统文件选择器导出；
- 打开 Clash Meta for Android 的发布页，供用户安装兼容客户端。

它不包含第三方代理内核，也不自行创建 Android VPN。一键导入会打开兼容的
Clash Meta 客户端，用户仍需在客户端确认添加，并由客户端申请 VPN 权限。

## 本地构建

当前版本：`0.3.2`。要求：JDK 17、Android SDK Platform 34、Build Tools 34.0.0。

方式一：脚本构建（无需 Gradle，macOS / Linux / Windows Git Bash 均可）

```bash
./build-android.sh
```

脚本会先运行 `tests/` 中的单元测试，再打包签名。SDK 自动从
`ANDROID_SDK_ROOT`/`ANDROID_HOME` 或常见默认路径查找，JDK 可通过 `JAVA_HOME` 指定。
构建产物：`build/Claude网络配置助手-Android-debug.apk`

方式二：Gradle / Android Studio

```bash
./gradlew assembleDebug
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`
