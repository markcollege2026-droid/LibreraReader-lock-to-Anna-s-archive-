package com.foobnix.network;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

/** Runs unchanged on the JVM and the emulator. No live website or DNS is required. */
public class NetworkPolicyTest {
    @Test public void acceptsKeywordInActualHost() throws Exception {
        for (String url : new String[] {
                "https://annas-archive.org/", "http://annas-archive.se:8080/search?q=books",
                "https://sub.annas-archive.li/book", "https://ANNAS-ARCHIVE.ORG/",
                "https://annas-archive.example.com/", "https://mirror-annas-archive.test/"
        }) NetworkPolicy.requireAllowed(url);
    }

    @Test public void rejectsBypassesAndNonNetworkSchemes() {
        for (String url : new String[] {
                null, "", "not a url", "//annas-archive.org/book", "/annas-archive",
                "https://attacker.com/?next=https://annas-archive.org/",
                "https://attacker.com/annas-archive", "https://attacker.com/#annas-archive",
                "https://annas-archive.org@attacker.com/", "https://user@annas-archive.org/",
                "https://annas-archive.org\\@attacker.com/", "https://annas-archive.org:bad/",
                "https://annas-archive.org:65536/", "https://annas%2darchive.org/",
                "https://annas-archive.org%2eattacker.com/", "https://attacker.com/?url=://annas-archive.org",
                "https://127.0.0.1/annas-archive", "https://[::1]/?annas-archive",
                "file:///annas-archive.org", "ftp://annas-archive.org/", "javascript:annas-archive",
                "data:text/html,annas-archive", "https://annas-archive.org/has a space"
        }) {
            try {
                NetworkPolicy.requireAllowed(url);
                fail("Accepted forbidden URL: " + url);
            } catch (IOException expected) { /* must be a checked I/O failure */ }
        }
    }

    @Test public void localDocumentsAreSeparateFromNetworkPermission() {
        assertTrue(NetworkPolicy.isLocalDocument("file:///android_asset/licenses.html"));
        assertTrue(NetworkPolicy.isLocalDocument("file:///storage/emulated/0/book.html"));
        assertTrue(NetworkPolicy.isLocalDocument("about:blank"));
        for (String url : new String[] { null, "file://attacker.com/book", "content://provider/book",
                "https://annas-archive.org/", "javascript:alert(1)", "data:text/html,hi" }) {
            assertFalse(NetworkPolicy.isLocalDocument(url));
        }
    }
}
