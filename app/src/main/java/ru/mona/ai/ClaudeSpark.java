package ru.mona.ai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Bintang oranye ala Claude: delapan sinar membulat. Mode animasi: sinar berputar pelan dan
 * denyutnya berjalan berurutan, seperti indikator "sedang berpikir". Mode statis dipakai
 * sebagai logo, misalnya di sambutan dan di header proses berpikir.
 */
final class ClaudeSpark extends View {

    private static final int RAYS = 8;
    private static final long CYCLE_MS = 2000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean animate;
    private float phase;             // 0..1, satu siklus animasi
    private ValueAnimator va;

    ClaudeSpark(Context c, boolean animate) {
        super(c);
        this.animate = animate;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float cx = w / 2f, cy = h / 2f;
        float r = java.lang.Math.min(w, h) / 2f;

        paint.setColor(Theme.PRIMARY);
        paint.setStrokeWidth(r * 0.24f);

        float rot = animate ? phase * 360f / RAYS : 0f;   // satu sinar per siklus, mulus
        float inner = r * 0.18f;
        for (int i = 0; i < RAYS; i++) {
            double a = java.lang.Math.toRadians(i * (360.0 / RAYS) + rot);
            float wave = animate
                    ? (float) (0.5 + 0.5 * java.lang.Math.sin(2 * java.lang.Math.PI * (phase - i / (float) RAYS)))
                    : 1f;
            float outer = r * (0.5f + 0.5f * wave);
            float c = (float) java.lang.Math.cos(a), s = (float) java.lang.Math.sin(a);
            canvas.drawLine(cx + c * inner, cy + s * inner, cx + c * outer, cy + s * outer, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animate && va == null) {
            va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(CYCLE_MS);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            va.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (va != null) { va.cancel(); va = null; }
        super.onDetachedFromWindow();
    }
}
