package com.zenxi.yogatimer;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 练习计时引擎。
 *
 * 时间基准是 SystemClock.elapsedRealtime()，配合前台服务 + Partial WakeLock，
 * 保证 App 退到后台、甚至用户在看视频时，末尾的四声铃依然准时响起。
 *
 * 时间线由 {@link Timeline} 计算（纯 Java，可单测）。
 */
public final class TimerEngine {

    private static final String TAG = "ZenXi/Engine";
    private static final TimerEngine INSTANCE = new TimerEngine();

    public static TimerEngine get() {
        return INSTANCE;
    }

    public enum State { IDLE, ACTIVE, PAUSED, DONE }

    public enum Phase { PREPARE, WORK, REST }

    public static final int CUE_DING = Timeline.CUE_DING;
    public static final int CUE_DANG = Timeline.CUE_DANG;
    public static final int CUE_TICK = Timeline.CUE_TICK;
    public static final int CUE_START = Timeline.CUE_START;
    public static final int CUE_HALF = Timeline.CUE_HALF;
    public static final int CUE_END = Timeline.CUE_END;
    public static final int CUE_REST = Timeline.CUE_REST;
    public static final int CUE_BREATH_IN = Timeline.CUE_BREATH_IN;
    public static final int CUE_BREATH_OUT = Timeline.CUE_BREATH_OUT;

    /** 界面刷新用的状态快照。 */
    public static final class Status {
        public State state = State.IDLE;
        public Phase phase = Phase.WORK;
        public long remainMs;
        public long phaseMs;
        public long sessionRemainMs;
        public long sessionTotalMs;
        public int round = 1;
        public int rounds = 1;
        public float progress;
        public int breathPhase = -1;
        public boolean tailing;
        /** 距下一个整分铃的毫秒数，-1 表示未开启。 */
        public long intervalRemainMs = -1L;
    }

    public interface Listener {
        default void onTick(Status s) {
        }

        default void onState(State s) {
        }

        default void onCue(int cue) {
        }

        default void onFinished(long workedMs, int rounds) {
        }
    }

    private final class CueRunner implements Runnable {
        final Timeline.Cue cue;

        CueRunner(Timeline.Cue cue) {
            this.cue = cue;
        }

        @Override
        public void run() {
            fire(cue);
        }
    }

    private final Object lock = new Object();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<CueRunner> runners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Context app;
    private HandlerThread thread;
    private Handler zh;
    private PowerManager.WakeLock wake;
    private Vibrator vibrator;

    private TimerConfig cfg;
    private Timeline.Plan plan;
    private volatile State state = State.IDLE;
    private long startElapsed;
    private long endElapsed;
    private long pauseAt;
    private long totalWorkMs;

    private TimerEngine() {
    }

    // ------------------------------------------------------------------ 对外接口

    public void addListener(Listener l) {
        synchronized (lock) {
            if (!listeners.contains(l)) {
                listeners.add(l);
            }
        }
    }

    public void removeListener(Listener l) {
        synchronized (lock) {
            listeners.remove(l);
        }
    }

    public State state() {
        return state;
    }

    public boolean isBusy() {
        State s = state;
        return s == State.ACTIVE || s == State.PAUSED;
    }

    public TimerConfig config() {
        synchronized (lock) {
            return cfg == null ? null : cfg.copy();
        }
    }

    public void start(Context context, TimerConfig config) {
        synchronized (lock) {
            stopLocked(false);
            app = context.getApplicationContext();
            cfg = config.copy();
            if (vibrator == null) {
                try {
                    vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
                } catch (Throwable t) {
                    Log.w(TAG, "vibrator", t);
                }
            }
            ensureThread();
            long now = SystemClock.elapsedRealtime();
            startElapsed = now;
            plan = Timeline.build(cfg, now);
            endElapsed = plan.end;
            totalWorkMs = plan.workMs();
            runners.clear();
            for (Timeline.Cue c : plan.cues) {
                runners.add(new CueRunner(c));
            }
            state = State.ACTIVE;
            acquireWake(now);
            postCues(now);
            zh.removeCallbacks(ticker);
            zh.postDelayed(ticker, 60L);
            try {
                Settings.get(app).saveSession(System.currentTimeMillis()
                        + Math.max(0L, endElapsed - now), totalWorkMs, cfg.rounds);
            } catch (Throwable t) {
                Log.w(TAG, "save session", t);
            }
        }
        notifyState();
        notifyTick();
    }

    public void pause() {
        synchronized (lock) {
            if (state != State.ACTIVE) {
                return;
            }
            state = State.PAUSED;
            pauseAt = SystemClock.elapsedRealtime();
            if (zh != null) {
                zh.removeCallbacksAndMessages(null);
            }
            releaseWake();
        }
        Speaker.get().stop();
        notifyState();
        notifyTick();
    }

    public void resume() {
        synchronized (lock) {
            if (state != State.PAUSED) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long delta = Math.max(0L, now - pauseAt);
            if (plan != null) {
                for (Timeline.Seg s : plan.segs) {
                    s.start += delta;
                    s.end += delta;
                }
                for (Timeline.Cue c : plan.cues) {
                    if (c.at > pauseAt) {
                        c.at += delta;
                    }
                }
                plan.start += delta;
                plan.end += delta;
            }
            endElapsed += delta;
            state = State.ACTIVE;
            acquireWake(now);
            postCues(now);
            zh.removeCallbacks(ticker);
            zh.postDelayed(ticker, 40L);
        }
        notifyState();
        notifyTick();
    }

    /** 用户主动结束：已经练过的时间仍计入记录。 */
    public void stop() {
        synchronized (lock) {
            stopLocked(true);
        }
        notifyState();
        notifyTick();
    }

    /** 结束后复位界面（不记录）。 */
    public void reset() {
        synchronized (lock) {
            stopLocked(false);
        }
        notifyState();
        notifyTick();
    }

    private void stopLocked(boolean record) {
        long now = SystemClock.elapsedRealtime();
        boolean wasRunning = state == State.ACTIVE || state == State.PAUSED;
        if (record && wasRunning && app != null) {
            long done = elapsedWorkMs(now);
            if (done > 0L) {
                try {
                    Stats.get(app).add(done);
                } catch (Throwable t) {
                    Log.w(TAG, "stats", t);
                }
            }
        }
        if (zh != null) {
            zh.removeCallbacksAndMessages(null);
        }
        releaseWake();
        runners.clear();
        plan = null;
        if (app != null) {
            try {
                Settings.get(app).clearSession();
            } catch (Throwable ignored) {
            }
        }
        cfg = null;
        state = State.IDLE;
        Speaker.get().stop();
    }

    public Status status() {
        synchronized (lock) {
            return snapshot(SystemClock.elapsedRealtime());
        }
    }

    // ------------------------------------------------------------------ 内部

    private void ensureThread() {
        if (thread == null || !thread.isAlive()) {
            thread = new HandlerThread("zenxi-timer");
            thread.start();
            zh = new Handler(thread.getLooper());
        }
    }

    private long elapsedWorkMs(long now) {
        if (plan == null) {
            return 0L;
        }
        long sum = 0L;
        for (Timeline.Seg s : plan.segs) {
            if (s.phase != Timeline.PHASE_WORK) {
                continue;
            }
            if (now >= s.end) {
                sum += s.duration();
            } else if (now > s.start) {
                sum += now - s.start;
            }
        }
        return sum;
    }

    private void postCues(long now) {
        if (zh == null || plan == null) {
            return;
        }
        long baseUptime = SystemClock.uptimeMillis();
        long baseElapsed = SystemClock.elapsedRealtime();
        for (CueRunner r : runners) {
            if (r.cue.at >= now - 40L) {
                long delay = Math.max(0L, r.cue.at - baseElapsed);
                zh.postAtTime(r, baseUptime + delay);
            }
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean again = false;
            synchronized (lock) {
                if (state == State.ACTIVE) {
                    if (SystemClock.elapsedRealtime() >= endElapsed) {
                        completeLocked();
                    } else {
                        notifyTick();
                        again = true;
                    }
                }
            }
            if (again && zh != null) {
                zh.postDelayed(this, 100L);
            }
        }
    };

    private void completeLocked() {
        final long worked = totalWorkMs;
        final int rounds = cfg == null ? 1 : cfg.rounds;
        state = State.DONE;
        if (zh != null) {
            zh.removeCallbacks(ticker);
        }
        releaseWake();
        if (app != null) {
            try {
                Stats.get(app).add(worked);
                Settings.get(app).clearSession();
            } catch (Throwable t) {
                Log.w(TAG, "finish bookkeeping", t);
            }
        }
        if (cfg != null && cfg.voice && app != null) {
            Speaker.get().speak(app, "练习结束");
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : copyListeners()) {
                    try {
                        l.onFinished(worked, rounds);
                    } catch (Throwable t) {
                        Log.w(TAG, "listener", t);
                    }
                }
            }
        });
    }

    private Status snapshot(long now) {
        Status s = new Status();
        s.state = state;
        if (plan == null || cfg == null) {
            s.rounds = 1;
            s.round = 1;
            s.phase = Phase.WORK;
            return s;
        }
        s.rounds = Math.max(1, cfg.rounds);
        s.sessionTotalMs = endElapsed - startElapsed;
        s.sessionRemainMs = Math.max(0L, endElapsed - now);
        s.progress = s.sessionTotalMs <= 0L ? 1f
                : Math.min(1f, Math.max(0f, (now - startElapsed) / (float) s.sessionTotalMs));

        Timeline.Seg cur = Timeline.segmentAt(plan.segs, now);
        if (cur == null) {
            return s;
        }
        s.phase = phaseOf(cur.phase);
        s.round = cur.round;
        s.phaseMs = cur.duration();
        s.remainMs = Math.max(0L, cur.end - now);
        s.tailing = cur.phase == Timeline.PHASE_WORK && cfg.tailBell
                && s.remainMs <= 4000L && s.phaseMs >= Timeline.MIN_TAIL_MS;
        s.intervalRemainMs = Timeline.nextIntervalMs(cfg, cur, now);
        if (!s.tailing && s.intervalRemainMs >= 0L && s.intervalRemainMs <= 4000L) {
            // 整分铃进入最后 4 秒时，圆环同样转暖
            s.tailing = true;
        }
        s.breathPhase = Timeline.breathPhaseAt(cfg, cur, now);
        return s;
    }

    private static Phase phaseOf(int p) {
        if (p == Timeline.PHASE_PREPARE) {
            return Phase.PREPARE;
        }
        if (p == Timeline.PHASE_REST) {
            return Phase.REST;
        }
        return Phase.WORK;
    }

    // ------------------------------------------------------------------ 出声

    private void fire(Timeline.Cue cue) {
        TimerConfig c;
        Context ctx;
        synchronized (lock) {
            c = cfg;
            ctx = app;
        }
        if (c == null || ctx == null) {
            return;
        }
        float vol = Math.max(0f, Math.min(1f, c.volume)) * cue.gain;
        SoundBank sb = SoundBank.get();
        switch (cue.kind) {
            case CUE_DING:
                sb.ding(c.tone, vol);
                break;
            case CUE_DANG:
                sb.dang(c.tone, vol);
                break;
            case CUE_TICK:
                sb.tick(vol);
                break;
            case CUE_START:
                sb.start(c.startTone, vol);
                if (c.voice) {
                    Speaker.get().speak(ctx, cue.round <= 1 ? "开始练习" : "第" + cue.round + "组开始");
                }
                break;
            case CUE_HALF:
                sb.half(vol);
                break;
            case CUE_END:
                sb.settle(vol);
                break;
            case CUE_REST:
                sb.half(vol);
                if (c.voice) {
                    Speaker.get().speak(ctx, "休息");
                }
                break;
            case CUE_BREATH_IN:
                sb.breathIn(vol);
                break;
            case CUE_BREATH_OUT:
                sb.breathOut(vol);
                break;
            default:
                break;
        }
        if (c.vibrate) {
            vibrate(cue.kind);
        }
        final int kind = cue.kind;
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : copyListeners()) {
                    try {
                        l.onCue(kind);
                    } catch (Throwable t) {
                        Log.w(TAG, "listener", t);
                    }
                }
            }
        });
    }

    private void vibrate(int cue) {
        Vibrator v = vibrator;
        if (v == null || !v.hasVibrator()) {
            return;
        }
        try {
            long[] timings;
            int[] amps;
            switch (cue) {
                case CUE_DING:
                    timings = new long[]{0, 26};
                    amps = new int[]{0, 150};
                    break;
                case CUE_DANG:
                    timings = new long[]{0, 55};
                    amps = new int[]{0, 235};
                    break;
                case CUE_TICK:
                    timings = new long[]{0, 16};
                    amps = new int[]{0, 90};
                    break;
                case CUE_END:
                    timings = new long[]{0, 55, 130, 95};
                    amps = new int[]{0, 220, 0, 110};
                    break;
                case CUE_REST:
                    timings = new long[]{0, 30, 90, 30};
                    amps = new int[]{0, 120, 0, 120};
                    break;
                default:
                    return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
            } else {
                legacyVibrate(v, timings);
            }
        } catch (Throwable t) {
            Log.w(TAG, "vibrate", t);
        }
    }

    @SuppressWarnings("deprecation")
    private void legacyVibrate(Vibrator v, long[] timings) {
        v.vibrate(timings, -1);
    }

    // ------------------------------------------------------------------ 唤醒锁

    private void acquireWake(long now) {
        try {
            if (wake == null) {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zenxi:timer");
                wake.setReferenceCounted(false);
            }
            long timeout = Math.max(15000L, endElapsed - now + 90000L);
            if (wake.isHeld()) {
                wake.release();
            }
            wake.acquire(timeout);
        } catch (Throwable t) {
            Log.w(TAG, "wakelock", t);
        }
    }

    private void releaseWake() {
        try {
            if (wake != null && wake.isHeld()) {
                wake.release();
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ 回调分发

    private List<Listener> copyListeners() {
        synchronized (lock) {
            return new ArrayList<>(listeners);
        }
    }

    private void notifyState() {
        final State s = state;
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : copyListeners()) {
                    try {
                        l.onState(s);
                    } catch (Throwable t) {
                        Log.w(TAG, "listener", t);
                    }
                }
            }
        });
    }

    private void notifyTick() {
        final Status s;
        synchronized (lock) {
            s = snapshot(SystemClock.elapsedRealtime());
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : copyListeners()) {
                    try {
                        l.onTick(s);
                    } catch (Throwable t) {
                        Log.w(TAG, "listener", t);
                    }
                }
            }
        });
    }
}
