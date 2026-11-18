package com.hypernowbar.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Pure Canvas vector icons so the injected SystemUI overlay never depends on external APK resources.
 */
public final class VectorIcons {

    public static final int ICON_PLAY = 1;
    public static final int ICON_PAUSE = 2;
    public static final int ICON_PREV = 3;
    public static final int ICON_NEXT = 4;
    public static final int ICON_CLOSE = 5;
    public static final int ICON_MUSIC = 6;
    public static final int ICON_BELL = 7;
    public static final int ICON_CHEVRON_DOWN = 8;
    public static final int ICON_SWITCH = 9;

    public static Drawable create(int iconType, int color) {
        return new IconDrawable(iconType, color);
    }

    private static final class IconDrawable extends Drawable {
        private final int type;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rectF = new RectF();

        IconDrawable(int type, int color) {
            this.type = type;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(color);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width();
            float h = b.height();
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float s = Math.min(w, h);
            float l = cx - s / 2f;
            float t = cy - s / 2f;

            path.reset();
            switch (type) {
                case ICON_PLAY: {
                    path.moveTo(l + s * 0.30f, t + s * 0.20f);
                    path.lineTo(l + s * 0.80f, t + s * 0.50f);
                    path.lineTo(l + s * 0.30f, t + s * 0.80f);
                    path.close();
                    canvas.drawPath(path, fillPaint);
                    break;
                }
                case ICON_PAUSE: {
                    float barW = s * 0.20f;
                    float r = barW * 0.45f;
                    rectF.set(l + s * 0.25f, t + s * 0.20f, l + s * 0.25f + barW, t + s * 0.80f);
                    canvas.drawRoundRect(rectF, r, r, fillPaint);
                    rectF.set(l + s * 0.55f, t + s * 0.20f, l + s * 0.55f + barW, t + s * 0.80f);
                    canvas.drawRoundRect(rectF, r, r, fillPaint);
                    break;
                }
                case ICON_PREV: {
                    float barW = s * 0.11f;
                    rectF.set(l + s * 0.20f, t + s * 0.24f, l + s * 0.20f + barW, t + s * 0.76f);
                    canvas.drawRoundRect(rectF, barW * 0.45f, barW * 0.45f, fillPaint);
                    path.moveTo(l + s * 0.78f, t + s * 0.24f);
                    path.lineTo(l + s * 0.35f, t + s * 0.50f);
                    path.lineTo(l + s * 0.78f, t + s * 0.76f);
                    path.close();
                    canvas.drawPath(path, fillPaint);
                    break;
                }
                case ICON_NEXT: {
                    path.moveTo(l + s * 0.22f, t + s * 0.24f);
                    path.lineTo(l + s * 0.65f, t + s * 0.50f);
                    path.lineTo(l + s * 0.22f, t + s * 0.76f);
                    path.close();
                    canvas.drawPath(path, fillPaint);
                    float barW = s * 0.11f;
                    rectF.set(l + s * 0.69f, t + s * 0.24f, l + s * 0.69f + barW, t + s * 0.76f);
                    canvas.drawRoundRect(rectF, barW * 0.45f, barW * 0.45f, fillPaint);
                    break;
                }
                case ICON_CLOSE: {
                    strokePaint.setStrokeWidth(s * 0.11f);
                    float pad = s * 0.28f;
                    canvas.drawLine(l + pad, t + pad, l + s - pad, t + s - pad, strokePaint);
                    canvas.drawLine(l + s - pad, t + pad, l + pad, t + s - pad, strokePaint);
                    break;
                }
                case ICON_MUSIC: {
                    strokePaint.setStrokeWidth(s * 0.09f);
                    canvas.drawCircle(l + s * 0.33f, t + s * 0.70f, s * 0.11f, fillPaint);
                    canvas.drawCircle(l + s * 0.69f, t + s * 0.63f, s * 0.11f, fillPaint);
                    path.moveTo(l + s * 0.42f, t + s * 0.70f);
                    path.lineTo(l + s * 0.42f, t + s * 0.26f);
                    path.lineTo(l + s * 0.78f, t + s * 0.19f);
                    path.lineTo(l + s * 0.78f, t + s * 0.63f);
                    canvas.drawPath(path, strokePaint);
                    break;
                }
                case ICON_BELL: {
                    strokePaint.setStrokeWidth(s * 0.09f);
                    rectF.set(l + s * 0.26f, t + s * 0.22f, l + s * 0.74f, t + s * 0.72f);
                    path.moveTo(l + s * 0.22f, t + s * 0.70f);
                    path.lineTo(l + s * 0.78f, t + s * 0.70f);
                    canvas.drawArc(rectF, 180f, 180f, false, strokePaint);
                    canvas.drawLine(l + s * 0.26f, t + s * 0.47f, l + s * 0.24f, t + s * 0.70f, strokePaint);
                    canvas.drawLine(l + s * 0.74f, t + s * 0.47f, l + s * 0.76f, t + s * 0.70f, strokePaint);
                    canvas.drawPath(path, strokePaint);
                    canvas.drawCircle(cx, t + s * 0.80f, s * 0.07f, fillPaint);
                    break;
                }
                case ICON_CHEVRON_DOWN: {
                    strokePaint.setStrokeWidth(s * 0.11f);
                    path.moveTo(l + s * 0.24f, t + s * 0.36f);
                    path.lineTo(cx, t + s * 0.66f);
                    path.lineTo(l + s * 0.76f, t + s * 0.36f);
                    canvas.drawPath(path, strokePaint);
                    break;
                }
                case ICON_SWITCH: {
                    strokePaint.setStrokeWidth(s * 0.095f);
                    // Top arrow pointing right
                    canvas.drawLine(l + s * 0.20f, t + s * 0.34f, l + s * 0.78f, t + s * 0.34f, strokePaint);
                    path.moveTo(l + s * 0.62f, t + s * 0.20f);
                    path.lineTo(l + s * 0.78f, t + s * 0.34f);
                    path.lineTo(l + s * 0.62f, t + s * 0.48f);
                    canvas.drawPath(path, strokePaint);
                    // Bottom arrow pointing left
                    path.reset();
                    canvas.drawLine(l + s * 0.80f, t + s * 0.66f, l + s * 0.22f, t + s * 0.66f, strokePaint);
                    path.moveTo(l + s * 0.38f, t + s * 0.52f);
                    path.lineTo(l + s * 0.22f, t + s * 0.66f);
                    path.lineTo(l + s * 0.38f, t + s * 0.80f);
                    canvas.drawPath(path, strokePaint);
                    break;
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
