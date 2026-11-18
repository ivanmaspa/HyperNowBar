package com.hypernowbar.ui;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class CapsuleColors {
    public static final int DEFAULT_ACCENT = 0xFF6C8CFF;
    public static final int DEFAULT_SECONDARY_ACCENT = 0xFF4AE3B5;

    public static int extractVibrantAccent(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return DEFAULT_ACCENT;
        }
        try {
            Bitmap small = Bitmap.createScaledBitmap(bitmap, 16, 16, true);
            float bestScore = -1f;
            int bestColor = DEFAULT_ACCENT;
            float[] hsv = new float[3];
            for (int y = 0; y < small.getHeight(); y++) {
                for (int x = 0; x < small.getWidth(); x++) {
                    int px = small.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    Color.colorToHSV(px, hsv);
                    float sat = hsv[1];
                    float val = hsv[2];
                    if (val < 0.2f || sat < 0.15f) continue;
                    float score = sat * 1.5f + val;
                    if (score > bestScore) {
                        bestScore = score;
                        bestColor = px;
                    }
                }
            }
            if (small != bitmap) small.recycle();
            Color.colorToHSV(bestColor, hsv);
            hsv[1] = Math.max(0.55f, Math.min(0.88f, hsv[1]));
            hsv[2] = Math.max(0.75f, Math.min(0.98f, hsv[2]));
            return Color.HSVToColor(255, hsv);
        } catch (Throwable ignored) {
            return DEFAULT_ACCENT;
        }
    }

    public static int withAlpha(int color, int alpha0to255) {
        int a = Math.max(0, Math.min(255, alpha0to255));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int blend(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int a = (int) (Color.alpha(c1) * inv + Color.alpha(c2) * ratio);
        int r = (int) (Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.argb(a, r, g, b);
    }
}
