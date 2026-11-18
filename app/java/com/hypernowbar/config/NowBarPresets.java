package com.hypernowbar.config;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Curated built-in presets + persistent user-created custom presets for HyperNowBar 1.G.1.
 */
public final class NowBarPresets {
    private static final String USER_PRESETS_INDEX_KEY = "user_presets_index";
    private static final String USER_PRESET_PREFIX = "user_preset_data_";

    public static final class PresetItem {
        public final String name;
        public final boolean isCustom;
        public final String serializedData;

        public PresetItem(String name, boolean isCustom, String serializedData) {
            this.name = name;
            this.isCustom = isCustom;
            this.serializedData = serializedData;
        }
    }

    public static List<PresetItem> getBuiltInPresets() {
        List<PresetItem> list = new ArrayList<>();

        // 1. One UI 7 Classic
        NowBarConfig p1 = new NowBarConfig();
        p1.style = NowBarConfig.STYLE_ONEUI_GLOW;
        p1.iosLiquidGlass = false;
        p1.betaCompactMode = false;
        p1.systemBlurEnabled = true;
        p1.backdropBlurEnabled = true;
        p1.blurRadiusPx = 96;
        p1.backdropBlurRadiusPx = 68;
        p1.capsuleAlpha = 72;
        p1.scrimAlpha = 48;
        p1.animStyle = NowBarConfig.ANIM_SPRING_IOS;
        list.add(new PresetItem("One UI 7", false, p1.serializePreset()));

        // 2. iOS Liquid Glass (opt-in preset!)
        NowBarConfig p2 = new NowBarConfig();
        p2.style = NowBarConfig.STYLE_COLOROS_GLASS;
        p2.iosLiquidGlass = true;
        p2.betaCompactMode = false;
        p2.systemBlurEnabled = true;
        p2.backdropBlurEnabled = true;
        p2.blurRadiusPx = 124;
        p2.backdropBlurRadiusPx = 96;
        p2.capsuleAlpha = 48;
        p2.scrimAlpha = 28;
        p2.cornerRadiusDp = 26;
        p2.borderWidthDp = 2;
        p2.animStyle = NowBarConfig.ANIM_SPRING_IOS;
        p2.animSpeedPct = 110;
        list.add(new PresetItem("iOS Стекло", false, p2.serializePreset()));

        // 3. Monochrome City (B&W Chrome)
        NowBarConfig p3 = new NowBarConfig();
        p3.style = NowBarConfig.STYLE_MONOCHROME_BW;
        p3.iosLiquidGlass = false;
        p3.betaCompactMode = false;
        p3.dynamicGlowColor = false;
        p3.manualAccentColor = 0xFFE6E9F0;
        p3.systemBlurEnabled = true;
        p3.backdropBlurEnabled = true;
        p3.blurRadiusPx = 92;
        p3.backdropBlurRadiusPx = 74;
        p3.capsuleAlpha = 70;
        p3.scrimAlpha = 52;
        p3.animStyle = NowBarConfig.ANIM_FLUID_HYPER;
        list.add(new PresetItem("Ч/Б Город", false, p3.serializePreset()));

        // 4. Compact Adaptive (Beta)
        NowBarConfig p4 = new NowBarConfig();
        p4.style = NowBarConfig.STYLE_COLOROS_GLASS;
        p4.iosLiquidGlass = false;
        p4.betaCompactMode = true;
        p4.systemBlurEnabled = true;
        p4.backdropBlurEnabled = false;
        p4.blurRadiusPx = 88;
        p4.capsuleAlpha = 68;
        p4.scrimAlpha = 0;
        p4.capsuleWidthMode = NowBarConfig.WIDTH_COMPACT;
        p4.animStyle = NowBarConfig.ANIM_SPRING_IOS;
        list.add(new PresetItem("Компакт (Бета)", false, p4.serializePreset()));

        // 5. ColorOS Fluid
        NowBarConfig p5 = new NowBarConfig();
        p5.style = NowBarConfig.STYLE_COLOROS_GLASS;
        p5.iosLiquidGlass = false;
        p5.betaCompactMode = false;
        p5.systemBlurEnabled = true;
        p5.backdropBlurEnabled = true;
        p5.blurRadiusPx = 108;
        p5.backdropBlurRadiusPx = 80;
        p5.capsuleAlpha = 66;
        p5.scrimAlpha = 44;
        p5.cornerRadiusDp = 24;
        p5.animStyle = NowBarConfig.ANIM_FLUID_HYPER;
        list.add(new PresetItem("ColorOS", false, p5.serializePreset()));

        // 6. Stealth OLED Matte
        NowBarConfig p6 = new NowBarConfig();
        p6.style = NowBarConfig.STYLE_MINIMAL_MATTE;
        p6.iosLiquidGlass = false;
        p6.betaCompactMode = false;
        p6.systemBlurEnabled = false;
        p6.backdropBlurEnabled = false;
        p6.capsuleAlpha = 94;
        p6.scrimAlpha = 70;
        p6.borderWidthDp = 1;
        p6.animStyle = NowBarConfig.ANIM_INSTANT;
        p6.animSpeedPct = 70;
        list.add(new PresetItem("OLED Мат", false, p6.serializePreset()));

        // 7. Neon Frost
        NowBarConfig p7 = new NowBarConfig();
        p7.style = NowBarConfig.STYLE_ONEUI_GLOW;
        p7.iosLiquidGlass = false;
        p7.betaCompactMode = false;
        p7.dynamicGlowColor = true;
        p7.systemBlurEnabled = true;
        p7.backdropBlurEnabled = true;
        p7.blurRadiusPx = 130;
        p7.backdropBlurRadiusPx = 105;
        p7.capsuleAlpha = 58;
        p7.scrimAlpha = 40;
        p7.borderWidthDp = 3;
        p7.animStyle = NowBarConfig.ANIM_SLIDE_UP;
        list.add(new PresetItem("Неон Фрост", false, p7.serializePreset()));

        return list;
    }

    public static List<PresetItem> loadUserPresets(SharedPreferences prefs) {
        List<PresetItem> list = new ArrayList<>();
        if (prefs == null) return list;
        String index = prefs.getString(USER_PRESETS_INDEX_KEY, "");
        if (index == null || index.trim().isEmpty()) return list;
        for (String name : index.split("\\|")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) continue;
            String data = prefs.getString(USER_PRESET_PREFIX + trimmed, null);
            if (data != null && !data.isEmpty()) {
                list.add(new PresetItem(trimmed, true, data));
            }
        }
        return list;
    }

    public static void saveUserPreset(SharedPreferences prefs, String rawName, NowBarConfig cfg) {
        if (prefs == null || rawName == null || cfg == null) return;
        String name = rawName.replace("|", "").replace(";", "").trim();
        if (name.isEmpty()) return;

        Map<String, String> existing = new LinkedHashMap<>();
        for (PresetItem p : loadUserPresets(prefs)) {
            existing.put(p.name, p.serializedData);
        }
        existing.put(name, cfg.serializePreset());
        writeUserPresets(prefs, existing);
    }

    public static void deleteUserPreset(SharedPreferences prefs, String name) {
        if (prefs == null || name == null) return;
        Map<String, String> existing = new LinkedHashMap<>();
        for (PresetItem p : loadUserPresets(prefs)) {
            if (!name.equals(p.name)) {
                existing.put(p.name, p.serializedData);
            }
        }
        prefs.edit().remove(USER_PRESET_PREFIX + name).apply();
        writeUserPresets(prefs, existing);
    }

    private static void writeUserPresets(SharedPreferences prefs, Map<String, String> map) {
        StringBuilder idx = new StringBuilder();
        SharedPreferences.Editor ed = prefs.edit();
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) idx.append("|");
            first = false;
            idx.append(e.getKey());
            ed.putString(USER_PRESET_PREFIX + e.getKey(), e.getValue());
        }
        ed.putString(USER_PRESETS_INDEX_KEY, idx.toString());
        ed.apply();
    }
}
