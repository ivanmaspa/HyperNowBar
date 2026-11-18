package com.hypernowbar.hook;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.hypernowbar.config.NowBarConfig;
import com.hypernowbar.model.NotifItem;
import com.hypernowbar.ui.CapsuleColors;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hooks SystemUI's internal NotificationListenerService (and registerAsSystemService)
 * to obtain live notifications, dismiss individual alerts, and open messages directly from Now Bar.
 */
public final class NotifTracker {
    public interface Listener {
        void onNotificationsChanged(List<NotifItem> items);
    }

    private static volatile NotificationListenerService listenerService;
    private static volatile NotifTracker activeInstance;
    private static boolean hooksInstalled = false;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Map<String, StatusBarNotification> sbnCache = new HashMap<>();
    private final Map<String, Long> seenOnUnlockTimes = new HashMap<>();
    private final Map<String, Drawable> appIconCache = new HashMap<>();
    private final Map<String, String> appNameCache = new HashMap<>();
    private List<NotifItem> latestItems = new ArrayList<>();
    private boolean hideSeenAfterUnlock = true;
    private int notifSourceMode = NowBarConfig.NOTIF_SOURCE_ALL_SHADE;
    private long lastScreenOffTimeMs = 0L;

    private static final Set<String> IGNORED_SYSTEM_PACKAGES = new HashSet<>();
    static {
        IGNORED_SYSTEM_PACKAGES.add("android");
        IGNORED_SYSTEM_PACKAGES.add("com.android.systemui");
        IGNORED_SYSTEM_PACKAGES.add("com.miui.securitycenter");
        IGNORED_SYSTEM_PACKAGES.add("com.miui.powerkeeper");
        IGNORED_SYSTEM_PACKAGES.add("com.miui.daemon");
    }

    public NotifTracker(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        activeInstance = this;
        if (listenerService != null) {
            refreshFromService();
        }
    }

    public static void installHooks(ClassLoader classLoader) {
        if (hooksInstalled) return;
        hooksInstalled = true;

        Class<?>[] candidates = new Class<?>[] {
            NotificationListenerService.class,
            XposedHelpers.findClassIfExists("com.android.systemui.statusbar.NotificationListener", classLoader),
            XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.NotificationListenerWithPlugins", classLoader)
        };

        for (Class<?> cls : candidates) {
            if (cls == null) continue;
            try {
                XposedBridge.hookAllMethods(cls, "registerAsSystemService", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof NotificationListenerService) {
                            listenerService = (NotificationListenerService) param.thisObject;
                            XposedBridge.log("[HyperNowBar] Captured NotificationListener via registerAsSystemService");
                            NotifTracker inst = activeInstance;
                            if (inst != null) inst.refreshFromService();
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onListenerConnected", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof NotificationListenerService) {
                            listenerService = (NotificationListenerService) param.thisObject;
                            NotifTracker inst = activeInstance;
                            if (inst != null) inst.refreshFromService();
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onNotificationPosted", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof NotificationListenerService) {
                            listenerService = (NotificationListenerService) param.thisObject;
                        }
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof StatusBarNotification) {
                            StatusBarNotification sbn = (StatusBarNotification) param.args[0];
                            NotifTracker inst = activeInstance;
                            if (inst != null) inst.onPosted(sbn);
                        }
                    }
                });

                XposedBridge.hookAllMethods(cls, "onNotificationRemoved", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof StatusBarNotification) {
                            StatusBarNotification sbn = (StatusBarNotification) param.args[0];
                            NotifTracker inst = activeInstance;
                            if (inst != null) inst.onRemoved(sbn);
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[HyperNowBar] NotifTracker hook error on " + cls.getName() + ": " + t.getMessage());
            }
        }
    }

    public void refreshFromService() {
        mainHandler.post(() -> {
            NotificationListenerService svc = listenerService;
            if (svc == null) return;
            try {
                StatusBarNotification[] active = svc.getActiveNotifications();
                if (active != null) {
                    sbnCache.clear();
                    for (StatusBarNotification sbn : active) {
                        if (sbn != null && sbn.getKey() != null) {
                            sbnCache.put(sbn.getKey(), sbn);
                        }
                    }
                    rebuildAndNotify();
                }
            } catch (Throwable ignored) {}
        });
    }

    public void setHideSeenAfterUnlock(boolean enabled) {
        mainHandler.post(() -> {
            if (hideSeenAfterUnlock == enabled) return;
            hideSeenAfterUnlock = enabled;
            rebuildAndNotify();
        });
    }

    public void setNotifSourceMode(int mode) {
        mainHandler.post(() -> {
            if (notifSourceMode == mode) return;
            notifSourceMode = mode;
            if (mode == NowBarConfig.NOTIF_SOURCE_AFTER_SCREEN_OFF && lastScreenOffTimeMs == 0L) {
                lastScreenOffTimeMs = System.currentTimeMillis();
            }
            rebuildAndNotify();
        });
    }

    public void onScreenTurnedOff() {
        mainHandler.post(() -> {
            lastScreenOffTimeMs = System.currentTimeMillis();
            if (notifSourceMode == NowBarConfig.NOTIF_SOURCE_AFTER_SCREEN_OFF) {
                rebuildAndNotify();
            }
        });
    }

    /**
     * Called when the user unlocks the phone: marks all currently visible notifications as seen
     * so they disappear from the lockscreen Now Bar (while staying intact in Android's shade).
     * Any NEW message arriving afterwards (or updated with a newer postTime) will appear fresh!
     */
    public void onDeviceUnlocked() {
        mainHandler.post(() -> {
            for (StatusBarNotification sbn : sbnCache.values()) {
                if (sbn != null && sbn.getKey() != null) {
                    seenOnUnlockTimes.put(sbn.getKey(), sbn.getPostTime());
                }
            }
            if (hideSeenAfterUnlock) {
                rebuildAndNotify();
            }
        });
    }

    private void onPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getKey() == null) return;
        mainHandler.post(() -> {
            sbnCache.put(sbn.getKey(), sbn);
            Long seenTime = seenOnUnlockTimes.get(sbn.getKey());
            if (seenTime != null && sbn.getPostTime() > seenTime) {
                // Fresh update/message on an existing chat — show it again!
                seenOnUnlockTimes.remove(sbn.getKey());
            }
            rebuildAndNotify();
        });
    }

    private void onRemoved(StatusBarNotification sbn) {
        if (sbn == null || sbn.getKey() == null) return;
        mainHandler.post(() -> {
            sbnCache.remove(sbn.getKey());
            seenOnUnlockTimes.remove(sbn.getKey());
            rebuildAndNotify();
        });
    }

    private void rebuildAndNotify() {
        Map<String, Integer> groupChildren = new HashMap<>();
        for (StatusBarNotification sbn : sbnCache.values()) {
            Notification n = sbn.getNotification();
            if (n == null) continue;
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) == 0 && sbn.getGroupKey() != null) {
                Integer cnt = groupChildren.get(sbn.getGroupKey());
                groupChildren.put(sbn.getGroupKey(), cnt == null ? 1 : cnt + 1);
            }
        }

        List<NotifItem> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        for (StatusBarNotification sbn : sbnCache.values()) {
            Notification n = sbn.getNotification();
            if (n == null) continue;

            // Mode 1: Only show notifications posted AFTER the screen was turned off
            if (notifSourceMode == NowBarConfig.NOTIF_SOURCE_AFTER_SCREEN_OFF && lastScreenOffTimeMs > 0L) {
                if (sbn.getPostTime() < lastScreenOffTimeMs) {
                    continue;
                }
            }

            // Hide notifications that were already present before the last screen unlock
            if (hideSeenAfterUnlock) {
                Long seenTime = seenOnUnlockTimes.get(sbn.getKey());
                if (seenTime != null && sbn.getPostTime() <= seenTime) {
                    continue;
                }
            }

            Bundle extras = n.extras != null ? n.extras : Bundle.EMPTY;

            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
                    || Notification.CATEGORY_TRANSPORT.equals(n.category)) {
                continue;
            }

            boolean isSummary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            if (isSummary && sbn.getGroupKey() != null) {
                Integer children = groupChildren.get(sbn.getGroupKey());
                if (children != null && children > 0) continue;
            }

            boolean ongoing = sbn.isOngoing() || (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
            String pkg = sbn.getPackageName();
            if (ongoing && (IGNORED_SYSTEM_PACKAGES.contains(pkg) || Notification.CATEGORY_SERVICE.equals(n.category))) {
                continue;
            }

            String title = extractCharSeq(extras.getCharSequence(Notification.EXTRA_TITLE));
            String text = extractBestMessageText(extras);

            if (title.isEmpty() && text.isEmpty()) continue;

            NotifItem item = new NotifItem();
            item.key = sbn.getKey();
            item.packageName = pkg;
            item.appName = resolveAppName(pm, pkg);
            item.appIcon = resolveAppIcon(pm, pkg, n);
            item.title = title.isEmpty() ? item.appName : title;
            item.text = text;
            item.timestampMs = sbn.getPostTime();
            item.accentColor = n.color != 0 ? (n.color | 0xFF000000) : CapsuleColors.DEFAULT_SECONDARY_ACCENT;
            item.clearable = sbn.isClearable();
            item.contentIntent = n.contentIntent;
            result.add(item);
        }

        Collections.sort(result, new Comparator<NotifItem>() {
            @Override
            public int compare(NotifItem a, NotifItem b) {
                return Long.compare(b.timestampMs, a.timestampMs);
            }
        });

        latestItems = result;
        if (listener != null) {
            listener.onNotificationsChanged(result);
        }
    }

    @SuppressWarnings("deprecation")
    private static String extractBestMessageText(Bundle extras) {
        try {
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null && messages.length > 0) {
                Parcelable last = messages[messages.length - 1];
                if (last instanceof Bundle) {
                    Bundle msgBundle = (Bundle) last;
                    String sender = extractCharSeq(msgBundle.getCharSequence("sender"));
                    String msgText = extractCharSeq(msgBundle.getCharSequence("text"));
                    if (!msgText.isEmpty()) {
                        return sender.isEmpty() ? msgText : (sender + ": " + msgText);
                    }
                }
            }
        } catch (Throwable ignored) {}

        String bigText = extractCharSeq(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (!bigText.isEmpty()) return bigText;

        String text = extractCharSeq(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (!text.isEmpty()) return text;

        return extractCharSeq(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
    }

    private static String extractCharSeq(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private String resolveAppName(PackageManager pm, String pkg) {
        String cached = appNameCache.get(pkg);
        if (cached != null) return cached;
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(info);
            String name = label != null ? label.toString() : pkg;
            appNameCache.put(pkg, name);
            return name;
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    private Drawable resolveAppIcon(PackageManager pm, String pkg, Notification n) {
        Drawable cached = appIconCache.get(pkg);
        if (cached != null) return cached;
        try {
            Drawable icon = pm.getApplicationIcon(pkg);
            if (icon != null) {
                appIconCache.put(pkg, icon);
                return icon;
            }
        } catch (Throwable ignored) {}
        try {
            if (n.getSmallIcon() != null) {
                return n.getSmallIcon().loadDrawable(context);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public List<NotifItem> getLatestItems() {
        return latestItems;
    }

    public void dismiss(String key) {
        if (key == null) return;
        NotificationListenerService svc = listenerService;
        if (svc != null) {
            try {
                svc.cancelNotification(key);
            } catch (Throwable ignored) {}
        }
        sbnCache.remove(key);
        rebuildAndNotify();
    }

    public void clearAll() {
        NotificationListenerService svc = listenerService;
        List<String> toRemove = new ArrayList<>();
        for (NotifItem item : latestItems) {
            if (item.clearable) {
                toRemove.add(item.key);
                if (svc != null) {
                    try {
                        svc.cancelNotification(item.key);
                    } catch (Throwable ignored) {}
                }
            }
        }
        for (String k : toRemove) {
            sbnCache.remove(k);
        }
        rebuildAndNotify();
    }
}
