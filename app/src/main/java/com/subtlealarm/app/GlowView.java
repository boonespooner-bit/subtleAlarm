package com.subtlealarm.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

/**
 * Soft ambient light. In AMBIENT mode it breathes very faintly behind the
 * home screen. In SUNRISE mode it grows from the bottom of the screen like
 * first light, following the alarm's fade-in progress.
 */
public class GlowView extends android.view.View {

    public static final int MODE_AMBIENT = 0;
    public static final int MODE_SUNRISE = 1;

    private int mode = MODE_AMBIENT;
    private float sunrise = 0f; // 0..1
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private long startTime;

    public GlowView(Context context) {
        super(context);
    }

    public GlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    /** Sunrise progress, 0..1, driven by the alarm's fade-in. */
    public void setSunrise(float progress) {
        this.sunrise = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTime = System.currentTimeMillis();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        double t = (System.currentTimeMillis() - startTime) / 1000.0;

        if (mode == MODE_AMBIENT) {
            // a faint warm halo, slowly breathing high on the screen
            float breathe = (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI / 9.0));
            float cx = w * 0.5f;
            float cy = h * 0.34f;
            float radius = Math.min(w, h) * (0.52f + 0.05f * breathe);
            int alpha = (int) (16 + 12 * breathe);
            drawGlow(canvas, cx, cy, radius, 0xFFD9A0, alpha);
        } else {
            // first light: a warm dome rising from below, gently shimmering
            float p = sunrise;
            float shimmer = (float) (1.0 + 0.05 * Math.sin(t * 2 * Math.PI / 5.0));
            float cx = w * 0.5f;
            float cy = h * (1.05f - 0.25f * p);
            float radius = h * (0.30f + 0.85f * p) * shimmer;
            int alpha = (int) (30 + 150 * p);
            drawGlow(canvas, cx, cy, radius * 1.35f, 0xFF9A54, (int) (alpha * 0.55f));
            drawGlow(canvas, cx, cy, radius, 0xFFC98A, alpha);
            drawGlow(canvas, cx, cy, radius * 0.45f, 0xFFE8C2, (int) (alpha * 0.8f));
        }
    }

    private void drawGlow(Canvas canvas, float cx, float cy, float radius, int rgb, int alpha) {
        if (radius <= 0) return;
        int center = (Math.min(255, Math.max(0, alpha)) << 24) | (rgb & 0xFFFFFF);
        int edge = rgb & 0xFFFFFF; // alpha 0
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{center, edge}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }
}
