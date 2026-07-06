package online.thundercloud.pay.ui;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.view.View.OnLongClickListener;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.zxing.activity.CaptureActivity;
import online.thundercloud.pay.R;
import online.thundercloud.pay.manager.AppConstants;
import online.thundercloud.pay.service.ForeService;
import online.thundercloud.pay.service.PayNotificationListenerService;
import online.thundercloud.pay.util.MonitorLogUtils;
import online.thundercloud.pay.util.ServerConfigUtils;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 email：576892817@qq.com
 qq：576892817
 GitHub：https://github.com/ronniechen888/tc-mianqian-android
 @©️版权所有
 */

public class MainActivity extends AppCompatActivity implements OnLongClickListener {

    public static final String PREF_SHINIAN = "shinian";
    public static final String PREF_HOST = "host";
    public static final String PREF_KEY = "key";
    public static final String PREF_CONN_STATE = "connection_state";
    public static final String PREF_CONN_COLOR = "connection_color";
    public static final String ACTION_CONNECTION_STATE_CHANGED = "online.thundercloud.pay.CONNECTION_STATE_CHANGED";
    public static final String EXTRA_CONNECTION_STATE = "state";
    public static final String EXTRA_CONNECTION_COLOR = "color";
    private static final long UI_HEARTBEAT_POLL_MS = 10 * 1000L;
    private static final long MONITOR_LOG_RETENTION_MS = 30 * 60 * 1000L;

    private TextView txthost;
    private TextView txtkey;
    private TextView cyberStatus;
    private boolean isOk = false;
    private static final String TAG = "MainActivity";
    private static String host;
    private static String key;
    public static TextView LogsTextView;
    private static LinearLayout logs_linear_layout;
    private int id = 0;
    private String state_swich;
    private TextView sj_dl;//当前电量Text
    private PopupWindow quickMenuWindow;
    private int capacity;
    private final int ld = 5;//亮度值0~255
    //当前版本

    private Thread dlThread;
    private final Handler heartbeatPollHandler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (hasSavedServerConfig()) {
                requestHeartbeat(true, false);
            }
            heartbeatPollHandler.postDelayed(this, UI_HEARTBEAT_POLL_MS);
        }
    };
    private final BroadcastReceiver connectionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) return;
            String state = intent.getStringExtra(EXTRA_CONNECTION_STATE);
            int color = intent.getIntExtra(EXTRA_CONNECTION_COLOR, Color.parseColor("#89A4B8"));
            if (!TextUtils.isEmpty(state)) {
                setConnectionState(state, color);
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 输出关键日志，方便调试
        Log.d(TAG, "========== MainActivity onCreate 开始 ==========");
        Log.d(TAG, "设备信息：" + Build.MANUFACTURER + " " + Build.MODEL);
        Log.d(TAG, "Android 版本：" + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        Log.d(TAG, "MIUI 版本：" + getMiuiVersion());
        
        // 自动适配屏幕
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Log.d(TAG, "布局加载完成");


        //查找组件
        txthost = findViewById(R.id.txt_host);
        txtkey = findViewById(R.id.txt_key);
        cyberStatus = findViewById(R.id.cyber_status);
        LogsTextView = findViewById(R.id.state_logs);
        logs_linear_layout = findViewById(R.id.logs_linear_layout);
        LogsTextView.setOnLongClickListener(this);//长按
        sj_dl = findViewById(R.id.sj_dl);

        // 设置底部版权信息文本的下划线
        TextView bqTextView = findViewById(R.id.bq);
        if (bqTextView != null) {
            bqTextView.setPaintFlags(bqTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }

        // 延迟启动前台服务，避免在 onCreate 时阻塞 UI 线程
        // Android 14 需要在 5 秒内调用 startForeground()
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent serviceIntent = new Intent(MainActivity.this, ForeService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Android 8.0+ 必须使用 startForegroundService
                        startForegroundService(serviceIntent);
                        Log.d(TAG, "已调用 startForegroundService");
                    } else {
                        startService(serviceIntent);
                        Log.d(TAG, "已调用 startService");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "启动前台服务失败：" + e.getMessage(), e);
                    // 在主线程显示 Toast
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "服务启动失败，请检查权限", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }, 1000); // 延迟 1 秒启动，确保 Activity 完全初始化



        //接收并设置日志信息
        SharedPreferences read1 = getSharedPreferences("items", MODE_PRIVATE);
        String logsStr = MonitorLogUtils.keepRecentLogs(read1.getString("logsStr", ""), MONITOR_LOG_RETENTION_MS);
        read1.edit().putString("logsStr", logsStr).apply();
        LogsTextView.setText(normalizeLogsText(logsStr));


        if (!checkQQInstalled(this, "com.tencent.mm")) {
            Toast.makeText(getApplication(), "提示：无法检测到微信包名！\n可能无法正常监听", Toast.LENGTH_LONG).show();
        }
        //检测微信支付宝包名 写在前面
        if (!checkQQInstalled(this, "com.eg.android.AlipayGphone")) {
            Toast.makeText(getApplication(), "提示：无法检测到支付宝包名！\n可能无法正常监听", Toast.LENGTH_LONG).show();
        }

        // 获取状态
        SharedPreferences state = getSharedPreferences("state_switch", MODE_PRIVATE);
        state_swich = state.getString("state_switch", "");
        //屏幕是否常亮
        if (state_swich.equals("no")) {
            Toast.makeText(getApplication(), "当前处于屏幕常亮模式" + "\n当前界面亮度值：" + ld, Toast.LENGTH_LONG).show();
            // 添加屏幕常亮
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // 添加壁纸
            //getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            initAppHeart();//电量线程
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = (float) ld * (1f / 255f);//亮度值0~255
                    getWindow().setAttributes(lp);
                }
            }, 3000);
        }

        //检测通知使用权是否启用
        if (!isNotificationListenersEnabled()) {

            //增加一层权限判断 以防部分云手机获取不到是否开启
            if (!isNLServiceEnabled()) {

                //创建弹窗对象
                AlertDialog.Builder qx = new AlertDialog.Builder(MainActivity.this);
                //alert = builder.setIcon(R.mipmap.ic_icon_fish)
                qx.setIcon(R.drawable.menu_gy);
                qx.setTitle("温馨提示：");
                qx.setMessage("当前你未授权收款插件读取通知栏权限，请前往授权后再继续操作");
                qx.setCancelable(false); //设置是否可以点击对话框外部取消
                qx.setPositiveButton("前往授权", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //跳转到通知使用权页面
                        gotoNotificationAccessSetting();
                    }
                });
                qx.setNeutralButton("暂不授权", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getApplication(), "请给予监听权限！否则无法正常运行", Toast.LENGTH_LONG).show();
                    }
                });
                qx.show();//显示
            }
        }

        //读入保存的配置数据并显示
        SharedPreferences read = getSharedPreferences(PREF_SHINIAN, MODE_PRIVATE);
        host = read.getString(PREF_HOST, "");
        key = read.getString(PREF_KEY, "");

        if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(key)) {
            txthost.setText(" 通知地址：" + host);
            txtkey.setText(" 通讯密钥：" + key);
            applySavedConnectionState();
        } else {
            setConnectionState("OFFLINE", Color.parseColor("#89A4B8"));
        }

        Log.d(TAG, "========== MainActivity onCreate 完成 ==========");

    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(connectionStateReceiver, new IntentFilter(ACTION_CONNECTION_STATE_CHANGED));
        if (hasSavedServerConfig()) {
            applySavedConnectionState();
        }
        startHeartbeatPolling();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopHeartbeatPolling();
        unregisterReceiver(connectionStateReceiver);
    }

    /**
     * 获取 MIUI 版本号
     *
     * @return MIUI 版本字符串，如果不是 MIUI 则返回空字符串
     */
    private String getMiuiVersion() {
        try {
            Class<?> clazz = Class.forName("miui.os.Build");
            java.lang.reflect.Field field = clazz.getDeclaredField("VERSION_INCREMENT");
            Object value = field.get(null);
            return value != null ? value.toString() : "未知";
        } catch (Exception e) {
            return "非 MIUI 设备";
        }
    }

    /**
     * 请求忽略电池优化（小米手机重要）
     */
    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Log.d(TAG, "已请求电池优化白名单");
                } catch (Exception e) {
                    Log.e(TAG, "请求电池优化白名单失败", e);
                }
            }
        }
    }

    /**
     * 退出应用，停止所有服务和清理资源
     */
    private void exitApp() {
        try {
            // 1. 停止心跳线程（如果存在）
            if (dlThread != null) {
                dlThread.interrupt();
                dlThread = null;
                Log.d(TAG, "已停止电量线程");
            }

            // 2. 停止前台服务（监听服务）
            try {
                stopService(new Intent(MainActivity.this, ForeService.class));
                Log.d(TAG, "已停止前台监听服务");
            } catch (Exception e) {
                Log.e(TAG, "停止前台服务失败", e);
            }

            // 3. 释放唤醒锁
            try {
                Class<?> serviceClass = Class.forName("online.thundercloud.pay.service.PayNotificationListenerService");
                Method releaseMethod = serviceClass.getDeclaredMethod("releaseWakeLock");
                releaseMethod.invoke(null);
                Log.d(TAG, "已释放唤醒锁");
            } catch (Exception e) {
                Log.e(TAG, "释放唤醒锁失败", e);
            }

            // 4. 清除所有通知
            try {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.cancelAll();
                    Log.d(TAG, "已清除所有通知");
                }
            } catch (Exception e) {
                Log.e(TAG, "清除通知失败", e);
            }

            // 5. 显示退出提示
            Toast.makeText(this, "正在退出监控服务...", Toast.LENGTH_SHORT).show();

            // 6. 延迟退出，确保所有服务已停止
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 7. 终止进程
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(0);
                    } catch (Exception e) {
                        Log.e(TAG, "终止进程失败", e);
                        // 强制终止
                        Runtime.getRuntime().exit(0);
                    }
                }
            }, 800); // 延迟 800 毫秒，确保服务完全停止

        } catch (Exception e) {
            Log.e(TAG, "退出应用时发生错误", e);
            // 如果清理过程失败，直接退出
            try {
                android.os.Process.killProcess(android.os.Process.myPid());
            } catch (Exception ex) {
                Runtime.getRuntime().exit(0);
            }
        }
    }


    //监听日志接收参数
    public static Handler monitorLogHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (LogsTextView != null) {
                LogsTextView.setText(normalizeLogsText(msg.getData().getString("logsStr")));
            }
        }
    };

    private static String normalizeLogsText(String logsStr) {
        if (TextUtils.isEmpty(logsStr) || "null".equalsIgnoreCase(logsStr.trim())) {
            return "";
        }
        return logsStr;
    }

    /**
     * 获取当前电量百分比
     *
     * @param context
     * @return
     */
    public int getBatteryCurrent(Context context) {
        int capacity = 0;
        try {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            capacity = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);//当前电量剩余百分比
        } catch (Exception e) {

        }
        return capacity;
    }


    // 电池电量线程
    public void initAppHeart() {

        dlThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            sj_dl.setText("当前电量：" + getBatteryCurrent(MainActivity.this) + "%");
                        }
                    }, 1000);

                    try {
                        Thread.sleep(60 * 1000);//一分钟休眠
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        });

        dlThread.start(); //启动线程
    }


    /**
     * @param string
     * @return 转换之后的内容
     * @Title: unicodeDecode
     * @Description: unicode解码 将Unicode的编码转换为中文
     */
    public String unicodeDecode(String string) {
        Pattern pattern = Pattern.compile("(\\\\u(\\p{XDigit}{4}))");
        Matcher matcher = pattern.matcher(string);
        char ch;
        while (matcher.find()) {
            ch = (char) Integer.parseInt(matcher.group(2), 16);
            string = string.replace(matcher.group(1), ch + "");
        }
        return string;
    }

    @NonNull
    static HttpURLConnection getHttpURLConnection(String urlPath) throws IOException {
        URL url = new URL(urlPath);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();

        //设置参数
        httpConn.setDoOutput(true);     //需要输出
        httpConn.setDoInput(true);      //需要输入
        httpConn.setUseCaches(false);   //不允许缓存
        httpConn.setRequestMethod("POST");      //设置POST方式连接

        //设置请求属性
        httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        httpConn.setRequestProperty("Connection", "Keep-Alive");// 维持长连接
        httpConn.setRequestProperty("Charset", "UTF-8");

        //连接,也可以不用明文connect，使用下面的httpConn.getOutputStream()会自动connect
        httpConn.connect();
        return httpConn;
    }

    public String getHtml(String path) throws Exception {
        URL url = new URL(path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8 * 1000);
        //通过输入流获取html数据
        InputStream inStream = conn.getInputStream();
        //获取html的二进制数组
        byte[] data = readInputStream(inStream);
        //获取指定字符集解码指定的字节数组构造一个新的字符串
        String html = new String(data, StandardCharsets.UTF_8);
        return html;
    }

    //读取输入流 获取HTML二进制数组
    public byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        inStream.close();
        return outStream.toByteArray();
    }

    public boolean isIgnoringBatteryOptimizations(Context context) {
        if (context == null) {
            return false;
        }
        boolean isIgnoring = false;
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return isIgnoring;
    }

    /**
     * 忽略电池优化
     */

    public void ignoreBatteryOptimization(Activity activity) {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        boolean hasIgnored = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            hasIgnored = powerManager.isIgnoringBatteryOptimizations(activity.getPackageName());
            //  判断当前APP是否有加入电池优化的白名单，如果没有，弹出加入电池优化的白名单的设置对话框。
            if (!hasIgnored) {
                try {//先调用系统显示 电池优化权限
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {//如果失败了则引导用户到电池优化界面
                    try {
                        Intent intent = new Intent(Intent.ACTION_MAIN);

                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        intent.addCategory(Intent.CATEGORY_LAUNCHER);

                        ComponentName cn = ComponentName.unflattenFromString("com.android.settings/.Settings$HighPowerApplicationsActivity");

                        intent.setComponent(cn);

                        startActivity(intent);
                    } catch (Exception ex) {//如果全部失败则说明没有电池优化功能

                    }
                }

            }
        }


    }

    //检测电池白名单权限
    private boolean isIgnoringBatteryOptimizations() {

        boolean isIgnoring = false;

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isIgnoring = powerManager.isIgnoringBatteryOptimizations(getPackageName());
            }
        }
        return isIgnoring;
    }

    //打开电池白名单设置
    public void requestIgnoreBatteryOptimizations() {

        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //点击两次退出
    static boolean isExit;

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!isExit) {
                //isExit = true;
                AlertDialog.Builder exit = new AlertDialog.Builder(this);
                exit.setTitle("温馨提示：");
                exit.setIcon(R.drawable.menu_exit);
                exit.setMessage("确定退出程序吗？退出将无法正常监听!");
                exit.setCancelable(false); //设置是否可以点击对话框外部取消
                exit.setPositiveButton("确定", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dia, int which) {
                                exitApp();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .create();
                exit.show();
            }
            return false;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void showQuickMenu(View anchor) {
        dismissQuickMenu();

        View panelView = LayoutInflater.from(this).inflate(R.layout.panel_main_menu, null, false);
        bindQuickMenuAction(panelView, R.id.menu_about, R.id.about);
        bindQuickMenuAction(panelView, R.id.menu_setting, R.id.setting);
        bindQuickMenuAction(panelView, R.id.menu_exit, R.id.exit);

        quickMenuWindow = new PopupWindow(
                panelView,
                dpToPx(248),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        quickMenuWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        quickMenuWindow.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            quickMenuWindow.setElevation(dpToPx(18));
        }

        int xoff = -dpToPx(206);
        int yoff = dpToPx(8);
        quickMenuWindow.showAsDropDown(anchor, xoff, yoff, Gravity.END);
    }

    private void bindQuickMenuAction(View root, int viewId, int itemId) {
        View target = root.findViewById(viewId);
        if (target == null) {
            return;
        }
        target.setOnClickListener(v -> {
            dismissQuickMenu();
            handleMenuAction(itemId);
        });
    }

    private void dismissQuickMenu() {
        if (quickMenuWindow != null && quickMenuWindow.isShowing()) {
            quickMenuWindow.dismiss();
        }
        quickMenuWindow = null;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    //菜单监听item
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return handleMenuAction(item.getItemId()) || super.onOptionsItemSelected(item);
    }

    private boolean handleMenuAction(int itemId) {
        if (itemId == R.id.about) {
            View aboutView = LayoutInflater.from(this).inflate(R.layout.dialog_about_panel, null);
            TextView aboutContent = aboutView.findViewById(R.id.about_dialog_content);
            aboutContent.setText(AboutActivity.getSoftwareIntroContent());
            AlertDialog.Builder gy = new AlertDialog.Builder(this);
            gy.setView(aboutView);
            gy.setCancelable(false); //设置是否可以点击对话框外部取消
            gy.setNegativeButton("不同意协议", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                            //Toast.makeText(MainActivity.this, "你点击了取消按钮~", Toast.LENGTH_SHORT).show();
                        }
                    })

                    .setPositiveButton("已阅读并同意", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //Toast.makeText(MainActivity.this, "你点击了确定按钮~", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNeutralButton("配置文档", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //Toast.makeText(MainActivity.this, "你点击了中立按钮~", Toast.LENGTH_SHORT).show();
                            Intent help = new Intent();
                            help.setClass(MainActivity.this, HelpActivity.class);
                            startActivity(help);

                        /*
                         Intent intent_d = new Intent();
                         intent_d.setAction("android.intent.action.VIEW");
                         Uri content_url = Uri.parse("https://github.com/szvone/Vmq");
                         intent_d.setData(content_url);
                         startActivity(intent_d);
                         */
                        }
                    });
            AlertDialog aboutDialog = gy.create();
            aboutDialog.setOnShowListener(dialogInterface -> styleDialog(aboutDialog));
            aboutDialog.show(); //显示对话框
            return true;
        }
        //设置
        else if (itemId == R.id.setting) {
            Intent set = new Intent();
            set.setClass(MainActivity.this, SettingActivity.class);
            startActivity(set);
            return true;
        }
        //退出程序
        else if (itemId == R.id.exit) {
            exitApp();
            return true;
        }

        return false;
    }

    private void styleDialog(AlertDialog dialog) {
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), R.drawable.cyber_button_primary, "#E8F7FF");
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), R.drawable.cyber_button_secondary, "#89A4B8");
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), R.drawable.cyber_button_secondary, "#33FF99");
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void styleDialogButton(Button button, int backgroundRes, String textColor) {
        if (button == null) {
            return;
        }
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor(textColor));
        button.setTextSize(13f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackgroundResource(backgroundRes);
        button.setMinHeight(dpToPx(42));
        button.setMinimumHeight(dpToPx(42));
        button.setPadding(dpToPx(14), 0, dpToPx(14), 0);

        ViewGroup.LayoutParams rawParams = button.getLayoutParams();
        if (rawParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.weight = 1f;
            params.leftMargin = dpToPx(4);
            params.rightMargin = dpToPx(4);
            params.topMargin = dpToPx(6);
            button.setLayoutParams(params);
        }
    }

    /**
     * 判断 用户是否安装QQ客户端
     */
    public boolean isQQClientAvailable(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
        if (pinfo != null) {
            for (int i = 0; i < pinfo.size(); i++) {
                String pn = pinfo.get(i).packageName;
                if (pn.equalsIgnoreCase("com.tencent.qqlite") || pn.equalsIgnoreCase("com.tencent.mobileqq")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断 Uri是否有效 方法
     */
    public boolean isValidIntent(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        return !activities.isEmpty();
    }

    /**
     * 复制内容到剪切板
     *
     * @param copyStr
     * @return
     */
    private boolean copyStr(String copyStr) {
        try {
            //获取剪贴板管理器
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", copyStr);
            // 将ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**************************************************************************/
    //后台管理
    public void admin_url(View v) {
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, "请先配置数据！", Toast.LENGTH_LONG).show();
        } else {
            String adminUrl = ServerConfigUtils.getAdminUrl(host);
            if (TextUtils.isEmpty(adminUrl)) {
                Toast.makeText(this, "配置地址无效，请重新配置！", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent_d = new Intent();
            intent_d.setAction("android.intent.action.VIEW");
            Uri content_url = Uri.parse(adminUrl);
            intent_d.setData(content_url);
            startActivity(intent_d);
        }

    }

    //扫码配置
    public void startQrCode(View v) {
        // 申请相机权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, AppConstants.REQ_PERM_CAMERA);
            return;
        }
        // 申请文件读写权限（部分朋友遇到相册选图需要读写权限的情况，这里一并写一下）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, AppConstants.REQ_PERM_EXTERNAL_STORAGE);
            return;
        }
        // 二维码扫码
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, AppConstants.REQ_QR_CODE);
    }

    /**
     * 扫码结果处理
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //扫描结果回调
        if (requestCode == AppConstants.REQ_QR_CODE && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString(AppConstants.INTENT_EXTRA_KEY_QR_SCAN);
            applyServerConfig(scanResult, "二维码错误，请您扫描网站上显示的二维码!");

        }
    }

    //手动配置
    public void doInput(View v) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_config, null);
        final EditText inputServer = dialogView.findViewById(R.id.input_server);
        if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(key)) {
            inputServer.setText(ServerConfigUtils.toDisplayText(host, key));
            inputServer.setSelection(inputServer.getText().length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setIcon(R.drawable.icon_pzsj);
        builder.setCancelable(false); //设置是否可以点击对话框外部取消

        builder.setNeutralButton("如何配置?", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                //配置文档activity
                Intent help = new Intent();
                help.setClass(MainActivity.this, HelpActivity.class);
                startActivity(help);
            }
        });

        builder.setNegativeButton("取消", null);

        builder.setPositiveButton("确认连接", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {

                applyServerConfig(inputServer.getText().toString(), "数据不能为空或数据错误!");

            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                styleDialog(dialog);
            }
        });
        dialog.show();
    }

    private void applyServerConfig(String configText, String errorText) {
        ServerConfigUtils.ParsedConfig parsed = ServerConfigUtils.parse(configText);
        if (parsed == null) {
            setConnectionState("INVALID", Color.parseColor("#FF6B6B"));
            String message = ServerConfigUtils.looksLikeApiBaseOnly(configText)
                    ? "当前二维码或配置只包含接口地址，还需要拼接通讯密钥后再保存"
                    : errorText;
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri parsedUri = Uri.parse(parsed.baseUrl);
        String parsedHost = parsedUri.getHost();
        if (!TextUtils.isEmpty(parsedHost) && (parsedHost.contains("localhost") || parsedHost.startsWith("127."))) {
            setConnectionState("INVALID", Color.parseColor("#FF6B6B"));
            Toast.makeText(MainActivity.this, "配置信息错误，请使用可从手机访问的局域网或公网地址", Toast.LENGTH_LONG).show();
            return;
        }

        setConnectionState("SYNCING", Color.parseColor("#00E5FF"));
        String t = String.valueOf(new Date().getTime());
        String sign = md5(t + parsed.secret);
        String heartUrl = ServerConfigUtils.buildApiUrl(parsed.baseUrl, "/appHeart?t=" + t + "&sign=" + sign);

        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(heartUrl).method("GET", null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "配置心跳请求失败: " + e.getMessage());
                setConnectionState("OFFLINE", Color.parseColor("#FF6B6B"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                            + "\r\r\r\r" + "配置连接失败：" + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.d(TAG, "onResponse: " + body);
                try {
                    JSONObject result = new JSONObject(body);
                    int code = result.getInt("code");
                    String msg = result.optString("msg");
                    if (code == 1) {
                        isOk = true;
                        setConnectionState("ONLINE", Color.parseColor("#33FF99"));
                    } else {
                        isOk = false;
                        setConnectionState("OFFLINE", Color.parseColor("#FF6B6B"));
                        if (msg.contains("冻结")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "当前应用已被冻结，请联系管理员", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                } catch (JSONException e) {
                    isOk = false;
                    setConnectionState("OFFLINE", Color.parseColor("#FF6B6B"));
                }
            }
        });

        ignoreBatteryOptimization(this);
        txthost.setText(" 通知地址：" + parsed.baseUrl);
        txtkey.setText(" 通讯密钥：" + parsed.secret);
        host = parsed.baseUrl;
        key = parsed.secret;

        SharedPreferences.Editor editor = getSharedPreferences(PREF_SHINIAN, MODE_PRIVATE).edit();
        editor.putString(PREF_HOST, host);
        editor.putString(PREF_KEY, key);
        editor.apply();
    }

    private void setConnectionState(final String state, final int color) {
        persistConnectionState(state, color);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (cyberStatus != null) {
                    cyberStatus.setText(state);
                    cyberStatus.setTextColor(color);
                }
            }
        });
    }

    private void persistConnectionState(String state, int color) {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_SHINIAN, MODE_PRIVATE).edit();
        editor.putString(PREF_CONN_STATE, state);
        editor.putInt(PREF_CONN_COLOR, color);
        editor.apply();
    }

    private void applySavedConnectionState() {
        SharedPreferences read = getSharedPreferences(PREF_SHINIAN, MODE_PRIVATE);
        String state = read.getString(PREF_CONN_STATE, "READY");
        int color = read.getInt(PREF_CONN_COLOR, Color.parseColor("#00E5FF"));
        setConnectionState(state, color);
    }

    private boolean hasSavedServerConfig() {
        return !TextUtils.isEmpty(host) && !TextUtils.isEmpty(key);
    }

    private void startHeartbeatPolling() {
        heartbeatPollHandler.removeCallbacks(heartbeatPollRunnable);
        heartbeatPollHandler.postDelayed(heartbeatPollRunnable, UI_HEARTBEAT_POLL_MS);
    }

    private void stopHeartbeatPolling() {
        heartbeatPollHandler.removeCallbacks(heartbeatPollRunnable);
    }

    private void requestHeartbeat(final boolean silent, boolean showSyncing) {
        if (showSyncing) {
            setConnectionState("SYNCING", Color.parseColor("#00E5FF"));
        }

        String t = String.valueOf(new Date().getTime());
        String sign = md5(t + key);

        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(ServerConfigUtils.buildApiUrl(host, "/appHeart?t=" + t + "&sign=" + sign))
                .method("GET", null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isOk = false;
                        setConnectionState("OFFLINE", Color.parseColor("#FF6B6B"));
                        if (!silent) {
                            Toast.makeText(MainActivity.this, "心跳状态错误，请检查配置是否正确!", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String str = response.body().string();
                    JSONObject result = new JSONObject(str);
                    int code = result.getInt("code");
                    String msg = result.getString("msg");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (code == 1 && msg.equals("成功")) {
                                isOk = true;
                                setConnectionState("ONLINE", Color.parseColor("#33FF99"));
                                if (!silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                                            + "\r\r\r\r" + "心跳返回：" + msg
                                            + "\n心跳JSON：" + str);
                                }
                            } else {
                                isOk = false;
                                setConnectionState("OFFLINE", Color.parseColor("#FF6B6B"));
                                if (!silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                                            + "\r\r\r\r" + "心跳返回错误：" + msg
                                            + "\n心跳JSON：" + str);
                                }
                                if (!silent && msg.contains("冻结")) {
                                    Toast.makeText(MainActivity.this, "当前应用已被冻结，请联系管理员", Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                    });
                } catch (JSONException e) {
                    String error = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isOk = false;
                            setConnectionState("OFFLINE", Color.parseColor("#FF6B6B"));
                            if (!silent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\r\r\r\r" + "心跳错误：" + error);
                            }
                        }
                    });
                }
            }
        });
    }

    // 检测心跳
    public void doStart(View view) {
        SharedPreferences read = getSharedPreferences(PREF_SHINIAN, MODE_PRIVATE);
        host = read.getString(PREF_HOST, "");
        key = read.getString(PREF_KEY, "");

        if (!hasSavedServerConfig()) {
            setConnectionState("OFFLINE", Color.parseColor("#FF6B6B"));
            Toast.makeText(MainActivity.this, "请先配置服务器地址和通讯密钥!", Toast.LENGTH_SHORT).show();
            return;
        }

        requestHeartbeat(false, true);
    }

    // 检测监听
    // 通知渠道常量
    private static final String NOTIFICATION_CHANNEL_ID = "vmq_test_channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "ThunderCloud测试通知";
    private static final int MAX_NOTIFICATION_ID = 1000; // 防止 ID 无限增长

    /**
     * 发送测试推送通知
     *
     * @param v 触发视图
     */
    public void checkPush(View v) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    + "\r\r\r\r" + "开始检测通知监听权限...");
        }

        if (!isNotificationListenersEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                        + "\r\r\r\r" + "监听权限未开启，请在系统设置中允许通知使用权。");
            }
            Toast.makeText(this, "监听权限未开启，请先允许通知使用权", Toast.LENGTH_LONG).show();
            gotoNotificationAccessSetting();
            return;
        }

        refreshNotificationListenerService(this);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                        + "\r\r\r\r" + "通知服务不可用，无法发送测试通知。");
            }
            Toast.makeText(this, "通知服务不可用", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建通知渠道（Android O 及以上）
        createNotificationChannel(notificationManager);

        // 构建通知
        Notification notification = buildTestNotification();

        // 发送通知，ID 循环使用防止溢出
        int notificationId = id++;
        if (notificationId > MAX_NOTIFICATION_ID) {
            id = 0; // 重置 ID 计数器
        }
        notificationManager.notify(notificationId, notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    + "\r\r\r\r" + "测试通知已发送，正在等待监听服务回调。若没有后续“监听服务开启成功”日志，请检查系统是否拦截通知或重启 App。");
        }
        Toast.makeText(this, "已发送测试通知，请查看日志回调", Toast.LENGTH_SHORT).show();
    }

    /**
     * 创建通知渠道（Android O 及以上必需）
     *
     * @param notificationManager 通知管理器
     */
    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            channel.setShowBadge(true);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 构建测试通知
     *
     * @return 通知对象
     */
    private Notification buildTestNotification() {
        String title = "ThunderCloud测试推送";
        String content = "测试推送通知，如果程序正常，则会提示监听权限正常";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(content);
            // Android 10+ setTicker 已废弃，但为了兼容保留
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                builder.setTicker("测试推送信息，如果程序正常，则会提示监听权限正常");
            }
            return builder.build();
        } else {
            Notification.Builder builder = new Notification.Builder(this);
            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setTicker("测试推送信息，如果程序正常，则会提示监听权限正常");
            return builder.build();
        }
    }

    //清除所有日志
    public void Logs(View v) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                SharedPreferences.Editor editor = getSharedPreferences("items", MODE_PRIVATE).edit();
                editor.putString("logsStr", "");
                editor.commit();
                LogsTextView.setText("");

            }
        });
    }

    //获取电量onClick
    public void dl(View v) {
        //电量获取
        BatteryManager manager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        capacity = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);//当前电量剩余百分比
        sj_dl.setText("当前电量：" + capacity + "%");
    }

    //联系作者
    public void author(View v) {
        // 跳转之前，可以先判断手机是否安装 QQ
        if (isQQClientAvailable(this)) {
            // 跳转到客服的 QQ
            String url = "mqqwpa://im/chat?chat_type=wpa&uin=1614790395";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            // 跳转前先判断 Uri 是否存在，如果打开一个不存在的 Uri，App 可能会崩溃
            if (isValidIntent(this, intent)) {
                startActivity(intent);
            }
        }
    }

    //打开作者网站
    public void openAuthorWebsite(View v) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://shinian-a.github.io/"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开网页，请检查浏览器设置", Toast.LENGTH_SHORT).show();
        }
    }


    //监听日志
    private void sendMonitorLogs(String msgStr) {
        /**
         * 先读取logs日志存储数据
         */
        SharedPreferences read = getSharedPreferences("items", MODE_PRIVATE);
        String logsStr = MonitorLogUtils.keepRecentLogs(read.getString("logsStr", ""), MONITOR_LOG_RETENTION_MS);
        /**
         * 存储起来，在监听界面会用到
         */
        SharedPreferences.Editor editor = getSharedPreferences("items", MODE_PRIVATE).edit();
        logsStr = MonitorLogUtils.appendRecentLog(logsStr, msgStr, MONITOR_LOG_RETENTION_MS);
        editor.putString("logsStr", logsStr);
        editor.commit();

        //创建消息对象
        Message msg = new Message();
        msg.what = 0;
        Bundle bundle = new Bundle();
        bundle.putString("logsStr", logsStr);
        msg.setData(bundle);
        MainActivity.monitorLogHandler.sendMessage(msg);
    }


    //复制监听日志内容到剪贴板
    @Override
    public boolean onLongClick(View v) {
        int viewId = v.getId();

        if (viewId == R.id.state_logs) {
            String data = LogsTextView.getText().toString();
            copyStr(data);
            Toast.makeText(getApplication(), "Log日志已复制到剪贴板！", Toast.LENGTH_LONG).show();
        }

        //当返回true时，将不会产生连带触发，如点击事件
        return true;
    }


    //MD5取值
    public String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            String result = "";
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result += temp;
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    //判断读写权限
    private boolean pdPermissions() {
        boolean qx = false;
        qx = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        return qx;
    }

    //动态申请读写权限
    private void requestMyPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //没有授权，编写申请权限代码
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        } else {
            Log.d(TAG, "requestMyPermissions: 有写SD权限");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //没有授权，编写申请权限代码
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        } else {
            Log.d(TAG, "requestMyPermissions: 有写SD权限");
        }

    }

    // 刷新通知监听服务绑定，优先使用系统 rebind，避免频繁 disable/enable 引发系统解绑异常
    private void refreshNotificationListenerService(Context context) {
        ComponentName componentName = new ComponentName(context, PayNotificationListenerService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(componentName);
                Log.d(TAG, "已请求系统重新绑定通知监听服务");
                return;
            } catch (Exception e) {
                Log.w(TAG, "requestRebind 失败，回退到切换组件状态", e);
            }
        }

        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        Log.d(TAG, "已通过切换组件状态刷新通知监听服务");
    }

    //是否获取通知监听权限
    public boolean isNotificationListenersEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (int i = 0; i < names.length; i++) {
                final ComponentName cn = ComponentName.unflattenFromString(names[i]);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 是否启用通知监听服务
     *
     * @return
     */
    public boolean isNLServiceEnabled() {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(this);
        return packageNames.contains(getPackageName());
    }


    // 跳转到通知监听设置页面
    protected boolean gotoNotificationAccessSetting() {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;


        } catch (ActivityNotFoundException e) {//普通情况下找不到的时候需要再特殊处理找一次
            try {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName cn = new ComponentName("com.android.settings",
                        "com.android.settings.Settings$NotificationAccessSettingsActivity");
                intent.setComponent(cn);
                intent.putExtra(":settings:show_fragment", "NotificationAccessSettings");
                startActivity(intent);
                return true;
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            Toast.makeText(this, "对不起，您的手机暂不支持", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return false;
        }
    }




    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case AppConstants.REQ_PERM_CAMERA:
                // 摄像头权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的相机访问权限", Toast.LENGTH_LONG).show();
                }
                break;
            case AppConstants.REQ_PERM_EXTERNAL_STORAGE:
                // 文件读写权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的文件读写权限", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    //获取当前程序版本号
    public String getAppVersionCode() {
        String versioncode = "";
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            versioncode = String.valueOf(pi.versionCode);
            if (versioncode == null || versioncode.length() <= 0) {
                return "";
            }
        } catch (Exception e) {
            Log.e("VersionInfo", "Exception", e);
        }
        return versioncode;
    }

    //获取当前应用的版本名(展示给消费者的版本号)
    private String getAppVersionName() {
        // 获取packagemanager的实例
        PackageManager packageManager = getPackageManager();
        // getPackageName()是你当前类的包名，0代表是获取版本信息
        PackageInfo packInfo = null;
        try {
            packInfo = packageManager.getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String version = packInfo.versionName;
        return version;
    }

    //获取
    private boolean checkQQInstalled(Context context, String pkgName) {
        if (TextUtils.isEmpty(pkgName)) {
            return false;
        }
        try {
            context.getPackageManager().getPackageInfo(pkgName, 0);
        } catch (Exception x) {
            return false;
        }
        return true;
    }


    //主题
    private void setGraySheme(int gray) {
        View decorView = getWindow().getDecorView();
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(gray);//灰度效果 取值gray（0 - 1）  0是灰色，1取消灰色
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
    }
}
