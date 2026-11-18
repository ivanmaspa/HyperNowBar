package com.hypernowbar.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hypernowbar.config.NowBarConfig;
import com.hypernowbar.hook.NowBarController;
import com.hypernowbar.model.MediaState;
import com.hypernowbar.model.NotifItem;
import com.hypernowbar.ui.CapsuleColors;
import com.hypernowbar.ui.NowBarOverlayView;
import com.hypernowbar.ui.VectorIcons;
import java.util.ArrayList;
import java.util.List;

/**
 * Pro Configuration & Interactive Live Preview UI for HyperNowBar v2.0.
 */
public final class MainActivity extends Activity {

    private SharedPreferences prefs;
    private NowBarConfig config;
    private NowBarOverlayView previewBar;
    private MediaState mockMedia;
    private List<NotifItem> mockNotifs;
    private TextView[] styleChips;
    private TextView[] widthChips;
    private TextView liveDiagTv;
    private BroadcastReceiver pongReceiver;

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
        final int baseBot = dp(36);
        root.setPadding(baseH, baseTop, baseH, baseBot);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets sys = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(baseH + sys.left, baseTop + sys.top, baseH + sys.right, baseBot + sys.bottom);
            return insets;
        });

        // 1. Header + Status badge
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleTv = new TextView(this);
        titleTv.setText("HyperNowBar 2.3 Beta");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(titleTv);

        TextView subTv = new TextView(this);
        subTv.setText("Now Bar & Live Capsule • HyperOS 3");
        subTv.setTextColor(0x99FFFFFF);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        titleCol.addView(subTv);

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean active = isModuleActive();
        TextView statusBadge = new TextView(this);
        statusBadge.setText(active ? "Активен" : "SystemUI");
        statusBadge.setTextColor(active ? 0xFF071B13 : 0xFF221504);
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420));
        psLp.topMargin = dp(14);
        root.addView(previewStage, psLp);

        // 3. Visual Style Selector (4 styles including Monochrome B&W!)
        root.addView(sectionHeader("Стиль оформления"));
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] styleNames = {"One UI 7", "ColorOS", "Matte", "Ч/Б Моно"};
        styleChips = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int styleIdx = i;
            TextView chip = new TextView(this);
            chip.setText(styleNames[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(6), dp(11), dp(6), dp(11));
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
        root.addView(styleRow);

        // 4. Pill Width Mode
        root.addView(sectionHeader("Ширина капсулы"));
        LinearLayout widthRow = new LinearLayout(this);
        widthRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] widthNames = {"Компактная", "Стандарт", "Широкая"};
        widthChips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int wIdx = i;
            TextView chip = new TextView(this);
            chip.setText(widthNames[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(10), dp(8), dp(10));
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
        root.addView(widthRow);

        // 5. Positioning, Geometry & Glass Sliders Card
        root.addView(sectionHeader("Позиция, размеры и стекло"));
        LinearLayout slidersCard = createCard();

        addSliderRow(slidersCard,
                "Высота капсулы от низа (над сканером: 145–165 dp)",
                16, 340, config.bottomMarginDp, " dp",
                val -> { config.bottomMarginDp = val; persistAndBroadcast(); });
        addDivider(slidersCard);

        addSliderRow(slidersCard,
                "Высота открытого меню от низа (не задевает сканер)",
                80, 360, config.sheetBottomMarginDp, " dp",
                val -> { config.sheetBottomMarginDp = val; persistAndBroadcast(); });
        addDivider(slidersCard);

        addSliderRow(slidersCard,
                "Сила системного блюра (HyperOS GPU Blur)",
                20, 160, config.blurRadiusPx, " px",
                val -> { config.blurRadiusPx = val; persistAndBroadcast(); });
        addDivider(slidersCard);

        addSliderRow(slidersCard,
                "Максимальная высота списка сообщений",
                160, 440, config.notifMaxHeightDp, " dp",
                val -> { config.notifMaxHeightDp = val; persistAndBroadcast(); });
        addDivider(slidersCard);

        addSliderRow(slidersCard,
                "Скругление углов капсулы",
                10, 28, config.cornerRadiusDp, " dp",
                val -> { config.cornerRadiusDp = val; persistAndBroadcast(); });
        addDivider(slidersCard);

        addSliderRow(slidersCard,
                "Плотность матового стекла",
                45, 100, config.capsuleAlpha, "%",
                val -> { config.capsuleAlpha = val; persistAndBroadcast(); });
        addDivider(slidersCard);

        addSliderRow(slidersCard,
                "Затемнение фона при раскрытии меню",
                0, 90, config.scrimAlpha, "%",
                val -> { config.scrimAlpha = val; persistAndBroadcast(); });

        root.addView(slidersCard);

        // 6. Controls & Navigation Card
        root.addView(sectionHeader("Управление и переходы"));
        LinearLayout actionsCard = createCard();

        addSwitchRow(actionsCard,
                "Стопка уведомлений (стиль iOS)",
                "Сообщения частично накладываются друг на друга и разворачиваются по тапу",
                config.iosNotifStack,
                checked -> { config.iosNotifStack = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Убирать сообщения после разблокировки",
                "После разблокировки старые уведомления исчезают из капсулы до прихода новых",
                config.clearNotifsAfterUnlock,
                checked -> { config.clearNotifsAfterUnlock = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Системный блюр (как в шторке HyperOS)",
                "Аппаратное GPU-размытие фона за капсулой и раскрытым меню",
                config.systemBlurEnabled,
                checked -> { config.systemBlurEnabled = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Быстрые кнопки в свёрнутой капсуле",
                "Пауза/старт и следующий трек прямо на капсуле в 1 тап",
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
                "Тап по сообщению разблокирует экран и открывает приложение",
                config.tapNotifOpensApp,
                checked -> { config.tapNotifOpensApp = checked; persistAndBroadcast(); });
        addDivider(actionsCard);

        addSwitchRow(actionsCard,
                "Скрывать при вводе пароля (PIN)",
                "Мгновенно убирать капсулу, когда открывается экран ввода кода",
                config.hideOnBouncer,
                checked -> { config.hideOnBouncer = checked; persistAndBroadcast(); });

        root.addView(actionsCard);

        // 7. Lockscreen Behavior Card
        root.addView(sectionHeader("Экран блокировки и внешний вид"));
        LinearLayout togglesCard = createCard();

        addSwitchRow(togglesCard,
                "Включить HyperNowBar",
                "Отображать нижнюю капсулу на экране блокировки",
                config.enabled,
                checked -> { config.enabled = checked; persistAndBroadcast(); });
        addDivider(togglesCard);

        addSwitchRow(togglesCard,
                "Чистый экран блокировки",
                "Скрывать стандартные карточки уведомлений и стоковый плеер",
                config.hideStockNotifications,
                checked -> { config.hideStockNotifications = checked; persistAndBroadcast(); });
        addDivider(togglesCard);

        addSwitchRow(togglesCard,
                "Подсветка в цвет обложки",
                "Адаптивное свечение контура под цвет альбома (кроме Ч/Б Моно)",
                config.dynamicGlowColor,
                checked -> { config.dynamicGlowColor = checked; persistAndBroadcast(); });
        addDivider(togglesCard);

        addSwitchRow(togglesCard,
                "Анимация эквалайзера",
                "Живая звуковая волна во время проигрывания",
                config.showEqualizer,
                checked -> { config.showEqualizer = checked; persistAndBroadcast(); });
        addDivider(togglesCard);

        addSwitchRow(togglesCard,
                "Показывать плеер на паузе",
                "Оставлять трек в капсуле для быстрого возобновления",
                config.showMediaWhenPaused,
                checked -> { config.showMediaWhenPaused = checked; persistAndBroadcast(); });
        addDivider(togglesCard);

        addSwitchRow(togglesCard,
                "Скрывать на Always-On Display",
                "Автоматически прятать на AOD (защита OLED)",
                config.hideOnAod,
                checked -> { config.hideOnAod = checked; persistAndBroadcast(); });
        addDivider(togglesCard);

        addSwitchRow(togglesCard,
                "Показывать пустую капсулу",
                "Отображать мини-капсулу даже без музыки и уведомлений",
                config.alwaysShowCapsule,
                checked -> { config.alwaysShowCapsule = checked; persistAndBroadcast(); });

        root.addView(togglesCard);

        // 8. Restart SystemUI Button
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
                String mediaPart = (media != null && !media.isEmpty()) ? (" • Плеер: " + media) : "";
                liveDiagTv.setText("SystemUI подключён (" + win + ") • Уведомлений: " + notifs
                        + " • Переходы: " + (hasStarter ? "OK" : "резерв")
                        + " • Слоёв: " + tracked + mediaPart);
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
                new int[] {0xFF151C36, 0xFF1C1433, 0xFF0C101A});
        wallBg.setCornerRadius(dp(26));
        wallBg.setStroke(dp(1), 0x26FFFFFF);
        stage.setBackground(wallBg);
        stage.setClipToOutline(true);

        LinearLayout clockCol = new LinearLayout(this);
        clockCol.setOrientation(LinearLayout.VERTICAL);
        clockCol.setGravity(Gravity.CENTER_HORIZONTAL);
        clockCol.setPadding(0, dp(22), 0, 0);

        TextView dateTv = new TextView(this);
        dateTv.setText("Предпросмотр • тап по капсуле раскрывает меню");
        dateTv.setTextColor(0x99FFFFFF);
        dateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        clockCol.addView(dateTv);

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
            public void onOpenMediaApp() {
                Toast.makeText(MainActivity.this, "Открытие плеера (на локскрине разблокирует экран)", Toast.LENGTH_SHORT).show();
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
        mockMedia.durationMs = 243_000L;
        mockMedia.positionMs = 86_000L;
        mockMedia.artwork = createDemoArtwork(0xFF5C6BC0);
        mockMedia.accentColor = CapsuleColors.extractVibrantAccent(mockMedia.artwork);

        mockNotifs = new ArrayList<>();
        String[][] demos = {
            {"AyuGram", "Артём", "Зацени 2.0 с быстрыми кнопками и скроллом!"},
            {"VK", "Максим", "Скинул новый трек в личку"},
            {"Telegram", "Чат", "Прокрутка сообщений больше не вызывает ввод PIN"}
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

    private void updateStyleChips() {
        for (int i = 0; i < styleChips.length; i++) {
            boolean selected = (config.style == i);
            TextView chip = styleChips[i];
            chip.setTextColor(selected ? Color.WHITE : 0x99FFFFFF);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(selected ? 0xFF2A3661 : 0xFF141824);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), selected ? 0xFF6C8CFF : 0x22FFFFFF);
            chip.setBackground(bg);
        }
    }

    private void updateWidthChips() {
        for (int i = 0; i < widthChips.length; i++) {
            boolean selected = (config.capsuleWidthMode == i);
            TextView chip = widthChips[i];
            chip.setTextColor(selected ? Color.WHITE : 0x99FFFFFF);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(selected ? 0xFF2A3661 : 0xFF141824);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), selected ? 0xFF6C8CFF : 0x22FFFFFF);
            chip.setBackground(bg);
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

    private void restartSystemUi() {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", "killall com.android.systemui"});
            int code = p.waitFor();
            if (code == 0) {
                Toast.makeText(this, "SystemUI перезапущен", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Выдайте root приложению HyperNowBar в KernelSU Next или перезагрузите телефон", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Выдайте root приложению HyperNowBar в KernelSU Next или перезагрузите телефон", Toast.LENGTH_LONG).show();
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF131722);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0x1EFFFFFF);
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
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(0x88FFFFFF);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
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
