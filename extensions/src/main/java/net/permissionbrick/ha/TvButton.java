// SPDX-License-Identifier: GPL-3.0-only
package net.permissionbrick.ha;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TvButton extends ImageButton {
    private static final AtomicBoolean SENDING = new AtomicBoolean();
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public TvButton(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public TvButton(Context context) { this(context, null); }
    public TvButton(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setImageDrawable(new TvIcon());
        setOnClickListener(view -> send());
        setOnLongClickListener(view -> { settings(); return true; });
        setTooltipText("Send to TV. Hold for settings");
        setSaveEnabled(false);
    }
    private View fullscreen;
    private final ViewTreeObserver.OnPreDrawListener visibility = () -> {
        if (fullscreen == null || !fullscreen.isAttachedToWindow()) {
            int id = getResources().getIdentifier("fullscreen_button", "id", getContext().getPackageName());
            if (id == 0) id = getResources().getIdentifier("fullscreen_button", "id", "com.google.android.youtube");
            fullscreen = id == 0 ? null : getRootView().findViewById(id);
        }
        // YouTube animates individual controls. Match fullscreen instead of staying over the video.
        boolean show = fullscreen != null && fullscreen.isShown();
        setVisibility(show ? VISIBLE : INVISIBLE);
        if (show) setAlpha(fullscreen.getAlpha());
        return true;
    };
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(visibility);
    }
    @Override protected void onDetachedFromWindow() {
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnPreDrawListener(visibility);
        fullscreen = null;
        super.onDetachedFromWindow();
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
        help.setText("Paste the same Home Assistant webhook URL used by your browser script. Home Assistant controls the TV. No separate API token is needed.\n\nTap the TV button to pause here and send the current video and position. Hold it to change settings.");
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
                toast("Saved. Tap the TV button to send a video.");
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
        if (!pauseLocal()) {
            toast("Could not find YouTube's pause control. Show the player controls and try again.");
            return;
        }
        if (!SENDING.compareAndSet(false, true)) return;
        toast("Sending to Home Assistant...");
        NETWORK.execute(() -> {
            String result;
            try { Webhook.send(endpoint, video); result = "Sent to Home Assistant."; }
            catch (Webhook.HttpFailure e) { result = e.getMessage() + " Check the webhook URL and automation."; }
            catch (java.net.SocketTimeoutException e) { result = "Home Assistant timed out. The command may have arrived. Check the TV before retrying."; }
            catch (Exception e) { result = "Could not reach Home Assistant. Check your connection and webhook URL. Playback remains paused."; }
            final String message = result;
            MAIN.post(() -> { SENDING.set(false); toast(message); });
        });
    }
    private String resourceString(String name) {
        int id = getResources().getIdentifier(name, "string", getContext().getPackageName());
        if (id == 0) id = getResources().getIdentifier(name, "string", "com.google.android.youtube");
        return id == 0 ? "" : getResources().getString(id);
    }
    private boolean pauseLocal() {
        // Use YouTube's localized accessibility labels, never a play/pause toggle or global media key.
        List<View> pause = new ArrayList<>();
        collect(getRootView(), resourceString("accessibility_pause"), pause);
        if (pause.size() == 1) return pause.get(0).performClick();
        if (!pause.isEmpty()) return false;
        List<View> play = new ArrayList<>();
        collect(getRootView(), resourceString("accessibility_play"), play);
        return play.size() == 1; // Already paused. Do not start it again.
    }
    private static void collect(View view, String label, List<View> matches) {
        if (label.isEmpty() || !view.isShown()) return;
        if (view.isEnabled() && view.isClickable() && label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) matches.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), label, matches);
        }
    }
    private static final class TvIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
            canvas.drawRoundRect(2, 3, 22, 18, 2, 2, paint);
            canvas.drawLine(8, 22, 16, 22, paint); canvas.drawLine(12, 18, 12, 22, paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 24; }
        @Override public int getIntrinsicHeight() { return 24; }
    }
}
