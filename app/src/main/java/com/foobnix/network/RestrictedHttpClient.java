package com.foobnix.network;

import java.io.IOException;
import java.net.Proxy;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** All HTTP (including Glide and WebView resources) must use this builder. */
public final class RestrictedHttpClient {
    private RestrictedHttpClient() { }

    public static OkHttpClient.Builder newBuilder() {
        return new OkHttpClient.Builder()
                // OkHttp's automatic redirect loop is below application interceptors. Own the
                // loop here instead, so EACH target is checked before DNS, TCP or TLS happens.
                .followRedirects(false)
                .followSslRedirects(false)
                .proxy(Proxy.NO_PROXY)
                .addInterceptor(new HostInterceptor());
    }

    public static final class HostInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            for (int redirects = 0; ; redirects++) {
                NetworkPolicy.requireAllowed(request.url().toString());
                Response response = chain.proceed(request);
                String location = response.header("Location");
                if (!response.isRedirect() || location == null) return response;
                HttpUrl next = request.url().resolve(location);
                // Close even when validation throws: do not leak pooled connections.
                response.close();
                if (next == null) throw new IOException("Malformed redirect URL");
                NetworkPolicy.requireAllowed(next.toString());
                if (redirects >= 19) throw new IOException("Too many redirects");
                Request.Builder follow = request.newBuilder().url(next);
                if (!request.url().host().equals(next.host())
                        || request.url().port() != next.port()
                        || !request.url().scheme().equals(next.scheme())) {
                    follow.removeHeader("Authorization").removeHeader("Cookie");
                }
                int code = response.code();
                if (code == 303 || ((code == 301 || code == 302)
                        && !"GET".equals(request.method()) && !"HEAD".equals(request.method()))) {
                    follow.method("HEAD".equals(request.method()) ? "HEAD" : "GET", null);
                    follow.removeHeader("Content-Length").removeHeader("Content-Type")
                            .removeHeader("Transfer-Encoding");
                }
                request = follow.build();
            }
        }
    }
}
