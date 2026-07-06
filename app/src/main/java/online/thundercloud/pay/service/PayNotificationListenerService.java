package online.thundercloud.pay.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import online.thundercloud.pay.ui.MainActivity;
import online.thundercloud.pay.util.MonitorLogUtils;
import online.thundercloud.pay.util.ServerConfigUtils;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;


public class PayNotificationListenerService extends NotificationListenerService {

    private static final String TAG = "PayNotService";
    private static final long HEARTBEAT_SUCCESS_DELAY_MS = 50 * 1000L;
    private static final long HEARTBEAT_RETRY_DELAY_MS = 10 * 1000L;
    private static final long MONITOR_LOG_RETENTION_MS = 30 * 60 * 1000L;
    private String host = "";
    private String key = "";
    private Thread newThread = null;
    private PowerManager.WakeLock mWakeLock = null;
    private OkHttpClient okHttpClient; // 复用 OkHttpClient 实例
    private Handler mainHandler; // 复用主线程 Handler

    private void publishConnectionState(String state, int color) {
        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.PREF_SHINIAN, MODE_PRIVATE).edit();
        editor.putString(MainActivity.PREF_CONN_STATE, state);
        editor.putInt(MainActivity.PREF_CONN_COLOR, color);
        editor.apply();

        Intent intent = new Intent(MainActivity.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(MainActivity.EXTRA_CONNECTION_STATE, state);
        intent.putExtra(MainActivity.EXTRA_CONNECTION_COLOR, color);
        sendBroadcast(intent);
    }

    // MD5 加密
    public static String md5(String string) {
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

    // 释放设备电源锁
    public void releaseWakeLock() {
        if (null != mWakeLock) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    // 心跳进程
    public void initAppHeart() {
        Log.d(TAG, "开始启动心跳线程");

        // 防止重复启动
        if (newThread != null) {
            return;
        }
        // 申请设备电源锁
        acquireWakeLock(this);

        // 初始化复用的 OkHttpClient
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }

        newThread = new Thread(() -> {
            Log.d(TAG, "心跳线程启动！");
            while (true) {
                long nextDelayMs = HEARTBEAT_SUCCESS_DELAY_MS;
                SharedPreferences read = getSharedPreferences(MainActivity.PREF_SHINIAN, MODE_PRIVATE);
                host = read.getString(MainActivity.PREF_HOST, "");
                key = read.getString(MainActivity.PREF_KEY, "");
                if (TextUtils.isEmpty(host) || TextUtils.isEmpty(key)) {
                    Log.w(TAG, "心跳跳过：服务器配置为空");
                    try {
                        Thread.sleep(HEARTBEAT_RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }

                String t = String.valueOf(new Date().getTime());
                String sign = md5(t + key);
                Request request = new Request.Builder()
                        .url(ServerConfigUtils.buildApiUrl(host, "/appHeart?t=" + t + "&sign=" + sign))
                        .method("GET", null)
                        .build();

                try (Response response = okHttpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "onResponse heard: " + body);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                                + "\r\r\r\r" + "自动心跳返回"
                                + "\n心跳JSON：" + body);
                    }
                    try {
                        JSONObject result = new JSONObject(body);
                        int code = result.optInt("code");
                        String msg = result.optString("msg");
                        if (code == 1) {
                            publishConnectionState("ONLINE", 0xFF33FF99);
                        } else {
                            nextDelayMs = HEARTBEAT_RETRY_DELAY_MS;
                            publishConnectionState("OFFLINE", 0xFFFF6B6B);
                            if (msg.contains("冻结")) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                                                    + "\r\r\r\r" + "心跳返回：当前应用已被冻结，请联系管理员");
                                        }
                                        Toast.makeText(getApplicationContext(), "当前应用已被冻结，请联系管理员", Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    } catch (JSONException e) {
                        nextDelayMs = HEARTBEAT_RETRY_DELAY_MS;
                        publishConnectionState("OFFLINE", 0xFFFF6B6B);
                        Log.w(TAG, "心跳返回解析失败: " + e.getMessage());
                    }
                } catch (IOException e) {
                    nextDelayMs = HEARTBEAT_RETRY_DELAY_MS;
                    final String error = e.getMessage();
                    publishConnectionState("OFFLINE", 0xFFFF6B6B);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {

                            if (MainActivity.LogsTextView != null && !MainActivity.LogsTextView.getText().toString().contains("心跳状态错误")) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                                            + "\r\r\r\r" + "心跳状态错误，请重新配置或切换网络环境!\n错误详情：" + error);
                                }
                                Toast.makeText(getApplicationContext(), "心跳状态错误，请重新配置或切换网络!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }

                try {
                    Thread.sleep(nextDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        newThread.start();
    }


    // 支付平台包名常量
    private static final String PACKAGE_WECHAT = "com.tencent.mm";
    private static final String PACKAGE_WECHAT_WORK = "com.tencent.wework";
    private static final String PACKAGE_ALIPAY = "com.eg.android.AlipayGphone";
    private static final String PACKAGE_SELF = "online.thundercloud.pay";
    
    // 微信支付标题关键字
    private static final String[] WECHAT_PAY_TITLES = {
        "微信支付", "微信收款助手", "微信收款商业版", "对外收款", "企业微信","Weixin Cashier Assistant"
    };
    
    // 金额提取正则表达式（预编译提升性能）
    private static final java.util.regex.Pattern MONEY_PATTERN = 
            java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?");

    /**
     * 当收到一条消息的时候回调，sbn 即收到的消息
     */
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 获取通知对象和包名（只获取一次）
        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        
        if (notification == null) {
            return;
        }
        
        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }
        
        String title = extras.getString(NotificationCompat.EXTRA_TITLE, "");
        String content = extras.getString(NotificationCompat.EXTRA_TEXT, "");
        Log.d(TAG, "包名: " + pkg);

        if (shouldLogPaymentNotification(pkg, title, content, extras)) {
            logRawNotification(pkg, sbn, notification, extras, title, content);
        }

        if (PACKAGE_SELF.equals(pkg)) {
            handleSelfTestNotification(content);
            return;
        }

        // 获取配置（只在需要推送到后台时读取）
        SharedPreferences read = getSharedPreferences("shinian", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(key)) {
            Log.w(TAG, "appPush: 服务器配置为空，跳过回调");
            return;
        }

        // 根据包名分发处理逻辑
        if (PACKAGE_WECHAT.equals(pkg) || PACKAGE_WECHAT_WORK.equals(pkg)) {
            handleWechatNotification(title, content, extras);
        } else if (PACKAGE_ALIPAY.equals(pkg)) {
            handleAlipayNotification(title, content);
        }
    }

    private boolean shouldLogPaymentNotification(String pkg, String title, String content, Bundle extras) {
        if (PACKAGE_ALIPAY.equals(pkg)) {
            return isAlipayPaymentNotification(title, content);
        }
        if (PACKAGE_WECHAT.equals(pkg) || PACKAGE_WECHAT_WORK.equals(pkg)) {
            return isWechatPaymentNotification(title, content, extras);
        }
        return false;
    }

    private boolean isAlipayPaymentNotification(String title, String content) {
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            return false;
        }
        boolean isNormalPay = (title.contains("成功收款") && content.contains("已转入余额"))
                || content.contains("通过扫码向你付款");
        boolean isStaffPay = title.contains("店员通") || content.contains("支付宝成功收款");
        return isNormalPay || isStaffPay;
    }

    private boolean isWechatPaymentNotification(String title, String content, Bundle extras) {
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            return false;
        }

        boolean isPayTitle = false;
        for (String payTitle : WECHAT_PAY_TITLES) {
            if (!TextUtils.isEmpty(title) && title.contains(payTitle)) {
                isPayTitle = true;
                break;
            }
        }
        if (!isPayTitle) {
            return false;
        }

        String bigText = extraText(extras, "android.bigText");
        String textLines = formatExtraValue(extras.get("android.textLines"));
        String mergedText = title + "\n" + content + "\n" + bigText + "\n" + textLines;
        return mergedText.contains("收款") && !mergedText.contains("已支付");
    }

    private void logRawNotification(String pkg, StatusBarNotification sbn, Notification notification, Bundle extras, String title, String content) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatNow()).append("\r\r\r\r")
                .append("原始通知捕捉")
                .append("\n包名：").append(pkg)
                .append("\n通知ID：").append(sbn.getId())
                .append("\n通知Tag：").append(emptyToNone(sbn.getTag()))
                .append("\n通知Key：").append(emptyToNone(sbn.getKey()))
                .append("\n发布时间：").append(formatTimeMillis(sbn.getPostTime()))
                .append("\n标题：").append(emptyToNone(title))
                .append("\n正文：").append(emptyToNone(content))
                .append("\nTicker：").append(emptyToNone(notification.tickerText))
                .append("\nBigText：").append(extraText(extras, "android.bigText"))
                .append("\nSubText：").append(extraText(extras, "android.subText"))
                .append("\nInfoText：").append(extraText(extras, "android.infoText"))
                .append("\nSummaryText：").append(extraText(extras, "android.summaryText"))
                .append("\nTextLines：").append(formatExtraValue(extras.get("android.textLines")))
                .append("\nExtras：").append(formatExtras(extras));

        String rawLog = builder.toString();
        Log.d(TAG, rawLog);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sendMonitorLogs(rawLog);
        }
    }

    private String formatExtras(Bundle extras) {
        Set<String> keys = extras.keySet();
        if (keys == null || keys.isEmpty()) return "无";
        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            builder.append("\n  ").append(key).append(" = ").append(formatExtraValue(extras.get(key)));
        }
        return builder.toString();
    }

    private String formatExtraValue(Object value) {
        if (value == null) return "无";
        if (value instanceof CharSequence[]) {
            CharSequence[] items = (CharSequence[]) value;
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < items.length; i++) {
                if (i > 0) builder.append(", ");
                builder.append(emptyToNone(items[i]));
            }
            builder.append("]");
            return limitText(builder.toString());
        }
        if (value instanceof Object[]) {
            Object[] items = (Object[]) value;
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < items.length; i++) {
                if (i > 0) builder.append(", ");
                builder.append(items[i]);
            }
            builder.append("]");
            return limitText(builder.toString());
        }
        return limitText(String.valueOf(value));
    }

    private String extraText(Bundle extras, String key) {
        return emptyToNone(extras.getCharSequence(key));
    }

    private String emptyToNone(CharSequence value) {
        if (value == null || TextUtils.isEmpty(value.toString())) return "无";
        return limitText(value.toString());
    }

    private String limitText(String value) {
        if (value == null) return "无";
        return value.length() > 300 ? value.substring(0, 300) + "...[已截断]" : value;
    }

    private String formatNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }
        return String.valueOf(new Date().getTime());
    }

    private String formatTimeMillis(long timeMillis) {
        if (timeMillis <= 0) return "无";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timeMillis));
        }
        return String.valueOf(timeMillis);
    }
    
    /**
     * 从文本内容中提取金额数字
     * @param content 包含金额的文本内容
     * @return 提取到的金额字符串，如果未找到则返回 null
     */
    public static String getMoney(String content) {
        // 空值检查
        if (content == null || content.isEmpty()) return null;
        List<String> validAmounts = new ArrayList<>();
        String targetText = content;
        int shoukuanIndex = content.indexOf("收款");
        if (shoukuanIndex >= 0) {
            targetText = content.substring(Math.max(0, shoukuanIndex - 12));
        }

        java.util.regex.Matcher matcher = MONEY_PATTERN.matcher(targetText);

        while (matcher.find()) {
            String matched = matcher.group();
            if (isValidNumber(matched)) {
                try {
                    double amount = Double.parseDouble(matched);
                    // 金额范围
                    if (amount >= 0.01 && amount <= 999999.99) {
                        validAmounts.add(matched);
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "getMoney: 匹配到无效金额：" + matched);
                }
            }
        }

        if (!validAmounts.isEmpty()) return validAmounts.get(0);
        return null;
    }
    
    /**
     * 处理支付宝收款通知
     */
    private void handleAlipayNotification(String title, String content) {
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            return;
        }

        String money = null;
        String platform = "";
        int platformType = 2;

        boolean isNormalPay = (title.contains("成功收款") && content.contains("已转入余额"))
                             || content.contains("通过扫码向你付款");
        boolean isStaffPay = title.contains("店员通") || content.contains("支付宝成功收款");

        if (isNormalPay) {
            // 普通收款：优先从标题获取金额
            platform = "支付宝";
            money = getMoney(title);
            if (money == null || money.isEmpty()) {
                money = getMoney(content);
            }
        } else if (isStaffPay) {
            // 店员通收款：优先从内容获取金额
            platform = "支付宝店员";
            platformType = 2; // 保持和普通支付宝一致
            money = getMoney(content);
            if (money == null || money.isEmpty()) {
                money = getMoney(title);
            }
        } else {
            return; // 不匹配的收款类型，直接返回
        }
        
        // 重试一次避免掉单
        if (money == null || money.isEmpty()) {
            if (isNormalPay) {
                money = getMoney(content);
            } else {
                money = getMoney(title);
            }
        }
        
        if (money != null && !money.isEmpty()) {
            try {
                double amount = Double.parseDouble(money);
                Toast.makeText(this, "匹配成功：" + platform + "到账" + money + "元", Toast.LENGTH_LONG).show();
                Log.d(TAG, "onAccessibilityEvent: 匹配成功：" + platform + "到账 " + money + "元");
                appPush(platformType, amount);
            } catch (NumberFormatException e) {
                Log.e(TAG, "解析" + platform + "金额失败：" + money, e);
                showMoneyParseErrorToast(platform);
            }
        } else {
            showMoneyParseErrorToast(platform);
        }
    }
    
    /**
     * 处理自检测试通知
     */
    private void handleSelfTestNotification(String content) {
        if ("测试推送通知，如果程序正常，则会提示监听权限正常".equals(content)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) 
                               + "\r\r\r\r" + "测试收款监听权限正常！");
            }
        }
    }
    
    /**
     * 显示金额解析错误提示（避免频繁弹出 Toast）
     */
    private void showMoneyParseErrorToast(String platform) {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        
        mainHandler.post(() -> 
            Toast.makeText(getApplicationContext(), 
                "监听到" + platform + "收款消息但未匹配到金额！", 
                Toast.LENGTH_SHORT).show()
        );
    }

    //当移除一条消息的时候回调，sbn是被移除的消息
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    // 申请设备电源锁
    @SuppressLint("InvalidWakeLockTag")
    public void acquireWakeLock(Context context) {
        if (null == mWakeLock) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "WakeLock");
            if (null != mWakeLock) {
                mWakeLock.acquire();
            }
        }
    }

    /**
     * 推送收款通知到服务器
     * @param type 支付类型：1-微信，2-支付宝
     * @param price 收款金额
     */
    public void appPush(final int type, final double price) {
        SharedPreferences read = getSharedPreferences("shinian", MODE_PRIVATE);
        host = read.getString("host", "");
        key = read.getString("key", "");
    
        // 格式化价格，避免精度问题（例如：0.1 变成 0.10000000000000000555）
        String priceStr = String.format("%.2f", price);
            
        Log.d(TAG, "appPush: 开始 - 类型:" + type + ", 金额:" + priceStr);
    
        // 构建请求 URL
        String t = String.valueOf(new Date().getTime());
        String sign = md5(type + priceStr + t + key);
        String url = buildPushUrl(host, type, priceStr, t, sign);
            
        Log.d(TAG, "appPush: URL:" + url);
    
        // 使用复用的 OkHttpClient 实例
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }
    
        Request request = new Request.Builder().url(url).method("GET", null).build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String error = e.getMessage();
                Log.e(TAG, "appPush: 请求失败 - " + error);
                    
                // 发送失败日志
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) 
                            + "\r\r\r\r" + "通知回调失败：" + error);
                }
                    
                // 请求失败后延迟 1 秒自动补回调
                scheduleRetryCallback(type, price, priceStr);
            }
    
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String str = response.body().string();
                    JSONObject result = new JSONObject(str);
                    String msg = result.getString("msg");
                        
                    // 根据支付类型记录日志
                    logPushResult(type, priceStr, msg, str, false);
                        
                    // 通知回调成功后，清除对应的通知消息（防止通知栏堆积）
                    if ("成功".equals(msg)) {
                        cancelNotification(type);
                    }
                        
                } catch (JSONException e) {
                    String error = e.getMessage();
                    Log.e(TAG, "appPush: JSON解析失败 - " + error);
                        
                    // 发送失败日志
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) 
                                + "\r\r\r\r" + "通知回调失败：" + error);
                    }
                        
                    // JSON解析失败后延迟 1 秒自动补回调
                    scheduleRetryCallback(type, price, priceStr);
                }
            }
        });

    }

    /**
     * 清除支付通知（防止通知栏堆积）
     * @param type 支付类型：1-微信，2-支付宝
     */
    private void cancelNotification(int type) {
        try {
            // 获取所有活跃的通知
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            if (activeNotifications == null || activeNotifications.length == 0) {
                return;
            }

            // 根据支付类型确定要清除的通知包名
            String targetPackage = (type == 1) ? PACKAGE_WECHAT : PACKAGE_ALIPAY;

            // 遍历并清除指定包名的通知
            for (StatusBarNotification sbn : activeNotifications) {
                if (sbn != null && targetPackage.equals(sbn.getPackageName())) {
                    // 取消该通知
                    cancelNotification(sbn.getKey());
                    Log.d(TAG, "cancelNotification: 已清除" + (type == 1 ? "微信" : "支付宝") +
                            "通知 - " + sbn.getPackageName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "cancelNotification: 清除通知失败 - " + e.getMessage());
        }
    }

    /**
     * 构建推送 URL（消除重复代码）
     */
    private String buildPushUrl(String host, int type, String priceStr, String t, String sign) {
        return ServerConfigUtils.buildApiUrl(host, "/appPush?t=" +
                t +
                "&type=" +
                type +
                "&price=" +
                priceStr +
                "&sign=" +
                sign);
    }

    /**
     * 记录推送结果日志（消除重复代码）
     */
    private void logPushResult(int type, String priceStr, String msg, String data, boolean isRetry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String prefix = isRetry ? "自动补回调：" : "";
            String payType = (type == 1) ? "微信支付" : "支付宝";
            String logContent = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                    + "\r\r\r\r"
                    + prefix + "监听到" + payType + "收款" + priceStr + "元"
                    + "\t" + "通知回调状态：" + msg
                    + "\n" + "通知回调信息：" + data;
            sendMonitorLogs(logContent);
        }
    }
        
    /**
     * 延迟重试回调（消除重复代码）
     */
    private void scheduleRetryCallback(final int type, final double price, final String priceStr) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    String t = String.valueOf(new Date().getTime());
                    if (TextUtils.isEmpty(host) || TextUtils.isEmpty(key)) {
                        Log.w(TAG, "appPush: 补回调跳过，服务器配置为空");
                        return;
                    }
                    String sign = md5(type + priceStr + t + key);
                    String url = buildPushUrl(host, type, priceStr, t, sign);
                        
                    // 同步请求（在子线程中执行）
                    String data = getHtml(url);
                        
                    // 解析响应
                    JSONObject jsonObject = new JSONObject(data);
                    int code = jsonObject.getInt("code");
                    String message = jsonObject.getString("msg");
                        
                    if (code == 1 && "成功".equals(message)) {
                        // 记录补回调日志
                        logPushResult(type, priceStr, message, data, true);
                            
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), 
                                        "补通知回调成功：" + data, 
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        Log.w(TAG, "appPush: 补回调失败 - code:" + code + ", msg:" + message);
                    }
                } catch (Exception e) {
                    String error = e.getMessage();
                    Log.e(TAG, "appPush: 补回调异常 - " + error);
                        
                    final String finalError = error;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplication(), 
                                    "自动补单回调失败！联系作者反馈\n错误详情：" + finalError, 
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, 1000); // 延迟 1 秒重试
    }

    /**
     * 验证字符串是否为合法的数字格式
     * @param str 待验证的字符串
     * @return true ，false
     */
    private static boolean isValidNumber(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        // 不能以小数点开头或结尾，且只能包含一个小数点
        int dotCount = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '.') {
                dotCount++;
                // 不能有超过一个小数点，或者小数点在开头/结尾
                if (dotCount > 1 || i == 0 || i == str.length() - 1) {
                    return false;
                }
            } else if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 处理微信收款通知
     */
    private void handleWechatNotification(String title, String content, Bundle extras) {
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) return;
        if (!isWechatPaymentNotification(title, content, extras)) return;

        String bigText = extraText(extras, "android.bigText");
        String textLines = formatExtraValue(extras.get("android.textLines"));
        String mergedText = title + "\n" + content + "\n" + bigText + "\n" + textLines;

        String money = firstMoney(
                content,
                bigText,
                textLines,
                title,
                mergedText
        );

        if (money != null && !money.isEmpty()) {
            try {
                double amount = Double.parseDouble(money);
                Toast.makeText(this, "匹配成功：微信到账" + money + "元", Toast.LENGTH_LONG).show();
                Log.d(TAG, "onAccessibilityEvent: 匹配成功：微信到账 " + money + "元");
                appPush(1, amount);
            } catch (NumberFormatException e) {
                Log.e(TAG, "解析微信金额失败：" + money, e);
                showMoneyParseErrorToast("微信");
            }
        } else {
            showMoneyParseErrorToast("微信");
        }
    }

    private String firstMoney(String... texts) {
        for (String text : texts) {
            String money = getMoney(text);
            if (!TextUtils.isEmpty(money)) {
                return money;
            }
        }
        return null;
    }

    // 监听服务连接成功时回调初始化心跳线程
    @Override
    public void onListenerConnected() {
        // 初始化主线程 Handler（只初始化一次）
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }

        // 初始化心跳线程
        initAppHeart();
        //延迟发送监听日志
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sendMonitorLogs(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                            + "\r\r\r\r" + "监听服务开启成功！");
                }
            }
        }, 1000);
    }

    // 监听日志
    private void sendMonitorLogs(String msgStr) {
        SharedPreferences read = getSharedPreferences("items", MODE_PRIVATE);
        String logsStr = MonitorLogUtils.keepRecentLogs(read.getString("logsStr", ""), MONITOR_LOG_RETENTION_MS);

        // 追加新日志
        logsStr = MonitorLogUtils.appendRecentLog(logsStr, msgStr, MONITOR_LOG_RETENTION_MS);
            
        SharedPreferences.Editor editor = getSharedPreferences("items", MODE_PRIVATE).edit();
        editor.putString("logsStr", logsStr);
        editor.commit();
    
        //创建消息对象并发送
        Message msg = new Message();
        msg.what = 0;
        Bundle bundle = new Bundle();
        bundle.putString("logsStr", logsStr);
        msg.setData(bundle);
        MainActivity.monitorLogHandler.sendMessage(msg);
    }

    // 获取 HTML（确保资源正确关闭）
    public String getHtml(String path) throws Exception {
        HttpURLConnection conn = null;
        InputStream inStream = null;
        try {
            URL url = new URL(path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8 * 1000);
                
            //通过输入流获取 html 数据
            inStream = conn.getInputStream();
            //获取 html 的二进制数组
            byte[] data = readInputStream(inStream);
            //获取指定字符集解码指定的字节数组构造一个新的字符串
            return new String(data, StandardCharsets.UTF_8);
        } finally {
            // 关闭输入流
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭输入流失败", e);
                }
            }
            // 断开连接
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // 读取输入流 获取 HTML 二进制数组（调用后需关闭输入流）
    public byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        return outStream.toByteArray();
    }


}
