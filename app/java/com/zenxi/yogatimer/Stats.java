package com.zenxi.yogatimer;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** 练习记录：按天累计、连续天数、近七日。 */
public final class Stats {

    private static final String FILE = "zenxi_stats";
    private static Stats instance;

    public static synchronized Stats get(Context c) {
        if (instance == null) {
            instance = new Stats(c.getApplicationContext());
        }
        return instance;
    }

    private final SharedPreferences sp;
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private Stats(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private String key(long dayOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, (int) dayOffset);
        return "d:" + df.format(cal.getTime());
    }

    /** 记录一次完成的练习（毫秒）。 */
    public void add(long ms) {
        if (ms <= 0) {
            return;
        }
        String k = key(0);
        long now = sp.getLong(k, 0L) + ms;
        SharedPreferences.Editor e = sp.edit();
        e.putLong(k, now);
        e.putLong("total", sp.getLong("total", 0L) + ms);
        e.putInt("sessions", sp.getInt("sessions", 0) + 1);
        e.apply();
    }

    public long today() {
        return sp.getLong(key(0), 0L);
    }

    public long week() {
        long sum = 0;
        for (int i = 0; i < 7; i++) {
            sum += sp.getLong(key(-i), 0L);
        }
        return sum;
    }

    public long total() {
        return sp.getLong("total", 0L);
    }

    public int sessions() {
        return sp.getInt("sessions", 0);
    }

    /** 连续练习天数（以今天或昨天为终点）。 */
    public int streak() {
        int start = today() > 0 ? 0 : 1;
        int n = 0;
        for (int i = start; i < 400; i++) {
            if (sp.getLong(key(-i), 0L) > 0) {
                n++;
            } else {
                break;
            }
        }
        return n;
    }

    /** 近 7 天秒数，index 0 = 6 天前，index 6 = 今天。 */
    public long[] last7() {
        long[] out = new long[7];
        for (int i = 0; i < 7; i++) {
            out[6 - i] = sp.getLong(key(-i), 0L) / 1000L;
        }
        return out;
    }

    /** 近 7 天的星期标签。 */
    public String[] last7Labels() {
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        String[] out = new String[7];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -(6 - i));
            out[i] = names[c.get(Calendar.DAY_OF_WEEK) - 1];
        }
        return out;
    }
}
