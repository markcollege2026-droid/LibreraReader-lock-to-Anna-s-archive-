package com.foobnix.network;

import android.content.Context;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class RestrictedWebViewTest {
    @Test public void chromiumNetworkIsDisabledAndBlockedNavigationDoesNotLoad() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            RestrictedWebView web = new RestrictedWebView(context);
            try {
                assertTrue(web.getSettings().getBlockNetworkLoads());
                assertFalse(web.getSettings().getAllowUniversalAccessFromFileURLs());
                RestrictedWebViewClient client = new RestrictedWebViewClient();
                assertTrue(client.shouldOverrideUrlLoading(web, "https://evil.test/?annas-archive"));
                assertFalse(client.shouldOverrideUrlLoading(web, "https://annas-archive.org/"));
                WebResourceResponse denied = client.shouldInterceptRequest(web,
                        "https://evil.test/tracker.png?annas-archive");
                assertEquals(403, denied.getStatusCode());
                web.loadUrl("https://evil.test/?annas-archive");
                assertNull(web.getUrl());
                try {
                    web.setWebViewClient(new WebViewClient());
                    fail("Unguarded WebViewClient accepted");
                } catch (IllegalArgumentException expected) { }
            } finally {
                web.destroy();
            }
        });
    }
}
