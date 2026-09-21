package net.permissionbrick.ha;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.*;

/** Isolated Android tests: no real Home Assistant or YouTube server is contacted. */
public final class ButtonTestActivity extends Activity {
    private int pauses;
    private final Handler main = new Handler();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<Throwable> networkError = new AtomicReference<>();
    private LinearLayout root;
    private HomeAssistantEntry entry;
    private Button play;
    private Dialog sheet;
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        play = new Button(this);
        play.setText("Player pause control"); play.setContentDescription("Pause video");
        play.setOnClickListener(v -> { pauses++; play.setContentDescription("Play video"); });
        root.addView(play);
        setContentView(root);
        main.postDelayed(() -> {
            try { runTests(); } catch (Exception e) { throw new RuntimeException(e); }
        }, 500);
    }
    private void runTests() throws Exception {
        check(LocalPlayback.tryPause(root) && pauses == 1, "Pause control invoked");
        check(LocalPlayback.tryPause(root) && pauses == 1, "Already paused stays paused");
        root.removeView(play);
        LinearLayout wrapper = new LinearLayout(this);
        TextView label = new TextView(this); label.setContentDescription("Pause video");
        wrapper.addView(label); wrapper.setOnClickListener(v -> pauses++); root.addView(wrapper);
        check(LocalPlayback.tryPause(root) && pauses == 2, "Label on child invokes clickable parent");
        root.removeView(wrapper);
        View delegated = new View(this);
        delegated.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription("Pause video"); info.setClickable(true);
            }
            @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) { pauses++; return true; }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        root.addView(delegated);
        check(LocalPlayback.tryPause(root) && pauses == 3, "Accessibility delegate pause control");
        root.removeView(delegated);
        check(!LocalPlayback.tryPause(root), "Missing control is harmless");
        root.addView(play); play.setContentDescription("Pause video");
        play.setOnClickListener(v -> { throw new IllegalStateException("Broken pause control"); });
        check(!LocalPlayback.tryPause(root), "Throwing control is harmless");
        root.removeView(play);
        String endpoint = "https://example.net/api/webhook/secret-test";
        Config.save(this, endpoint);
        check(Config.read(this).equals(endpoint), "Encrypted config round trip");
        String stored = new String(Files.readAllBytes(new File(getNoBackupFilesDir(), "ha_send_to_tv.bin").toPath()), StandardCharsets.ISO_8859_1);
        check(!stored.contains("secret-test"), "URL not stored as plaintext");
        Config.clear(this);
        check(Config.read(this).isEmpty(), "Forget settings");

        ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(12000);
        Config.save(this, "http://127.0.0.1:" + server.getLocalPort() + "/api/webhook/test");
        Playback.setVideoId("dQw4w9WgXcQ"); Playback.setVideoTime(95000);
        new Thread(() -> {
            try (server) {
                for (int n = 0; n < 3; n++) try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    check(in.readLine().equals("POST /api/webhook/test HTTP/1.1"), "Actual webhook POST");
                    int length = 0; String line;
                    while (!(line = in.readLine()).isEmpty()) if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                    char[] body = new char[length]; int offset = 0;
                    while (offset < length) { int read = in.read(body, offset, length - offset); check(read > 0, "Complete request body"); offset += read; }
                    check(new String(body).contains("watch?v=dQw4w9WgXcQ&t=95s"), "Video and timestamp retained");
                    requests.incrementAndGet();
                    Thread.sleep(150); // Keep the request pending while a duplicate tap is attempted.
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                }
            } catch (Throwable error) { networkError.set(error); }
        }, "test-webhook").start();
        sheet = new Dialog(this);
        entry = new HomeAssistantEntry(new android.view.ContextThemeWrapper(this, android.R.style.Theme_Material_Light_NoActionBar));
        TextView text = new TextView(this); text.setText("Home Assistant"); entry.addView(text);
        sheet.setContentView(entry); sheet.show();
        entry.performClick(); entry.performClick(); // Missing pause still sends, duplicate tap suppressed.
        main.postDelayed(() -> next(1), 700);
    }
    private void next(int count) {
        check(networkError.get() == null, "Mock server: " + networkError.get());
        check(requests.get() == count, "Exactly one POST per completed tap, stage " + count + ", got " + requests.get());
        if (count == 1) {
            root.addView(play); // Throws, but must still send.
            entry.performClick(); main.postDelayed(() -> next(2), 700);
        } else if (count == 2) {
            play.setOnClickListener(v -> { pauses++; play.setContentDescription("Play video"); });
            entry.performClick();
            check(pauses == 4, "Pause finds underlying activity from device sheet context");
            main.postDelayed(() -> next(3), 700);
        } else {
            sheet.dismiss(); Config.clear(this);
            entry.performClick(); // First tap opens the actual settings dialog.
            Log.i("HA-TEST", "PASS: missing/throwing pause still POSTs; duplicate suppression; sheet-to-player pause; wrapped and delegated controls; encrypted settings");
        }
    }
}
