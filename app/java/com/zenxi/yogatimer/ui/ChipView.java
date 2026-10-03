package com.zenxi.yogatimer.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

import com.zenxi.yogatimer.Palette;
import com.zenxi.yogatimer.Themed;
import com.zenxi.yogatimer.Ui;

/** 胶囊形选择片，选中状态带动画过渡。 */
public class ChipView extends TextView implements Themed {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private Palette palette = Palette.DARK;
    private boolean active;
    private float activeT;
    private ValueAnimator anim;
    private Drawable icon;
    private int iconSize;

    public ChipView(Context c) {
        this(c, null);
    }

    public ChipView(Context c, AttributeSet a) {
        super(c, a);
        setGravity(Gravity.CENTER);
        setTextSize(13f);
        setClickable(true);
        setFocusable(true);
        int px = (int) Ui.dp(c, 15f);
        int py = (int) Ui.dp(c, 8f);
        setPadding(px, py, px, py);
        setBackground(null);
    }

    public void setIconRes(int resId) {
        iconSize = (int) Ui.dp(getContext(), compact ? 14f : 16f);
        icon = getResources().getDrawable(resId, getContext().getTheme());
        setCompoundDrawablePadding((int) Ui.dp(getContext(), compact ? 5f : 6f));
        if (icon != null) {
            icon.setBounds(0, 0, iconSize, iconSize);
            setCompoundDrawables(null, null, icon, null);
        }
    }

    private boolean compact;

    /** 紧凑模式：用于一行放下多个快捷开关的小屏场景。 */
    public void setCompact(boolean v) {
        compact = v;
        int px = (int) Ui.dp(getContext(), v ? 10f : 15f);
        int py = (int) Ui.dp(getContext(), v ? 7f : 8f);
        setPadding(px, py, px, py);
        setTextSize(v ? 12.5f : 13f);
        if (icon != null) {
            iconSize = (int) Ui.dp(getContext(), v ? 14f : 16f);
            setCompoundDrawablePadding((int) Ui.dp(getContext(), v ? 5f : 6f));
            icon.setBounds(0, 0, iconSize, iconSize);
            setCompoundDrawables(null, null, icon, null);
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        setActive(v, true);
    }

    public void setActive(boolean v, boolean animate) {
        active = v;
        float target = v ? 1f : 0f;
        if (!animate) {
            activeT = target;
            refresh();
            return;
        }
        if (anim != null) {
            anim.cancel();
        }
        anim = ValueAnimator.ofFloat(activeT, target);
        anim.setDuration(200L);
        anim.setInterpolator(Ui.EASE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                activeT = (Float) a.getAnimatedValue();
                refresh();
            }
        });
        anim.start();
    }

    private void refresh() {
        setTextColor(Ui.mix(palette.chipText, palette.accent, activeT));
        if (icon != null) {
            icon.setTint(Ui.mix(palette.chipText, palette.accent, activeT));
        }
        invalidate();
    }

    @Override
    public void applyTheme(Palette p) {
        palette = p;
        refresh();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float radius = getHeight() / 2f;
        rect.set(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f);
        fill.setColor(Ui.mix(palette.chip, palette.accentSoft, activeT));
        c.drawRoundRect(rect, radius, radius, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, Ui.dp(getContext(), 0.9f)));
        stroke.setColor(Ui.mix(palette.chipStroke, palette.accent, activeT));
        c.drawRoundRect(rect, radius, radius, stroke);
        super.onDraw(c);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        animate().scaleX(pressed ? 0.96f : 1f).scaleY(pressed ? 0.96f : 1f)
                .setDuration(130L).start();
    }
}
