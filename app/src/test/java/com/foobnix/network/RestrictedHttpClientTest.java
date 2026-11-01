package com.foobnix.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.*;

public class RestrictedHttpClientTest {
    private MockWebServer server;
    private OkHttpClient client;
    private AtomicInteger dnsCalls;

    @Before public void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        dnsCalls = new AtomicInteger();
        client = RestrictedHttpClient.newBuilder().dns(host -> {
            dnsCalls.incrementAndGet();
            // All allowed-host tests terminate at our in-process fixture, not the Internet.
            return Collections.singletonList(InetAddress.getByName("127.0.0.1"));
        }).build();
    }

    @After public void teardown() throws Exception {
        server.shutdown();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
    }

    private String url(String host, String path) {
        return server.url(path).newBuilder().host(host).build().toString();
    }

    @Test public void blockedBeforeDnsOrConnection() throws Exception {
        try {
            client.newCall(new Request.Builder().url(url("attacker.test", "/?annas-archive")).build()).execute();
            fail("Request was not blocked");
        } catch (IOException expected) {
            assertEquals(0, dnsCalls.get());
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test public void allowedRequestWorks() throws Exception {
        server.enqueue(new MockResponse().setBody("ok"));
        try (Response response = client.newCall(new Request.Builder()
                .url(url("annas-archive.test", "/book")).build()).execute()) {
            assertEquals("ok", response.body().string());
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test public void redirectsCannotEscapeHostPolicy() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302)
                .addHeader("Location", url("attacker.test", "/?annas-archive")));
        try {
            client.newCall(new Request.Builder().url(url("annas-archive.test", "/")).build()).execute();
            fail("Redirect escaped policy");
        } catch (IOException expected) {
            assertEquals(1, server.getRequestCount());
            assertEquals(1, dnsCalls.get());
        }
    }

    @Test public void relativeRedirectToAllowedHostWorks() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/book"));
        server.enqueue(new MockResponse().setBody("book"));
        try (Response response = client.newCall(new Request.Builder()
                .url(url("annas-archive.test", "/")).build()).execute()) {
            assertEquals("book", response.body().string());
            assertEquals("/book", response.request().url().encodedPath());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test public void clonedClientsKeepPolicy() throws Exception {
        try {
            client.newBuilder().build().newCall(new Request.Builder()
                    .url(url("attacker.test", "/")).build()).execute();
            fail("Cloned client lost the policy");
        } catch (IOException expected) {
            assertEquals(0, dnsCalls.get());
        }
    }
}
