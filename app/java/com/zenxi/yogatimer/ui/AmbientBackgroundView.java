package com.zenxi.yogatimer.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.zenxi.yogatimer.Palette;
import com.zenxi.yogatimer.Themed;
import com.zenxi.yogatimer.Ui;

/**
 * 背景：深墨渐变 + 两团缓慢漂移的光雾（离屏位图绘制，成本很低）。
 */
public class AmbientBackgroundView extends View implements Themed {

    private final Paint basePaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix glowMatrix = new Matrix();
    private final RectF rect = new RectF();

    private Palette palette = Palette.DARK;
    private Bitmap glowBitmap;
    private ValueAnimator animator;
    private float phase;
    private boolean animate;
    private boolean attached;
    private long lastNanos;

    public AmbientBackgroundView(Context c) {
        this(c, null);
    }

    public AmbientBackgroundView(Context c, AttributeSet a) {
        super(c, a);
        applyTheme(palette);
    }

    @Override
    public void applyTheme(Palette p) {
        palette = p;
        glowBitmap = buildGlow(p);
        basePaint.setShader(null);
        if (getHeight() > 0) {
            basePaint.setShader(new LinearGradient(0f, 0f, 0f, getHeight(),
                    p.bgTop, p.bgBottom, Shader.TileMode.CLAMP));
        }
        invalidate();
    }

    private Bitmap buildGlow(Palette p) {
        int size = 256;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f;
        paint.setShader(new RadialGradient(r, r, r,
                new int[]{Ui.withAlpha(p.glowA, 0xFF), Ui.withAlpha(p.glowA, 0x00)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r, paint);
        return bmp;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        basePaint.setShader(new LinearGradient(0f, 0f, 0f, h,
                palette.bgTop, palette.bgBottom, Shader.TileMode.CLAMP));
        vignettePaint.setShader(new RadialGradient(w / 2f, h * 0.42f,
                Math.max(w, h) * 0.78f,
                new int[]{0x00000000, palette.vignette},
                new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    /** 计时进行中才让背景缓慢流动。 */
    public void setAnimating(boolean on) {
        animate = on;
        if (on) {
            start();
        } else {
            stop();
            invalidate();
        }
    }

    private void start() {
        if (animator != null || !attached) {
            return;
        }
        lastNanos = System.nanoTime();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private int frame;

            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                long now = System.nanoTime();
                float dt = Math.min(0.2f, (now - lastNanos) / 1_000_000_000f);
                lastNanos = now;
                phase = (phase + dt * 0.028f) % 1f;
                // 30fps 足够，省电
                if ((frame++ & 1) == 0) {
                    invalidate();
                }
            }
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (animate) {
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        c.drawRect(0f, 0f, w, h, basePaint);

        if (glowBitmap != null) {
            float t = phase * (float) (Math.PI * 2);
            float a = (float) Math.sin(t);
            float b = (float) Math.cos(t * 0.7f);
            float size1 = Math.max(w, h) * (1.05f + 0.04f * a);
            float cx1 = w * (0.22f + 0.10f * a);
            float cy1 = h * (0.24f + 0.06f * b);
            drawGlow(c, cx1, cy1, size1, glowPaint, 150);

            float t2 = t * 0.6f + 1.6f;
            float size2 = Math.max(w, h) * (0.95f + 0.05f * (float) Math.cos(t2));
            float cx2 = w * (0.80f + 0.08f * (float) Math.sin(t2));
            float cy2 = h * (0.78f + 0.06f * (float) Math.cos(t2));
            glowMatrix.reset();
            glowMatrix.postScale(1f, 1f);
            glowPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    Ui.withAlpha(palette.glowB, 0xFF), android.graphics.PorterDuff.Mode.SRC_IN));
            drawGlow(c, cx2, cy2, size2, glowPaint, 130);
            glowPaint.setColorFilter(null);
        }

        c.drawRect(0f, 0f, w, h, vignettePaint);
    }

    private void drawGlow(Canvas c, float cx, float cy, float size, Paint paint, int alpha) {
        rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        paint.setAlpha(alpha);
        c.drawBitmap(glowBitmap, null, rect, paint);
    }
}
