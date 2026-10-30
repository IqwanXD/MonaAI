package ru.mona.ai;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service yang menjaga proses tetap hidup selama permintaan AI atau unduhan
 * berjalan, walaupun aplikasi sudah di latar belakang. Service berhenti otomatis ketika
 * pekerjaan terakhir selesai.
 */
public class MonaService extends Service {

    private static int jobs;

    /** Tandai satu pekerjaan dimulai dan jalankan service foreground. */
    static synchronized void begin(Context c, String what) {
        jobs++;
        Context app = c.getApplicationContext();
        Intent i = new Intent(app, MonaService.class);
        i.putExtra("what", what);
        try {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
            else app.startService(i);
        } catch (Exception ignored) {
            // Tanpa service, pekerjaan tetap berjalan selama proses masih hidup.
        }
    }

    /** Tandai satu pekerjaan selesai. Service berhenti jika tidak ada pekerjaan lain. */
    static synchronized void end(Context c) {
        if (jobs > 0) jobs--;
        if (jobs == 0) {
            Context app = c.getApplicationContext();
            try { app.stopService(new Intent(app, MonaService.class)); } catch (Exception ignored) { }
        }
    }

    static synchronized boolean running() { return jobs > 0; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MonaNotify.ensureChannels(this);
        String what = intent == null ? null : intent.getStringExtra("what");
        android.app.Notification n = MonaNotify.workNotification(this, what);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(MonaNotify.ID_WORK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(MonaNotify.ID_WORK, n);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
