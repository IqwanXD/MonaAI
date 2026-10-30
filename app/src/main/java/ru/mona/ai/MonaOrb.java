package ru.mona.ai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Ikon Mona.
 *  - Mode ring (default): titik-titik melingkar, beriak seperti gelombang berputar.
 *  - Mode huruf M (letterM = true): titik-titik membentuk huruf "M" yang tetap terbaca,
 *    lalu setiap titik naik-turun seperti ombak yang berjalan dari kiri ke kanan.
 * animate = false menampilkan bentuk diam.
 * Catatan: pakai java.lang.Math, karena paket ini punya kelas ru.mona.ai.Math.
 */
final class MonaOrb extends View {

    private static final int DOTS = 12;
    private static final int M_DOTS = 22;

    private final boolean animate;
    private final boolean letterM;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mx = new float[M_DOTS];   // posisi titik huruf M, koordinat -1..1
    private final float[] my = new float[M_DOTS];
    private float bloom = 1f;   // 0..1: bentuk mekar dari pusat
    private float phase = 0f;   // 0..1: posisi riak per siklus
    private ValueAnimator bloomAnim, waveAnim;

    MonaOrb(Context c, boolean animate) {
        this(c, animate, false);
    }

    MonaOrb(Context c, boolean animate, boolean letterM) {
        super(c);
        this.animate = animate;
        this.letterM = letterM;
        if (letterM) buildLetterM();
    }

    /** Titik-titik tersebar rata sepanjang garis huruf M (kiri-bawah -> atas -> lembah -> atas -> kanan-bawah). */
    private void buildLetterM() {
        final float[][] p = {
            {-0.80f,  0.62f}, {-0.80f, -0.62f}, {0f, 0.22f}, {0.80f, -0.62f}, {0.80f, 0.62f}
        };
        final float[] seg = new float[p.length - 1];
        float total = 0f;
        for (int i = 0; i < seg.length; i++) {
            float dx = p[i + 1][0] - p[i][0], dy = p[i + 1][1] - p[i][1];
            seg[i] = (float) java.lang.Math.sqrt(dx * dx + dy * dy);
            total += seg[i];
        }
        for (int k = 0; k < M_DOTS; k++) {
            float t = total * k / (M_DOTS - 1);
            int s = 0;
            while (s < seg.length - 1 && t > seg[s]) { t -= seg[s]; s++; }
            float f = seg[s] == 0f ? 0f : t / seg[s];
            mx[k] = p[s][0] + (p[s + 1][0] - p[s][0]) * f;
            my[k] = p[s][1] + (p[s + 1][1] - p[s][1]) * f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animate) return;
        bloom = 0f;
        bloomAnim = ValueAnimator.ofFloat(0f, 1f);
        bloomAnim.setDuration(640);
        bloomAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        bloomAnim.addUpdateListener(a -> { bloom = (float) a.getAnimatedValue(); invalidate(); });
        bloomAnim.start();

        waveAnim = ValueAnimator.ofFloat(0f, 1f);
        waveAnim.setDuration(letterM ? 1800 : 1300);
        waveAnim.setRepeatCount(ValueAnimator.INFINITE);
        waveAnim.setInterpolator(new LinearInterpolator());
        waveAnim.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
        waveAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (bloomAnim != null) { bloomAnim.cancel(); bloomAnim = null; }
        if (waveAnim != null) { waveAnim.cancel(); waveAnim = null; }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float side = java.lang.Math.min(w, h);
        if (side <= 0) return;
        if (letterM) drawLetterM(canvas, w / 2f, h / 2f, side);
        else drawRing(canvas, w / 2f, h / 2f, side);
    }

    private void drawLetterM(Canvas canvas, float cx, float cy, float side) {
        float scale = side * 0.40f * bloom;            // setengah lebar huruf
        float amp = side * 0.075f * bloom;             // tinggi ombak
        float dot = side * 0.040f * (0.6f + 0.4f * bloom);
        paint.setColor(Theme.PRIMARY);

        for (int k = 0; k < M_DOTS; k++) {
            // satu gelombang berjalan dari kiri ke kanan sepanjang huruf
            double wave = animate
            ? java.lang.Math.sin(2 * java.lang.Math.PI * (phase - (float) k / M_DOTS * 1.5f))
            : 0.0;
            float x = cx + mx[k] * scale;
            float y = cy + my[k] * scale + amp * (float) wave;
            float level = (float) ((wave + 1.0) / 2.0);  // 0..1
            paint.setAlpha((int) (255 * (0.45f + 0.55f * level)));
            canvas.drawCircle(x, y, dot * (0.85f + 0.3f * level), paint);
        }
    }

    private void drawRing(Canvas canvas, float cx, float cy, float side) {
        float base = side * 0.30f * bloom;               // jari-jari ring
        float amp = side * 0.07f * bloom;                 // tinggi riak
        float dot = side * 0.11f * (0.55f + 0.45f * bloom);
        paint.setColor(Theme.PRIMARY);

        for (int i = 0; i < DOTS; i++) {
            double ang = 2 * java.lang.Math.PI * i / DOTS - java.lang.Math.PI / 2;
            double wave = animate
            ? java.lang.Math.sin(2 * java.lang.Math.PI * (phase - (float) i / DOTS))
            : 0.0;
            float r = base + amp * (float) wave;
            float x = cx + (float) (r * java.lang.Math.cos(ang));
            float y = cy + (float) (r * java.lang.Math.sin(ang));
            float level = (float) ((wave + 1.0) / 2.0);  // 0..1
            paint.setAlpha((int) (255 * (0.4f + 0.6f * level)));
            canvas.drawCircle(x, y, dot * (0.8f + 0.4f * level), paint);
        }
    }
}
