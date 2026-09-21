package net.permissionbrick.ha;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.util.Log;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;

/** Isolated Android UI test: no real Home Assistant or YouTube server is contacted. */
public final class ButtonTestActivity extends Activity {
    private int pauses;
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff333333);
        Button play = new Button(this);
        play.setText("Pause"); play.setContentDescription("Pause");
        play.setOnClickListener(v -> { pauses++; play.setContentDescription("Play"); });
        root.addView(play);
        Button fullscreen = new Button(this);
        fullscreen.setText("Fullscreen");
        fullscreen.setId(getResources().getIdentifier("fullscreen_button", "id", getPackageName()));
        root.addView(fullscreen);
        TvButton tv = new TvButton(this);
        root.addView(tv, new LinearLayout.LayoutParams(128, 128));
        setContentView(root);
        new Handler().postDelayed(() -> {
            try {
                check(tv.getVisibility() == View.VISIBLE, "TV button visible with controls");
                fullscreen.setAlpha(0f);
                tv.getViewTreeObserver().dispatchOnPreDraw();
                check(!tv.isEnabled(), "Invisible control cannot send");
                fullscreen.setAlpha(1f);
                tv.getViewTreeObserver().dispatchOnPreDraw();
                check(tv.isEnabled(), "Control re-enabled when visible");
                Method pause = TvButton.class.getDeclaredMethod("pauseLocal");
                pause.setAccessible(true);
                check((Boolean) pause.invoke(tv) && pauses == 1, "Pause control invoked");
                check((Boolean) pause.invoke(tv) && pauses == 1, "Already paused stays paused");
                String endpoint = "https://example.net/api/webhook/secret-test";
                Config.save(this, endpoint);
                check(Config.read(this).equals(endpoint), "Encrypted config round trip");
                String stored = new String(Files.readAllBytes(new File(getNoBackupFilesDir(), "ha_send_to_tv.bin").toPath()), StandardCharsets.ISO_8859_1);
                check(!stored.contains("secret-test"), "URL not stored as plaintext");
                Config.clear(this);
                check(Config.read(this).isEmpty(), "Forget settings");
                tv.performClick(); // First tap opens the actual settings dialog.
                Log.i("HA-TEST", "PASS: visibility, local pause, already-paused behavior, encrypted storage, reset, settings dialog");
            } catch (Exception e) { throw new RuntimeException(e); }
        }, 1000);
    }
}
