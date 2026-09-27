package com.jael.claudenet;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成链式代理配置：国外流量 → 机场节点（前置）→ 美国住宅 ISP → 目标站，国内直连。
 *
 * <p>与桌面版一致：ISP 节点通过 dialer-proxy 挂在“Claude-前置节点”组上，
 * 前置组默认走自动测速，连不通 ISP 时由用户手动换机场节点。
 */
final class ConfigBuilder {
    static final String FRONT_GROUP = "Claude-前置节点";
    static final String FRONT_AUTO_GROUP = "Claude-前置-自动测速";
    static final String ISP_GROUP = "Claude-住宅ISP";
    static final String EXIT_GROUP = "Claude-专用出口";

    private static final String HEALTH_URL = "https://www.gstatic.com/generate_204";
    // 机场订阅里常见的“剩余流量/套餐到期”等信息节点，不能作为前置
    private static final String INFO_NODE_FILTER =
            "(?i)剩余|到期|流量|套餐|官网|重置|过期|expire|traffic|reset|website";
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
        List<ProxyPortDetector.Endpoint> nodes = expandUnknown(endpoints);
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
        yaml.append("      interval: 300\n\n");

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
        // 订阅尚未加载或节点全部不可用时，空组会被 Mihomo 当作 DIRECT，
        // 导致手机直连住宅 IP（国内会失败，并把 ISP 节点误判为不可用）。
        // 放一个 REJECT 兜底：宁可拒绝，也不绕过机场。
        yaml.append("    proxies:\n");
        yaml.append("      - REJECT\n");
        yaml.append("    use:\n");
        yaml.append("      - airport\n");
        yaml.append("    exclude-filter: ").append(quote(INFO_NODE_FILTER)).append("\n");
        yaml.append("    url: ").append(HEALTH_URL).append("\n");
        yaml.append("    interval: 300\n");
        yaml.append("    tolerance: 50\n");
        // 健康检查走完整链路，自动跳过协议或端口不通的 ISP 节点
        yaml.append("  - name: ").append(ISP_GROUP).append("\n");
        yaml.append("    type: fallback\n");
        yaml.append("    proxies:\n");
        for (ProxyPortDetector.Endpoint endpoint : nodes) {
            yaml.append("      - ").append(proxyName(endpoint)).append("\n");
        }
        yaml.append("    url: ").append(HEALTH_URL).append("\n");
        // 启动时订阅未就绪，首次检查会全部失败；缩短间隔并在连续失败后立即复查
        yaml.append("    interval: 60\n");
        yaml.append("    lazy: false\n");
        yaml.append("    max-failed-times: 1\n");
        yaml.append("  - name: ").append(EXIT_GROUP).append("\n");
        yaml.append("    type: select\n");
        yaml.append("    proxies:\n");
        yaml.append("      - ").append(ISP_GROUP).append("\n");
        for (ProxyPortDetector.Endpoint endpoint : nodes) {
            yaml.append("      - ").append(proxyName(endpoint)).append("\n");
        }
        yaml.append("\n");

        // 国内 DNS：1.1.1.1/8.8.8.8 在国内直连不可用，会导致机场节点域名都解析失败。
        // fake-ip 下走代理的域名由远端解析，不会用到这里的结果。
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

        yaml.append("rules:\n");
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
        yaml.append("  - GEOIP,CN,DIRECT\n");
        // 其余国外流量（Google 登录、Cloudflare 验证等）也走同一条住宅链路
        yaml.append("  - MATCH,").append(EXIT_GROUP).append("\n");
        return yaml.toString();
    }

    /** 协议未识别的端口同时生成 SOCKS5 与 HTTP 两个节点，由 fallback 组按实际连通性选择。 */
    static List<ProxyPortDetector.Endpoint> expandUnknown(
            List<ProxyPortDetector.Endpoint> endpoints) {
        List<ProxyPortDetector.Endpoint> nodes = new ArrayList<>();
        for (ProxyPortDetector.Endpoint endpoint : endpoints) {
            if (endpoint.protocol == ProxyPortDetector.Protocol.UNKNOWN) {
                nodes.add(new ProxyPortDetector.Endpoint(
                        endpoint.port, ProxyPortDetector.Protocol.SOCKS5));
                nodes.add(new ProxyPortDetector.Endpoint(
                        endpoint.port, ProxyPortDetector.Protocol.HTTP));
            } else {
                nodes.add(endpoint);
            }
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
