package com.zplay.playable.panosdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.zplay.playable.utils.UserConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Description:
 * <p>
 * mraid 注入过程参考资料：
 * https://www.programcreek.com/java-api-examples/index.php?source_dir=MobFox-Android-SDK-master/src/main/java/com/adsdk/sdk/mraid/MraidView.java
 * <p>
 * 原理为，使用 <head><script>mraid.js</script> 替换 HTML 中的 <head> 标签
 * <p>
 * <p>
 * 还有一种注入方法，是直接使用 webview.evaluateJavascript(js, new ValueCallback<String>(..)) 来注入
 * 如：https://github.com/nexage/sourcekit-mraid-android/blob/master/src/src/org/nexage/sourcekit/mraid/MRAIDView.java
 * <p>
 * 实践过程中，直接使用 webview.evaluateJavascript 注入，在执行 webview.load(htmlDocUrl) 后，在 htmlDocUrl
 * 里无法找到 window.mraid 对象；先执行 webview.load(htmlDocUrl) ，然后在 onPageFinished 回调中执行 webview.evaluateJavascript，
 * 可以在 htmlDocUrl 中找到 window.mraid 对象，但此对象应在加载完成之前添入到 htmlDocUrl，所以没有使用这个方法。
 * <p>
 * <p>
 * Created by lgd on 2018/10/11.
 */

public class WebViewController {
    private static final String TAG = "WebViewController";
    public static final String FUNCTION1 = "function1";
    public static final String FUNCTION2 = "function2";

    private static Map<String, WebViewController> sWebViewControllerMap = new HashMap<>();

    private WebView mWebView;
    private String mHtmlData;
    private String mTargetUrl;

    private WebViewListener mWebViewListener;
    private WebViewJSListener mWebViewJSListener;
    private boolean isCachedHtmlData;

    public WebViewController(@NonNull Context context, int supportFunctionCode) {

        mWebView = new WebView(context);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        if (supportFunctionCode == 2) {
            mWebView.addJavascriptInterface(new ZPLAYAdsJavascriptInterface(), "ZPLAYAds");
        }


        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isCachedHtmlData = true;
                if (mWebViewListener != null) {
                    mWebViewListener.onPageFinished(view, url);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (mWebViewListener != null) {
                    mWebViewListener.onReceivedError(view, request, error);
                }
            }
        });
    }


    public void preRenderHtml(@NonNull String htmlData, @Nullable WebViewListener listener) {
        if (mWebView == null) {
            Log.d(TAG, "preRenderHtml: webview already destroy, recreated it again.");
            return;
        }

        mWebViewListener = listener;
        if (htmlData.startsWith("http")) {
            loadUrl(mWebView, htmlData);
        } else {
            loadHtmlData(mWebView, htmlData);
        }
    }


    public static void loadHtmlData(WebView webView, String data) {
        if (data == null) {
            return;
        }

        if (!UserConfig.getInstance(null).isSupportMraid()) {
            webView.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
            return;
        }

        // If the string data lacks the HTML boilerplate, add it.
        if (!data.contains("<html>")) {
            data = "<html><head></head><body style='margin:0;padding:0;'>" + data +
                    "</body></html>";
        }

        // Inject the MRAID JavaScript bridge.
        data = data.replace("<head>", "<head><script>" + Assets.MRAID_JS + "</script>");

        webView.loadDataWithBaseURL(null, data, "text/html", "UTF-8", null);
    }

    public static void loadUrl(WebView webView, String url) {
        if (url == null) {
            return;
        }

        if (url.startsWith("javascript:")) {
            webView.loadUrl(url);
            return;
        }

        if (!UserConfig.getInstance(null).isSupportMraid()) {
            webView.loadUrl(url);
            return;
        }

        fetchHtmlAndLoad(webView, url);
    }

    private static void fetchHtmlAndLoad(final WebView webView, @NonNull final String urlStr) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream inStream;
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    inStream = conn.getInputStream();
                    if (inStream == null) {
                        throw new IOException("http request input stream is null.");
                    }
                } catch (IOException e) {
                    Log.d(TAG, e.getMessage());
                    return;
                }
                final String html;
                try {
                    byte[] data = readInputStream(inStream);
                    html = new String(data, StandardCharsets.UTF_8);
                    if (webView == null || TextUtils.isEmpty(html)) {
                        Log.d(TAG, "fetch html doc is null.");
                        return;
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            loadHtmlData(webView, html);
                        }
                    });
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        }).start();
    }

    private static byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        inStream.close();
        return outStream.toByteArray();
    }

    public void setHtmlData(String htmlData) {
        mHtmlData = htmlData;
    }

    public String getHtmlData() {
        return TextUtils.isEmpty(mHtmlData) ? "" : mHtmlData;
    }

    public void setTargetUrl(String targetUrl) {
        mTargetUrl = targetUrl;
    }

    public String getTargetUrl() {
        return TextUtils.isEmpty(mTargetUrl) ? "" : mTargetUrl;
    }

    public void setWebViewJSListener(WebViewJSListener webViewJSListener) {
        this.mWebViewJSListener = webViewJSListener;
    }

    public boolean isCachedHtmlData() {
        return isCachedHtmlData;
    }

    @Nullable
    public WebView getWebView() {
        return mWebView;
    }

    public void destroy() {
        isCachedHtmlData = false;
        if (mWebView == null) {
            Log.d(TAG, "destroy: webivew is null");
            return;
        }
        if (mWebView.getParent() != null && mWebView.getParent() instanceof ViewGroup) {
            ((ViewGroup) mWebView.getParent()).removeView(mWebView);
        }
        mWebView.destroy();

    }

    public interface WebViewListener {
        void onPageFinished(WebView view, String url);

        void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error);
    }

    public static void storeWebViewController(@NonNull String id, WebViewController webViewController) {
        sWebViewControllerMap.put(id, webViewController);
    }

    public static WebViewController getWebViewController(@NonNull String id) {
        if (!sWebViewControllerMap.containsKey(id)) {
            Log.i(TAG, "getWebViewController: can not found value of the id: " + id);
            return null;
        }
        return sWebViewControllerMap.get(id);
    }

    static void removeWebViewController(@NonNull String id) {
        sWebViewControllerMap.remove(id);
    }


    public interface WebViewJSListener {
        void onCloseSelected();

        void onInstallSelected();

        void onVideoEndLoading();

    }

    public class ZPLAYAdsJavascriptInterface {

        @JavascriptInterface
        public void onCloseSelected() {
            // 可玩广告点击关闭按钮时，触发该方法
            Log.d(TAG, "onCloseSelected: ");
            if (mWebViewJSListener != null) {
                mWebViewJSListener.onCloseSelected();
            }
        }

        @JavascriptInterface
        public void onInstallSelected() {
            // 当点击"安装"按钮时，触发该方法
            Log.d(TAG, "onInstallSelected: ");
            if (mWebViewJSListener != null) {
                mWebViewJSListener.onInstallSelected();
            }
        }

        @JavascriptInterface
        public void onVideoEndLoading() {
            Log.d(TAG, "onVideoEndLoading: ");
            if (mWebViewJSListener != null) {
                mWebViewJSListener.onVideoEndLoading();
            }
        }

    }

}
