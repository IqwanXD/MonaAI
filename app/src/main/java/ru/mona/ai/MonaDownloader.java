package ru.mona.ai;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Menyalin file dari penyimpanan MonaAI ke folder Download, dengan progres di kartu chat
 * dan di notifikasi. Berjalan di thread sendiri, jadi tetap lanjut saat aplikasi di latar belakang.
 */
final class MonaDownloader {

    interface Listener {
        void onProgress(int pct);
        /** savedName = nama tampilan lokasi simpan; error = null jika berhasil. */
        void onDone(String savedName, String error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int nextId = MonaNotify.ID_DL_BASE;

    private MonaDownloader() { }

    static void start(Context c, final File src, final Listener l) {
        final Context app = c.getApplicationContext();
        final int id;
        synchronized (MonaDownloader.class) { id = nextId++; }
        final String name = src.getName();
        new Thread(() -> {
            String saved = null, err = null;
            try {
                saved = copy(app, src, id, name, l);
            } catch (Exception e) {
                err = e.getMessage() == null ? "gagal menyalin" : e.getMessage();
            }
            MonaNotify.downloadDone(app, id, name, err == null, err == null ? "Tersimpan di " + saved : err);
            final String fs = saved, fe = err;
            if (l != null) MAIN.post(() -> l.onDone(fs, fe));
        }).start();
    }

    private static String copy(Context app, File src, int id, String name, Listener l) throws Exception {
        String mime = mimeOf(name);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver r = app.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("tidak bisa membuat berkas unduhan");
            OutputStream out = r.openOutputStream(uri);
            if (out == null) throw new IOException("berkas unduhan tidak bisa ditulis");
            pump(app, new FileInputStream(src), out, src.length(), id, name, l);
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            r.update(uri, done, null, null);
            return "Download/" + name;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("folder Download tidak bisa dibuat");
        File dst = unique(dir, name);
        pump(app, new FileInputStream(src), new FileOutputStream(dst), src.length(), id, name, l);
        MediaScannerConnection.scanFile(app, new String[]{ dst.getAbsolutePath() }, new String[]{ mime }, null);
        return "Download/" + dst.getName();
    }

    /** Salin isi dan laporkan progres (dibatasi tiap 2%). Kedua stream ditutup di sini. */
    private static void pump(Context app, InputStream in, OutputStream out, long total, int id, String name, Listener l)
    throws IOException {
        byte[] buf = new byte[64 * 1024];
        long done = 0;
        int lastPct = -1;
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                int pct = total <= 0 ? 100 : (int) (done * 100 / total);
                if (pct / 2 != lastPct / 2) {
                    lastPct = pct;
                    MonaNotify.downloadProgress(app, id, name, pct);
                    if (l != null) {
                        final int fp = pct;
                        MAIN.post(() -> l.onProgress(fp));
                    }
                }
            }
            out.flush();
        } finally {
            in.close();
            out.close();
        }
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            File g = new File(dir, base + " (" + i + ")" + ext);
            if (!g.exists()) return g;
        }
        return f;
    }

    static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
        return m == null ? "application/octet-stream" : m;
    }
}
