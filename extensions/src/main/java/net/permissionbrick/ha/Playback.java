// SPDX-License-Identifier: GPL-3.0-only
package net.permissionbrick.ha;

/** Called by the patched foreground player, never by preloaded player responses. */
public final class Playback {
    private static String videoId = "";
    private static long positionMs;
    private Playback() {}
    public static synchronized void setVideoId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) {
            videoId = "";
            positionMs = 0;
        } else if (!id.equals(videoId)) {
            videoId = id;
            positionMs = 0;
        }
    }
    public static synchronized void setVideoTime(long ms) { positionMs = Math.max(0, ms); }
    public static synchronized Video snapshot() { return new Video(videoId, positionMs); }
    public static final class Video {
        public final String id;
        public final long seconds;
        public Video(String id, long ms) {
            this.id = id;
            long s = Math.max(0, ms / 1000);
            seconds = s < 5 ? 0 : s;
        }
        public String url() {
            return "https://www.youtube.com/watch?v=" + id + (seconds > 0 ? "&t=" + seconds + "s" : "");
        }
    }
}
