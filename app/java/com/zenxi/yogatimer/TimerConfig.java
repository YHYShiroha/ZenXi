package com.zenxi.yogatimer;

/** 一次练习的全部参数快照。 */
public final class TimerConfig {

    /** 每组练习时长（毫秒）。 */
    public long workMs = 60000L;
    /** 组数。 */
    public int rounds = 1;
    /** 组间休息（毫秒）。 */
    public long restMs = 30000L;
    /** 准备倒计时（秒），0 表示跳过。 */
    public int prepareSec = 3;

    /** 末尾四声铃：叮 叮 叮 铛。 */
    public boolean tailBell = true;
    /** 0 = 连响（-4/-3/-2/-1 秒），1 = 终点重音（-3/-2/-1/0 秒）。 */
    public int tailMode = 0;
    /** 结束后再落一记悠长收束铃。 */
    public boolean endBell = true;
    /** 半程轻铃。 */
    public boolean halfBell = false;
    /** 整分铃：练习过程中每隔这么久，末尾再响一轮「叮 叮 叮 铛」。0 = 关闭。 */
    public long intervalMs = 0L;

    /** 呼吸引导：0 = 关闭，1 = 4-4-4-4，2 = 4-7-8，3 = 5-5。 */
    public int breath = 0;
    /** 语音报时。 */
    public boolean voice = false;
    /** 震动同步。 */
    public boolean vibrate = true;

    /** 音色：0 铜铃 1 水晶 2 颂钵 3 木鱼 4 水滴。 */
    public int tone = 0;
    /** 起势音：0 双铃 1 清铃 2 滑音 3 木鱼 4 水滴 5 静音。 */
    public int startTone = 0;
    /** 音量 0..1。 */
    public float volume = 0.85f;

    public TimerConfig copy() {
        TimerConfig c = new TimerConfig();
        c.workMs = workMs;
        c.rounds = rounds;
        c.restMs = restMs;
        c.prepareSec = prepareSec;
        c.tailBell = tailBell;
        c.tailMode = tailMode;
        c.endBell = endBell;
        c.halfBell = halfBell;
        c.intervalMs = intervalMs;
        c.breath = breath;
        c.voice = voice;
        c.vibrate = vibrate;
        c.tone = tone;
        c.startTone = startTone;
        c.volume = volume;
        return c;
    }
}
