package com.hypernowbar.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.hypernowbar.config.NowBarConfig;
import com.hypernowbar.model.MediaState;
import com.hypernowbar.model.NotifItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified One UI 7 Now Bar + ColorOS Fluid Glass + Monochrome B&W Capsule overlay (v2.4-beta).
 * Features:
 *  - Inline 1-tap Play/Pause + Next track controls right inside the collapsed pill
 *  - Native HyperOS GPU MiBackgroundBlur / SurfaceFlinger BackgroundBlurDrawable
 *  - Pure continuously scrollable 3D iOS overlapping notification card deck (StackedNotifDeckView)
 *  - Expanded sheet anchored strictly ABOVE the under-display fingerprint scanner
 */
public final class NowBarOverlayView extends FrameLayout {

    public interface ActionCallback {
        void onPlayPause();
        void onNext();
        void onPrevious();
        void onSeekTo(long positionMs);
        void onSwitchPlayer();
        void onOpenMediaApp();
        void onOpenNotification(NotifItem item);
        void onDismissNotification(String key);
        void onClearAllNotifications();
    }

    private static final PathInterpolator FLUID_SPRING = new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f);

    private final ActionCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NowBarConfig config = new NowBarConfig();
    private MediaState mediaState = new MediaState();
    private List<NotifItem> notifications = new ArrayList<>();
    private boolean expanded = false;
    private boolean hostVisible = false;
    private boolean seeking = false;
    private boolean previewMode = false;

    // Root interactive layers
    private final View scrimView;
    private final LinearLayout capsulePill;
    private final LinearLayout expandedSheet;

    // Collapsed capsule children
    private final LinearLayout pillMediaGroup;
    private final ImageView pillArtView;
    private final TextView pillTitleView;
    private final EqualizerView pillEqualizer;
    private final LinearLayout pillQuickControls;
    private final ImageView pillPlayPauseBtn;
    private final ImageView pillNextBtn;
    private final View pillDivider;
    private final LinearLayout pillNotifGroup;
    private final LinearLayout pillNotifIconsRow;
    private final TextView pillNotifSnippet;
    private final TextView pillNotifBadge;
    private final TextView pillEmptyLabel;

    // Expanded sheet children
    private final LinearLayout mediaCard;
    private final ImageView expArtView;
    private final TextView expTitleView;
    private final TextView expArtistView;
    private final EqualizerView expEqualizer;
    private final LinearLayout expSwitchPlayerBtn;
    private final TextView expSwitchPlayerText;
    private final SeekBar expSeekBar;
    private final TextView expPosText;
    private final TextView expDurText;
    private final ImageView expPrevBtn;
    private final ImageView expPlayPauseBtn;
    private final ImageView expNextBtn;

    private final LinearLayout notifSection;
    private final TextView notifHeaderTitle;
    private final TextView notifClearBtn;
    private final StackedNotifDeckView notifDeckView;
    private final TextView notifEmptyHint;

    private final int[] tmpLoc = new int[2];

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (expanded && hostVisible && mediaState.hasSession && mediaState.isPlaying && !seeking) {
                updateProgressViews();
                mainHandler.postDelayed(this, 800);
            }
        }
    };

    public NowBarOverlayView(Context context, ActionCallback callback) {
        super(context);
        this.callback = callback;
        setClipChildren(false);
        setClipToPadding(false);

        // 1. Scrim backdrop (only active when expanded)
        scrimView = new View(context);
        scrimView.setBackgroundColor(0x9905070D);
        scrimView.setAlpha(0f);
        scrimView.setVisibility(GONE);
        scrimView.setClickable(true);
        scrimView.setOnClickListener(v -> collapse());
        addView(scrimView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 2. Collapsed Now Bar Pill
        capsulePill = new LinearLayout(context);
        capsulePill.setOrientation(LinearLayout.HORIZONTAL);
        capsulePill.setGravity(Gravity.CENTER_VERTICAL);
        capsulePill.setPadding(dp(9), dp(6), dp(10), dp(6));
        capsulePill.setMinimumHeight(dp(46));
        capsulePill.setElevation(dp(10));
        capsulePill.setClickable(true);
        capsulePill.setFocusable(true);
        capsulePill.setOnClickListener(v -> expand());

        // Media info + inline quick buttons inside pill
        pillMediaGroup = new LinearLayout(context);
        pillMediaGroup.setOrientation(LinearLayout.HORIZONTAL);
        pillMediaGroup.setGravity(Gravity.CENTER_VERTICAL);

        pillArtView = new ImageView(context);
        pillArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        clipRounded(pillArtView, dp(15));
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        artLp.rightMargin = dp(8);
        pillMediaGroup.addView(pillArtView, artLp);

        pillTitleView = new TextView(context);
        pillTitleView.setTextColor(Color.WHITE);
        pillTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        pillTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pillTitleView.setSingleLine(true);
        pillTitleView.setEllipsize(TextUtils.TruncateAt.END);
        pillTitleView.setMaxWidth(dp(106));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleLp.rightMargin = dp(6);
        pillMediaGroup.addView(pillTitleView, titleLp);

        pillEqualizer = new EqualizerView(context);
        LinearLayout.LayoutParams eqLp = new LinearLayout.LayoutParams(dp(14), dp(12));
        eqLp.rightMargin = dp(6);
        pillMediaGroup.addView(pillEqualizer, eqLp);

        pillQuickControls = new LinearLayout(context);
        pillQuickControls.setOrientation(LinearLayout.HORIZONTAL);
        pillQuickControls.setGravity(Gravity.CENTER_VERTICAL);

        pillPlayPauseBtn = createControlButton(VectorIcons.ICON_PLAY, dp(28), dp(6), Color.WHITE, 0x24FFFFFF);
        pillPlayPauseBtn.setOnClickListener(v -> {
            if (this.callback != null) this.callback.onPlayPause();
        });
        LinearLayout.LayoutParams qPlayLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        qPlayLp.rightMargin = dp(4);
        pillQuickControls.addView(pillPlayPauseBtn, qPlayLp);

        pillNextBtn = createControlButton(VectorIcons.ICON_NEXT, dp(28), dp(6), Color.WHITE, 0x1AFFFFFF);
        pillNextBtn.setOnClickListener(v -> {
            if (this.callback != null) this.callback.onNext();
        });
        pillQuickControls.addView(pillNextBtn, new LinearLayout.LayoutParams(dp(28), dp(28)));

        pillMediaGroup.addView(pillQuickControls);
        capsulePill.addView(pillMediaGroup);

        // Divider between media & notifications
        pillDivider = new View(context);
        pillDivider.setBackgroundColor(0x38FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(18));
        divLp.leftMargin = dp(8);
        divLp.rightMargin = dp(8);
        capsulePill.addView(pillDivider, divLp);

        // Notification group inside pill
        pillNotifGroup = new LinearLayout(context);
        pillNotifGroup.setOrientation(LinearLayout.HORIZONTAL);
        pillNotifGroup.setGravity(Gravity.CENTER_VERTICAL);

        pillNotifIconsRow = new LinearLayout(context);
        pillNotifIconsRow.setOrientation(LinearLayout.HORIZONTAL);
        pillNotifIconsRow.setGravity(Gravity.CENTER_VERTICAL);
        pillNotifGroup.addView(pillNotifIconsRow);

        pillNotifSnippet = new TextView(context);
        pillNotifSnippet.setTextColor(0xF0FFFFFF);
        pillNotifSnippet.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        pillNotifSnippet.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pillNotifSnippet.setSingleLine(true);
        pillNotifSnippet.setEllipsize(TextUtils.TruncateAt.END);
        pillNotifSnippet.setMaxWidth(dp(136));
        LinearLayout.LayoutParams snipLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        snipLp.leftMargin = dp(7);
        pillNotifGroup.addView(pillNotifSnippet, snipLp);

        pillNotifBadge = new TextView(context);
        pillNotifBadge.setTextColor(0xFF0A0D14);
        pillNotifBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        pillNotifBadge.setTypeface(Typeface.DEFAULT_BOLD);
        pillNotifBadge.setGravity(Gravity.CENTER);
        pillNotifBadge.setPadding(dp(6), dp(1), dp(6), dp(1));
        pillNotifBadge.setMinWidth(dp(20));
        pillNotifBadge.setMinHeight(dp(20));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = dp(5);
        pillNotifGroup.addView(pillNotifBadge, badgeLp);

        capsulePill.addView(pillNotifGroup);

        pillEmptyLabel = new TextView(context);
        pillEmptyLabel.setText("Now Bar • Нет событий");
        pillEmptyLabel.setTextColor(0x99FFFFFF);
        pillEmptyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        pillEmptyLabel.setPadding(dp(4), 0, dp(4), 0);
        pillEmptyLabel.setVisibility(GONE);
        capsulePill.addView(pillEmptyLabel);

        LayoutParams pillLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pillLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pillLp.bottomMargin = dp(config.bottomMarginDp);
        addView(capsulePill, pillLp);

        // 3. Expanded Frosted Glass Sheet (anchored strictly ABOVE the optical fingerprint scanner)
        expandedSheet = new LinearLayout(context);
        expandedSheet.setOrientation(LinearLayout.VERTICAL);
        expandedSheet.setPadding(dp(16), dp(10), dp(16), dp(16));
        expandedSheet.setElevation(dp(18));
        expandedSheet.setVisibility(GONE);
        expandedSheet.setClickable(true);
        expandedSheet.setClipChildren(false);

        // Top drag/collapse handle
        FrameLayout handleWrap = new FrameLayout(context);
        handleWrap.setPadding(0, dp(2), 0, dp(12));
        final float[] dragStartY = new float[1];
        handleWrap.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartY[0] = ev.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = Math.max(0f, ev.getRawY() - dragStartY[0]);
                    expandedSheet.setTranslationY(dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float dy = ev.getRawY() - dragStartY[0];
                    if (dy > dp(42) || Math.abs(dy) < dp(10)) {
                        collapse();
                    } else {
                        expandedSheet.animate()
                                .translationY(0f)
                                .setDuration(200)
                                .setInterpolator(FLUID_SPRING)
                                .start();
                    }
                    return true;
                }
            }
            return false;
        });

        View handleBar = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x55FFFFFF);
        handleBg.setCornerRadius(dp(2));
        handleBar.setBackground(handleBg);
        LayoutParams hbLp = new LayoutParams(dp(42), dp(4));
        hbLp.gravity = Gravity.CENTER;
        handleWrap.addView(handleBar, hbLp);
        expandedSheet.addView(handleWrap, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // --- Expanded Media Player Card ---
        mediaCard = new LinearLayout(context);
        mediaCard.setOrientation(LinearLayout.VERTICAL);
        mediaCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout mediaTopRow = new LinearLayout(context);
        mediaTopRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaTopRow.setGravity(Gravity.CENTER_VERTICAL);
        mediaTopRow.setClickable(true);
        mediaTopRow.setOnClickListener(v -> {
            if (config.tapMediaOpensApp && this.callback != null) {
                this.callback.onOpenMediaApp();
            }
        });

        expArtView = new ImageView(context);
        expArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        clipRounded(expArtView, dp(14));
        LinearLayout.LayoutParams expArtLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        expArtLp.rightMargin = dp(12);
        mediaTopRow.addView(expArtView, expArtLp);

        LinearLayout metaCol = new LinearLayout(context);
        metaCol.setOrientation(LinearLayout.VERTICAL);
        expTitleView = new TextView(context);
        expTitleView.setTextColor(Color.WHITE);
        expTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        expTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        expTitleView.setSingleLine(true);
        expTitleView.setEllipsize(TextUtils.TruncateAt.END);
        metaCol.addView(expTitleView);

        expArtistView = new TextView(context);
        expArtistView.setTextColor(0xB3FFFFFF);
        expArtistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        expArtistView.setSingleLine(true);
        expArtistView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams artistLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        artistLp.topMargin = dp(2);
        metaCol.addView(expArtistView, artistLp);

        mediaTopRow.addView(metaCol, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        expEqualizer = new EqualizerView(context);
        LinearLayout.LayoutParams expEqLp = new LinearLayout.LayoutParams(dp(20), dp(16));
        expEqLp.leftMargin = dp(8);
        mediaTopRow.addView(expEqualizer, expEqLp);

        // Compact multi-player switcher button (ONLY shown when 2+ music apps are open!)
        expSwitchPlayerBtn = new LinearLayout(context);
        expSwitchPlayerBtn.setOrientation(LinearLayout.HORIZONTAL);
        expSwitchPlayerBtn.setGravity(Gravity.CENTER_VERTICAL);
        expSwitchPlayerBtn.setPadding(dp(8), dp(5), dp(9), dp(5));
        expSwitchPlayerBtn.setClickable(true);
        expSwitchPlayerBtn.setFocusable(true);
        GradientDrawable swBg = new GradientDrawable();
        swBg.setColor(0x28FFFFFF);
        swBg.setCornerRadius(dp(14));
        swBg.setStroke(dp(1), 0x3DFFFFFF);
        expSwitchPlayerBtn.setBackground(swBg);
        expSwitchPlayerBtn.setVisibility(GONE);
        expSwitchPlayerBtn.setOnClickListener(v -> {
            if (this.callback != null) this.callback.onSwitchPlayer();
        });

        ImageView swIcon = new ImageView(context);
        swIcon.setImageDrawable(VectorIcons.create(VectorIcons.ICON_SWITCH, Color.WHITE));
        LinearLayout.LayoutParams swIconLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        swIconLp.rightMargin = dp(4);
        expSwitchPlayerBtn.addView(swIcon, swIconLp);

        expSwitchPlayerText = new TextView(context);
        expSwitchPlayerText.setText("1/2");
        expSwitchPlayerText.setTextColor(Color.WHITE);
        expSwitchPlayerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        expSwitchPlayerText.setTypeface(Typeface.DEFAULT_BOLD);
        expSwitchPlayerBtn.addView(expSwitchPlayerText);

        LinearLayout.LayoutParams swBtnLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        swBtnLp.leftMargin = dp(8);
        mediaTopRow.addView(expSwitchPlayerBtn, swBtnLp);

        mediaCard.addView(mediaTopRow);

        expSeekBar = new SeekBar(context);
        expSeekBar.setPadding(dp(4), dp(6), dp(4), dp(2));
        expSeekBar.setMax(1000);
        expSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaState.durationMs > 0) {
                    long target = (mediaState.durationMs * progress) / 1000L;
                    expPosText.setText(MediaState.formatTime(target));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { seeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seeking = false;
                if (mediaState.durationMs > 0 && NowBarOverlayView.this.callback != null) {
                    long target = (mediaState.durationMs * seekBar.getProgress()) / 1000L;
                    NowBarOverlayView.this.callback.onSeekTo(target);
                }
            }
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(8);
        mediaCard.addView(expSeekBar, sbLp);

        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        expPosText = new TextView(context);
        expPosText.setTextColor(0x99FFFFFF);
        expPosText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        expPosText.setText("0:00");
        timeRow.addView(expPosText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        expDurText = new TextView(context);
        expDurText.setTextColor(0x99FFFFFF);
        expDurText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        expDurText.setGravity(Gravity.END);
        expDurText.setText("0:00");
        timeRow.addView(expDurText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        mediaCard.addView(timeRow);

        LinearLayout ctrlRow = new LinearLayout(context);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ctrlRowLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ctrlRowLp.topMargin = dp(6);

        expPrevBtn = createControlButton(VectorIcons.ICON_PREV, dp(40), dp(10), Color.WHITE, 0x1AFFFFFF);
        expPrevBtn.setOnClickListener(v -> { if (this.callback != null) this.callback.onPrevious(); });

        expPlayPauseBtn = createControlButton(VectorIcons.ICON_PLAY, dp(48), dp(11), 0xFF0A0D14, CapsuleColors.DEFAULT_ACCENT);
        expPlayPauseBtn.setOnClickListener(v -> { if (this.callback != null) this.callback.onPlayPause(); });

        expNextBtn = createControlButton(VectorIcons.ICON_NEXT, dp(40), dp(10), Color.WHITE, 0x1AFFFFFF);
        expNextBtn.setOnClickListener(v -> { if (this.callback != null) this.callback.onNext(); });

        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        prevLp.rightMargin = dp(24);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        nextLp.leftMargin = dp(24);

        ctrlRow.addView(expPrevBtn, prevLp);
        ctrlRow.addView(expPlayPauseBtn, playLp);
        ctrlRow.addView(expNextBtn, nextLp);
        mediaCard.addView(ctrlRow, ctrlRowLp);

        expandedSheet.addView(mediaCard, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // --- Pure Scrollable 3D iOS Overlapping Notification Deck ---
        notifSection = new LinearLayout(context);
        notifSection.setOrientation(LinearLayout.VERTICAL);
        notifSection.setClipChildren(false);
        LinearLayout.LayoutParams nsLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        nsLp.topMargin = dp(12);

        LinearLayout notifHeader = new LinearLayout(context);
        notifHeader.setOrientation(LinearLayout.HORIZONTAL);
        notifHeader.setGravity(Gravity.CENTER_VERTICAL);
        notifHeader.setPadding(dp(2), 0, dp(2), dp(8));

        notifHeaderTitle = new TextView(context);
        notifHeaderTitle.setText("Уведомления");
        notifHeaderTitle.setTextColor(0xB3FFFFFF);
        notifHeaderTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        notifHeaderTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        notifHeader.addView(notifHeaderTitle, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        notifClearBtn = new TextView(context);
        notifClearBtn.setText("Очистить");
        notifClearBtn.setTextColor(0xD9FFFFFF);
        notifClearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        notifClearBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setColor(0x22FFFFFF);
        clearBg.setCornerRadius(dp(12));
        notifClearBtn.setBackground(clearBg);
        notifClearBtn.setOnClickListener(v -> {
            if (this.callback != null) this.callback.onClearAllNotifications();
        });
        notifHeader.addView(notifClearBtn);
        notifSection.addView(notifHeader);

        notifDeckView = new StackedNotifDeckView(context, new StackedNotifDeckView.DeckCallback() {
            @Override
            public void onOpenItem(NotifItem item) {
                if (config.tapNotifOpensApp && NowBarOverlayView.this.callback != null) {
                    NowBarOverlayView.this.callback.onOpenNotification(item);
                }
            }

            @Override
            public void onDismissItem(String key) {
                if (NowBarOverlayView.this.callback != null) {
                    NowBarOverlayView.this.callback.onDismissNotification(key);
                }
            }

            @Override
            public void onActiveIndexChanged(int activeIndex, int totalCount) {
                updateNotifHeaderCounter(activeIndex, totalCount);
            }
        });
        notifSection.addView(notifDeckView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        notifEmptyHint = new TextView(context);
        notifEmptyHint.setText("Новых сообщений пока нет");
        notifEmptyHint.setTextColor(0x73FFFFFF);
        notifEmptyHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        notifEmptyHint.setGravity(Gravity.CENTER);
        notifEmptyHint.setPadding(0, dp(10), 0, dp(6));
        notifEmptyHint.setVisibility(GONE);
        notifSection.addView(notifEmptyHint);

        expandedSheet.addView(notifSection, nsLp);

        int sheetWidth = Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(28), dp(376));
        LayoutParams sheetLp = new LayoutParams(sheetWidth, LayoutParams.WRAP_CONTENT);
        sheetLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sheetLp.bottomMargin = dp(config.sheetBottomMarginDp);
        addView(expandedSheet, sheetLp);

        applyVisualTheme();
        refreshContent();
    }

    public boolean isTouchInsideCollapsedPill(float rawX, float rawY) {
        if (expanded || getVisibility() != VISIBLE || capsulePill.getVisibility() != VISIBLE) {
            return false;
        }
        capsulePill.getLocationOnScreen(tmpLoc);
        int pad = dp(6);
        return rawX >= (tmpLoc[0] - pad)
                && rawX <= (tmpLoc[0] + capsulePill.getWidth() + pad)
                && rawY >= (tmpLoc[1] - pad)
                && rawY <= (tmpLoc[1] + capsulePill.getHeight() + pad);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::applyVisualTheme);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (expanded && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
        if (previewMode) {
            hostVisible = true;
            setVisibility(VISIBLE);
            setConfig(config);
            refreshContent();
        }
    }

    public void setConfig(NowBarConfig newConfig) {
        if (newConfig == null) return;
        this.config = newConfig.copy();

        LayoutParams pillLp = (LayoutParams) capsulePill.getLayoutParams();
        pillLp.bottomMargin = previewMode ? dp(18) : dp(config.bottomMarginDp);
        capsulePill.setLayoutParams(pillLp);

        LayoutParams sheetLp = (LayoutParams) expandedSheet.getLayoutParams();
        sheetLp.bottomMargin = previewMode ? dp(14) : dp(Math.max(config.sheetBottomMarginDp, config.bottomMarginDp));
        expandedSheet.setLayoutParams(sheetLp);

        int titleMaxDp = config.capsuleWidthMode == NowBarConfig.WIDTH_COMPACT ? 72
                : (config.capsuleWidthMode == NowBarConfig.WIDTH_WIDE ? 148 : 106);
        pillTitleView.setMaxWidth(dp(titleMaxDp));
        pillNotifSnippet.setMaxWidth(dp(titleMaxDp + 24));

        int scrimAlpha255 = Math.max(0, Math.min(235, (config.scrimAlpha * 255) / 100));
        scrimView.setBackgroundColor(Color.argb(scrimAlpha255, 5, 7, 13));

        applyVisualTheme();
        refreshContent();
    }

    public void setMediaState(MediaState state) {
        this.mediaState = state != null ? state : new MediaState();
        applyVisualTheme();
        refreshContent();
    }

    public void setNotifications(List<NotifItem> items) {
        this.notifications = items != null ? new ArrayList<>(items) : new ArrayList<>();
        applyVisualTheme();
        refreshContent();
    }

    public void setHostVisible(boolean visible) {
        if (previewMode) {
            hostVisible = true;
            setVisibility(VISIBLE);
            return;
        }
        if (this.hostVisible == visible) return;
        this.hostVisible = visible;
        if (!visible) {
            if (expanded) collapseImmediate();
            mainHandler.removeCallbacks(progressTicker);
            pillEqualizer.setPlaying(false);
            expEqualizer.setPlaying(false);
            animate().cancel();
            setVisibility(GONE);
        } else {
            refreshContent();
            if (shouldRenderAnyContent()) {
                setAlpha(0f);
                setVisibility(VISIBLE);
                animate().alpha(1f).setDuration(200).setInterpolator(FLUID_SPRING).start();
            }
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void expand() {
        if (expanded) return;
        expanded = true;
        refreshContent();

        scrimView.setVisibility(VISIBLE);
        scrimView.setAlpha(0f);
        scrimView.animate().alpha(1f).setDuration(210).start();

        capsulePill.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(140)
                .withEndAction(() -> capsulePill.setVisibility(INVISIBLE))
                .start();

        expandedSheet.setVisibility(VISIBLE);
        expandedSheet.setAlpha(0f);
        expandedSheet.setScaleX(0.92f);
        expandedSheet.setScaleY(0.88f);
        expandedSheet.setTranslationY(dp(24));
        expandedSheet.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(FLUID_SPRING)
                .start();

        mainHandler.removeCallbacks(progressTicker);
        if (mediaState.hasSession && mediaState.isPlaying) {
            mainHandler.postDelayed(progressTicker, 300);
        }
    }

    public void collapse() {
        if (!expanded) return;
        expanded = false;
        notifDeckView.resetToTop();
        mainHandler.removeCallbacks(progressTicker);

        scrimView.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> scrimView.setVisibility(GONE))
                .start();

        expandedSheet.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.90f)
                .translationY(dp(20))
                .setDuration(190)
                .setInterpolator(FLUID_SPRING)
                .withEndAction(() -> {
                    expandedSheet.setVisibility(GONE);
                    expandedSheet.setTranslationY(0f);
                })
                .start();

        if (shouldRenderAnyContent()) {
            capsulePill.setVisibility(VISIBLE);
            capsulePill.setAlpha(0f);
            capsulePill.setScaleX(0.92f);
            capsulePill.setScaleY(0.92f);
            capsulePill.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(FLUID_SPRING)
                    .start();
        }
    }

    private void collapseImmediate() {
        expanded = false;
        notifDeckView.resetToTop();
        mainHandler.removeCallbacks(progressTicker);
        scrimView.setVisibility(GONE);
        scrimView.setAlpha(0f);
        expandedSheet.setVisibility(GONE);
        expandedSheet.setAlpha(0f);
        expandedSheet.setTranslationY(0f);
        capsulePill.setVisibility(VISIBLE);
        capsulePill.setAlpha(1f);
        capsulePill.setScaleX(1f);
        capsulePill.setScaleY(1f);
    }

    private boolean hasVisibleMedia() {
        if (!mediaState.hasSession) return false;
        return mediaState.isPlaying || config.showMediaWhenPaused;
    }

    private boolean shouldRenderAnyContent() {
        if (!config.enabled && !previewMode) return false;
        return hasVisibleMedia() || !notifications.isEmpty() || config.alwaysShowCapsule || previewMode;
    }

    private int resolveActiveAccent() {
        if (config.style == NowBarConfig.STYLE_MONOCHROME_BW) {
            return 0xFFE6E9F0;
        }
        if (!config.dynamicGlowColor) return CapsuleColors.DEFAULT_ACCENT;
        if (hasVisibleMedia()) return mediaState.accentColor;
        if (!notifications.isEmpty()) return notifications.get(0).accentColor;
        return CapsuleColors.DEFAULT_ACCENT;
    }

    private void applyVisualTheme() {
        int accent = resolveActiveAccent();
        boolean useSystemBlur = config.systemBlurEnabled && !previewMode;
        int baseAlphaPct = useSystemBlur
                ? Math.max(38, Math.min(82, config.capsuleAlpha - 14))
                : config.capsuleAlpha;
        int alpha255 = Math.max(96, Math.min(255, (baseAlphaPct * 255) / 100));
        int sheetAlpha255 = Math.min(242, alpha255 + (useSystemBlur ? 18 : 14));
        int pillRadiusPx = dp(config.cornerRadiusDp);
        int sheetRadiusPx = dp(Math.max(18, config.cornerRadiusDp + 4));

        Drawable pillGlass = new GlassCapsuleDrawable(config.style, accent, alpha255, pillRadiusPx, true);
        Drawable sheetGlass = new GlassCapsuleDrawable(config.style, accent, sheetAlpha255, sheetRadiusPx, false);

        SystemBlurHelper.applyBackdropBlur(capsulePill, pillGlass, useSystemBlur, config.blurRadiusPx, pillRadiusPx);
        SystemBlurHelper.applyBackdropBlur(expandedSheet, sheetGlass, useSystemBlur,
                Math.min(180, (int) (config.blurRadiusPx * 1.15f)), sheetRadiusPx);

        GradientDrawable mediaCardBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {
                    CapsuleColors.withAlpha(accent, config.style == NowBarConfig.STYLE_MONOCHROME_BW ? 26 : 42),
                    0x1AFFFFFF
                });
        mediaCardBg.setCornerRadius(dp(Math.max(14, config.cornerRadiusDp - 4)));
        mediaCardBg.setStroke(dp(1), CapsuleColors.withAlpha(accent, 65));
        mediaCard.setBackground(mediaCardBg);

        pillEqualizer.setBarColor(accent);
        expEqualizer.setBarColor(accent);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(accent);
        badgeBg.setCornerRadius(dp(10));
        pillNotifBadge.setBackground(badgeBg);

        GradientDrawable playBtnBg = new GradientDrawable();
        playBtnBg.setShape(GradientDrawable.OVAL);
        playBtnBg.setColor(accent);
        expPlayPauseBtn.setBackground(playBtnBg);

        try {
            expSeekBar.getProgressDrawable().setTint(accent);
            expSeekBar.getThumb().setTint(Color.WHITE);
        } catch (Throwable ignored) {}
    }

    private void refreshContent() {
        boolean showMedia = hasVisibleMedia();
        boolean showNotifs = !notifications.isEmpty();
        boolean hasAny = shouldRenderAnyContent();

        if (!previewMode) {
            if (!hostVisible || !hasAny) {
                if (expanded) collapseImmediate();
                setVisibility(GONE);
                return;
            }
            if (getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
                setAlpha(1f);
            }
        }

        // --- Collapsed Pill ---
        pillMediaGroup.setVisibility(showMedia ? VISIBLE : GONE);
        pillNotifGroup.setVisibility(showNotifs ? VISIBLE : GONE);
        pillDivider.setVisibility((showMedia && showNotifs) ? VISIBLE : GONE);
        pillEmptyLabel.setVisibility((!showMedia && !showNotifs) ? VISIBLE : GONE);

        if (showMedia) {
            if (mediaState.artwork != null && !mediaState.artwork.isRecycled()) {
                pillArtView.setImageBitmap(mediaState.artwork);
                pillArtView.setBackground(null);
            } else {
                pillArtView.setImageDrawable(VectorIcons.create(VectorIcons.ICON_MUSIC, Color.WHITE));
                GradientDrawable ph = new GradientDrawable();
                ph.setShape(GradientDrawable.OVAL);
                ph.setColor(CapsuleColors.withAlpha(resolveActiveAccent(), 95));
                pillArtView.setBackground(ph);
            }
            pillTitleView.setText(mediaState.title);
            pillEqualizer.setVisibility(config.showEqualizer ? VISIBLE : GONE);
            pillEqualizer.setPlaying(mediaState.isPlaying && hostVisible);

            pillQuickControls.setVisibility(config.showQuickMediaButtons ? VISIBLE : GONE);
            pillPlayPauseBtn.setImageDrawable(VectorIcons.create(
                    mediaState.isPlaying ? VectorIcons.ICON_PAUSE : VectorIcons.ICON_PLAY,
                    Color.WHITE));
        } else {
            pillEqualizer.setPlaying(false);
        }

        if (showNotifs) {
            pillNotifIconsRow.removeAllViews();
            int shownIcons = 0;
            List<String> seenPkgs = new ArrayList<>();
            for (NotifItem item : notifications) {
                if (shownIcons >= 2) break;
                if (item.packageName != null && seenPkgs.contains(item.packageName)) continue;
                seenPkgs.add(item.packageName);
                ImageView iv = new ImageView(getContext());
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                clipRounded(iv, dp(11));
                if (item.appIcon != null) {
                    iv.setImageDrawable(item.appIcon);
                } else {
                    iv.setImageDrawable(VectorIcons.create(VectorIcons.ICON_BELL, Color.WHITE));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(22), dp(22));
                if (shownIcons > 0) lp.leftMargin = -dp(5);
                pillNotifIconsRow.addView(iv, lp);
                shownIcons++;
            }

            if (!showMedia) {
                NotifItem top = notifications.get(0);
                String snip = !TextUtils.isEmpty(top.title) ? top.title : top.appName;
                pillNotifSnippet.setText(snip);
                pillNotifSnippet.setVisibility(VISIBLE);
            } else {
                pillNotifSnippet.setVisibility(GONE);
            }

            pillNotifBadge.setText(String.valueOf(notifications.size()));
            pillNotifBadge.setVisibility(VISIBLE);
        }

        // --- Expanded Sheet ---
        mediaCard.setVisibility(showMedia ? VISIBLE : GONE);
        if (showMedia) {
            if (mediaState.artwork != null && !mediaState.artwork.isRecycled()) {
                expArtView.setImageBitmap(mediaState.artwork);
                expArtView.setBackground(null);
            } else {
                expArtView.setImageDrawable(VectorIcons.create(VectorIcons.ICON_MUSIC, Color.WHITE));
                GradientDrawable ph = new GradientDrawable();
                ph.setCornerRadius(dp(14));
                ph.setColor(CapsuleColors.withAlpha(resolveActiveAccent(), 95));
                expArtView.setBackground(ph);
            }
            expTitleView.setText(mediaState.title);
            boolean multiPlayers = mediaState.sessionCount > 1;
            String baseArtist = TextUtils.isEmpty(mediaState.artist) ? "Сейчас играет" : mediaState.artist;
            if (multiPlayers && !TextUtils.isEmpty(mediaState.appLabel)) {
                expArtistView.setText(baseArtist + " • " + mediaState.appLabel);
            } else {
                expArtistView.setText(baseArtist);
            }
            expSwitchPlayerBtn.setVisibility(multiPlayers ? VISIBLE : GONE);
            if (multiPlayers) {
                expSwitchPlayerText.setText((mediaState.activeSessionIndex + 1) + "/" + mediaState.sessionCount);
            }
            expEqualizer.setVisibility(config.showEqualizer ? VISIBLE : GONE);
            expEqualizer.setPlaying(mediaState.isPlaying && expanded && hostVisible);
            expPlayPauseBtn.setImageDrawable(VectorIcons.create(
                    mediaState.isPlaying ? VectorIcons.ICON_PAUSE : VectorIcons.ICON_PLAY,
                    0xFF0A0D14));
            updateProgressViews();
        } else {
            expEqualizer.setPlaying(false);
        }

        notifDeckView.setVisibility(showNotifs ? VISIBLE : GONE);
        notifClearBtn.setVisibility(showNotifs ? VISIBLE : GONE);
        notifEmptyHint.setVisibility(showNotifs ? GONE : VISIBLE);
        notifDeckView.setItems(notifications);
        updateNotifHeaderCounter(notifDeckView.getActiveIndex(), notifications.size());
    }

    private void updateNotifHeaderCounter(int activeIndex, int totalCount) {
        if (totalCount <= 0) {
            notifHeaderTitle.setText("Уведомления");
        } else if (totalCount == 1) {
            notifHeaderTitle.setText("Уведомления • 1");
        } else {
            notifHeaderTitle.setText("Уведомления • " + (activeIndex + 1) + " / " + totalCount);
        }
    }

    private void updateProgressViews() {
        long dur = mediaState.durationMs;
        long pos = mediaState.getLivePositionMs();
        expPosText.setText(MediaState.formatTime(pos));
        expDurText.setText(dur > 0 ? MediaState.formatTime(dur) : "--:--");
        if (dur > 0) {
            int p = (int) Math.max(0, Math.min(1000, (pos * 1000L) / dur));
            expSeekBar.setProgress(p);
            expSeekBar.setEnabled(true);
        } else {
            expSeekBar.setProgress(0);
            expSeekBar.setEnabled(false);
        }
    }

    private ImageView createControlButton(int iconType, int sizePx, int padPx, int iconColor, int bgColor) {
        ImageView btn = new ImageView(getContext());
        btn.setImageDrawable(VectorIcons.create(iconType, iconColor));
        btn.setPadding(padPx, padPx, padPx, padPx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(bgColor);
        btn.setBackground(bg);
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class GlassCapsuleDrawable extends Drawable {
        private final int style;
        private final int accentColor;
        private final int alpha255;
        private final float cornerRadius;
        private final boolean isPill;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF boundsF = new RectF();

        GlassCapsuleDrawable(int style, int accentColor, int alpha255, float cornerRadius, boolean isPill) {
            this.style = style;
            this.accentColor = accentColor;
            this.alpha255 = alpha255;
            this.cornerRadius = cornerRadius;
            this.isPill = isPill;
            borderPaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            float inset = 2f;
            boundsF.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
            if (boundsF.width() <= 0 || boundsF.height() <= 0) return;

            if (style == NowBarConfig.STYLE_ONEUI_GLOW) {
                int baseDark = CapsuleColors.withAlpha(0xFF10131C, alpha255);
                int tintedDark = CapsuleColors.withAlpha(
                        CapsuleColors.blend(0xFF121622, accentColor, isPill ? 0.24f : 0.14f),
                        alpha255);
                fillPaint.setShader(new LinearGradient(
                        boundsF.left, boundsF.top, boundsF.right, boundsF.bottom,
                        tintedDark, baseDark, Shader.TileMode.CLAMP));

                borderPaint.setStrokeWidth(isPill ? 3.2f : 2.6f);
                borderPaint.setShader(new LinearGradient(
                        boundsF.left, boundsF.top, boundsF.right, boundsF.bottom,
                        new int[] {
                            CapsuleColors.withAlpha(accentColor, 200),
                            0x4DFFFFFF,
                            CapsuleColors.withAlpha(accentColor, 120)
                        },
                        new float[] {0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
            } else if (style == NowBarConfig.STYLE_COLOROS_GLASS) {
                int topGlass = CapsuleColors.withAlpha(0xFF202533, alpha255);
                int botGlass = CapsuleColors.withAlpha(0xFF12151E, alpha255);
                fillPaint.setShader(new LinearGradient(
                        boundsF.left, boundsF.top, boundsF.left, boundsF.bottom,
                        topGlass, botGlass, Shader.TileMode.CLAMP));

                borderPaint.setStrokeWidth(2.6f);
                borderPaint.setShader(new LinearGradient(
                        boundsF.left, boundsF.top, boundsF.left, boundsF.bottom,
                        0x66FFFFFF, 0x18FFFFFF, Shader.TileMode.CLAMP));
            } else if (style == NowBarConfig.STYLE_MONOCHROME_BW) {
                int topMono = CapsuleColors.withAlpha(0xFF1A1A1D, alpha255);
                int botMono = CapsuleColors.withAlpha(0xFF0A0A0C, alpha255);
                fillPaint.setShader(new LinearGradient(
                        boundsF.left, boundsF.top, boundsF.right, boundsF.bottom,
                        topMono, botMono, Shader.TileMode.CLAMP));

                borderPaint.setStrokeWidth(2.8f);
                borderPaint.setShader(new LinearGradient(
                        boundsF.left, boundsF.top, boundsF.right, boundsF.bottom,
                        0x80FFFFFF, 0x22FFFFFF, Shader.TileMode.CLAMP));
            } else {
                fillPaint.setShader(null);
                fillPaint.setColor(CapsuleColors.withAlpha(0xFF0D0F15, alpha255));
                borderPaint.setShader(null);
                borderPaint.setStrokeWidth(2.2f);
                borderPaint.setColor(0x2EFFFFFF);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, fillPaint);
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, borderPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
