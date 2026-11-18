package com.hypernowbar.hook;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;

/**
 * Tracks Lockscreen (KEYGUARD) vs Unlocked (SHADE) vs Pulled-Down Shade (SHADE_LOCKED)
 * vs PIN/Pattern Bouncer vs Control Center vs AOD/Doze.
 *
 * Includes an automatic 250ms bouncer watchdog (ONLY active while Bouncer is open) so that
 * cancelling PIN/Pattern entry ALWAYS restores Now Bar cleanly after HyperOS fade-out!
 */
public final class KeyguardStateMonitor {
    public static final int STATE_SHADE = 0;
    public static final int STATE_KEYGUARD = 1;
    public static final int STATE_SHADE_LOCKED = 2;

    public interface Listener {
        void onLockscreenVisibilityChanged(boolean onKeyguard, boolean bouncerShowing, boolean isDozing, boolean screenInteractive);
        void onDeviceUnlocked();
    }

    private static volatile KeyguardStateMonitor activeInstance;
    private static volatile int hookedStatusBarState = STATE_SHADE;
    private static volatile boolean hookedDozing = false;
    private static volatile boolean hookedControlCenterExpanded = false;
    private static volatile boolean hookedBouncerShowing = false;
    private static volatile boolean stateControllerHooked = false;
    private static volatile Object keyguardViewManagerRef = null;
    private static volatile WeakReference<View> bouncerContainerRef = new WeakReference<>(null);
    private static boolean hooksInstalled = false;

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final KeyguardManager keyguardManager;
    private final PowerManager powerManager;
    private boolean screenOn = true;
    private boolean watchdogRunning = false;

    private final Runnable bouncerWatchdog = new Runnable() {
        @Override
        public void run() {
            boolean liveBouncer = queryLiveBouncerState();
            if (liveBouncer != hookedBouncerShowing) {
                hookedBouncerShowing = liveBouncer;
                dispatchUpdate();
            }
            if (liveBouncer && screenOn && isCurrentlyOnKeyguard()) {
                mainHandler.postDelayed(this, 220);
            } else {
                watchdogRunning = false;
                if (!liveBouncer) {
                    hookedBouncerShowing = false;
                    dispatchUpdate();
                }
            }
        }
    };

    public KeyguardStateMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.keyguardManager = (KeyguardManager) this.context.getSystemService(Context.KEYGUARD_SERVICE);
        this.powerManager = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
        this.screenOn = powerManager == null || powerManager.isInteractive();
        activeInstance = this;
        registerScreenReceiver();
        dispatchUpdate();
    }

    public static Object getKeyguardViewManager() {
        return keyguardViewManagerRef;
    }

    public static void installHooks(ClassLoader classLoader) {
        if (hooksInstalled) return;
        hooksInstalled = true;

        // 1. StatusBarStateControllerImpl (KEYGUARD vs SHADE vs SHADE_LOCKED vs DOZING)
        Class<?> sbscCls = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.StatusBarStateControllerImpl", classLoader);
        if (sbscCls != null) {
            try {
                XposedBridge.hookAllMethods(sbscCls, "setState", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof Integer) {
                            stateControllerHooked = true;
                            hookedStatusBarState = (Integer) param.args[0];
                            if (hookedStatusBarState == STATE_KEYGUARD) {
                                hookedControlCenterExpanded = false;
                            }
                            if (hookedStatusBarState == STATE_SHADE) {
                                hookedBouncerShowing = false;
                            }
                            notifyActive(false);
                        }
                    }
                });
                XposedBridge.hookAllMethods(sbscCls, "setIsDozing", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof Boolean) {
                            hookedDozing = (Boolean) param.args[0];
                            notifyActive(false);
                        }
                    }
                });
                XposedBridge.hookAllMethods(sbscCls, "setDozeAmount", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof Float) {
                            boolean nowDozing = ((Float) param.args[0]) > 0.35f;
                            if (nowDozing != hookedDozing) {
                                hookedDozing = nowDozing;
                                notifyActive(false);
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[HyperNowBar] StatusBarStateController hook error: " + t.getMessage());
            }
        }

        // 2. StatusBarKeyguardViewManager — HyperOS 3 PIN/Pattern Bouncer controller + dismissWithAction host!
        Class<?> kvmCls = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager", classLoader);
        if (kvmCls != null) {
            try {
                XposedBridge.hookAllConstructors(kvmCls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        keyguardViewManagerRef = param.thisObject;
                    }
                });

                String[] showMethods = {"showPrimaryBouncer", "showBouncer", "showPrimaryBouncerOrKeyguard"};
                for (String m : showMethods) {
                    XposedBridge.hookAllMethods(kvmCls, m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            keyguardViewManagerRef = param.thisObject;
                            hookedBouncerShowing = true;
                            notifyActive(true);
                        }
                    });
                }

                String[] hideOrChangeMethods = {
                    "hideBouncer", "hidePrimaryBouncer", "reset", "hide",
                    "onBouncerVisibilityChanged", "onBackPressed", "onKeyguardVisibilityChanged",
                    "cancelPendingWakeupAction", "onPanelExpansionChanged"
                };
                for (String m : hideOrChangeMethods) {
                    XposedBridge.hookAllMethods(kvmCls, m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            keyguardViewManagerRef = param.thisObject;
                            boolean live = queryLiveBouncerState();
                            if (live != hookedBouncerShowing) {
                                hookedBouncerShowing = live;
                                notifyActive(live);
                            }
                        }
                    });
                }
                XposedBridge.log("[HyperNowBar] Hooked StatusBarKeyguardViewManager for Bouncer + Dismiss");
            } catch (Throwable t) {
                XposedBridge.log("[HyperNowBar] StatusBarKeyguardViewManager hook error: " + t.getMessage());
            }
        }

        // 3. StatusBarHideIconsForBouncerManager
        Class<?> hfbCls = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.StatusBarHideIconsForBouncerManager", classLoader);
        if (hfbCls != null) {
            try {
                XposedBridge.hookAllMethods(hfbCls, "setBouncerShowingAndTriggerUpdate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof Boolean) {
                            boolean showing = (Boolean) param.args[0];
                            hookedBouncerShowing = showing;
                            notifyActive(showing);
                        }
                    }
                });
                XposedBridge.log("[HyperNowBar] Hooked StatusBarHideIconsForBouncerManager");
            } catch (Throwable ignored) {}
        }

        // 4. KeyguardStateControllerImpl
        Class<?> ksCls = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", classLoader);
        if (ksCls != null) {
            try {
                XposedBridge.hookAllMethods(ksCls, "notifyKeyguardState", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        boolean live = queryLiveBouncerState();
                        hookedBouncerShowing = live;
                        notifyActive(live);
                    }
                });
            } catch (Throwable ignored) {}
        }

        // 5. HyperOS Control Center awareness
        String[] ccCandidates = new String[] {
            "com.android.systemui.controlcenter.policy.ControlCenterControllerImpl",
            "com.miui.systemui.controlcenter.ControlCenterControllerImpl"
        };
        for (String ccName : ccCandidates) {
            Class<?> ccCls = XposedHelpers.findClassIfExists(ccName, classLoader);
            if (ccCls == null) continue;
            try {
                XposedBridge.hookAllMethods(ccCls, "setExpand", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof Boolean) {
                            hookedControlCenterExpanded = (Boolean) param.args[0];
                            notifyActive(false);
                        }
                    }
                });
                XposedBridge.hookAllMethods(ccCls, "collapseControlCenter", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (hookedControlCenterExpanded) {
                            hookedControlCenterExpanded = false;
                            notifyActive(false);
                        }
                    }
                });
                break;
            } catch (Throwable ignored) {}
        }
    }

    public static void bindBouncerContainer(ViewGroup root) {
        if (root == null) return;
        View found = findViewByResName(root, "keyguard_bouncer_container");
        if (found != null) {
            bouncerContainerRef = new WeakReference<>(found);
        }
    }

    public static boolean isBouncerContainer(View v) {
        View tracked = bouncerContainerRef.get();
        return tracked != null && v == tracked;
    }

    public static void onBouncerContainerVisibilityChanged(View v, int visibility) {
        View tracked = bouncerContainerRef.get();
        if (tracked != null && v == tracked) {
            boolean vis = (visibility == View.VISIBLE);
            hookedBouncerShowing = vis;
            notifyActive(vis);
        }
    }

    private static View findViewByResName(ViewGroup group, String targetName) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            try {
                int id = child.getId();
                if (id != View.NO_ID && targetName.equals(child.getResources().getResourceEntryName(id))) {
                    return child;
                }
            } catch (Throwable ignored) {}
            if (child instanceof ViewGroup) {
                View r = findViewByResName((ViewGroup) child, targetName);
                if (r != null) return r;
            }
        }
        return null;
    }

    /**
     * Queries the live ground-truth Bouncer state directly from StatusBarKeyguardViewManager
     * and the actual keyguard_bouncer_container view visibility/alpha.
     */
    private static boolean queryLiveBouncerState() {
        View bc = bouncerContainerRef.get();
        boolean viewSaysVisible = (bc != null && bc.getVisibility() == View.VISIBLE && bc.getAlpha() > 0.05f);

        Object kvm = keyguardViewManagerRef;
        if (kvm != null) {
            try {
                Object res = XposedHelpers.callMethod(kvm, "primaryBouncerIsShowing");
                if (res instanceof Boolean) {
                    return ((Boolean) res) || viewSaysVisible;
                }
            } catch (Throwable ignored) {}
            try {
                Object res = XposedHelpers.callMethod(kvm, "isBouncerShowing");
                if (res instanceof Boolean) {
                    return ((Boolean) res) || viewSaysVisible;
                }
            } catch (Throwable ignored) {}
            try {
                Object res = XposedHelpers.callMethod(kvm, "bouncerIsOrWillBeShowing");
                if (res instanceof Boolean) {
                    return ((Boolean) res) || viewSaysVisible;
                }
            } catch (Throwable ignored) {}
        }

        if (bc != null) {
            return viewSaysVisible;
        }
        return hookedBouncerShowing;
    }

    private static void notifyActive(boolean startWatchdog) {
        KeyguardStateMonitor inst = activeInstance;
        if (inst != null) {
            inst.mainHandler.post(() -> {
                if (startWatchdog) {
                    inst.ensureBouncerWatchdog();
                }
                inst.dispatchUpdate();
            });
        }
    }

    private void ensureBouncerWatchdog() {
        if (watchdogRunning) return;
        watchdogRunning = true;
        mainHandler.removeCallbacks(bouncerWatchdog);
        mainHandler.postDelayed(bouncerWatchdog, 200);
    }

    private void registerScreenReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String act = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(act)) {
                    screenOn = false;
                    hookedControlCenterExpanded = false;
                    hookedBouncerShowing = false;
                    watchdogRunning = false;
                    mainHandler.removeCallbacks(bouncerWatchdog);
                } else if (Intent.ACTION_SCREEN_ON.equals(act)) {
                    screenOn = true;
                    hookedDozing = false;
                    hookedBouncerShowing = queryLiveBouncerState();
                    if (hookedBouncerShowing) ensureBouncerWatchdog();
                    mainHandler.postDelayed(() -> {
                        hookedBouncerShowing = queryLiveBouncerState();
                        dispatchUpdate();
                    }, 220);
                } else if (Intent.ACTION_USER_PRESENT.equals(act)) {
                    screenOn = true;
                    hookedStatusBarState = STATE_SHADE;
                    hookedControlCenterExpanded = false;
                    hookedBouncerShowing = false;
                    watchdogRunning = false;
                    mainHandler.removeCallbacks(bouncerWatchdog);
                    if (listener != null) {
                        listener.onDeviceUnlocked();
                    }
                }
                dispatchUpdate();
            }
        };

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] Screen receiver error: " + t.getMessage());
        }
    }

    public boolean isCurrentlyOnKeyguard() {
        boolean kmLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        if (stateControllerHooked) {
            return kmLocked && (hookedStatusBarState != STATE_SHADE_LOCKED) && !hookedControlCenterExpanded;
        }
        return kmLocked && !hookedControlCenterExpanded;
    }

    public void dispatchUpdate() {
        boolean interactive = screenOn && (powerManager == null || powerManager.isInteractive());
        boolean onKeyguard = isCurrentlyOnKeyguard();
        boolean bouncer = queryLiveBouncerState();
        hookedBouncerShowing = bouncer;
        if (bouncer && interactive && onKeyguard) {
            ensureBouncerWatchdog();
        }
        if (listener != null) {
            listener.onLockscreenVisibilityChanged(onKeyguard, bouncer, hookedDozing, interactive);
        }
    }
}
