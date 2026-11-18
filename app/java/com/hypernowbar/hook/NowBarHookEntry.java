package com.hypernowbar.hook;

import com.hypernowbar.config.NowBarConfig;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed / Vector (LSPosed) entry point declared in assets/xposed_init.
 */
public final class NowBarHookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;

        if (NowBarConfig.PACKAGE_NAME.equals(lpparam.packageName)) {
            hookSelfActivationCheck(lpparam.classLoader);
            return;
        }

        if ("com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log("[HyperNowBar] Initializing hooks in com.android.systemui");
            NotifTracker.installHooks(lpparam.classLoader);
            KeyguardStateMonitor.installHooks(lpparam.classLoader);
            StockNotifHider.installHooks(lpparam.classLoader);
            NowBarController.installHooks(lpparam.classLoader);
        }
    }

    private void hookSelfActivationCheck(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.hypernowbar.app.MainActivity",
                    cl,
                    "isModuleActive",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(Boolean.TRUE);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] Self-hook error: " + t.getMessage());
        }
    }
}
