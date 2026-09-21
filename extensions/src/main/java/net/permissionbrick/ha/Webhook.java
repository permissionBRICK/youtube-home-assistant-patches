// SPDX-License-Identifier: GPL-3.0-only
package net.permissionbrick.ha;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** No redirects or automatic retries: a webhook can already have acted before a timeout. */
public final class Webhook {
    private Webhook() {}
    public static String validate(String input) {
        try {
            URI uri = new URI(input.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getRawQuery() != null || uri.getPort() > 65535
                    || !uri.getPath().matches(".*/api/webhook/[^/]+")) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter the complete http(s)://server/api/webhook/id URL.");
        }
    }
    public static String payload(Playback.Video video) {
        if (!video.id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Open a video first.");
        // All dynamic strings below are constructed from the validated video ID and integer time.
        return "{\"url\":\"" + video.url() + "\",\"video_id\":\"" + video.id
            + "\",\"position\":" + video.seconds + ",\"title\":\"\",\"source_url\":\"https://www.youtube.com/watch?v="
            + video.id + "\"}";
    }
    public static void send(String endpoint, Playback.Video video) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(validate(endpoint)).toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            byte[] body = payload(video).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpFailure(status);
        } finally { connection.disconnect(); }
    }
    public static final class HttpFailure extends IOException {
        public final int status;
        HttpFailure(int status) { super("Home Assistant returned HTTP " + status + "."); this.status = status; }
    }
}
