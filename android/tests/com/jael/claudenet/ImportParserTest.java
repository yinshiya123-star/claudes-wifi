package com.jael.claudenet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ImportParserTest {
    private static final List<String> NO_QR = Collections.emptyList();

    public static void main(String[] args) {
        // 1. proxy-seller 标准行
        ImportParser.Result r = ImportParser.parse(NO_QR, "203.0.113.7:50101:demo:s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");

        // 2. 代理 URL
        r = ImportParser.parse(NO_QR, "socks5://demo:s3cret@203.0.113.7:50101");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        require(r.subscriptionUrl == null, "代理 URL 不能当成订阅");

        // 3. user:pass@host:port
        r = ImportParser.parse(NO_QR, "demo:s3cret@203.0.113.7:50101");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");

        // 4. 英文标签，HTTP 与 SOCKS5 两个端口，OCR 把 0 认成 O
        r = ImportParser.parse(NO_QR, "IP: 203.0.113.7\nHTTP port: 5O100\n"
                + "SOCKS5 port: 50101\nLogin: demo\nPassword: s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50100, 50101), "demo", "s3cret");

        // 5. 中文标签（全角冒号）
        r = ImportParser.parse(NO_QR, "IP地址：203.0.113.7\n端口：50101\n账号：demo\n密码：s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");

        // 5b. OCR 把标签和值拆成两行
        r = ImportParser.parse(NO_QR, "IP\n203.0.113.7\nPort\n50101\nUsername\ndemo\nPassword\ns3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");

        // 6. YAML 片段
        r = ImportParser.parse(NO_QR, "server: '203.0.113.7'\nport: 50101\n"
                + "username: 'demo'\npassword: \"s3cret\"");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");

        // 7. 空格/Tab 分隔
        r = ImportParser.parse(NO_QR, "203.0.113.7\t50101 demo s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");

        // ---- 单端口的常见截图形态 ----
        // 面板表头 “HTTP(S)/SOCKS5 port”，OCR 按列输出
        r = ImportParser.parse(NO_QR, "IP address\n203.0.113.7\nHTTP(S)/SOCKS5 port\n50101\n"
                + "Login\ndemo\nPassword\ns3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        // 单独一行 ip:端口，账号密码另起
        r = ImportParser.parse(NO_QR, "203.0.113.7:50101\nLogin: demo\nPassword: s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        // 合并标签 “IP:Port”
        r = ImportParser.parse(NO_QR, "IP:Port: 203.0.113.7:50101\nLogin: demo\nPassword: s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        // OCR 丢了冒号
        r = ImportParser.parse(NO_QR, "IP 203.0.113.7\nPort 50101\nLogin demo\nPassword s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        // 端口旁边的日期、时长不能当端口
        r = ImportParser.parse(NO_QR, "IP: 203.0.113.7\nPort: 50101 (valid until 2026-10-27, 30 days)\n"
                + "Login: demo\nPassword: s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        // “Login:Password” 合并列
        r = ImportParser.parse(NO_QR, "IP: 203.0.113.7\nPort: 50101\nLogin:Password: demo:s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        // 正文里的 “User guide” 不能抢在明确标签前面
        r = ImportParser.parse(NO_QR, "User guide\nIP: 203.0.113.7\nPort: 50101\n"
                + "Username: demo\nPassword: s3cret");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");
        // 订阅链接里的 host:port 不能当成 ISP
        r = ImportParser.parse(NO_QR,
                "https://sub.example.com:8443/api/v1/client/subscribe?token=abc123");
        require(r.host == null && r.ports.isEmpty(), "订阅链接里的端口");

        // 端口有标签、IP 没标签时取唯一 IPv4
        r = ImportParser.parse(NO_QR, "Your proxy 203.0.113.7 is active\nPort: 50101");
        require("203.0.113.7".equals(r.host), "未标注的 IP");

        // 非法 IP 不采用
        r = ImportParser.parse(NO_QR, "999.1.1.1:50101:demo:s3cret");
        require(r.host == null, "非法 IP");

        // 订阅：截图里的链接，被 OCR 折成两行，旁边还有官网链接
        r = ImportParser.parse(NO_QR, "官网 https://airport.example.com\n订阅链接\n"
                + "https://sub.example.com/api/v1/client/subscribe?tok\nen=abc123def456");
        require("https://sub.example.com/api/v1/client/subscribe?token=abc123def456"
                .equals(r.subscriptionUrl), "折行订阅链接：" + r.subscriptionUrl);
        require(!r.hasIsp(), "订阅截图不应识别出 ISP");

        // 普通官网链接不算订阅
        r = ImportParser.parse(NO_QR, "欢迎访问 https://airport.example.com");
        require(r.subscriptionUrl == null, "官网链接不是订阅");

        // 二维码：订阅链接、clash 一键导入链接、单节点
        r = ImportParser.parse(Arrays.asList("https://sub.example.com/link/abc?clash=1"), "");
        require("https://sub.example.com/link/abc?clash=1".equals(r.subscriptionUrl), "二维码订阅");
        r = ImportParser.parse(Arrays.asList(
                "clash://install-config?url=https%3A%2F%2Fsub.example.com%2Fs%3Ftoken%3Dx&name=a"), "");
        require("https://sub.example.com/s?token=x".equals(r.subscriptionUrl), "install-config");
        r = ImportParser.parse(Arrays.asList("vmess://eyJ2IjoiMiJ9"), "");
        require("vmess".equals(r.nodeScheme) && r.subscriptionUrl == null, "单节点二维码");

        // 二维码里是 ISP 文本
        r = ImportParser.parse(Arrays.asList("203.0.113.7:50101:demo:s3cret"), "");
        isp(r, "203.0.113.7", Arrays.asList(50101), "demo", "s3cret");

        require(ImportParser.parse(NO_QR, "今天天气不错").isEmpty(), "无关文字");
        System.out.println("ImportParserTest: PASS");
    }

    private static void isp(ImportParser.Result r, String host, List<Integer> ports,
            String user, String pass) {
        require(host.equals(r.host), "host=" + r.host);
        require(ports.equals(r.ports), "ports=" + r.ports);
        require(user.equals(r.username), "user=" + r.username);
        require(pass.equals(r.password), "pass 不符");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("识别解析不符合预期：" + message);
    }
}
