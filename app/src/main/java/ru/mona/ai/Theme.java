package ru.mona.ai;

import android.content.Context;

/**
 * Fasad warna MonaAI di atas M3. Panggil load() sebelum membangun UI.
 * Semua view membaca warna dari sini, bukan dari angka hex yang tersebar.
 */
final class Theme {
    static boolean dark = true;

    /** Latar utama (chat, layar pengaturan, drawer). */
    static int BG;
    /** Kolom input dan latar kartu input. */
    static int SURFACE;
    /** Gelembung pesan pengguna, kartu pengaturan, sheet, dan baris terpilih. */
    static int SURFACE_HIGH, SURFACE_HIGHEST;
    /** Teks utama dan teks sekunder. */
    static int ON, ON_VAR;
    /** Aksen oranye Claude dan turunannya. */
    static int PRIMARY, ON_PRIMARY, PRIM_CONT, ON_PRIM_CONT;
    /** Garis tepi dan garis pemisah. */
    static int OUTLINE, LINE;
    /** Warna ripple dan aksi berbahaya (hapus). */
    static int RIPPLE, ERROR;

    private Theme() { }

    /** Hitung ulang palet dari Prefs terbaru, lalu isi field di atas. */
    static void load(Context c) {
        M3.init(c);
        dark = M3.dark;
        BG = M3.background;
        SURFACE = M3.surfaceContainer;
        SURFACE_HIGH = M3.surfaceContainerHigh;
        SURFACE_HIGHEST = M3.surfaceContainerHighest;
        ON = M3.onSurface;
        ON_VAR = M3.onSurfaceVariant;
        PRIMARY = M3.primary;
        ON_PRIMARY = M3.onPrimary;
        PRIM_CONT = M3.primaryContainer;
        ON_PRIM_CONT = M3.onPrimaryContainer;
        OUTLINE = M3.outline;
        LINE = M3.outlineVariant;
        RIPPLE = M3.rippleColor();
        ERROR = dark ? 0xFFFFB4AB : 0xFFBA1A1A;
    }
}
