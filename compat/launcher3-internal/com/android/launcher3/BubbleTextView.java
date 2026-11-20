package com.android.launcher3;

import android.content.Context;
import android.view.View;

import com.android.launcher3.util.MultiPropertyFactory;

/**
 * Stand-in for Launcher3's BubbleTextView: an app icon view. TextLauncher's icons have no label,
 * so the text alpha property is a no-op.
 */
public abstract class BubbleTextView extends View {
    private final MultiPropertyFactory<View> mTextAlpha =
            new MultiPropertyFactory<>(this, new android.util.FloatProperty<View>("textAlpha") {
                @Override
                public void setValue(View view, float value) { }

                @Override
                public Float get(View view) {
                    return 1f;
                }
            }, 1, (a, b) -> a * b);

    public BubbleTextView(Context context) {
        super(context);
    }

    public abstract int getIconSize();

    public MultiPropertyFactory<View>.MultiProperty getFloatingViewTextAlpha() {
        return mTextAlpha.get(0);
    }
}
