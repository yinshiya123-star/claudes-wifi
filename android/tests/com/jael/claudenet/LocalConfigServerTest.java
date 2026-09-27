package com.jael.claudenet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class LocalConfigServerTest {
    public static void main(String[] args) throws Exception {
        String expected = "mode: rule\nproxies: []\n";
        LocalConfigServer server = new LocalConfigServer(expected);
        URL url = new URL(server.url());
        require(url.getHost().equals("127.0.0.1"));
        require(url.getPath().endsWith("/claude-net.yaml"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        require(connection.getResponseCode() == 200);
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder actual = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) actual.append(line).append('\n');
        require(actual.toString().equals(expected));
        server.close();
        System.out.println("LocalConfigServerTest: PASS");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("本机自动导入服务测试失败");
    }
}
