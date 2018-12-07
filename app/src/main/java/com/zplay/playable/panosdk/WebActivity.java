package com.zplay.playable.panosdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

/**
 * Description:
 * <p>
 * Created by lgd on 2018/10/11.
 */

public class WebActivity extends Activity {
    private static final String TAG = "PA_no_SDK";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.web_activity);
        WebView.setWebContentsDebuggingEnabled(true);
        WebView webView = findViewById(R.id.web_view);
        String url = getIntent().getStringExtra("url");
        String data = getIntent().getStringExtra("data");
        String mimeType = getIntent().getStringExtra("mimeType");
        String encoding = getIntent().getStringExtra("encoding");


        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new ZPLAYAdsJavascriptInterface(), "ZPLAYAds");

        if (!TextUtils.isEmpty(url)) {
            webView.loadUrl(url);
        } else if (!TextUtils.isEmpty(data)) {
            webView.loadDataWithBaseURL(null, data, mimeType, encoding, null);
        } else {
            Toast.makeText(this, "oops~ not content to show.", Toast.LENGTH_SHORT).show();
        }
    }


    private class ZPLAYAdsJavascriptInterface {

        @JavascriptInterface
        public void onCloseSelected() {
            // 可玩广告点击关闭按钮时，触发该方法
            finish();
        }

        @JavascriptInterface
        public void onInstallSelected() {
            // 当点击"安装"按钮时，触发该方法
            final String marketLink = String.format("market://details?id=%s", "com.tencent.mm");
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(marketLink));
                startActivity(intent);
            } catch (Exception ignore) {
            }
        }

    }

    public static void launch(Context ctx, String url) {
        Intent i = new Intent(ctx, WebActivity.class);
        i.putExtra("url", url);
        ctx.startActivity(i);
    }

    public static void launch(Context ctx, String data, String mimeType, String encoding) {
        Intent i = new Intent(ctx, WebActivity.class);
        i.putExtra("data", data);
        i.putExtra("mimeType", mimeType);
        i.putExtra("encoding", encoding);
        ctx.startActivity(i);
    }

}
