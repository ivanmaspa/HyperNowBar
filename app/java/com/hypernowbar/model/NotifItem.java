package com.hypernowbar.model;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;

public final class NotifItem {
    public String key;
    public String packageName;
    public String appName;
    public Drawable appIcon;
    public String title;
    public String text;
    public long timestampMs;
    public int accentColor;
    public boolean clearable;
    public PendingIntent contentIntent;
}
