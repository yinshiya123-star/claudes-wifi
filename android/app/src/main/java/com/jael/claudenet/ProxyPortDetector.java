package com.jael.claudenet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class ProxyPortDetector {
    enum Protocol {
        SOCKS5, HTTPS, HTTP, UNKNOWN
    }

    static final class Endpoint {
        final int port;
        final Protocol protocol;

        Endpoint(int port, Protocol protocol) {
            this.port = port;
            this.protocol = protocol;
        }
    }

    private static final int TIMEOUT_MS = 3500;

    private ProxyPortDetector() {}

    static List<Integer> parsePorts(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请至少填写一个 ISP 端口。");
        }
        String[] parts = normalized.split("[\\s,，;；]+");
        Set<Integer> unique = new LinkedHashSet<>();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            final int port;
            try {
                port = Integer.parseInt(part);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("端口“" + part + "”不是有效数字。");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("端口“" + part + "”不在 1–65535 范围内。");
            }
            unique.add(port);
        }
        return new ArrayList<>(unique);
    }

    static List<Endpoint> detectAll(
            String host, List<Integer> ports, String username, String password) throws Exception {
        int workers = Math.min(4, Math.max(1, ports.size()));
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Endpoint>> futures = new ArrayList<>();
            for (int port : ports) {
                futures.add(executor.submit(new Callable<Endpoint>() {
                    @Override
                    public Endpoint call() {
                        return detectOne(host, port, username, password);
                    }
                }));
            }
            List<Endpoint> results = new ArrayList<>();
            for (Future<Endpoint> future : futures) results.add(future.get());
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    static Endpoint detectOne(String host, int port, String username, String password) {
        if (isSocks5(host, port)) return new Endpoint(port, Protocol.SOCKS5);
        if (isHttpProxy(host, port, username, password, true)) {
            return new Endpoint(port, Protocol.HTTPS);
        }
        if (isHttpProxy(host, port, username, password, false)) {
            return new Endpoint(port, Protocol.HTTP);
        }
        return new Endpoint(port, Protocol.UNKNOWN);
    }

    private static boolean isSocks5(String host, int port) {
        try (Socket socket = connect(host, port)) {
            OutputStream output = socket.getOutputStream();
            // 同时报出“无需认证”和“用户名/密码”两种方式，认证型 SOCKS5 也能被识别。
            output.write(new byte[]{0x05, 0x02, 0x00, 0x02});
            output.flush();
            int version = socket.getInputStream().read();
            int method = socket.getInputStream().read();
            return version == 0x05 && method >= 0 && method != 0xff;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isHttpProxy(
            String host, int port, String username, String password, boolean tls) {
        Socket raw = null;
        Socket socket = null;
        try {
            raw = connect(host, port);
            if (tls) {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new SecureRandom());
                SSLSocket ssl = (SSLSocket) context.getSocketFactory()
                        .createSocket(raw, host, port, true);
                ssl.setSoTimeout(TIMEOUT_MS);
                ssl.startHandshake();
                socket = ssl;
                raw = null;
            } else {
                socket = raw;
                raw = null;
            }

            StringBuilder request = new StringBuilder();
            request.append("CONNECT www.gstatic.com:443 HTTP/1.1\r\n")
                    .append("Host: www.gstatic.com:443\r\n")
                    .append("Proxy-Connection: close\r\n");
            if (!username.isEmpty()) {
                String credentials = username + ":" + password;
                request.append("Proxy-Authorization: Basic ")
                        .append(Base64.getEncoder().encodeToString(
                                credentials.getBytes(StandardCharsets.UTF_8)))
                        .append("\r\n");
            }
            request.append("\r\n");
            socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.US_ASCII));
            String status = reader.readLine();
            return status != null && status.startsWith("HTTP/");
        } catch (Exception ignored) {
            return false;
        } finally {
            closeQuietly(socket);
            closeQuietly(raw);
        }
    }

    private static Socket connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        return socket;
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
