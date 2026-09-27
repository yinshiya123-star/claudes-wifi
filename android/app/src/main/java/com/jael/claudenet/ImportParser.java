package com.jael.claudenet;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
        /** 截图里写明的端口类型：SOCKS5 / HTTP / HTTPS；看不出来时为 null。 */
        String protocol;
        /** 每个端口的标签类型，用于同时列出 HTTP 与 SOCKS5 端口时只保留一个。 */
        final java.util.Map<Integer, String> portProtocol = new java.util.LinkedHashMap<>();

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
    // 只有 host:port（账号密码另起一行或另一列）
    private static final Pattern HOST_PORT = Pattern.compile(
            "(?<![\\w.:/@-])(" + HOST + "):(\\d{1,5})(?![\\w.:])");
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
    // 端口值旁边常见的日期、时间、流量/时长等数字，不能当成端口
    private static final Pattern NOT_A_PORT = Pattern.compile(
            "\\d{4}\\s*[-/.年]\\s*\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*日?"
                    + "|\\d{1,2}:\\d{2}(?::\\d{2})?"
                    + "|\\d+(?:\\.\\d+)?\\s*(?:gb|mb|tb|kb|mbps|gbps|%|天|日|小时|个月|月|年"
                    + "|days?|hours?|months?|years?|usd|美元|元)"
                    + "|[$¥￥]\\s*\\d+(?:\\.\\d+)?",
            Pattern.CASE_INSENSITIVE);

    private ImportParser() {}

    static Result parse(List<String> qrPayloads, String text) {
        Result result = new Result();
        for (String payload : qrPayloads) {
            parseQr(payload == null ? "" : payload.trim(), result);
        }
        List<String> lines = normalizeLines(text == null ? "" : text);
        if (result.subscriptionUrl == null) result.subscriptionUrl = findSubscription(lines);
        parseIsp(lines, result);
        applyProtocol(result);
        return result;
    }

    /**
     * 购买页常同时列出 HTTP 端口和 SOCKS5 端口：只保留 SOCKS5 那个，避免生成第二个节点；
     * 只有 HTTP 端口时标记为 HTTP。
     */
    private static void applyProtocol(Result result) {
        List<Integer> socks = new ArrayList<>();
        List<Integer> http = new ArrayList<>();
        for (int port : result.ports) {
            String type = result.portProtocol.get(port);
            if ("SOCKS5".equals(type)) socks.add(port);
            if ("HTTP".equals(type) || "HTTPS".equals(type)) http.add(port);
        }
        if (!socks.isEmpty()) {
            result.ports.retainAll(socks);
            result.protocol = "SOCKS5";
        } else if (!http.isEmpty() && http.size() == result.ports.size()) {
            result.protocol = result.portProtocol.get(http.get(0));
        }
    }

    /** 标签里写了 SOCKS 还是 HTTP；两者都写（如 “HTTP(S)/SOCKS5 port”）时看不出来。 */
    private static String protocolOfLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        boolean socks = lower.contains("socks");
        boolean http = lower.contains("http");
        if (socks && !http) return "SOCKS5";
        if (http && !socks) return lower.contains("https") ? "HTTPS" : "HTTP";
        return null;
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
                if (at.group(1) != null) {
                    // socks5://… / http://… 已写明类型
                    result.portProtocol.put(Integer.parseInt(at.group(5)),
                            protocolOfLabel(at.group(1)));
                }
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
                continue;
            }
            if (!line.contains("://")) {
                // 单独的 ip:端口（账号密码在别处），链接里的 host:port 不算
                Matcher hostPort = HOST_PORT.matcher(line);
                while (hostPort.find()) {
                    if (validHost(hostPort.group(1)) && validPort(hostPort.group(2))) {
                        if (result.host == null) result.host = hostPort.group(1);
                        if (hostPort.group(1).equals(result.host)) addPort(result, hostPort.group(2));
                    }
                }
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

    // “IP:Port”“IP/端口”这类合并标签，值通常是 host:port
    private static final Pattern HOST_PORT_LABEL = Pattern.compile(
            "^\\s*(?:ip|host|server|proxy|代理|地址|服务器)\\s*[:/]\\s*(?:port|端口)\\s*[:：=]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    // “Login:Password”“账号/密码”这类合并标签，值通常是 user:pass
    private static final Pattern USER_PASS_LABEL = Pattern.compile(
            "^\\s*(?:login|user|username|账号|帐号|用户名)\\s*[:/]\\s*(?:password|pass|密码)"
                    + "\\s*[:：=]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static void parseLabeled(List<String> lines, Result result) {
        // 先认有冒号/分行的明确标签，再用“标签 值”补漏，避免正文里的 “User guide” 抢先
        parseLabeled(lines, result, false);
        parseLabeled(lines, result, true);
    }

    private static void parseLabeled(List<String> lines, Result result, boolean spacedOnly) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String kind;
            String value;
            String labelText = line;
            Matcher hostPortLabel = HOST_PORT_LABEL.matcher(line);
            Matcher userPassLabel = USER_PASS_LABEL.matcher(line);
            Matcher m = LABELED.matcher(line);
            String[] spaced;
            if (spacedOnly) {
                if (hostPortLabel.matches() || userPassLabel.matches()
                        || m.matches() && labelKind(m.group(1)) != null
                        || (spaced = splitSpacedLabel(line)) == null
                        || !fieldEmpty(result, spaced[0])) {
                    continue;
                }
                // OCR 常丢掉冒号：“Port 50101”“Login demo”
                kind = spaced[0];
                value = spaced[1];
                labelText = spaced[2];
            } else if (hostPortLabel.matches()) {
                kind = "host";
                value = hostPortLabel.group(1);
            } else if (userPassLabel.matches()) {
                kind = "userpass";
                value = userPassLabel.group(1);
            } else if (m.matches() && labelKind(m.group(1)) != null) {
                kind = labelKind(m.group(1));
                value = m.group(2);
                labelText = m.group(1);
            } else if (isLabelOnly(line) && i + 1 < lines.size()) {
                // OCR 常把“标签”和“值”拆成两行
                kind = labelKind(line);
                value = "";
            } else {
                continue;
            }
            if (value.isEmpty() && i + 1 < lines.size() && !isLabelOnly(lines.get(i + 1))) {
                value = lines.get(++i).trim();
            }
            value = unquote(value);
            if (value.isEmpty()) continue;
            switch (kind) {
                case "host":
                    Matcher hostPort = HOST_PORT.matcher(value);
                    if (hostPort.find() && validHost(hostPort.group(1))) {
                        if (result.host == null) result.host = hostPort.group(1);
                        addPort(result, hostPort.group(2));
                    } else {
                        String host = value.split("[\\s:/]")[0];
                        if (result.host == null && validHost(host)) result.host = host;
                    }
                    break;
                case "port":
                    Matcher p = PORT_NUMBER.matcher(NOT_A_PORT.matcher(fixDigits(value)).replaceAll(" "));
                    String type = protocolOfLabel(labelText);
                    while (p.find()) {
                        addPort(result, p.group(1));
                        if (type != null && validPort(p.group(1))) {
                            result.portProtocol.put(Integer.parseInt(p.group(1)), type);
                        }
                    }
                    break;
                case "user":
                    if (result.username == null) result.username = value.split("\\s")[0];
                    break;
                case "pass":
                    if (result.password == null) result.password = value.split("\\s")[0];
                    break;
                case "userpass":
                    // “Login:Password  demo:s3cret”
                    String[] pair = value.split("\\s")[0].split(":", 2);
                    if (pair.length == 2) {
                        if (result.username == null) result.username = pair[0];
                        if (result.password == null) result.password = pair[1];
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean fieldEmpty(Result result, String kind) {
        switch (kind) {
            case "host":
                return result.host == null;
            case "port":
                return result.ports.isEmpty();
            case "user":
                return result.username == null;
            case "pass":
                return result.password == null;
            case "userpass":
                return result.username == null || result.password == null;
            default:
                return false;
        }
    }

    private static final Set<String> LABEL_WORDS = new HashSet<>(Arrays.asList(
            "ip", "address", "addr", "host", "hostname", "server", "proxy", "port", "http", "https",
            "s", "socks", "socks5", "login", "user", "username", "name", "password", "pass", "passwd",
            "pwd", "ip地址", "地址", "服务器", "主机", "代理", "代理地址", "代理ip", "代理服务器", "端口",
            "端口号", "账号", "帐号", "账户", "用户", "用户名", "密码"));

    /** 整行只由标签词组成（如 “IP address”“HTTP(S)/SOCKS5 port”），值在下一行。 */
    private static boolean isLabelOnly(String line) {
        if (labelKind(line) == null) return false;
        String cleaned = line.trim().toLowerCase(Locale.ROOT).replaceAll("[:：=]$", "");
        for (String word : cleaned.split("[\\s/()（）]+")) {
            if (!word.isEmpty() && !LABEL_WORDS.contains(word)) return false;
        }
        return true;
    }

    /** “标签 值”（无冒号）：取最长的、能识别为标签的前 1–3 个词。 */
    private static String[] splitSpacedLabel(String line) {
        String[] words = line.trim().split("\\s+");
        for (int n = Math.min(3, words.length - 1); n >= 1; n--) {
            StringBuilder label = new StringBuilder(words[0]);
            for (int k = 1; k < n; k++) label.append(' ').append(words[k]);
            String kind = labelKind(label.toString());
            if (kind == null) continue;
            StringBuilder value = new StringBuilder(words[n]);
            for (int k = n + 1; k < words.length; k++) value.append(' ').append(words[k]);
            return new String[]{kind, value.toString(), label.toString()};
        }
        return null;
    }

    /** 返回 host/port/user/pass/userpass，或 null。 */
    static String labelKind(String rawLabel) {
        String label = rawLabel.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[:：=]$", "").trim();
        if (label.isEmpty() || label.length() > 30) return null;
        // 标签里不会有数字（socks5 除外），带数字的是值
        if (label.replace("socks5", "socks").matches(".*\\d.*")) return null;
        boolean port = label.matches(".*(^|[^a-z])port([^a-z]|$).*") || label.contains("端口");
        boolean host = label.matches("(ip|ip address|ip addr|ip地址|ip 地址|host|hostname|server"
                + "|服务器|主机|地址|代理地址|代理ip|proxy|proxy ip|代理服务器)")
                || port && label.matches(".*(^|[^a-z])(ip|host|server)([^a-z]|$).*");
        boolean user = label.matches(".*(^|[^a-z])(login|user|username|user name)([^a-z]|$).*")
                || label.matches(".*(用户名|账号|帐号|账户).*") || label.equals("用户");
        boolean pass = label.matches(".*(^|[^a-z])(password|passwd|pass|pwd)([^a-z]|$).*")
                || label.contains("密码");
        if (host) return "host";
        if (user && pass) return "userpass";
        if (port) return "port";
        if (user) return "user";
        if (pass) return "pass";
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
