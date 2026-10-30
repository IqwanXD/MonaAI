package ru.mona.ai;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Membaca berkas ZIP lampiran. Isinya diekstrak ke folder sandbox, lalu dirangkum
 * (daftar berkas + isi berkas teks) supaya AI benar-benar tahu isi zip, tidak menebak.
 *
 * Catatan: panggil dari thread latar belakang, jangan dari UI thread.
 */
final class ZipUtil {
    private static final long MAX_UNPACKED = 200L * 1024 * 1024; // batas total hasil ekstrak
    private static final int MAX_LISTED = 300;                    // berkas yang didaftar
    private static final int MAX_TEXT_TOTAL = 60 * 1024;          // total teks untuk AI
    private static final int MAX_TEXT_FILE = 16 * 1024;           // teks per berkas

    private ZipUtil() { }

    static boolean isZip(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /** Ekstrak ke folder bernama sama dengan zip, di dalam parent. Mengembalikan folder hasil. */
    static File extract(File zip, File parent) throws IOException {
        String base = zip.getName().replaceAll("(?i)\\.zip$", "").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        File dest = new File(parent, base.isEmpty() ? "zip" : base);
        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("folder tujuan tidak bisa dibuat");
        String root = dest.getCanonicalPath() + File.separator;
        byte[] buf = new byte[65536];
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(dest, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) continue; // cegah zip-slip (../)
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File dir = out.getParentFile();
                if (dir != null && !dir.isDirectory()) dir.mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_UNPACKED) throw new IOException("isi zip lebih dari 200 MB");
                        os.write(buf, 0, n);
                    }
                }
            }
        }
        return dest;
    }

    /** Ringkasan isi zip untuk dikirim ke AI: daftar berkas + isi berkas teks (dibatasi). */
    static String summarize(File zip, File parent) {
        StringBuilder sb = new StringBuilder();
        try {
            File dir = extract(zip, parent);
            List<File> files = new ArrayList<>();
            collect(dir, files);
            sb.append("\n\n[Isi ZIP ").append(zip.getName()).append(": ").append(files.size())
              .append(" berkas, sudah diekstrak ke ").append(dir.getAbsolutePath()).append("]\n");

            for (int i = 0; i < files.size(); i++) {
                if (i >= MAX_LISTED) {
                    sb.append("... dan ").append(files.size() - MAX_LISTED).append(" berkas lainnya\n");
                    break;
                }
                File f = files.get(i);
                sb.append("- ").append(rel(dir, f)).append(" (").append(f.length()).append(" byte)\n");
            }

            int used = 0;
            for (File f : files) {
                if (!isText(f.getName()) || used >= MAX_TEXT_TOTAL) continue;
                long len = f.length();
                int cap = (int) (len < MAX_TEXT_FILE ? len : MAX_TEXT_FILE);
                int room = MAX_TEXT_TOTAL - used;
                if (cap > room) cap = room;
                String text = readPrefix(f, cap);
                if (text == null) continue; // berkas biner, lewati
                used += text.length();
                sb.append("\n--- ").append(rel(dir, f)).append(" ---\n").append(text);
                if (len > cap) sb.append("\n... (dipotong)");
                sb.append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "\n\n[Gagal membaca ZIP " + zip.getName() + ": " + e.getMessage() + "]";
        }
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File f : kids) {
            if (f.isDirectory()) collect(f, out);
            else out.add(f);
        }
    }

    private static String rel(File dir, File f) {
        return f.getAbsolutePath().substring(dir.getAbsolutePath().length() + 1);
    }

    private static boolean isText(String name) {
        return name.toLowerCase(Locale.ROOT).matches(
        ".*\\.(txt|md|java|kt|js|ts|py|html|htm|css|xml|json|csv|gradle|sh|c|cpp|h|yml|yaml|log|kts|properties)$");
    }

    /** Baca maksimal {@code cap} byte; null jika berkas mengandung byte nol (biner). */
    private static String readPrefix(File f, int cap) {
        byte[] data = new byte[cap];
        int got = 0;
        try (InputStream in = new FileInputStream(f)) {
            while (got < cap) {
                int n = in.read(data, got, cap - got);
                if (n < 0) break;
                got += n;
            }
        } catch (IOException e) {
            return null;
        }
        for (int i = 0; i < got; i++) {
            if (data[i] == 0) return null;
        }
        return new String(data, 0, got, StandardCharsets.UTF_8);
    }
}
