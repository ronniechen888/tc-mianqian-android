package online.thundercloud.pay.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import online.thundercloud.pay.R;

public class HelpActivity extends AppCompatActivity {

    private static final String HELP_URL = "https://paysys.thunder-cloud.online/tutorial";
    private WebView helpWebView;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
		// 隐藏系统标题栏
		if (getSupportActionBar() != null) {
			getSupportActionBar().hide(); // 隐藏标题栏
		}
		
		setContentView(R.layout.activity_help);

        helpWebView = findViewById(R.id.help_webview);
        if (helpWebView != null) {
            WebSettings settings = helpWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(false);
            helpWebView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                }
            });
            helpWebView.loadUrl(HELP_URL);
        }
		
		// 设置返回按钮点击事件
		View btnBack = findViewById(R.id.btn_back);
		if (btnBack != null) {
			btnBack.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					finish(); // 返回上一层
				}
			});
		}
    }

    @Override
    public void onBackPressed() {
        if (helpWebView != null && helpWebView.canGoBack()) {
            helpWebView.goBack();
            return;
        }
        super.onBackPressed();
    }
    
}
