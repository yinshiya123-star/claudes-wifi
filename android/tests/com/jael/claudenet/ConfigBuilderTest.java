package com.jael.claudenet;

import java.util.Arrays;

public final class ConfigBuilderTest {
    public static void main(String[] args) {
        String yaml = ConfigBuilder.build(
                "https://example.com/sub?token=a'b",
                "203.0.113.7",
                Arrays.asList(
                        new ProxyPortDetector.Endpoint(50101, ProxyPortDetector.Protocol.SOCKS5),
                        new ProxyPortDetector.Endpoint(8443, ProxyPortDetector.Protocol.HTTPS)),
                "demo",
                "p'ass");
        require(yaml.contains("type: socks5"));
        require(yaml.contains("port: 50101"));
        require(yaml.contains("port: 8443"));
        require(yaml.contains("name: Claude-住宅ISP-HTTPS-8443\n    type: http"));
        require(yaml.contains("tls: true\n    skip-cert-verify: true"));
        require(yaml.contains("dialer-proxy: Claude-前置节点"));
        require(yaml.contains("use:\n      - airport"));
        require(yaml.contains("DOMAIN-SUFFIX,claude.ai,住宅ISP-全局出口"));
        require(yaml.contains("url: 'https://example.com/sub?token=a''b'"));
        require(yaml.contains("password: 'p''ass'"));

        String noAuth = ConfigBuilder.build(
                "https://example.com/sub", "proxy.example.com",
                Arrays.asList(new ProxyPortDetector.Endpoint(
                        8080, ProxyPortDetector.Protocol.HTTP)), "", "");
        require(noAuth.contains("type: http"));
        require(!noAuth.contains("username:"));
        require(!noAuth.contains("password:"));

        // 链式：所有国外流量（不只是 Claude）走住宅出口，国内直连；DNS 不能用国内无法直连的 1.1.1.1/8.8.8.8
        require(yaml.contains("  - MATCH,住宅ISP-全局出口\n"));
        // 规则不触发本地解析，国外域名不会发给国内 DNS（防 DNS 泄露）
        require(yaml.contains("  - GEOSITE,cn,DIRECT\n  - GEOIP,CN,DIRECT,no-resolve\n"));
        require(!yaml.contains("GEOIP,CN,DIRECT\n"));
        // 住宅 ISP 不支持 UDP，国外 UDP 必须拒绝，不能落到 DIRECT 绕过住宅 IP
        require(yaml.contains("  - NETWORK,UDP,REJECT\n  - MATCH,住宅ISP-全局出口\n"));
        // 全局模式也要走住宅出口，不能是默认的 DIRECT
        require(yaml.contains("  - name: GLOBAL\n    type: select\n    proxies:\n"
                + "      - 住宅ISP-全局出口\n"));
        require(!yaml.contains("MATCH,DIRECT"));
        require(!yaml.contains("1.1.1.1") && !yaml.contains("8.8.8.8"));
        require(yaml.contains("default-nameserver:\n    - 223.5.5.5"));
        // 前置组默认自动测速，并排除机场信息节点
        require(yaml.contains("name: Claude-前置节点\n    type: select\n    proxies:\n"
                + "      - Claude-前置-自动测速\n    use:\n      - airport"));
        require(yaml.contains("exclude-filter: '(?i)剩余"));
        // 正常节点名常带“流量倍率”，过滤规则不能单独匹配“流量”“套餐”
        require(!yaml.contains("|流量|") && !yaml.contains("|套餐|"));
        require(yaml.contains("name: Claude-住宅ISP\n    type: fallback"));
        // 自动测速组不能有 REJECT：一轮测速超时就会全部断网
        require(yaml.contains("type: url-test\n    use:\n      - airport"));
        require(!yaml.contains("- REJECT"));
        require(yaml.contains("interval: 60\n    timeout: 10000\n    lazy: false\n    max-failed-times: 1"));

        // 协议未识别的端口同时生成 SOCKS5 和 HTTP 节点，且都经机场拨号
        String unknown = ConfigBuilder.build(
                "https://example.com/sub", "203.0.113.7",
                Arrays.asList(new ProxyPortDetector.Endpoint(
                        50101, ProxyPortDetector.Protocol.UNKNOWN)), "u", "p");
        require(unknown.contains("name: Claude-住宅ISP-SOCKS5-50101\n    type: socks5"));
        require(unknown.contains("name: Claude-住宅ISP-HTTP-50101\n    type: http"));
        require(!unknown.contains("UNKNOWN"));
        require(count(unknown, "dialer-proxy: Claude-前置节点") == 2);
        System.out.println("ConfigBuilderTest: PASS");
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) count++;
        return count;
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("配置生成结果不符合预期");
    }
}
