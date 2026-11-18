package com.hypernowbar.hook;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.hypernowbar.config.NowBarConfig;
import com.hypernowbar.model.MediaState;
import com.hypernowbar.model.NotifItem;
import com.hypernowbar.ui.NowBarOverlayView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Main controller inside com.android.systemui.
 * Handles:
 *  - Universal WindowManagerGlobal.addView attachment (TYPE_NOTIFICATION_SHADE = 2040)
 *  - Touch isolation when expanded (prevents lockscreen swipe-to-unlock/bouncer from stealing scroll!)
 *  - Native StatusBarKeyguardViewManager.dismissWithAction() & ActivityStarter app launch
 *    (prompts Fingerprint/PIN first, then launches the exact chat/player in foreground!)
 *  - Instant Bouncer hide & auto-restore on cancel
 */
public final class NowBarController {
    public static final String ACTION_PING = "com.hypernowbar.ACTION_PING";
    public static final String ACTION_PONG = "com.hypernowbar.ACTION_PONG";
    private static final int TYPE_NOTIFICATION_SHADE = 2040;

    private static volatile NowBarController instance;
    private static volatile Object activityStarterRef;
    private static boolean hooksInstalled = false;
    private static boolean touchHookInstalled = false;

    private final Context context;
    private final ViewGroup shadeWindowView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final XSharedPreferences xprefs;
    private NowBarConfig config;

    private final NowBarOverlayView overlayView;
    private final MediaTracker mediaTracker;
    private final NotifTracker notifTracker;
    private final KeyguardStateMonitor keyguardMonitor;

    private boolean lastOnKeyguard = false;
    private boolean lastBouncerShowing = false;
    private boolean lastDozing = false;
    private boolean lastScreenInteractive = true;
    private boolean forwardingPillTouch = false;

    public static void installHooks(ClassLoader classLoader) {
        if (hooksInstalled) return;
        hooksInstalled = true;

        // 1. Capture SystemUI's native ActivityStarter (multiple package locations across Android 14-16 / HyperOS)
        String[] starterClasses = new String[] {
            "com.android.systemui.statusbar.phone.ActivityStarterImpl",
            "com.android.systemui.statusbar.phone.MiuiActivityStarterImpl",
            "com.android.systemui.ActivityStarterDelegate"
        };
        for (String sc : starterClasses) {
            Class<?> cls = XposedHelpers.findClassIfExists(sc, classLoader);
            if (cls == null) continue;
            try {
                XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject != null) {
                            activityStarterRef = param.thisObject;
                            XposedBridge.log("[HyperNowBar] Captured ActivityStarter: "
                                    + param.thisObject.getClass().getName());
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }

        // 2. UNIVERSAL PRIMARY HOOK: android.view.WindowManagerGlobal.addView
        Class<?> wmgCls = XposedHelpers.findClassIfExists("android.view.WindowManagerGlobal", classLoader);
        if (wmgCls != null) {
            try {
                XposedBridge.hookAllMethods(wmgCls, "addView", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length < 2) return;
                        if (!(param.args[0] instanceof ViewGroup)) return;
                        ViewGroup vg = (ViewGroup) param.args[0];
                        ViewGroup.LayoutParams lp = (param.args[1] instanceof ViewGroup.LayoutParams)
                                ? (ViewGroup.LayoutParams) param.args[1] : null;

                        String clsName = vg.getClass().getName();
                        boolean isShadeWindow = clsName.contains("NotificationShadeWindowView");
                        int winType = -1;
                        String winTitle = "";
                        if (lp instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) lp;
                            winType = wlp.type;
                            CharSequence t = wlp.getTitle();
                            winTitle = t != null ? t.toString() : "";
                            if (winType == TYPE_NOTIFICATION_SHADE || winTitle.contains("NotificationShade")) {
                                isShadeWindow = true;
                            }
                        }

                        if (isShadeWindow) {
                            XposedBridge.log("[HyperNowBar] Matched lockscreen window via WindowManagerGlobal.addView: "
                                    + clsName + " (type=" + winType + ", title=" + winTitle + ")");
                            installTouchIsolationHook(vg.getClass());
                            vg.post(() -> attachToWindow(vg));
                        }
                    }
                });
                XposedBridge.log("[HyperNowBar] Hooked WindowManagerGlobal.addView");
            } catch (Throwable t) {
                XposedBridge.log("[HyperNowBar] WindowManagerGlobal hook error: " + t.getMessage());
            }
        }

        // 3. SECONDARY BACKUP: Constructors of NotificationShadeWindowView
        String[] windowViewClasses = new String[] {
            "com.android.systemui.shade.NotificationShadeWindowView",
            "com.android.systemui.statusbar.phone.NotificationShadeWindowView"
        };
        for (String name : windowViewClasses) {
            Class<?> cls = XposedHelpers.findClassIfExists(name, classLoader);
            if (cls == null) continue;
            installTouchIsolationHook(cls);
            try {
                XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof ViewGroup) {
                            final ViewGroup vg = (ViewGroup) param.thisObject;
                            vg.post(() -> attachToWindow(vg));
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    private static void installTouchIsolationHook(Class<?> windowViewClass) {
        if (touchHookInstalled || windowViewClass == null) return;
        touchHookInstalled = true;
        try {
            XposedHelpers.findAndHookMethod(
                    windowViewClass,
                    "dispatchTouchEvent",
                    MotionEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            NowBarController ctrl = instance;
                            if (ctrl == null || param.thisObject != ctrl.shadeWindowView) return;
                            if (ctrl.overlayView.getVisibility() != android.view.View.VISIBLE) return;
                            MotionEvent ev = (MotionEvent) param.args[0];
                            if (ev == null) return;

                            if (ctrl.overlayView.isExpanded()) {
                                if (!ctrl.config.betaCompactMode) {
                                    boolean handled = ctrl.overlayView.dispatchTouchEvent(ev);
                                    param.setResult(Boolean.valueOf(handled || true));
                                    return;
                                }
                                // In Beta Compact Mode (no outer menu/scrim), isolate ONLY touches inside floating cards!
                                int act = ev.getActionMasked();
                                if (act == MotionEvent.ACTION_DOWN) {
                                    ctrl.forwardingPillTouch = ctrl.overlayView.isTouchInsideExpandedContent(
                                            ev.getRawX(), ev.getRawY());
                                }
                                if (ctrl.forwardingPillTouch) {
                                    boolean handled = ctrl.overlayView.dispatchTouchEvent(ev);
                                    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                                        ctrl.forwardingPillTouch = false;
                                    }
                                    param.setResult(Boolean.valueOf(handled || true));
                                }
                                return;
                            }

                            int action = ev.getActionMasked();
                            if (action == MotionEvent.ACTION_DOWN) {
                                ctrl.forwardingPillTouch = ctrl.overlayView.isTouchInsideCollapsedPill(
                                        ev.getRawX(), ev.getRawY());
                            }
                            if (ctrl.forwardingPillTouch) {
                                boolean handled = ctrl.overlayView.dispatchTouchEvent(ev);
                                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                                    ctrl.forwardingPillTouch = false;
                                }
                                param.setResult(Boolean.valueOf(handled || true));
                            }
                        }
                    });
            XposedBridge.log("[HyperNowBar] Installed touch isolation hook on " + windowViewClass.getName());
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] Touch isolation hook error: " + t.getMessage());
        }
    }

    private static void attachToWindow(ViewGroup windowView) {
        if (windowView == null) return;
        if (instance != null && instance.shadeWindowView == windowView) return;
        try {
            instance = new NowBarController(windowView);
            XposedBridge.log("[HyperNowBar] Attached NowBarOverlayView to " + windowView.getClass().getName());
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] Attach failed: " + t.getMessage());
        }
    }

    private NowBarController(ViewGroup shadeWindowView) {
        this.shadeWindowView = shadeWindowView;
        this.context = shadeWindowView.getContext();
        this.xprefs = new XSharedPreferences(NowBarConfig.PACKAGE_NAME, NowBarConfig.PREFS_NAME);
        this.config = NowBarConfig.fromXPrefs(xprefs);

        this.overlayView = new NowBarOverlayView(context, new NowBarOverlayView.ActionCallback() {
            @Override
            public void onPlayPause() {
                if (mediaTracker != null) mediaTracker.playPause();
            }

            @Override
            public void onNext() {
                if (mediaTracker != null) mediaTracker.next();
            }

            @Override
            public void onPrevious() {
                if (mediaTracker != null) mediaTracker.previous();
            }

            @Override
            public void onSeekTo(long positionMs) {
                if (mediaTracker != null) mediaTracker.seekTo(positionMs);
            }

            @Override
            public void onSwitchPlayer() {
                if (mediaTracker != null) mediaTracker.switchToNextPlayer();
            }

            @Override
            public void onOpenMediaApp() {
                if (!config.tapMediaOpensApp || mediaTracker == null) return;
                MediaState ms = mediaTracker.getCurrentState();
                overlayView.collapse();
                launchAfterKeyguardDismiss(ms.sessionActivity, ms.packageName);
            }

            @Override
            public void onOpenNotification(NotifItem item) {
                if (!config.tapNotifOpensApp || item == null) return;
                overlayView.collapse();
                launchAfterKeyguardDismiss(item.contentIntent, item.packageName);
            }

            @Override
            public void onDismissNotification(String key) {
                if (notifTracker != null) notifTracker.dismiss(key);
            }

            @Override
            public void onClearAllNotifications() {
                if (notifTracker != null) notifTracker.clearAll();
            }
        });

        this.overlayView.setConfig(config);
        StockNotifHider.setAlwaysKeepClockStretched(config.enabled && config.hideStockNotifications);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        shadeWindowView.addView(overlayView, lp);

        StockNotifHider.scanHierarchy(shadeWindowView);
        mainHandler.postDelayed(() -> StockNotifHider.scanHierarchy(shadeWindowView), 1500);
        mainHandler.postDelayed(() -> StockNotifHider.scanHierarchy(shadeWindowView), 4500);

        this.mediaTracker = new MediaTracker(context, state -> overlayView.setMediaState(state));
        this.mediaTracker.start();

        this.notifTracker = new NotifTracker(context, items -> overlayView.setNotifications(items));
        this.notifTracker.setHideSeenAfterUnlock(config.clearNotifsAfterUnlock);
        this.notifTracker.setNotifSourceMode(config.notifSourceMode);
        this.notifTracker.refreshFromService();

        this.keyguardMonitor = new KeyguardStateMonitor(context, new KeyguardStateMonitor.Listener() {
            @Override
            public void onLockscreenVisibilityChanged(boolean onKeyguard, boolean bouncerShowing, boolean isDozing, boolean interactive) {
                lastOnKeyguard = onKeyguard;
                lastBouncerShowing = bouncerShowing;
                lastDozing = isDozing;
                lastScreenInteractive = interactive;
                applyLockscreenState();
            }

            @Override
            public void onDeviceUnlocked() {
                if (notifTracker != null) {
                    notifTracker.onDeviceUnlocked();
                }
            }

            @Override
            public void onScreenTurnedOff() {
                if (notifTracker != null) {
                    notifTracker.onScreenTurnedOff();
                }
                overlayView.onScreenTurnedOff();
            }
        });

        registerConfigAndPingReceiver();
    }

    /**
     * Prompts PIN/Pattern/Fingerprint unlock FIRST, and ONLY AFTER successful unlock launches the target
     * PendingIntent or app package in the foreground — NEVER opens silently behind the lockscreen!
     */
    private void launchAfterKeyguardDismiss(final PendingIntent pendingIntent, final String packageName) {
        // 1. Primary native path: StatusBarKeyguardViewManager.dismissWithAction(OnDismissAction, ...)
        Object kvm = KeyguardStateMonitor.getKeyguardViewManager();
        if (kvm != null && invokeKvmDismissWithAction(kvm, () -> fireTargetDirectly(pendingIntent, packageName))) {
            XposedBridge.log("[HyperNowBar] Triggered unlock bouncer via StatusBarKeyguardViewManager.dismissWithAction");
            return;
        }

        // 2. Secondary native path: SystemUI ActivityStarter
        Object starter = resolveActivityStarter();
        if (starter != null) {
            if (pendingIntent != null && invokeStarterPendingIntent(starter, pendingIntent)) {
                return;
            }
            Intent launchIntent = buildLaunchIntent(packageName);
            if (launchIntent != null && invokeStarterIntent(starter, launchIntent)) {
                return;
            }
        }

        // 3. Last resort fallback (if already unlocked)
        fireTargetDirectly(pendingIntent, packageName);
    }

    private boolean invokeKvmDismissWithAction(Object kvm, final Runnable onUnlockedAction) {
        try {
            ClassLoader cl = kvm.getClass().getClassLoader();
            Class<?> onDismissCls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.plugins.ActivityStarter$OnDismissAction", cl);
            if (onDismissCls == null) return false;

            Object actionProxy = Proxy.newProxyInstance(
                    cl,
                    new Class<?>[] {onDismissCls},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String name = method.getName();
                            if ("onDismiss".equals(name)) {
                                mainHandler.post(onUnlockedAction);
                                return Boolean.FALSE;
                            }
                            if ("willRunAnimationOnKeyguard".equals(name)) {
                                return Boolean.FALSE;
                            }
                            if ("toString".equals(name)) return "HyperNowBarDismissAction";
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("equals".equals(name)) return proxy == args[0];
                            return null;
                        }
                    });

            // Find matching dismissWithAction overload on StatusBarKeyguardViewManager
            Method targetMethod = null;
            for (Method m : kvm.getClass().getMethods()) {
                if (!"dismissWithAction".equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length >= 3 && p[0].isAssignableFrom(onDismissCls) && p[2] == boolean.class) {
                    targetMethod = m;
                    break;
                }
            }
            if (targetMethod == null) return false;

            int pCount = targetMethod.getParameterTypes().length;
            if (pCount == 3) {
                targetMethod.invoke(kvm, actionProxy, null, Boolean.TRUE);
            } else if (pCount == 4) {
                targetMethod.invoke(kvm, actionProxy, null, Boolean.TRUE, null);
            } else {
                return false;
            }
            return true;
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] dismissWithAction error: " + t.getMessage());
            return false;
        }
    }

    private void fireTargetDirectly(PendingIntent pendingIntent, String packageName) {
        if (pendingIntent != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions opts = ActivityOptions.makeBasic();
                    opts.setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pendingIntent.send(context, 0, null, null, null, null, opts.toBundle());
                } else {
                    pendingIntent.send();
                }
                return;
            } catch (Throwable t) {
                XposedBridge.log("[HyperNowBar] PendingIntent send error: " + t.getMessage());
            }
        }

        Intent launchIntent = buildLaunchIntent(packageName);
        if (launchIntent != null) {
            try {
                context.startActivity(launchIntent);
            } catch (Throwable t) {
                XposedBridge.log("[HyperNowBar] startActivity error: " + t.getMessage());
            }
        }
    }

    private Intent buildLaunchIntent(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            return launch;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object resolveActivityStarter() {
        if (activityStarterRef != null) return activityStarterRef;
        try {
            ClassLoader cl = shadeWindowView.getClass().getClassLoader();
            Class<?> depCls = XposedHelpers.findClassIfExists("com.android.systemui.Dependency", cl);
            Class<?> asCls = XposedHelpers.findClassIfExists("com.android.systemui.plugins.ActivityStarter", cl);
            if (depCls != null && asCls != null) {
                Method getM = depCls.getMethod("get", Class.class);
                Object starter = getM.invoke(null, asCls);
                if (starter != null) {
                    activityStarterRef = starter;
                    return starter;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean invokeStarterPendingIntent(Object starter, PendingIntent pi) {
        try {
            Method m = starter.getClass().getMethod("postStartActivityDismissingKeyguard", PendingIntent.class);
            m.invoke(starter, pi);
            return true;
        } catch (Throwable ignored) {}
        try {
            Method m = starter.getClass().getMethod("startPendingIntentDismissingKeyguard", PendingIntent.class);
            m.invoke(starter, pi);
            return true;
        } catch (Throwable ignored) {}
        try {
            Method m = starter.getClass().getMethod("startPendingIntentDismissingKeyguard",
                    PendingIntent.class, Runnable.class);
            m.invoke(starter, pi, null);
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean invokeStarterIntent(Object starter, Intent intent) {
        try {
            Method m = starter.getClass().getMethod("postStartActivityDismissingKeyguard", Intent.class, int.class);
            m.invoke(starter, intent, 0);
            return true;
        } catch (Throwable ignored) {}
        try {
            Method m = starter.getClass().getMethod("startActivity", Intent.class, boolean.class);
            m.invoke(starter, intent, true);
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private void applyLockscreenState() {
        boolean bouncerBlocked = lastBouncerShowing && config.hideOnBouncer;
        boolean showNowBar = config.enabled
                && lastOnKeyguard
                && lastScreenInteractive
                && !bouncerBlocked
                && (!lastDozing || !config.hideOnAod);

        if (lastOnKeyguard) {
            StockNotifHider.scanHierarchy(shadeWindowView);
        }

        boolean hideStock = config.enabled
                && config.hideStockNotifications
                && lastOnKeyguard;
        StockNotifHider.setAlwaysKeepClockStretched(config.enabled && config.hideStockNotifications);
        StockNotifHider.setHiddenOnKeyguard(hideStock);

        overlayView.setHostVisible(showNowBar);

        if (showNowBar) {
            overlayView.bringToFront();
            mediaTracker.pollActiveSessions();
            notifTracker.refreshFromService();
        }
    }

    private void registerConfigAndPingReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(NowBarConfig.ACTION_CONFIG_CHANGED);
        filter.addAction(ACTION_PING);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String act = intent.getAction();
                if (NowBarConfig.ACTION_CONFIG_CHANGED.equals(act)) {
                    config = NowBarConfig.fromIntent(intent, config);
                    mainHandler.post(() -> {
                        if (notifTracker != null) {
                            notifTracker.setHideSeenAfterUnlock(config.clearNotifsAfterUnlock);
                            notifTracker.setNotifSourceMode(config.notifSourceMode);
                        }
                        StockNotifHider.setAlwaysKeepClockStretched(config.enabled && config.hideStockNotifications);
                        overlayView.setConfig(config);
                        applyLockscreenState();
                    });
                } else if (ACTION_PING.equals(act)) {
                    int tracked = StockNotifHider.scanHierarchy(shadeWindowView);
                    Intent pong = new Intent(ACTION_PONG);
                    pong.setPackage(NowBarConfig.PACKAGE_NAME);
                    pong.putExtra("window", shadeWindowView.getClass().getSimpleName());
                    pong.putExtra("tracked", tracked);
                    pong.putExtra("notifs", notifTracker.getLatestItems().size());
                    pong.putExtra("hasStarter",
                            KeyguardStateMonitor.getKeyguardViewManager() != null || resolveActivityStarter() != null);
                    pong.putExtra("media", mediaTracker.getCurrentState().hasSession
                            ? mediaTracker.getCurrentState().title : "");
                    try {
                        context.sendBroadcast(pong);
                    } catch (Throwable ignored) {}
                }
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getApplicationContext().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.getApplicationContext().registerReceiver(receiver, filter);
            }
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] Config receiver error: " + t.getMessage());
        }
    }
}
