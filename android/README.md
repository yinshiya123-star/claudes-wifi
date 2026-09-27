# Claude 网络配置助手（Android）

这是桌面工具的 Android 移植辅助版，保留 Jael 原项目署名。

原作者允许免费使用、分发和编译的声明见
`LICENSE-AUTHORIZATION.zh-CN.txt`。

它可以：

- 在设备本地保存机场订阅 URL 与住宅 ISP 代理信息；
- 拍照或选截图，自动识别机场订阅（二维码/链接）和住宅 ISP 的 IP、端口、账号、密码，
  核对后一键填入；识别在手机本地完成（ML Kit 离线模型），图片和识别出的文字不会上传
  （ML Kit 可能向 Google 发送不含图片和文字的匿名使用统计）；
- 一次输入多个端口，并自动识别 SOCKS5、HTTPS 或 HTTP 代理；
- 生成 Mihomo/Clash Meta 可读取的链式代理 YAML：国外流量先经机场节点、再从美国住宅 IP 出去，国内直连；
  机场订阅未就绪时拒绝连接，不会绕过机场直连住宅 IP；
- 通过本机一次性回环地址和官方 `clashmeta://install-config` 深链一键导入；
- 将配置复制到剪贴板或通过系统文件选择器导出；
- 打开 Clash Meta for Android 的发布页，供用户安装兼容客户端。

它不包含第三方代理内核，也不自行创建 Android VPN。一键导入会打开兼容的
Clash Meta 客户端，用户仍需在客户端确认添加，并由客户端申请 VPN 权限。

## 本地构建

当前版本：`0.4.0`。要求：JDK 17、Android SDK Platform 34。

```bash
./build-android.sh
```

脚本会先在 JVM 上运行 `tests/` 中的单元测试，再调用 Gradle 打包：

- 找到 `keystore.properties`（`android/` 目录下，或环境变量 `CLAUDE_NET_KEYSTORE_PROPERTIES`
  指向的文件）时，生成正式签名的 `build/Claude网络配置助手-Android-release.apk`；
- 否则生成调试签名的 `build/Claude网络配置助手-Android-debug.apk`。

也可以直接用 Gradle / Android Studio：`./gradlew assembleRelease` 或 `./gradlew assembleDebug`。

### 正式签名

`keystore.properties` 格式见 `app/build.gradle` 顶部注释。密钥库和密码**不要提交到仓库**
（已加入 `.gitignore`）。Android 只允许同一证书签名的新版本覆盖安装，
丢失密钥库后用户只能卸载重装，请务必离线备份。
