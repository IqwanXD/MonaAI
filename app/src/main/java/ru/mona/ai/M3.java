package ru.mona.ai;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.TextView;

/**
 * Token warna Material 3 untuk MonaAI. Hanya ada satu tema bawaan bernuansa Claude
 * (aksen oranye), dengan dua mode: gelap dan terang. Tidak ada warna dinamis atau pemilih warna.
 */
public final class M3 {

    /** Aksen oranye Claude, versi untuk tema gelap dan terang. */
    public static final int CLAUDE_ORANGE_DARK = 0xFFDA7757;
    public static final int CLAUDE_ORANGE_LIGHT = 0xFFD97757;

    public static int primary, onPrimary, primaryContainer, onPrimaryContainer;
    public static int background, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;
    public static int onSurface, onSurfaceVariant, outline, outlineVariant;
    public static int inverseSurface, inverseOnSurface;

    /** Tema yang sedang dipakai: true = gelap. */
    public static boolean dark = true;

    private static float density = 1f;
    private static boolean inited;

    /** Hitung ulang palet dari Prefs terbaru. Dipanggil sebelum membangun UI. */
    public static void init(Context c) {
        Prefs.init(c);
        density = c.getResources().getDisplayMetrics().density;
        dark = Prefs.darkTheme();
        if (dark) paletteDark(); else paletteLight();
        inverseSurface = onSurface;
        inverseOnSurface = background;
        inited = true;
    }

    private static void paletteDark() {
        primary = CLAUDE_ORANGE_DARK;
        onPrimary = 0xFFFFFFFF;
        primaryContainer = 0xFF4A2E22;
        onPrimaryContainer = 0xFFF5D2C2;
        background = 0xFF1B1B1A;
        surfaceContainer = 0xFF20201E;
        surfaceContainerHigh = 0xFF2B2B29;
        surfaceContainerHighest = 0xFF353533;
        onSurface = 0xFFF5F4EF;
        onSurfaceVariant = 0xFF9C9A92;
        outline = 0xFF6E6D67;
        outlineVariant = 0xFF3A3A38;
    }

    private static void paletteLight() {
        primary = CLAUDE_ORANGE_LIGHT;
        onPrimary = 0xFFFFFFFF;
        primaryContainer = 0xFFF6DED3;
        onPrimaryContainer = 0xFF4A2414;
        background = 0xFFFAF9F5;
        surfaceContainer = 0xFFFFFFFF;
        surfaceContainerHigh = 0xFFE8E6DC;
        surfaceContainerHighest = 0xFFDEDCD1;
        onSurface = 0xFF1F1E1D;
        onSurfaceVariant = 0xFF6B6B68;
        outline = 0xFF8F8D86;
        outlineVariant = 0xFFE5E3DA;
    }

    /** Warna dengan alpha tertentu (0..1), dipakai untuk garis pemisah dan ripple. */
    public static int alpha(int color, float a) {
        return (java.lang.Math.round(a * 255f) << 24) | (color & 0x00FFFFFF);
    }

    /** Ripple netral: putih di tema gelap, hitam di tema terang. */
    public static int rippleColor() {
        return dark ? 0x33FFFFFF : 0x33000000;
    }

    /** Ikon bar status dan navigasi mengikuti tema. */
    public static void styleBars(Window w) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getDecorView().getWindowInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(dark ? 0 : light, light);
            }
            return;
        }
        View d = w.getDecorView();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        int f = d.getSystemUiVisibility();
        d.setSystemUiVisibility(dark ? f & ~light : f | light);
    }

    public static int dp(float v) {
        return java.lang.Math.round(v * density);
    }

    /** Jarak toast dari bawah layar (dp), di atas navigation bar. */
    private static final int TOAST_BOTTOM_DP = 100;

    /**
     * Toast bawaan Android dengan tampilan M3: kartu inverse surface, sudut membulat,
     * teks inverse on surface. Dipakai di semua tempat di MonaAI.
     */
    public static void toast(Context c, CharSequence msg) {
        if (!inited) init(c);
        Context app = c.getApplicationContext();

        TextView t = new TextView(app);
        t.setText(msg);
        t.setTextSize(14);
        t.setTextColor(inverseOnSurface);
        t.setTypeface(Typeface.DEFAULT);
        t.setPadding(dp(20), dp(14), dp(20), dp(14));
        t.setBackground(Ui.rect(inverseSurface, 16));
        t.setElevation(dp(10));

        android.widget.Toast toast = new android.widget.Toast(app);
        toast.setView(t);
        toast.setDuration(android.widget.Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, dp(TOAST_BOTTOM_DP));
        toast.show();
    }
}
