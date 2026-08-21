# Claude 网络配置助手（Android）

这是桌面工具的 Android 移植辅助版，保留 Jael 原项目署名。

原作者允许免费使用、分发和编译的声明见
`LICENSE-AUTHORIZATION.zh-CN.txt`。

它可以：

- 在设备本地保存机场订阅 URL 与住宅 ISP 代理信息；
- 生成 Mihomo/Clash Meta 可读取的链式代理 YAML；
- 将配置复制到剪贴板或通过系统文件选择器导出；
- 打开 Clash Meta for Android 的发布页，供用户安装兼容客户端。

它不包含第三方代理内核，也不自行创建 Android VPN。用户需要把导出的 YAML
导入兼容 Mihomo/Clash Meta 的 Android 客户端，再由该客户端申请 VPN 权限。

## 本地构建

```bash
./build-android.sh
```

构建产物：`build/Claude网络配置助手-Android-debug.apk`

要求：JDK 17、Android SDK Platform 34、Build Tools 34.0.0。
