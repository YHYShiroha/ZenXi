package com.zenxi.yogatimer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/** 尺寸、圆角、主题遍历等零碎工具。 */
public final class Ui {

    public static final Interpolator EASE = new PathInterpolator(0.22f, 0f, 0f, 1f);
    public static final Interpolator EASE_OUT = new PathInterpolator(0.16f, 0.84f, 0.44f, 1f);
    public static final Interpolator SOFT = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private Ui() {
    }

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics());
    }

    public static int alpha(int color, float f) {
        int a = Color.alpha(color);
        int na = Math.max(0, Math.min(255, Math.round(a * f)));
        return (color & 0x00FFFFFF) | (na << 24);
    }

    public static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    public static GradientDrawable rr(int fill, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(radius);
        return d;
    }

    public static GradientDrawable rr(int fill, float rTL, float rTR, float rBR, float rBL) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadii(new float[]{rTL, rTL, rTR, rTR, rBR, rBR, rBL, rBL});
        return d;
    }

    public static GradientDrawable rr(int fill, int stroke, float strokeWidth, float radius) {
        GradientDrawable d = rr(fill, radius);
        if (strokeWidth > 0f) {
            d.setStroke(Math.max(1, Math.round(strokeWidth)), stroke);
        }
        return d;
    }

    public static GradientDrawable glowPill(int cA, int cB, float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{cA, cB});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(radius);
        return d;
    }

    public static void setBackground(View v, android.graphics.drawable.Drawable d) {
        v.setBackground(d);
    }

    /** 递归套用主题：自绘控件走 Themed，文字按 tag 取色。 */
    public static void themeTree(View v, Palette p) {
        if (v instanceof Themed) {
            ((Themed) v).applyTheme(p);
        }
        if (v instanceof TextView) {
            Object tag = v.getTag();
            if (tag instanceof String) {
                String s = (String) tag;
                if ("hi".equals(s)) {
                    ((TextView) v).setTextColor(p.textHi);
                } else if ("mid".equals(s)) {
                    ((TextView) v).setTextColor(p.textMid);
                } else if ("low".equals(s)) {
                    ((TextView) v).setTextColor(p.textLow);
                } else if ("accent".equals(s)) {
                    ((TextView) v).setTextColor(p.accent);
                } else if ("amber".equals(s)) {
                    ((TextView) v).setTextColor(p.amber);
                } else if ("danger".equals(s)) {
                    ((TextView) v).setTextColor(p.danger);
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                themeTree(g.getChildAt(i), p);
            }
        }
    }

    /** 把秒数格式化成 mm:ss（超过一小时用 h:mm:ss）。 */
    public static String clock(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long total = (ms + 999) / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    public static String minutes(long ms) {
        long sec = Math.round(ms / 1000.0);
        if (sec < 60) {
            return sec + " 秒";
        }
        long m = sec / 60;
        long r = sec % 60;
        if (r == 0) {
            return m + " 分钟";
        }
        return m + " 分 " + r + " 秒";
    }
}
