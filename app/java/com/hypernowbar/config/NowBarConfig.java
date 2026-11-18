package com.hypernowbar.config;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import java.util.HashMap;
import java.util.Map;

public final class NowBarConfig {
    public static final String PACKAGE_NAME = "com.hypernowbar";
    public static final String PREFS_NAME = "nowbar_prefs";
    public static final String ACTION_CONFIG_CHANGED = "com.hypernowbar.ACTION_CONFIG_CHANGED";
    private static final int CURRENT_PREFS_VERSION = 5;

    public static final int STYLE_ONEUI_GLOW = 0;
    public static final int STYLE_COLOROS_GLASS = 1;
    public static final int STYLE_MINIMAL_MATTE = 2;
    public static final int STYLE_MONOCHROME_BW = 3;

    public static final int WIDTH_COMPACT = 0;
    public static final int WIDTH_STANDARD = 1;
    public static final int WIDTH_WIDE = 2;

    public static final int NOTIF_SOURCE_ALL_SHADE = 0;
    public static final int NOTIF_SOURCE_AFTER_SCREEN_OFF = 1;

    public static final int ANIM_SPRING_IOS = 0;
    public static final int ANIM_FLUID_HYPER = 1;
    public static final int ANIM_SLIDE_UP = 2;
    public static final int ANIM_INSTANT = 3;

    public static final int[] ACCENT_SWATCHES = new int[] {
        0xFF6C8CFF, // Indigo
        0xFFE6E9F0, // Silver Chrome
        0xFF4AE3B5, // Emerald Mint
        0xFFFF6B9D, // Neon Pink
        0xFFFFB74D, // Warm Amber
        0xFFB388FF, // Lavender
        0xFF4FC3F7, // Ice Blue
        0xFFFF5252  // Crimson
    };

    public boolean enabled = true;
    public boolean hideStockNotifications = true;
    public boolean clearNotifsAfterUnlock = true;
    public int notifSourceMode = NOTIF_SOURCE_ALL_SHADE;

    // Visual & Glass
    public boolean systemBlurEnabled = true;
    public boolean backdropBlurEnabled = true;
    public boolean iosLiquidGlass = false;      // Optional iOS Liquid Glass mode (OFF by default!)
    public boolean betaCompactMode = false;     // Beta: no outer menu box, compact adaptive player & stack, folds on screen off/on
    public boolean dynamicGlowColor = true;
    public int manualAccentColor = 0xFF6C8CFF;

    // Animations
    public int animStyle = ANIM_SPRING_IOS;
    public int animSpeedPct = 100;              // 50..180%

    // Behavior
    public boolean autoAdaptPosition = true;    // Auto-adapt bottom margin to UDFPS (high) vs no-UDFPS (low) on any phone
    public boolean hideLauncherIcon = false;    // Hide app icon from launcher (open via Vector/LSPosed module button)
    public boolean hideOnBouncer = true;
    public boolean hideOnAod = true;
    public boolean alwaysShowCapsule = false;
    public boolean showMediaWhenPaused = true;
    public boolean showEqualizer = true;
    public boolean showQuickMediaButtons = true;
    public boolean tapMediaOpensApp = true;
    public boolean tapNotifOpensApp = true;

    // Geometry & Radii
    public int bottomMarginDp = 152;            // Comfortably above Xiaomi 13 fingerprint scanner
    public int sheetBottomMarginDp = 162;       // Expanded layout strictly above scanner
    public int cornerRadiusDp = 24;             // 10..28 dp
    public int borderWidthDp = 2;               // 0..4 dp
    public int pillFontSizeSp = 13;             // 11..15 sp
    public int capsuleWidthMode = WIDTH_STANDARD;
    public int capsuleAlpha = 72;               // 35..100%
    public int blurRadiusPx = 96;               // 20..160 px capsule & sheet GPU blur
    public int backdropBlurRadiusPx = 68;       // 0..160 px full-screen background blur when expanded
    public int scrimAlpha = 48;                 // 0..90% background dim
    public int style = STYLE_ONEUI_GLOW;

    public NowBarConfig() {}

    public NowBarConfig copy() {
        NowBarConfig c = new NowBarConfig();
        c.enabled = enabled;
        c.hideStockNotifications = hideStockNotifications;
        c.clearNotifsAfterUnlock = clearNotifsAfterUnlock;
        c.notifSourceMode = notifSourceMode;
        c.systemBlurEnabled = systemBlurEnabled;
        c.backdropBlurEnabled = backdropBlurEnabled;
        c.iosLiquidGlass = iosLiquidGlass;
        c.betaCompactMode = betaCompactMode;
        c.dynamicGlowColor = dynamicGlowColor;
        c.manualAccentColor = manualAccentColor;
        c.animStyle = animStyle;
        c.animSpeedPct = animSpeedPct;
        c.autoAdaptPosition = autoAdaptPosition;
        c.hideLauncherIcon = hideLauncherIcon;
        c.hideOnBouncer = hideOnBouncer;
        c.hideOnAod = hideOnAod;
        c.alwaysShowCapsule = alwaysShowCapsule;
        c.showMediaWhenPaused = showMediaWhenPaused;
        c.showEqualizer = showEqualizer;
        c.showQuickMediaButtons = showQuickMediaButtons;
        c.tapMediaOpensApp = tapMediaOpensApp;
        c.tapNotifOpensApp = tapNotifOpensApp;
        c.bottomMarginDp = bottomMarginDp;
        c.sheetBottomMarginDp = sheetBottomMarginDp;
        c.cornerRadiusDp = cornerRadiusDp;
        c.borderWidthDp = borderWidthDp;
        c.pillFontSizeSp = pillFontSizeSp;
        c.capsuleWidthMode = capsuleWidthMode;
        c.capsuleAlpha = capsuleAlpha;
        c.blurRadiusPx = blurRadiusPx;
        c.backdropBlurRadiusPx = backdropBlurRadiusPx;
        c.scrimAlpha = scrimAlpha;
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
        c.notifSourceMode = prefs.getInt("notifSourceMode", c.notifSourceMode);
        c.systemBlurEnabled = prefs.getBoolean("systemBlurEnabled", c.systemBlurEnabled);
        c.backdropBlurEnabled = prefs.getBoolean("backdropBlurEnabled", c.backdropBlurEnabled);
        c.iosLiquidGlass = prefs.getBoolean("iosLiquidGlass", c.iosLiquidGlass);
        c.betaCompactMode = prefs.getBoolean("betaCompactMode", c.betaCompactMode);
        c.dynamicGlowColor = prefs.getBoolean("dynamicGlowColor", c.dynamicGlowColor);
        c.manualAccentColor = prefs.getInt("manualAccentColor", c.manualAccentColor);
        c.animStyle = prefs.getInt("animStyle", c.animStyle);
        c.animSpeedPct = prefs.getInt("animSpeedPct", c.animSpeedPct);
        c.autoAdaptPosition = prefs.getBoolean("autoAdaptPosition", c.autoAdaptPosition);
        c.hideLauncherIcon = prefs.getBoolean("hideLauncherIcon", c.hideLauncherIcon);
        c.hideOnBouncer = prefs.getBoolean("hideOnBouncer", c.hideOnBouncer);
        c.hideOnAod = prefs.getBoolean("hideOnAod", c.hideOnAod);
        c.alwaysShowCapsule = prefs.getBoolean("alwaysShowCapsule", c.alwaysShowCapsule);
        c.showMediaWhenPaused = prefs.getBoolean("showMediaWhenPaused", c.showMediaWhenPaused);
        c.showEqualizer = prefs.getBoolean("showEqualizer", c.showEqualizer);
        c.showQuickMediaButtons = prefs.getBoolean("showQuickMediaButtons", c.showQuickMediaButtons);
        c.tapMediaOpensApp = prefs.getBoolean("tapMediaOpensApp", c.tapMediaOpensApp);
        c.tapNotifOpensApp = prefs.getBoolean("tapNotifOpensApp", c.tapNotifOpensApp);
        c.bottomMarginDp = prefs.getInt("bottomMarginDp", c.bottomMarginDp);
        c.sheetBottomMarginDp = prefs.getInt("sheetBottomMarginDp", c.sheetBottomMarginDp);
        c.cornerRadiusDp = prefs.getInt("cornerRadiusDp", c.cornerRadiusDp);
        c.borderWidthDp = prefs.getInt("borderWidthDp", c.borderWidthDp);
        c.pillFontSizeSp = prefs.getInt("pillFontSizeSp", c.pillFontSizeSp);
        c.capsuleWidthMode = prefs.getInt("capsuleWidthMode", c.capsuleWidthMode);
        c.capsuleAlpha = prefs.getInt("capsuleAlpha", c.capsuleAlpha);
        c.blurRadiusPx = prefs.getInt("blurRadiusPx", c.blurRadiusPx);
        c.backdropBlurRadiusPx = prefs.getInt("backdropBlurRadiusPx", c.backdropBlurRadiusPx);
        c.scrimAlpha = prefs.getInt("scrimAlpha", c.scrimAlpha);
        c.style = prefs.getInt("style", c.style);

        if (ver < CURRENT_PREFS_VERSION) {
            if (c.bottomMarginDp <= 132) c.bottomMarginDp = 152;
            if (c.sheetBottomMarginDp <= 142) c.sheetBottomMarginDp = 162;
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
            c.notifSourceMode = xprefs.getInt("notifSourceMode", c.notifSourceMode);
            c.systemBlurEnabled = xprefs.getBoolean("systemBlurEnabled", c.systemBlurEnabled);
            c.backdropBlurEnabled = xprefs.getBoolean("backdropBlurEnabled", c.backdropBlurEnabled);
            c.iosLiquidGlass = xprefs.getBoolean("iosLiquidGlass", c.iosLiquidGlass);
            c.betaCompactMode = xprefs.getBoolean("betaCompactMode", c.betaCompactMode);
            c.dynamicGlowColor = xprefs.getBoolean("dynamicGlowColor", c.dynamicGlowColor);
            c.manualAccentColor = xprefs.getInt("manualAccentColor", c.manualAccentColor);
            c.animStyle = xprefs.getInt("animStyle", c.animStyle);
            c.animSpeedPct = xprefs.getInt("animSpeedPct", c.animSpeedPct);
            c.autoAdaptPosition = xprefs.getBoolean("autoAdaptPosition", c.autoAdaptPosition);
            c.hideLauncherIcon = xprefs.getBoolean("hideLauncherIcon", c.hideLauncherIcon);
            c.hideOnBouncer = xprefs.getBoolean("hideOnBouncer", c.hideOnBouncer);
            c.hideOnAod = xprefs.getBoolean("hideOnAod", c.hideOnAod);
            c.alwaysShowCapsule = xprefs.getBoolean("alwaysShowCapsule", c.alwaysShowCapsule);
            c.showMediaWhenPaused = xprefs.getBoolean("showMediaWhenPaused", c.showMediaWhenPaused);
            c.showEqualizer = xprefs.getBoolean("showEqualizer", c.showEqualizer);
            c.showQuickMediaButtons = xprefs.getBoolean("showQuickMediaButtons", c.showQuickMediaButtons);
            c.tapMediaOpensApp = xprefs.getBoolean("tapMediaOpensApp", c.tapMediaOpensApp);
            c.tapNotifOpensApp = xprefs.getBoolean("tapNotifOpensApp", c.tapNotifOpensApp);
            c.bottomMarginDp = xprefs.getInt("bottomMarginDp", c.bottomMarginDp);
            c.sheetBottomMarginDp = xprefs.getInt("sheetBottomMarginDp", c.sheetBottomMarginDp);
            c.cornerRadiusDp = xprefs.getInt("cornerRadiusDp", c.cornerRadiusDp);
            c.borderWidthDp = xprefs.getInt("borderWidthDp", c.borderWidthDp);
            c.pillFontSizeSp = xprefs.getInt("pillFontSizeSp", c.pillFontSizeSp);
            c.capsuleWidthMode = xprefs.getInt("capsuleWidthMode", c.capsuleWidthMode);
            c.capsuleAlpha = xprefs.getInt("capsuleAlpha", c.capsuleAlpha);
            c.blurRadiusPx = xprefs.getInt("blurRadiusPx", c.blurRadiusPx);
            c.backdropBlurRadiusPx = xprefs.getInt("backdropBlurRadiusPx", c.backdropBlurRadiusPx);
            c.scrimAlpha = xprefs.getInt("scrimAlpha", c.scrimAlpha);
            c.style = xprefs.getInt("style", c.style);

            if (ver < CURRENT_PREFS_VERSION) {
                if (c.bottomMarginDp <= 132) c.bottomMarginDp = 152;
                if (c.sheetBottomMarginDp <= 142) c.sheetBottomMarginDp = 162;
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
            .putInt("notifSourceMode", notifSourceMode)
            .putBoolean("systemBlurEnabled", systemBlurEnabled)
            .putBoolean("backdropBlurEnabled", backdropBlurEnabled)
            .putBoolean("iosLiquidGlass", iosLiquidGlass)
            .putBoolean("betaCompactMode", betaCompactMode)
            .putBoolean("dynamicGlowColor", dynamicGlowColor)
            .putInt("manualAccentColor", manualAccentColor)
            .putInt("animStyle", animStyle)
            .putInt("animSpeedPct", animSpeedPct)
            .putBoolean("autoAdaptPosition", autoAdaptPosition)
            .putBoolean("hideLauncherIcon", hideLauncherIcon)
            .putBoolean("hideOnBouncer", hideOnBouncer)
            .putBoolean("hideOnAod", hideOnAod)
            .putBoolean("alwaysShowCapsule", alwaysShowCapsule)
            .putBoolean("showMediaWhenPaused", showMediaWhenPaused)
            .putBoolean("showEqualizer", showEqualizer)
            .putBoolean("showQuickMediaButtons", showQuickMediaButtons)
            .putBoolean("tapMediaOpensApp", tapMediaOpensApp)
            .putBoolean("tapNotifOpensApp", tapNotifOpensApp)
            .putInt("bottomMarginDp", bottomMarginDp)
            .putInt("sheetBottomMarginDp", sheetBottomMarginDp)
            .putInt("cornerRadiusDp", cornerRadiusDp)
            .putInt("borderWidthDp", borderWidthDp)
            .putInt("pillFontSizeSp", pillFontSizeSp)
            .putInt("capsuleWidthMode", capsuleWidthMode)
            .putInt("capsuleAlpha", capsuleAlpha)
            .putInt("blurRadiusPx", blurRadiusPx)
            .putInt("backdropBlurRadiusPx", backdropBlurRadiusPx)
            .putInt("scrimAlpha", scrimAlpha)
            .putInt("style", style)
            .apply();
    }

    public Intent toBroadcastIntent() {
        Intent intent = new Intent(ACTION_CONFIG_CHANGED);
        intent.putExtra("enabled", enabled);
        intent.putExtra("hideStockNotifications", hideStockNotifications);
        intent.putExtra("clearNotifsAfterUnlock", clearNotifsAfterUnlock);
        intent.putExtra("notifSourceMode", notifSourceMode);
        intent.putExtra("systemBlurEnabled", systemBlurEnabled);
        intent.putExtra("backdropBlurEnabled", backdropBlurEnabled);
        intent.putExtra("iosLiquidGlass", iosLiquidGlass);
        intent.putExtra("betaCompactMode", betaCompactMode);
        intent.putExtra("dynamicGlowColor", dynamicGlowColor);
        intent.putExtra("manualAccentColor", manualAccentColor);
        intent.putExtra("animStyle", animStyle);
        intent.putExtra("animSpeedPct", animSpeedPct);
        intent.putExtra("autoAdaptPosition", autoAdaptPosition);
        intent.putExtra("hideLauncherIcon", hideLauncherIcon);
        intent.putExtra("hideOnBouncer", hideOnBouncer);
        intent.putExtra("hideOnAod", hideOnAod);
        intent.putExtra("alwaysShowCapsule", alwaysShowCapsule);
        intent.putExtra("showMediaWhenPaused", showMediaWhenPaused);
        intent.putExtra("showEqualizer", showEqualizer);
        intent.putExtra("showQuickMediaButtons", showQuickMediaButtons);
        intent.putExtra("tapMediaOpensApp", tapMediaOpensApp);
        intent.putExtra("tapNotifOpensApp", tapNotifOpensApp);
        intent.putExtra("bottomMarginDp", bottomMarginDp);
        intent.putExtra("sheetBottomMarginDp", sheetBottomMarginDp);
        intent.putExtra("cornerRadiusDp", cornerRadiusDp);
        intent.putExtra("borderWidthDp", borderWidthDp);
        intent.putExtra("pillFontSizeSp", pillFontSizeSp);
        intent.putExtra("capsuleWidthMode", capsuleWidthMode);
        intent.putExtra("capsuleAlpha", capsuleAlpha);
        intent.putExtra("blurRadiusPx", blurRadiusPx);
        intent.putExtra("backdropBlurRadiusPx", backdropBlurRadiusPx);
        intent.putExtra("scrimAlpha", scrimAlpha);
        intent.putExtra("style", style);
        return intent;
    }

    public static NowBarConfig fromIntent(Intent intent, NowBarConfig fallback) {
        NowBarConfig c = fallback != null ? fallback.copy() : new NowBarConfig();
        if (intent == null) return c;
        c.enabled = intent.getBooleanExtra("enabled", c.enabled);
        c.hideStockNotifications = intent.getBooleanExtra("hideStockNotifications", c.hideStockNotifications);
        c.clearNotifsAfterUnlock = intent.getBooleanExtra("clearNotifsAfterUnlock", c.clearNotifsAfterUnlock);
        c.notifSourceMode = intent.getIntExtra("notifSourceMode", c.notifSourceMode);
        c.systemBlurEnabled = intent.getBooleanExtra("systemBlurEnabled", c.systemBlurEnabled);
        c.backdropBlurEnabled = intent.getBooleanExtra("backdropBlurEnabled", c.backdropBlurEnabled);
        c.iosLiquidGlass = intent.getBooleanExtra("iosLiquidGlass", c.iosLiquidGlass);
        c.betaCompactMode = intent.getBooleanExtra("betaCompactMode", c.betaCompactMode);
        c.dynamicGlowColor = intent.getBooleanExtra("dynamicGlowColor", c.dynamicGlowColor);
        c.manualAccentColor = intent.getIntExtra("manualAccentColor", c.manualAccentColor);
        c.animStyle = intent.getIntExtra("animStyle", c.animStyle);
        c.animSpeedPct = intent.getIntExtra("animSpeedPct", c.animSpeedPct);
        c.autoAdaptPosition = intent.getBooleanExtra("autoAdaptPosition", c.autoAdaptPosition);
        c.hideLauncherIcon = intent.getBooleanExtra("hideLauncherIcon", c.hideLauncherIcon);
        c.hideOnBouncer = intent.getBooleanExtra("hideOnBouncer", c.hideOnBouncer);
        c.hideOnAod = intent.getBooleanExtra("hideOnAod", c.hideOnAod);
        c.alwaysShowCapsule = intent.getBooleanExtra("alwaysShowCapsule", c.alwaysShowCapsule);
        c.showMediaWhenPaused = intent.getBooleanExtra("showMediaWhenPaused", c.showMediaWhenPaused);
        c.showEqualizer = intent.getBooleanExtra("showEqualizer", c.showEqualizer);
        c.showQuickMediaButtons = intent.getBooleanExtra("showQuickMediaButtons", c.showQuickMediaButtons);
        c.tapMediaOpensApp = intent.getBooleanExtra("tapMediaOpensApp", c.tapMediaOpensApp);
        c.tapNotifOpensApp = intent.getBooleanExtra("tapNotifOpensApp", c.tapNotifOpensApp);
        c.bottomMarginDp = intent.getIntExtra("bottomMarginDp", c.bottomMarginDp);
        c.sheetBottomMarginDp = intent.getIntExtra("sheetBottomMarginDp", c.sheetBottomMarginDp);
        c.cornerRadiusDp = intent.getIntExtra("cornerRadiusDp", c.cornerRadiusDp);
        c.borderWidthDp = intent.getIntExtra("borderWidthDp", c.borderWidthDp);
        c.pillFontSizeSp = intent.getIntExtra("pillFontSizeSp", c.pillFontSizeSp);
        c.capsuleWidthMode = intent.getIntExtra("capsuleWidthMode", c.capsuleWidthMode);
        c.capsuleAlpha = intent.getIntExtra("capsuleAlpha", c.capsuleAlpha);
        c.blurRadiusPx = intent.getIntExtra("blurRadiusPx", c.blurRadiusPx);
        c.backdropBlurRadiusPx = intent.getIntExtra("backdropBlurRadiusPx", c.backdropBlurRadiusPx);
        c.scrimAlpha = intent.getIntExtra("scrimAlpha", c.scrimAlpha);
        c.style = intent.getIntExtra("style", c.style);
        return c;
    }

    public String serializePreset() {
        StringBuilder sb = new StringBuilder();
        sb.append("style=").append(style)
          .append(";iosLiquidGlass=").append(iosLiquidGlass)
          .append(";betaCompactMode=").append(betaCompactMode)
          .append(";systemBlurEnabled=").append(systemBlurEnabled)
          .append(";backdropBlurEnabled=").append(backdropBlurEnabled)
          .append(";dynamicGlowColor=").append(dynamicGlowColor)
          .append(";manualAccentColor=").append(manualAccentColor)
          .append(";animStyle=").append(animStyle)
          .append(";animSpeedPct=").append(animSpeedPct)
          .append(";bottomMarginDp=").append(bottomMarginDp)
          .append(";sheetBottomMarginDp=").append(sheetBottomMarginDp)
          .append(";cornerRadiusDp=").append(cornerRadiusDp)
          .append(";borderWidthDp=").append(borderWidthDp)
          .append(";pillFontSizeSp=").append(pillFontSizeSp)
          .append(";capsuleWidthMode=").append(capsuleWidthMode)
          .append(";capsuleAlpha=").append(capsuleAlpha)
          .append(";blurRadiusPx=").append(blurRadiusPx)
          .append(";backdropBlurRadiusPx=").append(backdropBlurRadiusPx)
          .append(";scrimAlpha=").append(scrimAlpha)
          .append(";showQuickMediaButtons=").append(showQuickMediaButtons)
          .append(";showEqualizer=").append(showEqualizer)
          .append(";notifSourceMode=").append(notifSourceMode)
          .append(";clearNotifsAfterUnlock=").append(clearNotifsAfterUnlock);
        return sb.toString();
    }

    public void applySerializedPreset(String data) {
        if (data == null || data.isEmpty()) return;
        Map<String, String> map = new HashMap<>();
        for (String pair : data.split(";")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && eq < pair.length() - 1) {
                map.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        style = parseInt(map.get("style"), style);
        iosLiquidGlass = parseBool(map.get("iosLiquidGlass"), iosLiquidGlass);
        betaCompactMode = parseBool(map.get("betaCompactMode"), betaCompactMode);
        systemBlurEnabled = parseBool(map.get("systemBlurEnabled"), systemBlurEnabled);
        backdropBlurEnabled = parseBool(map.get("backdropBlurEnabled"), backdropBlurEnabled);
        dynamicGlowColor = parseBool(map.get("dynamicGlowColor"), dynamicGlowColor);
        manualAccentColor = parseInt(map.get("manualAccentColor"), manualAccentColor);
        animStyle = parseInt(map.get("animStyle"), animStyle);
        animSpeedPct = parseInt(map.get("animSpeedPct"), animSpeedPct);
        // Preserve device-specific auto-adapted position when applying built-in visual presets
        if (!autoAdaptPosition) {
            bottomMarginDp = parseInt(map.get("bottomMarginDp"), bottomMarginDp);
            sheetBottomMarginDp = parseInt(map.get("sheetBottomMarginDp"), sheetBottomMarginDp);
        }
        cornerRadiusDp = parseInt(map.get("cornerRadiusDp"), cornerRadiusDp);
        borderWidthDp = parseInt(map.get("borderWidthDp"), borderWidthDp);
        pillFontSizeSp = parseInt(map.get("pillFontSizeSp"), pillFontSizeSp);
        capsuleWidthMode = parseInt(map.get("capsuleWidthMode"), capsuleWidthMode);
        capsuleAlpha = parseInt(map.get("capsuleAlpha"), capsuleAlpha);
        blurRadiusPx = parseInt(map.get("blurRadiusPx"), blurRadiusPx);
        backdropBlurRadiusPx = parseInt(map.get("backdropBlurRadiusPx"), backdropBlurRadiusPx);
        scrimAlpha = parseInt(map.get("scrimAlpha"), scrimAlpha);
        showQuickMediaButtons = parseBool(map.get("showQuickMediaButtons"), showQuickMediaButtons);
        showEqualizer = parseBool(map.get("showEqualizer"), showEqualizer);
        notifSourceMode = parseInt(map.get("notifSourceMode"), notifSourceMode);
        clearNotifsAfterUnlock = parseBool(map.get("clearNotifsAfterUnlock"), clearNotifsAfterUnlock);
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (Throwable ignored) { return def; }
    }

    private static boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        return "true".equalsIgnoreCase(s);
    }
}
