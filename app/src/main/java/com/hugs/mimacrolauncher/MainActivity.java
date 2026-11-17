package com.hugs.mimacrolauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MiMacro 外部调起器。
 *
 * MiMacro（com.xiaomi.macro）没有任何 Activity，唯一入口是 MainService，
 * 平时由游戏加速器用显式 Intent 调起，带 gamePackage + clickIcon 参数。
 * 本应用声明 com.xiaomi.macro.permission（普通权限，安装即授予），
 * 点按钮即可从外部发出同样的 Intent。
 *
 * 注意：MainService 启动后会检查 FeatureParser 的 support_mi_game_macro
 * 开关，不支持的机型会自检跳过；注入还依赖肩键服务。两道坎都过不了
 * 的话，界面不会出现——这是机型限制，不是调起器的问题。
 */
public class MainActivity extends Activity {

    private static final String MACRO_PKG = "com.xiaomi.macro";
    private static final String MACRO_SERVICE = "com.xiaomi.macro.MainService";
    // 悬浮窗服务也要读这些配置，所以不用 private
    static final String PREFS = "launcher";
    static final String KEY_GAME_PKG = "gamePackage";
    private static final String KEY_LANDSCAPE = "landscape";
    private static final String KEY_FLOAT = "floatEnabled";

    private TextView statusText;
    private TextView resultText;
    private EditText gamePkgInput;
    private CheckBox floatCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 恢复横屏偏好：必须在搭界面之前设置，避免先竖屏闪一下
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_LANDSCAPE, false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("MiMacro 调起器");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusText.setPadding(0, dp(12), 0, dp(4));
        root.addView(statusText);

        TextView hint = new TextView(this);
        hint.setText("填写要开宏的游戏包名（打开游戏后，在游戏加速器里能看到；也可以填任意已安装应用的包名先测试调起）。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, dp(8), 0, dp(4));
        root.addView(hint);

        gamePkgInput = new EditText(this);
        gamePkgInput.setHint("例如：com.tencent.tmgp.pubgm");
        gamePkgInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        gamePkgInput.setSingleLine(true);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        gamePkgInput.setText(prefs.getString(KEY_GAME_PKG, ""));
        root.addView(gamePkgInput,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        Button startAndGameBtn = new Button(this);
        startAndGameBtn.setText("打开游戏并启动服务");
        startAndGameBtn.setOnClickListener(v -> startMacroAndGame());
        root.addView(startAndGameBtn, btnParams());

        Button startBtn = new Button(this);
        startBtn.setText("只启动 MiMacro 服务");
        startBtn.setOnClickListener(v -> startMacro());
        root.addView(startBtn, btnParams());

        Button kogBtn = new Button(this);
        kogBtn.setText("填入王者荣耀包名");
        kogBtn.setOnClickListener(v -> gamePkgInput.setText("com.tencent.tmgp.sgame"));
        root.addView(kogBtn, btnParams());

        CheckBox landscapeCheck = new CheckBox(this);
        landscapeCheck.setText("横屏模式");
        landscapeCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        landscapeCheck.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_LANDSCAPE, false));
        landscapeCheck.setOnCheckedChangeListener((v, checked) -> {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_LANDSCAPE, checked).apply();
            setRequestedOrientation(checked
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        });
        root.addView(landscapeCheck, btnParams());

        floatCheck = new CheckBox(this);
        floatCheck.setText("悬浮窗调起（游戏里点浮窗启动宏）");
        floatCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        floatCheck.setOnCheckedChangeListener((v, checked) -> {
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "请先给本调起器开悬浮窗权限", Toast.LENGTH_LONG).show();
                    openOverlaySettings(getPackageName());
                    floatCheck.setChecked(false);
                    return;
                }
                startForegroundService(new Intent(this, FloatingService.class));
            } else {
                stopService(new Intent(this, FloatingService.class));
            }
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean(KEY_FLOAT, checked).apply();
        });
        root.addView(floatCheck, btnParams());

        Button stopBtn = new Button(this);
        stopBtn.setText("停止 MiMacro 服务");
        stopBtn.setOnClickListener(v -> stopMacro());
        root.addView(stopBtn, btnParams());

        Button overlayBtn = new Button(this);
        overlayBtn.setText("给 MiMacro 开悬浮窗权限");
        overlayBtn.setOnClickListener(v -> openOverlaySettings(MACRO_PKG));
        root.addView(overlayBtn, btnParams());

        Button refreshBtn = new Button(this);
        refreshBtn.setText("重新检测安装状态");
        refreshBtn.setOnClickListener(v -> refreshStatus());
        root.addView(refreshBtn, btnParams());

        resultText = new TextView(this);
        resultText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        resultText.setPadding(0, dp(12), 0, 0);
        root.addView(resultText);

        TextView note = new TextView(this);
        note.setText("\n说明：\n1. 手机上必须先装好 MiMacro（com.xiaomi.macro）。\n2. MiMacro 需要悬浮窗权限，没有的话点上面按钮去开。\n3. 点启动后如果界面没出现，说明这台手机的 support_mi_game_macro 开关是关的，服务自检跳过了，调起器改不了这个开关。\n4. 即使界面出来，宏的点击注入依赖肩键服务，非肩键机型可能点不出去。\n5. “打开游戏并启动服务”会先打开上面填的游戏，再启动宏服务。\n6. 若是横屏游戏，先将启动器设置为横屏模式，再点击启动。\n7. 打开“悬浮窗调起”后，游戏里会一直有个“宏”小浮窗，点一下启动宏，长按停止；浮窗可以拖动换位置。");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        refreshStatus();

        // 恢复悬浮窗开关：之前开着且权限还在，就重新拉起浮窗服务
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_FLOAT, false)) {
            if (Settings.canDrawOverlays(this)) {
                floatCheck.setChecked(true);
            } else {
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putBoolean(KEY_FLOAT, false).apply();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        // 权限在设置里被撤掉的话，把开关同步关掉
        if (floatCheck.isChecked() && !Settings.canDrawOverlays(this)) {
            floatCheck.setChecked(false);
        }
    }

    private LinearLayout.LayoutParams btnParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(8);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void refreshStatus() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(MACRO_PKG, 0);
            boolean overlay = Settings.canDrawOverlays(this);
            statusText.setText("MiMacro 已安装，版本 " + info.versionName
                    + "（" + info.versionCode + "）\n"
                    + "本调起器悬浮窗权限：" + (overlay ? "已开" : "未开")
                    + "（MiMacro 自己的悬浮窗权限请点下面按钮检查）");
        } catch (PackageManager.NameNotFoundException e) {
            statusText.setText("MiMacro 未安装：请先安装 com.xiaomi.macro 的 APK，再用本调起器启动它。");
        }
    }

    static Intent macroIntent(String gamePkg) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(MACRO_PKG, MACRO_SERVICE));
        if (gamePkg != null) {
            intent.putExtra("gamePackage", gamePkg);
            intent.putExtra("clickIcon", true);
            intent.putExtra("macro_page_show", true);
        }
        return intent;
    }

    private void startMacro() {
        String gamePkg = gamePkgInput.getText().toString().trim();
        if (gamePkg.isEmpty()) {
            resultText.setText("请先填写游戏包名。");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString(KEY_GAME_PKG, gamePkg).apply();
        try {
            startForegroundService(macroIntent(gamePkg));
            resultText.append("\n已发送启动请求。\n"
                    + "如果 MiMacro 界面随后出现，说明调起成功。\n"
                    + "如果什么都没出现，说明这台手机的 support_mi_game_macro 开关是关的，服务自检跳过了。");
        } catch (Exception e) {
            resultText.append("\n启动失败：" + e.getClass().getSimpleName()
                    + "：\n" + e.getMessage());
        }
    }

    private void startMacroAndGame() {
        String gamePkg = gamePkgInput.getText().toString().trim();
        if (gamePkg.isEmpty()) {
            resultText.setText("请先填写游戏包名。");
            return;
        }
        // 先打开游戏，再启动宏服务。两句调用紧挨着执行：打开游戏只是发出启动
        // 指令（游戏界面随后异步出现），服务启动时本应用仍在前台，不会触发
        // Android 12+ 的后台启动前台服务限制。
        resultText.setText("正在打开游戏 " + gamePkg + "……");
        launchGame(gamePkg);
        startMacro();
    }

    private void launchGame(String gamePkg) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(gamePkg);
            if (launch == null) {
                resultText.append("\n打不开游戏：找不到 " + gamePkg + " 的启动入口（可能没安装这个应用）。");
                return;
            }
            startActivity(launch);
            resultText.append("\n正在打开游戏……");
        } catch (Exception e) {
            resultText.append("\n打开游戏失败：" + e.getClass().getSimpleName() + "：" + e.getMessage());
        }
    }

    private void stopMacro() {
        try {
            stopService(macroIntent(null));
            resultText.setText("已发送停止请求。");
        } catch (Exception e) {
            resultText.setText("停止失败：" + e.getClass().getSimpleName()
                    + "：\n" + e.getMessage());
        }
    }

    private void openOverlaySettings(String pkg) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            resultText.setText("打不开授权页：" + e.getMessage());
        }
    }
}
