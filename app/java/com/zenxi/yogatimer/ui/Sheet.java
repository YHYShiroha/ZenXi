package com.zenxi.yogatimer.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zenxi.yogatimer.Palette;
import com.zenxi.yogatimer.R;
import com.zenxi.yogatimer.Themed;
import com.zenxi.yogatimer.Ui;

/** 从底部升起的面板（设置 / 记录 / 自定义时间共用）。 */
public final class Sheet implements Themed {

    /** 内容超过屏幕 70% 时内部滚动。 */
    public static final class MaxScroll extends ScrollView {
        public MaxScroll(Context c) {
            super(c);
        }

        public MaxScroll(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.70f);
            int h = MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
            super.onMeasure(wSpec, h);
        }
    }

    private final Activity activity;
    private final Dialog dialog;
    private final FrameLayout root;
    private final View scrim;
    private final LinearLayout card;
    private final LinearLayout body;
    private final TextView titleView;
    private final ImageView closeView;
    private Palette palette;
    private int basePadBottom;
    private Runnable onDismiss;

    public Sheet(Activity a, String title, Palette p) {
        this.activity = a;
        this.palette = p;
        Context c = a;
        dialog = new Dialog(a, R.style.Theme_ZenXi_Sheet);

        root = new FrameLayout(c);
        scrim = new View(c);
        scrim.setBackgroundColor(p.scrim);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) Ui.dp(c, 20f);
        basePadBottom = (int) Ui.dp(c, 18f);
        card.setPadding(pad, (int) Ui.dp(c, 8f), pad, basePadBottom);
        root.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        View handle = new View(c);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                (int) Ui.dp(c, 40f), (int) Ui.dp(c, 4f));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.topMargin = (int) Ui.dp(c, 4f);
        hl.bottomMargin = (int) Ui.dp(c, 10f);
        card.addView(handle, hl);
        handle.setBackground(Ui.rr(Ui.withAlpha(p.textLow, 0x88), Ui.dp(c, 2f)));
        handle.setTag("handle");

        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleView = new TextView(c);
        titleView.setText(title);
        titleView.setTextSize(17f);
        titleView.setLetterSpacing(0.08f);
        titleView.setTextColor(p.textHi);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        closeView = new ImageView(c);
        closeView.setImageResource(R.drawable.ic_close);
        closeView.setPadding((int) Ui.dp(c, 8f), (int) Ui.dp(c, 8f),
                (int) Ui.dp(c, 8f), (int) Ui.dp(c, 8f));
        closeView.setColorFilter(p.textLow);
        closeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        titleRow.addView(closeView, new LinearLayout.LayoutParams(
                (int) Ui.dp(c, 38f), (int) Ui.dp(c, 38f)));
        card.addView(titleRow);

        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        MaxScroll scroll = new MaxScroll(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setBackground(Ui.rr(p.card, Ui.dp(c, 26f), Ui.dp(c, 26f), 0f, 0f));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @SuppressWarnings("deprecation")
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int bottom = Math.max(insets.getSystemWindowInsetBottom(), 0);
                card.setPadding(card.getPaddingLeft(), card.getPaddingTop(),
                        card.getPaddingRight(), basePadBottom + bottom);
                return insets;
            }
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setDimAmount(0f);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    public LinearLayout body() {
        return body;
    }

    public void setTitle(String title) {
        titleView.setText(title);
    }

    public void setOnDismiss(Runnable r) {
        onDismiss = r;
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void show() {
        dialog.show();
        final int h = Math.max((int) Ui.dp(activity, 320f), card.getHeight());
        card.setTranslationY(h);
        scrim.setAlpha(0f);
        card.animate().translationY(0f).setDuration(320L)
                .setInterpolator(Ui.EASE_OUT).start();
        scrim.animate().alpha(1f).setDuration(240L).start();
    }

    public void dismiss() {
        final int h = Math.max((int) Ui.dp(activity, 320f), card.getHeight());
        card.animate().translationY(h).setDuration(230L)
                .setInterpolator(Ui.EASE).start();
        scrim.animate().alpha(0f).setDuration(200L).withEndAction(new Runnable() {
            @Override
            public void run() {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        }).start();
    }

    /** 直接关闭（不做动画）。 */
    public void dismissNow() {
        try {
            dialog.dismiss();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void applyTheme(Palette p) {
        palette = p;
        scrim.setBackgroundColor(p.scrim);
        card.setBackground(Ui.rr(p.card, Ui.dp(activity, 26f), Ui.dp(activity, 26f), 0f, 0f));
        titleView.setTextColor(p.textHi);
        closeView.setColorFilter(p.textLow);
        Ui.themeTree(body, p);
    }
}
