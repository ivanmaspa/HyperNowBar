package com.hypernowbar.model;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import com.hypernowbar.ui.CapsuleColors;

public final class MediaState {
    public boolean hasSession = false;
    public boolean isPlaying = false;
    public String title = "";
    public String artist = "";
    public String packageName = "";
    public String appLabel = "";
    public int sessionCount = 0;
    public int activeSessionIndex = 0;
    public Bitmap artwork = null;
    public int accentColor = CapsuleColors.DEFAULT_ACCENT;
    public long durationMs = 0L;
    public long positionMs = 0L;
    public float playbackSpeed = 1.0f;
    public long positionUpdateTimeMs = 0L;
    public PendingIntent sessionActivity = null;

    public long getLivePositionMs() {
        if (!isPlaying || positionUpdateTimeMs <= 0L) {
            return Math.max(0L, positionMs);
        }
        long elapsed = SystemClock.elapsedRealtime() - positionUpdateTimeMs;
        long live = positionMs + (long) (elapsed * Math.max(0.1f, playbackSpeed));
        if (durationMs > 0L) {
            return Math.max(0L, Math.min(durationMs, live));
        }
        return Math.max(0L, live);
    }

    public static String formatTime(long ms) {
        if (ms <= 0L) return "0:00";
        long totalSec = ms / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" + sec : String.valueOf(sec));
    }
}
