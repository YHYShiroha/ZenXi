package com.zenxi.yogatimer;

import java.util.ArrayList;
import java.util.List;

/**
 * 练习时间线：把一次练习切成若干段（准备 / 练习 / 休息），
 * 并算出每一记提示音的绝对时刻。
 *
 * 这里刻意不依赖任何 Android API —— 纯 Java，便于直接用单元测试验证
 * 「末尾 4 秒：叮 叮 叮 铛」这类核心行为。
 *
 * 所有时间单位：毫秒。时间轴统一按设备开机时长（elapsedRealtime）计算，
 * 由调用方传入起点 now。
 */
public final class Timeline {

    public static final int PHASE_PREPARE = 0;
    public static final int PHASE_WORK = 1;
    public static final int PHASE_REST = 2;

    public static final int CUE_DING = 1;
    public static final int CUE_DANG = 2;
    public static final int CUE_TICK = 3;
    public static final int CUE_START = 4;
    public static final int CUE_HALF = 5;
    public static final int CUE_END = 6;
    public static final int CUE_REST = 7;
    public static final int CUE_BREATH_IN = 8;
    public static final int CUE_BREATH_OUT = 9;

    /** 时长小于该值时，末尾不再逐秒响，只在结束点落一声。 */
    public static final long MIN_TAIL_MS = 5200L;
    /** 整分铃间隔的下限（留足 4 秒提示空间）。 */
    public static final long MIN_INTERVAL_MS = 6000L;

    public static final class Seg {
        public final int phase;
        public final int round;
        public long start;
        public long end;

        Seg(int phase, int round, long start, long end) {
            this.phase = phase;
            this.round = round;
            this.start = start;
            this.end = end;
        }

        public long duration() {
            return end - start;
        }
    }

    public static final class Cue {
        public long at;
        public final int kind;
        public final float gain;
        public final int round;

        Cue(long at, int kind, float gain, int round) {
            this.at = at;
            this.kind = kind;
            this.gain = gain;
            this.round = round;
        }
    }

    public static final class Plan {
        public final List<Seg> segs = new ArrayList<>();
        public final List<Cue> cues = new ArrayList<>();
        public long start;
        public long end;

        /** 总练习时长（不含组间休息与准备）。 */
        public long workMs() {
            long sum = 0L;
            for (Seg s : segs) {
                if (s.phase == PHASE_WORK) {
                    sum += s.duration();
                }
            }
            return sum;
        }
    }

    private Timeline() {
    }

    public static Plan build(TimerConfig cfg, long now) {
        Plan plan = new Plan();
        plan.start = now;
        int rounds = Math.max(1, cfg.rounds);
        long t = now;

        if (cfg.prepareSec > 0) {
            long end = t + cfg.prepareSec * 1000L;
            plan.segs.add(new Seg(PHASE_PREPARE, 1, t, end));
            long dur = end - t;
            for (long k = 0; k + 150L < dur; k += 1000L) {
                plan.cues.add(new Cue(t + k, CUE_TICK, 0.55f, 1));
            }
            t = end;
        }

        for (int r = 1; r <= rounds; r++) {
            long we = t + Math.max(500L, cfg.workMs);
            plan.segs.add(new Seg(PHASE_WORK, r, t, we));
            workCues(plan, cfg, t, we, r, rounds);
            t = we;

            if (r < rounds && cfg.restMs > 0) {
                long re = t + cfg.restMs;
                plan.segs.add(new Seg(PHASE_REST, r, t, re));
                plan.cues.add(new Cue(t, CUE_REST, 0.75f, r));
                if (re - t > 2500L) {
                    plan.cues.add(new Cue(re - 1500L, CUE_TICK, 0.6f, r));
                }
                t = re;
            }
        }

        plan.end = t;
        return plan;
    }

    private static void workCues(Plan plan, TimerConfig cfg, long s, long e, int round, int rounds) {
        long dur = e - s;
        plan.cues.add(new Cue(s, CUE_START, 1f, round));

        if (cfg.halfBell && dur >= 90000L) {
            plan.cues.add(new Cue(s + dur / 2L, CUE_HALF, 0.8f, round));
        }

        if (cfg.breath > 0) {
            breathCues(plan, cfg, s, e, round);
        }

        boolean last = round == rounds;
        if (cfg.tailBell && dur >= MIN_TAIL_MS) {
            tailAt(plan, cfg, e, round);
        } else if (cfg.tailBell) {
            plan.cues.add(new Cue(e, CUE_DANG, 1f, round));
        }

        // 整分铃：每隔 intervalMs 的末尾也来一轮四声铃（终点那一轮已由上面处理）
        long interval = cfg.intervalMs;
        if (interval >= MIN_INTERVAL_MS && interval + 200L < dur) {
            for (long k = interval; k + 200L < dur; k += interval) {
                tailAt(plan, cfg, s + k, round);
            }
        }

        if (last) {
            if (cfg.endBell) {
                long at = (cfg.tailBell && cfg.tailMode == 1) ? e + 900L : e + 180L;
                plan.cues.add(new Cue(at, CUE_END, 1f, round));
            } else if (!cfg.tailBell) {
                // 两个铃都关掉时，至少保证结束有一声收束
                plan.cues.add(new Cue(e, CUE_END, 1f, round));
            }
        }
    }

    /** 在 endAt 处落下「叮 叮 叮 铛」。 */
    private static void tailAt(Plan plan, TimerConfig cfg, long endAt, int round) {
        if (cfg.tailMode == 1) {
            // 终点重音：最后一声「铛」正好落在这一刻
            plan.cues.add(new Cue(endAt - 3000L, CUE_DING, 1f, round));
            plan.cues.add(new Cue(endAt - 2000L, CUE_DING, 1f, round));
            plan.cues.add(new Cue(endAt - 1000L, CUE_DING, 1f, round));
            plan.cues.add(new Cue(endAt, CUE_DANG, 1f, round));
        } else {
            // 连响：末尾 4 秒 1 秒 1 声，铛在 -1 秒处（余韵盖过终点）
            plan.cues.add(new Cue(endAt - 4000L, CUE_DING, 1f, round));
            plan.cues.add(new Cue(endAt - 3000L, CUE_DING, 1f, round));
            plan.cues.add(new Cue(endAt - 2000L, CUE_DING, 1f, round));
            plan.cues.add(new Cue(endAt - 1000L, CUE_DANG, 1f, round));
        }
    }

    /** 距下一个整分铃还有多久；没有开启或不在练习段时返回 -1。 */
    public static long nextIntervalMs(TimerConfig cfg, Seg seg, long now) {
        if (cfg == null || seg == null || seg.phase != PHASE_WORK) {
            return -1L;
        }
        long interval = cfg.intervalMs;
        if (interval < MIN_INTERVAL_MS || interval + 200L >= seg.duration()) {
            return -1L;
        }
        long elapsed = Math.max(0L, now - seg.start);
        long rem = interval - (elapsed % interval);
        long toEnd = seg.end - now;
        if (rem > toEnd) {
            return -1L;
        }
        return rem;
    }

    private static void breathCues(Plan plan, TimerConfig cfg, long s, long e, int round) {
        int[] pattern = breathPattern(cfg.breath);
        if (pattern == null) {
            return;
        }
        int inhale = pattern[0];
        int hold1 = pattern[1];
        int exhale = pattern[2];
        int hold2 = pattern[3];
        long cycle = (inhale + hold1 + exhale + hold2) * 1000L;
        if (cycle <= 0) {
            return;
        }
        for (long t = s; t < e; t += cycle) {
            plan.cues.add(new Cue(t, CUE_BREATH_IN, 0.5f, round));
            long outAt = t + (inhale + hold1) * 1000L;
            if (outAt < e) {
                plan.cues.add(new Cue(outAt, CUE_BREATH_OUT, 0.5f, round));
            }
        }
    }

    /** 返回 {吸气, 屏息, 呼气, 屏息}，null 表示关闭。 */
    public static int[] breathPattern(int breath) {
        if (breath == 1) {
            return new int[]{4, 4, 4, 4};
        }
        if (breath == 2) {
            return new int[]{4, 7, 8, 0};
        }
        if (breath == 3) {
            return new int[]{5, 0, 5, 0};
        }
        return null;
    }

    /** 某个时刻处于呼吸的哪个阶段：0 吸 1 屏 2 呼 3 屏，-1 表示未启用。 */
    public static int breathPhaseAt(TimerConfig cfg, Seg seg, long now) {
        if (cfg == null || cfg.breath <= 0 || seg == null || seg.phase != PHASE_WORK) {
            return -1;
        }
        int[] p = breathPattern(cfg.breath);
        if (p == null) {
            return -1;
        }
        long cycle = (p[0] + p[1] + p[2] + p[3]) * 1000L;
        if (cycle <= 0) {
            return -1;
        }
        long x = Math.max(0L, now - seg.start) % cycle;
        if (x < p[0] * 1000L) {
            return 0;
        }
        x -= p[0] * 1000L;
        if (x < p[1] * 1000L) {
            return 1;
        }
        x -= p[1] * 1000L;
        if (x < p[2] * 1000L) {
            return 2;
        }
        return 3;
    }

    /** 找出某个时刻所处的段。 */
    public static Seg segmentAt(List<Seg> segs, long now) {
        if (segs == null || segs.isEmpty()) {
            return null;
        }
        Seg cur = segs.get(0);
        for (Seg s : segs) {
            if (now >= s.start && now < s.end) {
                return s;
            }
            if (now >= s.end) {
                cur = s;
            }
        }
        return cur;
    }
}
