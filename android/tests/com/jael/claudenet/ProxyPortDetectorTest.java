package com.jael.claudenet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class ProxyPortDetectorTest {
    public static void main(String[] args) throws Exception {
        List<Integer> parsed = ProxyPortDetector.parsePorts("1080, 443；8080\n1080");
        require(parsed.equals(Arrays.asList(1080, 443, 8080)));

        try (ServerSocket server = new ServerSocket(0)) {
            Thread mock = new Thread(() -> socksReply(server));
            mock.start();
            ProxyPortDetector.Endpoint result = ProxyPortDetector.detectOne(
                    "127.0.0.1", server.getLocalPort(), "", "");
            require(result.protocol == ProxyPortDetector.Protocol.SOCKS5);
            mock.join();
        }

        try (ServerSocket server = new ServerSocket(0)) {
            Thread mock = new Thread(() -> httpReply(server));
            mock.start();
            ProxyPortDetector.Endpoint result = ProxyPortDetector.detectOne(
                    "127.0.0.1", server.getLocalPort(), "demo", "secret");
            require(result.protocol == ProxyPortDetector.Protocol.HTTP);
            mock.join();
        }
        System.out.println("ProxyPortDetectorTest: PASS");
    }

    private static void socksReply(ServerSocket server) {
        try (Socket socket = server.accept()) {
            socket.getInputStream().read(new byte[4]);
            socket.getOutputStream().write(new byte[]{0x05, 0x02});
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static void httpReply(ServerSocket server) {
        try {
            // SOCKS5 探测会先建立一次连接。
            try (Socket ignored = server.accept()) {
                ignored.getInputStream().read(new byte[4]);
            }
            try (Socket socket = server.accept()) {
                // HTTPS 探测先到达，读取 TLS ClientHello 后关闭。
                socket.getInputStream().read(new byte[256]);
            }
            try (Socket socket = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.US_ASCII));
                while (true) {
                    String line = reader.readLine();
                    if (line == null || line.isEmpty()) break;
                }
                socket.getOutputStream().write(
                        "HTTP/1.1 407 Proxy Authentication Required\r\n\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
            }
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("端口检测结果不符合预期");
    }
}
