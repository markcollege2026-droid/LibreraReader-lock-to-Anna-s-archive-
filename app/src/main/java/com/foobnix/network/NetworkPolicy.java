package com.foobnix.network;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** The single policy for outgoing requests. This is a keyword rule, not domain ownership proof. */
public final class NetworkPolicy {
    private NetworkPolicy() { }

    public static void requireAllowed(String url) throws IOException {
        if (url == null) {
            throw new IOException("Network URL is missing");
        }
        try {
            URI uri = new URI(url).parseServerAuthority();
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if ((!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)))
                    || host == null || uri.getRawUserInfo() != null
                    || !host.toLowerCase(Locale.ROOT).contains("annas-archive")
                    || uri.getPort() > 65535) {
                throw new IOException("Network URL blocked by host policy");
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Malformed network URL", e);
        }
    }

    /** Only local, non-network documents. file://remote-host and content providers are refused. */
    public static boolean isLocalDocument(String url) {
        if (url == null) return false;
        if ("about:blank".equals(url)) return true;
        try {
            URI uri = new URI(url);
            return "file".equalsIgnoreCase(uri.getScheme())
                    && uri.getRawAuthority() == null && uri.getPath() != null
                    && uri.getPath().startsWith("/");
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
