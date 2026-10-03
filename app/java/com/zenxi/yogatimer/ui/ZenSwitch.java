package com.zenxi.yogatimer.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.zenxi.yogatimer.Palette;
import com.zenxi.yogatimer.Themed;
import com.zenxi.yogatimer.Ui;

/** 细长优雅的自绘开关。 */
public class ZenSwitch extends View implements Themed {

    public interface OnChange {
        void onChanged(boolean value);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Palette palette = Palette.DARK;
    private boolean checked;
    private float pos;
    private ValueAnimator anim;
    private OnChange onChange;

    public ZenSwitch(Context c) {
        this(c, null);
    }

    public ZenSwitch(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        setFocusable(true);
    }

    public void setOnChange(OnChange l) {
        onChange = l;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean v) {
        setChecked(v, true);
    }

    public void setChecked(boolean v, boolean animate) {
        checked = v;
        float target = v ? 1f : 0f;
        if (!animate) {
            pos = target;
            invalidate();
            return;
        }
        if (anim != null) {
            anim.cancel();
        }
        anim = ValueAnimator.ofFloat(pos, target);
        anim.setDuration(220L);
        anim.setInterpolator(Ui.EASE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                pos = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    public void applyTheme(Palette p) {
        palette = p;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = (int) Ui.dp(getContext(), 50f);
        int h = (int) Ui.dp(getContext(), 30f);
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float inset = h * 0.12f;
        rect.set(inset, inset, w - inset, h - inset);
        float radius = rect.height() / 2f;

        int track = Ui.mix(Ui.withAlpha(palette.trackOff, 0xFF), palette.accent, pos);
        paint.setColor(track);
        paint.setAlpha((int) (255 * (0.85f + 0.15f * pos)));
        c.drawRoundRect(rect, radius, radius, paint);

        float knobR = radius - Math.max(2f, h * 0.09f);
        float x = rect.left + radius + pos * (rect.width() - 2f * radius);
        paint.setAlpha(255);
        paint.setColor(Ui.withAlpha(palette.knob, 0xFF));
        c.drawCircle(x, rect.centerY(), knobR, paint);

        if (pos > 0.02f) {
            paint.setColor(Ui.withAlpha(palette.accent, (int) (90 * pos)));
            c.drawCircle(x, rect.centerY(), knobR * 1.45f, paint);
        }
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        setChecked(!checked, true);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (onChange != null) {
            onChange.onChanged(checked);
        }
        return handled;
    }
}
