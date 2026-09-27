package com.jael.claudenet;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从拍照/截图识别出的二维码内容和文字中提取机场订阅与住宅 ISP 信息。
 *
 * <p>ISP 支持的形态与桌面版 isp_parse 一致：
 * <ol>
 *   <li>proxy-seller 标准行：203.0.113.7:50101:user:pass</li>
 *   <li>代理 URL：socks5://user:pass@203.0.113.7:50101</li>
 *   <li>反过来的：user:pass@203.0.113.7:50101</li>
 *   <li>多行英文标签：IP: … / HTTP port: … / Login: … / Password: …</li>
 *   <li>中文标签：IP地址：… 端口：… 账号：… 密码：…</li>
 *   <li>YAML 片段：server: '…' port: … username: '…'</li>
 *   <li>空格/Tab 分隔：203.0.113.7 50101 user pass</li>
 * </ol>
 * 识别结果只用于预填，界面上必须让用户确认后才写入。
 */
final class ImportParser {
    static final class Result {
        String subscriptionUrl;
        /** 二维码是单个节点（ss:// 等）而不是订阅时记录其协议名，用于提示用户。 */
        String nodeScheme;
        String host;
        final List<Integer> ports = new ArrayList<>();
        String username;
        String password;

        boolean hasSubscription() {
            return subscriptionUrl != null;
        }

        boolean hasIsp() {
            return host != null || !ports.isEmpty() || username != null || password != null;
        }

        boolean isEmpty() {
            return !hasSubscription() && !hasIsp() && nodeScheme == null;
        }
    }

    private static final String IPV4 = "(?:\\d{1,3}\\.){3}\\d{1,3}";
    private static final String HOSTNAME = "(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}";
    private static final String HOST = "(?:" + IPV4 + "|" + HOSTNAME + ")";
    private static final String CRED = "[^\\s:@/]+";

    // socks5://user:pass@host:port 或 user:pass@host:port（scheme 可省略）
    private static final Pattern AT_FORM = Pattern.compile(
            "(?:(socks5h?|https?)://)?(" + CRED + "):([^\\s@]+)@(" + HOST + "):(\\d{1,5})",
            Pattern.CASE_INSENSITIVE);
    // host:port:user:pass
    private static final Pattern COLON_FORM = Pattern.compile(
            "(?<![\\w.])(" + HOST + "):(\\d{1,5}):(" + CRED + "):(\\S+)");
    // host port user pass（空格/Tab 分隔）
    private static final Pattern SPACE_FORM = Pattern.compile(
            "^(" + IPV4 + ")[ \\t]+(\\d{1,5})[ \\t]+(\\S+)[ \\t]+(\\S+)$");
    private static final Pattern IP_ONLY = Pattern.compile("(?<![\\w.])(" + IPV4 + ")(?![\\w.])");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>，。；]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTALL_CONFIG = Pattern.compile(
            "(?:clash|clashmeta|clash-meta)://install-config\\?(?:[^\\s]*&)?url=([^&\\s]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_URI = Pattern.compile(
            "^(ss|ssr|vmess|vless|trojan|hysteria2?|hy2|tuic|wireguard)://",
            Pattern.CASE_INSENSITIVE);
    // 标签：值（中英文冒号或空白分隔）；值为空表示值在下一行
    private static final Pattern LABELED = Pattern.compile(
            "^\\s*([A-Za-z0-9\\u4e00-\\u9fa5 _/()（）-]{1,24}?)\\s*[:：=]\\s*(.*?)\\s*$");
    private static final Pattern PORT_NUMBER = Pattern.compile("(?<!\\d)(\\d{2,5})(?!\\d)");

    private ImportParser() {}

    static Result parse(List<String> qrPayloads, String text) {
        Result result = new Result();
        for (String payload : qrPayloads) {
            parseQr(payload == null ? "" : payload.trim(), result);
        }
        List<String> lines = normalizeLines(text == null ? "" : text);
        if (result.subscriptionUrl == null) result.subscriptionUrl = findSubscription(lines);
        parseIsp(lines, result);
        return result;
    }

    private static void parseQr(String payload, Result result) {
        if (payload.isEmpty()) return;
        Matcher install = INSTALL_CONFIG.matcher(payload);
        if (install.find()) {
            if (result.subscriptionUrl == null) result.subscriptionUrl = urlDecode(install.group(1));
            return;
        }
        Matcher node = NODE_URI.matcher(payload);
        if (node.find()) {
            if (result.nodeScheme == null) result.nodeScheme = node.group(1).toLowerCase(Locale.ROOT);
            return;
        }
        if (isHttpUrl(payload) && !looksLikeProxyUrl(payload)) {
            if (result.subscriptionUrl == null) result.subscriptionUrl = payload;
            return;
        }
        // 二维码里也可能是 ISP 文本
        List<String> lines = normalizeLines(payload);
        parseIsp(lines, result);
    }

    // ---------------- 订阅 ----------------

    private static String findSubscription(List<String> lines) {
        List<String> joined = joinWrappedUrls(lines);
        String best = null;
        int bestScore = 0;
        for (String line : joined) {
            Matcher install = INSTALL_CONFIG.matcher(line);
            if (install.find()) return urlDecode(install.group(1));
            Matcher m = URL.matcher(line);
            while (m.find()) {
                String url = trimUrl(m.group());
                if (looksLikeProxyUrl(url)) continue;
                int score = subscriptionScore(url);
                if (score > bestScore) {
                    bestScore = score;
                    best = url;
                }
            }
        }
        return best;
    }

    /** 订阅链接通常带 token/subscribe 等特征；普通官网链接不算。 */
    private static int subscriptionScore(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        int score = 0;
        if (lower.contains("token=")) score += 3;
        if (lower.contains("subscribe") || lower.contains("/sub") || lower.contains("sub?")
                || lower.contains("/link/") || lower.contains("/api/v1/client")) score += 3;
        if (lower.contains("clash") || lower.contains("flag=")) score += 1;
        if (lower.indexOf('?') > 0 || lower.length() > 40) score += 1;
        return score >= 3 ? score : 0;
    }

    /** OCR 会把长链接折成多行：以链接结尾的行后面紧跟无空格的续行时拼起来。 */
    private static List<String> joinWrappedUrls(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = URL.matcher(line);
            boolean endsWithUrl = false;
            while (m.find()) endsWithUrl = m.end() == line.length();
            StringBuilder current = new StringBuilder(line);
            while (endsWithUrl && i + 1 < lines.size()
                    && lines.get(i + 1).matches("[A-Za-z0-9%&=?/_.\\-~+#]{4,}")
                    && !lines.get(i + 1).matches("\\d{1,5}")) {
                current.append(lines.get(++i));
            }
            out.add(current.toString());
        }
        return out;
    }

    // ---------------- 住宅 ISP ----------------

    private static void parseIsp(List<String> lines, Result result) {
        for (String line : lines) {
            Matcher at = AT_FORM.matcher(line);
            if (at.find() && validHost(at.group(4)) && validPort(at.group(5))) {
                fill(result, at.group(4), at.group(5), at.group(2), at.group(3));
                continue;
            }
            Matcher colon = COLON_FORM.matcher(line);
            if (colon.find() && validHost(colon.group(1)) && validPort(colon.group(2))) {
                fill(result, colon.group(1), colon.group(2), colon.group(3), colon.group(4));
                continue;
            }
            Matcher space = SPACE_FORM.matcher(line);
            if (space.find() && validHost(space.group(1)) && validPort(space.group(2))) {
                fill(result, space.group(1), space.group(2), space.group(3), space.group(4));
            }
        }
        parseLabeled(lines, result);
        if (result.host == null && (!result.ports.isEmpty() || result.username != null)) {
            // 有端口/账号但没有标注 IP 时，取文字里唯一的 IPv4
            Set<String> ips = new LinkedHashSet<>();
            for (String line : lines) {
                Matcher m = IP_ONLY.matcher(line);
                while (m.find()) if (validHost(m.group(1))) ips.add(m.group(1));
            }
            if (ips.size() == 1) result.host = ips.iterator().next();
        }
    }

    private static void parseLabeled(List<String> lines, Result result) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = LABELED.matcher(lines.get(i));
            String label;
            String value;
            if (m.matches()) {
                label = m.group(1);
                value = m.group(2);
            } else if (labelKind(lines.get(i)) != null && i + 1 < lines.size()) {
                // OCR 常把“标签”和“值”拆成两行
                label = lines.get(i);
                value = "";
            } else {
                continue;
            }
            String kind = labelKind(label);
            if (kind == null) continue;
            if (value.isEmpty() && i + 1 < lines.size() && labelKind(lines.get(i + 1)) == null) {
                value = lines.get(++i).trim();
            }
            value = unquote(value);
            if (value.isEmpty()) continue;
            switch (kind) {
                case "host":
                    String host = value.split("[\\s:]")[0];
                    if (result.host == null && validHost(host)) result.host = host;
                    break;
                case "port":
                    Matcher p = PORT_NUMBER.matcher(fixDigits(value));
                    while (p.find()) addPort(result, p.group(1));
                    break;
                case "user":
                    if (result.username == null) result.username = value.split("\\s")[0];
                    break;
                case "pass":
                    if (result.password == null) result.password = value.split("\\s")[0];
                    break;
                default:
                    break;
            }
        }
    }

    /** 返回 host/port/user/pass，或 null。 */
    static String labelKind(String rawLabel) {
        String label = rawLabel.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[:：=]$", "").trim();
        if (label.isEmpty() || label.length() > 24) return null;
        if (label.matches("(ip|ip address|ip地址|ip 地址|host|hostname|server|服务器|主机|地址|代理地址|代理ip)")) {
            return "host";
        }
        if (label.matches("((http|https|socks5?|socks)\\s*)?(port|端口)(\\s*\\((http|https|socks5?)\\))?"
                + "|端口号|(http|https|socks5?)\\s*端口")) {
            return "port";
        }
        if (label.matches("(login|user|username|user name|用户名|账号|帐号|账户|用户)")) return "user";
        if (label.matches("(password|pass|passwd|pwd|密码)")) return "pass";
        return null;
    }

    private static void fill(Result result, String host, String port, String user, String pass) {
        if (result.host == null) result.host = host;
        addPort(result, port);
        if (result.username == null) result.username = user;
        if (result.password == null) result.password = pass;
    }

    private static void addPort(Result result, String raw) {
        if (!validPort(raw)) return;
        int port = Integer.parseInt(raw);
        if (!result.ports.contains(port)) result.ports.add(port);
    }

    // ---------------- 工具 ----------------

    private static List<String> normalizeLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.replace(' ', ' ').replace('　', ' ')
                    .replace('：', ':').trim();
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    /** OCR 在数字里常把 O/o 认成 0、l/I 认成 1。只用于纯数字上下文。 */
    private static String fixDigits(String value) {
        return value.replaceAll("(?<=\\d)[Oo]|[Oo](?=\\d)", "0")
                .replaceAll("(?<=\\d)[lI|]|[lI|](?=\\d)", "1");
    }

    private static boolean validHost(String host) {
        if (host == null || host.isEmpty()) return false;
        if (host.matches(IPV4)) {
            for (String part : host.split("\\.")) {
                if (Integer.parseInt(part) > 255) return false;
            }
            return !host.startsWith("0.") && !host.startsWith("127.");
        }
        return host.matches(HOSTNAME);
    }

    private static boolean validPort(String raw) {
        if (raw == null || !raw.matches("\\d{1,5}")) return false;
        int port = Integer.parseInt(raw);
        return port >= 1 && port <= 65535;
    }

    private static boolean isHttpUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return (lower.startsWith("https://") || lower.startsWith("http://")) && !value.contains(" ");
    }

    /** http://user:pass@host:port 这种是 ISP 代理地址，不是订阅。 */
    private static boolean looksLikeProxyUrl(String url) {
        return AT_FORM.matcher(url).find();
    }

    private static String trimUrl(String url) {
        return url.replaceAll("[)\\]}>.,;!]+$", "");
    }

    private static String unquote(String value) {
        String v = value.trim();
        if (v.length() >= 2 && (v.startsWith("'") && v.endsWith("'")
                || v.startsWith("\"") && v.endsWith("\""))) {
            v = v.substring(1, v.length() - 1);
        }
        return v.trim();
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            return value;
        }
    }
}
