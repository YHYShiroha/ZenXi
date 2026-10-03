package com.zenxi.yogatimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 纯 JVM 单元测试：验证练习时间线与提示音时刻（无需 Android 设备）。
 *
 * 运行:  powershell -File tools\test_timeline.ps1
 */
public final class TimelineTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testDefaultOneMinute();
        testEndAccentMode();
        testMultiRound();
        testTooShortWork();
        testNoTailNoEndBell();
        testHalfBell();
        testIntervalBell();
        testIntervalEdgeCases();
        testBreathGuide();
        testPauseShiftIsTimeBased();

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ 用例

    private static void testDefaultOneMinute() {
        TimerConfig cfg = base();
        Timeline.Plan plan = Timeline.build(cfg, 1000L);

        title("默认：准备 3 秒 + 练习 1 分钟 + 末尾四声铃（连响）");
        print(plan);

        eq("总时长 = 3s + 60s", 63000L, plan.end - plan.start);
        eq("练习时长 = 60s", 60000L, plan.workMs());
        eq("段数 = 2", 2, plan.segs.size());

        List<Timeline.Cue> tail = tailOf(plan, 1);
        eq("末尾提示音数量 = 4", 4, tail.size());
        eq("第 1 声 叮 在 -4 秒", plan.start + 59000L, tail.get(0).at);
        eq("第 1 声类型 = 叮", Timeline.CUE_DING, tail.get(0).kind);
        eq("第 2 声 叮 在 -3 秒", plan.start + 60000L, tail.get(1).at);
        eq("第 3 声 叮 在 -2 秒", plan.start + 61000L, tail.get(2).at);
        eq("第 4 声 铛 在 -1 秒", plan.start + 62000L, tail.get(3).at);
        eq("第 4 声类型 = 铛", Timeline.CUE_DANG, tail.get(3).kind);

        Timeline.Cue end = firstOf(plan, Timeline.CUE_END);
        check("结束长铃存在", end != null);
        eq("结束长铃在终点后 180ms", plan.start + 63180L, end.at);

        int ticks = countOf(plan, Timeline.CUE_TICK);
        eq("准备阶段 3 声轻点", 3, ticks);
        eq("起势音 1 声", 1, countOf(plan, Timeline.CUE_START));
    }

    private static void testEndAccentMode() {
        TimerConfig cfg = base();
        cfg.tailMode = 1;
        Timeline.Plan plan = Timeline.build(cfg, 0L);

        title("终点重音：叮 叮 叮 铛，铛正好落在结束点");
        print(plan);

        List<Timeline.Cue> tail = tailOf(plan, 1);
        eq("末尾提示音数量 = 4", 4, tail.size());
        eq("叮 在 -3 秒（练习从 3s 起算）", 60000L, tail.get(0).at);
        eq("叮 在 -2 秒", 61000L, tail.get(1).at);
        eq("叮 在 -1 秒", 62000L, tail.get(2).at);
        eq("铛 正好在结束点 63s", 63000L, tail.get(3).at);
        eq("铛 类型正确", Timeline.CUE_DANG, tail.get(3).kind);
        eq("结束长铃顺延到 +900ms", 63900L, firstOf(plan, Timeline.CUE_END).at);
    }

    private static void testMultiRound() {
        TimerConfig cfg = base();
        cfg.rounds = 3;
        cfg.restMs = 30000L;
        Timeline.Plan plan = Timeline.build(cfg, 0L);

        title("3 组 × 1 分钟，组间休息 30 秒");
        print(plan);

        eq("段数 = 准备 + (练习/休息) × 2 + 练习 = 6", 6, plan.segs.size());
        eq("总时长 = 3 + 3×60 + 2×30 秒", 243000L, plan.end - plan.start);
        eq("累计练习时长 = 3 分钟", 180000L, plan.workMs());
        eq("每段结束各有一记铛", 3, countOf(plan, Timeline.CUE_DANG));
        eq("收束长铃只有一记", 1, countOf(plan, Timeline.CUE_END));
        eq("休息起音 2 次", 2, countOf(plan, Timeline.CUE_REST));
        eq("起势音 3 次（每组一次）", 3, countOf(plan, Timeline.CUE_START));

        Timeline.Cue end = firstOf(plan, Timeline.CUE_END);
        eq("收束长铃在整场结束之后", plan.segs.get(5).end + 180L, end.at);
    }

    private static void testTooShortWork() {
        TimerConfig cfg = base();
        cfg.workMs = 4000L;
        cfg.prepareSec = 0;
        Timeline.Plan plan = Timeline.build(cfg, 0L);

        title("极短练习（4 秒）：只落一记铛");
        print(plan);
        eq("铛 1 声", 1, countOf(plan, Timeline.CUE_DANG));
        eq("没有余量逐秒响", 0, countOf(plan, Timeline.CUE_DING));
    }

    private static void testNoTailNoEndBell() {
        TimerConfig cfg = base();
        cfg.tailBell = false;
        cfg.endBell = false;
        Timeline.Plan plan = Timeline.build(cfg, 0L);

        title("尾铃与收束铃都关闭：结束仍有一声收束");
        eq("收束音 1 声", 1, countOf(plan, Timeline.CUE_END));
        eq("无叮/铛", 0, countOf(plan, Timeline.CUE_DING) + countOf(plan, Timeline.CUE_DANG));
    }

    private static void testHalfBell() {
        TimerConfig cfg = base();
        cfg.workMs = 120000L;
        cfg.halfBell = true;
        Timeline.Plan plan = Timeline.build(cfg, 0L);

        title("2 分钟 + 半程轻铃");
        Timeline.Cue half = firstOf(plan, Timeline.CUE_HALF);
        check("半程轻铃存在", half != null);
        eq("半程轻铃落在中间", 3000L + 60000L, half.at);
    }

    private static void testIntervalBell() {
        TimerConfig cfg = base();
        cfg.workMs = 30 * 60000L;   // 一组 30 分钟
        cfg.prepareSec = 0;
        cfg.intervalMs = 60000L;    // 每 1 分钟末尾来一轮
        Timeline.Plan plan = Timeline.build(cfg, 0L);

        title("30 分钟练习 + 整分铃 1 分钟（每分钟末尾：叮 叮 叮 铛）");

        eq("铛 30 声（29 次整分 + 最后结束）", 30, countOf(plan, Timeline.CUE_DANG));
        eq("叮 90 声", 90, countOf(plan, Timeline.CUE_DING));
        eq("整场时长仍是 30 分钟", 1800000L, plan.end - plan.start);

        List<Timeline.Cue> tail = tailOf(plan, 1);
        eq("第 1 轮四声：59 秒处收尾", 59000L, tail.get(3).at);
        eq("第 1 轮第 1 声在 56 秒", 56000L, tail.get(0).at);
        eq("第 2 轮收尾在 119 秒", 119000L, tail.get(7).at);
        eq("最后一轮「铛」在 29:59（连响模式）", 1799000L, tail.get(tail.size() - 1).at);

        Timeline.Seg work = plan.segs.get(0);
        eq("第 10 秒时距下次铃 50 秒", 50000L, Timeline.nextIntervalMs(cfg, work, 10000L));
        eq("第 58 秒时距下次铃 2 秒", 2000L, Timeline.nextIntervalMs(cfg, work, 58000L));
    }

    private static void testIntervalEdgeCases() {
        TimerConfig cfg = base();
        cfg.prepareSec = 0;

        cfg.workMs = 12 * 60000L;
        cfg.intervalMs = 5 * 60000L;
        Timeline.Plan a = Timeline.build(cfg, 0L);
        title("12 分钟练习 + 整分铃 5 分钟：只在 5 / 10 分钟与结束响");
        eq("铛 3 声", 3, countOf(a, Timeline.CUE_DANG));

        cfg.workMs = 2 * 60000L;
        cfg.intervalMs = 10 * 60000L;
        Timeline.Plan b = Timeline.build(cfg, 0L);
        title("间隔比练习还长：不额外插入，只在结束响一轮");
        eq("铛 1 声", 1, countOf(b, Timeline.CUE_DANG));
        eq("叮 3 声", 3, countOf(b, Timeline.CUE_DING));

        cfg.workMs = 30 * 60000L;
        cfg.intervalMs = 60000L;
        cfg.tailMode = 1;
        Timeline.Plan c = Timeline.build(cfg, 0L);
        title("终点重音模式下整分铃：铛落在每个整分点");
        List<Timeline.Cue> t = tailOf(c, 1);
        eq("第 1 个整分点 60 秒", 60000L, t.get(3).at);
        eq("第 2 个整分点 120 秒", 120000L, t.get(7).at);
    }

    private static void testBreathGuide() {
        TimerConfig cfg = base();
        cfg.workMs = 32000L;
        cfg.prepareSec = 0;
        cfg.breath = 1; // 4-4-4-4
        Timeline.Plan plan = Timeline.build(cfg, 0L);

        title("呼吸引导 4-4-4-4（吸气 4 秒 / 屏息 4 秒 / 呼气 4 秒 / 屏息 4 秒）");
        int in = countOf(plan, Timeline.CUE_BREATH_IN);
        int out = countOf(plan, Timeline.CUE_BREATH_OUT);
        eq("吸气提示 2 次", 2, in);
        eq("呼气提示 2 次（32 秒内 2 个完整循环的呼气都落在区间内）", 2, out);

        Timeline.Seg work = plan.segs.get(0);
        eq("0 秒处于吸气", 0, Timeline.breathPhaseAt(cfg, work, 1000L));
        eq("6 秒处于屏息", 1, Timeline.breathPhaseAt(cfg, work, 6000L));
        eq("10 秒处于呼气", 2, Timeline.breathPhaseAt(cfg, work, 10000L));
        eq("14 秒处于屏息", 3, Timeline.breathPhaseAt(cfg, work, 14000L));
        eq("未启用时返回 -1", -1, Timeline.breathPhaseAt(base(), work, 1000L));
    }

    /** 暂停 / 继续是靠整体平移时间轴实现的，这里验证平移后间隔不变。 */
    private static void testPauseShiftIsTimeBased() {
        TimerConfig cfg = base();
        Timeline.Plan plan = Timeline.build(cfg, 0L);
        long baseGap = firstOf(plan, Timeline.CUE_END).at - plan.start;

        long shift = 5000L; // 暂停 5 秒
        for (Timeline.Seg s : plan.segs) {
            s.start += shift;
            s.end += shift;
        }
        for (Timeline.Cue c : plan.cues) {
            if (c.at > 0L) {
                c.at += shift;
            }
        }
        plan.end += shift;

        title("暂停 5 秒后恢复：所有提示音整体平移");
        List<Timeline.Cue> tail = tailOf(plan, 1);
        eq("铛 相对终点仍是 -1 秒", plan.segs.get(1).end - 1000L, tail.get(3).at);
        eq("整体时长不变", baseGap, firstOf(plan, Timeline.CUE_END).at - plan.segs.get(0).start);
    }

    // ------------------------------------------------------------------ 工具

    private static TimerConfig base() {
        TimerConfig cfg = new TimerConfig();
        cfg.workMs = 60000L;
        cfg.rounds = 1;
        cfg.restMs = 30000L;
        cfg.prepareSec = 3;
        cfg.tailBell = true;
        cfg.tailMode = 0;
        cfg.endBell = true;
        cfg.halfBell = false;
        cfg.breath = 0;
        return cfg;
    }

    /** 某组练习里的末尾提示音（叮/铛），按时间排序。 */
    private static List<Timeline.Cue> tailOf(Timeline.Plan plan, int round) {
        List<Timeline.Cue> out = new ArrayList<>();
        for (Timeline.Cue c : plan.cues) {
            if (c.round == round && (c.kind == Timeline.CUE_DING || c.kind == Timeline.CUE_DANG)) {
                out.add(c);
            }
        }
        Collections.sort(out, new Comparator<Timeline.Cue>() {
            @Override
            public int compare(Timeline.Cue a, Timeline.Cue b) {
                return Long.compare(a.at, b.at);
            }
        });
        return out;
    }

    private static Timeline.Cue firstOf(Timeline.Plan plan, int kind) {
        for (Timeline.Cue c : plan.cues) {
            if (c.kind == kind) {
                return c;
            }
        }
        return null;
    }

    private static int countOf(Timeline.Plan plan, int kind) {
        int n = 0;
        for (Timeline.Cue c : plan.cues) {
            if (c.kind == kind) {
                n++;
            }
        }
        return n;
    }

    private static void print(Timeline.Plan plan) {
        List<Timeline.Cue> sorted = new ArrayList<>(plan.cues);
        Collections.sort(sorted, new Comparator<Timeline.Cue>() {
            @Override
            public int compare(Timeline.Cue a, Timeline.Cue b) {
                return Long.compare(a.at, b.at);
            }
        });
        for (Timeline.Seg s : plan.segs) {
            System.out.printf("   段 %-6s 组%d  %6.1fs - %6.1fs%n",
                    phaseName(s.phase), s.round, (s.start - plan.start) / 1000.0,
                    (s.end - plan.start) / 1000.0);
        }
        for (Timeline.Cue c : sorted) {
            System.out.printf("      %6.1fs  %s%n", (c.at - plan.start) / 1000.0, cueName(c.kind));
        }
    }

    private static String phaseName(int p) {
        if (p == Timeline.PHASE_PREPARE) {
            return "准备";
        }
        if (p == Timeline.PHASE_REST) {
            return "休息";
        }
        return "练习";
    }

    private static String cueName(int k) {
        switch (k) {
            case Timeline.CUE_DING:
                return "叮";
            case Timeline.CUE_DANG:
                return "铛";
            case Timeline.CUE_TICK:
                return "滴（准备）";
            case Timeline.CUE_START:
                return "起";
            case Timeline.CUE_HALF:
                return "半程轻铃";
            case Timeline.CUE_END:
                return "收束长铃";
            case Timeline.CUE_REST:
                return "休息铃";
            case Timeline.CUE_BREATH_IN:
                return "吸气";
            case Timeline.CUE_BREATH_OUT:
                return "呼气";
            default:
                return "?";
        }
    }

    private static void title(String t) {
        System.out.println();
        System.out.println("── " + t);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("   [PASS] " + name);
        } else {
            failed++;
            System.out.println("   [FAIL] " + name);
        }
    }

    private static void eq(String name, long expected, long actual) {
        if (expected == actual) {
            passed++;
            System.out.println("   [PASS] " + name);
        } else {
            failed++;
            System.out.println("   [FAIL] " + name + "  expected=" + expected + " actual=" + actual);
        }
    }

    private static void eq(String name, int expected, int actual) {
        eq(name, (long) expected, (long) actual);
    }
}
