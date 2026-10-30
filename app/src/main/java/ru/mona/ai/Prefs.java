package ru.mona.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferensi aplikasi (SharedPreferences "mona_ai"). Riwayat chat, provider, API key, dan
 * nama pengguna disimpan di file yang sama supaya data lama tetap terbaca.
 */
final class Prefs {

    static final String NAME = "mona_ai";

    private static SharedPreferences sp;

    private Prefs() { }

    static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    /** Akses ke SharedPreferences yang sama untuk kode lain (riwayat, provider, dan sebagainya). */
    static SharedPreferences get(Context c) {
        init(c);
        return sp;
    }

    /**
     * Tema gelap (default). false = terang. Kunci "dark" dipakai mulai versi ini; kunci lama
     * "theme" (system/light/dark) hanya dibaca sekali sebagai cadangan.
     */
    static boolean darkTheme() {
        if (sp == null) return true;
        if (sp.contains("dark")) return sp.getBoolean("dark", true);
        return !"light".equals(sp.getString("theme", "dark"));
    }

    static void setDarkTheme(boolean on) {
        if (sp != null) sp.edit().putBoolean("dark", on).apply();
    }
}
