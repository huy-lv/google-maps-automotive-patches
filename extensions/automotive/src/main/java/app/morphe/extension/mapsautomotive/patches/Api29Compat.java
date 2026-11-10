package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.webkit.WebView;
import android.widget.PopupWindow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Android 10 (API 29) members that Maps Automotive calls without an SDK_INT check, for Android 9.
 * Each method uses the member on API 29+ and the Android 9 equivalent below, often the hidden
 * API 28 method that androidx calls by reflection there. API 29 outlines without an entry here
 * return a default value on Android 9 (see Api29CompatPatch).
 */
@SuppressWarnings({"unused", "NewApi", "JavaReflectionMemberAccess", "unchecked", "rawtypes"})
public final class Api29Compat {
    private static final String TAG = "MorpheApi29Compat";
    private static final Map<String, Method> hiddenMethods = new ConcurrentHashMap<>();
    private static final Object MISSING = new Object();

    private static volatile boolean hiddenApisExempted;

    static {
        exemptHiddenApis();
    }

    private Api29Compat() {
    }

    /**
     * Android 9 knows Insets, WindowInsets.inset and most API 29 members as hidden APIs. Maps links
     * them directly, so the process is exempted from the hidden API checks, through reflection on
     * VMRuntime called by a boot class (the "meta-reflection" that Android 9 allows).
     */
    public static void exemptHiddenApis() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P || hiddenApisExempted) return;
        hiddenApisExempted = true;
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntime, "getRuntime", null);
            Method setExemptions = (Method) getDeclaredMethod.invoke(vmRuntime, "setHiddenApiExemptions",
                    new Class<?>[]{String[].class});
            setExemptions.invoke(getRuntime.invoke(null), new Object[]{new String[]{"L"}});
        } catch (Throwable error) {
            ReportLog.w(TAG, "Hidden API exemption failed", error);
        }
    }

    // WindowInsets: Android 9 has system window and stable insets only, as androidx Impl20 reads them.

    public static Insets getSystemWindowInsets(WindowInsets insets) {
        if (atLeastQ()) return insets.getSystemWindowInsets();
        return Insets.of(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
    }

    public static Insets getStableInsets(WindowInsets insets) {
        if (atLeastQ()) return insets.getStableInsets();
        return Insets.of(insets.getStableInsetLeft(), insets.getStableInsetTop(),
                insets.getStableInsetRight(), insets.getStableInsetBottom());
    }

    public static Insets getSystemGestureInsets(WindowInsets insets) {
        if (atLeastQ()) return insets.getSystemGestureInsets();
        return getSystemWindowInsets(insets);
    }

    public static Insets getMandatorySystemGestureInsets(WindowInsets insets) {
        if (atLeastQ()) return insets.getMandatorySystemGestureInsets();
        return getSystemWindowInsets(insets);
    }

    public static Insets getTappableElementInsets(WindowInsets insets) {
        if (atLeastQ()) return insets.getTappableElementInsets();
        return getSystemWindowInsets(insets);
    }

    /** Insets.max is public since API 29; the hidden Android 9 Insets lacks it. */
    public static Insets max(Insets a, Insets b) {
        if (atLeastQ()) return Insets.max(a, b);
        return Insets.of(Math.max(a.left, b.left), Math.max(a.top, b.top),
                Math.max(a.right, b.right), Math.max(a.bottom, b.bottom));
    }

    private static boolean atLeastQ() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    // Hidden API 28 method of [type] or a superclass, or null.
    private static Method hidden(Class<?> type, String name, Class<?>... parameters) {
        return find(type, name, true, parameters);
    }

    private static Method find(Class<?> type, String name, boolean logMissing, Class<?>... parameters) {
        String key = type.getName() + "#" + name + parameters.length;
        Method method = hiddenMethods.get(key);
        if (method != null) return method;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                method = c.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                hiddenMethods.put(key, method);
                return method;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable error) {
                break;
            }
        }
        if (logMissing) ReportLog.w(TAG, "No hidden " + type.getName() + "." + name);
        return null;
    }

    private static Object callHidden(Object target, String name, Class<?>[] parameters, Object... arguments) {
        Method method = hidden(target.getClass(), name, parameters);
        if (method == null) return MISSING;
        try {
            return method.invoke(target, arguments);
        } catch (Throwable error) {
            ReportLog.w(TAG, "Hidden " + name + " failed", error);
            return MISSING;
        }
    }

    // Context, Service, Activity

    public static String getOpPackageName(Context context) {
        if (atLeastQ()) return context.getOpPackageName();
        Object name = callHidden(context, "getOpPackageName", new Class<?>[0]);
        return name instanceof String ? (String) name : context.getPackageName();
    }

    /** The foreground service type only exists since API 29. */
    public static void startForeground(Service service, int id, Notification notification, int type) {
        if (atLeastQ()) service.startForeground(id, notification, type);
        else service.startForeground(id, notification);
    }

    /** Callbacks arrive on the main thread instead of the executor. */
    public static boolean bindService(Context context, Intent service, int flags, Executor executor,
                                      ServiceConnection connection) {
        if (atLeastQ()) return context.bindService(service, flags, executor, connection);
        return context.bindService(service, connection, flags);
    }

    /**
     * androidx Lifecycle registers its ReportFragment callbacks per activity on API 29 and only reads
     * the pre and post events. Below, the application callbacks of that one activity drive them.
     */
    public static void registerActivityLifecycleCallbacks(Activity activity,
                                                          Application.ActivityLifecycleCallbacks callback) {
        if (atLeastQ()) {
            activity.registerActivityLifecycleCallbacks(callback);
            return;
        }
        activity.getApplication().registerActivityLifecycleCallbacks(new ActivityCallbacks(activity, callback));
    }

    private static final class ActivityCallbacks implements Application.ActivityLifecycleCallbacks {
        private final Activity activity;
        private final Application.ActivityLifecycleCallbacks callback;
        private boolean created;

        ActivityCallbacks(Activity activity, Application.ActivityLifecycleCallbacks callback) {
            this.activity = activity;
            this.callback = callback;
        }

        private void dispatch(String name, Activity activity, Bundle bundle, boolean withBundle) {
            Class<?>[] parameters = withBundle
                    ? new Class<?>[]{Activity.class, Bundle.class} : new Class<?>[]{Activity.class};
            // Callbacks that do not override a pre or post event have nothing to run for it.
            Method method = find(callback.getClass(), name, false, parameters);
            if (method == null) return;
            try {
                if (withBundle) method.invoke(callback, activity, bundle);
                else method.invoke(callback, activity);
            } catch (Throwable error) {
                ReportLog.w(TAG, "Lifecycle " + name + " failed", error);
            }
        }

        private void ensureCreated(Activity a) {
            if (created) return;
            created = true;
            dispatch("onActivityPreCreated", a, null, true);
            callback.onActivityCreated(a, null);
            dispatch("onActivityPostCreated", a, null, true);
        }

        @Override
        public void onActivityCreated(Activity a, Bundle bundle) {
            if (a != activity || created) return;
            created = true;
            dispatch("onActivityPreCreated", a, bundle, true);
            callback.onActivityCreated(a, bundle);
            dispatch("onActivityPostCreated", a, bundle, true);
        }

        @Override
        public void onActivityStarted(Activity a) {
            if (a != activity) return;
            ensureCreated(a);
            dispatch("onActivityPreStarted", a, null, false);
            callback.onActivityStarted(a);
            dispatch("onActivityPostStarted", a, null, false);
        }

        @Override
        public void onActivityResumed(Activity a) {
            if (a != activity) return;
            ensureCreated(a);
            dispatch("onActivityPreResumed", a, null, false);
            callback.onActivityResumed(a);
            dispatch("onActivityPostResumed", a, null, false);
        }

        @Override
        public void onActivityPaused(Activity a) {
            if (a != activity) return;
            dispatch("onActivityPrePaused", a, null, false);
            callback.onActivityPaused(a);
            dispatch("onActivityPostPaused", a, null, false);
        }

        @Override
        public void onActivityStopped(Activity a) {
            if (a != activity) return;
            dispatch("onActivityPreStopped", a, null, false);
            callback.onActivityStopped(a);
            dispatch("onActivityPostStopped", a, null, false);
        }

        @Override
        public void onActivitySaveInstanceState(Activity a, Bundle bundle) {
            if (a != activity) return;
            dispatch("onActivityPreSaveInstanceState", a, bundle, true);
            callback.onActivitySaveInstanceState(a, bundle);
            dispatch("onActivityPostSaveInstanceState", a, bundle, true);
        }

        @Override
        public void onActivityDestroyed(Activity a) {
            if (a != activity) return;
            dispatch("onActivityPreDestroyed", a, null, false);
            callback.onActivityDestroyed(a);
            dispatch("onActivityPostDestroyed", a, null, false);
            a.getApplication().unregisterActivityLifecycleCallbacks(this);
        }
    }

    // Environment, accessibility

    /** Storage before API 29 is the legacy model. */
    public static boolean isExternalStorageLegacy() {
        if (atLeastQ()) return android.os.Environment.isExternalStorageLegacy();
        return true;
    }

    public static int getRecommendedTimeoutMillis(AccessibilityManager manager, int originalTimeout, int flags) {
        if (atLeastQ()) return manager.getRecommendedTimeoutMillis(originalTimeout, flags);
        return originalTimeout;
    }

    // View

    public static float getTransitionAlpha(View view) {
        if (atLeastQ()) return view.getTransitionAlpha();
        Object alpha = callHidden(view, "getTransitionAlpha", new Class<?>[0]);
        return alpha instanceof Float ? (Float) alpha : view.getAlpha();
    }

    public static void setTransitionAlpha(View view, float alpha) {
        if (atLeastQ()) {
            view.setTransitionAlpha(alpha);
            return;
        }
        if (callHidden(view, "setTransitionAlpha", new Class<?>[]{float.class}, alpha) == MISSING) view.setAlpha(alpha);
    }

    public static void setTransitionVisibility(View view, int visibility) {
        if (atLeastQ()) {
            view.setTransitionVisibility(visibility);
            return;
        }
        if (callHidden(view, "setTransitionVisibility", new Class<?>[]{int.class}, visibility) == MISSING) {
            view.setVisibility(visibility);
        }
    }

    public static void setLeftTopRightBottom(View view, int left, int top, int right, int bottom) {
        if (atLeastQ()) {
            view.setLeftTopRightBottom(left, top, right, bottom);
            return;
        }
        Class<?>[] ints = {int.class, int.class, int.class, int.class};
        if (callHidden(view, "setLeftTopRightBottom", ints, left, top, right, bottom) == MISSING) {
            view.setLeft(left);
            view.setTop(top);
            view.setRight(right);
            view.setBottom(bottom);
        }
    }

    public static void transformMatrixToGlobal(View view, Matrix matrix) {
        if (atLeastQ()) view.transformMatrixToGlobal(matrix);
        else callHidden(view, "transformMatrixToGlobal", new Class<?>[]{Matrix.class}, matrix);
    }

    public static void transformMatrixToLocal(View view, Matrix matrix) {
        if (atLeastQ()) view.transformMatrixToLocal(matrix);
        else callHidden(view, "transformMatrixToLocal", new Class<?>[]{Matrix.class}, matrix);
    }

    public static void suppressLayout(ViewGroup group, boolean suppress) {
        if (atLeastQ()) group.suppressLayout(suppress);
        else callHidden(group, "suppressLayout", new Class<?>[]{boolean.class}, suppress);
    }

    public static long getUniqueDrawingId(View view) {
        if (atLeastQ()) return view.getUniqueDrawingId();
        return System.identityHashCode(view);
    }

    public static View.AccessibilityDelegate getAccessibilityDelegate(View view) {
        if (atLeastQ()) return view.getAccessibilityDelegate();
        return accessibilityDelegateField(view);
    }

    public static View.AccessibilityDelegate getAccessibilityDelegate(WebView view) {
        if (atLeastQ()) return view.getAccessibilityDelegate();
        return accessibilityDelegateField(view);
    }

    // As androidx ViewCompat.getAccessibilityDelegateThroughReflection.
    private static View.AccessibilityDelegate accessibilityDelegateField(View view) {
        try {
            Field field = View.class.getDeclaredField("mAccessibilityDelegate");
            field.setAccessible(true);
            return (View.AccessibilityDelegate) field.get(view);
        } catch (Throwable error) {
            return null;
        }
    }

    public static void setEpicenterBounds(PopupWindow window, Rect bounds) {
        if (atLeastQ()) window.setEpicenterBounds(bounds);
        else callHidden(window, "setEpicenterBounds", new Class<?>[]{Rect.class}, bounds);
    }

    public static void setTouchModal(PopupWindow window, boolean touchModal) {
        if (atLeastQ()) window.setTouchModal(touchModal);
        else callHidden(window, "setTouchModal", new Class<?>[]{boolean.class}, touchModal);
    }

    // DisplayCutout: API 28 only lists the bounding rects; a rect at the top edge is the top one.

    public static Rect getBoundingRectTop(DisplayCutout cutout) {
        if (atLeastQ()) return cutout.getBoundingRectTop();
        for (Rect rect : cutout.getBoundingRects()) {
            if (rect.top <= 0) return rect;
        }
        return new Rect();
    }

    public static Rect getBoundingRectLeft(DisplayCutout cutout) {
        if (atLeastQ()) return cutout.getBoundingRectLeft();
        for (Rect rect : cutout.getBoundingRects()) {
            if (rect.left <= 0 && rect.top > 0) return rect;
        }
        return new Rect();
    }

    public static Rect getBoundingRectRight(DisplayCutout cutout) {
        if (atLeastQ()) return cutout.getBoundingRectRight();
        return new Rect();
    }

    public static Rect getBoundingRectBottom(DisplayCutout cutout) {
        if (atLeastQ()) return cutout.getBoundingRectBottom();
        return new Rect();
    }

    // MotionEvent

    public static float getRawX(MotionEvent event, int pointerIndex) {
        if (atLeastQ()) return event.getRawX(pointerIndex);
        return event.getX(pointerIndex) + event.getRawX() - event.getX();
    }

    public static float getRawY(MotionEvent event, int pointerIndex) {
        if (atLeastQ()) return event.getRawY(pointerIndex);
        return event.getY(pointerIndex) + event.getRawY() - event.getY();
    }

    // Resources

    public static float getFloat(Resources resources, int id) {
        if (atLeastQ()) return resources.getFloat(id);
        TypedValue value = new TypedValue();
        resources.getValue(id, value, true);
        if (value.type == TypedValue.TYPE_FLOAT) return value.getFloat();
        throw new Resources.NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " is not a float");
    }

    // Parcel, in the framework's layout

    public static void writeParcelableList(Parcel parcel, List list, int flags) {
        if (atLeastQ()) {
            parcel.writeParcelableList(list, flags);
            return;
        }
        if (list == null) {
            parcel.writeInt(-1);
            return;
        }
        parcel.writeInt(list.size());
        for (Object item : list) parcel.writeParcelable((Parcelable) item, flags);
    }

    public static List readParcelableList(Parcel parcel, List list, ClassLoader loader) {
        if (atLeastQ()) return parcel.readParcelableList(list, loader);
        int size = parcel.readInt();
        if (size == -1) {
            list.clear();
            return list;
        }
        int existing = list.size();
        int i = 0;
        for (; i < existing && i < size; i++) list.set(i, parcel.readParcelable(loader));
        for (; i < size; i++) list.add(parcel.readParcelable(loader));
        for (; i < existing; i++) list.remove(size);
        return list;
    }

    // Graphics

    public static void enableZ(Canvas canvas) {
        if (atLeastQ()) canvas.enableZ();
        else callHidden(canvas, "insertReorderBarrier", new Class<?>[0]);
    }

    public static void disableZ(Canvas canvas) {
        if (atLeastQ()) canvas.disableZ();
        else callHidden(canvas, "insertInorderBarrier", new Class<?>[0]);
    }

    public static void drawColor(Canvas canvas, long color) {
        if (atLeastQ()) canvas.drawColor(color);
        else canvas.drawColor(Color.toArgb(color));
    }

    public static void drawDoubleRoundRect(Canvas canvas, RectF outer, float outerRx, float outerRy,
                                           RectF inner, float innerRx, float innerRy, Paint paint) {
        if (atLeastQ()) {
            canvas.drawDoubleRoundRect(outer, outerRx, outerRy, inner, innerRx, innerRy, paint);
            return;
        }
        Path path = new Path();
        path.addRoundRect(outer, outerRx, outerRy, Path.Direction.CW);
        Path hole = new Path();
        hole.addRoundRect(inner, innerRx, innerRy, Path.Direction.CW);
        path.op(hole, Path.Op.DIFFERENCE);
        canvas.drawPath(path, paint);
    }

    public static void drawDoubleRoundRect(Canvas canvas, RectF outer, float[] outerRadii,
                                           RectF inner, float[] innerRadii, Paint paint) {
        if (atLeastQ()) {
            canvas.drawDoubleRoundRect(outer, outerRadii, inner, innerRadii, paint);
            return;
        }
        Path path = new Path();
        path.addRoundRect(outer, outerRadii, Path.Direction.CW);
        Path hole = new Path();
        hole.addRoundRect(inner, innerRadii, Path.Direction.CW);
        path.op(hole, Path.Op.DIFFERENCE);
        canvas.drawPath(path, paint);
    }

    public static void setColor(Paint paint, long color) {
        if (atLeastQ()) paint.setColor(color);
        else paint.setColor(Color.toArgb(color));
    }

    public static long getColorLong(Paint paint) {
        if (atLeastQ()) return paint.getColorLong();
        return Color.pack(paint.getColor());
    }

    public static void getTextBounds(Paint paint, CharSequence text, int start, int end, Rect bounds) {
        if (atLeastQ()) paint.getTextBounds(text, start, end, bounds);
        else paint.getTextBounds(text.toString(), start, end, bounds);
    }
}
