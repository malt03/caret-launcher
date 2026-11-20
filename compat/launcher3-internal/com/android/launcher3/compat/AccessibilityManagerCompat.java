package com.android.launcher3.compat;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * Stand-in for Launcher3's AccessibilityManagerCompat (android-17.0.0_r1). The methods
 * AbstractFloatingView calls are copied; the test protocol parts are left out.
 */
public class AccessibilityManagerCompat {

    public static boolean isAccessibilityEnabled(Context context) {
        return getManager(context).isEnabled();
    }

    public static boolean isObservedEventType(Context context, int eventType) {
        // TODO: Use new API once available
        return isAccessibilityEnabled(context);
    }

    public static void sendCustomAccessibilityEvent(View target, int type, String text) {
        if (target != null && isObservedEventType(target.getContext(), type)) {
            AccessibilityEvent event = AccessibilityEvent.obtain(type);
            target.onInitializeAccessibilityEvent(event);
            if (!android.text.TextUtils.isEmpty(text)) {
                event.getText().add(text);
            }
            getManager(target.getContext()).sendAccessibilityEvent(event);
        }
    }

    private static AccessibilityManager getManager(Context context) {
        return (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }
}
