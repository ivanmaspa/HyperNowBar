package com.hypernowbar.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Lightweight 4-bar fluid audio waveform visualizer for Now Bar.
 */
public final class EqualizerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean playing = false;
    private int barColor = CapsuleColors.DEFAULT_ACCENT;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (playing && isAttachedToWindow() && getVisibility() == VISIBLE) {
                invalidate();
                postDelayed(this, 32); // ~30fps smooth & ultra-low power
            }
        }
    };

    public EqualizerView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(barColor);
    }

    public void setBarColor(int color) {
        this.barColor = color;
        paint.setColor(color);
        invalidate();
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) return;
        this.playing = playing;
        removeCallbacks(ticker);
        if (playing && isAttachedToWindow() && getVisibility() == VISIBLE) {
            post(ticker);
        } else {
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (playing && getVisibility() == VISIBLE) {
            removeCallbacks(ticker);
            post(ticker);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        removeCallbacks(ticker);
        if (playing && visibility == VISIBLE && isAttachedToWindow()) {
            post(ticker);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int bars = 4;
        float gap = w * 0.14f;
        float barW = (w - gap * (bars - 1)) / bars;
        float radius = barW / 2f;
        double t = SystemClock.uptimeMillis() / 160.0;

        float[] staticRatios = {0.35f, 0.60f, 0.45f, 0.30f};
        double[] speeds = {1.15, 1.55, 0.95, 1.35};
        double[] phases = {0.0, 1.3, 2.6, 3.9};

        for (int i = 0; i < bars; i++) {
            float ratio;
            if (playing) {
                double wave = 0.5 + 0.5 * Math.sin(t * speeds[i] + phases[i]);
                ratio = (float) (0.24 + 0.72 * wave);
            } else {
                ratio = staticRatios[i];
            }
            float barH = Math.max(barW, h * ratio);
            float left = i * (barW + gap);
            float top = (h - barH) / 2f;
            rect.set(left, top, left + barW, top + barH);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
