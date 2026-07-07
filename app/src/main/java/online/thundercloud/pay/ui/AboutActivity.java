package online.thundercloud.pay.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import online.thundercloud.pay.R;

/**
 * 关于软件文档阅读界面
 */
public class AboutActivity extends AppCompatActivity {
    
    private TextView versionNameView;
    private TextView versionCodeView;
    private TextView contentView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        
        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // 查找视图并设置返回按钮点击事件
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        
        versionNameView = findViewById(R.id.about_version_name);
        versionCodeView = findViewById(R.id.about_version_code);
        contentView = findViewById(R.id.about_content);
        
        // 设置文本可滚动
        contentView.setMovementMethod(ScrollingMovementMethod.getInstance());
        
        // 设置版本信息
        setVersionInfo();
        
        // 加载并显示文档内容
        loadDocumentContent();

    }
    
    /**
     * 设置版本信息
     */
    private void setVersionInfo() {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(), 0);
            
            String versionName = packInfo.versionName;
            int versionCode = packInfo.versionCode;
            
            versionNameView.setText("V" + versionName);
            versionCodeView.setText("版本号：" + versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            versionNameView.setText("未知版本");
            versionCodeView.setText("版本号：未知");
            e.printStackTrace();
        }
    }
    
    /**
     * 加载文档内容
     */
    private void loadDocumentContent() {
        contentView.setText(getSoftwareIntroContent());
    }
    
    /**
     * 获取软件简介内容
     */
    public static String getSoftwareIntroContent() {
        return "【ThunderCloud免签支付监控端】\n\n" +
               "一款基于 V 免签方案开发的 Android 收款监听应用,主要是让刚起步的网站和门店顺利接入个人收款码和个人经营码，这里主要提供学学和测试使用。和该程序搭配的后台系统可以直接使用作者部署的网站: https://tcpay.online 也可以联系作者进行私有化部署来达到更好的收款效果\n\n" +
               "【核心功能】\n" +
               "• 双平台监听：支持支付宝和微信收款通知监听\n" +
               "• 智能回调：匹配服务端订单金额后自动触发回调\n" +
               "• 日志面板：实时查看监听日志和回调记录\n" +
               "• 店员管理：支持店员监听功能\n" +
               "• 持久运行：电池白名单保护，后台稳定运行\n" +
               "• 性能优化：精简代码结构，启动速度提升\n\n" +
               "【技术信息】\n" +
               "开发语言：Java + XML\n" +
               "目标平台：Android 5.0+ (API 21)\n" +
               "编译 SDK: Android 36\n" +
               "构建工具：Android Gradle Plugin 8.6.1 / Gradle 8.7\n\n" +
               "【开发者信息】\n" +
               "开发者：Ronnie\n" +
               "邮箱：576892817@qq.com\n\n" +
               "GitHub: https://github.com/ronniechen888/tc-mianqian-android\n\n" +
               "【版权声明】\n" +
               "Copyright © 2026 tcpay.online · 版权所有\n\n" +
               "本项目基于 V 免签方案开发，感谢原作者的贡献。\n\n" +
               "【使用协议】\n" +
               "1. 本软件仅供学习交流使用\n" +
               "2. 请勿将本软件用于非法用途\n" +
               "3. 使用本软件产生的任何责任由使用者承担\n" +
               "4. 请遵守当地法律法规";
    }
    
    /**
     * 点击访问 GitHub
     */
    public void openGitHub(View v) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/ronniechen888/tc-mianqian-android"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 点击联系开发者
     */
    public void contactDeveloper(View v) {
        try {
            AlertDialogUtil.showAlertDialog(this, "联系方式", 
                "邮箱：576892817@qq.com\n\n" +
                "如有任何问题或建议，欢迎联系！");
        } catch (Exception e) {
            Toast.makeText(this, "显示失败", Toast.LENGTH_SHORT).show();
        }
    }
}
