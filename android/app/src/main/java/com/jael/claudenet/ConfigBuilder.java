package com.jael.claudenet;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成链式代理配置：所有国外流量（不只是 Claude）→ 机场节点（前置）→ 美国住宅 ISP → 目标站，
 * 国内直连。
 *
 * <p>与桌面版一致：ISP 节点通过 dialer-proxy 挂在“Claude-前置节点”组上，
 * 前置组默认走自动测速，连不通 ISP 时由用户手动换机场节点。
 */
final class ConfigBuilder {
    static final String FRONT_GROUP = "Claude-前置节点";
    static final String FRONT_AUTO_GROUP = "Claude-前置-自动测速";
    static final String ISP_GROUP = "Claude-住宅ISP";
    static final String EXIT_GROUP = "住宅ISP-全局出口";

    private static final String HEALTH_URL = "https://www.gstatic.com/generate_204";
    // 机场订阅里的“剩余流量/套餐到期/距离下次重置”等信息节点，不能作为前置。
    // 不能单独匹配“流量”“套餐”：很多机场的正常节点名带“流量倍率”，会被整组排除。
    private static final String INFO_NODE_FILTER =
            "(?i)剩余|到期|过期|重置|官网|官方网站|expire|remaining|reset|website|traffic\\s*[:：]";
    // 国内可直连的 geo 数据镜像，客户端缺少数据库时使用
    private static final String GEO_MIRROR =
            "https://testingcf.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/";

    private ConfigBuilder() {}

    static String build(
            String subscriptionUrl,
            String host,
            List<ProxyPortDetector.Endpoint> endpoints,
            String username,
            String password) {
        List<ProxyPortDetector.Endpoint> nodes = oneNodePerPort(endpoints);
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Claude 网络配置助手生成\n");
        yaml.append("# 原项目作者：Jael；Android 移植辅助版\n");
        yaml.append("# 链路：国外流量 → 机场节点 → 美国住宅 ISP → 目标站；国内直连\n");
        yaml.append("mixed-port: 7890\n");
        yaml.append("allow-lan: false\n");
        yaml.append("mode: rule\n");
        yaml.append("log-level: info\n");
        yaml.append("ipv6: false\n");
        yaml.append("geodata-mode: false\n");
        yaml.append("geox-url:\n");
        yaml.append("  geoip: ").append(GEO_MIRROR).append("geoip.dat\n");
        yaml.append("  geosite: ").append(GEO_MIRROR).append("geosite.dat\n");
        yaml.append("  mmdb: ").append(GEO_MIRROR).append("country.mmdb\n\n");

        yaml.append("proxy-providers:\n");
        yaml.append("  airport:\n");
        yaml.append("    type: http\n");
        yaml.append("    url: ").append(quote(subscriptionUrl)).append("\n");
        yaml.append("    path: ./providers/airport.yaml\n");
        yaml.append("    interval: 86400\n");
        yaml.append("    health-check:\n");
        yaml.append("      enable: true\n");
        yaml.append("      url: ").append(HEALTH_URL).append("\n");
        yaml.append("      interval: 300\n");
        // 主动测速，客户端里能直接看到机场节点延迟
        yaml.append("      lazy: false\n\n");

        yaml.append("proxies:\n");
        for (ProxyPortDetector.Endpoint endpoint : nodes) {
            String type = endpoint.protocol == ProxyPortDetector.Protocol.SOCKS5
                    ? "socks5" : "http";
            yaml.append("  - name: ").append(proxyName(endpoint)).append("\n");
            yaml.append("    type: ").append(type).append("\n");
            yaml.append("    server: ").append(quote(host)).append("\n");
            yaml.append("    port: ").append(endpoint.port).append("\n");
            if (endpoint.protocol == ProxyPortDetector.Protocol.HTTPS) {
                yaml.append("    tls: true\n");
                // 与端口检测一致：检测时不校验证书，自签证书的 ISP 也要能用
                yaml.append("    skip-cert-verify: true\n");
            }
            if (!username.isEmpty()) {
                yaml.append("    username: ").append(quote(username)).append("\n");
            }
            if (!password.isEmpty()) {
                yaml.append("    password: ").append(quote(password)).append("\n");
            }
            // 先经机场再连住宅 ISP
            yaml.append("    dialer-proxy: ").append(FRONT_GROUP).append("\n");
        }
        yaml.append("\n");

        yaml.append("proxy-groups:\n");
        // 连不上 Claude 时在这里手动换机场节点；默认走自动测速
        yaml.append("  - name: ").append(FRONT_GROUP).append("\n");
        yaml.append("    type: select\n");
        yaml.append("    proxies:\n");
        yaml.append("      - ").append(FRONT_AUTO_GROUP).append("\n");
        yaml.append("    use:\n");
        yaml.append("      - airport\n");
        yaml.append("  - name: ").append(FRONT_AUTO_GROUP).append("\n");
        yaml.append("    type: url-test\n");
        // 不放 REJECT 兜底：一轮测速超时就会选中 REJECT，导致全部断网直到下次测速
        yaml.append("    use:\n");
        yaml.append("      - airport\n");
        yaml.append("    exclude-filter: ").append(quote(INFO_NODE_FILTER)).append("\n");
        yaml.append("    url: ").append(HEALTH_URL).append("\n");
        yaml.append("    interval: 300\n");
        yaml.append("    tolerance: 50\n");
        yaml.append("    lazy: false\n");
        boolean multiplePorts = nodes.size() > 1;
        if (multiplePorts) {
            // 多个端口时健康检查走完整链路，自动跳过不通的端口
            yaml.append("  - name: ").append(ISP_GROUP).append("\n");
            yaml.append("    type: fallback\n");
            yaml.append("    proxies:\n");
            for (ProxyPortDetector.Endpoint endpoint : nodes) {
                yaml.append("      - ").append(proxyName(endpoint)).append("\n");
            }
            yaml.append("    url: ").append(HEALTH_URL).append("\n");
            yaml.append("    interval: 60\n");
            yaml.append("    timeout: 10000\n");
            yaml.append("    lazy: false\n");
            yaml.append("    max-failed-times: 1\n");
        }
        // 出口组：单个端口时只有这一个住宅节点
        yaml.append("  - name: ").append(EXIT_GROUP).append("\n");
        yaml.append("    type: select\n");
        yaml.append("    proxies:\n");
        if (multiplePorts) {
            yaml.append("      - ").append(ISP_GROUP).append("\n");
        } else {
            yaml.append("      - ").append(proxyName(nodes.get(0))).append("\n");
        }
        // 客户端切到“全局”模式时 Mihomo 用 GLOBAL 组；不自定义的话默认是 DIRECT，
        // 全部流量既不过机场也不过住宅 ISP。这里让全局模式也默认走住宅出口。
        yaml.append("  - name: GLOBAL\n");
        yaml.append("    type: select\n");
        yaml.append("    proxies:\n");
        yaml.append("      - ").append(EXIT_GROUP).append("\n");
        yaml.append("      - ").append(FRONT_GROUP).append("\n");
        yaml.append("      - DIRECT\n");
        yaml.append("\n");

        // 国内 DNS：1.1.1.1/8.8.8.8 在国内直连不可用，会导致机场节点域名都解析失败。
        // 规则全部 no-resolve，走住宅出口的域名由 ISP 远端解析，不会发给国内 DNS（防 DNS 泄露）。
        yaml.append("dns:\n");
        yaml.append("  enable: true\n");
        yaml.append("  ipv6: false\n");
        yaml.append("  enhanced-mode: fake-ip\n");
        yaml.append("  fake-ip-range: 198.18.0.1/16\n");
        yaml.append("  fake-ip-filter:\n");
        yaml.append("    - '*.lan'\n");
        yaml.append("    - '+.local'\n");
        yaml.append("    - '+.pool.ntp.org'\n");
        yaml.append("    - '+.msftconnecttest.com'\n");
        yaml.append("  default-nameserver:\n");
        yaml.append("    - 223.5.5.5\n");
        yaml.append("    - 119.29.29.29\n");
        yaml.append("  nameserver:\n");
        yaml.append("    - https://223.5.5.5/dns-query\n");
        yaml.append("    - https://doh.pub/dns-query\n");
        yaml.append("  proxy-server-nameserver:\n");
        yaml.append("    - https://223.5.5.5/dns-query\n");
        yaml.append("    - https://doh.pub/dns-query\n\n");

        // 手机开了“私人 DNS”或 App 自己解析域名时，拿到的是国内 DNS 污染过的 IP
        // （google.com 等），连过去必然失败。嗅探 TLS/HTTP 里的真实域名并替换目标，
        // 交给住宅 ISP 远端解析。HTTP 只嗅探 80 端口，避免误伤住宅代理端口的检测流量。
        yaml.append("sniffer:\n");
        yaml.append("  enable: true\n");
        yaml.append("  force-dns-mapping: true\n");
        yaml.append("  parse-pure-ip: true\n");
        yaml.append("  override-destination: true\n");
        yaml.append("  sniff:\n");
        yaml.append("    HTTP:\n");
        yaml.append("      ports: [80]\n");
        yaml.append("    TLS:\n");
        yaml.append("      ports: [443, 8443]\n");
        yaml.append("    QUIC:\n");
        yaml.append("      ports: [443, 8443]\n");
        yaml.append("  skip-domain:\n");
        yaml.append("    - '+.push.apple.com'\n\n");

        yaml.append("rules:\n");
        // 连住宅 ISP 服务器本身的流量（例如本 App 检测端口协议）走机场：
        // 国内直连住宅 IP 通常被阻断，走住宅出口则会绕回自己
        if (host.matches("(?:\\d{1,3}\\.){3}\\d{1,3}")) {
            yaml.append("  - IP-CIDR,").append(host).append("/32,").append(FRONT_GROUP)
                    .append(",no-resolve\n");
        } else {
            yaml.append("  - DOMAIN,").append(host).append(",").append(FRONT_GROUP).append("\n");
        }
        yaml.append("  - DOMAIN-SUFFIX,claude.ai,").append(EXIT_GROUP).append("\n");
        yaml.append("  - DOMAIN-SUFFIX,anthropic.com,").append(EXIT_GROUP).append("\n");
        yaml.append("  - DOMAIN-SUFFIX,claudeusercontent.com,").append(EXIT_GROUP).append("\n");
        yaml.append("  - DOMAIN-SUFFIX,claude.com,").append(EXIT_GROUP).append("\n");
        yaml.append("  - DOMAIN-SUFFIX,local,DIRECT\n");
        yaml.append("  - IP-CIDR,127.0.0.0/8,DIRECT,no-resolve\n");
        yaml.append("  - IP-CIDR,10.0.0.0/8,DIRECT,no-resolve\n");
        yaml.append("  - IP-CIDR,172.16.0.0/12,DIRECT,no-resolve\n");
        yaml.append("  - IP-CIDR,192.168.0.0/16,DIRECT,no-resolve\n");
        yaml.append("  - IP-CIDR,100.64.0.0/10,DIRECT,no-resolve\n");
        yaml.append("  - IP-CIDR,169.254.0.0/16,DIRECT,no-resolve\n");
        yaml.append("  - DOMAIN-SUFFIX,cn,DIRECT\n");
        // 国内域名按 GEOSITE 判断，不需要先用国内 DNS 解析国外域名
        yaml.append("  - GEOSITE,cn,DIRECT\n");
        yaml.append("  - GEOIP,CN,DIRECT,no-resolve\n");
        yaml.append("  - DST-PORT,123,DIRECT\n");
        // 住宅 ISP 节点不支持 UDP；Mihomo 会跳过不支持 UDP 的规则、最后落到 DIRECT，
        // 导致 QUIC/WebRTC 绕过住宅 IP 直连（会暴露真实 IP）。国外 UDP 一律拒绝，
        // 浏览器会自动改用 TCP，从住宅 IP 出去。
        yaml.append("  - NETWORK,UDP,REJECT\n");
        // 其余所有国外流量（不只是 Claude）都走“机场 → 住宅 ISP”
        yaml.append("  - MATCH,").append(EXIT_GROUP).append("\n");
        return yaml.toString();
    }

    /**
     * 每个端口只生成一个节点。协议未识别时界面会先让用户选择；
     * 万一仍是未知，按 SOCKS5 处理（桌面版实测可用的住宅 ISP 都是 SOCKS5 口）。
     */
    static List<ProxyPortDetector.Endpoint> oneNodePerPort(
            List<ProxyPortDetector.Endpoint> endpoints) {
        List<ProxyPortDetector.Endpoint> nodes = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();
        for (ProxyPortDetector.Endpoint endpoint : endpoints) {
            if (seen.contains(endpoint.port)) continue;
            seen.add(endpoint.port);
            nodes.add(endpoint.protocol == ProxyPortDetector.Protocol.UNKNOWN
                    ? new ProxyPortDetector.Endpoint(endpoint.port, ProxyPortDetector.Protocol.SOCKS5)
                    : endpoint);
        }
        return nodes;
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String proxyName(ProxyPortDetector.Endpoint endpoint) {
        return ISP_GROUP + "-" + endpoint.protocol.name() + "-" + endpoint.port;
    }
}
