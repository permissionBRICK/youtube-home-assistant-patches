package net.permissionbrick.ha;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class WebhookTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Playback.setVideoId("dQw4w9WgXcQ");
        Playback.setVideoTime(95500);
        Playback.Video video = Playback.snapshot();
        check(video.url().equals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=95s"), "Timestamp handoff");
        Playback.setVideoId("dQw4w9WgXcQ");
        check(Playback.snapshot().seconds == 95, "Repeated ID must preserve position");
        Playback.setVideoId("abcdefghijk");
        check(Playback.snapshot().seconds == 0, "New video resets position");
        Playback.setVideoTime(4999);
        check(!Playback.snapshot().url().contains("&t="), "Under 5 seconds starts at beginning");
        Playback.setVideoId("bad\"id");
        try { Webhook.payload(Playback.snapshot()); throw new AssertionError("Invalid ID accepted"); }
        catch (IllegalArgumentException expected) {}
        for (String bad : new String[]{"ftp://host/api/webhook/id", "https://host/", "https://user:pass@host/api/webhook/id", "https://host/api/webhook/id#fragment", "https://host/api/webhook/id?token=secret", "https://host:99999/api/webhook/id"}) {
            try { Webhook.validate(bad); throw new AssertionError("Invalid endpoint accepted: " + bad); }
            catch (IllegalArgumentException expected) {}
        }
        check(Webhook.validate(" https://host/prefix/api/webhook/id ").equals("https://host/prefix/api/webhook/id"), "Reverse proxy prefix");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger posts = new AtomicInteger(), redirects = new AtomicInteger();
        server.createContext("/api/webhook/ok", exchange -> {
            check(exchange.getRequestMethod().equals("POST"), "POST required");
            check(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/json"), "JSON required");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            check(body.equals(Webhook.payload(video)), "Exact browser-compatible payload");
            posts.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.createContext("/api/webhook/redirect", exchange -> {
            redirects.incrementAndGet(); exchange.getResponseHeaders().set("Location", "/api/webhook/ok");
            exchange.sendResponseHeaders(307, -1); exchange.close();
        });
        server.createContext("/api/webhook/error", exchange -> { exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhook/";
        try {
            Webhook.send(base + "ok", video);
            for (String route : new String[]{"redirect", "error"}) {
                try { Webhook.send(base + route, video); throw new AssertionError("HTTP failure accepted"); }
                catch (Webhook.HttpFailure expected) { check(expected.status == (route.equals("redirect") ? 307 : 500), "HTTP status preserved"); }
            }
            check(posts.get() == 1 && redirects.get() == 1, "No redirects or retries");
        } finally { server.stop(0); }
        System.out.println("PASS: timestamp, video changes, payload, endpoint validation, HTTP success/errors, redirect refusal");
    }
}
