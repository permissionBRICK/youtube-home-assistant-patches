// SPDX-License-Identifier: GPL-3.0-only
package net.permissionbrick.ha;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.LinkedHashSet;
import java.util.Set;

/** A failed or unavailable pause control must never prevent the TV handoff. */
final class LocalPlayback {
    static boolean tryPause(View root) {
        try {
            Set<View> pause = new LinkedHashSet<>();
            collect(root, label(root, "accessibility_pause"), pause);
            if (pause.size() == 1) {
                View control = pause.iterator().next();
                return control.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null);
            }
            if (!pause.isEmpty()) return false;
            Set<View> play = new LinkedHashSet<>();
            collect(root, label(root, "accessibility_play"), play);
            return play.size() == 1; // Already paused; never blindly toggle playback.
        } catch (RuntimeException ignored) {
            return false;
        }
    }
    private static String label(View root, String name) {
        int id = root.getResources().getIdentifier(name, "string", root.getContext().getPackageName());
        if (id == 0) id = root.getResources().getIdentifier(name, "string", "com.google.android.youtube");
        return id == 0 ? "" : root.getResources().getString(id).trim();
    }
    private static boolean matches(String label, CharSequence description) {
        return description != null && label.equals(description.toString().trim());
    }
    private static void collect(View view, String label, Set<View> matches) {
        if (label.isEmpty() || !view.isShown() || !view.isEnabled()) return;
        AccessibilityNodeInfo node = view.createAccessibilityNodeInfo();
        boolean match = matches(label, view.getContentDescription())
                || (node != null && matches(label, node.getContentDescription()));
        boolean clickable = view.isClickable() || (node != null && node.isClickable());
        if (node != null) node.recycle();
        if (match) {
            View control = view;
            // Some player controls expose the label on a child of the clickable button.
            while (!clickable) {
                ViewParent parent = control.getParent();
                if (!(parent instanceof View)) break;
                control = (View) parent;
                if (!control.isEnabled()) break;
                clickable = control.isClickable();
            }
            if (clickable) matches.add(control);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), label, matches);
        }
    }
}
