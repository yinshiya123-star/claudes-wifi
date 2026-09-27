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
        require(yaml.contains("tls: true"));
        require(yaml.contains("dialer-proxy: Claude-前置节点"));
        require(yaml.contains("use:\n      - airport"));
        require(yaml.contains("DOMAIN-SUFFIX,claude.ai,Claude-专用出口"));
        require(yaml.contains("url: 'https://example.com/sub?token=a''b'"));
        require(yaml.contains("password: 'p''ass'"));

        String noAuth = ConfigBuilder.build(
                "https://example.com/sub", "proxy.example.com",
                Arrays.asList(new ProxyPortDetector.Endpoint(
                        8080, ProxyPortDetector.Protocol.HTTP)), "", "");
        require(noAuth.contains("type: http"));
        require(!noAuth.contains("username:"));
        require(!noAuth.contains("password:"));
        System.out.println("ConfigBuilderTest: PASS");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("配置生成结果不符合预期");
    }
}
