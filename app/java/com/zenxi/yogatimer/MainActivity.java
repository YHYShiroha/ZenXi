package com.zenxi.yogatimer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;

import com.zenxi.yogatimer.ui.AmbientBackgroundView;
import com.zenxi.yogatimer.ui.ChipView;
import com.zenxi.yogatimer.ui.RingTimerView;
import com.zenxi.yogatimer.ui.Sheet;
import com.zenxi.yogatimer.ui.WeekBarsView;
import com.zenxi.yogatimer.ui.ZenSwitch;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements TimerEngine.Listener {

    private static final int REQ_NOTIF = 101;
    private static final long[] PRESET_MIN = {1, 3, 5, 10, 15, 20, 30};
    private static final int[] PREPARE_SEC = {0, 3, 5, 10};
    private static final long[] REST_MS = {15000L, 30000L, 60000L};
    private static final long[] INTERVAL_MS = {0L, 60000L, 120000L, 180000L, 300000L, 600000L};
    private static final String[] INTERVAL_NAMES =
            {"关闭", "1 分钟", "2 分钟", "3 分钟", "5 分钟", "10 分钟"};
    private static final int[] CUSTOM_MIN = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30, 45, 60};
    private static final int[] CUSTOM_SEC = {0, 10, 15, 20, 30, 45};

    private interface BoolSetter {
        void set(boolean v);
    }

    private interface IntSetter {
        void set(int v);
    }

    private Settings st;
    private Stats stats;
    private TimerEngine engine;
    private Palette palette;

    private FrameLayout rootView;
    private FrameLayout finishOverlay;
    private LinearLayout finishCard;
    private View finishScrim;
    private View contentView;
    private AmbientBackgroundView ambient;
    private RingTimerView ring;
    private TextView titleView;
    private TextView subtitleView;
    private TextView btnStart;
    private TextView statsLine;
    private TextView finishBig;
    private TextView finishSub;
    private TextView btnFinishClose;
    private ImageView btnSettings;
    private ImageView btnPause;
    private ImageView btnStop;
    private ImageView finishIcon;
    private LinearLayout presetRow;
    private LinearLayout idleControls;
    private LinearLayout runControls;
    private LinearLayout quickRow;

    private ChipView chipTail;
    private ChipView chipBreath;
    private ChipView chipVoice;
    private ChipView chipVibrate;

    private final List<ChipView> presetChips = new ArrayList<>();
    private boolean listenerAttached;
    private boolean finishVisible;
    private boolean runningUi;

    private final Runnable autoHideFinish = new Runnable() {
        @Override
        public void run() {
            hideFinish(true);
        }
    };

    // ------------------------------------------------------------- 生命周期

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        st = Settings.get(this);
        stats = Stats.get(this);
        engine = TimerEngine.get();
        palette = Palette.of(st.dark());
        setContentView(R.layout.activity_main);

        rootView = (FrameLayout) findViewById(R.id.root);
        contentView = findViewById(R.id.content);
        ambient = (AmbientBackgroundView) findViewById(R.id.ambient);
        ring = (RingTimerView) findViewById(R.id.ring);
        titleView = (TextView) findViewById(R.id.title);
        subtitleView = (TextView) findViewById(R.id.subtitle);
        btnStart = (TextView) findViewById(R.id.btnStart);
        statsLine = (TextView) findViewById(R.id.statsLine);
        btnSettings = (ImageView) findViewById(R.id.btnSettings);
        btnPause = (ImageView) findViewById(R.id.btnPause);
        btnStop = (ImageView) findViewById(R.id.btnStop);
        presetRow = (LinearLayout) findViewById(R.id.presetRow);
        idleControls = (LinearLayout) findViewById(R.id.idleControls);
        runControls = (LinearLayout) findViewById(R.id.runControls);
        quickRow = (LinearLayout) findViewById(R.id.quickRow);
        finishOverlay = (FrameLayout) findViewById(R.id.finishOverlay);
        finishScrim = findViewById(R.id.finishScrim);
        finishCard = (LinearLayout) findViewById(R.id.finishCard);
        finishIcon = (ImageView) findViewById(R.id.finishIcon);
        finishBig = (TextView) findViewById(R.id.finishBig);
        finishSub = (TextView) findViewById(R.id.finishSub);
        btnFinishClose = (TextView) findViewById(R.id.btnFinishClose);

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTimer();
            }
        });
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePause();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.stop();
            }
        });
        statsLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openStats();
            }
        });
        btnFinishClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.reset();
            }
        });
        finishOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.reset();
            }
        });
        applyInsets();
        applyPalette();
        buildPresets();
        buildQuickChips();
        refreshPresetChips();
        refreshQuickChips();
        refreshStatsLine();
        updateIdleView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!listenerAttached) {
            engine.addListener(this);
            listenerAttached = true;
        }
        TimerEngine.State s = engine.state();
        if (s == TimerEngine.State.ACTIVE || s == TimerEngine.State.PAUSED) {
            onTick(engine.status());
        } else if (s == TimerEngine.State.DONE) {
            TimerConfig c = engine.config();
            showFinish(c == null ? 0L : c.workMs * Math.max(1, c.rounds));
        }
    }

    @Override
    protected void onStop() {
        if (listenerAttached) {
            engine.removeListener(this);
            listenerAttached = false;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ring.setLoopEnabled(true);
        TimerEngine.State s = engine.state();
        ambient.setAnimating(s == TimerEngine.State.ACTIVE);
        updateKeepScreen(engine.isBusy());
        refreshStatsLine();
    }

    @Override
    protected void onPause() {
        ring.setLoopEnabled(false);
        ambient.setAnimating(false);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (finishVisible) {
            engine.reset();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_NOTIF) {
            refreshStatsLine();
        }
    }

    // ------------------------------------------------------------- 界面构建

    @SuppressWarnings("deprecation")
    private void applyInsets() {
        final int pl = contentView.getPaddingLeft();
        final int pt = contentView.getPaddingTop();
        final int pr = contentView.getPaddingRight();
        final int pb = contentView.getPaddingBottom();
        contentView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top = Math.max(insets.getSystemWindowInsetTop(), 0);
                int bottom = Math.max(insets.getSystemWindowInsetBottom(), 0);
                v.setPadding(pl, pt + top, pr, pb + bottom);
                return insets;
            }
        });
    }

    private void applyPalette() {
        palette = Palette.of(st.dark());
        Ui.themeTree(rootView, palette);

        btnStart.setBackground(Ui.glowPill(palette.accent, Ui.mix(palette.accent, palette.arcB, 0.45f),
                Ui.dp(this, 31f)));
        btnStart.setTextColor(palette.onAccent);

        btnPause.setBackground(Ui.rr(palette.accent, Ui.dp(this, 42f)));
        btnPause.setColorFilter(palette.onAccent);
        btnStop.setBackground(Ui.rr(palette.chip, palette.chipStroke, Ui.dp(this, 1f), Ui.dp(this, 30f)));
        btnStop.setColorFilter(palette.textMid);
        btnSettings.setColorFilter(palette.textMid);

        finishScrim.setBackgroundColor(palette.scrim);
        finishCard.setBackground(Ui.rr(palette.card, palette.cardStroke, Ui.dp(this, 1f), Ui.dp(this, 26f)));
        finishIcon.setColorFilter(palette.accent);
        btnFinishClose.setBackground(Ui.rr(palette.accent, Ui.dp(this, 24f)));
        btnFinishClose.setTextColor(palette.onAccent);

        applySystemBarIcons();
    }

    private void applySystemBarIcons() {
        try {
            Window w = getWindow();
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    c.setSystemBarsAppearance(st.dark() ? 0 : mask, mask);
                }
            } else {
                View d = w.getDecorView();
                int flags = st.dark() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                d.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {
        }
    }

    private void buildPresets() {
        presetRow.removeAllViews();
        presetChips.clear();
        for (final long min : PRESET_MIN) {
            final ChipView chip = new ChipView(this);
            chip.setText(min + " 分");
            chip.applyTheme(palette);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    st.setWorkMs(min * 60000L);
                    refreshPresetChips();
                    if (!engine.isBusy()) {
                        updateIdleView();
                    }
                }
            });
            presetRow.addView(chip, chipParams(8));
            presetChips.add(chip);
        }
        final ChipView custom = new ChipView(this);
        custom.setText("自定义");
        custom.applyTheme(palette);
        custom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCustomTime();
            }
        });
        presetRow.addView(custom, chipParams(0));
    }

    private LinearLayout.LayoutParams chipParams(int endMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) Ui.dp(this, endMarginDp);
        return lp;
    }

    private void buildQuickChips() {
        quickRow.removeAllViews();
        chipTail = quickChip("尾铃", R.drawable.ic_bell, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st.setTailBell(!st.tailBell());
                if (st.tailBell()) {
                    SoundBank.get().ding(st.tone(), st.volume());
                }
                refreshQuickChips();
            }
        });
        chipBreath = quickChip("呼吸", R.drawable.ic_wave, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st.setBreath((st.breath() + 1) % 4);
                refreshQuickChips();
            }
        });
        chipVoice = quickChip("报时", R.drawable.ic_voice, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st.setVoice(!st.voice());
                refreshQuickChips();
            }
        });
        chipVibrate = quickChip("震动", R.drawable.ic_vibrate, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                st.setVibrate(!st.vibrate());
                refreshQuickChips();
            }
        });
        int margin = (int) Ui.dp(this, 5f);
        quickRow.addView(chipTail, quickParams(margin));
        quickRow.addView(chipBreath, quickParams(margin));
        quickRow.addView(chipVoice, quickParams(margin));
        quickRow.addView(chipVibrate, quickParams(margin));
    }

    private LinearLayout.LayoutParams quickParams(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = margin;
        lp.rightMargin = margin;
        return lp;
    }

    private ChipView quickChip(String text, int icon, View.OnClickListener l) {
        ChipView c = new ChipView(this);
        c.setCompact(true);
        c.setText(text);
        c.setIconRes(icon);
        c.applyTheme(palette);
        c.setOnClickListener(l);
        return c;
    }

    private void refreshPresetChips() {
        long work = st.workMs();
        boolean matched = false;
        for (int i = 0; i < PRESET_MIN.length && i < presetChips.size(); i++) {
            boolean on = PRESET_MIN[i] * 60000L == work;
            matched |= on;
            presetChips.get(i).setActive(on, false);
        }
        if (presetRow.getChildCount() > presetChips.size()) {
            View custom = presetRow.getChildAt(presetRow.getChildCount() - 1);
            if (custom instanceof ChipView) {
                ((ChipView) custom).setActive(!matched, false);
            }
        }
    }

    private void refreshQuickChips() {
        if (chipTail == null) {
            return;
        }
        chipTail.setActive(st.tailBell());
        chipBreath.setActive(st.breath() > 0);
        chipVoice.setActive(st.voice());
        chipVibrate.setActive(st.vibrate());
    }

    private void refreshStatsLine() {
        long today = stats.today();
        int streak = stats.streak();
        String s = "今日 " + Ui.minutes(today) + " · 连续 " + streak + " 天";
        if (!engine.isBusy() && today == 0) {
            s = "今天还没有练习 · 点开看记录";
        }
        statsLine.setText(s + "  ›");
    }

    private void updateIdleView() {
        TimerConfig c = st.config();
        ring.setIdleOrbit(true);
        ring.clearFinish();
        ring.setWarmth(false);
        ring.setBreathPhase(-1);
        ring.setProgress(1f);
        ring.setTexts(Ui.clock(c.workMs), "准备开始", idleSub(c));
        setRunningUi(false, false);
        btnPause.setImageResource(R.drawable.ic_pause);
        refreshPresetChips();
        refreshQuickChips();
        refreshStatsLine();
        updateKeepScreen(false);
        ambient.setAnimating(false);
    }

    private String idleSub(TimerConfig c) {
        StringBuilder sb = new StringBuilder();
        if (c.rounds > 1) {
            sb.append(c.rounds).append(" 组 · ");
        }
        if (c.intervalMs > 0) {
            sb.append("每 ").append(c.intervalMs / 60000L).append(" 分钟一轮");
        } else {
            sb.append(c.tailBell ? "末尾四声 · 叮 叮 叮 铛" : "无尾铃");
        }
        return sb.toString();
    }

    private void setRunningUi(boolean running, boolean animate) {
        if (running == runningUi) {
            return;
        }
        runningUi = running;
        View in = running ? runControls : idleControls;
        View out = running ? idleControls : runControls;
        if (!animate) {
            in.setVisibility(View.VISIBLE);
            in.setAlpha(1f);
            in.setScaleX(1f);
            in.setScaleY(1f);
            out.setVisibility(View.GONE);
            return;
        }
        out.animate().alpha(0f).scaleX(0.88f).scaleY(0.88f).setDuration(170L)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        out.setVisibility(View.GONE);
                    }
                }).start();
        in.setVisibility(View.VISIBLE);
        in.setAlpha(0f);
        in.setScaleX(0.78f);
        in.setScaleY(0.78f);
        in.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(360L)
                .setInterpolator(new OvershootInterpolator(0.85f)).start();
    }

    private void updateKeepScreen(boolean on) {
        if (on && st.keepScreen()) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // ------------------------------------------------------------- 计时控制

    private void startTimer() {
        ensureNotificationPermission();
        hideFinish(false);
        TimerConfig c = st.config();
        engine.start(this, c);
        TimerService.send(this, TimerService.ACTION_START);
        ambient.setAnimating(true);
        setRunningUi(true, true);
    }

    private void togglePause() {
        if (engine.state() == TimerEngine.State.PAUSED) {
            engine.resume();
        } else {
            engine.pause();
        }
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }
    }

    private boolean notifGranted() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ignoringBattery() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void openBatterySettings() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------- 引擎回调

    @Override
    public void onTick(TimerEngine.Status s) {
        if (s.state == TimerEngine.State.IDLE || s.state == TimerEngine.State.DONE) {
            return;
        }
        boolean paused = s.state == TimerEngine.State.PAUSED;
        ring.setIdleOrbit(false);
        ring.clearFinish();
        ring.setProgress(s.phaseMs > 0 ? s.remainMs / (float) s.phaseMs : 0f);
        ring.setWarmth(s.tailing);
        ring.setBreathPhase(s.breathPhase);

        String time = Ui.clock(s.remainMs);
        String label;
        if (paused) {
            label = "已暂停";
        } else if (s.phase == TimerEngine.Phase.PREPARE) {
            label = "准备";
        } else if (s.phase == TimerEngine.Phase.REST) {
            label = "休息";
        } else if (s.rounds > 1) {
            label = "第 " + s.round + " / " + s.rounds + " 组";
        } else {
            label = "练习中";
        }
        String sub;
        if (s.tailing) {
            sub = "叮 叮 叮 铛";
        } else if (s.intervalRemainMs >= 0L) {
            sub = "距下次铃 " + Ui.clock(s.intervalRemainMs);
        } else if (s.rounds > 1) {
            sub = "总剩余 " + Ui.clock(s.sessionRemainMs);
        } else if (s.phase == TimerEngine.Phase.PREPARE) {
            sub = "即将开始";
        } else {
            sub = Ui.minutes(s.phaseMs);
        }
        ring.setTexts(time, label, sub);
        btnPause.setImageResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
        setRunningUi(true, true);
        ambient.setAnimating(!paused);
        updateKeepScreen(!paused);
    }

    @Override
    public void onState(TimerEngine.State s) {
        if (s == TimerEngine.State.IDLE) {
            if (finishVisible) {
                hideFinish(true);
            }
            updateIdleView();
        } else if (s == TimerEngine.State.ACTIVE) {
            setRunningUi(true, true);
        } else if (s == TimerEngine.State.PAUSED) {
            btnPause.setImageResource(R.drawable.ic_play);
            ambient.setAnimating(false);
            updateKeepScreen(false);
        }
    }

    @Override
    public void onCue(int cue) {
        float strength = 0.5f;
        if (cue == TimerEngine.CUE_DANG || cue == TimerEngine.CUE_END) {
            strength = 1f;
        } else if (cue == TimerEngine.CUE_DING) {
            strength = 0.8f;
        } else if (cue == TimerEngine.CUE_START) {
            strength = 0.7f;
        }
        ring.pulse(strength);
    }

    @Override
    public void onFinished(long workedMs, int rounds) {
        if (isFinishing()) {
            return;
        }
        showFinish(workedMs);
    }

    // ------------------------------------------------------------- 完成浮层

    private void showFinish(long workedMs) {
        if (finishVisible) {
            return;
        }
        finishVisible = true;
        ring.setIdleOrbit(false);
        ring.setWarmth(false);
        ring.setProgress(0f);
        ring.setTexts("00:00", "完成", "");
        ring.playFinish();
        finishBig.setText(Ui.minutes(workedMs));
        finishSub.setText("今日 " + Ui.minutes(stats.today())
                + " · 连续 " + stats.streak() + " 天");
        finishOverlay.setVisibility(View.VISIBLE);
        finishOverlay.setAlpha(0f);
        finishOverlay.animate().alpha(1f).setDuration(220L).start();
        finishCard.setAlpha(0f);
        finishCard.setScaleX(0.9f);
        finishCard.setScaleY(0.9f);
        finishCard.setTranslationY(Ui.dp(this, 20f));
        finishCard.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(430L).setInterpolator(new OvershootInterpolator(0.9f)).start();
        ambient.setAnimating(true);
        updateKeepScreen(false);
        refreshStatsLine();
        finishOverlay.removeCallbacks(autoHideFinish);
        finishOverlay.postDelayed(autoHideFinish, 8000L);
    }

    private void hideFinish(boolean animate) {
        finishOverlay.removeCallbacks(autoHideFinish);
        if (!finishVisible && finishOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        finishVisible = false;
        if (!animate) {
            finishOverlay.setVisibility(View.GONE);
            finishOverlay.setAlpha(1f);
            ring.clearFinish();
            return;
        }
        finishOverlay.animate().alpha(0f).setDuration(240L).withEndAction(new Runnable() {
            @Override
            public void run() {
                finishOverlay.setVisibility(View.GONE);
                finishOverlay.setAlpha(1f);
                ring.clearFinish();
                if (engine.state() == TimerEngine.State.DONE) {
                    engine.reset();
                }
            }
        }).start();
    }

    // ------------------------------------------------------------- 面板

    private void openSettings() {
        final Sheet sheet = new Sheet(this, "设置", palette);
        LinearLayout b = sheet.body();

        b.addView(section("提示音"));
        b.addView(switchRow("末尾四声铃", "最后 4 秒：叮 叮 叮 铛", st.tailBell(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setTailBell(v);
                refreshQuickChips();
            }
        }));
        b.addView(choiceRow("尾铃节奏", new String[]{"连响", "终点重音"}, st.tailMode(), new IntSetter() {
            @Override
            public void set(int v) {
                st.setTailMode(v);
            }
        }));
        b.addView(choiceRow("整分铃", "长练习时，每隔一段时间的末尾再响一轮 叮 叮 叮 铛",
                INTERVAL_NAMES, indexOfLong(INTERVAL_MS, st.intervalMs()), new IntSetter() {
                    @Override
                    public void set(int v) {
                        st.setIntervalMs(INTERVAL_MS[v]);
                        if (!engine.isBusy()) {
                            ring.setTexts(Ui.clock(st.workMs()), "准备开始", idleSub(st.config()));
                        }
                    }
                }));
        b.addView(switchRow("收束长铃", "结束后再落一记悠长低铃", st.endBell(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setEndBell(v);
            }
        }));
        b.addView(switchRow("半程轻铃", "过半时轻响一声（1.5 分钟以上）", st.halfBell(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setHalfBell(v);
            }
        }));
        b.addView(choiceRow("音色", SoundBank.TONE_NAMES, st.tone(), new IntSetter() {
            @Override
            public void set(int v) {
                st.setTone(v);
                SoundBank.get().preview(v, st.volume());
            }
        }));
        b.addView(choiceRow("起势音", "准备倒计时结束、正式进入练习时的那一声",
                SoundBank.START_NAMES, st.startTone(), new IntSetter() {
                    @Override
                    public void set(int v) {
                        st.setStartTone(v);
                        SoundBank.get().previewStart(v, st.volume());
                    }
                }));
        b.addView(volumeRow());

        b.addView(section("节奏"));
        b.addView(choiceRow("准备倒计时", new String[]{"关闭", "3 秒", "5 秒", "10 秒"},
                indexOfInt(PREPARE_SEC, st.prepareSec()), new IntSetter() {
                    @Override
                    public void set(int v) {
                        st.setPrepareSec(PREPARE_SEC[v]);
                    }
                }));
        b.addView(choiceRow("呼吸引导", new String[]{"关闭", "4-4-4-4", "4-7-8", "5-5"},
                Math.max(0, Math.min(3, st.breath())), new IntSetter() {
                    @Override
                    public void set(int v) {
                        st.setBreath(v);
                        refreshQuickChips();
                    }
                }));
        b.addView(choiceRow("练习组数", new String[]{"1", "2", "3", "4", "5"},
                Math.max(0, Math.min(4, st.rounds() - 1)), new IntSetter() {
                    @Override
                    public void set(int v) {
                        st.setRounds(v + 1);
                        if (!engine.isBusy()) {
                            ring.setTexts(Ui.clock(st.workMs()), "准备开始", idleSub(st.config()));
                        }
                    }
                }));
        b.addView(choiceRow("组间休息", new String[]{"15 秒", "30 秒", "60 秒"},
                indexOfLong(REST_MS, st.restMs()), new IntSetter() {
                    @Override
                    public void set(int v) {
                        st.setRestMs(REST_MS[v]);
                    }
                }));

        b.addView(section("反馈"));
        b.addView(switchRow("语音报时", "开始 / 休息 / 结束语音提示（用系统语音）", st.voice(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setVoice(v);
                refreshQuickChips();
            }
        }));
        b.addView(switchRow("震动同步", "每一声铃同时轻震", st.vibrate(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setVibrate(v);
                refreshQuickChips();
            }
        }));
        b.addView(switchRow("计时中屏幕常亮", "前台时保持不熄屏", st.keepScreen(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setKeepScreen(v);
                updateKeepScreen(engine.isBusy());
            }
        }));

        b.addView(section("外观"));
        b.addView(switchRow("素白模式", "浅色纸张质感，切换后界面重绘", !st.dark(), new BoolSetter() {
            @Override
            public void set(boolean v) {
                st.setDark(!v);
                sheet.dismissNow();
                recreate();
            }
        }));

        b.addView(section("后台运行"));
        b.addView(actionRow("电池优化白名单", ignoringBattery() ? "已加入白名单" : "建议加入，避免被系统清理",
                "去设置", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openBatterySettings();
                    }
                }));
        b.addView(actionRow("通知权限", notifGranted() ? "已授权" : "未授权，通知栏看不到倒计时",
                "授权", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ensureNotificationPermission();
                    }
                }));
        b.addView(hint("提示音走「闹钟」声道：手机静音时依然会响，"
                + "也不会打断你正在看的视频。"));
        sheet.show();
    }

    private void openStats() {
        final Sheet sheet = new Sheet(this, "练习记录", palette);
        LinearLayout b = sheet.body();

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(statCell("今日", Ui.minutes(stats.today())), weight());
        grid.addView(statCell("本周", Ui.minutes(stats.week())), weight());
        grid.addView(statCell("累计", Ui.minutes(stats.total())), weight());
        grid.addView(statCell("连续", stats.streak() + " 天"), weight());
        b.addView(grid);

        final WeekBarsView bars = new WeekBarsView(this);
        bars.applyTheme(palette);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Ui.dp(this, 140f));
        blp.topMargin = (int) Ui.dp(this, 20f);
        b.addView(bars, blp);
        bars.post(new Runnable() {
            @Override
            public void run() {
                bars.setData(stats.last7(), stats.last7Labels());
            }
        });

        b.addView(hint("共完成 " + stats.sessions() + " 次练习。"
                + "记录保存在本机，卸载后不再保留。"));
        sheet.show();
    }

    private void openCustomTime() {
        final Sheet sheet = new Sheet(this, "自定义时长", palette);
        LinearLayout b = sheet.body();

        long cur = st.workMs() / 1000L;
        final int[] sel = {indexOfInt(CUSTOM_MIN, (int) (cur / 60)),
                indexOfInt(CUSTOM_SEC, (int) (cur % 60))};
        if (sel[0] < 0) {
            sel[0] = 0;
        }
        if (sel[1] < 0) {
            sel[1] = 0;
        }

        final TextView preview = new TextView(this);
        preview.setTextSize(34f);
        preview.setGravity(Gravity.CENTER);
        preview.setTextColor(palette.accent);
        preview.setPadding(0, (int) Ui.dp(this, 6f), 0, (int) Ui.dp(this, 14f));

        String[] minNames = new String[CUSTOM_MIN.length];
        for (int i = 0; i < CUSTOM_MIN.length; i++) {
            minNames[i] = CUSTOM_MIN[i] + " 分";
        }
        String[] secNames = new String[CUSTOM_SEC.length];
        for (int i = 0; i < CUSTOM_SEC.length; i++) {
            secNames[i] = CUSTOM_SEC[i] + " 秒";
        }

        final Runnable update = new Runnable() {
            @Override
            public void run() {
                long ms = CUSTOM_MIN[sel[0]] * 60000L + CUSTOM_SEC[sel[1]] * 1000L;
                preview.setText(Ui.clock(ms));
            }
        };

        b.addView(preview);
        b.addView(chipPicker("分钟", minNames, sel[0], new IntSetter() {
            @Override
            public void set(int v) {
                sel[0] = v;
                update.run();
            }
        }));
        b.addView(chipPicker("秒", secNames, sel[1], new IntSetter() {
            @Override
            public void set(int v) {
                sel[1] = v;
                update.run();
            }
        }));
        update.run();

        TextView confirm = new TextView(this);
        confirm.setText("确定");
        confirm.setGravity(Gravity.CENTER);
        confirm.setTextSize(15f);
        confirm.setLetterSpacing(0.2f);
        confirm.setTextColor(palette.onAccent);
        confirm.setBackground(Ui.rr(palette.accent, Ui.dp(this, 24f)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Ui.dp(this, 48f));
        clp.topMargin = (int) Ui.dp(this, 18f);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long ms = CUSTOM_MIN[sel[0]] * 60000L + CUSTOM_SEC[sel[1]] * 1000L;
                st.setWorkMs(ms);
                sheet.dismiss();
                refreshPresetChips();
                if (!engine.isBusy()) {
                    updateIdleView();
                }
            }
        });
        b.addView(confirm, clp);
        sheet.show();
    }

    // ------------------------------------------------------------- 行组件

    private View section(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11f);
        t.setLetterSpacing(0.2f);
        t.setTextColor(palette.accent);
        t.setPadding(0, (int) Ui.dp(this, 16f), 0, (int) Ui.dp(this, 4f));
        return t;
    }

    private View textBlock(String title, String sub) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14.5f);
        t.setTextColor(palette.textHi);
        v.addView(t);
        if (sub != null && sub.length() > 0) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(11.5f);
            s.setTextColor(palette.textLow);
            s.setPadding(0, (int) Ui.dp(this, 2f), 0, 0);
            v.addView(s);
        }
        return v;
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) Ui.dp(this, 9f), 0, (int) Ui.dp(this, 9f));
        return row;
    }

    private View switchRow(String title, String sub, boolean value, final BoolSetter setter) {
        LinearLayout row = baseRow();
        row.addView(textBlock(title, sub), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ZenSwitch sw = new ZenSwitch(this);
        sw.applyTheme(palette);
        sw.setChecked(value, false);
        if (setter != null) {
            sw.setOnChange(new ZenSwitch.OnChange() {
                @Override
                public void onChanged(boolean v) {
                    setter.set(v);
                }
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) Ui.dp(this, 50f), (int) Ui.dp(this, 30f));
        lp.leftMargin = (int) Ui.dp(this, 12f);
        row.addView(sw, lp);
        return row;
    }

    private View choiceRow(String title, String[] options, int selected, final IntSetter setter) {
        return choiceRow(title, null, options, selected, setter);
    }

    private View choiceRow(String title, String sub, String[] options, int selected,
                           final IntSetter setter) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, (int) Ui.dp(this, 8f), 0, (int) Ui.dp(this, 4f));
        if (sub == null) {
            TextView t = new TextView(this);
            t.setText(title);
            t.setTextSize(13f);
            t.setTextColor(palette.textMid);
            col.addView(t);
        } else {
            col.addView(textBlock(title, sub));
            col.setPadding(0, (int) Ui.dp(this, 11f), 0, (int) Ui.dp(this, 4f));
        }
        col.addView(chipPicker(null, options, selected, setter));
        return col;
    }

    private View chipPicker(String label, String[] names, int selected, final IntSetter setter) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        if (label != null) {
            TextView t = new TextView(this);
            t.setText(label);
            t.setTextSize(12.5f);
            t.setTextColor(palette.textMid);
            t.setPadding(0, (int) Ui.dp(this, 8f), 0, 0);
            col.addView(t);
        }
        final HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) Ui.dp(this, 8f), 0, (int) Ui.dp(this, 4f));
        scroll.addView(row);

        for (int i = 0; i < names.length; i++) {
            final int index = i;
            final ChipView chip = new ChipView(this);
            chip.setText(names[i]);
            chip.applyTheme(palette);
            chip.setActive(i == selected, false);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int k = 0; k < row.getChildCount(); k++) {
                        View c = row.getChildAt(k);
                        if (c instanceof ChipView) {
                            ((ChipView) c).setActive(c == chip, true);
                        }
                    }
                    setter.set(index);
                }
            });
            row.addView(chip, chipParams(8));
        }
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private View volumeRow() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, (int) Ui.dp(this, 10f), 0, (int) Ui.dp(this, 4f));
        TextView t = new TextView(this);
        t.setText("铃声音量");
        t.setTextSize(13f);
        t.setTextColor(palette.textMid);
        col.addView(t);
        final SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.round(st.volume() * 100f));
        bar.setPadding(0, (int) Ui.dp(this, 6f), 0, 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                st.setVolume(progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                SoundBank.get().preview(st.tone(), st.volume());
            }
        });
        col.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private View actionRow(String title, String sub, String actionText, View.OnClickListener l) {
        LinearLayout row = baseRow();
        row.addView(textBlock(title, sub), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView btn = new TextView(this);
        btn.setText(actionText);
        btn.setTextSize(13f);
        btn.setTextColor(palette.accent);
        btn.setPadding((int) Ui.dp(this, 14f), (int) Ui.dp(this, 8f),
                (int) Ui.dp(this, 14f), (int) Ui.dp(this, 8f));
        btn.setBackground(Ui.rr(palette.accentSoft, Ui.dp(this, 20f)));
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) Ui.dp(this, 10f);
        row.addView(btn, lp);
        return row;
    }

    private View hint(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11.5f);
        t.setTextColor(palette.textLow);
        t.setLineSpacing(Ui.dp(this, 3f), 1f);
        t.setPadding(0, (int) Ui.dp(this, 14f), 0, (int) Ui.dp(this, 4f));
        return t;
    }

    private View statCell(String label, String value) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        TextView val = new TextView(this);
        val.setText(value);
        val.setTextSize(15f);
        val.setTextColor(palette.textHi);
        val.setGravity(Gravity.CENTER);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(11f);
        lab.setTextColor(palette.textLow);
        lab.setGravity(Gravity.CENTER);
        lab.setPadding(0, (int) Ui.dp(this, 4f), 0, 0);
        v.addView(val);
        v.addView(lab);
        return v;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static int indexOfInt(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private static int indexOfLong(long[] arr, long value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return 1;
    }
}
