package com.jael.claudenet;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** One-shot loopback HTTP server used by CMFA's install-config deep link. */
final class LocalConfigServer implements Closeable {
    private static final long LIFETIME_MILLIS = 5 * 60 * 1000L;
    private final byte[] config;
    private final ServerSocket server;
    private final String path;
    private final Thread worker;
    private volatile boolean closed;

    LocalConfigServer(String yaml) throws IOException {
        config = yaml.getBytes(StandardCharsets.UTF_8);
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(1000);
        path = "/" + randomToken() + "/claude-net.yaml";
        worker = new Thread(this::serve, "claude-net-config-server");
        worker.setDaemon(true);
        worker.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getLocalPort() + path;
    }

    private void serve() {
        long deadline = System.currentTimeMillis() + LIFETIME_MILLIS;
        while (!closed && System.currentTimeMillis() < deadline) {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(3000);
                if (handle(socket)) {
                    close();
                    return;
                }
            } catch (SocketTimeoutException ignored) {
                // Wake periodically to enforce the lifetime.
            } catch (IOException ignored) {
                if (!closed) close();
                return;
            }
        }
        close();
    }

    private boolean handle(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String request = reader.readLine();
        if (request == null) return false;
        String[] parts = request.split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String requestedPath = parts.length > 1 ? parts[1] : "";
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // Consume headers before responding.
        }

        if (!requestedPath.equals(path) || !(method.equals("GET") || method.equals("HEAD"))) {
            writeResponse(socket.getOutputStream(), 404, "text/plain", new byte[0], false);
            return false;
        }
        boolean sendBody = method.equals("GET");
        writeResponse(socket.getOutputStream(), 200, "application/x-yaml; charset=utf-8",
                config, sendBody);
        return sendBody;
    }

    private static void writeResponse(
            OutputStream output, int status, String type, byte[] body, boolean sendBody)
            throws IOException {
        String reason = status == 200 ? "OK" : "Not Found";
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        if (sendBody) output.write(body);
        output.flush();
    }

    private static String randomToken() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        StringBuilder token = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) token.append(String.format("%02x", value & 0xff));
        return token.toString();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}
