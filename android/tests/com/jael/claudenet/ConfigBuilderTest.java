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
        // 私人 DNS/App 自己解析拿到被污染的 IP 时，靠嗅探还原真实域名（否则 Google 打不开）
        require(yaml.contains("sniffer:\n  enable: true\n  force-dns-mapping: true\n"
                + "  parse-pure-ip: true\n  override-destination: true\n"));
        // HTTP 只嗅探 80，不能误伤 8080 等住宅代理端口的检测流量
        require(yaml.contains("    HTTP:\n      ports: [80]\n"));
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
        // 一个端口只生成一个节点（未知协议按 SOCKS5），不再出现第二个节点
        require(unknown.contains("name: Claude-住宅ISP-SOCKS5-50101\n    type: socks5"));
        require(!unknown.contains("Claude-住宅ISP-HTTP-50101"));
        require(!unknown.contains("UNKNOWN"));
        require(count(unknown, "dialer-proxy: Claude-前置节点") == 1);
        // 单端口时不需要 fallback 组，出口组里只有这一个节点
        require(!unknown.contains("type: fallback"));
        require(unknown.contains("  - name: 住宅ISP-全局出口\n    type: select\n    proxies:\n"
                + "      - Claude-住宅ISP-SOCKS5-50101\n  - name: GLOBAL"));
        // 连住宅 ISP 服务器本身（App 检测端口）走机场，排在所有规则最前面
        require(unknown.contains("rules:\n  - IP-CIDR,203.0.113.7/32,Claude-前置节点,no-resolve\n"));
        // 机场节点主动测速
        require(unknown.contains("interval: 300\n      lazy: false"));
        require(unknown.contains("tolerance: 50\n    lazy: false"));

        String domainHost = ConfigBuilder.build("https://example.com/sub", "isp.example.com",
                Arrays.asList(new ProxyPortDetector.Endpoint(
                        50101, ProxyPortDetector.Protocol.HTTP)), "u", "p");
        require(domainHost.contains("rules:\n  - DOMAIN,isp.example.com,Claude-前置节点\n"));
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
