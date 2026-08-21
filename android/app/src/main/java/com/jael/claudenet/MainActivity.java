package com.jael.claudenet;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
    private static final String PREFS = "claude_net_android";
    private static final String CLIENT_RELEASES =
            "https://github.com/MetaCubeX/ClashMetaForAndroid/releases";
    private static final String CMFA_PACKAGE = "com.github.metacubex.clash.meta";

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
        title.setTypeface(null, 1);
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
        detect.setOnClickListener(v -> withConfig(config ->
                showStatus("✓ 检测完成，所有已识别端口都会加入配置。", true)));
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
        client.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(CLIENT_RELEASES));
            startActivity(intent);
        });
        root.addView(client, matchWrap(dp(9)));

        status = text("点“一键导入”会直接打开 Clash Meta 的添加页面；确认添加后，"
                + "选择“Claude-前置节点”，再开启客户端 VPN。", 14,
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
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CLIENT_RELEASES)));
        } catch (Exception error) {
            if (configServer != null) configServer.close();
            configServer = null;
            showStatus("自动导入失败：" + error.getMessage(), false);
        }
    }

    private void exportConfig() {
        withConfig(config -> {
            pendingConfig = config;
            save();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/x-yaml");
            intent.putExtra(Intent.EXTRA_TITLE, "claude-net-android.yaml");
            startActivityForResult(intent, CREATE_CONFIG);
        });
    }

    private void copyConfig() {
        withConfig(config -> {
            save();
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("claude-net.yaml", config));
            showStatus("✓ YAML 已复制。注意：剪贴板可能被输入法或其他应用读取。", true);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_CONFIG || resultCode != RESULT_OK
                || data == null || data.getData() == null || pendingConfig == null) {
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("无法打开目标文件");
            output.write(pendingConfig.getBytes(StandardCharsets.UTF_8));
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
        if (server.isEmpty() || server.contains(" ")) {
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
        return new InputData(url, server, ports,
                username.getText().toString().trim(),
                password.getText().toString());
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
                List<ProxyPortDetector.Endpoint> known = new ArrayList<>();
                StringBuilder result = new StringBuilder();
                for (ProxyPortDetector.Endpoint endpoint : detected) {
                    if (result.length() > 0) result.append("；");
                    result.append(endpoint.port).append(" → ").append(endpoint.protocol.name());
                    if (endpoint.protocol != ProxyPortDetector.Protocol.UNKNOWN) known.add(endpoint);
                }
                runOnUiThread(() -> {
                    if (known.isEmpty()) {
                        showStatus("未识别到可用协议（" + result
                                + "）。请检查网络/账号，或在下拉框手动选择协议。", false);
                        return;
                    }
                    showStatus("检测结果：" + result, true);
                    action.run(ConfigBuilder.build(input.url, input.host, known,
                            input.username, input.password));
                });
            } catch (Exception error) {
                runOnUiThread(() -> showStatus(
                        "检测失败：" + error.getMessage() + "。可手动选择协议后重试。", false));
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
        protocol.setSelection(prefs.getInt("protocol", 0));
    }

    private void showStatus(String message, boolean success) {
        status.setText(message);
        status.setTextColor(success ? Color.rgb(20, 110, 70) : Color.rgb(180, 40, 40));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private TextView section(String label) {
        TextView view = text(label, 18, Color.rgb(20, 45, 88));
        view.setTypeface(null, 1);
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
