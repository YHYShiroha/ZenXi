package com.zenxi.yogatimer.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.zenxi.yogatimer.Palette;
import com.zenxi.yogatimer.Themed;
import com.zenxi.yogatimer.Ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 计时圆环：进度弧 + 光点 + 呼吸光晕 + 末尾提示脉冲。
 * 圆环显示的是「剩余时间」，随时间流逝而收束。
 */
public class RingTimerView extends View implements Themed {

    private static final class Pulse {
        long start;
        float strength;
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF oval = new RectF();
    private final RectF tmpRect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final List<Pulse> pulses = new ArrayList<>();

    private Palette palette = Palette.DARK;
    private Bitmap glowBitmap;

    private float progress = 1f;      // 剩余比例 1 -> 0
    private float shown = 1f;         // 平滑后的剩余比例
    private float warmth;             // 0..1 末尾提示色温
    private float warmthTarget;
    private float breathLevel;        // 0..1 呼吸光晕
    private float breathTarget;
    private float orbit;              // 空闲光点角度
    private boolean idleOrbit = true;
    private boolean loopEnabled;
    private boolean attached;
    private long lastFrameNanos;

    private String timeText = "01:00";
    private String labelText = "准备开始";
    private String subText = "";

    private ValueAnimator looper;
    private ValueAnimator finishAnim;
    private float finishT = -1f;

    public RingTimerView(Context c) {
        this(c, null);
    }

    public RingTimerView(Context c, AttributeSet a) {
        super(c, a);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        haloArcPaint.setStyle(Paint.Style.STROKE);
        haloArcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        pulsePaint.setStyle(Paint.Style.STROKE);
        timePaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        labelPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        subPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        applyTheme(palette);
    }

    // ------------------------------------------------------------ 对外 API

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
    }

    public void setTexts(String time, String label, String sub) {
        if (time != null) {
            timeText = time;
        }
        if (label != null) {
            labelText = label;
        }
        if (sub != null) {
            subText = sub;
        }
        invalidate();
    }

    public void setWarmth(boolean on) {
        warmthTarget = on ? 1f : 0f;
    }

    public void setBreathPhase(int phase) {
        if (phase < 0) {
            breathTarget = 0.25f;
        } else if (phase == 0) {
            breathTarget = 1f;
        } else if (phase == 2) {
            breathTarget = 0f;
        }
    }

    public void setIdleOrbit(boolean on) {
        idleOrbit = on;
        invalidate();
    }

    public void pulse(float strength) {
        Pulse p = new Pulse();
        p.start = System.nanoTime();
        p.strength = strength;
        pulses.add(p);
        wake();
    }

    public void playFinish() {
        if (finishAnim != null) {
            finishAnim.cancel();
        }
        finishAnim = ValueAnimator.ofFloat(0f, 1f);
        finishAnim.setDuration(1500L);
        finishAnim.setInterpolator(Ui.EASE_OUT);
        finishAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                finishT = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        finishAnim.start();
    }

    public void clearFinish() {
        finishT = -1f;
        if (finishAnim != null) {
            finishAnim.cancel();
            finishAnim = null;
        }
        invalidate();
    }

    /** 界面可见时才跑动画，省电。 */
    public void setLoopEnabled(boolean on) {
        loopEnabled = on;
        if (on) {
            startLoop();
        } else {
            stopLoop();
        }
    }

    private void wake() {
        if (loopEnabled && looper == null) {
            startLoop();
        }
        invalidate();
    }

    // ------------------------------------------------------------ 主题

    @Override
    public void applyTheme(Palette p) {
        palette = p;
        trackPaint.setColor(p.ringTrack);
        beadPaint.setColor(p.accent);
        dotPaint.setColor(p.accent);
        timePaint.setColor(p.textHi);
        labelPaint.setColor(p.textMid);
        subPaint.setColor(p.textLow);
        glowBitmap = makeGlow(p.accent);
        rebuildShader();
        invalidate();
    }

    private void rebuildShader() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        SweepGradient g = new SweepGradient(cx, cy,
                new int[]{palette.arcA, palette.arcB, palette.arcA},
                new float[]{0f, 0.55f, 1f});
        shaderMatrix.setRotate(-90f, cx, cy);
        g.setLocalMatrix(shaderMatrix);
        arcPaint.setShader(g);

        haloArcPaint.setShader(null);
        haloArcPaint.setColor(Ui.withAlpha(palette.arcA, 0x1F));
    }

    private Bitmap makeGlow(int color) {
        int size = 192;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f;
        p.setShader(new RadialGradient(r, r, r,
                new int[]{Ui.withAlpha(color, 0xAA), Ui.withAlpha(color, 0x44),
                        Ui.withAlpha(color, 0x00)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r, p);
        return bmp;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuildShader();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (loopEnabled) {
            startLoop();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        stopLoop();
        super.onDetachedFromWindow();
    }

    private void startLoop() {
        if (looper != null || !attached) {
            return;
        }
        lastFrameNanos = System.nanoTime();
        looper = ValueAnimator.ofFloat(0f, 1f);
        looper.setDuration(1000L);
        looper.setRepeatCount(ValueAnimator.INFINITE);
        looper.setInterpolator(new LinearInterpolator());
        looper.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                long now = System.nanoTime();
                float dt = Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
                lastFrameNanos = now;
                step(dt);
                invalidate();
            }
        });
        looper.start();
    }

    private void stopLoop() {
        if (looper != null) {
            looper.cancel();
            looper = null;
        }
    }

    private void step(float dt) {
        // 平滑跟随
        shown += (progress - shown) * Math.min(1f, dt * 9f);
        warmth += (warmthTarget - warmth) * Math.min(1f, dt * 5f);
        breathLevel += (breathTarget - breathLevel) * Math.min(1f, dt * 0.55f);
        orbit = (orbit + dt * 0.075f) % 1f;
        long now = System.nanoTime();
        Iterator<Pulse> it = pulses.iterator();
        while (it.hasNext()) {
            Pulse p = it.next();
            if ((now - p.start) / 1_000_000L > 900L) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------ 绘制

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - Ui.dp(getContext(), 16f);
        if (r < 40f) {
            return;
        }

        float stroke = Math.max(6f, r * 0.055f);
        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeWidth(stroke * 1.18f);
        haloArcPaint.setStrokeWidth(stroke * 2.4f);
        pulsePaint.setStrokeWidth(Math.max(2f, stroke * 0.28f));

        oval.set(cx - r, cy - r, cx + r, cy + r);

        // 呼吸光晕
        float haloR = r * (1.06f + 0.07f * breathLevel);
        if (glowBitmap != null) {
            tmpRect.set(cx - haloR * 1.5f, cy - haloR * 1.5f, cx + haloR * 1.5f, cy + haloR * 1.5f);
            glowPaint.setAlpha((int) (34 + 44 * breathLevel));
            c.drawBitmap(glowBitmap, null, tmpRect, glowPaint);
        }

        // 轨道
        c.drawCircle(cx, cy, r, trackPaint);

        float sweep = 360f * shown;
        int arcColor = Ui.mix(palette.arcA, palette.amber, warmth);

        // 进度弧外发光
        haloArcPaint.setColor(Ui.withAlpha(arcColor, 0x24));
        if (sweep > 0.5f) {
            c.drawArc(oval, -90f, sweep, false, haloArcPaint);
        }

        // 进度弧
        if (sweep > 0.5f) {
            if (warmth > 0.02f) {
                arcPaint.setShader(null);
                arcPaint.setColor(Ui.mix(palette.arcA, palette.amber, warmth));
            } else if (arcPaint.getShader() == null) {
                rebuildShader();
            }
            c.drawArc(oval, -90f, sweep, false, arcPaint);
        }

        // 进度光点
        float angle = (float) Math.toRadians(-90f + sweep);
        float bx = cx + r * (float) Math.cos(angle);
        float by = cy + r * (float) Math.sin(angle);
        boolean orbiting = shown > 0.995f && idleOrbit;
        if (!orbiting) {
            if (glowBitmap != null) {
                float g = stroke * 3.4f;
                tmpRect.set(bx - g, by - g, bx + g, by + g);
                glowPaint.setAlpha(200);
                c.drawBitmap(glowBitmap, null, tmpRect, glowPaint);
            }
            beadPaint.setColor(arcColor);
            c.drawCircle(bx, by, stroke * 0.62f, beadPaint);
        }

        // 空闲时的缓慢巡游光点
        if (orbiting) {
            float oa = (float) Math.toRadians(-90f + orbit * 360f);
            float ox = cx + r * (float) Math.cos(oa);
            float oy = cy + r * (float) Math.sin(oa);
            dotPaint.setColor(Ui.withAlpha(palette.accent, 0xEE));
            if (glowBitmap != null) {
                float g = stroke * 3.0f;
                tmpRect.set(ox - g, oy - g, ox + g, oy + g);
                glowPaint.setAlpha(190);
                c.drawBitmap(glowBitmap, null, tmpRect, glowPaint);
            }
            c.drawCircle(ox, oy, stroke * 0.55f, dotPaint);
        }

        // 提示脉冲
        long now = System.nanoTime();
        for (Pulse p : pulses) {
            float t = (now - p.start) / 900_000_000f;
            if (t < 0f || t > 1f) {
                continue;
            }
            float e = 1f - (1f - t) * (1f - t);
            pulsePaint.setColor(Ui.withAlpha(arcColor, (int) ((1f - e) * 150 * p.strength)));
            float pr = r * (1f + 0.10f * e);
            tmpRect.set(cx - pr, cy - pr, cx + pr, cy + pr);
            c.drawArc(tmpRect, -90f + sweep, 90f, false, pulsePaint);
        }

        // 完成时的扩散环
        if (finishT >= 0f) {
            for (int i = 0; i < 2; i++) {
                float t = (finishT - i * 0.16f) / 0.84f;
                if (t <= 0f || t >= 1f) {
                    continue;
                }
                float e = 1f - (1f - t) * (1f - t);
                pulsePaint.setColor(Ui.withAlpha(arcColor, (int) ((1f - e) * 190)));
                float pr = r * (1f + 0.22f * e);
                tmpRect.set(cx - pr, cy - pr, cx + pr, cy + pr);
                c.drawCircle(cx, cy, pr, pulsePaint);
            }
        }

        // 文字
        float timeSize = r * 0.36f;
        timePaint.setTextSize(timeSize);
        timePaint.setLetterSpacing(0.03f);
        labelPaint.setTextSize(Math.max(Ui.sp(getContext(), 10.5f), r * 0.098f));
        labelPaint.setLetterSpacing(0.14f);
        subPaint.setTextSize(Math.max(Ui.sp(getContext(), 10f), r * 0.088f));
        subPaint.setLetterSpacing(0.06f);

        float timeY = cy + timeSize * 0.34f;
        c.drawText(timeText, cx, timeY, timePaint);
        if (labelText != null && labelText.length() > 0) {
            c.drawText(labelText, cx, cy - r * 0.40f, labelPaint);
        }
        if (subText != null && subText.length() > 0) {
            c.drawText(subText, cx, cy + r * 0.56f, subPaint);
        }
    }

}
