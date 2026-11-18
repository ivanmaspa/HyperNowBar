package com.hypernowbar.ui;

import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;

/**
 * Applies genuine hardware SurfaceFlinger / HyperOS system backdrop blur to any View inside SystemUI.
 *
 * Strategy:
 *  1. Primary: Official AOSP SurfaceFlinger BackgroundBlurDrawable (ViewRootImpl.createBackgroundBlurDrawable)
 *     layered directly underneath our GlassCapsuleDrawable border/tint — supports exact cornerRadius & 120fps GPU blur.
 *  2. Secondary/Complement: Xiaomi HyperOS native View.setMiBackgroundBlurMode + setMiBackgroundBlurRadius.
 */
public final class SystemBlurHelper {
    private static boolean loggedOnce = false;

    public static boolean applyBackdropBlur(
            final View targetView,
            final Drawable glassOverlay,
            final boolean enabled,
            final int blurRadiusPx,
            final float cornerRadiusPx) {

        if (targetView == null) return false;

        // Always clip outline cleanly to exact rounded rect so GPU blur never bleeds outside corners
        targetView.setClipToOutline(true);
        targetView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerRadiusPx);
            }
        });

        if (!enabled || blurRadiusPx <= 0) {
            clearMiuiBlur(targetView);
            targetView.setBackground(glassOverlay);
            return false;
        }

        // Try Path A: AOSP SurfaceFlinger BackgroundBlurDrawable (works on Android 12-16 & HyperOS 3)
        Drawable sfBlur = tryCreateSurfaceFlingerBlur(targetView, blurRadiusPx, cornerRadiusPx);
        if (sfBlur != null) {
            clearMiuiBlur(targetView);
            LayerDrawable layered = new LayerDrawable(new Drawable[] {sfBlur, glassOverlay});
            targetView.setBackground(layered);
            logOnce("SurfaceFlinger BackgroundBlurDrawable (radius=" + blurRadiusPx + "px)");
            return true;
        }

        // Try Path B: Xiaomi HyperOS native MiBackgroundBlur on View
        if (tryApplyMiuiBlur(targetView, blurRadiusPx)) {
            targetView.setBackground(glassOverlay);
            logOnce("HyperOS MiBackgroundBlur (radius=" + blurRadiusPx + "px)");
            return true;
        }

        // Fallback (e.g. inside Settings preview Activity or before attach): standard frosted glass
        targetView.setBackground(glassOverlay);
        return false;
    }

    private static Drawable tryCreateSurfaceFlingerBlur(View view, int blurRadiusPx, float cornerRadiusPx) {
        try {
            Method getViewRoot = View.class.getMethod("getViewRootImpl");
            Object viewRoot = getViewRoot.invoke(view);
            if (viewRoot == null) return null;

            Method createBlur = viewRoot.getClass().getMethod("createBackgroundBlurDrawable");
            Object drawableObj = createBlur.invoke(viewRoot);
            if (!(drawableObj instanceof Drawable)) return null;

            Drawable blurDrawable = (Drawable) drawableObj;
            Class<?> bdCls = blurDrawable.getClass();

            try {
                Method setRadius = bdCls.getMethod("setBlurRadius", int.class);
                setRadius.invoke(blurDrawable, blurRadiusPx);
            } catch (Throwable ignored) {}

            try {
                Method setCorner = bdCls.getMethod("setCornerRadius", float.class);
                setCorner.invoke(blurDrawable, cornerRadiusPx);
            } catch (Throwable ignored) {}

            try {
                Method setColor = bdCls.getMethod("setColor", int.class);
                setColor.invoke(blurDrawable, Color.TRANSPARENT);
            } catch (Throwable ignored) {}

            return blurDrawable;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean tryApplyMiuiBlur(View view, int blurRadiusPx) {
        try {
            Method setMode = View.class.getMethod("setMiBackgroundBlurMode", int.class);
            Method setRadius = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            setMode.invoke(view, 1); // 1 = real-time backdrop blur
            setRadius.invoke(view, blurRadiusPx);
            try {
                Method setScale = View.class.getMethod("setMiBackgroundBlurScaleRatio", float.class);
                setScale.invoke(view, 1.0f);
            } catch (Throwable ignored) {}
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void clearMiuiBlur(View view) {
        try {
            Method setMode = View.class.getMethod("setMiBackgroundBlurMode", int.class);
            setMode.invoke(view, 0);
        } catch (Throwable ignored) {}
    }

    private static void logOnce(String info) {
        if (!loggedOnce) {
            loggedOnce = true;
            try {
                XposedBridge.log("[HyperNowBar] System blur active: " + info);
            } catch (Throwable ignored) {}
        }
    }
}
