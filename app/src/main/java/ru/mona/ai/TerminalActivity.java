package ru.mona.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Terminal ala Termux dengan tema Mona. Shell (/system/bin/sh) berjalan di folder data aplikasi.
 * Output dan input perintah ada di satu layar (TerminalView). Tab di bawah: tiap sesi shell
 * dan Files (jelajah, upload, hapus). Semua dialog memakai Sheet bawaan Mona. Edge-to-edge.
 */
public class TerminalActivity extends Activity {

    private static final int MAX_LOG = 120000;             // karakter yang disimpan per sesi
    private static final int MAX_VIEW_BYTES = 200 * 1024;  // batas baca berkas
    private static final String PWD_MARK = "\u0001PWD:";   // penanda direktori aktif dari shell
    private static final int REQ_UPLOAD = 9301;
    private static final int STYLE_PLAIN = 0, STYLE_PRIMARY = 1, STYLE_DANGER = 2;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd MMM HH:mm", Locale.US);

    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static final List<Session> sessions = new ArrayList<>();   // hidup selama proses
    private static TerminalActivity live;                              // activity yang sedang tampil

    // ===== Satu sesi shell =====
    private static class Session {
        final String name;
        final File home;
        File cwd;
        Process proc;
        OutputStream stdin;
        boolean alive;
        final StringBuilder log = new StringBuilder();
        final ExecutorService writer = Executors.newSingleThreadExecutor();
        TerminalView view;   // null jika sesi ini tidak sedang tampil

        Session(String name, File home) {
            this.name = name;
            this.home = home;
            this.cwd = home;
        }

        void print(String t) {
            log.append(t);
            if (log.length() > MAX_LOG) {
                log.delete(0, log.length() - MAX_LOG / 2);
                if (view != null) view.setOutput(log.toString());
            } else if (view != null) {
                view.append(t);
            }
        }

        String promptText() {
            return "mona:" + shortPath(cwd, home) + " $ ";
        }

        void refreshPrompt() {
            if (view != null) view.setPrompt(promptText());
        }

        void kill() {
            Process p = proc;
            proc = null;
            alive = false;
            if (stdin != null) {
                try { stdin.close(); } catch (IOException ignored) { }
            }
            stdin = null;
            if (p != null) p.destroy();
        }

        void close() {
            kill();
            writer.shutdownNow();
        }
    }

    // ===== Shell =====
    private static void startShell(final Session s, File tmp) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
            pb.directory(s.cwd);
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            env.put("HOME", s.home.getAbsolutePath());
            env.put("TMPDIR", tmp.getAbsolutePath());
            env.put("PATH", "/system/bin:/system/xbin:/vendor/bin:/sbin");
            env.put("TERM", "dumb");
            final Process p = pb.start();
            s.proc = p;
            s.stdin = p.getOutputStream();
            s.alive = true;
            final InputStream out = p.getInputStream();
            new Thread(() -> readLoop(s, p, out)).start();
        } catch (IOException e) {
            s.print("[gagal menjalankan shell: " + e.getMessage() + "]\n");
        }
    }

    private static void readLoop(final Session s, final Process p, InputStream in) {
        StringBuilder line = new StringBuilder();
        try {
            InputStreamReader r = new InputStreamReader(in, "UTF-8");
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) != -1) {
                for (int i = 0; i < n; i++) {
                    char c = buf[i];
                    if (c == '\n') {
                        final String l = line.toString();
                        line.setLength(0);
                        ui.post(() -> onLine(s, p, l));
                    } else {
                        line.append(c);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (line.length() > 0) {
            final String rest = line.toString();
            ui.post(() -> onLine(s, p, rest));
        }
        ui.post(() -> onExit(s, p));
    }

    private static void onLine(Session s, Process p, String raw) {
        if (s.proc != p) return;   // keluaran dari shell lama yang sudah diulang
        String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
        if (line.startsWith(PWD_MARK)) {
            s.cwd = new File(line.substring(PWD_MARK.length()).trim());
            s.refreshPrompt();
            if (live != null) live.refreshHeader();
            return;
        }
        s.print(stripAnsi(line) + "\n");
    }

    private static void onExit(Session s, Process p) {
        if (s.proc != p) return;
        s.alive = false;
        s.print("[shell berhenti, kirim perintah untuk memulai lagi]\n");
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\u001B\\[[0-9;?]*[ -/]*[@-~]", "").replace("\u001B", "");
    }

    private static String shortPath(File f, File root) {
        String p = f.getAbsolutePath();
        String r = root.getAbsolutePath();
        if (p.equals(r)) return "~";
        if (p.startsWith(r + "/")) return "~" + p.substring(r.length());
        return p;
    }

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return (b / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", b / (1024f * 1024f));
    }

    private static File uniqueIn(File dir, String name) {
        File f = new File(dir, name);
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int i = 1;
        while (f.exists()) f = new File(dir, base + "_" + (i++) + ext);
        return f;
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
    }

    // =====================================================================
    //  Activity
    // =====================================================================
    private File root;          // folder data internal aplikasi
    private File fileDir;       // posisi di tab Files
    private int active = 0;     // indeks sesi yang tampil
    private boolean filesTab = false;

    private TextView titleView, pathView;
    private FrameLayout content;
    private LinearLayout tabStrip, filesCol;
    private ScrollView filesScroll;
    private View keysRow;
    private LinearLayout bottomBar;   // keys + tab, diberi padding area navigasi
    private ImageButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        Theme.load(this);
        Ui.setupEdge(this);                 // edge-to-edge, sama seperti layar utama Mona
        // Android 11+: keyboard ditangani lewat padding per frame (lihat buildUi), jadi jendela tidak perlu menyusut
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        root = getFilesDir().getParentFile();
        if (root == null) root = getFilesDir();
        fileDir = root;
        if (sessions.isEmpty()) createSession();
        if (active < 0 || active >= sessions.size()) active = 0;
        buildUi();
        live = this;
        showSession(active);
    }

    @Override
    protected void onResume() {
        super.onResume();
        live = this;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachAll();
        if (live == this) live = null;
    }

    @Override
    public void onBackPressed() {
        if (filesTab && !fileDir.getAbsolutePath().equals(root.getAbsolutePath())) {
            fileDir = fileDir.getParentFile();
            renderFiles();
            refreshHeader();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_UPLOAD && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadFile(data.getData());
        }
    }

    private int dp(float v) {
        return Ui.dp(v);
    }

    private void toast(String msg) {
        M3.toast(this, msg);
    }

    private int createSession() {
        Session s = new Session("Sesi " + (sessions.size() + 1), root);
        sessions.add(s);
        startShell(s, getCacheDir());
        return sessions.size() - 1;
    }

    private Session currentSession() {
        if (filesTab || sessions.isEmpty() || active < 0 || active >= sessions.size()) return null;
        return sessions.get(active);
    }

    private void detachAll() {
        for (Session s : sessions) s.view = null;
    }

    // ----- Susunan layar -----
    /**
     * Padding dari inset sistem. Kolom diberi ruang keyboard, sedangkan bar tab menanggung area navigasi
     * (warnanya menyatu dengan tab). Saat keyboard naik, bar berada tepat di atas keyboard.
     */
    private void applyBarInsets(View v, WindowInsets ins) {
        int top, navBottom, imeBottom = 0;
        boolean imeUp = false;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets sys = ins.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            top = sys.top;
            navBottom = sys.bottom;
            imeUp = ins.isVisible(WindowInsets.Type.ime());
            imeBottom = ins.getInsets(WindowInsets.Type.ime()).bottom;
        } else {
            top = ins.getSystemWindowInsetTop();
            navBottom = ins.getSystemWindowInsetBottom();
        }
        Ui.insetTop = top;
        Ui.insetBottom = navBottom;
        v.setPadding(0, top, 0, imeUp ? imeBottom : 0);
        if (bottomBar != null) bottomBar.setPadding(0, 0, 0, imeUp ? 0 : navBottom);
    }

    private void buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Theme.BG);
        // Edge-to-edge: konten diberi jarak sebesar status bar, navigasi, dan keyboard
        // Bar bawah (tab) yang menanggung area navigasi, supaya warnanya menyatu dengan tab.
        // Saat keyboard naik, bar ditempatkan tepat di atas keyboard dan ruang keyboard diberikan ke kolom.
        col.setOnApplyWindowInsetsListener((v, ins) -> {
            applyBarInsets(v, ins);
            return ins;
        });
        // Keyboard naik/turun mengikuti animasi sistem: padding diperbarui setiap frame, jadi mulus
        if (Build.VERSION.SDK_INT >= 30) {
            col.setWindowInsetsAnimationCallback(new android.view.WindowInsetsAnimation.Callback(
                    android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP) {
                @Override
                public WindowInsets onProgress(WindowInsets ins, List<android.view.WindowInsetsAnimation> running) {
                    applyBarInsets(col, ins);
                    return ins;
                }
            });
        }

        // Header: kembali, judul, dan path aktif
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(16), dp(8));
        header.addView(iconButton(R.drawable.ic_back, Theme.ON, v -> onBackPressed()),
        new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = Ui.text(this, "Terminal", 17, Theme.ON, true);
        pathView = Ui.text(this, "", 12, Theme.ON_VAR, false);
        pathView.setSingleLine(true);
        pathView.setEllipsize(TextUtils.TruncateAt.START);
        titles.addView(titleView);
        titles.addView(pathView);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.setMarginStart(dp(8));
        header.addView(titles, tl);
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));
        col.addView(line(), new LinearLayout.LayoutParams(-1, dp(1)));

        // Area utama: sesi terminal, atau tab Files (dengan FAB)
        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        filesScroll = new ScrollView(this);
        filesScroll.setBackgroundColor(Theme.BG);
        filesCol = new LinearLayout(this);
        filesCol.setOrientation(LinearLayout.VERTICAL);
        filesCol.setPadding(dp(8), dp(4), dp(8), dp(88));   // ruang agar baris terakhir tidak tertutup FAB
        filesScroll.addView(filesCol);

        fab = new ImageButton(this);
        fab.setImageResource(R.drawable.ic_plus);
        fab.setColorFilter(Theme.ON_PRIMARY, PorterDuff.Mode.SRC_IN);
        fab.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fab.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Theme.PRIMARY);
        // ImageButton punya animasi elevasi bawaan saat ditekan; itu yang membuat FAB terlihat berkedut.
        fab.setStateListAnimator(null);
        fab.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Theme.RIPPLE), circle, null));
        fab.setElevation(dp(6));
        fab.setContentDescription("Tambah");
        fab.setOnClickListener(v -> showAddSheet());

        // Bagian bawah: tombol tambahan + tab, satu warna dengan tab (SURFACE_HIGH)
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Theme.SURFACE_HIGH);
        bottomBar = bottom;
        keysRow = buildKeysRow();
        bottom.addView(keysRow, new LinearLayout.LayoutParams(-1, -2));
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setGravity(Gravity.CENTER_VERTICAL);
        tabStrip.setPadding(dp(8), dp(6), dp(8), dp(8));
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Theme.SURFACE_HIGH);
        tabScroll.addView(tabStrip);
        bottom.addView(tabScroll, new LinearLayout.LayoutParams(-1, -2));
        col.addView(line(), new LinearLayout.LayoutParams(-1, dp(1)));
        col.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        setContentView(col);
    }

    private View line() {
        View v = new View(this);
        v.setBackgroundColor(Theme.LINE);
        return v;
    }

    private View buildKeysRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(4));
        addKey(row, "TAB", v -> insertText("\t"));
        addKey(row, "~", v -> insertText("~"));
        addKey(row, "/", v -> insertText("/"));
        addKey(row, "-", v -> insertText("-"));
        addKey(row, "|", v -> insertText("|"));
        addKey(row, "^C", v -> {
            Session s = currentSession();
            if (s != null) interrupt(s);
        });
        addKey(row, "SALIN", v -> {
            Session s = currentSession();
            if (s != null && s.view != null) s.view.copyOutput();
        });
        addKey(row, "CLR", v -> clearScreen());

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(Theme.SURFACE_HIGH);
        hs.addView(row);
        return hs;
    }

    private void addKey(LinearLayout row, String label, View.OnClickListener click) {
        TextView k = Ui.text(this, label, 13, Theme.ON, true);
        k.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        k.setGravity(Gravity.CENTER);
        k.setPadding(dp(12), dp(8), dp(12), dp(8));
        k.setBackground(Ui.ripple(Theme.SURFACE_HIGHEST, 8));
        k.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(6), 0);
        row.addView(k, lp);
    }

    private ImageButton iconButton(int res, int color, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(9), dp(9), dp(9), dp(9));
        b.setBackground(Ui.ripple(0, 100));
        b.setOnClickListener(click);
        return b;
    }

    private GradientDrawable roundedBg(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    // ----- Tampilan sesi -----
    private void showSession(int i) {
        if (i < 0 || i >= sessions.size()) return;
        detachAll();
        filesTab = false;
        active = i;
        final Session s = sessions.get(i);
        s.view = new TerminalView(this, cmd -> runCommand(s, cmd));
        s.view.setOutput(s.log.toString());
        s.view.setPrompt(s.promptText());
        content.removeAllViews();
        content.addView(s.view, new FrameLayout.LayoutParams(-1, -1));
        keysRow.setVisibility(View.VISIBLE);
        refreshHeader();
        buildTabs();
    }

    private void showFiles() {
        detachAll();
        filesTab = true;
        content.removeAllViews();
        content.addView(filesScroll, new FrameLayout.LayoutParams(-1, -1));
        content.addView(fab, fabParams());
        keysRow.setVisibility(View.GONE);
        renderFiles();
        refreshHeader();
        buildTabs();
    }

    private FrameLayout.LayoutParams fabParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.END | Gravity.BOTTOM);
        lp.setMargins(0, 0, dp(16), dp(16));
        return lp;
    }

    private void refreshHeader() {
        if (titleView == null) return;
        if (filesTab) {
            titleView.setText("Files");
            pathView.setText(shortPath(fileDir, root));
        } else {
            Session s = currentSession();
            titleView.setText(s != null ? s.name : "Terminal");
            pathView.setText(s != null ? shortPath(s.cwd, root) : "");
        }
    }

    private void buildTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < sessions.size(); i++) {
            final int idx = i;
            boolean sel = !filesTab && i == active;
            TextView t = tabView(sessions.get(i).name, sel);
            t.setOnClickListener(v -> showSession(idx));
            t.setOnLongClickListener(v -> {
                confirmClose(idx);
                return true;
            });
            tabStrip.addView(t);
        }
        TextView files = tabView("Files", filesTab);
        files.setOnClickListener(v -> showFiles());
        tabStrip.addView(files);

        TextView add = tabView("+ Sesi", false);
        add.setOnClickListener(v -> showSession(createSession()));
        tabStrip.addView(add);
    }

    private TextView tabView(String label, boolean sel) {
        TextView t = Ui.text(this, label, 13, sel ? Theme.ON_PRIM_CONT : Theme.ON_VAR, sel);
        t.setSingleLine(true);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(sel ? roundedBg(Theme.PRIM_CONT, 100, 0, 0) : Ui.ripple(0, 100));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(3), 0, dp(3), 0);
        t.setLayoutParams(lp);
        return t;
    }

    // ----- Dialog: memakai Sheet bawaan Mona -----
    private TextView sheetTitle(String text) {
        TextView t = Ui.text(this, text, 18, Theme.ON, true);
        t.setPadding(dp(4), 0, dp(4), dp(12));
        return t;
    }

    private TextView sheetText(String text) {
        TextView t = Ui.text(this, text, 14, Theme.ON_VAR, false);
        t.setPadding(dp(4), 0, dp(4), dp(12));
        return t;
    }

    private LinearLayout buttonRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, 0);
        return r;
    }

    private TextView actionButton(String label, int style, View.OnClickListener click) {
        TextView b = styledButton(label, style, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    /** Tombol selebar sheet, untuk daftar aksi. */
    private TextView fullButton(String label, int style, View.OnClickListener click) {
        TextView b = styledButton(label, style, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));   // jarak antar tombol yang tersusun vertikal
        b.setLayoutParams(lp);
        return b;
    }

    private TextView styledButton(String label, int style, View.OnClickListener click) {
        int fg = style == STYLE_PRIMARY ? Theme.ON_PRIMARY : style == STYLE_DANGER ? Color.WHITE : Theme.ON;
        int bg = style == STYLE_PRIMARY ? Theme.PRIMARY : style == STYLE_DANGER ? Theme.ERROR : Theme.SURFACE_HIGHEST;
        TextView b = Ui.text(this, label, 14, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        b.setBackground(Ui.ripple(bg, 14));   // radius tombol sheet 14dp, riak sentuh saat ditekan
        b.setOnClickListener(click);
        return b;
    }

    private void confirmClose(final int idx) {
        if (sessions.size() <= 1) {
            toast("Sesi terakhir tidak bisa ditutup");
            return;
        }
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        box.addView(sheetTitle("Tutup " + sessions.get(idx).name + "?"));
        box.addView(sheetText("Proses yang sedang berjalan di sesi ini ikut dihentikan."));
        LinearLayout btns = buttonRow();
        btns.addView(actionButton("Batal", STYLE_PLAIN, v -> sheet.dismiss()));
        btns.addView(actionButton("Tutup", STYLE_DANGER, v -> {
            sheet.dismiss();
            Session s = sessions.remove(idx);
            s.close();
            active = idx > 0 ? idx - 1 : 0;
            showSession(active);
        }));
        box.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        sheet.show();
    }

    // ----- Perintah -----
    private void runCommand(Session s, String cmd) {
        if (!s.alive || s.stdin == null) startShell(s, getCacheDir());
        if (s.stdin == null) return;
        s.print(s.promptText() + cmd + "\n");
        final OutputStream os = s.stdin;
        // Setelah perintah, shell mencetak penanda direktori aktif (dipakai untuk header dan prompt)
        final String payload = cmd + "\necho \"" + PWD_MARK + "$PWD\"\n";
        s.writer.execute(() -> {
            try {
                os.write(payload.getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) {
                ui.post(() -> s.print("[gagal kirim perintah: " + e.getMessage() + "]\n"));
            }
        });
    }

    /** Ctrl+C: shell tidak punya TTY, jadi sesi dimulai ulang untuk menghentikan proses. */
    private void interrupt(Session s) {
        s.kill();
        s.print("^C\n");
        startShell(s, getCacheDir());
    }

    private void clearScreen() {
        Session s = currentSession();
        if (s == null) return;
        s.log.setLength(0);
        if (s.view != null) s.view.clear();
    }

    private void insertText(String t) {
        Session s = currentSession();
        if (s != null && s.view != null) s.view.insert(t);
    }

    // ----- Tab Files -----
    private void renderFiles() {
        filesCol.removeAllViews();
        if (!fileDir.getAbsolutePath().equals(root.getAbsolutePath())) {
            filesCol.addView(fileRow("..", "naik ke folder atas", v -> {
                fileDir = fileDir.getParentFile();
                renderFiles();
                refreshHeader();
            }, null));
        }
        File[] kids = fileDir.listFiles();
        if (kids == null) {
            filesCol.addView(note("Folder ini tidak bisa dibaca"));
            return;
        }
        Arrays.sort(kids, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        if (kids.length == 0) filesCol.addView(note("Folder kosong. Ketuk + untuk upload berkas."));
        for (final File f : kids) {
            String meta = (f.isDirectory() ? "folder" : humanSize(f.length()))
            + " · " + DATE.format(new Date(f.lastModified()));
            filesCol.addView(fileRow(f.getName(), meta, v -> {
                if (f.isDirectory()) {
                    fileDir = f;
                    renderFiles();
                    refreshHeader();
                } else {
                    openFile(f);
                }
            }, v -> {
                showInfo(f);
                return true;
            }));
        }
    }

    private View fileRow(String name, String meta, View.OnClickListener click, View.OnLongClickListener longClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(Ui.ripple(0, 12));
        row.setClickable(true);
        TextView n = Ui.text(this, name, 15, Theme.ON, false);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        TextView m = Ui.text(this, meta, 12, Theme.ON_VAR, false);
        row.addView(n);
        row.addView(m);
        row.setOnClickListener(click);
        if (longClick != null) row.setOnLongClickListener(longClick);
        return row;
    }

    private View note(String text) {
        TextView t = Ui.text(this, text, 13, Theme.ON_VAR, false);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        return t;
    }

    /** FAB: upload berkas atau buat folder baru di folder yang sedang dibuka. */
    private void showAddSheet() {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        box.addView(sheetTitle("Tambah di " + shortPath(fileDir, root)));
        box.addView(sheetText("Upload berkas dari perangkat, atau buat folder baru di sini."));
        box.addView(fullButton("Upload berkas", STYLE_PRIMARY, v -> {
            sheet.dismiss();
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_UPLOAD);
        }));
        box.addView(fullButton("Folder baru", STYLE_PLAIN, v -> {
            sheet.dismiss();
            askFolderName();
        }));
        sheet.show();
    }

    private void askFolderName() {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        box.addView(sheetTitle("Folder baru"));
        final EditText name = new EditText(this);
        Ui.tintCursor(name);
        name.setHint("Nama folder");
        name.setHintTextColor(Theme.ON_VAR);
        name.setTextColor(Theme.ON);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        name.setBackground(roundedBg(Theme.SURFACE, 12, Theme.LINE, 1));
        name.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout btns = buttonRow();
        btns.addView(actionButton("Batal", STYLE_PLAIN, v -> sheet.dismiss()));
        btns.addView(actionButton("Buat", STYLE_PRIMARY, v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty() || n.contains("/")) {
                toast("Nama folder tidak valid");
                return;
            }
            File d = new File(fileDir, n);
            if (d.mkdirs()) toast("Folder dibuat");
            else toast("Folder sudah ada atau gagal dibuat");
            sheet.dismiss();
            renderFiles();
        }));
        box.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        sheet.show();
    }

    private void uploadFile(Uri uri) {
        String name = "berkas_" + System.currentTimeMillis();
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0 && c.getString(col) != null) name = c.getString(col);
                }
            } finally {
                c.close();
            }
        }
        File dst = uniqueIn(fileDir, name.replaceAll("[\\\\/:*?\"<>|]+", "_"));
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dst)) {
            if (in == null) throw new IOException("berkas tidak bisa dibuka");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            toast("Diupload: " + dst.getName());
        } catch (IOException e) {
            dst.delete();
            toast("Upload gagal: " + e.getMessage());
        }
        if (filesTab) renderFiles();
    }

    private void openFile(File f) {
        long size = f.length();
        int cap = (int) (size < MAX_VIEW_BYTES ? size : MAX_VIEW_BYTES);
        byte[] data = new byte[cap];
        int got = 0;
        String text;
        try (InputStream in = new FileInputStream(f)) {
            while (got < cap) {
                int n = in.read(data, got, cap - got);
                if (n < 0) break;
                got += n;
            }
            boolean binary = false;
            for (int i = 0; i < got; i++) {
                if (data[i] == 0) {
                    binary = true;
                    break;
                }
            }
            text = binary
            ? "Berkas biner (" + humanSize(size) + "), tidak bisa ditampilkan sebagai teks."
            : new String(data, 0, got, "UTF-8");
            if (!binary && size > cap) text += "\n\n... (dipotong dari " + humanSize(size) + ")";
        } catch (IOException e) {
            toast("Gagal membaca: " + e.getMessage());
            return;
        }

        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        box.addView(sheetTitle(f.getName()));
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setTextColor(Theme.ON);
        tv.setTextIsSelectable(true);
        tv.setText(text);
        ScrollView sv = new ScrollView(this);
        sv.setBackground(roundedBg(Theme.SURFACE, 16, 0, 0));
        sv.setPadding(dp(12), dp(12), dp(12), dp(12));
        sv.addView(tv);
        box.addView(sv, new LinearLayout.LayoutParams(-1, dp(420)));
        LinearLayout btns = buttonRow();
        btns.addView(actionButton("Tutup", STYLE_PLAIN, v -> sheet.dismiss()));
        box.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        sheet.show();
    }

    private void showInfo(final File f) {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        box.addView(sheetTitle(f.getName()));
        box.addView(sheetText("Path: " + f.getAbsolutePath()
        + "\nUkuran: " + (f.isDirectory() ? "folder" : humanSize(f.length()))
        + "\nDiubah: " + DATE.format(new Date(f.lastModified()))));
        if (f.isDirectory()) {
            box.addView(fullButton("Buka di terminal", STYLE_PRIMARY, v -> {
                sheet.dismiss();
                openInTerminal(f);
            }));
        }
        box.addView(fullButton("Salin path", STYLE_PLAIN, v -> copyPath(f.getAbsolutePath())));
        box.addView(fullButton("Hapus", STYLE_DANGER, v -> {
            sheet.dismiss();
            confirmDelete(f);
        }));
        box.addView(fullButton("Tutup", STYLE_PLAIN, v -> sheet.dismiss()));
        sheet.show();
    }

    private void confirmDelete(final File f) {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        box.addView(sheetTitle("Hapus \"" + f.getName() + "\"?"));
        box.addView(sheetText(f.isDirectory()
        ? "Folder beserta isinya akan dihapus permanen." : "Berkas akan dihapus permanen."));
        LinearLayout btns = buttonRow();
        btns.addView(actionButton("Batal", STYLE_PLAIN, v -> sheet.dismiss()));
        btns.addView(actionButton("Hapus", STYLE_DANGER, v -> {
            sheet.dismiss();
            toast(deleteRecursive(f) ? "Dihapus" : "Gagal menghapus");
            renderFiles();
        }));
        box.addView(btns, new LinearLayout.LayoutParams(-1, -2));
        sheet.show();
    }

    private void copyPath(String path) {
        android.content.ClipboardManager cm =
        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("path", path));
        toast("Path disalin");
    }

    private void openInTerminal(File f) {
        if (sessions.isEmpty()) return;
        showSession(active);
        Session s = sessions.get(active);
        runCommand(s, "cd '" + f.getAbsolutePath().replace("'", "'\\''") + "'");
    }
}
