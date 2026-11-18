package com.hypernowbar.hook;

import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Cleanly hides HyperOS stock lockscreen notification cards and media card
 * ONLY while strictly on Keyguard, AND also forwards keyguard_bouncer_container
 * visibility changes to KeyguardStateMonitor for instant 0ms PIN/Password hiding.
 */
public final class StockNotifHider {
    private static volatile boolean hideActive = false;
    private static volatile boolean hasTrackedViews = false;
    private static boolean hooksInstalled = false;
    private static final Set<View> trackedViews =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public static void installHooks(ClassLoader classLoader) {
        if (hooksInstalled) return;
        hooksInstalled = true;

        try {
            XposedHelpers.findAndHookMethod(
                    View.class,
                    "setVisibility",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object target = param.thisObject;
                            if (!(target instanceof View)) return;
                            View v = (View) target;

                            if (hideActive && hasTrackedViews && trackedViews.contains(v)) {
                                int req = (Integer) param.args[0];
                                if (req == View.VISIBLE) {
                                    param.args[0] = View.INVISIBLE;
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object target = param.thisObject;
                            if (target instanceof View && KeyguardStateMonitor.isBouncerContainer((View) target)) {
                                int vis = (Integer) param.args[0];
                                KeyguardStateMonitor.onBouncerContainerVisibilityChanged((View) target, vis);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    View.class,
                    "setAlpha",
                    float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!hideActive || !hasTrackedViews) return;
                            Object target = param.thisObject;
                            if (target instanceof View && trackedViews.contains(target)) {
                                float req = (Float) param.args[0];
                                if (req > 0.01f) {
                                    param.args[0] = 0f;
                                }
                            }
                        }
                    });
            XposedBridge.log("[HyperNowBar] StockNotifHider hooked View.setVisibility/setAlpha");
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] StockNotifHider View hook error: " + t.getMessage());
        }

        installClockStretchHooks(classLoader);
    }

    /**
     * Tells HyperOS 3's stretchy Magazine Lockscreen Clock (KeyguardClockContainer & animation classes)
     * that there are 0 stock lockscreen notifications whenever StockNotifHider is hiding them,
     * allowing the clock to stretch to its full vertical height!
     */
    private static void installClockStretchHooks(ClassLoader classLoader) {
        String[] clockClasses = new String[] {
            "com.android.keyguard.clock.KeyguardClockContainer",
            "com.android.keyguard.clock.KeyguardClockInjector",
            "com.android.keyguard.clock.animation.ClockBaseAnimation",
            "com.android.keyguard.clock.animation.aod.AodClockBaseAnimation",
            "com.android.keyguard.clock.MiuiKeyguardClockPositionAlgorithm",
            "com.android.keyguard.clock.MiuiKeyguardBaseClock"
        };

        int hookedCount = 0;
        for (String clsName : clockClasses) {
            Class<?> cls = XposedHelpers.findClassIfExists(clsName, classLoader);
            if (cls == null) continue;
            try {
                java.lang.reflect.Method[] methods = cls.getDeclaredMethods();
                for (java.lang.reflect.Method m : methods) {
                    String nameLower = m.getName().toLowerCase();
                    if (!nameLower.contains("notif")) continue;
                    Class<?>[] params = m.getParameterTypes();
                    Class<?> ret = m.getReturnType();

                    if (params.length == 0 && (ret == boolean.class || ret == Boolean.class)) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (hideActive) {
                                    param.setResult(Boolean.FALSE);
                                }
                            }
                        });
                        hookedCount++;
                        XposedBridge.log("[HyperNowBar] Clock stretch hook (ret=false): " + cls.getSimpleName() + "." + m.getName());
                    } else if (params.length == 0 && (ret == int.class || ret == Integer.class)) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (hideActive) {
                                    param.setResult(Integer.valueOf(0));
                                }
                            }
                        });
                        hookedCount++;
                        XposedBridge.log("[HyperNowBar] Clock stretch hook (ret=0): " + cls.getSimpleName() + "." + m.getName());
                    } else if (params.length >= 1) {
                        boolean hasBoolOrInt = false;
                        for (Class<?> pt : params) {
                            if (pt == boolean.class || pt == Boolean.class || pt == int.class || pt == Integer.class) {
                                hasBoolOrInt = true;
                                break;
                            }
                        }
                        if (hasBoolOrInt) {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    if (!hideActive || param.args == null) return;
                                    for (int i = 0; i < param.args.length; i++) {
                                        if (param.args[i] instanceof Boolean) {
                                            param.args[i] = Boolean.FALSE;
                                        } else if (param.args[i] instanceof Integer && nameLower.contains("count")) {
                                            param.args[i] = Integer.valueOf(0);
                                        }
                                    }
                                }
                            });
                            hookedCount++;
                            XposedBridge.log("[HyperNowBar] Clock stretch hook (arg): " + cls.getSimpleName() + "." + m.getName());
                        }
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("[HyperNowBar] Clock stretch inspect error on " + clsName + ": " + t.getMessage());
            }
        }

        // Also hook NotificationStackScrollLayout visible-count queries while strictly on Keyguard
        Class<?> nsslCls = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", classLoader);
        if (nsslCls != null) {
            String[] zeroCountMethods = {"getVisibleNotificationCount", "getNotGoneChildCount", "getActiveNotificationCount"};
            for (String mName : zeroCountMethods) {
                try {
                    XposedBridge.hookAllMethods(nsslCls, mName, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (hideActive) {
                                param.setResult(Integer.valueOf(0));
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        }

        XposedBridge.log("[HyperNowBar] Installed " + hookedCount + " HyperOS stretchy clock hooks");
    }

    public static int scanHierarchy(ViewGroup root) {
        if (root == null) return trackedViews.size();
        KeyguardStateMonitor.bindBouncerContainer(root);
        scanRecursive(root);
        hasTrackedViews = !trackedViews.isEmpty();
        return trackedViews.size();
    }

    private static void scanRecursive(ViewGroup group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            if (child.getClass().getName().startsWith("com.hypernowbar.")) continue;

            String clsName = child.getClass().getName().toLowerCase();
            String resName = safeResName(child);
            String resLower = resName != null ? resName.toLowerCase() : "";

            boolean isStockNotifContainer =
                    clsName.contains("notificationstackscrolllayout")
                    || clsName.contains("keyguardnotification")
                    || resLower.equals("notification_stack_scroller")
                    || resLower.contains("keyguard_notification_container")
                    || resLower.contains("keyguard_media_container")
                    || resLower.contains("miui_keyguard_media");

            if (isStockNotifContainer) {
                trackView(child);
                continue;
            }

            if (child instanceof ViewGroup) {
                scanRecursive((ViewGroup) child);
            }
        }
    }

    private static String safeResName(View v) {
        try {
            int id = v.getId();
            if (id == View.NO_ID) return null;
            return v.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void trackView(View v) {
        if (v == null) return;
        boolean added = trackedViews.add(v);
        hasTrackedViews = true;
        if (added) {
            XposedBridge.log("[HyperNowBar] Tracking stock lockscreen container: "
                    + v.getClass().getName() + " (id=" + safeResName(v) + ")");
        }
        applyStateToView(v, hideActive);
    }

    public static void setHiddenOnKeyguard(boolean hide) {
        if (hideActive == hide && !hide) return;
        hideActive = hide;
        View[] snapshot;
        synchronized (trackedViews) {
            snapshot = trackedViews.toArray(new View[0]);
        }
        for (View v : snapshot) {
            if (v != null) {
                applyStateToView(v, hide);
            }
        }
    }

    private static void applyStateToView(View v, boolean hide) {
        v.post(() -> {
            try {
                if (hide) {
                    v.setAlpha(0f);
                    if (v.getVisibility() == View.VISIBLE) {
                        v.setVisibility(View.INVISIBLE);
                    }
                } else {
                    if (v.getVisibility() == View.INVISIBLE) {
                        v.setVisibility(View.VISIBLE);
                    }
                    v.setAlpha(1f);
                }
            } catch (Throwable ignored) {}
        });
    }
}
