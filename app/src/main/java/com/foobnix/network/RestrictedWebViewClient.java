package com.foobnix.network;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.foobnix.opds.OPDS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Never let Chromium perform network I/O: even permitted resources use the guarded HTTP client. */
public class RestrictedWebViewClient extends WebViewClient {
    public static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by network policy",
                new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (NetworkPolicy.isLocalDocument(url)) return false;
        try {
            NetworkPolicy.requireAllowed(url);
            view.getSettings().setJavaScriptEnabled(false);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return shouldOverrideUrlLoading(view, request.getUrl().toString());
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return resource(request.getUrl().toString(), request.getMethod(), request.getRequestHeaders());
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return resource(url, "GET", new HashMap<>());
    }

    private WebResourceResponse resource(String url, String method, Map<String, String> headers) {
        if (NetworkPolicy.isLocalDocument(url)) return null;
        try {
            NetworkPolicy.requireAllowed(url);
            // WebView does not expose request bodies. Never replay a POST as a GET.
            if (!"GET".equals(method) && !"HEAD".equals(method)) return blocked();
            Request.Builder builder = new Request.Builder().url(url).method(method, null);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            final Response response = OPDS.client.newCall(builder.build()).execute();
            ResponseBody body = response.body();
            // Android forbids 3xx WebResourceResponses. Unfollowable redirects fail closed.
            if (body == null || response.isRedirect()) {
                response.close();
                return blocked();
            }
            String mime = body.contentType() == null ? "application/octet-stream"
                    : body.contentType().type() + "/" + body.contentType().subtype();
            String encoding = body.contentType() == null ? "UTF-8"
                    : body.contentType().charset(java.nio.charset.StandardCharsets.UTF_8).name();
            Map<String, String> resultHeaders = new HashMap<>();
            for (String name : response.headers().names()) {
                resultHeaders.put(name, response.header(name));
            }
            String reason = response.message().isEmpty() ? "HTTP response" : response.message();
            // Chromium closes this stream when done, releasing the OkHttp body/connection.
            return new WebResourceResponse(mime, encoding, response.code(), reason,
                    resultHeaders, body.byteStream());
        } catch (IOException | IllegalArgumentException e) {
            return blocked();
        }
    }
}
