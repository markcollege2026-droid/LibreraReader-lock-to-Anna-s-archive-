package com.foobnix.network;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.util.Map;

/** Native Chromium networking stays disabled, including redirect and service-worker fallbacks. */
public class RestrictedWebView extends WebView {
    public RestrictedWebView(Context context) { super(context); configure(); }
    public RestrictedWebView(Context context, AttributeSet attrs) { super(context, attrs); configure(); }

    private void configure() {
        getSettings().setBlockNetworkLoads(true);
        getSettings().setAllowFileAccessFromFileURLs(false);
        getSettings().setAllowUniversalAccessFromFileURLs(false);
        getSettings().setAllowContentAccess(false);
        getSettings().setGeolocationEnabled(false);
        getSettings().setSupportMultipleWindows(false);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        super.setWebViewClient(new RestrictedWebViewClient());
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        if (!(client instanceof RestrictedWebViewClient)) {
            throw new IllegalArgumentException("A policy-aware WebViewClient is required");
        }
        super.setWebViewClient(client);
    }

    protected boolean mayLoad(String url) {
        getSettings().setJavaScriptEnabled(false);
        if (NetworkPolicy.isLocalDocument(url)) return true;
        try {
            NetworkPolicy.requireAllowed(url);
            // Remote JS could create channels not exposed by shouldInterceptRequest (WebSocket,
            // WebRTC). Remote pages are read-only; the offline EPUB renderer enables JS itself.
            getSettings().setJavaScriptEnabled(false);
            return true;
        } catch (IOException e) {
            android.util.Log.w("NetworkPolicy", "WebView navigation blocked", e);
            return false;
        }
    }

    @Override public void loadUrl(String url) {
        if (mayLoad(url)) super.loadUrl(url);
    }

    @Override public void loadUrl(String url, Map<String, String> headers) {
        if (mayLoad(url)) super.loadUrl(url, headers);
    }

    @Override public void postUrl(String url, byte[] data) {
        // Request bodies cannot be safely forwarded by WebViewClient.
        android.util.Log.w("NetworkPolicy", "WebView POST blocked");
    }
}
