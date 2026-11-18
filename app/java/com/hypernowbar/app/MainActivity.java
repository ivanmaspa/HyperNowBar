package com.hypernowbar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hypernowbar.config.DeviceLayoutAdapter;
import com.hypernowbar.config.NowBarConfig;
import com.hypernowbar.config.NowBarPresets;
import com.hypernowbar.hook.NowBarController;
import com.hypernowbar.model.MediaState;
import com.hypernowbar.model.NotifItem;
import com.hypernowbar.ui.CapsuleColors;
import com.hypernowbar.ui.NowBarOverlayView;
import com.hypernowbar.ui.VectorIcons;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration, Presets & Interactive Live Preview UI for HyperNowBar 1.G.1.
 */
public final class MainActivity extends Activity {

    private SharedPreferences prefs;
    private NowBarConfig config;
    private NowBarOverlayView previewBar;
    private MediaState mockMedia;
    private List<NotifItem> mockNotifs;

    private LinearLayout controlsContainer;
    private LinearLayout presetsRow;
    private TextView previewHintTv;
    private TextView liveDiagTv;
    private BroadcastReceiver pongReceiver;

    private TextView[] styleChips;
    private TextView[] widthChips;
    private TextView[] notifSourceChips;
    private TextView[] animStyleChips;
    private View[] accentSwatches;

    public static boolean isModuleActive() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = NowBarConfig.openAppPrefs(this);
        config = NowBarConfig.fromPrefs(prefs);
        initMockPreviewData();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0A0C12);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int baseH = dp(18);
        final int baseTop = dp(20);
        final int baseBot = dp(38);
        root.setPadding(baseH, baseTop, baseH, baseBot);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets sys = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(baseH + sys.left, baseTop + sys.top, baseH + sys.right, baseBot + sys.bottom);
            return insets;
        });

        // 1. Header + Status Badge
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleTv = new TextView(this);
        titleTv.setText("HyperNowBar 1.G.2");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(titleTv);

        TextView subTv = new TextView(this);
        subTv.setText(DeviceLayoutAdapter.getDeviceBanner());
        subTv.setTextColor(0x99FFFFFF);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        titleCol.addView(subTv);
        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean active = isModuleActive();
        TextView statusBadge = new TextView(this);
        statusBadge.setText(active ? "Релиз 1.G.2" : "SystemUI");
        statusBadge.setTextColor(active ? 0xFF071B13 : 0xFF101828);
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        statusBadge.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable sbBg = new GradientDrawable();
        sbBg.setColor(active ? 0xFF4AE3B5 : 0xFF8CA6FF);
        sbBg.setCornerRadius(dp(14));
        statusBadge.setBackground(sbBg);
        headerRow.addView(statusBadge);
        root.addView(headerRow);

        // Live SystemUI connection banner
        liveDiagTv = new TextView(this);
        liveDiagTv.setText("Проверка связи с SystemUI...");
        liveDiagTv.setTextColor(0xFFD6E0FF);
        liveDiagTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        liveDiagTv.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable hcBg = new GradientDrawable();
        hcBg.setColor(0x1F6C8CFF);
        hcBg.setCornerRadius(dp(16));
        hcBg.setStroke(dp(1), 0x4D6C8CFF);
        liveDiagTv.setBackground(hcBg);
        LinearLayout.LayoutParams hcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hcLp.topMargin = dp(12);
        root.addView(liveDiagTv, hcLp);

        // 2. Interactive Live Preview Stage
        FrameLayout previewStage = buildPreviewStage();
        LinearLayout.LayoutParams psLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(428));
        psLp.topMargin = dp(14);
        root.addView(previewStage, psLp);

        // 3. Presets Strip (Built-in + Custom User Presets)
        LinearLayout presetsHeader = new LinearLayout(this);
        presetsHeader.setOrientation(LinearLayout.HORIZONTAL);
        presetsHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams phLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        phLp.topMargin = dp(18);
        phLp.bottomMargin = dp(8);

        TextView phTitle = new TextView(this);
        phTitle.setText("Пресеты оформления (удерживайте свой для удаления)");
        phTitle.setTextColor(0xB3FFFFFF);
        phTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        phTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        presetsHeader.addView(phTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(presetsHeader, phLp);

        HorizontalScrollView presetsScroll = new HorizontalScrollView(this);
        presetsScroll.setHorizontalScrollBarEnabled(false);
        presetsRow = new LinearLayout(this);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        presetsRow.setGravity(Gravity.CENTER_VERTICAL);
        presetsScroll.addView(presetsRow);
        root.addView(presetsScroll);
        rebuildPresetsRow();

        // 4. Dynamic Settings Controls Container (rebuilt cleanly on preset apply)
        controlsContainer = new LinearLayout(this);
        controlsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(controlsContainer);
        rebuildControls();

        // 5. Restart SystemUI Button
        TextView restartBtn = new TextView(this);
        restartBtn.setText("Перезапустить SystemUI");
        restartBtn.setTextColor(Color.WHITE);
        restartBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        restartBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        restartBtn.setGravity(Gravity.CENTER);
        restartBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable rbBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xFF4F6BFF, 0xFF7A5CFF});
        rbBg.setCornerRadius(dp(18));
        restartBtn.setBackground(rbBg);
        restartBtn.setOnClickListener(v -> restartSystemUi());
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rbLp.topMargin = dp(20);
        root.addView(restartBtn, rbLp);

        scroll.addView(root);
        setContentView(scroll);
        registerPongReceiver();
    }

    private void rebuildPresetsRow() {
        presetsRow.removeAllViews();

        // "+ Создать" button
        TextView addBtn = new TextView(this);
        addBtn.setText("+ Создать");
        addBtn.setTextColor(0xFF0A141E);
        addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        addBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        addBtn.setPadding(dp(13), dp(9), dp(13), dp(9));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(0xFF4AE3B5);
        addBg.setCornerRadius(dp(16));
        addBtn.setBackground(addBg);
        addBtn.setOnClickListener(v -> showCreatePresetDialog());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.rightMargin = dp(8);
        presetsRow.addView(addBtn, addLp);

        List<NowBarPresets.PresetItem> all = new ArrayList<>();
        all.addAll(NowBarPresets.loadUserPresets(prefs));
        all.addAll(NowBarPresets.getBuiltInPresets());

        for (NowBarPresets.PresetItem item : all) {
            TextView chip = new TextView(this);
            chip.setText(item.isCustom ? ("★ " + item.name) : item.name);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            chip.setPadding(dp(13), dp(9), dp(13), dp(9));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(item.isCustom ? 0xFF222B4A : 0xFF171C2B);
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), item.isCustom ? 0xFF7B9AFF : 0x2BFFFFFF);
            chip.setBackground(bg);

            chip.setOnClickListener(v -> {
                config.applySerializedPreset(item.serializedData);
                rebuildControls();
                persistAndBroadcast();
                Toast.makeText(this, "Применён пресет: " + item.name, Toast.LENGTH_SHORT).show();
            });

            if (item.isCustom) {
                chip.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Удалить пресет?")
                            .setMessage("Удалить сохранённый пресет «" + item.name + "»?")
                            .setPositiveButton("Удалить", (d, w) -> {
                                NowBarPresets.deleteUserPreset(prefs, item.name);
                                rebuildPresetsRow();
                                Toast.makeText(this, "Пресет удалён", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                    return true;
                });
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            presetsRow.addView(chip, lp);
        }
    }

    private void showCreatePresetDialog() {
        EditText input = new EditText(this);
        input.setHint("Например: Ночной неон");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int pad = dp(18);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Создать свой пресет")
                .setMessage("Сохранятся текущие цвета, стекло, блюр, анимации, размеры и режим отображения.")
                .setView(input)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Введите название пресета", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    NowBarPresets.saveUserPreset(prefs, name, config);
                    rebuildPresetsRow();
                    Toast.makeText(this, "Пресет «" + name + "» сохранён", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void rebuildControls() {
        controlsContainer.removeAllViews();
        updatePreviewHint();

        // === BETA COMPACT ADAPTIVE MODE ===
        controlsContainer.addView(sectionHeader("Экспериментальный режим"));
        LinearLayout betaCard = createAccentCard(0xFF191D33, 0x667A6BFF);
        addSwitchRow(betaCard,
                "Компактный адаптивный режим (Бета)",
                "Меньше плеер, компактнее стопка, без внешней рамки меню и затемнения. Сворачивается в бар при выключении и включении экрана",
                config.betaCompactMode,
                checked -> {
                    config.betaCompactMode = checked;
                    updatePreviewHint();
                    persistAndBroadcast();
                });
        controlsContainer.addView(betaCard);

        // === NOTIFICATIONS SOURCE & LIFECYCLE ===
        controlsContainer.addView(sectionHeader("Источник и очистка уведомлений"));
        LinearLayout notifCard = createCard();

        TextView srcLbl = new TextView(this);
        srcLbl.setText("Какие уведомления показывать");
        srcLbl.setTextColor(Color.WHITE);
        srcLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        srcLbl.setPadding(dp(2), dp(6), dp(2), dp(8));
        notifCard.addView(srcLbl);

        LinearLayout srcRow = new LinearLayout(this);
        srcRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] srcNames = {"Все из шторки", "После выкл. экрана"};
        notifSourceChips = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView chip = makeChoiceChip(srcNames[i], 12f);
            chip.setOnClickListener(v -> {
                config.notifSourceMode = idx;
                updateNotifSourceChips();
                persistAndBroadcast();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            srcRow.addView(chip, lp);
            notifSourceChips[i] = chip;
        }
        updateNotifSourceChips();
        notifCard.addView(srcRow);

        TextView srcHint = new TextView(this);
        srcHint.setText("«Все из шторки» копирует текущие уведомления; «После выкл. экрана» показывает только новые, пришедшие после блокировки.");
        srcHint.setTextColor(0x80FFFFFF);
        srcHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        srcHint.setPadding(dp(2), dp(6), dp(2), dp(6));
        notifCard.addView(srcHint);

        addDivider(notifCard);
        addSwitchRow(notifCard,
                "Очищать уведомления после разблокировки",
                "Вкл: пропадают из бара после разблокировки • Выкл: остаются без очистки",
                config.clearNotifsAfterUnlock,
                checked -> { config.clearNotifsAfterUnlock = checked; persistAndBroadcast(); });
        controlsContainer.addView(notifCard);

        // === VISUAL STYLE & iOS LIQUID GLASS ===
        controlsContainer.addView(sectionHeader("Стиль, стекло и акцентный цвет"));
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] styleNames = {"One UI 7", "ColorOS", "Matte", "Ч/Б Моно"};
        styleChips = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int styleIdx = i;
            TextView chip = makeChoiceChip(styleNames[i], 11.5f);
            chip.setOnClickListener(v -> {
                config.style = styleIdx;
                updateStyleChips();
                persistAndBroadcast();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) clp.leftMargin = dp(6);
            styleRow.addView(chip, clp);
            styleChips[i] = chip;
        }
        updateStyleChips();
        controlsContainer.addView(styleRow);

        LinearLayout glassCard = createCard();
        LinearLayout.LayoutParams gcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gcLp.topMargin = dp(10);

        addSwitchRow(glassCard,
                "Режим жидкого стекла iOS (Liquid Glass)",
                "По умолчанию выключен. Полупрозрачные бликующие линзы, ледяной кант и глубина как в iOS",
                config.iosLiquidGlass,
                checked -> { config.iosLiquidGlass = checked; persistAndBroadcast(); });
        addDivider(glassCard);

        addSwitchRow(glassCard,
                "Подсветка в цвет обложки трека",
                "Автоматически подбирать цвет контура по обложке альбома",
                config.dynamicGlowColor,
                checked -> { config.dynamicGlowColor = checked; persistAndBroadcast(); });
        addDivider(glassCard);

        TextView accentLbl = new TextView(this);
        accentLbl.setText("Ручной акцентный цвет (когда обложки нет или автоцвет выкл.)");
        accentLbl.setTextColor(0xCCFFFFFF);
        accentLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        accentLbl.setPadding(dp(2), dp(6), dp(2), dp(8));
        glassCard.addView(accentLbl);

        LinearLayout swatchRow = new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        swatchRow.setPadding(dp(2), dp(2), dp(2), dp(6));
        accentSwatches = new View[NowBarConfig.ACCENT_SWATCHES.length];
        for (int i = 0; i < NowBarConfig.ACCENT_SWATCHES.length; i++) {
            final int col = NowBarConfig.ACCENT_SWATCHES[i];
            View dot = new View(this);
            dot.setOnClickListener(v -> {
                config.manualAccentColor = col;
                updateAccentSwatches();
                persistAndBroadcast();
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(30), dp(30));
            if (i > 0) dlp.leftMargin = dp(8);
            swatchRow.addView(dot, dlp);
            accentSwatches[i] = dot;
        }
        updateAccentSwatches();
        glassCard.addView(swatchRow);
        controlsContainer.addView(glassCard, gcLp);

        // === ANIMATIONS ===
        controlsContainer.addView(sectionHeader("Анимации"));
        LinearLayout animCard = createCard();

        LinearLayout animRow = new LinearLayout(this);
        animRow.setOrientation(LinearLayout.HORIZONTAL);
        animRow.setPadding(0, dp(4), 0, dp(4));
        String[] animNames = {"Пружина iOS", "HyperOS", "Выезд", "Мгновенно"};
        animStyleChips = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView chip = makeChoiceChip(animNames[i], 11f);
            chip.setOnClickListener(v -> {
                config.animStyle = idx;
                updateAnimStyleChips();
                persistAndBroadcast();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            animRow.addView(chip, lp);
            animStyleChips[i] = chip;
        }
        updateAnimStyleChips();
        animCard.addView(animRow);
        addDivider(animCard);

        addSliderRow(animCard,
                "Скорость анимаций (меньше = быстрее)",
                50, 180, config.animSpeedPct, "%",
                val -> { config.animSpeedPct = val; persistAndBroadcast(); });
        controlsContainer.addView(animCard);

        // === BLUR & BACKDROP ===
        controlsContainer.addView(sectionHeader("Блюр и размытие фона"));
        LinearLayout blurCard = createCard();

        addSwitchRow(blurCard,
                "Системный GPU-блюр капсулы и карточек",
                "Аппаратное размытие HyperOS MiBackgroundBlur",
                config.systemBlurEnabled,
                checked -> { config.systemBlurEnabled = checked; persistAndBroadcast(); });
        addDivider(blurCard);

        addSliderRow(blurCard,
                "Сила блюра капсулы и меню",
                20, 160, config.blurRadiusPx, " px",
                val -> { config.blurRadiusPx = val; persistAndBroadcast(); });
        addDivider(blurCard);

        addSwitchRow(blurCard,
                "Красивый задний блюр при открытом меню",
                "Мягкое полноэкранное размытие обоев и часов за раскрытым меню уведомлений",
                config.backdropBlurEnabled,
                checked -> { config.backdropBlurEnabled = checked; persistAndBroadcast(); });
        addDivider(blurCard);

        addSliderRow(blurCard,
                "Сила заднего блюра фона",
                0, 160, config.backdropBlurRadiusPx, " px",
                val -> { config.backdropBlurRadiusPx = val; persistAndBroadcast(); });
        addDivider(blurCard);

        addSliderRow(blurCard,
                "Затемнение фона при раскрытии меню",
                0, 90, config.scrimAlpha, "%",
                val -> { config.scrimAlpha = val; persistAndBroadcast(); });
        addDivider(blurCard);

        addSliderRow(blurCard,
                "Плотность стекла капсулы",
                35, 100, config.capsuleAlpha, "%",
                val -> { config.capsuleAlpha = val; persistAndBroadcast(); });
        controlsContainer.addView(blurCard);

        // === GEOMETRY & DIMENSIONS ===
        controlsContainer.addView(sectionHeader("Адаптация под экран и геометрия"));
        LinearLayout widthRow = new LinearLayout(this);
        widthRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] widthNames = {"Компактная", "Стандарт", "Широкая"};
        widthChips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int wIdx = i;
            TextView chip = makeChoiceChip(widthNames[i], 12f);
            chip.setOnClickListener(v -> {
                config.capsuleWidthMode = wIdx;
                updateWidthChips();
                persistAndBroadcast();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) clp.leftMargin = dp(8);
            widthRow.addView(chip, clp);
            widthChips[i] = chip;
        }
        updateWidthChips();
        controlsContainer.addView(widthRow);

        LinearLayout geomCard = createCard();
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = dp(10);

        DeviceLayoutAdapter.DeviceProfile devProfile = DeviceLayoutAdapter.detect(this);
        int shownCapsuleDp = config.autoAdaptPosition ? devProfile.recommendedCapsuleBottomDp : config.bottomMarginDp;
        int shownSheetDp = config.autoAdaptPosition ? devProfile.recommendedSheetBottomDp : config.sheetBottomMarginDp;

        addSwitchRow(geomCard,
                "Авто-высота под телефон и сканер",
                devProfile.summary + " (телефоны без подэкранного отпечатка располагаются ниже)",
                config.autoAdaptPosition,
                checked -> {
                    config.autoAdaptPosition = checked;
                    if (checked) {
                        config.bottomMarginDp = devProfile.recommendedCapsuleBottomDp;
                        config.sheetBottomMarginDp = devProfile.recommendedSheetBottomDp;
                        rebuildControls();
                    }
                    persistAndBroadcast();
                });
        addDivider(geomCard);

        addSliderRow(geomCard,
                "Высота капсулы от низа (ручная подстройка)",
                16, 340, shownCapsuleDp, " dp",
                val -> {
                    config.autoAdaptPosition = false;
                    config.bottomMarginDp = val;
                    persistAndBroadcast();
                });
        addDivider(geomCard);

        addSliderRow(geomCard,
                "Высота открытого меню от низа (ручная подстройка)",
                24, 360, shownSheetDp, " dp",
                val -> {
                    config.autoAdaptPosition = false;
                    config.sheetBottomMarginDp = val;
                    persistAndBroadcast();
                });
        addDivider(geomCard);

        addSliderRow(geomCard,
                "Скругление углов капсулы",
                10, 28, config.cornerRadiusDp, " dp",
                val -> { config.cornerRadiusDp = val; persistAndBroadcast(); });
        addDivider(geomCard);

        addSliderRow(geomCard,
                "Толщина светящегося контура",
                0, 4, config.borderWidthDp, " dp",
                val -> { config.borderWidthDp = val; persistAndBroadcast(); });
        addDivider(geomCard);

        addSliderRow(geomCard,
                "Размер шрифта капсулы",
                11, 15, config.pillFontSizeSp, " sp",
                val -> { config.pillFontSizeSp = val; persistAndBroadcast(); });
        controlsContainer.addView(geomCard, gLp);

        // === CONTROLS & BEHAVIOR ===
        controlsContainer.addView(sectionHeader("Управление и поведение"));
        LinearLayout actionsCard = createCard();

        addSwitchRow(actionsCard,
                "Включить HyperNowBar",
                "Отображать нижнюю капсулу на экране блокировки",
                config.enabled,
                checked -> { config.enabled = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Чистый экран блокировки",
                "Скрывать стандартные уведомления и удерживать часы HyperOS растянутыми",
                config.hideStockNotifications,
                checked -> { config.hideStockNotifications = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Быстрые кнопки на свёрнутой капсуле",
                "Пауза/старт и следующий трек в 1 тап прямо на капсуле",
                config.showQuickMediaButtons,
                checked -> { config.showQuickMediaButtons = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Переход в плеер по нажатию",
                "Тап по обложке/названию трека открывает музыкальное приложение",
                config.tapMediaOpensApp,
                checked -> { config.tapMediaOpensApp = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Переход в чат по нажатию на уведомление",
                "Тап по передней карточке разблокирует экран и открывает приложение",
                config.tapNotifOpensApp,
                checked -> { config.tapNotifOpensApp = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Скрывать при вводе пароля (PIN)",
                "Мгновенно прятать капсулу на экране ввода кода и возвращать при отмене",
                config.hideOnBouncer,
                checked -> { config.hideOnBouncer = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Анимация эквалайзера",
                "Живая звуковая волна во время проигрывания музыки",
                config.showEqualizer,
                checked -> { config.showEqualizer = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Показывать плеер на паузе",
                "Оставлять трек в капсуле для быстрого возобновления",
                config.showMediaWhenPaused,
                checked -> { config.showMediaWhenPaused = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Скрывать на Always-On Display",
                "Автоматически прятать на AOD (защита OLED)",
                config.hideOnAod,
                checked -> { config.hideOnAod = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Показывать пустую капсулу",
                "Отображать мини-капсулу даже без музыки и уведомлений",
                config.alwaysShowCapsule,
                checked -> { config.alwaysShowCapsule = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Скрыть иконку с рабочего стола",
                "Убирает ярлык из меню приложений — настройки всегда открываются кнопкой в Vector / LSPosed",
                isLauncherIconHidden(),
                checked -> {
                    config.hideLauncherIcon = checked;
                    setLauncherIconHidden(checked);
                    persistAndBroadcast();
                });

        controlsContainer.addView(actionsCard);
    }

    private void updatePreviewHint() {
        if (previewHintTv == null) return;
        if (config.betaCompactMode) {
            previewHintTv.setText("Бета-компакт • тап по часам имитирует выкл/вкл экрана");
        } else {
            previewHintTv.setText("Предпросмотр • тап по капсуле раскрывает меню");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            sendBroadcast(new Intent(NowBarController.ACTION_PING));
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pongReceiver != null) {
            try {
                unregisterReceiver(pongReceiver);
            } catch (Throwable ignored) {}
        }
    }

    private void registerPongReceiver() {
        pongReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent == null || liveDiagTv == null) return;
                String win = intent.getStringExtra("window");
                int tracked = intent.getIntExtra("tracked", 0);
                int notifs = intent.getIntExtra("notifs", 0);
                boolean hasStarter = intent.getBooleanExtra("hasStarter", false);
                String media = intent.getStringExtra("media");
                String devProf = intent.getStringExtra("deviceProfile");
                String mediaPart = (media != null && !media.isEmpty()) ? (" • Плеер: " + media) : "";
                String devPart = (devProf != null && !devProf.isEmpty()) ? ("\n" + devProf) : "";
                liveDiagTv.setText("SystemUI подключён (" + win + ") • Уведомлений: " + notifs
                        + " • Переходы: " + (hasStarter ? "OK" : "резерв")
                        + " • Слоёв: " + tracked + mediaPart + devPart);
                liveDiagTv.setTextColor(0xFFB0F2DA);
                GradientDrawable okBg = new GradientDrawable();
                okBg.setColor(0x1F4AE3B5);
                okBg.setCornerRadius(dp(16));
                okBg.setStroke(dp(1), 0x554AE3B5);
                liveDiagTv.setBackground(okBg);
            }
        };
        IntentFilter f = new IntentFilter(NowBarController.ACTION_PONG);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(pongReceiver, f, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(pongReceiver, f);
            }
        } catch (Throwable ignored) {}
    }

    private FrameLayout buildPreviewStage() {
        FrameLayout stage = new FrameLayout(this);
        GradientDrawable wallBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF151C36, 0xFF1F163B, 0xFF0C101A});
        wallBg.setCornerRadius(dp(26));
        wallBg.setStroke(dp(1), 0x26FFFFFF);
        stage.setBackground(wallBg);
        stage.setClipToOutline(true);

        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);
        clockCol.setGravity(Gravity.CENTER_HORIZONTAL);
        clockCol.setPadding(0, dp(18), 0, dp(8));
        clockCol.setOnClickListener(v -> {
            if (previewBar != null && previewBar.isExpanded()) {
                previewBar.onScreenTurnedOff();
            }
        });

        previewHintTv = new TextView(this);
        previewHintTv.setTextColor(0x99FFFFFF);
        previewHintTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        clockCol.addView(previewHintTv);

        TextView timeTv = new TextView(this);
        timeTv.setText("16:45");
        timeTv.setTextColor(0xEEFFFFFF);
        timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f);
        timeTv.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        clockCol.addView(timeTv);

        stage.addView(clockCol, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        previewBar = new NowBarOverlayView(this, new NowBarOverlayView.ActionCallback() {
            @Override
            public void onPlayPause() {
                mockMedia.isPlaying = !mockMedia.isPlaying;
                previewBar.setMediaState(mockMedia);
            }
            @Override
            public void onNext() {
                mockMedia.title = "Midnight City".equals(mockMedia.title) ? "Starboy" : "Midnight City";
                mockMedia.artist = "Starboy".equals(mockMedia.title) ? "The Weeknd" : "M83";
                mockMedia.artwork = createDemoArtwork("Starboy".equals(mockMedia.title) ? 0xFFE53935 : 0xFF5C6BC0);
                mockMedia.accentColor = CapsuleColors.extractVibrantAccent(mockMedia.artwork);
                previewBar.setMediaState(mockMedia);
            }
            @Override public void onPrevious() { onNext(); }
            @Override
            public void onSeekTo(long positionMs) {
                mockMedia.positionMs = positionMs;
                previewBar.setMediaState(mockMedia);
            }
            @Override
            public void onSwitchPlayer() {
                mockMedia.activeSessionIndex = (mockMedia.activeSessionIndex + 1) % 2;
                if (mockMedia.activeSessionIndex == 0) {
                    mockMedia.appLabel = "Яндекс Музыка";
                    mockMedia.title = "Midnight City";
                    mockMedia.artist = "M83";
                    mockMedia.artwork = createDemoArtwork(0xFF5C6BC0);
                } else {
                    mockMedia.appLabel = "Spotify";
                    mockMedia.title = "Starboy";
                    mockMedia.artist = "The Weeknd";
                    mockMedia.artwork = createDemoArtwork(0xFF1DB954);
                }
                mockMedia.accentColor = CapsuleColors.extractVibrantAccent(mockMedia.artwork);
                previewBar.setMediaState(mockMedia);
            }
            @Override
            public void onOpenMediaApp() {
                Toast.makeText(MainActivity.this, "Открытие плеера (на локскрине запросит разблокировку)", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onOpenNotification(NotifItem item) {
                Toast.makeText(MainActivity.this, "Переход в: " + item.title, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onDismissNotification(String key) {
                for (int i = mockNotifs.size() - 1; i >= 0; i--) {
                    if (key.equals(mockNotifs.get(i).key)) mockNotifs.remove(i);
                }
                previewBar.setNotifications(mockNotifs);
            }
            @Override
            public void onClearAllNotifications() {
                mockNotifs.clear();
                previewBar.setNotifications(mockNotifs);
            }
        });
        previewBar.setPreviewMode(true);
        previewBar.setConfig(config);
        previewBar.setMediaState(mockMedia);
        previewBar.setNotifications(mockNotifs);

        stage.addView(previewBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return stage;
    }

    private void initMockPreviewData() {
        mockMedia = new MediaState();
        mockMedia.hasSession = true;
        mockMedia.isPlaying = true;
        mockMedia.title = "Midnight City";
        mockMedia.artist = "M83";
        mockMedia.appLabel = "Яндекс Музыка";
        mockMedia.sessionCount = 2;
        mockMedia.activeSessionIndex = 0;
        mockMedia.durationMs = 243_000L;
        mockMedia.positionMs = 86_000L;
        mockMedia.artwork = createDemoArtwork(0xFF5C6BC0);
        mockMedia.accentColor = CapsuleColors.extractVibrantAccent(mockMedia.artwork);

        mockNotifs = new ArrayList<>();
        String[][] demos = {
            {"AyuGram", "Артём", "Релиз 1.G.2 — адаптация под любые телефоны и сканеры!"},
            {"VK", "Максим", "Задний блюр и компактный Бета-режим выглядят топ"},
            {"Telegram", "Дима", "Скинул новый трек, оцени в плеере"},
            {"WhatsApp", "Саша", "Встречаемся в 19:00 у центра"}
        };
        for (int i = 0; i < demos.length; i++) {
            NotifItem n = new NotifItem();
            n.key = "demo_" + i;
            n.packageName = "demo.pkg." + i;
            n.appName = demos[i][0];
            n.appIcon = VectorIcons.create(VectorIcons.ICON_BELL, i == 0 ? 0xFF4FC3F7 : 0xFF9FA8DA);
            n.title = demos[i][1];
            n.text = demos[i][2];
            n.timestampMs = System.currentTimeMillis() - (i + 1) * 60_000L;
            n.accentColor = 0xFF29B6F6;
            n.clearable = true;
            mockNotifs.add(n);
        }
    }

    private Bitmap createDemoArtwork(int primaryColor) {
        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 96, 96, primaryColor, 0xFF1A1E3A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 96, 96, p);
        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setColor(0x55FFFFFF);
        c.drawCircle(68, 28, 22, circle);
        return bmp;
    }

    private TextView makeChoiceChip(String label, float textSizeSp) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(6), dp(10), dp(6), dp(10));
        return chip;
    }

    private void styleChoiceGroup(TextView[] chips, int selectedIndex) {
        if (chips == null) return;
        for (int i = 0; i < chips.length; i++) {
            boolean selected = (selectedIndex == i);
            TextView chip = chips[i];
            chip.setTextColor(selected ? Color.WHITE : 0x99FFFFFF);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(selected ? 0xFF2A3661 : 0xFF141824);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), selected ? 0xFF6C8CFF : 0x22FFFFFF);
            chip.setBackground(bg);
        }
    }

    private void updateStyleChips() { styleChoiceGroup(styleChips, config.style); }
    private void updateWidthChips() { styleChoiceGroup(widthChips, config.capsuleWidthMode); }
    private void updateNotifSourceChips() { styleChoiceGroup(notifSourceChips, config.notifSourceMode); }
    private void updateAnimStyleChips() { styleChoiceGroup(animStyleChips, config.animStyle); }

    private void updateAccentSwatches() {
        if (accentSwatches == null) return;
        for (int i = 0; i < accentSwatches.length; i++) {
            int col = NowBarConfig.ACCENT_SWATCHES[i];
            boolean sel = (config.manualAccentColor == col);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(col);
            d.setStroke(dp(sel ? 3 : 1), sel ? Color.WHITE : 0x44FFFFFF);
            accentSwatches[i].setBackground(d);
            accentSwatches[i].setScaleX(sel ? 1.12f : 1.0f);
            accentSwatches[i].setScaleY(sel ? 1.12f : 1.0f);
        }
    }

    private void persistAndBroadcast() {
        config.saveToPrefs(prefs);
        if (previewBar != null) {
            previewBar.setConfig(config);
        }
        try {
            sendBroadcast(config.toBroadcastIntent());
        } catch (Throwable ignored) {}
    }

    private boolean isLauncherIconHidden() {
        try {
            ComponentName cn = new ComponentName(this, "com.hypernowbar.app.LauncherAlias");
            int state = getPackageManager().getComponentEnabledSetting(cn);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable ignored) {
            return config.hideLauncherIcon;
        }
    }

    private void setLauncherIconHidden(boolean hide) {
        try {
            ComponentName cn = new ComponentName(this, "com.hypernowbar.app.LauncherAlias");
            int newState = hide
                    ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            getPackageManager().setComponentEnabledSetting(cn, newState, PackageManager.DONT_KILL_APP);
            Toast.makeText(this,
                    hide ? "Иконка скрыта. Открывайте настройки из Vector / LSPosed"
                         : "Иконка возвращена на рабочий стол",
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Не удалось изменить видимость иконки: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void restartSystemUi() {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", "killall com.android.systemui"});
            int code = p.waitFor();
            if (code == 0) {
                Toast.makeText(this, "SystemUI перезапущен", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Выдайте root приложению HyperNowBar (KernelSU / Magisk / APatch) или перезагрузите телефон", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Выдайте root приложению HyperNowBar (KernelSU / Magisk / APatch) или перезагрузите телефон", Toast.LENGTH_LONG).show();
        }
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xB3FFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout createCard() {
        return createAccentCard(0xFF131722, 0x1EFFFFFF);
    }

    private LinearLayout createAccentCard(int fillColor, int strokeColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), strokeColor);
        card.setBackground(bg);
        return card;
    }

    private void addDivider(LinearLayout parent) {
        View d = new View(this);
        d.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        parent.addView(d, lp);
    }

    private interface BoolConsumer { void accept(boolean v); }
    private interface IntConsumer { void accept(int v); }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle, boolean initial, BoolConsumer onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(0x88FFFFFF);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.8f);
        textCol.addView(s);

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> onChange.accept(isChecked));
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.leftMargin = dp(10);
        row.addView(sw, swLp);

        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
    }

    private void addSliderRow(LinearLayout parent, String label, int min, int max, int initial, String unit, IntConsumer onChange) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(2), dp(8), dp(2), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        top.addView(lbl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valTv = new TextView(this);
        valTv.setText(initial + unit);
        valTv.setTextColor(0xFF8CA6FF);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        valTv.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(valTv);
        col.addView(top);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, initial - min)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                valTv.setText(v + unit);
                if (fromUser) onChange.accept(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(6);
        col.addView(sb, sbLp);
        parent.addView(col);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
