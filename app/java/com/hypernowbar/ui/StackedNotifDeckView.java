package com.hypernowbar.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.hypernowbar.model.NotifItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * True iOS-style continuously scrollable overlapping 3D notification card deck.
 * Cards overlap physically beneath the active front card; dragging UP/DOWN smoothly peels
 * the front card away and elevates the next stacked card into focus with spring snap & fling physics.
 */
public final class StackedNotifDeckView extends FrameLayout {

    public interface DeckCallback {
        void onOpenItem(NotifItem item);
        void onDismissItem(String key);
        void onActiveIndexChanged(int activeIndex, int totalCount);
    }

    private static final PathInterpolator SPRING_CURVE = new PathInterpolator(0.20f, 1.0f, 0.32f, 1.0f);

    private final DeckCallback callback;
    private final int touchSlop;
    private final List<NotifItem> items = new ArrayList<>();
    private final List<View> cardViews = new ArrayList<>();

    private float scrollPos = 0f;
    private int lastReportedIndex = -1;
    private ValueAnimator snapAnimator;
    private boolean compactMode = false;
    private boolean liquidGlass = false;
    private boolean cardSystemBlur = false;
    private int blurRadiusPx = 80;

    private float downX = 0f;
    private float downY = 0f;
    private float lastY = 0f;
    private boolean dragging = false;
    private VelocityTracker velocityTracker;

    public StackedNotifDeckView(Context context, DeckCallback callback) {
        super(context);
        this.callback = callback;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setVisualOptions(boolean compactMode, boolean liquidGlass, boolean cardSystemBlur, int blurRadiusPx) {
        if (this.compactMode == compactMode
                && this.liquidGlass == liquidGlass
                && this.cardSystemBlur == cardSystemBlur
                && this.blurRadiusPx == blurRadiusPx) {
            return;
        }
        this.compactMode = compactMode;
        this.liquidGlass = liquidGlass;
        this.cardSystemBlur = cardSystemBlur;
        this.blurRadiusPx = blurRadiusPx;
        rebuildCards();
        updateDeckHeight();
        layoutDeck();
    }

    public void setItems(List<NotifItem> newItems) {
        cancelSnap();
        items.clear();
        if (newItems != null) {
            int max = Math.min(newItems.size(), 25);
            for (int i = 0; i < max; i++) {
                items.add(newItems.get(i));
            }
        }
        int maxIdx = Math.max(0, items.size() - 1);
        if (scrollPos > maxIdx) scrollPos = maxIdx;
        if (scrollPos < 0f) scrollPos = 0f;

        rebuildCards();
        updateDeckHeight();
        layoutDeck();
        reportIndexIfChanged();
    }

    public void resetToTop() {
        cancelSnap();
        scrollPos = 0f;
        layoutDeck();
        reportIndexIfChanged();
    }

    public int getActiveIndex() {
        if (items.isEmpty()) return 0;
        return Math.max(0, Math.min(items.size() - 1, Math.round(scrollPos)));
    }

    private void updateDeckHeight() {
        int n = items.size();
        int hDp;
        if (n <= 0) {
            hDp = 0;
        } else if (compactMode) {
            if (n == 1) hDp = 68;
            else if (n == 2) hDp = 104;
            else if (n == 3) hDp = 132;
            else hDp = 152;
        } else {
            if (n == 1) hDp = 88;
            else if (n == 2) hDp = 136;
            else if (n == 3) hDp = 174;
            else hDp = 200;
        }
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.height = dp(hDp);
            setLayoutParams(lp);
        } else {
            setMinimumHeight(dp(hDp));
        }
    }

    private void rebuildCards() {
        removeAllViews();
        cardViews.clear();
        if (items.isEmpty()) return;

        int cardH = compactMode ? dp(64) : dp(82);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (int i = 0; i < items.size(); i++) {
            final int cardIndex = i;
            final NotifItem item = items.get(i);
            View card = createCardView(item, cardIndex, timeFmt);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, cardH);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            addView(card, lp);
            cardViews.add(card);
        }
    }

    private View createCardView(final NotifItem item, final int cardIndex, SimpleDateFormat timeFmt) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = compactMode ? dp(10) : dp(12);
        int padV = compactMode ? dp(7) : dp(10);
        int cornerPx = compactMode ? dp(16) : dp(18);
        row.setPadding(padH, padV, padH, padV);

        GradientDrawable bg;
        if (liquidGlass) {
            bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[] {0x7AFFFFFF, 0x3D1E2638, 0x55121622});
            bg.setStroke(dp(1), 0x8CFFFFFF);
        } else if (cardSystemBlur) {
            bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0xAD1E2330, 0xA0131620});
            bg.setStroke(dp(1), 0x45FFFFFF);
        } else {
            bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0xF0202534, 0xEB151924});
            bg.setStroke(dp(1), 0x38FFFFFF);
        }
        bg.setCornerRadius(cornerPx);

        if (cardSystemBlur) {
            SystemBlurHelper.applyBackdropBlur(row, bg, true, blurRadiusPx, cornerPx);
        } else {
            row.setBackground(bg);
            clipRounded(row, cornerPx);
        }

        row.setClickable(true);
        row.setOnClickListener(v -> {
            int active = getActiveIndex();
            if (cardIndex != active) {
                animateToIndex(cardIndex);
            } else if (callback != null) {
                callback.onOpenItem(item);
            }
        });

        int iconSize = compactMode ? dp(28) : dp(34);
        ImageView iconView = new ImageView(getContext());
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        clipRounded(iconView, compactMode ? dp(8) : dp(10));
        if (item.appIcon != null) {
            iconView.setImageDrawable(item.appIcon);
        } else {
            iconView.setImageDrawable(VectorIcons.create(VectorIcons.ICON_BELL, Color.WHITE));
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.rightMargin = compactMode ? dp(8) : dp(10);
        row.addView(iconView, iconLp);

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headerLine = new LinearLayout(getContext());
        headerLine.setOrientation(LinearLayout.HORIZONTAL);
        headerLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(getContext());
        titleTv.setText(item.title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactMode ? 12.5f : 13.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        headerLine.addView(titleTv, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView timeTv = new TextView(getContext());
        timeTv.setText(item.timestampMs > 0 ? timeFmt.format(new Date(item.timestampMs)) : "");
        timeTv.setTextColor(0x99FFFFFF);
        timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactMode ? 10f : 10.5f);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        timeLp.leftMargin = dp(6);
        headerLine.addView(timeTv, timeLp);
        textCol.addView(headerLine);

        if (!TextUtils.isEmpty(item.text)) {
            TextView bodyTv = new TextView(getContext());
            bodyTv.setText(item.text);
            bodyTv.setTextColor(0xDCFFFFFF);
            bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactMode ? 11.5f : 12.5f);
            bodyTv.setMaxLines(compactMode ? 1 : 2);
            bodyTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            bodyLp.topMargin = dp(1);
            textCol.addView(bodyTv, bodyLp);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        if (item.clearable) {
            int cbSize = compactMode ? dp(22) : dp(26);
            ImageView closeBtn = new ImageView(getContext());
            closeBtn.setImageDrawable(VectorIcons.create(VectorIcons.ICON_CLOSE, 0xCCFFFFFF));
            closeBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
            GradientDrawable cbBg = new GradientDrawable();
            cbBg.setShape(GradientDrawable.OVAL);
            cbBg.setColor(0x20FFFFFF);
            closeBtn.setBackground(cbBg);
            closeBtn.setOnClickListener(v -> {
                if (callback != null) callback.onDismissItem(item.key);
            });
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(cbSize, cbSize);
            cbLp.leftMargin = dp(6);
            row.addView(closeBtn, cbLp);
        }

        return row;
    }

    /**
     * Positions every card in continuous 3D overlapping iOS stack coordinates based on scrollPos.
     */
    private void layoutDeck() {
        int n = cardViews.size();
        for (int i = 0; i < n; i++) {
            View v = cardViews.get(i);
            float d = i - scrollPos; // 0 = front card, >0 = overlapping cards below, <0 = peeling away above

            if (d < -1.25f || d > 3.65f) {
                v.setVisibility(GONE);
                continue;
            }
            v.setVisibility(VISIBLE);

            if (d >= 0f) {
                float step1 = compactMode ? 33f : 44f;
                float step2 = compactMode ? 27f : 36f;
                float step3 = compactMode ? 18f : 24f;
                float yDp;
                if (d <= 1f) {
                    yDp = d * step1;
                } else if (d <= 2f) {
                    yDp = step1 + (d - 1f) * step2;
                } else {
                    yDp = step1 + step2 + (d - 2f) * step3;
                }

                float scale = Math.max(0.82f, 1.0f - d * 0.048f);
                float alpha;
                if (d <= 1f) {
                    alpha = 1.0f - d * 0.10f;
                } else if (d <= 2f) {
                    alpha = 0.90f - (d - 1f) * 0.22f;
                } else {
                    alpha = Math.max(0f, 0.68f - (d - 2f) * 0.45f);
                }

                float zDp = Math.max(1f, 20f - d * 5f);
                v.setTranslationY(dpF(yDp));
                v.setScaleX(scale);
                v.setScaleY(scale);
                v.setAlpha(alpha);
                v.setElevation(dpF(zDp));
                v.setTranslationZ(dpF(zDp));
            } else {
                // Front card peeling smoothly UP and away as you scroll forward!
                float progress = -d; // 0..1.25
                float yDp = -progress * 42f;
                float scale = Math.max(0.86f, 1.0f - progress * 0.10f);
                float alpha = Math.max(0f, 1.0f - progress * 1.18f);
                float zDp = Math.max(1f, 20f - progress * 8f);

                v.setTranslationY(dpF(yDp));
                v.setScaleX(scale);
                v.setScaleY(scale);
                v.setAlpha(alpha);
                v.setElevation(dpF(zDp));
                v.setTranslationZ(dpF(zDp));
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (items.size() <= 1) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSnap();
                downX = ev.getRawX();
                downY = ev.getRawY();
                lastY = downY;
                dragging = false;
                initVelocityTracker(ev);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (velocityTracker != null) velocityTracker.addMovement(ev);
                float dx = Math.abs(ev.getRawX() - downX);
                float dy = Math.abs(ev.getRawY() - downY);
                if (dy > touchSlop && dy >= dx * 0.8f) {
                    dragging = true;
                    lastY = ev.getRawY();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                recycleVelocityTracker();
                return false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (items.size() <= 1) return false;
        if (velocityTracker != null) velocityTracker.addMovement(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSnap();
                downY = ev.getRawY();
                lastY = downY;
                dragging = true;
                initVelocityTracker(ev);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float curY = ev.getRawY();
                float dy = curY - lastY;
                lastY = curY;

                // 1 card step = ~68dp of vertical finger travel (silky 1:1 tracking!)
                float stepPx = dpF(68f);
                float deltaCards = -dy / stepPx;
                float maxIdx = Math.max(0f, items.size() - 1);

                // Rubber-band resistance past top/bottom edge
                if ((scrollPos < 0f && deltaCards < 0f) || (scrollPos > maxIdx && deltaCards > 0f)) {
                    deltaCards *= 0.28f;
                }

                scrollPos = Math.max(-0.45f, Math.min(maxIdx + 0.45f, scrollPos + deltaCards));
                layoutDeck();
                reportIndexIfChanged();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                float velY = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, dpF(2800f));
                    velY = velocityTracker.getYVelocity();
                    recycleVelocityTracker();
                }
                int maxIdx = Math.max(0, items.size() - 1);
                // Fling projection: flicking fast advances multiple cards smoothly!
                float projected = scrollPos - (velY / dpF(1100f));
                int target = Math.max(0, Math.min(maxIdx, Math.round(projected)));
                animateToIndex(target);
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    public void animateToIndex(int targetIndex) {
        int maxIdx = Math.max(0, items.size() - 1);
        int clamped = Math.max(0, Math.min(maxIdx, targetIndex));
        cancelSnap();
        float dist = Math.abs(clamped - scrollPos);
        long dur = (long) Math.max(160, Math.min(340, 180 + dist * 65));
        snapAnimator = ValueAnimator.ofFloat(scrollPos, (float) clamped);
        snapAnimator.setDuration(dur);
        snapAnimator.setInterpolator(SPRING_CURVE);
        snapAnimator.addUpdateListener(anim -> {
            scrollPos = (Float) anim.getAnimatedValue();
            layoutDeck();
            reportIndexIfChanged();
        });
        snapAnimator.start();
    }

    private void cancelSnap() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
    }

    private void reportIndexIfChanged() {
        int idx = getActiveIndex();
        if (idx != lastReportedIndex) {
            lastReportedIndex = idx;
            if (callback != null) {
                callback.onActiveIndexChanged(idx, items.size());
            }
        }
    }

    private void initVelocityTracker(MotionEvent ev) {
        recycleVelocityTracker();
        velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(ev);
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void clipRounded(View view, final int radiusPx) {
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radiusPx);
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float dpF(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
