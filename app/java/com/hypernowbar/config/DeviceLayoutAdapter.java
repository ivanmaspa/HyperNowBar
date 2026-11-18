package com.hypernowbar.config;

import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Universal device & sensor detector for all Android 12-16 smartphones & ROMs
 * (HyperOS/MIUI, Samsung One UI, Pixel/AOSP/LineageOS, OnePlus/ColorOS/OxygenOS, Nothing OS, Vivo, etc.).
 *
 * Automatically determines whether the phone has an under-display fingerprint sensor (UDFPS/FOD)
 * or NOT (side/rear/no fingerprint), and computes safe, ergonomic bottom margins & capsule widths
 * for any screen resolution or aspect ratio.
 */
public final class DeviceLayoutAdapter {

    public static final class DeviceProfile {
        public final boolean hasUnderDisplayFingerprint;
        public final int recommendedCapsuleBottomDp;
        public final int recommendedSheetBottomDp;
        public final int screenWidthDp;
        public final int screenHeightDp;
        public final String summary;

        public DeviceProfile(
                boolean hasUdfps,
                int capsuleBottomDp,
                int sheetBottomDp,
                int screenWidthDp,
                int screenHeightDp,
                String summary) {
            this.hasUnderDisplayFingerprint = hasUdfps;
            this.recommendedCapsuleBottomDp = capsuleBottomDp;
            this.recommendedSheetBottomDp = sheetBottomDp;
            this.screenWidthDp = screenWidthDp;
            this.screenHeightDp = screenHeightDp;
            this.summary = summary;
        }
    }

    private static volatile int runtimeUdfpsTopFromBottomDp = -1;
    private static volatile Boolean runtimeUdfpsViewFound = null;

    private DeviceLayoutAdapter() {}

    /**
     * Scans the live lockscreen View hierarchy (inside SystemUI) for real UDFPS / FOD icon views
     * to measure the exact clearance height above the under-display fingerprint scanner.
     */
    public static void inspectLockscreenHierarchy(ViewGroup root) {
        if (root == null) return;
        int rootH = root.getHeight();
        if (rootH <= 0) return;
        float density = root.getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 3f;

        View udfpsView = findUdfpsView(root);
        if (udfpsView != null && udfpsView.getHeight() > 0) {
            int[] rootLoc = new int[2];
            int[] vLoc = new int[2];
            root.getLocationOnScreen(rootLoc);
            udfpsView.getLocationOnScreen(vLoc);
            int topFromBottomPx = (rootLoc[1] + rootH) - vLoc[1];
            int topFromBottomDp = Math.round(topFromBottomPx / density);
            // Sanity bounds for real under-display sensors (typically 70..220 dp from bottom)
            if (topFromBottomDp >= 65 && topFromBottomDp <= 230) {
                runtimeUdfpsViewFound = Boolean.TRUE;
                runtimeUdfpsTopFromBottomDp = topFromBottomDp;
            }
        }
    }

    private static View findUdfpsView(ViewGroup group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            String cls = child.getClass().getName().toLowerCase();
            if (cls.startsWith("com.hypernowbar.")) continue;

            String res = safeResName(child);
            if (cls.contains("udfps")
                    || cls.contains("gxzw")
                    || cls.contains("fod")
                    || (cls.contains("deviceentryicon") && child.getWidth() > 0)
                    || res.contains("udfps")
                    || res.contains("gxzw")
                    || res.contains("fod_icon")) {
                if (child.getVisibility() == View.VISIBLE && child.getHeight() > 20) {
                    return child;
                }
            }
            if (child instanceof ViewGroup) {
                View found = findUdfpsView((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String safeResName(View v) {
        try {
            int id = v.getId();
            if (id == View.NO_ID) return "";
            return v.getResources().getResourceEntryName(id).toLowerCase();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Detects the current device's UDFPS status, screen metrics, and recommended margins.
     */
    public static DeviceProfile detect(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density > 0f ? dm.density : 3f;
        int wDp = Math.round(dm.widthPixels / density);
        int hDp = Math.round(dm.heightPixels / density);

        int sensorTopFromBottomDp = runtimeUdfpsTopFromBottomDp;
        Boolean udfps = runtimeUdfpsViewFound;

        // 1. Official Android 12-16 FingerprintManager sensor properties (gives exact Y + radius on AOSP/Pixel/OEMs)
        if (udfps == null) {
            int[] apiResult = queryFingerprintSensorApi(context, dm.heightPixels, density);
            if (apiResult != null) {
                udfps = (apiResult[0] == 1);
                if (apiResult[1] > 0) {
                    sensorTopFromBottomDp = apiResult[1];
                }
            }
        }

        // 2. Vendor system properties fallback (Xiaomi HyperOS/MIUI, OPPO/OnePlus/Realme, Samsung, Vivo, Nothing)
        if (udfps == null) {
            udfps = detectUdfpsFromSystemProps(context);
        }

        boolean hasUdfps = Boolean.TRUE.equals(udfps);
        int capsuleBottom;
        int sheetBottom;
        String summary;

        if (hasUdfps) {
            if (sensorTopFromBottomDp > 0) {
                capsuleBottom = clamp(sensorTopFromBottomDp + 22, 134, 210);
                sheetBottom = clamp(capsuleBottom + 10, 144, 224);
            } else {
                // Proportional fallback for taller/shorter screens with optical/ultrasonic scanner
                capsuleBottom = hDp < 740 ? 138 : (hDp > 900 ? 162 : 152);
                sheetBottom = capsuleBottom + 10;
            }
            summary = "Сканер под экраном (UDFPS) • авто " + capsuleBottom + " dp";
        } else {
            // Phones WITHOUT under-display fingerprint (side/rear sensor or no fingerprint):
            // sits cleanly low near the bottom edge, right above gesture bar & lockscreen shortcuts!
            capsuleBottom = hDp < 700 ? 34 : 44;
            sheetBottom = capsuleBottom + 12;
            summary = "Без подэкранного сканера • авто внизу " + capsuleBottom + " dp";
        }

        return new DeviceProfile(hasUdfps, capsuleBottom, sheetBottom, wDp, hDp, summary);
    }

    /**
     * Returns int[]{hasUdfps (0/1), topFromBottomDp (or -1)} or null if API unavailable/restricted.
     */
    private static int[] queryFingerprintSensorApi(Context context, int screenHeightPx, float density) {
        try {
            FingerprintManager fm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
            if (fm == null) return new int[] {0, -1};

            boolean hasHw = false;
            try {
                hasHw = fm.isHardwareDetected();
            } catch (Throwable ignored) {}
            if (!hasHw) {
                return new int[] {0, -1};
            }

            Method getProps = FingerprintManager.class.getMethod("getSensorPropertiesInternal");
            Object res = getProps.invoke(fm);
            if (res instanceof List<?>) {
                List<?> propsList = (List<?>) res;
                if (propsList.isEmpty()) return null;
                boolean foundUdfps = false;
                int topFromBottomDp = -1;

                for (Object prop : propsList) {
                    if (prop == null) continue;
                    boolean isUdfps = false;
                    try {
                        Method mUdfps = prop.getClass().getMethod("isAnyUdfpsType");
                        Object b = mUdfps.invoke(prop);
                        if (b instanceof Boolean && (Boolean) b) {
                            isUdfps = true;
                        }
                    } catch (Throwable ignored) {}

                    if (!isUdfps) {
                        try {
                            int sensorType = prop.getClass().getField("sensorType").getInt(prop);
                            // Android SensorProps: 2 = UDFPS_ULTRASONIC, 3 = UDFPS_OPTICAL
                            if (sensorType == 2 || sensorType == 3) {
                                isUdfps = true;
                            }
                        } catch (Throwable ignored) {}
                    }

                    if (isUdfps) {
                        foundUdfps = true;
                        try {
                            Method getLoc = prop.getClass().getMethod("getLocation");
                            Object loc = getLoc.invoke(prop);
                            if (loc != null) {
                                int locY = loc.getClass().getField("sensorLocationY").getInt(loc);
                                int rad = loc.getClass().getField("sensorRadius").getInt(loc);
                                if (locY > 0 && screenHeightPx > locY) {
                                    int topPx = Math.max(0, locY - Math.max(rad, Math.round(36 * density)));
                                    int fromBottom = Math.round((screenHeightPx - topPx) / density);
                                    if (fromBottom >= 65 && fromBottom <= 230) {
                                        topFromBottomDp = fromBottom;
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                        break;
                    }
                }
                return new int[] {foundUdfps ? 1 : 0, topFromBottomDp};
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Boolean detectUdfpsFromSystemProps(Context context) {
        // Explicit positive indicators across major OEMs
        String[] positiveKeys = new String[] {
            "ro.hardware.fp.fod",
            "persist.vendor.sys.fp.fod.location.X_Y",
            "ro.vendor.fingerprint.optical",
            "persist.vendor.fingerprint.optical",
            "ro.boot.fp.fod",
            "ro.oplus.fingerprint.optical",
            "ro.fingerprint.udfps"
        };
        for (String key : positiveKeys) {
            String val = getSysProp(key);
            if (!val.isEmpty() && !"0".equals(val) && !"false".equalsIgnoreCase(val) && !"none".equalsIgnoreCase(val)) {
                return Boolean.TRUE;
            }
        }

        // Check Xiaomi/HyperOS side fingerprint property explicitly
        String sideFp = getSysProp("ro.hardware.fp.sideCap");
        if ("true".equalsIgnoreCase(sideFp) || "1".equals(sideFp)) {
            return Boolean.FALSE;
        }

        // Check Samsung / AOSP framework resource boolean or package features
        try {
            int resId = context.getResources().getIdentifier("config_is_udfps_supported", "bool", "android");
            if (resId != 0) {
                return context.getResources().getBoolean(resId);
            }
        } catch (Throwable ignored) {}

        // If device has NO fingerprint hardware at all -> definitely no UDFPS
        try {
            if (!context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")) {
                return Boolean.FALSE;
            }
        } catch (Throwable ignored) {}

        // Fallback: check Samsung UDFPS prop or known side-scanner indicators
        String samsungFp = getSysProp("ro.hardware.biometrics.fingerprint");
        if (!samsungFp.isEmpty()) {
            String low = samsungFp.toLowerCase();
            if (low.contains("ultrasonic") || low.contains("optical") || low.contains("indisplay")) {
                return Boolean.TRUE;
            }
            if (low.contains("side") || low.contains("rear")) {
                return Boolean.FALSE;
            }
        }

        // Default conservative fallback: if fingerprint hardware exists on modern Android 13+ flagship
        // without explicit side-sensor marker, check ro.product.model/manufacturer hints; otherwise false
        String fpType = getSysProp("ro.hardware.fingerprint").toLowerCase();
        if (fpType.contains("fod") || fpType.contains("udfps") || fpType.contains("optical") || fpType.contains("qbt")) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private static String getSysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, "");
            return v != null ? v.toString().trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static int resolveEffectiveCapsuleBottomDp(Context context, NowBarConfig cfg) {
        if (cfg != null && !cfg.autoAdaptPosition) {
            return cfg.bottomMarginDp;
        }
        return detect(context).recommendedCapsuleBottomDp;
    }

    public static int resolveEffectiveSheetBottomDp(Context context, NowBarConfig cfg) {
        if (cfg != null && !cfg.autoAdaptPosition) {
            return cfg.sheetBottomMarginDp;
        }
        return detect(context).recommendedSheetBottomDp;
    }

    public static String getDeviceBanner() {
        String brand = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
        String model = Build.MODEL != null ? Build.MODEL : "";
        if (!brand.isEmpty() && !model.toLowerCase().startsWith(brand.toLowerCase())) {
            return brand + " " + model + " • Android " + Build.VERSION.RELEASE;
        }
        return model + " • Android " + Build.VERSION.RELEASE;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
