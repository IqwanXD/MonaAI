package ru.mona.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Notifikasi MonaAI: proses latar belakang, balasan siap, dan unduhan file. */
final class MonaNotify {

    static final String CH_WORK = "mona_work";
    static final String CH_DL = "mona_download";
    static final String CH_DONE = "mona_done";

    static final int ID_WORK = 1;
    static final int ID_DONE = 2;
    /** Id unduhan dimulai dari sini supaya tidak bentrok dengan id lain. */
    static final int ID_DL_BASE = 1000;

    private MonaNotify() { }

    static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /** Membuat channel (Android 8+). Aman dipanggil berkali-kali. */
    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager m = nm(c);
        if (m == null) return;
        m.createNotificationChannel(new NotificationChannel(CH_WORK, "Proses Mona", NotificationManager.IMPORTANCE_LOW));
        m.createNotificationChannel(new NotificationChannel(CH_DONE, "Balasan Mona", NotificationManager.IMPORTANCE_DEFAULT));
        m.createNotificationChannel(new NotificationChannel(CH_DL, "Unduhan", NotificationManager.IMPORTANCE_LOW));
    }

    private static Notification.Builder builder(Context c, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(c, channel);
        return new Notification.Builder(c);
    }

    /** Ketuk notifikasi membuka aplikasi. */
    private static PendingIntent openApp(Context c) {
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch == null) launch = new Intent();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, 0, launch, flags);
    }

    /** Notifikasi permanen selama ada pekerjaan berjalan (dipakai MonaService). */
    static Notification workNotification(Context c, String what) {
        return builder(c, CH_WORK)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Mona sedang bekerja")
        .setContentText(what == null || what.isEmpty() ? "Memproses permintaan" : what)
        .setOngoing(true)
        .setContentIntent(openApp(c))
        .build();
    }

    /** Balasan siap saat aplikasi tidak terlihat. */
    static void replyReady(Context c, String title, String text) {
        ensureChannels(c);
        nm(c).notify(ID_DONE, builder(c, CH_DONE)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(title == null || title.isEmpty() ? "Balasan Mona siap" : title)
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(openApp(c))
        .build());
    }

    static void downloadProgress(Context c, int id, String name, int pct) {
        ensureChannels(c);
        nm(c).notify(id, builder(c, CH_DL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Mengunduh " + name)
        .setContentText(pct + "%")
        .setProgress(100, pct, false)
        .setOngoing(true)
        .build());
    }

    static void downloadDone(Context c, int id, String name, boolean ok, String msg) {
        ensureChannels(c);
        nm(c).notify(id, builder(c, CH_DL)
        .setSmallIcon(ok ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_notify_error)
        .setContentTitle(ok ? "Unduhan selesai" : "Unduhan gagal")
        .setContentText(name + " · " + msg)
        .setAutoCancel(true)
        .setOngoing(false)
        .build());
    }
}
