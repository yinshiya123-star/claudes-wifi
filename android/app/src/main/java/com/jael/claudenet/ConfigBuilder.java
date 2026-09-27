package com.jael.claudenet;

import java.util.List;

final class ConfigBuilder {
    private ConfigBuilder() {}

    static String build(
            String subscriptionUrl,
            String host,
            List<ProxyPortDetector.Endpoint> endpoints,
            String username,
            String password) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Claude 网络配置助手生成\n");
        yaml.append("# 原项目作者：Jael；Android 移植辅助版\n");
        yaml.append("mixed-port: 7890\n");
        yaml.append("allow-lan: false\n");
        yaml.append("mode: rule\n");
        yaml.append("log-level: info\n\n");

        yaml.append("proxy-providers:\n");
        yaml.append("  airport:\n");
        yaml.append("    type: http\n");
        yaml.append("    url: ").append(quote(subscriptionUrl)).append("\n");
        yaml.append("    path: ./providers/airport.yaml\n");
        yaml.append("    interval: 86400\n");
        yaml.append("    health-check:\n");
        yaml.append("      enable: true\n");
        yaml.append("      url: https://www.gstatic.com/generate_204\n");
        yaml.append("      interval: 600\n\n");

        yaml.append("proxies:\n");
        for (ProxyPortDetector.Endpoint endpoint : endpoints) {
            String name = proxyName(endpoint);
            String type = endpoint.protocol == ProxyPortDetector.Protocol.SOCKS5
                    ? "socks5" : "http";
            yaml.append("  - name: ").append(name).append("\n");
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
            yaml.append("    dialer-proxy: Claude-前置节点\n");
        }
        yaml.append("\n");

        yaml.append("proxy-groups:\n");
        yaml.append("  - name: Claude-前置节点\n");
        yaml.append("    type: select\n");
        yaml.append("    use:\n");
        yaml.append("      - airport\n");
        yaml.append("  - name: Claude-住宅ISP\n");
        yaml.append("    type: select\n");
        yaml.append("    proxies:\n");
        for (ProxyPortDetector.Endpoint endpoint : endpoints) {
            yaml.append("      - ").append(proxyName(endpoint)).append("\n");
        }
        yaml.append("  - name: Claude-专用出口\n");
        yaml.append("    type: select\n");
        yaml.append("    proxies:\n");
        yaml.append("      - Claude-住宅ISP\n\n");

        yaml.append("dns:\n");
        yaml.append("  enable: true\n");
        yaml.append("  ipv6: false\n");
        yaml.append("  enhanced-mode: fake-ip\n");
        yaml.append("  nameserver:\n");
        yaml.append("    - https://1.1.1.1/dns-query\n");
        yaml.append("    - https://8.8.8.8/dns-query\n\n");

        yaml.append("rules:\n");
        yaml.append("  - DOMAIN-SUFFIX,claude.ai,Claude-专用出口\n");
        yaml.append("  - DOMAIN-SUFFIX,anthropic.com,Claude-专用出口\n");
        yaml.append("  - DOMAIN-SUFFIX,claudeusercontent.com,Claude-专用出口\n");
        yaml.append("  - DOMAIN-SUFFIX,claude.com,Claude-专用出口\n");
        yaml.append("  - MATCH,DIRECT\n");
        return yaml.toString();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String proxyName(ProxyPortDetector.Endpoint endpoint) {
        return "Claude-住宅ISP-" + endpoint.protocol.name() + "-" + endpoint.port;
    }
}
