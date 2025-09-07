package com.flying.whitefox.ui.webview;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.flying.whitefox.BuildConfig;
import com.flying.whitefox.R;
import com.flying.whitefox.databinding.ActivityWebviewBinding;
import com.google.android.material.switchmaterial.SwitchMaterial;

import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

public class WebViewActivity extends AppCompatActivity {

    private static final String EXTRA_URL = "extra_url";

    private ActivityWebviewBinding binding;
    private WebView webView;
    private ProgressBar progressBar;
    private ProgressBar initialLoadingIndicator;
    private TextView tvWebTitle;
    private SwitchMaterial switchAdBlock;
    private String url;

    public static void start(Context context, String url) {
        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra(EXTRA_URL, url);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWebviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 启用WebView调试（仅在调试模式下）
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        initViews();
        initToolbar();
        getIntentData();
        setupWebView();
        loadUrl();
    }

    private void initViews() {
        webView = binding.webview;
        progressBar = binding.progressBar;
        initialLoadingIndicator = binding.initialLoadingIndicator;
        tvWebTitle = binding.tvWebTitle;
        switchAdBlock = binding.switchAdBlock;
    }

    private void initToolbar() {
        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void getIntentData() {
        url = getIntent().getStringExtra(EXTRA_URL);
        Log.i("getIntentData", "Received URL: " + url);
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // 启用更多WebView功能以支持现代网站
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.i("WebView", "Loading URL: " + url);
                
                // 阻止重定向到应用的自定义协议
                if (url.startsWith("zhihu://") || url.startsWith("zhuanlan://") || 
                    url.startsWith("bilibili://") || url.startsWith("immomo://") ||
                    url.startsWith("xiami://") || url.startsWith("weibo://") ||
                    url.startsWith("tbopen://") || url.startsWith("youku://") ||
                    url.startsWith("qzone://") || url.startsWith("tenvideo://") ||
                    url.startsWith("pptv://") || url.startsWith("mgtv://") ||
                    url.startsWith("bytedance://")) {
                    Log.i("WebView", "Blocked redirect to app: " + url);
                    return true; // 阻止加载
                }
                
                // 处理市场链接
                if (url.startsWith("market://")) {
                    Log.i("WebView", "Blocked market link: " + url);
                    return true;
                }
                
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.i("WebView", "Page started: " + url);
                // 隐藏初始加载指示器
                initialLoadingIndicator.setVisibility(View.GONE);
                // 显示进度条
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i("WebView", "Page finished: " + url);
                progressBar.setVisibility(View.GONE);
                tvWebTitle.setText(view.getTitle());
                
                // 注入JavaScript插件
                injectJavascriptPlugins();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e("WebView", "Error loading page: " + error.getDescription());
                progressBar.setVisibility(View.GONE);
                initialLoadingIndicator.setVisibility(View.GONE);
                tvWebTitle.setText("Error loading page");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(title);
                }
                tvWebTitle.setText(title);
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                Log.i("WebView Console", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return super.onConsoleMessage(consoleMessage);
            }
        });
    }

    /**
     * 注入JavaScript插件
     */
    private void injectJavascriptPlugins() {
        // 示例：注入一个简单的广告屏蔽插件
        String adBlockScript = 
            "javascript:(function() {" +
            "  var styles = 'div[class*=\"ad\"], div[id*=\"ad\"], iframe[src*=\"ads\"], ' +" +
            "               '.advertisement, .ads, .sponsor { display: none !important; }';" +
            "  var styleSheet = document.createElement('style');" +
            "  styleSheet.innerText = styles;" +
            "  document.head.appendChild(styleSheet);" +
            "})()";
        
        // 注入用户自定义脚本
        String userScript = readAssetFile("userscript.js");
        if (userScript != null && !userScript.isEmpty()) {
            webView.loadUrl("javascript:" + userScript);
        }
        
        // 根据开关状态决定是否注入广告屏蔽脚本
        if (switchAdBlock != null && switchAdBlock.isChecked()) {
            webView.loadUrl(adBlockScript);
        }
    }
    
    /**
     * 读取assets目录下的JavaScript文件
     * @param fileName 文件名
     * @return 文件内容
     */
    private String readAssetFile(String fileName) {
        AssetManager assetManager = getAssets();
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open(fileName);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } catch (IOException e) {
            Log.e("WebView", "Error reading asset file: " + fileName, e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e("WebView", "Error closing input stream", e);
                }
            }
        }
    }

    private void loadUrl() {
        Log.i("loadUrl", "Attempting to load URL: " + url);
        if (url != null && !url.isEmpty()) {
            Log.i("loadUrl", "Loading URL: " + url);
            // 显示初始加载指示器
            initialLoadingIndicator.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        } else {
            Log.e("loadUrl", "URL is null or empty");
            initialLoadingIndicator.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
//        if (id == R.id.action_refresh) {
//            webView.reload();
//            return true;
//        } else if (id == R.id.action_open_in_browser) {
//            // TODO: 实现在浏览器中打开功能
//            return true;
//        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.loadUrl("about:blank");
            webView.onPause();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}