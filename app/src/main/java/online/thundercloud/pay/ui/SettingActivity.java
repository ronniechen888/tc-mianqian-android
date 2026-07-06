package online.thundercloud.pay.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import online.thundercloud.pay.R;
import online.thundercloud.pay.manager.AppConstants;
import online.thundercloud.pay.receiver.ScreenReceiverUtil;
import online.thundercloud.pay.service.DaemonService;
import online.thundercloud.pay.service.NativeDaemonService;
import online.thundercloud.pay.service.PlayerMusicService;
import online.thundercloud.pay.util.ScreenManager;

public class SettingActivity extends AppCompatActivity {

    private static final String TAG = "SettingActivity";

    // 延迟时间常量
    private static final int RESTART_DELAY_OPEN = 2000;  // 开启时延迟2秒
    private static final int RESTART_DELAY_CLOSE = 1500; // 关闭时延迟1.5秒
    // 动态注册锁屏等广播
    private ScreenReceiverUtil mScreenListener;
    // 1 像素 Activity 管理类
    private ScreenManager mScreenManager;
    private TextView version;
    private Switch state_switch;
    private String state_swich;

    // 复用 Handler 实例
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 使用静态内部类避免内存泄漏
    private static class ScreenStateListenerImpl implements ScreenReceiverUtil.SreenStateListener {
        private final ScreenManager mScreenManager;

        ScreenStateListenerImpl(ScreenManager screenManager) {
            this.mScreenManager = screenManager;
        }

        @Override
        public void onSreenOn() {
            if (mScreenManager != null) {
                mScreenManager.finishActivity();
            }
        }

        @Override
        public void onSreenOff() {
            if (mScreenManager != null) {
                mScreenManager.startActivity();
            }
        }

        @Override
        public void onUserPresent() {
            // 解锁，暂不用
        }
    }

    private ScreenReceiverUtil.SreenStateListener mScreenListenerer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_setting);

        version = findViewById(R.id.version);
        state_switch = findViewById(R.id.state_switch);
        version.setText("当前软件版本 V" + getAppVersionName());

        // 设置返回按钮点击事件
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        SharedPreferences state = getSharedPreferences("state_switch", MODE_PRIVATE);
        state_swich = state.getString("state_switch", "");

        // 设置 Switch 开关状态
        setSwitchState(state_swich);
    }

    /**
     * 设置开关状态显示
     */
    private void setSwitchState(String state) {
        if ("no".equals(state)) {
            state_switch.setChecked(true);
            state_switch.setHint("开启");
        } else if ("off".equals(state)) {
            state_switch.setChecked(false);
            state_switch.setHint("关闭");
        }
    }

    private void stopPlayMusicService() {
        Intent intent = new Intent(this, PlayerMusicService.class);
        stopService(intent);
    }

    private void startPlayMusicService() {
        Intent intent = new Intent(this, PlayerMusicService.class);
        startService(intent);
    }

    private void startDaemonService() {
        Intent intent = new Intent(this, DaemonService.class);
        startService(intent);
    }

    /**
     * 启动 Native 守护服务
     * 注意：需确保系统允许执行 shell 命令
     */
    private void startNativeDaemonService() {
        try {
            Intent intent = new Intent(this, NativeDaemonService.class);
            startService(intent);
            if (AppConstants.DEBUG) {
                Log.d(TAG, "Native 守护服务已启动");
            }
        } catch (Exception e) {
            Log.e(TAG, "启动 Native 守护服务失败", e);
        }
    }
    
    /**
     * 启动 Account Sync 保活 (系统级白名单)
     * 效果：系统每 15 分钟自动唤醒应用一次，即使被杀死也能复活
     */
    private void startAccountSync() {
        try {
            online.thundercloud.pay.util.SyncManager syncManager = online.thundercloud.pay.util.SyncManager.getInstance(this);
            syncManager.startPeriodicSync();
            if (AppConstants.DEBUG) {
                Log.i(TAG, "Account Sync 保活已启动，同步间隔：15 分钟");
            }
        } catch (Exception e) {
            Log.e(TAG, "启动 Account Sync 失败", e);
        }
    }

    // 停止 service
    private void stopDaemonService() {
        Intent intent = new Intent(this, DaemonService.class);
        stopService(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (AppConstants.DEBUG) {
            Log.d(TAG, "--->onDestroy");
        }

        // 清理资源
        if (mScreenListener != null) {
            try {
                mScreenListener.stopScreenReceiverListener();
            } catch (Exception e) {
                Log.w(TAG, "停止广播接收器失败", e);
            }
        }

        mScreenManager = null;
        mScreenListener = null;
        mScreenListenerer = null;
    }

    // 获取当前应用的版本名
    private String getAppVersionName() {
        PackageManager packageManager = getPackageManager();
        try {
            PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(), 0);
            if (packInfo != null && packInfo.versionName != null) {
                return packInfo.versionName;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本名失败", e);
        }
        return "未知版本";
    }

    // 问题反馈（跳转到 GitHub Issues）
    public void email_fk(View view) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/ronniechen888/tc-mianqian-android/issues"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器，请检查浏览器设置", Toast.LENGTH_SHORT).show();
        }
    }

    // Docs
    public void help_api(View v) {
        Intent help = new Intent(this, HelpActivity.class);
        startActivity(help);
    }

    // 白名单权限检测
    public void dc_qx(View v) {
        if (!isIgnoringBatteryOptimizations()) {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(i);
            Toast.makeText(getApplication(), "请勾选此应用\"不优化\"", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "已授权白名单权限!", Toast.LENGTH_SHORT).show();
        }
    }

    // 检测电池白名单权限
    private boolean isIgnoringBatteryOptimizations() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return powerManager.isIgnoringBatteryOptimizations(getPackageName());
        }
        return false;
    }

    // 打开电池白名单设置
    public void requestIgnoreBatteryOptimizations() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "打开电池优化设置失败", e);
        }
    }

    // 保活服务
    public void service_start(View v) {
        // 1. 注册锁屏广播监听器
        mScreenListener = new ScreenReceiverUtil(this);
        mScreenManager = ScreenManager.getScreenManagerInstance(this);
        mScreenListenerer = new ScreenStateListenerImpl(mScreenManager);
        mScreenListener.setScreenReceiverListener(mScreenListenerer);

        // 2. 启动前台 Service
        startDaemonService();

        // 3. 启动播放音乐 Service
        startPlayMusicService();

        // 4. 启动 Native 守护服务 (高级保活)
        startNativeDaemonService();

        // 5. 启动 Account Sync 保活 (系统级白名单，推荐!)
        startAccountSync();

        Toast.makeText(this, "服务启动成功!", Toast.LENGTH_SHORT).show();
    }

    // 屏幕永亮
    public void state_swit(View v) {
        if (state_switch.isChecked()) {
            showEnableAlwaysOnDialog();
        } else {
            disableAlwaysOn();
        }
    }

    /**
     * 显示开启屏幕常亮对话框
     */
    private void showEnableAlwaysOnDialog() {
        String message = "开启或关闭此功能将会在 2 秒后重启软件！\n" +
                        "开启之后便会自动调低软件窗口亮度并进入全屏模式（退出即可恢复！）\n" +
                        "重启后请不要关闭软件，保持 Log 日志面板即可否则无效！\n" +
                        "此功能仅适用于真机独立挂 ThunderCloud免签支付监控端的";

        new AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("我已知晓", (dia, which) -> {
                // 先保存设置
                SharedPreferences.Editor editor = getSharedPreferences("state_switch", MODE_PRIVATE).edit();
                editor.putString("state_switch", "no");
                editor.apply(); // 使用 apply() 异步保存
                
                state_switch.setHint("开启");
                Toast.makeText(this, "屏幕永亮开启成功，2S 后重启...", Toast.LENGTH_SHORT).show();

                // 延迟重启
                restartApp(RESTART_DELAY_OPEN);
            })
                .setNegativeButton("暂不开启", (dia, which) -> {
                    // 用户取消，恢复开关状态
                    state_switch.setChecked(false);
                })
            .create()
            .show();
    }

    /**
     * 关闭屏幕常亮
     */
    private void disableAlwaysOn() {
        // 先保存设置
        SharedPreferences.Editor editor = getSharedPreferences("state_switch", MODE_PRIVATE).edit();
        editor.putString("state_switch", "off");
        editor.apply(); // 使用 apply() 异步保存
        state_switch.setHint("关闭");
        Toast.makeText(this, "屏幕永亮已关闭，1.5S 后重启...", Toast.LENGTH_SHORT).show();
        // 延迟重启
        restartApp(RESTART_DELAY_CLOSE);
    }

    /**
     * 重启应用
     * 使用 PendingIntent 方式重启，更加优雅
     * @param delayMillis 延迟时间 (毫秒)
     */
    private void restartApp(int delayMillis) {
        mainHandler.postDelayed(() -> {
            try {
                // 获取启动 Intent
                Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (intent != null) {
                    // 添加标志位，确保重新启动
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    // 启动主 Activity
                    startActivity(intent);
                    // 结束当前 Activity
                    finish();
                    // 退出进程（可选，根据需求决定是否保留）
                    android.os.Process.killProcess(android.os.Process.myPid());
                } else {
                    Log.e(TAG, "无法获取启动 Intent");
                    Toast.makeText(this, "重启失败，请手动重启应用", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "重启应用失败", e);
                Toast.makeText(this, "重启失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, delayMillis);
    }

    public void vmq_Pro_gy(View v) {
        Intent aboutIntent = new Intent(this, AboutActivity.class);
        startActivity(aboutIntent);
    }
}
