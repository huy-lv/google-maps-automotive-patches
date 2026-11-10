package android.view;

import android.graphics.Insets;
import android.graphics.Rect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Android 9 backport of WindowInsets.Builder (API 29, with the API 30 per-type setters), which
 * androidx WindowInsetsCompat of Maps (built for minSdk 30) creates unconditionally. On API 29+
 * the framework class is found first and this one is never loaded. Android 9 insets only know
 * system window and stable insets: per-type insets are merged into those.
 */
@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public final class WindowInsets$Builder {
    // WindowInsets.Type bits: status bars, navigation bars, caption bar, IME.
    private static final int SYSTEM_TYPES = 1 | 1 << 1 | 1 << 2 | 1 << 3;
    private static final int TYPE_COUNT = 9;

    private final Insets[] typed = new Insets[TYPE_COUNT];
    private final Insets[] typedStable = new Insets[TYPE_COUNT];
    private Rect system;
    private Rect stable;
    private DisplayCutout cutout;
    private boolean round;
    private boolean alwaysConsumeNavBar;

    public WindowInsets$Builder() {
    }

    public WindowInsets$Builder(WindowInsets insets) {
        if (insets == null) return;
        system = new Rect(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
        stable = new Rect(insets.getStableInsetLeft(), insets.getStableInsetTop(),
                insets.getStableInsetRight(), insets.getStableInsetBottom());
        cutout = insets.getDisplayCutout();
        round = insets.isRound();
        try {
            Method method = WindowInsets.class.getMethod("shouldAlwaysConsumeNavBar");
            alwaysConsumeNavBar = (Boolean) method.invoke(insets);
        } catch (Throwable ignored) {
        }
    }

    private static Rect rect(Insets insets) {
        return insets == null ? new Rect() : new Rect(insets.left, insets.top, insets.right, insets.bottom);
    }

    private static Rect merge(Insets[] insets, Rect fallback) {
        Rect result = null;
        for (int i = 0; i < TYPE_COUNT; i++) {
            if ((SYSTEM_TYPES & (1 << i)) == 0 || insets[i] == null) continue;
            if (result == null) result = new Rect();
            result.left = Math.max(result.left, insets[i].left);
            result.top = Math.max(result.top, insets[i].top);
            result.right = Math.max(result.right, insets[i].right);
            result.bottom = Math.max(result.bottom, insets[i].bottom);
        }
        return result == null ? fallback : result;
    }

    public WindowInsets$Builder setSystemWindowInsets(Insets insets) {
        system = rect(insets);
        java.util.Arrays.fill(typed, null);
        return this;
    }

    public WindowInsets$Builder setStableInsets(Insets insets) {
        stable = rect(insets);
        java.util.Arrays.fill(typedStable, null);
        return this;
    }

    public WindowInsets$Builder setSystemGestureInsets(Insets insets) {
        return this;
    }

    public WindowInsets$Builder setMandatorySystemGestureInsets(Insets insets) {
        return this;
    }

    public WindowInsets$Builder setTappableElementInsets(Insets insets) {
        return this;
    }

    public WindowInsets$Builder setDisplayCutout(DisplayCutout displayCutout) {
        cutout = displayCutout;
        return this;
    }

    public WindowInsets$Builder setInsets(int typeMask, Insets insets) {
        for (int i = 0; i < TYPE_COUNT; i++) if ((typeMask & (1 << i)) != 0) typed[i] = insets;
        return this;
    }

    public WindowInsets$Builder setInsetsIgnoringVisibility(int typeMask, Insets insets) {
        for (int i = 0; i < TYPE_COUNT; i++) if ((typeMask & (1 << i)) != 0) typedStable[i] = insets;
        return this;
    }

    public WindowInsets$Builder setVisible(int typeMask, boolean visible) {
        return this;
    }

    public WindowInsets$Builder setRoundedCorner(int position, Object roundedCorner) {
        return this;
    }

    public WindowInsets build() {
        Rect systemInsets = merge(typed, system == null ? new Rect() : system);
        Rect stableInsets = merge(typedStable, stable == null ? new Rect() : stable);
        try {
            Constructor<WindowInsets> constructor = WindowInsets.class.getConstructor(Rect.class, Rect.class,
                    Rect.class, boolean.class, boolean.class, DisplayCutout.class);
            return constructor.newInstance(systemInsets, new Rect(), stableInsets, round, alwaysConsumeNavBar, cutout);
        } catch (Throwable error) {
            try {
                return WindowInsets.class.getConstructor(Rect.class).newInstance(systemInsets);
            } catch (Throwable fallbackError) {
                throw new IllegalStateException("No WindowInsets constructor", fallbackError);
            }
        }
    }
}
