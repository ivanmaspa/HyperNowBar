package com.hypernowbar.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;

public final class NowBarConfig {
    public static final String PACKAGE_NAME = "com.hypernowbar";
    public static final String PREFS_NAME = "nowbar_prefs";
    public static final String ACTION_CONFIG_CHANGED = "com.hypernowbar.ACTION_CONFIG_CHANGED";
    private static final int CURRENT_PREFS_VERSION = 3;

    public static final int STYLE_ONEUI_GLOW = 0;
    public static final int STYLE_COLOROS_GLASS = 1;
    public static final int STYLE_MINIMAL_MATTE = 2;
    public static final int STYLE_MONOCHROME_BW = 3;

    public static final int WIDTH_COMPACT = 0;
    public static final int WIDTH_STANDARD = 1;
    public static final int WIDTH_WIDE = 2;

    public boolean enabled = true;
    public boolean hideStockNotifications = true;
    public boolean clearNotifsAfterUnlock = true;
    public boolean iosNotifStack = true;
    public boolean systemBlurEnabled = true;
    public boolean hideOnBouncer = true;
    public boolean hideOnAod = true;
    public boolean alwaysShowCapsule = false;
    public boolean showMediaWhenPaused = true;
    public boolean showEqualizer = true;
    public boolean showQuickMediaButtons = true;
    public boolean tapMediaOpensApp = true;
    public boolean tapNotifOpensApp = true;
    public boolean dynamicGlowColor = true;

    public int bottomMarginDp = 152;      // Comfortably above Xiaomi 13 glowing fingerprint scanner
    public int sheetBottomMarginDp = 162; // Expanded sheet well clear of fingerprint scanner
    public int cornerRadiusDp = 24;       // 10..28 dp
    public int capsuleWidthMode = WIDTH_STANDARD;
    public int capsuleAlpha = 72;         // Balanced translucent tint for real GPU system blur
    public int blurRadiusPx = 96;         // 20..160 px system backdrop blur radius
    public int scrimAlpha = 60;           // 0..90%
    public int notifMaxHeightDp = 290;    // 160..440 dp
    public int style = STYLE_ONEUI_GLOW;

    public NowBarConfig() {}

    public NowBarConfig copy() {
        NowBarConfig c = new NowBarConfig();
        c.enabled = enabled;
        c.hideStockNotifications = hideStockNotifications;
        c.clearNotifsAfterUnlock = clearNotifsAfterUnlock;
        c.iosNotifStack = iosNotifStack;
        c.systemBlurEnabled = systemBlurEnabled;
        c.hideOnBouncer = hideOnBouncer;
        c.hideOnAod = hideOnAod;
        c.alwaysShowCapsule = alwaysShowCapsule;
        c.showMediaWhenPaused = showMediaWhenPaused;
        c.showEqualizer = showEqualizer;
        c.showQuickMediaButtons = showQuickMediaButtons;
        c.tapMediaOpensApp = tapMediaOpensApp;
        c.tapNotifOpensApp = tapNotifOpensApp;
        c.dynamicGlowColor = dynamicGlowColor;
        c.bottomMarginDp = bottomMarginDp;
        c.sheetBottomMarginDp = sheetBottomMarginDp;
        c.cornerRadiusDp = cornerRadiusDp;
        c.capsuleWidthMode = capsuleWidthMode;
        c.capsuleAlpha = capsuleAlpha;
        c.blurRadiusPx = blurRadiusPx;
        c.scrimAlpha = scrimAlpha;
        c.notifMaxHeightDp = notifMaxHeightDp;
        c.style = style;
        return c;
    }

    @SuppressWarnings("deprecation")
    public static SharedPreferences openAppPrefs(Context context) {
        try {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public static NowBarConfig fromPrefs(SharedPreferences prefs) {
        NowBarConfig c = new NowBarConfig();
        if (prefs == null) return c;
        int ver = prefs.getInt("prefsVersion", 1);
        c.enabled = prefs.getBoolean("enabled", c.enabled);
        c.hideStockNotifications = prefs.getBoolean("hideStockNotifications", c.hideStockNotifications);
        c.clearNotifsAfterUnlock = prefs.getBoolean("clearNotifsAfterUnlock", c.clearNotifsAfterUnlock);
        c.iosNotifStack = prefs.getBoolean("iosNotifStack", c.iosNotifStack);
        c.systemBlurEnabled = prefs.getBoolean("systemBlurEnabled", c.systemBlurEnabled);
        c.hideOnBouncer = prefs.getBoolean("hideOnBouncer", c.hideOnBouncer);
        c.hideOnAod = prefs.getBoolean("hideOnAod", c.hideOnAod);
        c.alwaysShowCapsule = prefs.getBoolean("alwaysShowCapsule", c.alwaysShowCapsule);
        c.showMediaWhenPaused = prefs.getBoolean("showMediaWhenPaused", c.showMediaWhenPaused);
        c.showEqualizer = prefs.getBoolean("showEqualizer", c.showEqualizer);
        c.showQuickMediaButtons = prefs.getBoolean("showQuickMediaButtons", c.showQuickMediaButtons);
        c.tapMediaOpensApp = prefs.getBoolean("tapMediaOpensApp", c.tapMediaOpensApp);
        c.tapNotifOpensApp = prefs.getBoolean("tapNotifOpensApp", c.tapNotifOpensApp);
        c.dynamicGlowColor = prefs.getBoolean("dynamicGlowColor", c.dynamicGlowColor);
        c.bottomMarginDp = prefs.getInt("bottomMarginDp", c.bottomMarginDp);
        c.sheetBottomMarginDp = prefs.getInt("sheetBottomMarginDp", c.sheetBottomMarginDp);
        c.cornerRadiusDp = prefs.getInt("cornerRadiusDp", c.cornerRadiusDp);
        c.capsuleWidthMode = prefs.getInt("capsuleWidthMode", c.capsuleWidthMode);
        c.capsuleAlpha = prefs.getInt("capsuleAlpha", c.capsuleAlpha);
        c.blurRadiusPx = prefs.getInt("blurRadiusPx", c.blurRadiusPx);
        c.scrimAlpha = prefs.getInt("scrimAlpha", c.scrimAlpha);
        c.notifMaxHeightDp = prefs.getInt("notifMaxHeightDp", c.notifMaxHeightDp);
        c.style = prefs.getInt("style", c.style);

        // One-time v2.2 migration: bump old too-low scanner defaults slightly higher + tune alpha for real blur
        if (ver < CURRENT_PREFS_VERSION) {
            if (c.bottomMarginDp <= 132) c.bottomMarginDp = 152;
            if (c.sheetBottomMarginDp <= 142) c.sheetBottomMarginDp = 162;
            if (c.capsuleAlpha == 88) c.capsuleAlpha = 72;
            c.saveToPrefs(prefs);
        }
        return c;
    }

    public static NowBarConfig fromXPrefs(XSharedPreferences xprefs) {
        NowBarConfig c = new NowBarConfig();
        if (xprefs == null) return c;
        try {
            xprefs.reload();
            int ver = xprefs.getInt("prefsVersion", 1);
            c.enabled = xprefs.getBoolean("enabled", c.enabled);
            c.hideStockNotifications = xprefs.getBoolean("hideStockNotifications", c.hideStockNotifications);
            c.clearNotifsAfterUnlock = xprefs.getBoolean("clearNotifsAfterUnlock", c.clearNotifsAfterUnlock);
            c.iosNotifStack = xprefs.getBoolean("iosNotifStack", c.iosNotifStack);
            c.systemBlurEnabled = xprefs.getBoolean("systemBlurEnabled", c.systemBlurEnabled);
            c.hideOnBouncer = xprefs.getBoolean("hideOnBouncer", c.hideOnBouncer);
            c.hideOnAod = xprefs.getBoolean("hideOnAod", c.hideOnAod);
            c.alwaysShowCapsule = xprefs.getBoolean("alwaysShowCapsule", c.alwaysShowCapsule);
            c.showMediaWhenPaused = xprefs.getBoolean("showMediaWhenPaused", c.showMediaWhenPaused);
            c.showEqualizer = xprefs.getBoolean("showEqualizer", c.showEqualizer);
            c.showQuickMediaButtons = xprefs.getBoolean("showQuickMediaButtons", c.showQuickMediaButtons);
            c.tapMediaOpensApp = xprefs.getBoolean("tapMediaOpensApp", c.tapMediaOpensApp);
            c.tapNotifOpensApp = xprefs.getBoolean("tapNotifOpensApp", c.tapNotifOpensApp);
            c.dynamicGlowColor = xprefs.getBoolean("dynamicGlowColor", c.dynamicGlowColor);
            c.bottomMarginDp = xprefs.getInt("bottomMarginDp", c.bottomMarginDp);
            c.sheetBottomMarginDp = xprefs.getInt("sheetBottomMarginDp", c.sheetBottomMarginDp);
            c.cornerRadiusDp = xprefs.getInt("cornerRadiusDp", c.cornerRadiusDp);
            c.capsuleWidthMode = xprefs.getInt("capsuleWidthMode", c.capsuleWidthMode);
            c.capsuleAlpha = xprefs.getInt("capsuleAlpha", c.capsuleAlpha);
            c.blurRadiusPx = xprefs.getInt("blurRadiusPx", c.blurRadiusPx);
            c.scrimAlpha = xprefs.getInt("scrimAlpha", c.scrimAlpha);
            c.notifMaxHeightDp = xprefs.getInt("notifMaxHeightDp", c.notifMaxHeightDp);
            c.style = xprefs.getInt("style", c.style);

            if (ver < CURRENT_PREFS_VERSION) {
                if (c.bottomMarginDp <= 132) c.bottomMarginDp = 152;
                if (c.sheetBottomMarginDp <= 142) c.sheetBottomMarginDp = 162;
                if (c.capsuleAlpha == 88) c.capsuleAlpha = 72;
            }
        } catch (Throwable ignored) {}
        return c;
    }

    public void saveToPrefs(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
            .putInt("prefsVersion", CURRENT_PREFS_VERSION)
            .putBoolean("enabled", enabled)
            .putBoolean("hideStockNotifications", hideStockNotifications)
            .putBoolean("clearNotifsAfterUnlock", clearNotifsAfterUnlock)
            .putBoolean("iosNotifStack", iosNotifStack)
            .putBoolean("systemBlurEnabled", systemBlurEnabled)
            .putBoolean("hideOnBouncer", hideOnBouncer)
            .putBoolean("hideOnAod", hideOnAod)
            .putBoolean("alwaysShowCapsule", alwaysShowCapsule)
            .putBoolean("showMediaWhenPaused", showMediaWhenPaused)
            .putBoolean("showEqualizer", showEqualizer)
            .putBoolean("showQuickMediaButtons", showQuickMediaButtons)
            .putBoolean("tapMediaOpensApp", tapMediaOpensApp)
            .putBoolean("tapNotifOpensApp", tapNotifOpensApp)
            .putBoolean("dynamicGlowColor", dynamicGlowColor)
            .putInt("bottomMarginDp", bottomMarginDp)
            .putInt("sheetBottomMarginDp", sheetBottomMarginDp)
            .putInt("cornerRadiusDp", cornerRadiusDp)
            .putInt("capsuleWidthMode", capsuleWidthMode)
            .putInt("capsuleAlpha", capsuleAlpha)
            .putInt("blurRadiusPx", blurRadiusPx)
            .putInt("scrimAlpha", scrimAlpha)
            .putInt("notifMaxHeightDp", notifMaxHeightDp)
            .putInt("style", style)
            .apply();
    }

    public Intent toBroadcastIntent() {
        Intent intent = new Intent(ACTION_CONFIG_CHANGED);
        intent.putExtra("enabled", enabled);
        intent.putExtra("hideStockNotifications", hideStockNotifications);
        intent.putExtra("clearNotifsAfterUnlock", clearNotifsAfterUnlock);
        intent.putExtra("iosNotifStack", iosNotifStack);
        intent.putExtra("systemBlurEnabled", systemBlurEnabled);
        intent.putExtra("hideOnBouncer", hideOnBouncer);
        intent.putExtra("hideOnAod", hideOnAod);
        intent.putExtra("alwaysShowCapsule", alwaysShowCapsule);
        intent.putExtra("showMediaWhenPaused", showMediaWhenPaused);
        intent.putExtra("showEqualizer", showEqualizer);
        intent.putExtra("showQuickMediaButtons", showQuickMediaButtons);
        intent.putExtra("tapMediaOpensApp", tapMediaOpensApp);
        intent.putExtra("tapNotifOpensApp", tapNotifOpensApp);
        intent.putExtra("dynamicGlowColor", dynamicGlowColor);
        intent.putExtra("bottomMarginDp", bottomMarginDp);
        intent.putExtra("sheetBottomMarginDp", sheetBottomMarginDp);
        intent.putExtra("cornerRadiusDp", cornerRadiusDp);
        intent.putExtra("capsuleWidthMode", capsuleWidthMode);
        intent.putExtra("capsuleAlpha", capsuleAlpha);
        intent.putExtra("blurRadiusPx", blurRadiusPx);
        intent.putExtra("scrimAlpha", scrimAlpha);
        intent.putExtra("notifMaxHeightDp", notifMaxHeightDp);
        intent.putExtra("style", style);
        return intent;
    }

    public static NowBarConfig fromIntent(Intent intent, NowBarConfig fallback) {
        NowBarConfig c = fallback != null ? fallback.copy() : new NowBarConfig();
        if (intent == null) return c;
        c.enabled = intent.getBooleanExtra("enabled", c.enabled);
        c.hideStockNotifications = intent.getBooleanExtra("hideStockNotifications", c.hideStockNotifications);
        c.clearNotifsAfterUnlock = intent.getBooleanExtra("clearNotifsAfterUnlock", c.clearNotifsAfterUnlock);
        c.iosNotifStack = intent.getBooleanExtra("iosNotifStack", c.iosNotifStack);
        c.systemBlurEnabled = intent.getBooleanExtra("systemBlurEnabled", c.systemBlurEnabled);
        c.hideOnBouncer = intent.getBooleanExtra("hideOnBouncer", c.hideOnBouncer);
        c.hideOnAod = intent.getBooleanExtra("hideOnAod", c.hideOnAod);
        c.alwaysShowCapsule = intent.getBooleanExtra("alwaysShowCapsule", c.alwaysShowCapsule);
        c.showMediaWhenPaused = intent.getBooleanExtra("showMediaWhenPaused", c.showMediaWhenPaused);
        c.showEqualizer = intent.getBooleanExtra("showEqualizer", c.showEqualizer);
        c.showQuickMediaButtons = intent.getBooleanExtra("showQuickMediaButtons", c.showQuickMediaButtons);
        c.tapMediaOpensApp = intent.getBooleanExtra("tapMediaOpensApp", c.tapMediaOpensApp);
        c.tapNotifOpensApp = intent.getBooleanExtra("tapNotifOpensApp", c.tapNotifOpensApp);
        c.dynamicGlowColor = intent.getBooleanExtra("dynamicGlowColor", c.dynamicGlowColor);
        c.bottomMarginDp = intent.getIntExtra("bottomMarginDp", c.bottomMarginDp);
        c.sheetBottomMarginDp = intent.getIntExtra("sheetBottomMarginDp", c.sheetBottomMarginDp);
        c.cornerRadiusDp = intent.getIntExtra("cornerRadiusDp", c.cornerRadiusDp);
        c.capsuleWidthMode = intent.getIntExtra("capsuleWidthMode", c.capsuleWidthMode);
        c.capsuleAlpha = intent.getIntExtra("capsuleAlpha", c.capsuleAlpha);
        c.blurRadiusPx = intent.getIntExtra("blurRadiusPx", c.blurRadiusPx);
        c.scrimAlpha = intent.getIntExtra("scrimAlpha", c.scrimAlpha);
        c.notifMaxHeightDp = intent.getIntExtra("notifMaxHeightDp", c.notifMaxHeightDp);
        c.style = intent.getIntExtra("style", c.style);
        return c;
    }
}
