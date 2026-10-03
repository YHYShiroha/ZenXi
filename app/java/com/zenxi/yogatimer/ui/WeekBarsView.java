package com.zenxi.yogatimer.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.zenxi.yogatimer.Palette;
import com.zenxi.yogatimer.Themed;
import com.zenxi.yogatimer.Ui;

/** 近七日练习柱状图。 */
public class WeekBarsView extends View implements Themed {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Palette palette = Palette.DARK;
    private long[] data = new long[]{0, 0, 0, 0, 0, 0, 0};
    private String[] labels = new String[]{"一", "二", "三", "四", "五", "六", "日"};
    private float grow = 1f;
    private ValueAnimator anim;

    public WeekBarsView(Context c) {
        this(c, null);
    }

    public WeekBarsView(Context c, AttributeSet a) {
        super(c, a);
        bar.setStyle(Paint.Style.FILL);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        value.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(Ui.sp(c, 10.5f));
        value.setTextSize(Ui.sp(c, 9.5f));
    }

    public void setData(long[] seconds, String[] dayLabels) {
        if (seconds != null && seconds.length == 7) {
            data = seconds;
        }
        if (dayLabels != null && dayLabels.length == 7) {
            labels = dayLabels;
        }
        if (anim != null) {
            anim.cancel();
        }
        grow = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(680L);
        anim.setInterpolator(Ui.EASE_OUT);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                grow = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
        invalidate();
    }

    @Override
    public void applyTheme(Palette p) {
        palette = p;
        label.setColor(p.textLow);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float labelH = Ui.dp(getContext(), 24f);
        float topPad = Ui.dp(getContext(), 22f);
        float usable = Math.max(1f, h - labelH - topPad);
        float col = w / 7f;
        float barW = Math.min(Ui.dp(getContext(), 20f), col * 0.46f);
        float bottom = topPad + usable;

        long max = 1;
        for (long v : data) {
            max = Math.max(max, v);
        }

        for (int i = 0; i < 7; i++) {
            float cx = col * i + col / 2f;
            float frac = data[i] / (float) max;
            float bh = Math.max(Ui.dp(getContext(), 3f), usable * frac * grow);
            rect.set(cx - barW / 2f, bottom - bh, cx + barW / 2f, bottom);
            boolean today = i == 6;
            bar.setColor(today ? palette.accent : Ui.withAlpha(palette.accent, 0x66));
            c.drawRoundRect(rect, barW / 2f, barW / 2f, bar);

            label.setColor(today ? palette.textMid : palette.textLow);
            c.drawText(labels[i], cx, h - Ui.dp(getContext(), 7f), label);
            if (data[i] > 0 && grow > 0.98f) {
                value.setColor(today ? palette.textHi : palette.textLow);
                String t = data[i] >= 60 ? (data[i] / 60) + "分" : data[i] + "秒";
                c.drawText(t, cx, rect.top - Ui.dp(getContext(), 5f), value);
            }
        }
    }
}
