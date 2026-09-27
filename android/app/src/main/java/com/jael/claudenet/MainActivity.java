package com.jael.claudenet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int CREATE_CONFIG = 101;
    private static final int TAKE_PHOTO = 102;
    private static final int PICK_IMAGE = 103;
    private static final String PREFS = "claude_net_android";
    private static final String CLIENT_RELEASES =
            "https://github.com/MetaCubeX/ClashMetaForAndroid/releases";
    private static final String CMFA_PACKAGE = "com.github.metacubex.clash.meta";
    private static final String STATE_PENDING_CONFIG = "pending_config";

    private EditText subscription;
    private EditText host;
    private EditText port;
    private EditText username;
    private EditText password;
    private Spinner protocol;
    private TextView status;
    private String pendingConfig;
    private LocalConfigServer configServer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Claude 网络配置助手");
        setContentView(createContent());
        restore();
        if (state != null) pendingConfig = state.getString(STATE_PENDING_CONFIG);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        // 选择导出位置期间 Activity 可能被系统重建，需保留待写入的配置
        if (pendingConfig != null) state.putString(STATE_PENDING_CONFIG, pendingConfig);
    }

    private View createContent() {
        int padding = dp(20);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, dp(28), padding, dp(36));
        root.setBackgroundColor(Color.rgb(246, 248, 252));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Claude 网络配置助手", 27, Color.rgb(20, 45, 88));
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);
        TextView byline = text("原项目作者：Jael · Android 本地配置版", 14,
                Color.rgb(80, 95, 120));
        byline.setPadding(0, dp(5), 0, dp(18));
        root.addView(byline);

        TextView note = text(
                "填写后生成 Mihomo/Clash Meta 配置。数据只保存在本机；本应用不上传账号密码，也不内置 VPN 内核。",
                15, Color.rgb(40, 53, 72));
        note.setBackgroundColor(Color.rgb(228, 237, 252));
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(note, matchWrap(dp(14)));

        root.addView(section("拍照 / 截图识别"));
        TextView scanHint = text("拍机场后台的订阅二维码或链接、住宅 ISP 购买页的 IP/端口/账号/密码，"
                + "自动识别并填入。识别在手机本地完成，图片和识别出的文字不会上传"
                + "（识别组件可能向 Google 发送不含图片和文字的匿名使用统计）。",
                14, Color.rgb(75, 82, 94));
        scanHint.setPadding(0, 0, 0, dp(8));
        root.addView(scanHint);
        LinearLayout scanRow = new LinearLayout(this);
        scanRow.setOrientation(LinearLayout.HORIZONTAL);
        Button takePhoto = button("拍照识别", Color.rgb(196, 110, 40));
        takePhoto.setOnClickListener(v -> takePhoto());
        Button pickImage = button("选截图识别", Color.rgb(196, 110, 40));
        pickImage.setOnClickListener(v -> pickImage());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        halfRight.leftMargin = dp(8);
        scanRow.addView(takePhoto, half);
        scanRow.addView(pickImage, halfRight);
        root.addView(scanRow, matchWrap(dp(4)));

        root.addView(section("机场订阅"));
        subscription = field("https://… 订阅地址", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(subscription, matchWrap(dp(10)));

        root.addView(section("美国住宅 ISP"));
        host = field("IP 地址或域名", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(host, matchWrap(dp(8)));
        port = field("端口（多个用空格、逗号或换行分隔）",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        port.setSingleLine(false);
        port.setMinLines(2);
        root.addView(port, matchWrap(dp(8)));
        username = field("账号（白名单授权可留空）", InputType.TYPE_CLASS_TEXT);
        root.addView(username, matchWrap(dp(8)));
        password = field("密码（可留空）", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, matchWrap(dp(8)));

        protocol = new Spinner(this);
        String[] protocols = {"自动识别（推荐）", "SOCKS5", "HTTPS", "HTTP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, protocols);
        protocol.setAdapter(adapter);
        root.addView(protocol, matchWrap(dp(9)));

        Button detect = button("检测所有端口协议", Color.rgb(108, 75, 150));
        detect.setOnClickListener(v -> {
            if (protocol.getSelectedItemPosition() != 0) {
                showStatus("已手动指定协议，无需检测。", true);
                return;
            }
            // 检测结果由 withConfig 显示
            withConfig(config -> {});
        });
        root.addView(detect, matchWrap(dp(16)));

        Button save = button("保存到本机", Color.rgb(72, 92, 122));
        save.setOnClickListener(v -> {
            if (readInput() != null) {
                save();
                showStatus("✓ 已保存。账号信息只在本机应用数据中。", true);
            }
        });
        root.addView(save, matchWrap(dp(9)));

        Button autoImport = button("一键导入 Clash Meta", Color.rgb(27, 95, 170));
        autoImport.setOnClickListener(v -> autoImportConfig());
        root.addView(autoImport, matchWrap(dp(9)));

        Button export = button("导出 YAML（备用）", Color.rgb(72, 92, 122));
        export.setOnClickListener(v -> exportConfig());
        root.addView(export, matchWrap(dp(9)));

        Button copy = button("复制 YAML 到剪贴板", Color.rgb(39, 122, 109));
        copy.setOnClickListener(v -> copyConfig());
        root.addView(copy, matchWrap(dp(9)));

        Button client = button("打开兼容客户端发布页", Color.rgb(108, 75, 150));
        client.setOnClickListener(v -> openClientReleases());
        root.addView(client, matchWrap(dp(9)));

        status = text("点“一键导入”会打开 Clash Meta 的添加页面；确认添加并开启 VPN 后，"
                + "所有国外流量（不只是 Claude）都会先经机场、再从美国住宅 IP 出去，国内网站直连；"
                + "客户端切到“全局”模式则连国内网站也走住宅 IP。连不上时，"
                + "到“Claude-前置节点”组里手动换一个机场节点。", 14,
                Color.rgb(75, 82, 94));
        status.setPadding(dp(12), dp(13), dp(12), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status, matchWrap(0));
        return scroll;
    }

    private void autoImportConfig() {
        withConfig(this::openImport);
    }

    private void openImport(String config) {
        save();
        if (configServer != null) configServer.close();
        try {
            configServer = new LocalConfigServer(config);
            Uri deepLink = new Uri.Builder()
                    .scheme("clashmeta")
                    .authority("install-config")
                    .appendQueryParameter("url", configServer.url())
                    .build();
            Intent intent = new Intent(Intent.ACTION_VIEW, deepLink);
            intent.setPackage(CMFA_PACKAGE);
            startActivity(intent);
            showStatus("✓ 配置已发送到 Clash Meta，请在客户端确认添加。", true);
        } catch (ActivityNotFoundException error) {
            if (configServer != null) configServer.close();
            configServer = null;
            showStatus("未安装 Clash Meta for Android，正在打开官方下载页。", false);
            openClientReleases();
        } catch (Exception error) {
            if (configServer != null) configServer.close();
            configServer = null;
            showStatus("自动导入失败：" + error.getMessage(), false);
        }
    }

    private void openClientReleases() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CLIENT_RELEASES)));
        } catch (ActivityNotFoundException error) {
            showStatus("未找到可打开网页的浏览器：" + CLIENT_RELEASES, false);
        }
    }

    private void takePhoto() {
        CaptureProvider.clear(this);
        Uri output = CaptureProvider.captureUri();
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, output);
        intent.setClipData(ClipData.newRawUri("", output));
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, TAKE_PHOTO);
        } catch (ActivityNotFoundException | SecurityException error) {
            showStatus("无法打开相机，请改用“选截图识别”。", false);
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, PICK_IMAGE);
        } catch (ActivityNotFoundException error) {
            showStatus("系统没有可用的图片选择器。", false);
        }
    }

    private void recognize(Uri image, boolean isCapture) {
        showStatus("正在识别图片……", true);
        ImageRecognizer.recognize(this, image, new ImageRecognizer.Callback() {
            @Override
            public void onResult(List<String> qrPayloads, String text) {
                if (isCapture) CaptureProvider.clear(MainActivity.this);
                if (isFinishing() || isDestroyed()) return;
                showImportResult(ImportParser.parse(qrPayloads, text));
            }

            @Override
            public void onError(Exception error) {
                if (isCapture) CaptureProvider.clear(MainActivity.this);
                if (isFinishing() || isDestroyed()) return;
                showStatus("识别失败：" + error.getMessage(), false);
            }
        });
    }

    /** 识别结果先回显给用户核对，确认后才填入（任何识别都会出错）。 */
    private void showImportResult(ImportParser.Result result) {
        if (result.isEmpty()) {
            showStatus("没有识别到订阅链接或 ISP 信息。请确保图片清晰、文字完整，"
                    + "或直接手动填写。", false);
            return;
        }
        StringBuilder message = new StringBuilder();
        if (result.hasSubscription()) {
            message.append("机场订阅：\n").append(result.subscriptionUrl).append("\n\n");
        } else if (result.nodeScheme != null) {
            message.append("二维码是单个 ").append(result.nodeScheme)
                    .append(" 节点，不是订阅链接，无法使用。请在机场后台找“订阅链接”或"
                            + "“Clash 订阅”的二维码/链接。\n\n");
        }
        if (result.hasIsp()) {
            message.append("住宅 ISP：\n");
            if (result.host != null) message.append("地址：").append(result.host).append('\n');
            if (!result.ports.isEmpty()) {
                message.append("端口：").append(joinPorts(result.ports)).append('\n');
            }
            if (result.username != null) {
                message.append("账号：").append(result.username).append('\n');
            }
            if (result.password != null) {
                message.append("密码：").append(mask(result.password)).append('\n');
            }
        }
        message.append("\n请核对无误后填入；没识别到的项保持原样。");
        boolean fillable = result.hasSubscription() || result.hasIsp();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(fillable ? "识别结果" : "未找到可填入的信息")
                .setMessage(message.toString().trim())
                .setNegativeButton(fillable ? "取消" : "知道了", null);
        if (fillable) {
            dialog.setPositiveButton("填入", (d, which) -> {
                if (result.subscriptionUrl != null) subscription.setText(result.subscriptionUrl);
                if (result.host != null) host.setText(result.host);
                if (!result.ports.isEmpty()) port.setText(joinPorts(result.ports));
                if (result.username != null) username.setText(result.username);
                if (result.password != null) password.setText(result.password);
                showStatus("✓ 已填入识别结果，请核对后再导入。", true);
            });
        }
        dialog.show();
        showStatus("识别完成，请在弹窗中核对。", true);
    }

    private static String joinPorts(List<Integer> ports) {
        StringBuilder out = new StringBuilder();
        for (int value : ports) {
            if (out.length() > 0) out.append(' ');
            out.append(value);
        }
        return out.toString();
    }

    private static String mask(String secret) {
        if (secret.length() <= 2) return "**";
        return secret.charAt(0) + "****" + secret.charAt(secret.length() - 1)
                + "（共 " + secret.length() + " 位）";
    }

    private void exportConfig() {
        withConfig(config -> {
            pendingConfig = config;
            save();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/x-yaml");
            intent.putExtra(Intent.EXTRA_TITLE, "claude-net-android.yaml");
            try {
                startActivityForResult(intent, CREATE_CONFIG);
            } catch (ActivityNotFoundException error) {
                pendingConfig = null;
                showStatus("系统没有可用的文件选择器，请改用“复制 YAML 到剪贴板”。", false);
            }
        });
    }

    private void copyConfig() {
        withConfig(config -> {
            save();
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                showStatus("无法访问系统剪贴板。", false);
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("claude-net.yaml", config));
            showStatus("✓ YAML 已复制。注意：剪贴板可能被输入法或其他应用读取。", true);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TAKE_PHOTO) {
            if (resultCode == RESULT_OK && CaptureProvider.captureFile(this).length() > 0) {
                recognize(CaptureProvider.captureUri(), true);
            } else {
                CaptureProvider.clear(this);
            }
            return;
        }
        if (requestCode == PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                recognize(data.getData(), false);
            }
            return;
        }
        if (requestCode != CREATE_CONFIG) return;
        String config = pendingConfig;
        pendingConfig = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (config == null) {
            showStatus("导出失败：配置已失效，请重新点击导出。", false);
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("无法打开目标文件");
            output.write(config.getBytes(StandardCharsets.UTF_8));
            showStatus("✓ 配置已导出。请在兼容客户端中导入该 YAML。", true);
        } catch (Exception error) {
            showStatus("导出失败：" + error.getMessage(), false);
        }
    }

    @Override
    protected void onDestroy() {
        if (configServer != null) configServer.close();
        super.onDestroy();
    }

    private InputData readInput() {
        String url = subscription.getText().toString().trim();
        String server = host.getText().toString().trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            showStatus("订阅地址必须以 https:// 或 http:// 开头。", false);
            return null;
        }
        String user = username.getText().toString().trim();
        String secret = password.getText().toString();
        if (hasControlChar(url) || hasControlChar(user) || hasControlChar(secret)) {
            showStatus("订阅地址、账号或密码中包含换行等非法字符。", false);
            return null;
        }
        if (server.isEmpty() || server.contains(" ") || server.contains("/")
                || hasControlChar(server)) {
            showStatus("请填写有效的 ISP IP 地址或域名。", false);
            return null;
        }
        List<Integer> ports;
        try {
            ports = ProxyPortDetector.parsePorts(port.getText().toString());
        } catch (IllegalArgumentException error) {
            showStatus(error.getMessage(), false);
            return null;
        }
        return new InputData(url, server, ports, user, secret);
    }

    private static boolean hasControlChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return true;
        }
        return false;
    }

    private void withConfig(ConfigAction action) {
        InputData input = readInput();
        if (input == null) return;
        int selection = protocol.getSelectedItemPosition();
        if (selection != 0) {
            ProxyPortDetector.Protocol selected = ProxyPortDetector.Protocol.valueOf(
                    protocol.getSelectedItem().toString());
            List<ProxyPortDetector.Endpoint> endpoints = new ArrayList<>();
            for (int number : input.ports) {
                endpoints.add(new ProxyPortDetector.Endpoint(number, selected));
            }
            action.run(ConfigBuilder.build(input.url, input.host, endpoints,
                    input.username, input.password));
            return;
        }

        showStatus("正在并行检测端口协议，请稍候……", true);
        new Thread(() -> {
            try {
                List<ProxyPortDetector.Endpoint> detected = ProxyPortDetector.detectAll(
                        input.host, input.ports, input.username, input.password);
                boolean anyUnknown = false;
                StringBuilder result = new StringBuilder();
                for (ProxyPortDetector.Endpoint endpoint : detected) {
                    if (result.length() > 0) result.append("；");
                    result.append(endpoint.port).append(" → ").append(endpoint.protocol.name());
                    if (endpoint.protocol == ProxyPortDetector.Protocol.UNKNOWN) anyUnknown = true;
                }
                boolean guessed = anyUnknown;
                runOnUiThread(() -> {
                    // 检测需要数秒，期间用户可能已离开页面
                    if (isFinishing() || isDestroyed()) return;
                    // 手机直连美国住宅 IP 常被阻断，检测不到不代表不可用：
                    // 未识别的端口同时生成 SOCKS5/HTTP 节点，经机场连通后由客户端自动选用
                    showStatus(guessed
                            ? "检测结果：" + result + "。未识别的端口多半是手机直连不到住宅 IP，"
                                    + "已同时按 SOCKS5 和 HTTP 生成，连上机场后客户端会自动选用能通的那个。"
                            : "检测结果：" + result, true);
                    action.run(ConfigBuilder.build(input.url, input.host, detected,
                            input.username, input.password));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showStatus("检测失败：" + error.getMessage() + "。可手动选择协议后重试。", false);
                });
            }
        }, "proxy-port-detector").start();
    }

    private interface ConfigAction {
        void run(String config);
    }

    private static final class InputData {
        final String url;
        final String host;
        final List<Integer> ports;
        final String username;
        final String password;

        InputData(String url, String host, List<Integer> ports, String username, String password) {
            this.url = url;
            this.host = host;
            this.ports = ports;
            this.username = username;
            this.password = password;
        }
    }

    private void save() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("subscription", subscription.getText().toString().trim())
                .putString("host", host.getText().toString().trim())
                .putString("port", port.getText().toString().trim())
                .putString("username", username.getText().toString().trim())
                .putString("password", password.getText().toString())
                .putInt("protocol", protocol.getSelectedItemPosition())
                .apply();
    }

    private void restore() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        subscription.setText(prefs.getString("subscription", ""));
        host.setText(prefs.getString("host", ""));
        port.setText(prefs.getString("port", ""));
        username.setText(prefs.getString("username", ""));
        password.setText(prefs.getString("password", ""));
        int savedProtocol = prefs.getInt("protocol", 0);
        if (savedProtocol >= 0 && savedProtocol < protocol.getCount()) {
            protocol.setSelection(savedProtocol);
        }
    }

    private void showStatus(String message, boolean success) {
        status.setText(message);
        status.setTextColor(success ? Color.rgb(20, 110, 70) : Color.rgb(180, 40, 40));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private TextView section(String label) {
        TextView view = text(label, 18, Color.rgb(20, 45, 88));
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(22), 0, dp(7));
        return view;
    }

    private EditText field(String hint, int inputType) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextSize(16);
        view.setSingleLine(true);
        view.setInputType(inputType);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackgroundColor(Color.WHITE);
        return view;
    }

    private Button button(String label, int color) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setAllCaps(false);
        view.setBackgroundColor(color);
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
