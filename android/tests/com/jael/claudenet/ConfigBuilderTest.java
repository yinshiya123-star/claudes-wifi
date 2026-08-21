package com.jael.claudenet;

public final class ConfigBuilderTest {
    public static void main(String[] args) {
        String yaml = ConfigBuilder.build(
                "https://example.com/sub?token=a'b",
                "203.0.113.7",
                50101,
                "demo",
                "p'ass",
                "SOCKS5");
        require(yaml.contains("type: socks5"));
        require(yaml.contains("port: 50101"));
        require(yaml.contains("dialer-proxy: Claude-前置节点"));
        require(yaml.contains("use:\n      - airport"));
        require(yaml.contains("DOMAIN-SUFFIX,claude.ai,Claude-专用出口"));
        require(yaml.contains("url: 'https://example.com/sub?token=a''b'"));
        require(yaml.contains("password: 'p''ass'"));

        String noAuth = ConfigBuilder.build(
                "https://example.com/sub", "proxy.example.com", 8080, "", "", "HTTP");
        require(noAuth.contains("type: http"));
        require(!noAuth.contains("username:"));
        require(!noAuth.contains("password:"));
        System.out.println("ConfigBuilderTest: PASS");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("配置生成结果不符合预期");
    }
}
