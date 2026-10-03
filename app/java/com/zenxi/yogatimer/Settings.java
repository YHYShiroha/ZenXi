package com.zenxi.yogatimer;

import android.content.Context;
import android.content.SharedPreferences;

/** SharedPreferences 封装：所有开关与偏好。 */
public final class Settings {

    private static final String FILE = "zenxi_settings";
    private static Settings instance;

    public static synchronized Settings get(Context c) {
        if (instance == null) {
            instance = new Settings(c.getApplicationContext());
        }
        return instance;
    }

    private final SharedPreferences sp;

    private Settings(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private SharedPreferences p() {
        return sp;
    }

    // ---- 时长与组数 -------------------------------------------------

    public long workMs() {
        return p().getLong("workMs", 60000L);
    }

    public void setWorkMs(long v) {
        p().edit().putLong("workMs", Math.max(1000L, v)).apply();
    }

    public int rounds() {
        return p().getInt("rounds", 1);
    }

    public void setRounds(int v) {
        p().edit().putInt("rounds", Math.max(1, Math.min(20, v))).apply();
    }

    public long restMs() {
        return p().getLong("restMs", 30000L);
    }

    public void setRestMs(long v) {
        p().edit().putLong("restMs", Math.max(5000L, v)).apply();
    }

    public int prepareSec() {
        return p().getInt("prepareSec", 3);
    }

    public void setPrepareSec(int v) {
        p().edit().putInt("prepareSec", Math.max(0, Math.min(30, v))).apply();
    }

    // ---- 提示音 ------------------------------------------------------

    public boolean tailBell() {
        return p().getBoolean("tailBell", true);
    }

    public void setTailBell(boolean v) {
        p().edit().putBoolean("tailBell", v).apply();
    }

    public int tailMode() {
        return p().getInt("tailMode", 0);
    }

    public void setTailMode(int v) {
        p().edit().putInt("tailMode", v).apply();
    }

    public boolean endBell() {
        return p().getBoolean("endBell", true);
    }

    public void setEndBell(boolean v) {
        p().edit().putBoolean("endBell", v).apply();
    }

    public boolean halfBell() {
        return p().getBoolean("halfBell", false);
    }

    public void setHalfBell(boolean v) {
        p().edit().putBoolean("halfBell", v).apply();
    }

    /** 整分铃间隔（毫秒），0 表示关闭。 */
    public long intervalMs() {
        return p().getLong("intervalMs", 0L);
    }

    public void setIntervalMs(long v) {
        long clamped = v <= 0L ? 0L : Math.max(60000L, Math.min(3600000L, v));
        p().edit().putLong("intervalMs", clamped).apply();
    }

    public int tone() {
        return p().getInt("tone", 0);
    }

    public void setTone(int v) {
        p().edit().putInt("tone", Math.max(0, Math.min(4, v))).apply();
    }

    public int startTone() {
        return p().getInt("startTone", 0);
    }

    public void setStartTone(int v) {
        p().edit().putInt("startTone", Math.max(0, Math.min(5, v))).apply();
    }

    public float volume() {
        return p().getFloat("volume", 0.85f);
    }

    public void setVolume(float v) {
        p().edit().putFloat("volume", Math.max(0f, Math.min(1f, v))).apply();
    }

    // ---- 节奏与反馈 --------------------------------------------------

    public int breath() {
        return p().getInt("breath", 0);
    }

    public void setBreath(int v) {
        p().edit().putInt("breath", v).apply();
    }

    public boolean voice() {
        return p().getBoolean("voice", false);
    }

    public void setVoice(boolean v) {
        p().edit().putBoolean("voice", v).apply();
    }

    public boolean vibrate() {
        return p().getBoolean("vibrate", true);
    }

    public void setVibrate(boolean v) {
        p().edit().putBoolean("vibrate", v).apply();
    }

    public boolean keepScreen() {
        return p().getBoolean("keepScreen", true);
    }

    public void setKeepScreen(boolean v) {
        p().edit().putBoolean("keepScreen", v).apply();
    }

    public boolean dark() {
        return p().getBoolean("dark", true);
    }

    public void setDark(boolean v) {
        p().edit().putBoolean("dark", v).apply();
    }

    public boolean hintIgnoresOptimization() {
        return p().getBoolean("askedBattery", false);
    }

    public void setHintIgnoresOptimization() {
        p().edit().putBoolean("askedBattery", true).apply();
    }

    // ---- 组合 --------------------------------------------------------

    public TimerConfig config() {
        TimerConfig c = new TimerConfig();
        c.workMs = workMs();
        c.rounds = rounds();
        c.restMs = restMs();
        c.prepareSec = prepareSec();
        c.tailBell = tailBell();
        c.tailMode = tailMode();
        c.endBell = endBell();
        c.halfBell = halfBell();
        c.intervalMs = intervalMs();
        c.breath = breath();
        c.voice = voice();
        c.vibrate = vibrate();
        c.tone = tone();
        c.startTone = startTone();
        c.volume = volume();
        return c;
    }

    // ---- 进程被杀后的续跑 --------------------------------------------

    public void saveSession(long endWallClock, long totalWorkMs, int rounds) {
        p().edit().putLong("sEnd", endWallClock)
                .putLong("sWork", totalWorkMs)
                .putInt("sRounds", rounds)
                .apply();
    }

    public void clearSession() {
        p().edit().remove("sEnd").remove("sWork").remove("sRounds").apply();
    }

    public long sessionEnd() {
        return p().getLong("sEnd", 0L);
    }

    public long sessionWork() {
        return p().getLong("sWork", 0L);
    }

    public int sessionRounds() {
        return p().getInt("sRounds", 1);
    }
}
