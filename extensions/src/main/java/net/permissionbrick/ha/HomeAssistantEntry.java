// SPDX-License-Identifier: GPL-3.0-only
package net.permissionbrick.ha;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HomeAssistantEntry extends LinearLayout {
    private static final AtomicBoolean SENDING = new AtomicBoolean();
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public HomeAssistantEntry(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public HomeAssistantEntry(Context context) { this(context, null); }
    public HomeAssistantEntry(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setOnClickListener(view -> send());
        setOnLongClickListener(view -> { settings(); return true; });
        setTooltipText("Send to Home Assistant. Hold for settings");
        setFocusable(true);
        setSaveEnabled(false);
    }
    private void toast(String message) { Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show(); }
    private void settings() {
        if (SENDING.get()) { toast("Wait for the current request to finish."); return; }
        Context context = getContext();
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, 0);
        TextView help = new TextView(context);
        help.setText("Paste the same Home Assistant webhook URL used by your browser script. Home Assistant controls the TV. No separate API token is needed.\n\nTap Home Assistant in the device list to send the current video and position. Local pausing is best effort. Hold the entry to change settings.");
        panel.addView(help);
        EditText url = new EditText(context);
        url.setHint("https://ha.example.net/api/webhook/...");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        url.setSingleLine(true);
        url.setSaveEnabled(false);
        url.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        try { url.setText(Config.read(context)); }
        catch (Exception e) { toast("Saved settings could not be read. Enter the webhook URL again."); }
        panel.addView(url);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("Home Assistant")
            .setView(panel).setPositiveButton("Save", null).setNegativeButton("Cancel", null)
            .setNeutralButton("Forget URL", (d, w) -> { Config.clear(context); toast("Webhook URL removed."); }).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String endpoint = Webhook.validate(url.getText().toString());
                Config.save(context, endpoint);
                dialog.dismiss();
                toast("Saved. Tap Home Assistant in the device list to send a video.");
            } catch (IllegalArgumentException e) { url.setError(e.getMessage()); }
            catch (Exception e) { url.setError("Could not save securely. Try again."); }
        }));
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }
    private void send() {
        if (SENDING.get()) { toast("Already sending to Home Assistant."); return; }
        final String endpoint;
        try { endpoint = Config.read(getContext()); }
        catch (Exception e) { settings(); return; }
        if (endpoint.isEmpty()) { settings(); return; }
        final Playback.Video video = Playback.snapshot();
        if (video.id.isEmpty()) { toast("Open a video and wait for playback to begin."); return; }
        if (!SENDING.compareAndSet(false, true)) return;
        try { LocalPlayback.tryPause(playerRoot()); }
        catch (RuntimeException ignored) { /* Sending is independent of YouTube's controls. */ }
        NETWORK.execute(() -> {
            String result;
            boolean accepted = false;
            try { Webhook.send(endpoint, video); result = "Sent to Home Assistant"; accepted = true; }
            catch (Webhook.HttpFailure e) { result = e.getMessage() + " Check the webhook URL and automation."; }
            catch (java.net.SocketTimeoutException e) { result = "Home Assistant timed out. The command may have arrived. Check the TV before retrying."; }
            catch (Exception e) { result = "Could not reach Home Assistant. Check your connection and webhook URL."; }
            final String message = result;
            final int duration = accepted ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
            MAIN.post(() -> {
                SENDING.set(false);
                Toast.makeText(getContext(), message, duration).show();
            });
        });
    }
    private View playerRoot() {
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof android.app.Activity) {
                return ((android.app.Activity) context).getWindow().getDecorView();
            }
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return getRootView();
    }
}
