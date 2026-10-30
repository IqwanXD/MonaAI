package ru.mona.ai;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

/** Helper pembuat view tanpa XML layout, plus pengaturan edge-to-edge. */
final class Ui {

    static float density = 1f;
    /** Inset sistem terakhir (status bar atas, navigasi bawah), diisi dari MainActivity. */
    static int insetTop, insetBottom;

    private Ui() { }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return java.lang.Math.round(v * density);
    }

    static GradientDrawable rect(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    static GradientDrawable topRounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = dp(radiusDp);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return g;
    }

    /** Ripple. bg = 0 berarti latar transparan. */
    static RippleDrawable ripple(int bg, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE),
                bg == 0 ? null : rect(bg, radiusDp),
                rect(0xFFFFFFFF, radiusDp));
    }

    /** Ripple untuk baris daftar: maskernya persegi selebar baris. */
    static RippleDrawable rowRipple(int fill) {
        return new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE),
                fill == 0 ? null : new ColorDrawable(fill),
                new ColorDrawable(0xFFFFFFFF));
    }

    static void round(View v, final float radiusDp) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radiusDp));
            }
        });
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return t;
    }

    /** Drawable dari res/drawable, diwarnai lewat color filter (copy supaya tidak saling mengubah). */
    static Drawable tinted(Context c, int res, int color) {
        Drawable d = c.getResources().getDrawable(res).mutate();
        d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return d;
    }

    static ImageView icon(Context c, int res, int color) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(tinted(c, res, color));
        return iv;
    }

    /** Tombol ikon dengan ripple. sizeDp = area sentuh. */
    static ImageView iconButton(Context c, int res, int color, int sizeDp, Runnable action) {
        ImageView iv = new ImageView(c);
        int pad = dp(sizeDp * 0.24f);
        iv.setPadding(pad, pad, pad, pad);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(tinted(c, res, color));
        iv.setBackground(ripple(0, sizeDp / 2f));
        iv.setClickable(true);
        if (action != null) iv.setOnClickListener(v -> action.run());
        return iv;
    }

    /** Gambar sampai ke belakang status bar dan navigasi. Ikon bar mengikuti tema. */
    /** Kursor dan sorotan teks memakai warna Mona, bukan aksen warna sistem (yang sering hijau). */
    static void tintCursor(android.widget.EditText e) {
        e.setHighlightColor(M3.alpha(Theme.PRIMARY, 0.35f));
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable c = new GradientDrawable();
            c.setColor(Theme.PRIMARY);
            c.setSize(dp(2), (int) (e.getTextSize() * 1.2f));
            e.setTextCursorDrawable(c);
        }
    }

    static void setupEdge(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(0x00000000);
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        w.setNavigationBarColor(0x00000000);
        w.setBackgroundDrawable(new ColorDrawable(Theme.BG));
        cutoutShortEdges(w);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        M3.styleBars(w);
    }

    /** Konten boleh tampil di area poni/lubang kamera. */
    static void cutoutShortEdges(Window w) {
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
    }
}
