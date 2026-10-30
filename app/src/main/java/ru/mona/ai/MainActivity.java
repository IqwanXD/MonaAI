package ru.mona.ai;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MonaAI: chat AI dengan tampilan ala Claude. Pure Java, tanpa XML layout, androidx, atau appcompat.
 * Permintaan AI berjalan di latar belakang (MonaService). Riwayat, sesi, dan status pekerjaan
 * disimpan secara statis supaya tetap ada saat activity dibuat ulang atau aplikasi di latar belakang.
 *
 * Catatan: paket ini punya kelas ru.mona.ai.Math yang membayangi java.lang.Math,
 * jadi semua pemanggilan matematika memakai java.lang.Math secara eksplisit.
 */
public class MainActivity extends Activity {

    // ===== Ikon =====
    private static final int ICON_MORE     = R.drawable.icon_more_vert_outline;
    private static final int ICON_SEND     = R.drawable.icon_send_outline;
    private static final int ICON_NEW_CHAT = R.drawable.icon_chat_add;
    private static final int ICON_ADD      = R.drawable.icon_chats;
    private static final int ICON_HISTORY  = R.drawable.icon_history_outline;
    private static final int ICON_MENU     = R.drawable.icon_menu;
    private static final int ICON_PROFILE  = R.drawable.icon_user_circle;
    private static final int ICON_CREDITS  = R.drawable.icon_info_outline;
    private static final int ICON_UPLOAD   = R.drawable.icon_file_upload;
    private static final int ICON_TERMINAL = R.drawable.icon_command_line_prompt;
    private static final int ICON_DELETE   = R.drawable.icon_trash;
    private static final int RIPPLE10      = R.drawable.inoripple10;
    private static final int RIPPLE0       = R.drawable.inoripple0;

    private static final int EDGE_DP = 40;                       // lebar zona swipe dari tepi kiri
    private static final float SETTINGS_END_SCALE = 18f / 34f;   // judul pengaturan 34sp -> 18sp
    private static final int REQ_PICK_PHOTO = 7101;
    private static final String PROFILE_NAME = "profil.png";
    private static final String CREDITS_TEXT =
    "Mona AI\n\nUI terinspirasi Claude.\nDibuat dengan Java murni (tanpa XML, androidx, appcompat).\n\n"
    + "Dibuat oleh: IqwanoIno";

    // ===== Provider AI =====
    private static final int TYPE_OPENAI = 0, TYPE_ANTHROPIC = 1, TYPE_GEMINI = 2;

    private static class Provider {
        final String name, baseUrl;
        final int type;
        final boolean keyOptional;
        final String[] models;
        Provider(String name, String baseUrl, int type, boolean keyOptional, String... models) {
            this.name = name; this.baseUrl = baseUrl; this.type = type;
            this.keyOptional = keyOptional; this.models = models;
        }
    }

    private static final Provider[] PROVIDERS = {
        new Provider("OpenAI", "https://api.openai.com/v1", TYPE_OPENAI, false, "gpt-4o-mini", "gpt-4o"),
        new Provider("Anthropic", "https://api.anthropic.com/v1", TYPE_ANTHROPIC, false, "claude-sonnet-4-5", "claude-haiku-4-5"),
        new Provider("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", TYPE_GEMINI, false, "gemini-2.5-flash", "gemini-2.5-pro"),
        new Provider("OpenRouter", "https://openrouter.ai/api/v1", TYPE_OPENAI, false, "openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "google/gemini-flash-1.5"),
        new Provider("Groq", "https://api.groq.com/openai/v1", TYPE_OPENAI, false, "llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
        new Provider("DeepSeek", "https://api.deepseek.com", TYPE_OPENAI, false, "deepseek-chat", "deepseek-reasoner"),
        new Provider("Mistral", "https://api.mistral.ai/v1", TYPE_OPENAI, false, "mistral-small-latest", "mistral-large-latest"),
        new Provider("xAI", "https://api.x.ai/v1", TYPE_OPENAI, false, "grok-4.7", "grok-4.3"),
        new Provider("Together AI", "https://api.together.ai/v1", TYPE_OPENAI, false, "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
        new Provider("Custom (OpenAI-compatible)", "http://localhost:11434/v1", TYPE_OPENAI, true, "llama3"),
    };

    // ===== Model data =====
    private static class ChatMessage {
        final String content;
        final boolean isUser;
        /** Langkah proses (pikiran) untuk balasan AI. */
        final List<String> steps;
        /** Path berkas yang dilampirkan AI (kartu unduhan), atau null. */
        final String attach;
        /** Path berkas yang dilampirkan user (ditampilkan di atas bubble, tidak digabung ke teks). */
        final List<String> files;
        /** Teks tambahan khusus API (isi berkas lampiran). Tidak ditampilkan di chat. */
        String extra = "";
        /** Respon pengguna pada balasan ini: 1 suka, -1 tidak suka, 0 belum. */
        int feedback;
        ChatMessage(String content, boolean isUser) {
            this(content, isUser, new ArrayList<String>(), null, new ArrayList<String>());
        }
        ChatMessage(String content, boolean isUser, List<String> steps, String attach) {
            this(content, isUser, steps, attach, new ArrayList<String>());
        }
        ChatMessage(String content, boolean isUser, List<String> steps, String attach, List<String> files) {
            this.content = content;
            this.isUser = isUser;
            this.steps = steps != null ? steps : new ArrayList<String>();
            this.attach = attach;
            this.files = files != null ? files : new ArrayList<String>();
        }
    }

    private static class ChatSession {
        String id = String.valueOf(System.currentTimeMillis());
        String title = "Chat baru";
        long updated = System.currentTimeMillis();
        final List<ChatMessage> messages = new ArrayList<>();
    }

    // ===== State bersama (hidup selama proses, dipakai semua activity) =====
    private static SharedPreferences prefs;
    private static List<ChatSession> sessions;
    private static boolean busy;                 // ada permintaan AI yang sedang berjalan
    private static ChatSession pendingSession;   // chat tempat permintaan berjalan
    private static MainActivity live;            // activity yang sedang tampil, null jika di latar belakang
    private static boolean dirty;                // ada hasil baru saat tidak tampil: render ulang saat kembali
    private static boolean pendingRecreate;      // tema diganti saat permintaan masih berjalan
    private static final Handler handler = new Handler(Looper.getMainLooper());

    // ===== State per activity =====
    private ChatSession current;                 // null = chat baru yang belum dikirim
    private ModeChanger modeChanger;
    private MonaShell shell;
    private View typingRow;
    private ThinkingLive typingLive;
    /** Token animasi streaming teks; naik saat stop/stream baru. */
    private long streamToken = 0;
    private Runnable streamRunnable;

    private int insetTop, insetBottom, insetNav, imeBottom;
    private LinearLayout headerView, inputOuter, drawerView;
    private LinearLayout inputTopRow, inputBottomRow;
    private boolean inputExpanded;

    private DrawerLayoutView drawerLayout;
    private FrameLayout rootFrame;
    private LinearLayout chatContainer;
    private ScrollView scrollView;
    private EditText inputField;
    private ImageButton sendButton;
    private LinearLayout pendingRow;
    private android.widget.HorizontalScrollView pendingScroll;
    private TextView modelPill;
    private ImageButton plusBtn;
    private TextView subtitle, userNameText, userAvatarText, emptyHistory;
    private HistoryAdapter historyAdapter;

    // ===== Layar pengaturan (overlay, judul mengecil saat di-scroll) =====
    private FrameLayout settingsScreen;          // null jika tertutup
    private ScrollView settingsScroll;
    private LinearLayout settingsCol;
    private FrameLayout settingsHeader;
    private TextView settingsTitle;
    private ImageView settingsBack;
    private FrameLayout.LayoutParams settingsBackLp;
    private int sExpandedH, sCollapsedH, sMaxScroll, sLastY;

    private interface OnPick { void pick(int index); }
    private interface Toggle { void onToggle(boolean on); }
    private interface TextSink { void set(String s); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        Prefs.init(this);
        prefs = Prefs.get(this);
        Theme.load(this);
        if (sessions == null) loadSessions();
        shell = new MonaShell(this);
        modeChanger = new ModeChanger(this, prefs);
        modeChanger.setUserName(prefs.getString("user_name", ""));
        if (savedInstanceState != null) {
            String cur = savedInstanceState.getString("cur");
            if (cur != null) for (ChatSession s : sessions) if (s.id.equals(cur)) current = s;
        }

        Ui.setupEdge(this);

        rootFrame = new FrameLayout(this);
        drawerLayout = new DrawerLayoutView(this);
        drawerLayout.setViews(buildContent(), buildDrawer());
        rootFrame.addView(drawerLayout, new FrameLayout.LayoutParams(-1, -1));
        rootFrame.setOnApplyWindowInsetsListener((v, ins) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = ins.getInsets(WindowInsets.Type.ime());
                insetTop = bars.top;
                insetNav = bars.bottom;
                imeBottom = ime.bottom;
                if (!imeAnimating) insetBottom = java.lang.Math.max(bars.bottom, ime.bottom);
            } else {
                insetTop = ins.getSystemWindowInsetTop();
                insetNav = ins.getSystemWindowInsetBottom();
                insetBottom = insetNav;
            }
            applyInsets();
            return ins;
        });
        setupImeAnimation();
        setContentView(rootFrame);
        applyBarAppearance();
        rootFrame.requestApplyInsets();

        renderCurrent();
        updateSubtitle();
        refreshHistory();
        refreshUserFooter();

        if (savedInstanceState != null && savedInstanceState.getBoolean("settings")) {
            rootFrame.post(this::showSettingsScreen);   // tema diganti: buka lagi pengaturan
        } else if (savedInstanceState == null && !shell.hasStoragePermission()) {
            rootFrame.post(this::showPermissionSheet);  // minta izin penyimpanan saat pertama dibuka
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        live = this;
        // Jawaban yang selesai saat app di latar belakang sudah mengubah busy; ikon kirim disegarkan di sini
        updateSendButton();
        applyOfflineState();
        if (pendingRecreate && !busy) {
            pendingRecreate = false;
            recreate();
            return;
        }
        updateSubtitle();   // model bisa berganti otomatis saat aplikasi di latar belakang
        if (dirty) {
            dirty = false;
            renderCurrent();
            refreshHistory();
        }
    }

    @Override
    protected void onPause() {
        if (live == this) live = null;
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (current != null) out.putString("cur", current.id);
        out.putBoolean("settings", settingsScreen != null);
    }

    @Override
    public void onBackPressed() {
        if (Sheet.current != null) Sheet.current.dismiss();
        else if (settingsScreen != null) closeSettingsScreen();
        else if (drawerLayout != null && drawerLayout.isOpen()) drawerLayout.close();
        else if (inputField != null && inputField.hasFocus()) {
            // Back saat keyboard terbuka: tutup keyboard saja, jangan keluar activity
            releaseInputFocus();
        } else super.onBackPressed();
    }

    // =====================================================================
    //  WINDOW + INSET (edge-to-edge)
    // =====================================================================
    /** Harus dipanggil SETELAH setContentView (DecorView baru ada). */
    private void applyBarAppearance() {
        M3.styleBars(getWindow());
    }

    private int appliedTop = -1, appliedBottom = -1;

    private void applyInsets() {
        Ui.insetTop = insetTop;
        Ui.insetBottom = insetBottom;
        // Hanya relayout bila nilainya benar-benar berubah. Tanpa ini, setiap frame animasi
        // keyboard memicu layout ulang header dan membuat gerakan terasa patah-patah.
        if (headerView != null && insetTop != appliedTop) {
            headerView.setPadding(dp(8), insetTop, dp(8), 0);
            ViewGroup.LayoutParams lp = headerView.getLayoutParams();
            if (lp != null) { lp.height = dp(56) + insetTop; headerView.setLayoutParams(lp); }
            appliedTop = insetTop;
        }
        if (inputOuter != null && insetBottom != appliedBottom) {
            inputOuter.setPadding(dp(12), dp(8), dp(12), dp(12) + insetBottom);
            appliedBottom = insetBottom;
        }
        if (drawerView != null) drawerView.setPadding(0, insetTop, 0, insetNav);
        if (settingsScreen != null) applySettingsInsets();
    }

    // ---------------------------------------------------------------------
    //  KEYBOARD: input bar naik/turun mulus mengikuti keyboard (ala Gemini)
    // ---------------------------------------------------------------------
    private boolean imeAnimating;
    private boolean imeSticky;   // true = kolom input harus tetap fokus + keyboard tetap tampil

    private void setupImeAnimation() {
        if (Build.VERSION.SDK_INT < 30) return;
        rootFrame.setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(
        WindowInsetsAnimation.Callback.DISPATCH_MODE_STOP) {
            @Override public void onPrepare(WindowInsetsAnimation a) {
                if ((a.getTypeMask() & WindowInsets.Type.ime()) != 0) imeAnimating = true;
            }
            @Override public WindowInsets onProgress(WindowInsets ins, List<WindowInsetsAnimation> anims) {
                if (!imeAnimating) return ins;
                int ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
                insetBottom = java.lang.Math.max(insetNav, ime);
                applyInsets();
                return ins;
            }
            @Override public void onEnd(WindowInsetsAnimation a) {
                if ((a.getTypeMask() & WindowInsets.Type.ime()) == 0) return;
                imeAnimating = false;
                insetBottom = java.lang.Math.max(insetNav, imeBottom);
                applyInsets();
                // Posisi chat dibiarkan: fokus/unfokus tidak boleh menggeser riwayat yang sedang dibaca.
            }
        });
        // Saat tinggi area chat berubah (keyboard muncul/hilang, input difokus/tidak difokus),
        // bila pengguna sedang di bawah, tetap di bawah. Posisi saat membaca riwayat dibiarkan.
        scrollView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            int oldH = ob - ot, newH = b - t;
            if (oldH == newH || newH <= 0) return;
            int content = chatContainer.getHeight();
            boolean atBottom = scrollView.getScrollY() >= content - oldH - dp(8);
            if (atBottom) scrollView.scrollTo(0, java.lang.Math.max(0, content - newH));
        });
    }

    /** Tap di luar kolom input -> lepas fokus + tutup keyboard. Selain itu keyboard dibiarkan. */
    private float tapX, tapY;
    private boolean tapCandidate;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (inputField != null && inputField.hasFocus()) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tapX = ev.getRawX(); tapY = ev.getRawY();
                    tapCandidate = !isInsideInput(ev.getRawX(), ev.getRawY());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (tapCandidate) {
                        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
                        if (java.lang.Math.abs(ev.getRawX() - tapX) > slop
                        || java.lang.Math.abs(ev.getRawY() - tapY) > slop) tapCandidate = false;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (tapCandidate) releaseInputFocus();
                    tapCandidate = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    tapCandidate = false;
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isInsideInput(float rx, float ry) {
        if (inputOuter == null) return false;
        int[] loc = new int[2];
        inputOuter.getLocationOnScreen(loc);
        return rx >= loc[0] && rx <= loc[0] + inputOuter.getWidth()
        && ry >= loc[1] && ry <= loc[1] + inputOuter.getHeight();
    }

    /** Lepas fokus input dengan sengaja (tap luar, buka drawer, buka pengaturan). */
    private void releaseInputFocus() {
        imeSticky = false;
        if (inputField != null) inputField.clearFocus();
        hideKeyboard();
    }

    // =====================================================================
    //  DRAWER (custom, mode PUSH: konten ikut terdorong ke kanan, bisa swipe & drag)
    // =====================================================================
    private class DrawerLayoutView extends FrameLayout {
        private static final float PARALLAX = 0.25f;   // drawer ikut bergeser pelan dari kiri
        private static final float SHRINK   = 0.06f;   // konten mengecil sedikit saat terdorong
        private FrameLayout contentHolder;
        private View scrim, drawer;
        private final int drawerWidth, slop, edge;
        private float offset = 0f, downX, downY, lastX;
        private boolean dragging, candidate;
        private VelocityTracker vt;
        private ValueAnimator anim;

        DrawerLayoutView(Context c) {
            super(c);
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            drawerWidth = java.lang.Math.min(dp(320), (int) (sw * 0.82f));
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            edge = dp(EDGE_DP);
        }

        void setViews(View content, View drawerV) {
            removeAllViews();
            // latar seluruh area drawer = warna drawer, sehingga tepi chat yang membulat tidak pernah memperlihatkan warna lain
            setBackgroundColor(Theme.BG);

            // Lapisan bawah: drawer (diam di belakang, hanya terlihat saat konten bergeser)
            drawer = drawerV;
            drawer.setClickable(true);
            addView(drawer, new LayoutParams(drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT));

            // Lapisan atas: konten + peredup. Seluruhnya yang bergeser (push).
            contentHolder = new FrameLayout(getContext());
            contentHolder.addView(content, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scrim = new View(getContext());
            scrim.setBackgroundColor(Color.BLACK);
            scrim.setOnClickListener(v -> close());
            contentHolder.addView(scrim, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            contentHolder.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(28) * offset);
                }
            });
            addView(contentHolder, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setOffset(0f);
        }

        boolean isOpen() { return offset > 0f; }

        void setOffset(float o) {
            float prev = offset;
            offset = java.lang.Math.max(0f, java.lang.Math.min(1f, o));

            // konten terdorong ke kanan sejauh lebar drawer
            float sc = 1f - SHRINK * offset;
            contentHolder.setPivotX(0f);
            contentHolder.setPivotY(contentHolder.getHeight() / 2f);
            contentHolder.setScaleX(sc);
            contentHolder.setScaleY(sc);
            contentHolder.setTranslationX(drawerWidth * offset);
            contentHolder.setClipToOutline(offset > 0f);   // sudut membulat hanya saat bergeser
            contentHolder.invalidateOutline();

            // drawer paralaks halus dari kiri
            drawer.setTranslationX(-drawerWidth * PARALLAX * (1f - offset));
            drawer.setVisibility(offset > 0f ? View.VISIBLE : View.INVISIBLE);

            // konten diredupkan (di tema terang lebih tipis)
            scrim.setAlpha(offset * (Theme.dark ? 0.35f : 0.12f));
            scrim.setVisibility(offset > 0f ? View.VISIBLE : View.GONE);

            if (prev == 0f && offset > 0f) releaseInputFocus();
        }

        void open()  { animateTo(1f); }
        void close() { animateTo(0f); }

        private void animateTo(float target) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(offset, target);
            anim.setDuration((long) (260 * java.lang.Math.abs(target - offset)) + 60);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(a -> setOffset((float) a.getAnimatedValue()));
            anim.start();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (vt == null) vt = VelocityTracker.obtain();
            vt.addMovement(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = false;
                boolean animating = anim != null && anim.isRunning();
                if (animating) anim.cancel();
                candidate = isOpen() || downX <= edge || animating;
                break;
                case MotionEvent.ACTION_MOVE:
                if (!candidate || dragging) break;
                float dx = ev.getX() - downX, dy = ev.getY() - downY;
                if (java.lang.Math.abs(dx) > slop && java.lang.Math.abs(dx) > java.lang.Math.abs(dy)
                && (offset > 0f || dx > 0)) {
                    dragging = true;
                    lastX = ev.getX();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                candidate = false;
                if (vt != null) { vt.recycle(); vt = null; }
                break;
            }
            return dragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (vt == null) vt = VelocityTracker.obtain();
            vt.addMovement(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                return true;
                case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float x = ev.getX();
                    setOffset(offset + (x - lastX) / drawerWidth);
                    lastX = x;
                }
                break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    vt.computeCurrentVelocity(1000);
                    float vx = vt.getXVelocity();
                    boolean open = java.lang.Math.abs(vx) > dp(500) ? vx > 0 : offset > 0.5f;
                    animateTo(open ? 1f : 0f);
                }
                dragging = false;
                candidate = false;
                if (vt != null) { vt.recycle(); vt = null; }
                break;
            }
            return true;
        }
    }

    // =====================================================================
    //  KONTEN UTAMA
    // =====================================================================
    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);

        headerView = buildHeader();
        root.addView(headerView, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setVerticalFadingEdgeEnabled(true);   // fade atas/bawah
        scrollView.setFadingEdgeLength(dp(30));
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(dp(16), dp(8), dp(16), dp(16));
        scrollView.addView(chatContainer, new ScrollView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout scrollHolder = new FrameLayout(this);
        scrollHolder.addView(scrollView, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        jumpDown = buildJumpDown();
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(dp(42), dp(42),
        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        jp.bottomMargin = dp(14);
        scrollHolder.addView(jumpDown, jp);
        jumpShown = false;
        scrollView.getViewTreeObserver().addOnScrollChangedListener(this::updateJumpButton);
        chatContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> updateJumpButton());
        root.addView(scrollHolder, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        inputOuter = buildInputBar();
        root.addView(inputOuter, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Theme.BG);
        header.setPadding(dp(8), 0, dp(8), 0);

        header.addView(headerIcon(ICON_MENU, v -> drawerLayout.open()),
        new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(8), 0, 0, 0);
        TextView title = Ui.text(this, "Mona", 18, Theme.ON, true);
        subtitle = Ui.text(this, "", 14, Theme.ON_VAR, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(headerIcon(R.drawable.ic_thin_search, v -> showSearchSheet()),
        new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(headerIcon(ICON_NEW_CHAT, v -> startNewChat()),
        new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(headerIcon(ICON_MORE, v -> showMorePopup(v)),
        new LinearLayout.LayoutParams(dp(44), dp(44)));
        return header;
    }

    /**
     * Input ala Claude: kolom teks di atas, baris bawah berisi [+] [model] ... [kirim/berhenti].
     */
    private LinearLayout buildInputBar() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Theme.BG);
        outer.setPadding(dp(12), dp(8), dp(12), dp(12));

        LinearLayout inputBox = new LinearLayout(this);
        inputBox.setOrientation(LinearLayout.VERTICAL);
        inputBox.setBackground(roundedBg(Theme.SURFACE, 26, Theme.LINE, 1));
        inputBox.setPadding(dp(8), dp(6), dp(8), dp(6));

        pendingRow = new LinearLayout(this);
        pendingRow.setOrientation(LinearLayout.HORIZONTAL);
        pendingRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        pendingRow.setVisibility(View.GONE);
        pendingScroll = new android.widget.HorizontalScrollView(this);
        pendingScroll.setHorizontalScrollBarEnabled(false);
        pendingScroll.addView(pendingRow);
        inputBox.addView(pendingScroll, new LinearLayout.LayoutParams(-1, -2));
        refreshPendingRow();

        inputField = new EditText(this);
        inputField.setHint("Tanya Mona...");
        inputField.setHintTextColor(Theme.ON_VAR);
        inputField.setTextColor(Theme.ON);
        inputField.setTextSize(15);
        inputField.setBackground(null);
        inputField.setInputType(InputType.TYPE_CLASS_TEXT
        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputField.setMaxLines(6);
        inputField.setOnFocusChangeListener((v, has) -> {
            // Unfocus sengaja (back / tap luar) tidak merebut fokus lagi; imeSticky sudah
            // di-set false di releaseInputFocus().
            if (has) imeSticky = true;
        });
        inputField.setPadding(dp(8), dp(8), dp(8), dp(8));
        Ui.tintCursor(inputField);   // kursor oranye Mona, bukan hijau sistem
        inputBox.addView(inputField, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        // Tombol + (tambah lampiran) — rapi, rounded
        ImageButton plus = iconButton(R.drawable.ic_plus, Theme.ON_VAR, v -> showAddSheet());
        plus.setBackground(roundedBg(Theme.SURFACE_HIGH, 100, 0, 0));
        plus.setPadding(dp(10), dp(10), dp(10), dp(10));
        plus.setContentDescription("Tambah lampiran");
        plusBtn = plus;
        bottom.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));

        // Pill model — lebih rapi, sedikit padding
        modelPill = Ui.text(this, "", 13, Theme.ON_VAR, false);
        modelPill.setSingleLine(true);
        modelPill.setEllipsize(TextUtils.TruncateAt.END);
        modelPill.setGravity(Gravity.CENTER);          // teks tepat di tengah pill
        modelPill.setIncludeFontPadding(false);
        modelPill.setMinWidth(dp(56));
        modelPill.setPadding(dp(14), 0, dp(14), 0);    // tinggi diatur oleh LayoutParams (40dp)
        modelPill.setBackground(roundedBg(Theme.SURFACE_HIGH, 100, 0, 0));
        modelPill.setOnClickListener(v -> pickModel(getProvider()));
        modelPill.setContentDescription("Pilih model");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, dp(40));
        mp.leftMargin = dp(8);
        bottom.addView(modelPill, mp);

        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Tombol kirim / stop
        sendButton = iconButton(ICON_SEND, Color.WHITE, v -> handleSend());
        sendButton.setBackground(roundedBg(Theme.PRIMARY, 100, 0, 0));
        sendButton.setFocusable(false);
        sendButton.setFocusableInTouchMode(false);
        sendButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        bottom.addView(sendButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        inputBox.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        refreshModelPill();
        updateSendButton();
        outer.addView(inputBox, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    // =====================================================================
    //  ISI DRAWER
    // =====================================================================
    private View drawerItem(int icon, String label, final Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(Ui.ripple(Theme.SURFACE, 14));   // item drawer: latar dan radius 14 ala Claude
        row.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Theme.ON_VAR, PorterDuff.Mode.SRC_IN);
        row.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = dp(20);
        row.addView(Ui.text(this, label, 16, Theme.ON, false), tl);
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private View buildDrawer() {
        LinearLayout d = new LinearLayout(this);
        d.setOrientation(LinearLayout.VERTICAL);
        d.setBackgroundColor(Theme.BG);
        drawerView = d;

        // --- Judul drawer ---
        TextView drawerTitle = new TextView(this);
        drawerTitle.setText("Mona");
        drawerTitle.setTextColor(Theme.ON);
        drawerTitle.setTextSize(40);
        drawerTitle.setTypeface(Typeface.SERIF, Typeface.NORMAL);
        drawerTitle.setPadding(dp(22), dp(24), dp(18), dp(12));
        d.addView(drawerTitle, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- Menu: Terminal (shell di folder data aplikasi, lihat TerminalActivity) ---
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuLp.setMargins(dp(10), 0, dp(10), dp(4));
        d.addView(drawerItem(ICON_TERMINAL, "Terminal", () -> {
            drawerLayout.close();
            startActivity(new Intent(this, TerminalActivity.class));
        }), menuLp);

        // --- Judul daftar: Riwayat ---
        TextView historyTitle = Ui.text(this, "Riwayat", 15, Theme.ON, true);
        historyTitle.setPadding(dp(22), dp(8), dp(18), dp(2));
        d.addView(historyTitle, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- Daftar obrolan, dikelompokkan per tanggal ---
        FrameLayout listFrame = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setDivider(null);
        list.setPadding(dp(8), dp(0), dp(8), dp(8));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setVerticalFadingEdgeEnabled(true);
        list.setFadingEdgeLength(dp(40));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        historyAdapter = new HistoryAdapter();
        list.setAdapter(historyAdapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= historyRows.size()) return;
            ChatSession picked = historyRows.get(pos).session;
            if (picked == null) return;   // baris header tanggal
            current = picked;
            renderCurrent();
            historyAdapter.notifyDataSetChanged();
            drawerLayout.close();
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos >= 0 && pos < historyRows.size() && historyRows.get(pos).session != null) {
                confirmDelete(historyRows.get(pos).session);
            }
            return true;
        });
        listFrame.addView(list, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyHistory = Ui.text(this, "Belum ada obrolan", 13, Theme.ON_VAR, false);
        emptyHistory.setGravity(Gravity.CENTER);
        listFrame.addView(emptyHistory, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.addView(listFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // --- Footer: tombol pengguna (buka pengaturan) + tombol chat baru ---
        View line = new View(this);
        line.setBackgroundColor(Theme.LINE);
        d.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(10), dp(10), dp(14), dp(10));

        LinearLayout userButton = new LinearLayout(this);
        userButton.setOrientation(LinearLayout.HORIZONTAL);
        userButton.setGravity(Gravity.CENTER_VERTICAL);
        userButton.setPadding(dp(8), dp(6), dp(12), dp(6));
        userButton.setBackgroundResource(RIPPLE10);
        userButton.setClickable(true);
        userButton.setOnClickListener(v -> { drawerLayout.close(); showSettingsScreen(); });
        userAvatarText = makeAvatar("U", 14, Theme.ON_VAR);
        LinearLayout.LayoutParams uav = new LinearLayout.LayoutParams(dp(36), dp(36));
        uav.setMarginEnd(dp(12));
        userButton.addView(userAvatarText, uav);
        userNameText = Ui.text(this, "", 14, Theme.ON, false);
        userNameText.setSingleLine(true);
        userNameText.setEllipsize(TextUtils.TruncateAt.END);
        userButton.addView(userNameText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        footer.addView(userButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton newChatButton = new ImageButton(this);
        newChatButton.setImageResource(ICON_ADD);
        newChatButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        newChatButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        newChatButton.setPadding(dp(13), dp(13), dp(13), dp(13));
        newChatButton.setContentDescription("Chat baru");
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.OVAL);
        fill.setColor(Theme.PRIMARY);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        newChatButton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), fill, mask));
        newChatButton.setOnClickListener(v -> { startNewChat(); drawerLayout.close(); });
        LinearLayout.LayoutParams ncp = new LinearLayout.LayoutParams(dp(48), dp(48));
        ncp.setMarginStart(dp(8));
        footer.addView(newChatButton, ncp);

        d.addView(footer, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return d;
    }

    // ----- Baris daftar riwayat: header tanggal (session == null) atau satu chat -----
    private static class HistoryRow {
        final String header;
        final ChatSession session;
        HistoryRow(String header, ChatSession session) { this.header = header; this.session = session; }
    }

    private final List<HistoryRow> historyRows = new ArrayList<>();

    /** Waktu chat; fallback ke id (yang berisi timestamp) untuk data lama tanpa "updated". */
    private long chatTime(ChatSession s) {
        if (s.updated > 0) return s.updated;
        try { return Long.parseLong(s.id); } catch (NumberFormatException e) { return 0L; }
    }

    private long startOfDay(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String dateGroup(long t) {
        long days = java.lang.Math.round((startOfDay(System.currentTimeMillis()) - startOfDay(t)) / 86400000.0);
        if (days <= 0) return "Hari ini";
        if (days == 1) return "Kemarin";
        if (days <= 7) return "7 Hari Terakhir";
        if (days <= 30) return "30 Hari Terakhir";
        return new java.text.SimpleDateFormat("MMMM yyyy", new Locale("id", "ID"))
        .format(new java.util.Date(t));
    }

    /** Susun ulang daftar: urut terbaru dulu, lalu sisipkan header tiap ganti kelompok tanggal. */
    private void rebuildHistoryRows() {
        historyRows.clear();
        List<ChatSession> sorted = new ArrayList<>(sessions);
        Collections.sort(sorted, (a, b) -> Long.compare(chatTime(b), chatTime(a)));
        String last = null;
        for (ChatSession s : sorted) {
            String g = dateGroup(chatTime(s));
            if (!g.equals(last)) { historyRows.add(new HistoryRow(g, null)); last = g; }
            historyRows.add(new HistoryRow(null, s));
        }
    }

    private class HistoryAdapter extends BaseAdapter {
        HistoryAdapter() { rebuildHistoryRows(); }

        @Override public void notifyDataSetChanged() {
            rebuildHistoryRows();
            super.notifyDataSetChanged();
        }
        @Override public int getCount() { return historyRows.size(); }
        @Override public Object getItem(int i) { return historyRows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return historyRows.get(i).session == null ? 0 : 1; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int i) { return historyRows.get(i).session != null; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            HistoryRow r = historyRows.get(pos);
            if (r.session == null) {
                TextView h = Ui.text(MainActivity.this, r.header, 12, Theme.ON_VAR, true);
                h.setPadding(dp(12), pos == 0 ? dp(10) : dp(18), dp(12), dp(6));
                return h;
            }

            ChatSession s = r.session;
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            if (s == current) row.setBackground(roundedBg(Theme.SURFACE_HIGH, 14, 0, 0));
            else row.setBackground(Ui.ripple(0, 14));

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageResource(ICON_HISTORY);
            icon.setColorFilter(Theme.ON_VAR, PorterDuff.Mode.SRC_IN);
            row.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

            TextView t = Ui.text(MainActivity.this, s.title, 14, Theme.ON, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(12), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return row;
        }
    }

    private void refreshHistory() {
        if (historyAdapter != null) historyAdapter.notifyDataSetChanged();
        if (emptyHistory != null) emptyHistory.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void refreshUserFooter() {
        String n = prefs.getString("user_name", "Pengguna");
        if (userNameText != null) userNameText.setText(n);
        if (userAvatarText != null) paintAvatar(userAvatarText, loadSavedPhoto(), n, 36, 14);
    }

    private void updateSubtitle() {
        String s;
        if (modeChanger != null && modeChanger.isOffline()) {
            s = "Offline · model.json";
        } else {
            Provider p = getProvider();
            s = p.name + " · " + getModel(p);
        }
        if (subtitle != null) subtitle.setText(s);
        refreshModelPill();
    }

    private void startNewChat() {
        current = null;
        renderCurrent();
        refreshHistory();
    }

    private void confirmDelete(final ChatSession s) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(16));
        box.addView(dialogTitle("Hapus chat?"));

        TextView msg = Ui.text(this, s.title, 14, Theme.ON_VAR, false);
        msg.setPadding(0, dp(10), 0, dp(16));
        box.addView(msg);

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.END);
        TextView cancel = button("Batal", false);
        TextView ok = button("Hapus", true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMarginStart(dp(8));
        btns.addView(cancel);
        btns.addView(ok, bp);
        box.addView(btns);

        final Dialog dialog = createDialog(box);
        cancel.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            sessions.remove(s);
            if (current == s) { current = null; renderCurrent(); }
            saveSessions();
            refreshHistory();
            dialog.dismiss();
        });
        showDialog(dialog);
    }

    // =====================================================================
    //  POPUP MENU "MORE"
    // =====================================================================
    private void showMorePopup(View anchor) {
        final PopupWindow popup;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundedBg(Theme.SURFACE, 14, Theme.LINE, 1));
        box.setPadding(0, dp(6), 0, dp(6));
        final float radius = dp(14);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
        popup = new PopupWindow(box, dp(200), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));

        final ChatSession selected = current;
        box.addView(popupRow(ICON_UPLOAD, "Bagikan", () -> {
            popup.dismiss();
            if (selected == null || selected.messages.isEmpty()) {
                M3.toast(this, "Belum ada obrolan untuk dibagikan");
                return;
            }
            StringBuilder body = new StringBuilder();
            for (ChatMessage m : selected.messages) body.append(m.isUser ? "Kamu: " : "Mona: ").append(m.content).append("\n\n");
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, body.toString());
            startActivity(Intent.createChooser(send, "Bagikan obrolan"));
        }));
        box.addView(popupDivider());
        box.addView(popupRow(ICON_PROFILE, "Ganti nama", () -> {
            popup.dismiss();
            if (selected == null) {
                M3.toast(this, "Pilih obrolan terlebih dahulu");
                return;
            }
            showRenameChat(selected);
        }));
        box.addView(popupDivider());
        box.addView(popupRow(ICON_DELETE, "Hapus", () -> {
            popup.dismiss();
            if (selected != null) confirmDelete(selected);
            else M3.toast(this, "Pilih obrolan terlebih dahulu");
        }));
        popup.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    /** Garis tipis, tidak full: mulai sejajar teks, berhenti sebelum tepi kanan. */
    private View popupDivider() {
        View v = new View(this);
        v.setBackgroundColor(Theme.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, java.lang.Math.max(1, dp(1) / 2));
        lp.setMargins(dp(46), 0, dp(14), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private View popupRow(int icon, String text, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundResource(RIPPLE0);
        row.setOnClickListener(v -> action.run());

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Theme.ON_VAR, PorterDuff.Mode.SRC_IN);
        row.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView tv = Ui.text(this, text, 15, Theme.ON, false);
        tv.setPadding(dp(14), 0, 0, 0);
        row.addView(tv);
        return row;
    }

    // =====================================================================
    //  IZIN PENYIMPANAN (bottom sheet saat pertama dibuka)
    // =====================================================================
    private void showPermissionSheet() {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;

        TextView t = Ui.text(this, "Izin penyimpanan", 20, Theme.ON, true);
        t.setPadding(dp(4), 0, dp(4), dp(8));
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView d = Ui.text(this, "Mona perlu akses penyimpanan untuk membaca, membuat, dan mengirim "
        + "file di folder Download. Chat tetap bisa dipakai tanpa izin ini, tetapi fitur file tidak akan berfungsi.",
        14, Theme.ON_VAR, false);
        d.setLineSpacing(dp(2), 1f);
        d.setPadding(dp(4), 0, dp(4), dp(8));
        box.addView(d, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.END);
        TextView later = button("Nanti", false);
        TextView allow = button("Izinkan", true);
        later.setOnClickListener(v -> sheet.dismiss());
        allow.setOnClickListener(v -> {
            sheet.dismiss();
            requestAllPermissions();
        });
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-2, -2);
        m.setMarginStart(dp(8));
        btns.addView(later);
        btns.addView(allow, m);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = dp(16);
        box.addView(btns, bl);
        sheet.show();
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ "android.permission.POST_NOTIFICATIONS" }, 1002);
        }
        if (!shell.hasStoragePermission()) shell.requestStoragePermission();
    }

    // =====================================================================
    //  PROFIL + PENGATURAN
    // =====================================================================
    private void showSettingsScreen() {
        if (settingsScreen != null) return;
        imeSticky = false;
        sLastY = 0;
        settingsScreen = buildSettingsScreen();
        rootFrame.addView(settingsScreen, new FrameLayout.LayoutParams(-1, -1));
        applyInsets();
        settingsScreen.setTranslationX(getResources().getDisplayMetrics().widthPixels);
        settingsScreen.animate().translationX(0).setDuration(220)
        .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeSettingsScreen() {
        final FrameLayout s = settingsScreen;
        if (s == null) return;
        settingsScreen = null;
        settingsScroll = null;
        settingsCol = null;
        settingsHeader = null;
        settingsTitle = null;
        settingsBack = null;
        settingsBackLp = null;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(s.getWindowToken(), 0);
        s.animate().translationX(getResources().getDisplayMetrics().widthPixels).setDuration(180)
        .withEndAction(() -> rootFrame.removeView(s)).start();
    }

    private FrameLayout buildSettingsScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Theme.BG);
        screen.setClickable(true);                 // jangan tembus ke chat di bawahnya

        final ScrollView sv = new ScrollView(this);
        settingsScroll = sv;
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        settingsCol = new LinearLayout(this);
        settingsCol.setOrientation(LinearLayout.VERTICAL);
        sv.addView(settingsCol, new FrameLayout.LayoutParams(-1, -2));
        // Konten selalu cukup panjang untuk di-scroll sejauh sMaxScroll, supaya judul bisa mengecil
        sv.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int min = (bt - t) + sMaxScroll;
            if (settingsCol != null && settingsCol.getMinimumHeight() != min) settingsCol.setMinimumHeight(min);
        });
        sv.getViewTreeObserver().addOnScrollChangedListener(() -> collapseSettingsTitle(sv.getScrollY()));
        screen.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        settingsHeader = buildSettingsHeader();
        screen.addView(settingsHeader, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        populateSettings();
        return screen;
    }

    private FrameLayout buildSettingsHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setBackgroundColor(Theme.BG);

        settingsBack = Ui.iconButton(this, R.drawable.ic_back, Theme.ON, 46, this::closeSettingsScreen);
        settingsBackLp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP | Gravity.START);
        settingsBackLp.leftMargin = dp(8);
        h.addView(settingsBack, settingsBackLp);

        settingsTitle = Ui.text(this, "Pengaturan", 34, Theme.ON, false);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tl.leftMargin = dp(16);
        h.addView(settingsTitle, tl);

        // Tinggi judul bisa berubah setelah layout pertama: hitung ulang posisinya.
        h.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> collapseSettingsTitle(sLastY));
        return h;
    }

    private void applySettingsInsets() {
        sExpandedH = insetTop + dp(176);
        sCollapsedH = insetTop + dp(56);
        sMaxScroll = sExpandedH - sCollapsedH;
        if (settingsBackLp != null) {
            settingsBackLp.topMargin = insetTop + (dp(56) - dp(46)) / 2;
            settingsBack.setLayoutParams(settingsBackLp);
        }
        if (settingsCol != null) settingsCol.setPadding(dp(16), sExpandedH, dp(16), insetBottom + dp(24));
        collapseSettingsTitle(sLastY);
    }

    /** Dipanggil setiap scroll. scrollY = jarak konten yang sudah di-scroll. */
    private void collapseSettingsTitle(int scrollY) {
        if (settingsHeader == null || settingsTitle == null || sMaxScroll <= 0) return;
        sLastY = scrollY;
        float p = java.lang.Math.max(0f, java.lang.Math.min(1f, scrollY / (float) sMaxScroll));

        int h = java.lang.Math.max(sCollapsedH, sExpandedH - scrollY);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) settingsHeader.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            settingsHeader.setLayoutParams(lp);
        }

        float th = settingsTitle.getHeight();
        if (th == 0) {
            settingsTitle.measure(0, 0);
            th = settingsTitle.getMeasuredHeight();
        }
        float s = 1f + (SETTINGS_END_SCALE - 1f) * p;
        float topExpanded = sExpandedH - dp(16) - th;                          // di bawah header
        float topCollapsed = insetTop + (dp(56) - th * SETTINGS_END_SCALE) / 2f; // sejajar tombol back

        settingsTitle.setPivotX(0f);
        settingsTitle.setPivotY(0f);
        settingsTitle.setScaleX(s);
        settingsTitle.setScaleY(s);
        settingsTitle.setTranslationX(dp(48) * p);                              // 16dp -> 64dp
        settingsTitle.setTranslationY(topExpanded + (topCollapsed - topExpanded) * p);
    }

    /** Isi pengaturan. Dibangun ulang setiap ada perubahan supaya semua baris ikut terbarui. */
    private void populateSettings() {
        if (settingsCol == null) return;
        settingsCol.removeAllViews();
        final Provider p = getProvider();
        final String name = prefs.getString("user_name", "Pengguna");
        final boolean offline = modeChanger.isOffline();

        addBlock(section("Profil"));
        addBlock(card(
        avatarRow(name),
        row(0, "Nama pengguna", name, v -> promptText("Nama pengguna", "Nama pengguna", name, false, s -> {
            String n = s.isEmpty() ? "Pengguna" : s;
            prefs.edit().putString("user_name", n).apply();
            modeChanger.setUserName(n);
            refreshUserFooter();
            populateSettings();
        }))));

        addBlock(section("Tampilan"));
        addBlock(card(switchRow("Tema gelap", "Gelap, aksen oranye Claude", "Terang, aksen oranye Claude",
        Theme.dark, on -> {
            Prefs.setDarkTheme(on);
            // tunggu animasi saklar selesai (±360 ms) baru bangun ulang tampilan
            handler.postDelayed(() -> applyThemeChange(), 380);
        })));

        addBlock(section("Mode"));
        addBlock(card(switchRow("Mode offline", "Aktif: jawaban dari model.json, tanpa internet",
        "Nonaktif: chat dikirim ke API provider", offline, on -> {
            modeChanger.setMode(on ? ModeChanger.Mode.OFFLINE : ModeChanger.Mode.ONLINE);
            updateSubtitle();
            // bagian Provider berubah; bangun ulang setelah animasi saklar selesai
            handler.postDelayed(() -> populateSettings(), 380);
        }),
        switchRow("Ganti model otomatis", "Aktif: pindah ke model lain saat token habis",
        "Nonaktif: langsung tampilkan pesan token habis", autoSwitchOn(), on -> {
            prefs.edit().putBoolean("auto_switch", on).apply();
        }),
        switchRow("Mode berpikir", "Aktif: AI berpikir dulu sebelum menjawab (lebih pintar, sedikit lebih lama)",
        "Nonaktif: jawaban langsung tanpa berpikir", thinkOn(), on -> {
            prefs.edit().putBoolean("think", on).apply();
        })));

        if (!offline) {
            addBlock(section("Provider AI"));
            addBlock(card(
            row(0, "Provider", p.name, v -> pickProvider()),
            row(0, "Base URL", getBaseUrl(p), v -> promptText("Base URL", "https://...", getBaseUrl(p), false, s -> {
                prefs.edit().putString("base_" + p.name, s.isEmpty() ? p.baseUrl : stripSlash(s)).apply();
                updateSubtitle();
                populateSettings();
            })),
            row(0, "API key", maskKey(getKey(p)), v -> promptText("API key", "API key", getKey(p), true, s -> {
                prefs.edit().putString("key_" + p.name, s).apply();
                populateSettings();
            })),
            row(0, "Model", getModel(p), v -> pickModel(p)),
            row(0, "Muat daftar model", p.keyOptional ? "Ambil dari server (API key opsional)"
            : "Ambil dari API key yang diisi", v -> loadModelsFromApi(p))));
        }

        addBlock(section("Tentang"));
        addBlock(card(row(ICON_CREDITS, "Credits", "Mona AI · Dibuat oleh IqwanoIno", v -> showCreditsDialog())));
    }

    /**
     * Tema diganti tanpa memulai ulang activity, jadi bisa dilakukan di tengah percakapan
     * (juga saat balasan AI masih berjalan). Isi chat dibangun ulang dari data sesi.
     */
    private void applyThemeChange() {
        final boolean settingsOpen = settingsScreen != null;
        Theme.load(this);
        appliedTop = -1;
        appliedBottom = -1;
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.BG));
        drawerLayout.setViews(buildContent(), buildDrawer());
        applyBarAppearance();
        renderCurrent();
        scrollToBottom();   // tampilan baru dimulai dari pesan terbaru, seperti saat dibuka
        updateSubtitle();
        refreshHistory();
        refreshUserFooter();
        refreshModelPill();
        updateSendButton();
        if (settingsOpen) {
            rootFrame.removeView(settingsScreen);
            settingsScreen = null;
            settingsScroll = null;
            settingsCol = null;
            settingsHeader = null;
            settingsTitle = null;
            settingsBack = null;
            settingsBackLp = null;
            showSettingsScreen();
            settingsScreen.animate().cancel();   // tanpa geser ulang: pengaturan langsung tampil dengan tema baru
            settingsScreen.setTranslationX(0f);
        }
        rootFrame.requestApplyInsets();
    }

    private void pickProvider() {
        final Provider cur = getProvider();
        List<String> names = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < PROVIDERS.length; i++) {
            names.add(PROVIDERS[i].name);
            if (PROVIDERS[i] == cur) sel = i;
        }
        pickSheet("Provider AI", names, sel, i -> {
            prefs.edit().putString("provider", PROVIDERS[i].name).apply();
            updateSubtitle();
            populateSettings();
        });
    }

    private void pickModel(final Provider p) {
        final List<String> models = new ArrayList<>(getModelList(p));
        final String cur = getModel(p);
        int sel = models.indexOf(cur);
        models.add("Ketik nama model…");
        pickSheet("Model", models, sel, i -> {
            if (i == models.size() - 1) {
                promptText("Nama model", "contoh: " + p.models[0], cur, false, s -> saveModel(p, s));
            } else {
                saveModel(p, models.get(i));
            }
        });
    }

    private void saveModel(Provider p, String s) {
        prefs.edit().putString("model_" + p.name, s.isEmpty() ? p.models[0] : s).apply();
        updateSubtitle();
        populateSettings();
    }

    /** GET daftar model dari API key yang sudah tersimpan, lalu simpan ke daftar provider. */
    private void loadModelsFromApi(final Provider p) {
        final String base = getBaseUrl(p);
        final String key = getKey(p);
        if (base.isEmpty()) { M3.toast(this, "Base URL kosong"); return; }
        if (key.isEmpty() && !p.keyOptional) { M3.toast(this, "Isi API key dulu"); return; }
        M3.toast(this, "Memuat daftar model…");
        new Thread(() -> {
            try {
                final List<String> list = fetchModels(p, base, key);
                handler.post(() -> {
                    if (isFinishing()) return;
                    if (list.isEmpty()) { M3.toast(this, "Tidak ada model untuk API key ini"); return; }
                    saveModelList(p, list);
                    M3.toast(this, list.size() + " model dimuat");
                    populateSettings();
                });
            } catch (final Exception e) {
                handler.post(() -> M3.toast(this, "⚠️ " + e.getMessage()));
            }
        }).start();
    }

    private String maskKey(String k) {
        if (k == null || k.isEmpty()) return "Belum diisi";
        if (k.length() <= 4) return "••••";
        return "••••" + k.substring(k.length() - 4);
    }

    // ----- Bagian dan kartu -----
    private void addBlock(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        settingsCol.addView(v, lp);
    }

    private TextView section(String label) {
        TextView t = Ui.text(this, label, 14, Theme.PRIMARY, true);
        t.setPadding(dp(8), dp(20), dp(8), dp(4));
        return t;
    }

    /** Kartu berisi baris-baris, dipisahkan garis tipis. */
    private LinearLayout card(View... rows) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rect(Theme.SURFACE_HIGH, 20));
        c.setClipToOutline(true);
        for (int i = 0; i < rows.length; i++) {
            c.addView(rows[i], new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i < rows.length - 1) c.addView(divider(), dividerParams());
        }
        return c;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(M3.alpha(Theme.OUTLINE, 0.4f));
        return v;
    }

    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(16), 0, dp(16), 0);
        return lp;
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(Ui.rowRipple(0));
        row.setClickable(true);
        return row;
    }

    private LinearLayout textsCol(String label, String sub) {
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, label, 16, Theme.ON, false));
        texts.addView(Ui.text(this, sub, 13, Theme.ON_VAR, false));
        return texts;
    }

    /** Baris biasa: ikon opsional, judul, keterangan. icon = 0 berarti tanpa ikon. */
    private View row(int icon, String label, String sub, View.OnClickListener click) {
        LinearLayout row = baseRow();
        if (icon != 0) row.addView(Ui.icon(this, icon, Theme.ON_VAR), new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        if (icon != 0) tl.leftMargin = dp(20);
        row.addView(textsCol(label, sub), tl);
        row.setOnClickListener(click);
        return row;
    }

    /** Baris foto profil: avatar bulat + nama. Ketuk untuk mengganti foto. */
    private View avatarRow(String name) {
        LinearLayout row = baseRow();
        TextView av = makeAvatar("U", 22, Theme.ON_VAR);
        paintAvatar(av, loadSavedPhoto(), name, 56, 22);
        row.addView(av, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(16);
        row.addView(textsCol(name, "Ketuk untuk mengganti foto profil"), tl);
        row.setOnClickListener(v -> pickPhoto());
        return row;
    }

    /** Baris dengan MaterialSwitch. Saklar hanya tampilan; barisnya yang menangani ketukan. */
    private LinearLayout switchRow(String label, final String onText, final String offText, boolean on,
                                   final Toggle toggle) {
        final LinearLayout row = baseRow();
        final TextView sub = Ui.text(this, on ? onText : offText, 13, Theme.ON_VAR, false);
        final MaterialSwitch sw = new MaterialSwitch(this);
        sw.setEnabled(false);
        sw.setFocusable(false);
        sw.setActiveColor(Theme.PRIMARY);
        sw.setInactiveColor(Color.TRANSPARENT);
        sw.setStrokeActiveColor(Theme.PRIMARY);
        sw.setStrokeInactiveColor(Theme.OUTLINE);
        sw.setThumbActiveColor(Theme.ON_PRIMARY);
        sw.setThumbInactiveColor(Theme.OUTLINE);
        sw.setCheckedImmediate(on);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, label, 16, Theme.ON, false));
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));

        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next);
            sub.setText(next ? onText : offText);
            toggle.onToggle(next);
        });
        return row;
    }

    /** Dialog isi teks satu baris (nama, URL, API key, nama model, nama chat). */
    private void promptText(String title, String hint, String value, final boolean secret, final TextSink sink) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(16));
        box.addView(dialogTitle(title));

        final EditText et = new EditText(this);
        Ui.tintCursor(et);
        styleField(et, hint);
        et.setText(value);
        et.setSelection(et.getText().length());
        if (secret) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(12);
        box.addView(et, ep);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        TextView cancel = button("Batal", false);
        TextView save = button("Simpan", true);
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-2, -2);
        margin.setMarginStart(dp(8));
        buttons.addView(cancel);
        buttons.addView(save, margin);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = dp(16);
        box.addView(buttons, bl);

        final Dialog dialog = createDialog(box);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            sink.set(et.getText().toString().trim());
            dialog.dismiss();
        });
        showDialog(dialog);
    }

    /** Bottom sheet berisi pilihan. Baris terpilih diberi tanda centang. */
    private void pickSheet(String title, List<String> items, int selected, final OnPick cb) {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;

        TextView t = Ui.text(this, title, 20, Theme.ON, true);
        t.setPadding(dp(4), 0, dp(4), dp(12));
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean on = i == selected;
            LinearLayout row = baseRow();
            row.addView(Ui.text(this, items.get(i), 16, on ? Theme.PRIMARY : Theme.ON, on),
            new LinearLayout.LayoutParams(0, -2, 1f));
            if (on) row.addView(Ui.text(this, "✓", 16, Theme.PRIMARY, true));
            row.setOnClickListener(v -> {
                sheet.dismiss();
                cb.pick(idx);
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1,
        java.lang.Math.min(dp(420), dp(52) * items.size())));
        sheet.show();
    }

    // ----- Foto profil -----
    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Pilih foto profil"), REQ_PICK_PHOTO);
        } catch (Exception e) {
            M3.toast(this, "Tidak ada aplikasi galeri");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ATTACH) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) addPendingAttachment(data.getData());
            return;
        }
        if (requestCode != REQ_PICK_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Bitmap b = decodePicked(data.getData());
            savePhotoFile(cropSquare(b, 512, false));
            refreshUserFooter();
            populateSettings();
            M3.toast(this, "Foto profil diperbarui");
        } catch (Exception e) {
            M3.toast(this, "Foto gagal disimpan: " + e.getMessage());
        }
    }

    /** Decode gambar dari galeri, diperkecil agar hemat memori. */
    private Bitmap decodePicked(Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("berkas tidak bisa dibuka");
        try { BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("format gambar tidak didukung");
        int s = 1;
        while (java.lang.Math.max(o.outWidth, o.outHeight) / s > 1024) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("berkas tidak bisa dibuka");
        try {
            Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            if (b == null) throw new IOException("gambar rusak");
            return b;
        } finally { in.close(); }
    }

    /** Potong tengah jadi persegi lalu skala ke px. circle = true membuat lingkaran (transparan di luar). */
    private static Bitmap cropSquare(Bitmap src, int px, boolean circle) {
        int side = java.lang.Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2, y = (src.getHeight() - side) / 2;
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (circle) {
            c.drawCircle(px / 2f, px / 2f, px / 2f, p);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        }
        c.drawBitmap(src, new Rect(x, y, x + side, y + side), new Rect(0, 0, px, px), p);
        return out;
    }

    /** Folder foto profil: internal app (selalu bisa ditulis, tanpa izin storage). */
    private File profileDir() {
        File dir = new File(getFilesDir(), "Account");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** Tulis foto ke filesDir/Account/profil.png (PNG persegi). */
    private void savePhotoFile(Bitmap square) throws IOException {
        File dir = profileDir();
        if (!dir.isDirectory()) throw new IOException("folder tidak bisa dibuat");
        File tmp = new File(dir, PROFILE_NAME + ".tmp");
        File dst = new File(dir, PROFILE_NAME);
        FileOutputStream fos = new FileOutputStream(tmp);
        try {
            if (!square.compress(Bitmap.CompressFormat.PNG, 100, fos)) throw new IOException("gagal menulis PNG");
            fos.getFD().sync();
        } finally {
            fos.close();
        }
        if (dst.exists() && !dst.delete()) throw new IOException("gagal menimpa foto lama");
        if (!tmp.renameTo(dst)) throw new IOException("gagal memindah foto");
    }

    /** Foto profil tersimpan, atau null jika belum ada / tidak bisa dibaca. */
    private Bitmap loadSavedPhoto() {
        File f = new File(profileDir(), PROFILE_NAME);
        if (!f.isFile()) return null;
        try { return BitmapFactory.decodeFile(f.getAbsolutePath()); }
        catch (Exception e) { return null; }
    }

    /** Isi avatar: foto (lingkaran) jika ada, kalau tidak huruf awal nama. */
    private void paintAvatar(TextView tv, Bitmap photo, String name, int sizeDp, int textSize) {
        tv.setTextSize(textSize);
        if (photo != null) {
            tv.setText("");
            tv.setBackground(new BitmapDrawable(getResources(), cropSquare(photo, dp(sizeDp), true)));
        } else {
            tv.setText(name == null || name.isEmpty() ? "U" : name.substring(0, 1).toUpperCase(Locale.ROOT));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Theme.ON_VAR);
            tv.setBackground(circle);
        }
    }

    private void showRenameChat(final ChatSession session) {
        promptText("Ganti nama obrolan", "Nama obrolan", session.title, false, value -> {
            if (!value.isEmpty()) session.title = value;
            saveSessions();
            refreshHistory();
        });
    }

    private void showCreditsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(16));
        box.addView(dialogTitle("Credits"));

        TextView body = Ui.text(this, CREDITS_TEXT, 14, Theme.ON_VAR, false);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(0, dp(12), 0, dp(16));
        box.addView(body);

        TextView close = button("Tutup", true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.END;
        box.addView(close, cp);

        final Dialog dialog = createDialog(box);
        close.setOnClickListener(v -> dialog.dismiss());
        showDialog(dialog);
    }

    private TextView dialogTitle(String text) {
        return Ui.text(this, text, 20, Theme.ON, true);
    }

    private Dialog createDialog(View content) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(roundedBg(Theme.BG, 20, Theme.LINE, 1));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    private void showDialog(Dialog d) {
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            int sw = getResources().getDisplayMetrics().widthPixels;
            w.setLayout(java.lang.Math.min(sw - dp(32), dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // =====================================================================
    //  CHAT
    // =====================================================================
    /** "mona arti makan" -> "arti makan"; */
    private String stripMona(String s) {
        if (s.length() > 5 && s.toLowerCase(Locale.ROOT).startsWith("mona ")) return s.substring(5).trim();
        return s;
    }

    private void handleSend() {
        if (busy) { stopCurrentJob(); return; }
        final String text = stripMona(inputField.getText().toString().trim());
        if (TextUtils.isEmpty(text) && pending.isEmpty()) return;

        final boolean offline = modeChanger.isOffline();
        final Provider p = getProvider();
        final String key = getKey(p);
        if (!offline && key.isEmpty() && !p.keyOptional) {
            M3.toast(this, "API key belum diatur");
            showSettingsScreen();
            return;
        }

        boolean fresh = current == null;
        if (fresh) {
            current = new ChatSession();
            current.title = shorten(OutputHelper.toPlain(text), 40);
            sessions.add(0, current);
            chatContainer.removeAllViews();
        }
        final ChatSession target = current;
        // Lampiran: isi berkas teks masuk ke pesan API, gambar dikirim pada giliran ini saja.
        final List<PendingAtt> atts = new ArrayList<>(pending);
        pending.clear();
        refreshPendingRow();
        // Teks pesan terpisah dari lampiran (kartu di atas bubble, tidak digabung ke teks)
        final String shown = text.isEmpty() && !atts.isEmpty() ? "" : text;
        StringBuilder extraSb = new StringBuilder();
        curImages = new ArrayList<>();
        final List<String> filePaths = new ArrayList<>();
        final List<File> zips = new ArrayList<>();
        for (PendingAtt a : atts) {
            filePaths.add(a.file.getAbsolutePath());
            if (a.image) curImages.add(a.file);
            else if (ZipUtil.isZip(a.file.getName())) zips.add(a.file);   // diekstrak di thread latar
            else if (isTextLike(a.file.getName())) extraSb.append(readTextForApi(a.file, a.file.getName()));
        }
        ChatMessage userMsg = new ChatMessage(shown, true, new ArrayList<String>(), null, filePaths);
        userMsg.extra = extraSb.toString();
        target.messages.add(userMsg);
        target.updated = System.currentTimeMillis();
        saveSessions();
        refreshHistory();

        addUserMessage(userMsg);
        inputField.setText("");
        // Jangan paksa fokus lagi setelah kirim — biarkan user yang putuskan keyboard
        // keepInputFocus();

        // Proses berpikir: langkah yang benar-benar dijalankan aplikasi (bukan penalaran model)
        // synchronized: sheet "Berpikir" membaca langkah ini dari UI thread sambil thread kerja menambah langkah
        final List<String> steps = Collections.synchronizedList(new ArrayList<String>());
        liveSteps = steps;   // bisa dibuka lewat ketuk indikator "Berpikir" saat proses berjalan
        jobRunning = true;
        jobStartedAt = System.currentTimeMillis();
        steps.add(pick("Membaca pesan kamu", "Memahami pesan yang kamu tulis", "Menerima permintaanmu"));
        final String base = getBaseUrl(p), model = getModel(p);
        steps.add(offline ? "Mencari jawaban di model.json" : "Mengirim ke " + p.name + " · " + model);

        busy = true;
        pendingSession = target;
        updateSendButton();
        final long token = ++jobSeq;
        MonaService.begin(getApplicationContext(), shorten(text, 60));
        typingRow = addTypingMessage();
        typingLive.setStep(steps.get(steps.size() - 1));

        final boolean showCode = wantsCodeView(text);
        final MainActivity self = this;
        final List<ChatMessage> snapshot = new ArrayList<>(target.messages);
        new Thread(() -> {
            String reply;
            CommandIntent.Result cmd = null;
            String attach = null;
            String missingAttach = null;   // path yang diminta AI tetapi tidak ada di sandbox
            boolean err = false;
            // Zip lampiran: ekstrak lalu rangkum isinya untuk AI (sebelumnya AI tidak tahu isi zip)
            if (!zips.isEmpty()) {
                StringBuilder zb = new StringBuilder();
                for (File z : zips) zb.append(ZipUtil.summarize(z, sandboxDir()));
                userMsg.extra = userMsg.extra + zb;
            }
            try {
                if (wantsSandboxDelete(text)) {
                    // "kirim lalu hapus": salin dulu ke Download supaya berkas tetap bisa dikirim
                    String copy = wantsFileSend(text) ? exportNewestToDownload() : null;
                    // hapus berkas sandbox langsung di perangkat: tidak perlu AI, berlaku online dan offline
                    reply = deleteFromText(text);
                    if (copy != null) {
                        attach = copy;
                        reply = reply + "\n\nBerkas sudah saya salin ke Download lalu dilampirkan.";
                    } else if (wantsFileSend(text)) {
                        reply = reply + "\n\nTidak ada berkas yang bisa dikirim.";
                    }
                } else if (offline) {
                    handler.post(() -> { if (typingLive != null) typingLive.setStep("Menyusun jawaban"); });
                    Thread.sleep(420 + new java.util.Random().nextInt(480));   // jeda alami, bukan instan
                    reply = modeChanger.reply(text);
                } else {
                    reply = sendWithFallback(p, base, key, model, snapshot, steps);
                    if (!lastThought.isEmpty()) steps.add("think|Pikiran|" + clipThought(lastThought));
                }
                if (!offline) {
                    attach = aiAttach(reply);
                    if (attach != null) {
                        String wanted = attach;
                        attach = resolveAttachPath(wanted);   // cocokkan ke berkas nyata
                        if (attach == null) missingAttach = wanted;
                    }
                    steps.addAll(planStepsFrom(reply));
                    String[] write = aiWrite(reply);
                    String delArg = aiDeleteArg(reply);
                    cmd = aiCommand(reply);
                    reply = stripBlocks(reply);
                    if (write != null && !wantsFileCreate(text)) {
                        // minta kode tanpa kata file: tampilkan di kotak kode, jangan buat berkas
                        reply = (reply.isEmpty() ? "" : reply + "\n\n") + "```" + langOf(write[0]) + "\n" + write[1] + "\n```";
                        write = null;
                    }
                    if (missingAttach != null) {
                        String note = "Berkas \"" + new File(missingAttach).getName() + "\" tidak ada. " + sandboxNames();
                        reply = reply.isEmpty() ? note : reply + "\n\n" + note;
                    }
                    if (delArg != null) {
                        reply = sandboxDelete(delArg);
                        steps.add("Menghapus berkas di sandbox");
                        cmd = null;
                    }
                    String[] firstOut = new String[1];
                    // blok kode hanya disimpan jadi berkas jika pengguna memang minta file
                    if (wantsFileCreate(text)) reply = saveCodeBlocks(reply, steps, showCode, firstOut);
                    // Berkas tetap di sandbox; hanya dilampirkan jika pengguna memintanya.
                    if (attach == null && missingAttach == null && wantsFileSend(text)) attach = newestSandboxFile();
                    if (write != null) {
                        String out = writeFile(write[0], write[1]);
                        if (out.startsWith("⚠️")) {
                            steps.add(out);
                            // gagal tulis: laporkan apa adanya, jangan bilang berkas sudah dibuat
                            reply = reply.isEmpty() ? out : reply + "\n\n" + out;
                        } else {
                            String wname = new File(out).getName();
                            // Langkah detail ala Claude supaya proses pembuatan file terlihat panjang
                            steps.add(pick("Merencanakan isi berkas " + wname,
                                    "Menyusun rencana untuk " + wname, "Memikirkan isi dari " + wname));
                            steps.add(pick("Menyiapkan format dan struktur konten",
                                    "Menentukan susunan dan gaya tulisan", "Merapikan kerangka konten"));
                            steps.add(pick("Menulis konten secara lengkap",
                                    "Menuliskan isi dari awal sampai akhir", "Mengisi seluruh bagian berkas"));
                            steps.add(pick("Memeriksa konsistensi dan kelengkapan",
                                    "Mengecek ulang isi dan kelengkapannya", "Meninjau kembali hasil tulisan"));
                            steps.add(pick("Menyimpan berkas ke sandbox",
                                    "Menyimpan hasil ke penyimpanan Mona", "Menulis berkas ke sandbox Mona"));
                            steps.add("file|Membuat " + wname + "|" + out);
                            if (attach == null) attach = out;   // kartu file tampil di chat
                        }
                        if (reply.isEmpty()) reply = "Berkas " + new File(out).getName() + " sudah saya buat. Ini filenya.";
                    }
                    if (cmd != null) {
                        steps.add("shell|" + describeCommand(cmd) + "|");
                        if (reply.isEmpty()) reply = "Baik, saya jalankan.";
                    }
                    if (reply.isEmpty() && attach != null) reply = "Ini filenya.";
                }
                steps.add("Menerima balasan · " + reply.length() + " karakter");
            } catch (Exception e) {
                reply = "⚠️ " + e.getMessage();
                steps.add("Gagal: " + reply);
                err = true;
            }
            final String r = reply;
            final boolean fe = err;
            final CommandIntent.Result fc = cmd;
            final String fa = attach;
            handler.post(() -> {
                if (cancelledJob == token) {         // dihentikan pengguna: balasan dibuang
                    jobRunning = false;
                    return;
                }
                self.finishJob(target, r, fe, steps, fc, fa);
            });
        }).start();
    }

    /**
     * Dipanggil di main thread saat permintaan selesai. Berjalan walaupun activity pengirim
     * sudah ditutup: hasil tetap disimpan, dan jika tidak ada activity yang tampil, notifikasi dikirim.
     */
    private void finishJob(ChatSession target, String reply, boolean isError, List<String> steps,
                           CommandIntent.Result cmd, String attach) {
        pendingSession = null;
        jobRunning = false;
        MonaService.end(getApplicationContext());

        ChatMessage msg = new ChatMessage(reply, false, steps, attach);
        // Balasan tidak valid atau galat tetap disimpan di riwayat, tidak dibuang.
        target.messages.add(msg);
        target.updated = System.currentTimeMillis();
        saveSessions();

        // Activity yang tampil (jika ada) menangani UI dan perintah file yang butuh konfirmasi.
        MainActivity host = live != null ? live : this;
        if (live == null) {
            busy = false;
            dirty = true;
            MonaNotify.replyReady(getApplicationContext(), target.title, shorten(reply, 90));
            if (cmd != null && !isError) host.runChatCommand(target, cmd);
            return;
        }
        host.removeTypingRow();
        // Biarkan busy=true selama animasi streaming teks (mirip Claude loading)
        if (host.current == target) {
            // busy tetap true sampai stream selesai (di dalam addAiMessage)
            host.updateSendButton(); // tetap tampil stop selama stream
            host.addAiMessage(msg, true);
        } else {
            busy = false;
            host.updateSendButton();
        }
        if (live != null) live.refreshModelPill();
        host.refreshHistory();
        if (cmd != null && !isError) host.runChatCommand(target, cmd);
        if (pendingRecreate) { pendingRecreate = false; host.recreate(); return; }
        // Jangan paksa fokus/keyboard setelah selesai — biarkan user yang tutup keyboard
    }

    // =====================================================================
    //  PERINTAH FILE LEWAT CHAT
    //  AI menulis blok ```mona-cmd {...}``` (cd, ls, cat, mkdir, touch, pwd, storage, write).
    //  AI menulis blok ```mona-attach {...}``` untuk melampirkan berkas yang sudah ada.
    //  Path dibatasi ke penyimpanan internal + folder aplikasi, dan tidak ada perintah hapus.
    // =====================================================================
    private static final Pattern MONA_CMD = Pattern.compile("```mona-cmd\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
    private static final Pattern MONA_ATTACH = Pattern.compile("```mona-attach\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);

    private String systemPrompt() {
        return "Kamu adalah Mona, asisten AI di aplikasi Android.\n"
        + "Folder aktif sekarang: " + shell.getCurrentDir() + "\n"
        + "Penyimpanan internal: /storage/emulated/0 (Download, DCIM, Documents, Music, Pictures, Movies).\n"
        + sandboxSummary()
        + "Jika pengguna meminta pindah/masuk/buka folder, melihat isi folder, membaca file, atau membuat folder/file kosong, "
        + "balas HANYA dengan satu blok seperti ini, tanpa teks lain:\n"
        + "```mona-cmd\n{\"action\":\"cd\",\"path\":\"/storage/emulated/0/Download\"}\n```\n"
        + "action yang boleh: cd, ls, cat, mkdir, touch, pwd, storage (tanpa path; untuk meminta izin akses penyimpanan). "
        + "Untuk naik satu folder pakai path \"..\".\n"
        + "Hanya jika pengguna jelas meminta membuat FILE (menyebut kata file, berkas, simpan, atau nama berkas), balas dengan blok write. "
        + "Isi blok write: "
        + "action write dan field content berisi SELURUH isi file (bukan kerangka atau judul saja). Newline di dalam content ditulis sebagai \\n:\n"
        + "```mona-cmd\n{\"action\":\"write\",\"path\":\"china.txt\",\"content\":\"Judul\\n\\nIsi paragraf...\"}\n```\n"
        + "Jika pengguna meminta mengirim atau melampirkan file yang sudah ada, balas HANYA:\n"
        + "```mona-attach\n{\"path\":\"/storage/emulated/0/Download/china.txt\"}\n```\n"
        + "Berkas yang dibuat disimpan di sandbox Mona: isi path dengan nama file saja (misalnya MainActivity.java), bukan path penuh.\n"
        + "Permintaan kode atau teks TANPA kata file (misalnya \"buatkan kode java toast\"): tampilkan langsung di chat dalam blok kode markdown "
        + "(```java ...```) dan JANGAN membuat berkas. "
        + "Jika membuat berkas, di luar blok write tulis penjelasan singkat dan ramah: isi berkasnya, fitur-fitur utamanya, dan cara memakainya, "
        + "seperti asisten yang menjelaskan hasil kerjanya.\n"
        + "Jika pengguna meminta menghapus berkas di sandbox Mona, balas HANYA:\n"
        + "```mona-cmd\n{\"action\":\"delete\",\"path\":\"NAMA_BERKAS\"}\n```\n"
        + "Pakai path \"*\" untuk menghapus semua berkas sandbox. Jangan membuat berkas baru untuk permintaan hapus. "
        + "Jangan mengarang nama berkas: pakai hanya nama dari daftar sandbox. Jika pengguna minta kirim berkas tanpa menyebut nama, "
        + "pakai berkas terbaru di daftar. Jika berkas yang diminta tidak ada di daftar, katakan terus terang dan jangan mengklaim berkas itu ada.\n"
        + "Riwayat memuat hasil perintah sebelumnya sebagai [Hasil perintah: ...]. Jika pengguna meminta menampilkan, menjelaskan, "
        + "atau memakai isi berkas yang sudah dibaca (misalnya \"tampilkan ke chat\" atau \"tampilkan ke codebox\"), "
        + "pakai isi dari hasil itu dan tampilkan dalam blok kode markdown. Jangan jalankan ulang perintah jika hasilnya sudah ada di riwayat. "
        + "Kata \"tampilkan\" tanpa kata file berarti tampilkan di chat, bukan membuat berkas.\n"
        + "Untuk pertanyaan biasa, jawab seperti biasa.";
    }

    /** Online: baca blok mona-cmd dari balasan AI; null jika tidak ada atau action tidak dikenal. */
    private CommandIntent.Result aiCommand(String reply) {
        Matcher m = MONA_CMD.matcher(reply);
        if (!m.find()) return null;
        try {
            JSONObject o = new JSONObject(m.group(1));
            String path = o.optString("path", "");
            CommandIntent.Action act;
            switch (o.optString("action", "").toLowerCase(Locale.ROOT)) {
                case "cd":    act = CommandIntent.Action.CD; break;
                case "open":  act = CommandIntent.Action.OPEN; break;
                case "ls":    act = CommandIntent.Action.LS; break;
                case "cat":   act = CommandIntent.Action.CAT; break;
                case "mkdir": act = CommandIntent.Action.MKDIR; break;
                case "touch": act = CommandIntent.Action.TOUCH; break;
                case "pwd":   act = CommandIntent.Action.PWD; break;
                case "storage": act = CommandIntent.Action.STORAGE; break;
                default:      return null;   // termasuk "write", ditangani aiWrite()
            }
            return new CommandIntent.Result(act, path.isEmpty() ? null : path, null, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** Blok mona-cmd dengan action write: {path, content}, atau null. */
    private String[] aiWrite(String reply) {
        Matcher m = MONA_CMD.matcher(reply);
        if (!m.find()) return null;
        try {
            JSONObject o = new JSONObject(m.group(1));
            if (!"write".equalsIgnoreCase(o.optString("action", ""))) return null;
            String path = o.optString("path", "");
            if (path.isEmpty() || !o.has("content")) return null;
            return new String[]{ path, o.optString("content", "") };
        } catch (Exception e) {
            return null;
        }
    }

    /** Blok mona-attach: path berkas yang dilampirkan, atau null. */
    private String aiAttach(String reply) {
        Matcher m = MONA_ATTACH.matcher(reply);
        if (!m.find()) return null;
        try {
            String path = new JSONObject(m.group(1)).optString("path", "");
            return path.isEmpty() ? null : path;
        } catch (Exception e) {
            return null;
        }
    }

    /** Buang blok perintah yang valid dari teks. Blok yang tidak valid TIDAK dibuang: tetap tampil apa adanya. */
    private String stripBlocks(String reply) {
        String s = stripValid(reply, MONA_CMD, "action");
        s = stripValid(s, MONA_ATTACH, "path");
        // Blok mona-* yang terpotong (tanpa penutup ```) juga disembunyikan dari chat
        s = s.replaceAll("(?s)```\\s*mona-(cmd|attach)\\b.*$", "");
        return s.trim();
    }

    private String stripValid(String s, Pattern p, String requiredKey) {
        Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            boolean ok = false;
            try {
                ok = !new JSONObject(m.group(1)).optString(requiredKey, "").isEmpty();
            } catch (Exception e) {
                ok = false;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(ok ? "" : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Blok kode markdown ```lang ... ```. */
    private static final Pattern CODE_FENCE = Pattern.compile("```([\\w+#.-]*)[ \\t]*\\n(.*?)```", Pattern.DOTALL);

    /** Kode ditampilkan di chat hanya jika pengguna menyebut kode DAN memintanya ditampilkan. */
    /** Kata untuk efek mengetik saat menunggu balasan. */
    private static final String[] THINK_WORDS = {
        "membaca", "menimbang", "merangkai", "menyusun", "memeriksa", "mencari", "merapikan",
        "mengingat", "meninjau", "menelusuri", "konteks", "gagasan", "jawaban", "detail",
        "kalimat", "sebentar", "menghubungkan", "memilah", "mengolah", "menata"
    };

    /** Penyorot sintaks ringan: 1 = komentar, 2 = string, 3 = angka, 4 = kata. */
    private static final Pattern HL = Pattern.compile(
        "(/\\*[\\s\\S]*?\\*/|//[^\\n]*)|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')|(\\b\\d+(?:\\.\\d+)?[fFlL]?\\b)|(\\b[A-Za-z_]\\w*\\b)");
    private static final String KW_LIST = " abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for fun function goto if implements import in instanceof int interface let long native new null package private protected public return short static strictfp super switch synchronized this throw throws transient try val var void volatile while true false async await def elif from with yield lambda None True False self export ";
    private static final int CODE_BG = 0xFF1C1C1C, CODE_DIM = 0xFFA3A3A3, CODE_TEXT = 0xFFE6E6E6;
    private static final int C_KW = 0xFFC792EA, C_STR = 0xFFECC48D, C_NUM = 0xFF89DDFF, C_TYPE = 0xFFFFCB6B, C_COM = 0xFF6B7A8F;

    private static final int REQ_PICK_ATTACH = 7102;
    /** Lampiran yang menunggu dikirim (belum masuk chat). */
    private static final List<PendingAtt> pending = new ArrayList<>();
    private static List<File> curImages = new ArrayList<>();   // gambar untuk giliran ini saja
    private static long jobSeq;                                // nomor permintaan terakhir
    private static volatile long cancelledJob = -1;            // permintaan yang dihentikan pengguna

    private static class PendingAtt {
        final File file;
        final boolean image;
        PendingAtt(File f, boolean i) { file = f; image = i; }
    }

    /** Warna sintaks sesuai tema: 0 kata kunci, 1 string, 2 angka, 3 tipe, 4 komentar. */
    private static int hl(int k) {
        if (Theme.dark) {
            switch (k) {
                case 0: return C_KW;
                case 1: return C_STR;
                case 2: return C_NUM;
                case 3: return C_TYPE;
                default: return C_COM;
            }
        }
        switch (k) {
            case 0: return 0xFFA0412D;
            case 1: return 0xFF2D6A4F;
            case 2: return 0xFF1D4ED8;
            case 3: return 0xFF8A5A00;
            default: return 0xFF8F8D86;
        }
    }

    private static CharSequence highlight(String code) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(code);
        Matcher m = HL.matcher(code);
        while (m.find()) {
            int color;
            if (m.group(1) != null) color = hl(4);
            else if (m.group(2) != null) color = hl(1);
            else if (m.group(3) != null) color = hl(2);
            else {
                String w = m.group(4);
                if (KW_LIST.contains(" " + w + " ")) color = hl(0);
                else if (Character.isUpperCase(w.charAt(0))) color = hl(3);
                else continue;
            }
            sb.setSpan(new android.text.style.ForegroundColorSpan(color), m.start(), m.end(),
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private void copyToClipboard(String s) {
        android.content.ClipboardManager cm =
        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Mona", s));
        M3.toast(this, "Disalin");
    }

    /** Simpan kode ke sandbox lalu salin ke folder Download (hanya saat tombol unduh ditekan). */
    private void downloadCode(String lang, String body) {
        String out = writeSandbox(guessName(lang, body, 1), body);
        if (out.startsWith("⚠️")) { M3.toast(this, out); return; }
        M3.toast(this, "Mengunduh " + new File(out).getName());
        MonaDownloader.start(getApplicationContext(), new File(out), null);
    }

    private TextView codeText(String body) {
        TextView t = new TextView(this);
        t.setText(highlight(body));
        t.setTextColor(Theme.ON);
        t.setTextSize(13f);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(dp(14), dp(10), dp(14), dp(14));
        return t;
    }

    /** Header CodeBox: label bahasa, salin, layar penuh (atau tutup), unduh. */
    private LinearLayout codeHeader(final String lang, final String body, final Runnable onFull, int fullIcon) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(2), dp(4), dp(2));
        TextView label = Ui.text(this, lang.isEmpty() ? "teks" : lang, 12, Theme.ON_VAR, true);
        head.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(Ui.iconButton(this, R.drawable.ic_copy, Theme.ON_VAR, 34, () -> copyToClipboard(body)),
        new LinearLayout.LayoutParams(dp(38), dp(38)));
        head.addView(Ui.iconButton(this, fullIcon, Theme.ON_VAR, 34, onFull),
        new LinearLayout.LayoutParams(dp(38), dp(38)));
        head.addView(Ui.iconButton(this, R.drawable.ic_download, Theme.ON_VAR, 34, () -> downloadCode(lang, body)),
        new LinearLayout.LayoutParams(dp(38), dp(38)));
        return head;
    }

    /** Pratinjau layar penuh ala Claude: judul, keterangan, salin, unduh. Mengikuti tema terang/gelap. */
    private void openViewer(final String title, final String sub, final String body, final Runnable onDownload) {
        openViewer(title, sub, body, onDownload, null);
    }

    /** Pratinjau: teks/kode, atau foto (image != null) dengan zoom. Jendela edge-to-edge. */
    private void openViewer(final String title, final String sub, final String body, final Runnable onDownload,
                            final File image) {
        final Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        final android.view.Window dw = d.getWindow();
        if (dw != null) {
            dw.setStatusBarColor(Color.TRANSPARENT);
            dw.setNavigationBarColor(Color.TRANSPARENT);
            dw.setBackgroundDrawable(new ColorDrawable(Theme.BG));
            if (Build.VERSION.SDK_INT >= 30) {
                dw.setDecorFitsSystemWindows(false);
            } else {
                dw.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
            M3.styleBars(dw);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        // padding mengikuti status bar dan navigation bar yang sebenarnya
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets b = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = b.top;
                bottom = b.bottom;
            } else {
                top = ins.getSystemWindowInsetTop();
                bottom = ins.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return ins;
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), dp(6), dp(6), dp(6));
        head.addView(Ui.iconButton(this, R.drawable.ic_back, Theme.ON_VAR, 40, () -> d.dismiss()),
        new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView tt = Ui.text(this, title, 16, Theme.ON, false);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        TextView ts = Ui.text(this, sub, 12, Theme.ON_VAR, false);
        ts.setSingleLine(true);
        ts.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(tt);
        titles.addView(ts);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = dp(6);
        head.addView(titles, tlp);

        head.addView(Ui.iconButton(this, R.drawable.ic_copy, Theme.ON_VAR, 40, () -> {
            if (body != null) copyToClipboard(body);
        }), new LinearLayout.LayoutParams(dp(40), dp(40)));
        head.addView(Ui.iconButton(this, R.drawable.ic_download, Theme.ON_VAR, 40, () -> {
            if (onDownload != null) onDownload.run();
        }), new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(Theme.LINE);
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        if (image != null) {
            ZoomImageView zoom = new ZoomImageView(this);
            android.graphics.Bitmap bmp = FileUtil.decodeSampleBitmapFromPath(image.getAbsolutePath(), 1600, 1600);
            if (bmp != null) zoom.setImageBitmap(bmp);
            else zoom.setImageDrawable(null);
            root.addView(zoom, new LinearLayout.LayoutParams(-1, 0, 1f));
            d.setContentView(root);
            root.requestApplyInsets();
            d.show();
            return;
        }

        ScrollView vs = new ScrollView(this);
        vs.setFillViewport(true);
        if (body == null) {
            TextView none = Ui.text(this, "Pratinjau belum tersedia untuk jenis berkas ini. Ketuk unduh untuk menyimpannya.",
                    14, Theme.ON_VAR, false);
            none.setPadding(dp(20), dp(20), dp(20), dp(20));
            vs.addView(none);
        } else {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(lineNumbers(body));
            row.addView(codeText(body));
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.addView(row);
            vs.addView(hs);
        }
        root.addView(vs, new LinearLayout.LayoutParams(-1, 0, 1f));
        d.setContentView(root);
        root.requestApplyInsets();
        d.show();
    }

    /** Nomor baris di sisi kiri pratinjau. Padding atas dan bawah sama dengan teks kode agar sejajar. */
    private TextView lineNumbers(String body) {
        int n = body.split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            if (i > 1) sb.append('\n');
            sb.append(i);
        }
        TextView t = new TextView(this);
        t.setText(sb);
        t.setTextColor(Theme.ON_VAR);
        t.setTextSize(13f);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.END);
        t.setPadding(dp(14), dp(10), dp(10), dp(14));
        return t;
    }

    /** Layar penuh untuk satu blok kode di chat. */
    private void showFullCode(final String lang, final String body) {
        int lines = body.split("\n", -1).length;
        openViewer(lang.isEmpty() ? "Kode" : lang, "Kode · " + lines + " baris", body, () -> downloadCode(lang, body));
    }

    /** Isi berkas untuk pratinjau (maks. 200 KB). */
    private static String readFileForView(File f) {
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            byte[] buf = new byte[200000];
            int n = in.read(buf);
            String s = n <= 0 ? "" : new String(buf, 0, n, "UTF-8");
            return f.length() > 200000 ? s + "\n…(dipotong)" : s;
        } catch (IOException e) {
            return "⚠️ Berkas tidak bisa dibaca.";
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    /** Buka pratinjau berkas di layar penuh. Berkas non-teks hanya bisa diunduh. */
    private void showFileViewer(final File f) {
        final String name = f.getName();
        int dot = name.lastIndexOf('.');
        String kind = dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "FILE";
        if (name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp|gif|bmp)$")) {
            openViewer(name, kind + " · " + sizeText(f.length()), null,
                    () -> MonaDownloader.start(getApplicationContext(), f, null), f);
            return;
        }
        String body = isTextLike(name) ? readFileForView(f) : null;
        openViewer(name, kind + " · " + sizeText(f.length()), body,
                () -> MonaDownloader.start(getApplicationContext(), f, null));
    }

    private static boolean isTextLike(String name) {
        return name.toLowerCase(Locale.ROOT).matches(
        ".*\\.(txt|md|java|kt|js|ts|py|html|htm|css|xml|json|csv|gradle|sh|c|cpp|h|yml|yaml|log|kts|properties)$");
    }

    /** Isi berkas teks (maks. 60 KB) untuk dikirim ke API. */
    private static String readTextForApi(File f, String name) {
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            byte[] buf = new byte[60000];
            int n = in.read(buf);
            String s = n <= 0 ? "" : new String(buf, 0, n, "UTF-8");
            boolean cut = f.length() > 60000;
            return "\n\n--- Isi berkas " + name + " ---\n" + s + (cut ? "\n…(dipotong)" : "") + "\n--- akhir berkas ---";
        } catch (IOException e) {
            return "";
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static String mimeOfFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static String b64(File f) throws IOException {
        byte[] data = new byte[(int) f.length()];
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
        } finally { in.close(); }
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    /** Tombol + : bottom sheet untuk menambah foto atau file (rapi ala Claude). */
    private void showAddSheet() {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        TextView t = Ui.text(this, "Tambahkan lampiran", 18, Theme.ON, true);
        t.setPadding(dp(4), 0, dp(4), dp(4));
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = Ui.text(this, "Foto atau berkas apa saja", 13, Theme.ON_VAR, false);
        sub.setPadding(dp(4), 0, dp(4), dp(16));
        box.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        box.addView(addSheetRow(R.drawable.icon_image, "Foto", "Gambar dari galeri atau kamera", () -> {
            sheet.dismiss(); pickAttachment(true);
        }), new LinearLayout.LayoutParams(-1, -2));
        box.addView(addSheetRow(R.drawable.icon_file_upload, "Berkas", "Dokumen, kode, PDF, dan lainnya", () -> {
            sheet.dismiss(); pickAttachment(false);
        }), new LinearLayout.LayoutParams(-1, -2));
        sheet.show();
    }

    private View addSheetRow(int res, String label, String desc, Runnable action) {
        LinearLayout row = baseRow();
        row.setPadding(dp(12), dp(14), dp(12), dp(14));
        // Ikon dalam lingkaran lembut
        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setBackground(Ui.rect(Theme.SURFACE_HIGH, 12));
        ImageView ic = Ui.icon(this, res, Theme.ON_VAR);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(22), dp(22));
        il.gravity = Gravity.CENTER;
        iconWrap.addView(ic, il);
        row.addView(iconWrap, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, label, 16, Theme.ON, false));
        TextView d = Ui.text(this, desc, 12, Theme.ON_VAR, false);
        d.setPadding(0, dp(2), 0, 0);
        texts.addView(d);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(14);
        row.addView(texts, tl);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void pickAttachment(boolean image) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(image ? "image/*" : "*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, image ? "Pilih foto" : "Pilih file"), REQ_PICK_ATTACH);
        } catch (Exception e) {
            M3.toast(this, "Tidak ada aplikasi pemilih berkas");
        }
    }

    /** Salin berkas pilihan ke sandbox/uploads dan tambahkan chip di atas kolom input. */
    private void addPendingAttachment(Uri uri) {
        try {
            String mime = getContentResolver().getType(uri);
            String name = "lampiran_" + System.currentTimeMillis();
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int col = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (col >= 0 && c.getString(col) != null) name = c.getString(col);
                    }
                } finally { c.close(); }
            }
            boolean image = mime != null && mime.startsWith("image/");
            File dir = new File(sandboxDir(), "uploads");
            if (!dir.isDirectory()) dir.mkdirs();
            File dst = uniqueFile(new File(dir, name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_")));
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("berkas tidak bisa dibuka");
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[65536];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 15L * 1024 * 1024) throw new IOException("berkas lebih dari 15 MB");
                    out.write(buf, 0, n);
                }
            } finally {
                in.close();
                out.close();
            }
            pending.add(new PendingAtt(dst, image));
            refreshPendingRow();
        } catch (IOException e) {
            M3.toast(this, "Lampiran gagal: " + e.getMessage());
        }
    }

    private void refreshPendingRow() {
        if (pendingRow == null) return;
        pendingRow.removeAllViews();
        pendingRow.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
        for (final PendingAtt a : new ArrayList<>(pending)) {
            // Kartu lampiran ala Claude: canvas rounded + badge/thumbnail + nama file (tanpa emoji)
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(8), dp(6), dp(8), dp(6));
            GradientDrawable bg = Ui.rect(Theme.SURFACE_HIGH, 14);
            bg.setStroke(dp(1), Theme.LINE);
            card.setBackground(bg);

            String name = a.file.getName();
            int dot = name.lastIndexOf('.');
            String ext = (dot >= 0 && dot < name.length() - 1)
                    ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "FILE";
            if (ext.length() > 4) ext = "FILE";

            if (a.image) {
                // Thumbnail kecil untuk gambar
                ImageView thumb = new ImageView(this);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 8;
                    Bitmap bmp = BitmapFactory.decodeFile(a.file.getAbsolutePath(), opts);
                    if (bmp != null) thumb.setImageBitmap(bmp);
                    else thumb.setImageDrawable(Ui.tinted(this, R.drawable.icon_image, Theme.ON_VAR));
                } catch (Exception e) {
                    thumb.setImageDrawable(Ui.tinted(this, R.drawable.icon_image, Theme.ON_VAR));
                }
                GradientDrawable clip = Ui.rect(Theme.SURFACE_HIGHEST, 8);
                thumb.setBackground(clip);
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    thumb.setClipToOutline(true);
                    thumb.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
                        }
                    });
                }
                card.addView(thumb, new LinearLayout.LayoutParams(dp(40), dp(40)));
            } else {
                // Badge ekstensi (canvas)
                TextView badge = Ui.text(this, ext, 10, Theme.ON_VAR, true);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.rect(Theme.SURFACE_HIGHEST, 8));
                card.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));
            }

            TextView title = Ui.text(this, name, 13, Theme.ON, false);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setMaxWidth(dp(140));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.leftMargin = dp(8);
            tl.rightMargin = dp(6);
            card.addView(title, tl);

            // Tombol hapus (×)
            TextView x = Ui.text(this, "×", 18, Theme.ON_VAR, false);
            x.setPadding(dp(4), 0, dp(2), 0);
            x.setOnClickListener(v -> { pending.remove(a); refreshPendingRow(); });
            card.addView(x);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            pendingRow.addView(card, lp);
        }
    }

    /** Berkas sandbox paling baru (untuk permintaan "kirim filenya"). */
    private String newestSandboxFile() {
        File[] fs = sandboxDir().listFiles();
        File best = null;
        if (fs != null) for (File f : fs) if (f.isFile() && (best == null || f.lastModified() > best.lastModified())) best = f;
        return best == null ? null : best.getAbsolutePath();
    }

    /** Pengguna meminta berkas dikirim: hanya saat itu berkas sandbox dilampirkan. */
    private static boolean wantsFileSend(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        boolean what = s.contains("file") || s.contains("berkas") || s.contains("kodenya") || s.contains("hasilnya");
        boolean send = s.contains("kirim") || s.contains("mana") || s.contains("lampir") || s.contains("unduh")
        || s.contains("download") || s.contains("send") || s.contains("attach") || s.contains("bagikan");
        return what && send;
    }

    /** Tombol kirim berubah jadi tombol berhenti selama permintaan berjalan. */
    private void updateSendButton() {
        if (sendButton == null) return;
        sendButton.setImageDrawable(Ui.tinted(this, busy ? R.drawable.ic_stop : ICON_SEND, Color.WHITE));
        sendButton.setContentDescription(busy ? "Berhenti" : "Kirim");
    }

    /** Hentikan permintaan.
     * - Saat masih menunggu server: pesan user dikembalikan ke input.
     * - Saat sedang streaming teks: batalkan animasi, tampilkan teks penuh, selesai. */
    private void stopCurrentJob() {
        ChatSession t = pendingSession;
        cancelledJob = jobSeq;
        boolean wasStreaming = (streamRunnable != null);
        cancelStream();
        busy = false;
        pendingSession = null;
        jobRunning = false;
        MonaService.end(getApplicationContext());
        removeTypingRow();
        updateSendButton();

        if (t != null && !t.messages.isEmpty() && !wasStreaming) {
            // Masih menunggu server: kembalikan prompt ke input
            ChatMessage last = t.messages.get(t.messages.size() - 1);
            if (last.isUser) {
                String prompt = last.content;
                t.messages.remove(t.messages.size() - 1);
                t.updated = System.currentTimeMillis();
                saveSessions();
                if (current == t) {
                    int childCount = chatContainer.getChildCount();
                    if (childCount > 0) chatContainer.removeViewAt(childCount - 1);
                    inputField.setText(prompt);
                    inputField.setSelection(prompt.length());
                    inputField.requestFocus();
                    keepInputFocus();
                }
            }
        } else if (wasStreaming && current != null && !current.messages.isEmpty()) {
            // Streaming dibatalkan: pastikan pesan AI terakhir tampil penuh
            // (render ulang pesan terakhir tanpa animasi)
            ChatMessage last = current.messages.get(current.messages.size() - 1);
            if (!last.isUser) {
                // Hapus view terakhir (partial stream) lalu tampilkan penuh
                int childCount = chatContainer.getChildCount();
                if (childCount > 0) chatContainer.removeViewAt(childCount - 1);
                addAiMessage(last, false);
            }
        }
        refreshHistory();
    }

    /** Tombol model di samping tombol +: menampilkan model aktif, ketuk untuk ganti. */
    private void refreshModelPill() {
        if (modelPill == null) return;
        applyOfflineState();
        String m = getModel(getProvider());
        modelPill.setText(m.length() > 20 ? m.substring(0, 19) + "…" : m);
    }

    /** Pengguna menyebut file/berkas/simpan atau nama berkas: baru boleh membuat berkas. */
    private static boolean wantsFileCreate(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        return s.matches("(?s).*\\b(file|berkas|simpan|save|dokumen)\\b.*")
            || s.matches("(?s).*\\.(txt|java|kt|md|json|py|js|html|xml|csv|gradle|sh)\\b.*");
    }

    /** Nama bahasa untuk blok kode markdown dari nama berkas. */
    private static String langOf(String path) {
        String n = path.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot + 1);
        switch (ext) {
            case "kt": return "kotlin";
            case "js": return "javascript";
            case "py": return "python";
            case "md": return "markdown";
            default: return ext;
        }
    }

    private static boolean wantsCodeView(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        if (s.contains("codebox") || s.contains("code box") || s.contains("kotak kode")) return true;
        boolean about = s.contains("kode") || s.contains("code") || s.contains("script") || s.contains("source");
        boolean show = s.contains("tampil") || s.contains("tunjuk") || s.contains("perlihat") || s.contains("lihat")
                || s.contains("show") || s.contains("print") || s.contains("paste") || s.contains("kasih")
                || s.contains("di chat") || s.contains("di sini") || s.contains("tulis di");
        return about && show;
    }

    /**
     * Blok kode di balasan AI disimpan ke sandbox, dan dicatat di timeline seperti perintah shell
     * "echo ... > NamaFile" plus langkah file. Jika pengguna tidak meminta kode, blok dibuang dari teks chat.
     * firstOut[0] diisi path berkas pertama (untuk kartu file).
     */
    private String saveCodeBlocks(String reply, List<String> steps, boolean showCode, String[] firstOut) {
        Matcher m = CODE_FENCE.matcher(reply);
        StringBuffer sb = new StringBuffer();
        int n = 0;
        while (m.find()) {
            String lang = m.group(1).toLowerCase(Locale.ROOT);
            String code = m.group(2);
            // Blok perintah mona-* yang tidak valid dan blok kosong dibiarkan apa adanya.
            if (lang.startsWith("mona-") || code.trim().isEmpty()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            n++;
            String out = writeSandbox(guessName(lang, code, n), code);
            if (out.startsWith("⚠️")) {
                steps.add(out);
            } else {
                String name = new File(out).getName();
                String first = code.trim().split("\n")[0].trim().replace('|', '/');
                if (first.length() > 60) first = first.substring(0, 60) + "…";
                // Langkah detail ala Claude agar proses file terlihat panjang & cermat
                if (name.endsWith(".java") || lang.equals("java") || lang.equals("kotlin") || lang.equals("kt")) {
                    steps.add("Merencanakan struktur kelas dan package");
                    steps.add("Menyiapkan import dan konstanta yang dibutuhkan");
                    steps.add("Menulis kerangka class dan field utama");
                    steps.add("Mengisi logika inti dan method pendukung");
                    steps.add("Menambahkan penanganan event dan input");
                    steps.add("Menyusun fungsi gambar / UI / rendering");
                    steps.add("Memeriksa konsistensi tipe data dan null-safety");
                    steps.add("Menulis komentar dan penanda bagian penting");
                    steps.add("Menyimpan berkas ke sandbox dengan nama " + name);
                } else if (name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".md")) {
                    steps.add("Merencanakan struktur konten " + name);
                    steps.add("Menulis bagian utama isi berkas");
                    steps.add("Menyusun format dan tata letak yang rapi");
                    steps.add("Memeriksa kelengkapan data");
                    steps.add("Menyimpan berkas ke sandbox");
                } else {
                    steps.add("Menyiapkan konten untuk " + name);
                    steps.add("Menulis isi berkas secara lengkap");
                    steps.add("Memeriksa format dan menyimpan ke sandbox");
                }
                steps.add("shell|echo \"" + first.replace("\"", "'") + "\" > " + name + "|");
                steps.add("file|Membuat " + name + "|" + out);
                if (firstOut[0] == null) firstOut[0] = out;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(showCode ? m.group(0) : ""));
        }
        m.appendTail(sb);
        String s = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        if (s.isEmpty() && n > 0) s = "Kodenya sudah saya simpan di sandbox.";
        return s;
    }

    /** Nama berkas untuk blok kode: dari baris "file: X", nama kelas Java, atau kode<N>.<ext>. */
    private static String guessName(String lang, String code, int n) {
        Matcher f = Pattern.compile("(?:file|berkas|nama file)\\s*:\\s*([\\w./-]+\\.\\w+)", Pattern.CASE_INSENSITIVE).matcher(code);
        if (f.find()) return new File(f.group(1)).getName();
        if (lang.equals("java") || lang.equals("kotlin") || lang.equals("kt")) {
            Matcher c = Pattern.compile("(?:public\\s+)?(?:final\\s+|abstract\\s+)?(?:class|interface|enum)\\s+(\\w+)").matcher(code);
            if (c.find()) return c.group(1) + (lang.equals("java") ? ".java" : ".kt");
        }
        return "kode" + (n > 1 ? "_" + n : "") + "." + extOf(lang);
    }

    private static String extOf(String lang) {
        switch (lang) {
            case "": return "txt";
            case "javascript": case "js": return "js";
            case "python": case "py": return "py";
            case "kotlin": return "kt";
            case "bash": case "sh": case "shell": return "sh";
            case "markdown": case "md": return "md";
            default:
                String e = lang.replaceAll("[^a-z0-9]", "");
                return e.isEmpty() ? "txt" : e;
        }
    }

    /** Pisahkan blok kode dari teks balasan. Mengisi out (lang, kode); mengembalikan teks tanpa blok. */
    private String extractCodeBoxes(String content, List<String[]> out) {
        Matcher m = CODE_FENCE.matcher(content);
        while (m.find()) {
            if (!m.group(2).trim().isEmpty()) out.add(new String[]{ m.group(1), m.group(2) });
        }
        return CODE_FENCE.matcher(content).replaceAll("").trim();
    }

    /** CodeBox ala Claude: kartu gelap berlabel bahasa, dengan salin, layar penuh, dan unduh. */
    private View codeBox(final String lang, final String code) {
        final String body = code.replaceAll("\\s+$", "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundedBg(Theme.SURFACE_HIGH, 14, 0, 0));
        box.addView(codeHeader(lang, body, () -> showFullCode(lang, body), R.drawable.ic_fullscreen),
        new LinearLayout.LayoutParams(-1, -2));
        ScrollView vs = new ScrollView(this);
        vs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.addView(codeText(body));
        vs.addView(hs);
        int lines = body.split("\n", -1).length;
        int h = java.lang.Math.min(lines * dp(19) + dp(24), dp(340));
        box.addView(vs, new LinearLayout.LayoutParams(-1, h));
        return box;
    }

    private String describeCommand(CommandIntent.Result r) {
        String a = r.action.name().toLowerCase(Locale.ROOT);
        return "Perintah file: " + a + (r.arg == null ? "" : " " + r.arg);
    }

    /** Path dari balasan AI. Path relatif dihitung dari folder aktif. */
    private File resolveFile(String path) {
        File f = new File(path);
        if (!f.isAbsolute()) f = new File(shell.getCurrentDir(), path);
        return f;
    }

    /**
     * Tulis teks ke berkas baru. Berkas yang sudah ada tidak ditimpa: nama diberi akhiran (1), (2), dst.
     * Mengembalikan path berkas, atau pesan galat yang diawali "⚠️".
     */
    /** Berkas dari AI selalu masuk sandbox Mona (hanya nama file yang dipakai), tidak ke folder Download. */
    private String writeFile(String path, String content) {
        return writeSandbox(new File(path).getName(), content);
    }

    /** Folder sandbox Mona di penyimpanan privat aplikasi. */
    private File sandboxDir() {
        File d = new File(getFilesDir(), "sandbox");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    private boolean isSandboxed(File f) {
        try {
            return f.getCanonicalPath().startsWith(sandboxDir().getCanonicalPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private List<File> sandboxFiles() {
        List<File> out = new ArrayList<>();
        collectFiles(sandboxDir(), out);
        return out;
    }

    private void collectFiles(File d, List<File> out) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collectFiles(f, out);
            else out.add(f);
        }
    }

    /** Hapus satu berkas menurut nama, atau semua berkas bila arg = "*". Hanya di dalam sandbox. */
    private String sandboxDelete(String arg) {
        List<File> files = sandboxFiles();
        if (files.isEmpty()) return "Sandbox Mona sudah kosong, tidak ada berkas yang perlu dihapus.";
        List<File> targets = new ArrayList<>();
        boolean all = "*".equals(arg) || "all".equalsIgnoreCase(arg);
        if (all) {
            targets.addAll(files);
        } else {
            String want = new File(arg == null ? "" : arg).getName();
            for (File f : files) if (f.getName().equalsIgnoreCase(want)) targets.add(f);
        }
        if (targets.isEmpty()) return "Berkas **" + arg + "** tidak ditemukan di sandbox Mona.";
        List<String> names = new ArrayList<>();
        for (File f : targets) {
            if (isSandboxed(f) && f.delete()) names.add(f.getName());
        }
        if (names.isEmpty()) return "⚠️ Gagal menghapus berkas di sandbox.";
        return "Menghapus " + names.size() + " berkas dari sandbox Mona"
                + (names.size() <= 5 ? ": " + TextUtils.join(", ", names) : "") + ".";
    }

    /** Permintaan hapus berkas sandbox dalam bahasa biasa (dikenali tanpa AI, berlaku online maupun offline). */
    private boolean wantsSandboxDelete(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        boolean verb = t.matches("(?s).*\\b(hapus|hapuskan|menghapus|delete|bersihkan|kosongkan)\\b.*");
        boolean obj = t.contains("file") || t.contains("berkas") || t.contains("sandbox") || t.contains("dokumen");
        if (!verb || !obj) return false;
        return true;
    }

    /** Menjalankan permintaan hapus: nama berkas yang disebut, atau semua berkas bila diminta. */
    private String deleteFromText(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        for (File f : sandboxFiles()) {
            if (t.contains(f.getName().toLowerCase(Locale.ROOT))) return sandboxDelete(f.getName());
        }
        if (t.matches("(?s).*(semua|seluruh|sandbox|file[²2]|file-file|file file|all).*")) return sandboxDelete("*");
        return "Sebutkan nama berkasnya, atau tulis \"hapus semua file di sandbox\".";
    }

    /** AI minta hapus lewat blok mona-cmd {"action":"delete","path":...}; null jika bukan itu. */
    private String aiDeleteArg(String reply) {
        Matcher m = MONA_CMD.matcher(reply);
        if (!m.find()) return null;
        try {
            JSONObject o = new JSONObject(m.group(1));
            if (!"delete".equalsIgnoreCase(o.optString("action", ""))) return null;
            return o.optString("path", "");
        } catch (Exception e) {
            return null;
        }
    }

    /** Tulis teks ke sandbox. Nama yang sudah ada tidak ditimpa. Mengembalikan path, atau "⚠️ …". */
    private String writeSandbox(String name, String content) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "berkas.txt";
        File dst = uniqueFile(new File(sandboxDir(), safe));
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(dst);
            os.write(content.getBytes("UTF-8"));
            os.flush();
        } catch (IOException e) {
            return "⚠️ Gagal menulis: " + e.getMessage();
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ignored) { }
            }
        }
        return dst.getAbsolutePath();
    }

    private File uniqueFile(File f) {
        if (!f.exists()) return f;
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            File g = new File(f.getParentFile(), base + " (" + i + ")" + ext);
            if (!g.exists()) return g;
        }
        return f;
    }

    /** Jalankan perintah ls/cd/cat/dst. Perintah yang perlu konfirmasi hanya jalan jika aplikasi tampil. */
    private void runChatCommand(final ChatSession target, final CommandIntent.Result r) {
        final List<String> steps = new ArrayList<>();
        final String cmd = shell.commandFor(r);
        steps.add("shell|" + (cmd == null ? describeCommand(r) : cmd) + "|");
        if (cmd == null) {
            steps.add("Ditolak: " + shell.getDenyReason());
            addShellResult(target, "⚠️ " + shell.getDenyReason(), steps);
            return;
        }
        if (r.action == CommandIntent.Action.MKDIR || r.action == CommandIntent.Action.TOUCH) {
            if (live != this || isFinishing()) {
                steps.add("Perlu konfirmasi: buka aplikasi lalu kirim ulang");
                addShellResult(target, "Perintah ini perlu konfirmasi. Buka Mona lalu minta lagi.", steps);
                return;
            }
            new AlertDialog.Builder(this)
            .setTitle("Jalankan perintah ini?")
            .setMessage(cmd)
            .setPositiveButton("Jalankan", (d, w) -> execChatCommand(target, r, steps))
            .setNegativeButton("Batal", (d, w) -> {
                steps.add("Dibatalkan pengguna");
                addShellResult(target, "Perintah dibatalkan.", steps);
            })
            .show();
            return;
        }
        execChatCommand(target, r, steps);
    }

    private void execChatCommand(final ChatSession target, CommandIntent.Result r, final List<String> steps) {
        final StringBuilder out = new StringBuilder();
        final int[] lines = { 0 };
        shell.runIntent(r, new MonaShell.Callback() {
            @Override public void onOutput(String line) {
                lines[0]++;
                if (out.length() > 40000) return;
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
            @Override public void onFinish(String result) {
                String body = out.toString();
                if (body.length() > 20000) body = body.substring(0, 20000) + "\n…(dipotong)";
                if (result != null && !result.isEmpty()) body = body + "\n" + result;
                String tail = body.trim();
                if (tail.length() > 4000) tail = tail.substring(0, 4000) + "…";
                String exit = result == null || result.isEmpty() ? "" : "\n" + result;
                steps.set(0, steps.get(0) + tail + exit);
                addShellResult(target, "```\n" + body.trim() + "\n```", steps);
            }
        });
    }

    /** Simpan hasil perintah file sebagai pesan AI. Tampil jika chat ini sedang dibuka. */
    private void addShellResult(ChatSession target, String text, List<String> steps) {
        // Output perintah hanya masuk ke daftar tugas (Proses berpikir), bukan teks chat
        ChatMessage msg = new ChatMessage("", false, steps, null);
        target.messages.add(msg);
        target.updated = System.currentTimeMillis();
        saveSessions();
        MainActivity v = live;
        if (v != null) {
            if (v.current == target) v.addAiMessage(msg);
            v.refreshHistory();
        } else {
            dirty = true;
        }
    }

    /** Gabung pesan berurutan dengan role sama supaya valid untuk semua API. */
    private List<String[]> normalize(List<ChatMessage> msgs) {
        List<String[]> out = new ArrayList<>();
        int from = java.lang.Math.max(0, msgs.size() - 30);
        for (int i = from; i < msgs.size(); i++) {
            ChatMessage m = msgs.get(i);
            String role = m.isUser ? "user" : "assistant";
            // Hasil perintah shell hanya ada di langkah (bukan teks chat); tanpa ini AI lupa isi berkas
            // dan menjalankan ulang perintah. Hanya 8 balasan terakhir yang dibawakan supaya hemat token.
            String c = m.isUser ? m.content + m.extra
                    : (i >= msgs.size() - 8 ? shellContext(m) : "") + m.content;
            if (c.trim().isEmpty()) continue;
            if (!out.isEmpty() && out.get(out.size() - 1)[0].equals(role)) {
                String[] prev = out.get(out.size() - 1);
                prev[1] = prev[1] + "\n" + c;
            } else {
                out.add(new String[]{ role, c });
            }
        }
        // semua API minta pesan pertama dari user
        while (!out.isEmpty() && !out.get(0)[0].equals("user")) out.remove(0);
        return out;
    }

    /** Output perintah shell dari langkah sebuah balasan, untuk konteks model (dibatasi panjangnya). */
    private String shellContext(ChatMessage m) {
        StringBuilder sb = new StringBuilder();
        for (String s : m.steps) {
            if (!s.startsWith("shell|")) continue;
            String[] p = s.split("\\|", 3);
            if (p.length < 3 || p[2].trim().isEmpty()) continue;
            String out = p[2].trim();
            if (out.length() > 4000) out = out.substring(0, 4000) + "…";
            sb.append("[Hasil perintah: ").append(p[1]).append("]\n").append(out).append("\n\n");
        }
        return sb.toString();
    }

    // =====================================================================
    //  REQUEST KE BERBAGAI AI (POST chat, GET daftar model)
    // =====================================================================
    // ===== Ganti model otomatis =====
    /** Model yang baru gagal karena token/kuota habis atau tidak tersedia: "provider|model" -> sampai kapan dilewati. */
    private static final Map<String, Long> unavailable = new HashMap<>();
    private static final long UNAVAILABLE_MS = 30L * 60L * 1000L;

    /** Default aktif: jika model gagal (token habis, tidak tersedia, galat apa pun), langsung pindah ke model lain. */
    private boolean autoSwitchOn() { return prefs.getBoolean("auto_switch", true); }

    private boolean isUnavailable(Provider p, String m) {
        synchronized (unavailable) {
            Long until = unavailable.get(p.name + "|" + m);
            return until != null && until > System.currentTimeMillis();
        }
    }

    private void markUnavailable(Provider p, String m) {
        synchronized (unavailable) {
            unavailable.put(p.name + "|" + m, System.currentTimeMillis() + UNAVAILABLE_MS);
        }
    }

    private static boolean isQuotaError(String msg) {
        String s = msg.toLowerCase(Locale.ROOT);
        return s.contains("http 429") || s.contains("http 402") || s.contains("quota") || s.contains("rate limit")
        || s.contains("rate_limit") || s.contains("insufficient") || s.contains("exceeded") || s.contains("credit")
        || s.contains("billing") || s.contains("token") || s.contains("resource_exhausted");
    }

    private static boolean isModelMissingError(String msg) {
        String s = msg.toLowerCase(Locale.ROOT);
        return s.contains("http 404") || s.contains("not found") || s.contains("does not exist")
        || s.contains("not supported") || s.contains("decommission") || s.contains("no longer");
    }

    /**
     * Kirim ke model aktif. Jika "Ganti model otomatis" aktif dan model habis/tidak tersedia,
     * lewati model itu dan coba model lain. Pesan galat hanya muncul jika semua model gagal.
     */
    private String sendWithFallback(Provider p, String base, String key, String model,
                                    List<ChatMessage> msgs, List<String> steps) throws Exception {
        if (!autoSwitchOn()) return sendChat(p, base, key, model, msgs);

        // Urutan coba: model aktif dulu, lalu model lain. Model yang baru gagal dilewati dulu.
        List<String> order = new ArrayList<>();
        order.add(model);
        for (String c : getModelList(p)) if (!order.contains(c)) order.add(c);

        List<String> skipped = new ArrayList<>();
        Exception last = null;
        for (String m : order) {
            if (isUnavailable(p, m)) { skipped.add(m); continue; }
            try {
                return tryModel(p, base, key, model, m, msgs, steps);
            } catch (Exception e) {
                markUnavailable(p, m);
                last = e;
            }
        }
        // Semua model lain sedang ditandai gagal: coba lagi, siapa tahu sudah pulih.
        for (String m : skipped) {
            try {
                return tryModel(p, base, key, model, m, msgs, steps);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new IOException("Semua model habis atau tidak tersedia. Terakhir: "
                + (last == null ? "-" : String.valueOf(last.getMessage())));
    }

    /** Satu percobaan ke model m. Balasan kosong dihitung sebagai gagal. */
    private String tryModel(Provider p, String base, String key, String active, String m,
                            List<ChatMessage> msgs, List<String> steps) throws Exception {
        String reply = sendChat(p, base, key, m, msgs);
        if (reply.trim().isEmpty()) throw new IOException("balasan kosong dari " + m);
        if (!m.equals(active)) {
            prefs.edit().putString("model_" + p.name, m).apply();
            steps.add("Model " + active + " tidak tersedia, pindah ke " + m);
        }
        return reply;
    }

    /** Budget token untuk mode berpikir (dipakai Claude dan Gemini). */
    private static final int THINK_BUDGET = 4000;
    /** Pikiran dari balasan terakhir; kosong jika model tidak mengirimkannya. */
    private String lastThought = "";

    private boolean thinkOn() {
        return prefs.getBoolean("think", true);
    }

    /** Coba dengan mode berpikir; jika provider menolak parameternya, ulangi tanpa berpikir. */
    private String sendChat(Provider p, String base, String key, String model, List<ChatMessage> msgs)
    throws Exception {
        lastThought = "";
        if (!thinkOn()) return sendChatBody(p, base, key, model, msgs, false);
        try {
            return sendChatBody(p, base, key, model, msgs, true);
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            if (!(m.contains("400") || m.contains("think") || m.contains("budget") || m.contains("thought"))) throw e;
            lastThought = "";
            return sendChatBody(p, base, key, model, msgs, false);
        }
    }

    /** Daftar berkas sandbox untuk system prompt: AI hanya boleh menyebut nama yang ada di sini. */
    private String sandboxSummary() {
        File[] fs = sandboxDir().listFiles();
        List<File> files = new ArrayList<>();
        if (fs != null) for (File f : fs) if (f.isFile()) files.add(f);
        if (files.isEmpty()) return "Sandbox Mona saat ini kosong (belum ada berkas).\n";
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        StringBuilder sb = new StringBuilder("Berkas di sandbox Mona (terbaru dulu). Hanya berkas ini yang ada:\n");
        for (int i = 0; i < files.size() && i < 30; i++) {
            File f = files.get(i);
            sb.append("- ").append(f.getName()).append(" (").append(f.length()).append(" byte)\n");
        }
        return sb.toString();
    }

    private String sandboxNames() {
        File[] fs = sandboxDir().listFiles();
        StringBuilder sb = new StringBuilder();
        if (fs != null) for (File f : fs) if (f.isFile()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.getName());
        }
        return sb.length() == 0 ? "Sandbox Mona kosong." : "Yang ada di sandbox: " + sb + ".";
    }

    /** Cocokkan path dari AI ke berkas nyata: path asli, nama di sandbox, atau nama yang sangat mirip. */
    private String resolveAttachPath(String path) {
        File f = resolveAny(path);
        if (f.isFile()) return f.getAbsolutePath();
        String name = new File(path).getName().toLowerCase(Locale.ROOT);
        File best = null;
        double score = 0;
        File[] fs = sandboxDir().listFiles();
        if (fs != null) for (File g : fs) if (g.isFile()) {
            double s = StringSimilarity.jaroWinkler(name, g.getName().toLowerCase(Locale.ROOT));
            if (s > score) { score = s; best = g; }
        }
        return best != null && score >= 0.92 ? best.getAbsolutePath() : null;
    }

    /** Salin berkas sandbox terbaru ke folder Download; path hasil salinan, atau null. */
    private String exportNewestToDownload() {
        String p = newestSandboxFile();
        if (p == null) return null;
        File src = new File(p);
        File dst = uniqueFile(new File(FileUtil.getPublicDir("Download"), src.getName()));
        try {
            FileUtil.copyFile(src.getAbsolutePath(), dst.getAbsolutePath());
        } catch (RuntimeException e) {
            return null;
        }
        return dst.isFile() ? dst.getAbsolutePath() : null;
    }

    private static String clipThought(String s) {
        return s.length() > 6000 ? s.substring(0, 6000) + "…" : s;
    }

    private String sendChatBody(Provider p, String base, String key, String model, List<ChatMessage> msgs, boolean think)
    throws Exception {
        List<String[]> turns = normalize(msgs);
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        int last = turns.size() - 1;
        boolean withImg = last >= 0 && "user".equals(turns.get(last)[0]) && !curImages.isEmpty();

        if (p.type == TYPE_ANTHROPIC) {
            h.put("x-api-key", key);
            h.put("anthropic-version", "2023-06-01");
            JSONArray arr = new JSONArray();
            for (int i = 0; i < turns.size(); i++) {
                String[] t = turns.get(i);
                Object content = t[1];
                if (withImg && i == last) {
                    JSONArray parts = new JSONArray();
                    for (File f : curImages) {
                        parts.put(new JSONObject().put("type", "image").put("source", new JSONObject()
                        .put("type", "base64").put("media_type", mimeOfFile(f)).put("data", b64(f))));
                    }
                    parts.put(new JSONObject().put("type", "text").put("text", t[1]));
                    content = parts;
                }
                arr.put(new JSONObject().put("role", t[0]).put("content", content));
            }
            JSONObject body = new JSONObject()
            .put("model", model).put("max_tokens", think ? THINK_BUDGET + 4096 : 4096)
            .put("system", systemPrompt()).put("messages", arr);
            if (think) body.put("thinking", new JSONObject().put("type", "enabled").put("budget_tokens", THINK_BUDGET));
            String res = http("POST", base + "/messages", h, body.toString());
            JSONArray c = new JSONObject(res).getJSONArray("content");
            StringBuilder sb = new StringBuilder(), th = new StringBuilder();
            for (int i = 0; i < c.length(); i++) {
                JSONObject part = c.getJSONObject(i);
                if ("text".equals(part.optString("type"))) sb.append(part.optString("text"));
                else if ("thinking".equals(part.optString("type"))) th.append(part.optString("thinking"));
            }
            lastThought = th.toString();
            return orEmpty(sb.toString());

        } else if (p.type == TYPE_GEMINI) {
            h.put("x-goog-api-key", key);
            JSONArray contents = new JSONArray();
            for (int i = 0; i < turns.size(); i++) {
                String[] t = turns.get(i);
                JSONObject c = new JSONObject();
                c.put("role", t[0].equals("user") ? "user" : "model");
                JSONArray parts = new JSONArray();
                if (withImg && i == last) {
                    for (File f : curImages) {
                        parts.put(new JSONObject().put("inline_data", new JSONObject()
                        .put("mime_type", mimeOfFile(f)).put("data", b64(f))));
                    }
                }
                parts.put(new JSONObject().put("text", t[1]));
                c.put("parts", parts);
                contents.put(c);
            }
            String m = model.startsWith("models/") ? model.substring(7) : model;
            JSONObject gbody = new JSONObject().put("systemInstruction", new JSONObject().put("parts",
            new JSONArray().put(new JSONObject().put("text", systemPrompt())))).put("contents", contents);
            if (think) gbody.put("generationConfig", new JSONObject().put("thinkingConfig",
            new JSONObject().put("includeThoughts", true).put("thinkingBudget", THINK_BUDGET)));
            String res = http("POST", base + "/models/" + m + ":generateContent", h, gbody.toString());
            JSONArray cands = new JSONObject(res).optJSONArray("candidates");
            if (cands == null || cands.length() == 0) return orEmpty("");
            JSONArray parts = cands.getJSONObject(0).getJSONObject("content").optJSONArray("parts");
            StringBuilder sb = new StringBuilder(), th = new StringBuilder();
            if (parts != null) for (int i = 0; i < parts.length(); i++) {
                JSONObject pt = parts.getJSONObject(i);
                if (pt.optBoolean("thought")) th.append(pt.optString("text"));
                else sb.append(pt.optString("text"));
            }
            lastThought = th.toString();
            return orEmpty(sb.toString());

        } else { // OpenAI-compatible
            if (!key.isEmpty()) h.put("Authorization", "Bearer " + key);
            JSONArray withSys = new JSONArray().put(new JSONObject().put("role", "system").put("content", systemPrompt()));
            for (int i = 0; i < turns.size(); i++) {
                String[] t = turns.get(i);
                Object content = t[1];
                if (withImg && i == last) {
                    JSONArray parts = new JSONArray();
                    for (File f : curImages) {
                        parts.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject()
                        .put("url", "data:" + mimeOfFile(f) + ";base64," + b64(f))));
                    }
                    parts.put(new JSONObject().put("type", "text").put("text", t[1]));
                    content = parts;
                }
                withSys.put(new JSONObject().put("role", t[0]).put("content", content));
            }
            JSONObject body = new JSONObject().put("model", model).put("messages", withSys);
            String res = http("POST", base + "/chat/completions", h, body.toString());
            JSONArray ch = new JSONObject(res).getJSONArray("choices");
            JSONObject msg = ch.getJSONObject(0).getJSONObject("message");
            lastThought = msg.optString("reasoning_content", "");   // DeepSeek, Groq, dll. bila tersedia
            return orEmpty(msg.optString("content"));
        }
    }

    /** GET daftar model yang didukung API key. Mengikuti paginasi Anthropic/Gemini, memfilter model non-chat. */
    private List<String> fetchModels(Provider p, String base, String key) throws Exception {
        Map<String, String> h = new HashMap<>();
        if (p.type == TYPE_ANTHROPIC) {
            h.put("x-api-key", key);
            h.put("anthropic-version", "2023-06-01");
        } else if (p.type == TYPE_GEMINI) {
            h.put("x-goog-api-key", key);
        } else if (!key.isEmpty()) {
            h.put("Authorization", "Bearer " + key);
        }

        List<String> out = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < 10; page++) {
            String url = base + "/models";
            if (p.type == TYPE_ANTHROPIC) {
                url += "?limit=100";
                if (pageToken != null) url += "&after_id=" + pageToken;
            } else if (p.type == TYPE_GEMINI) {
                url += "?pageSize=100";
                if (pageToken != null) url += "&pageToken=" + pageToken;
            }
            String t = http("GET", url, h, null).trim();

            JSONObject obj = null;
            JSONArray arr;
            if (t.startsWith("[")) arr = new JSONArray(t);
            else {
                obj = new JSONObject(t);
                arr = obj.optJSONArray(p.type == TYPE_GEMINI ? "models" : "data");
            }

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    if (m == null) continue;
                    if (p.type == TYPE_GEMINI) {
                        String n = m.optString("name");
                        if (n.startsWith("models/")) n = n.substring(7);
                        JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                        if (methods != null && !jsonContains(methods, "generateContent")) continue;
                        if (!n.isEmpty() && !out.contains(n)) out.add(n);
                    } else {
                        String id = m.optString("id");
                        if (id.isEmpty() || out.contains(id)) continue;
                        if (p.type == TYPE_OPENAI && isNonChatModel(id)) continue;
                        out.add(id);
                    }
                }
            }

            // paginasi
            pageToken = null;
            if (obj != null) {
                if (p.type == TYPE_ANTHROPIC && obj.optBoolean("has_more", false)) {
                    String last = obj.optString("last_id", "");
                    if (!last.isEmpty()) pageToken = last;
                } else if (p.type == TYPE_GEMINI) {
                    String nt = obj.optString("nextPageToken", "");
                    if (!nt.isEmpty()) pageToken = nt;
                }
            }
            if (pageToken == null) break;
        }
        Collections.sort(out);
        return out;
    }

    private boolean isNonChatModel(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        String[] bad = { "embed", "whisper", "tts", "dall-e", "moderation", "davinci", "babbage",
            "transcribe", "image", "rerank", "guard" };
        for (String b : bad) if (s.contains(b)) return true;
        return false;
    }

    private boolean jsonContains(JSONArray a, String s) {
        for (int i = 0; i < a.length(); i++) if (s.equals(a.optString(i))) return true;
        return false;
    }

    private String http(String method, String urlStr, Map<String, String> headers, String body)
    throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(20000);
            c.setReadTimeout(120000);
            if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
            if (body != null) {
                byte[] b = body.getBytes("UTF-8");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(b.length);
                OutputStream os = c.getOutputStream();
                os.write(b);
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String res = readAll(is);
            if (code >= 400) {
                String msg = extractError(res);
                if (msg.length() > 400) msg = msg.substring(0, 400) + "...";
                throw new IOException("HTTP " + code + ": " + msg);
            }
            return res;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private String extractError(String res) {
        try {
            JSONObject o = new JSONObject(res);
            Object err = o.opt("error");
            if (err instanceof JSONObject) return ((JSONObject) err).optString("message", res);
            if (err instanceof String) return (String) err;
        } catch (Exception ignored) {}
        return res.trim();
    }

    private String orEmpty(String s) { return s == null || s.trim().isEmpty() ? "(balasan kosong)" : s.trim(); }

    // =====================================================================
    //  PENYIMPANAN
    // =====================================================================
    private Provider getProvider() {
        String n = prefs.getString("provider", PROVIDERS[0].name);
        for (Provider p : PROVIDERS) if (p.name.equals(n)) return p;
        return PROVIDERS[0];
    }
    private String getKey(Provider p) { return prefs.getString("key_" + p.name, ""); }
    private String getModel(Provider p) { return prefs.getString("model_" + p.name, p.models[0]); }
    private String getBaseUrl(Provider p) { return stripSlash(prefs.getString("base_" + p.name, p.baseUrl)); }
    private String stripSlash(String s) { while (s.endsWith("/")) s = s.substring(0, s.length() - 1); return s; }

    /** Daftar model hasil GET terakhir (tersimpan per provider), atau daftar bawaan. */
    private List<String> getModelList(Provider p) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("models_" + p.name, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {}
        if (out.isEmpty()) out.addAll(Arrays.asList(p.models));
        return out;
    }

    private void saveModelList(Provider p, List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        prefs.edit().putString("models_" + p.name, a.toString()).apply();
    }

    private void loadSessions() {
        sessions = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("sessions", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ChatSession s = new ChatSession();
                s.id = o.optString("id", s.id);
                s.title = o.optString("title", "Chat");
                s.updated = o.optLong("updated");
                JSONArray ms = o.optJSONArray("m");
                if (ms != null) for (int j = 0; j < ms.length(); j++) {
                    JSONObject mo = ms.getJSONObject(j);
                    List<String> steps = new ArrayList<>();
                    JSONArray ps = mo.optJSONArray("p");
                    if (ps != null) for (int k = 0; k < ps.length(); k++) steps.add(ps.optString(k));
                    String a = mo.optString("a", "");
                    List<String> files = new ArrayList<>();
                    JSONArray fs = mo.optJSONArray("f");
                    if (fs != null) for (int k = 0; k < fs.length(); k++) {
                        String fp = fs.optString(k);
                        if (fp != null && !fp.isEmpty()) files.add(fp);
                    }
                    ChatMessage cm = new ChatMessage(mo.optString("c"), mo.optBoolean("u"), steps, a.isEmpty() ? null : a, files);
                    cm.extra = mo.optString("x", "");
                    s.messages.add(cm);
                }
                sessions.add(s);
            }
        } catch (JSONException ignored) {}
    }

    private void saveSessions() {
        try {
            JSONArray arr = new JSONArray();
            for (ChatSession s : sessions) {
                JSONObject o = new JSONObject();
                o.put("id", s.id).put("title", s.title).put("updated", s.updated);
                JSONArray ms = new JSONArray();
                for (ChatMessage m : s.messages) {
                    JSONObject mo = new JSONObject().put("c", m.content).put("u", m.isUser);
                    if (!m.steps.isEmpty()) {
                        JSONArray ps = new JSONArray();
                        for (String st : m.steps) ps.put(st);
                        mo.put("p", ps);
                    }
                    if (m.attach != null) mo.put("a", m.attach);
                    if (!m.extra.isEmpty()) mo.put("x", m.extra);
                    if (m.files != null && !m.files.isEmpty()) {
                        JSONArray fa = new JSONArray();
                        for (String fp : m.files) fa.put(fp);
                        mo.put("f", fa);
                    }
                    ms.put(mo);
                }
                o.put("m", ms);
                arr.put(o);
            }
            prefs.edit().putString("sessions", arr.toString()).apply();
        } catch (JSONException ignored) {}
    }

    // =====================================================================
    //  PESAN: user = gelembung, AI = proses berpikir (bisa dibuka) lalu teks jawaban
    // =====================================================================
    private void renderCurrent() {
        chatContainer.removeAllViews();
        typingRow = null;
        typingLive = null;
        if (current == null || current.messages.isEmpty()) { addWelcome(); return; }
        for (ChatMessage m : current.messages) {
            if (m.isUser) addUserMessage(m); else addAiMessage(m);
        }
        if (busy && current == pendingSession) typingRow = addTypingMessage();
    }

    /** Sambutan awal ala Claude: bintang oranye + "Selamat malam, <nama>" (huruf serif). */
    private void addWelcome() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(48), dp(16), dp(96));

        box.addView(new MonaOrb(this, true, true), new LinearLayout.LayoutParams(dp(72), dp(72)));

        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String sapa = h < 4 ? "Selamat malam" : h < 11 ? "Selamat pagi" : h < 15 ? "Selamat siang"
        : h < 18 ? "Selamat sore" : "Selamat malam";
        String n = prefs.getString("user_name", "Pengguna");
        if (n != null && !n.trim().isEmpty() && !"Pengguna".equals(n)) sapa += ", " + n.trim();

        TextView tv = new TextView(this);
        tv.setText(sapa);
        tv.setTextColor(Theme.ON);
        tv.setTextSize(30);
        tv.setTypeface(Typeface.SERIF);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(20);
        box.addView(tv, tp);
        tv.setAlpha(0f);
        tv.setTranslationY(dp(8));
        tv.animate().alpha(1f).translationY(0f).setStartDelay(280).setDuration(520)
        .setInterpolator(new DecelerateInterpolator()).start();

        chatContainer.addView(box, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /**
     * Pesan user: kartu lampiran di ATAS bubble (thumbnail/badge + nama file), teks terpisah.
     * Tombol salin & edit muncul hanya saat bubble diklik.
     * Saat edit: pesan AI setelahnya dihapus karena akan dibuat ulang.
     */
    private View addUserMessage(final ChatMessage msg) {
        final String text = msg.content != null ? msg.content : "";
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.END);
        col.setPadding(0, dp(6), 0, dp(4));

        // Kartu lampiran di atas teks (tidak digabung ke bubble)
        if (msg.files != null && !msg.files.isEmpty()) {
            LinearLayout filesCol = new LinearLayout(this);
            filesCol.setOrientation(LinearLayout.VERTICAL);
            filesCol.setGravity(Gravity.END);
            for (String path : msg.files) {
                View card = userAttachCard(path);
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-2, -2);
                flp.leftMargin = dp(48);
                flp.bottomMargin = dp(6);
                filesCol.addView(card, flp);
            }
            col.addView(filesCol, new LinearLayout.LayoutParams(-1, -2));
        }

        // Bubble teks (hanya jika ada teks)
        final LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setGravity(Gravity.END);
        acts.setVisibility(View.GONE);

        if (!text.isEmpty()) {
            TextView bubble = Ui.text(this, text, 15, Theme.ON, false);
            bubble.setLineSpacing(dp(2), 1f);
            bubble.setTextIsSelectable(false);
            bubble.setBackground(roundedBg(Theme.SURFACE_HIGH, 18, 0, 0));
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.leftMargin = dp(48);
            col.addView(bubble, bp);

            bubble.setClickable(true);
            bubble.setOnClickListener(v -> toggleUserActs(col, acts));
        }

        acts.addView(Ui.iconButton(this, R.drawable.ic_copy, Theme.ON_VAR, 30, () -> copyToClipboard(text)),
                new LinearLayout.LayoutParams(dp(34), dp(34)));
        acts.addView(Ui.iconButton(this, R.drawable.ic_edit, Theme.ON_VAR, 30, () -> {
            if (current != null) {
                int idx = -1;
                for (int i = 0; i < current.messages.size(); i++) {
                    if (current.messages.get(i) == msg) { idx = i; break; }
                }
                // fallback: cocokkan konten
                if (idx < 0) {
                    for (int i = 0; i < current.messages.size(); i++) {
                        ChatMessage cm = current.messages.get(i);
                        if (cm.isUser && text.equals(cm.content)) idx = i;
                    }
                }
                if (idx >= 0) {
                    while (current.messages.size() > idx) {
                        current.messages.remove(current.messages.size() - 1);
                    }
                    current.updated = System.currentTimeMillis();
                    saveSessions();
                    chatContainer.removeAllViews();
                    for (ChatMessage m : current.messages) {
                        if (m.isUser) addUserMessage(m);
                        else addAiMessage(m);
                    }
                    refreshHistory();
                }
            }
            inputField.setText(text);
            inputField.setSelection(text.length());
            inputField.requestFocus();
            keepInputFocus();
        }), new LinearLayout.LayoutParams(dp(34), dp(34)));
        col.addView(acts, new LinearLayout.LayoutParams(-2, -2));

        // Jika hanya lampiran tanpa teks, klik kartu juga bisa toggle acts
        if (text.isEmpty() && msg.files != null && !msg.files.isEmpty()) {
            col.setClickable(true);
            col.setOnClickListener(v -> toggleUserActs(col, acts));
        }

        chatContainer.addView(col, new LinearLayout.LayoutParams(-1, -2));
        scrollToBottom();
        return col;
    }

    private void toggleUserActs(LinearLayout col, LinearLayout acts) {
        if (acts.getVisibility() == View.VISIBLE) {
            acts.setVisibility(View.GONE);
        } else {
            for (int i = 0; i < chatContainer.getChildCount(); i++) {
                View child = chatContainer.getChildAt(i);
                if (child instanceof LinearLayout && child != col) {
                    LinearLayout ll = (LinearLayout) child;
                    for (int j = 0; j < ll.getChildCount(); j++) {
                        View c = ll.getChildAt(j);
                        if (c instanceof LinearLayout && c != acts) {
                            // hide other acts rows that look like action bars
                            LinearLayout maybe = (LinearLayout) c;
                            if (maybe.getChildCount() == 2 && maybe.getOrientation() == LinearLayout.HORIZONTAL) {
                                maybe.setVisibility(View.GONE);
                            }
                        }
                    }
                }
            }
            acts.setVisibility(View.VISIBLE);
        }
    }

    /** Kartu lampiran user ala Claude: thumbnail (gambar) atau badge tipe + nama file di bawah. */
    private View userAttachCard(final String path) {
        final File f = resolveAny(path);
        final boolean exists = f.isFile();
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String ext = (dot >= 0 && dot < name.length() - 1)
                ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "FILE";
        if (ext.length() > 5) ext = "FILE";
        boolean isImg = isImageFile(name);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = Ui.rect(Theme.SURFACE, 14);
        bg.setStroke(dp(1), Theme.LINE);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE), bg, null));
        card.setClickable(true);

        if (isImg && exists) {
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                Bitmap bmp = BitmapFactory.decodeFile(path, opts);
                if (bmp != null) thumb.setImageBitmap(bmp);
                else thumb.setImageDrawable(Ui.tinted(this, R.drawable.icon_image, Theme.ON_VAR));
            } catch (Exception e) {
                thumb.setImageDrawable(Ui.tinted(this, R.drawable.icon_image, Theme.ON_VAR));
            }
            if (Build.VERSION.SDK_INT >= 21) {
                thumb.setClipToOutline(true);
                thumb.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
                    }
                });
            }
            card.addView(thumb, new LinearLayout.LayoutParams(dp(160), dp(120)));
            TextView title = Ui.text(this, name, 12, Theme.ON, false);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(0, dp(6), 0, 0);
            card.addView(title, new LinearLayout.LayoutParams(dp(160), -2));
        } else {
            // Box tipe file di atas, nama file di bawah kiri
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = Ui.text(this, ext, 11, Theme.ON_VAR, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.rect(Theme.SURFACE_HIGHEST, 8));
            badge.setPadding(dp(8), dp(6), dp(8), dp(6));
            top.addView(badge, new LinearLayout.LayoutParams(-2, -2));
            card.addView(top, new LinearLayout.LayoutParams(-2, -2));

            TextView title = Ui.text(this, name, 13, Theme.ON, false);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(0, dp(8), 0, 0);
            card.addView(title, new LinearLayout.LayoutParams(dp(140), -2));
            TextView sub = Ui.text(this, exists ? sizeText(f.length()) : "Tidak ditemukan", 11, Theme.ON_VAR, false);
            sub.setPadding(0, dp(2), 0, 0);
            card.addView(sub, new LinearLayout.LayoutParams(-2, -2));
        }

        card.setOnClickListener(v -> {
            if (!exists) { M3.toast(this, "File tidak ditemukan"); return; }
            showFileViewer(f);
        });
        return card;
    }

    private static boolean isImageFile(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp");
    }

    /** Balasan AI: proses berpikir (ketuk untuk melihat langkah), teks jawaban (bisa dianimasikan), lalu kartu lampiran. */
    private View addAiMessage(ChatMessage m) {
        return addAiMessage(m, false);
    }

    /** Tombol respon di bawah balasan AI: suka, tidak suka, salin, bagikan, dan buat ulang. Ikon thin. */
    private void addAiActions(LinearLayout extra, final ChatMessage m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(4), 0, 0);
        final ImageView[] fb = new ImageView[2];
        fb[0] = Ui.iconButton(this, R.drawable.ic_thin_thumb_up, Theme.ON_VAR, 30, () -> {
            m.feedback = m.feedback == 1 ? 0 : 1;
            paintFeedback(fb[0], fb[1], m.feedback);
        });
        fb[1] = Ui.iconButton(this, R.drawable.ic_thin_thumb_down, Theme.ON_VAR, 30, () -> {
            m.feedback = m.feedback == -1 ? 0 : -1;
            paintFeedback(fb[0], fb[1], m.feedback);
        });
        paintFeedback(fb[0], fb[1], m.feedback);
        row.addView(fb[0], actParams());
        row.addView(fb[1], actParams());
        row.addView(Ui.iconButton(this, R.drawable.ic_thin_copy, Theme.ON_VAR, 30,
                () -> copyToClipboard(OutputHelper.toPlain(m.content))), actParams());
        row.addView(Ui.iconButton(this, R.drawable.ic_thin_share, Theme.ON_VAR, 30,
                () -> shareText(OutputHelper.toPlain(m.content))), actParams());
        row.addView(Ui.iconButton(this, R.drawable.ic_thin_retry, Theme.ON_VAR, 30,
                () -> recreateFrom(m)), actParams());
        extra.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout.LayoutParams actParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.setMarginEnd(dp(4));
        return lp;
    }

    /** Suka/tidak suka aktif diwarnai oranye (PRIMARY), yang tidak aktif abu-abu. */
    private void paintFeedback(ImageView up, ImageView down, int state) {
        up.setImageDrawable(Ui.tinted(this, R.drawable.ic_thin_thumb_up, state == 1 ? Theme.PRIMARY : Theme.ON_VAR));
        down.setImageDrawable(Ui.tinted(this, R.drawable.ic_thin_thumb_down, state == -1 ? Theme.PRIMARY : Theme.ON_VAR));
    }

    private void shareText(String s) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, s);
        startActivity(Intent.createChooser(send, "Bagikan jawaban"));
    }

    /** Buat ulang: buang balasan ini beserta pertanyaannya, lalu kirim pertanyaan yang sama lagi. */
    private void recreateFrom(ChatMessage m) {
        if (current == null || busy) {
            M3.toast(this, "Tunggu sampai jawaban selesai");
            return;
        }
        int idx = current.messages.indexOf(m);
        if (idx < 0) return;
        int u = idx - 1;
        while (u >= 0 && !current.messages.get(u).isUser) u--;
        if (u < 0) return;
        String prompt = current.messages.get(u).content;
        while (current.messages.size() > u) current.messages.remove(current.messages.size() - 1);
        current.updated = System.currentTimeMillis();
        saveSessions();
        renderCurrent();
        inputField.setText(prompt);
        inputField.setSelection(prompt.length());
        handleSend();
    }

    /** Cari obrolan berdasarkan judul atau isi pesan. Sheet ini juga dibuka dari ikon search di header. */
    private void showSearchSheet() {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;
        final EditText q = new EditText(this);
        q.setHint("Cari obrolan...");
        q.setHintTextColor(Theme.ON_VAR);
        q.setTextColor(Theme.ON);
        q.setTextSize(15);
        q.setSingleLine(true);
        q.setBackground(roundedBg(Theme.SURFACE, 14, Theme.LINE, 1));
        q.setPadding(dp(14), dp(12), dp(14), dp(12));
        Ui.tintCursor(q);
        box.addView(q, new LinearLayout.LayoutParams(-1, -2));

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, 0);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, dp(420)));

        final Runnable fill = () -> {
            list.removeAllViews();
            String qs = q.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<ChatSession> sorted = new ArrayList<>(sessions);
            Collections.sort(sorted, (a, b) -> Long.compare(chatTime(b), chatTime(a)));
            int shown = 0;
            for (final ChatSession s : sorted) {
                if (!qs.isEmpty() && !matchesChat(s, qs)) continue;
                TextView t = Ui.text(this, s.title, 15, Theme.ON, false);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(14), dp(12), dp(14), dp(12));
                t.setBackground(Ui.ripple(0, 14));
                t.setClickable(true);
                t.setOnClickListener(v -> {
                    sheet.dismiss();
                    current = s;
                    renderCurrent();
                    refreshHistory();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = dp(4);
                list.addView(t, lp);
                shown++;
            }
            if (shown == 0) {
                list.addView(Ui.text(this, "Tidak ada obrolan yang cocok.", 14, Theme.ON_VAR, false),
                        new LinearLayout.LayoutParams(-1, -2));
            }
        };
        q.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { fill.run(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        sheet.show();
        fill.run();
    }

    private boolean matchesChat(ChatSession s, String qs) {
        if (s.title != null && s.title.toLowerCase(Locale.ROOT).contains(qs)) return true;
        for (ChatMessage m : s.messages) {
            if (m.content != null && m.content.toLowerCase(Locale.ROOT).contains(qs)) return true;
        }
        return false;
    }

    /** Mode offline: pilih model dan tambah lampiran dinonaktifkan dengan warna disable. */
    private void applyOfflineState() {
        boolean off = modeChanger != null && modeChanger.isOffline();
        float a = off ? 0.38f : 1f;   // 38% opasitas = warna disable Material
        if (plusBtn != null) { plusBtn.setEnabled(!off); plusBtn.setAlpha(a); }
        if (modelPill != null) { modelPill.setEnabled(!off); modelPill.setAlpha(a); }
    }

    private View addAiMessage(final ChatMessage m, boolean animate) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(6), dp(2), dp(10));
        if (!m.steps.isEmpty()) box.addView(thinkingRow(m.steps), new LinearLayout.LayoutParams(-1, -2));

        final TextView tv = new TextView(this);
        tv.setTextColor(Theme.ON);
        tv.setTextSize(15.5f);
        tv.setLineSpacing(dp(4), 1f);
        tv.setTextIsSelectable(true);
        tv.setMovementMethod(new OutputHelper.LinkMovement());
        tv.setPadding(dp(2), dp(8), dp(2), 0);
        box.addView(tv, new LinearLayout.LayoutParams(-1, -2));

        final List<String[]> boxes = new ArrayList<>();
        final String body = extractCodeBoxes(m.content, boxes);
        final String fullPlain = body; // untuk streaming kita pakai plain dulu, format di akhir

        // Container ekstra (codebox + attach) ditambahkan setelah streaming selesai
        final LinearLayout extra = new LinearLayout(this);
        extra.setOrientation(LinearLayout.VERTICAL);
        box.addView(extra, new LinearLayout.LayoutParams(-1, -2));

        chatContainer.addView(box, new LinearLayout.LayoutParams(-1, -2));

        if (!animate || fullPlain.isEmpty()) {
            tv.setText(OutputHelper.format(fullPlain, mdStyle()), TextView.BufferType.SPANNABLE);
            appendAiExtras(extra, boxes, m.attach);
            addAiActions(extra, m);
            scrollToBottom();
            return box;
        }

        // Streaming ala Claude: teks biasa dianimasikan; markdown diformat penuh di akhir
        tv.setText("");
        final long token = ++streamToken;
        final String[] words = fullPlain.trim().isEmpty() ? new String[0] : fullPlain.split("\\s+");
        final java.util.Random rnd = new java.util.Random();
        final int[] idx = {0};
        final StringBuilder acc = new StringBuilder();

        if (words.length == 0) {
            tv.setText(OutputHelper.format(fullPlain, mdStyle()), TextView.BufferType.SPANNABLE);
            appendAiExtras(extra, boxes, m.attach);
            addAiActions(extra, m);
            scrollToBottom();
            if (busy) { busy = false; updateSendButton(); }
            return box;
        }

        streamRunnable = new Runnable() {
            @Override public void run() {
                if (token != streamToken) return; // dibatalkan
                if (idx[0] >= words.length) {
                    // selesai: format markdown penuh + tampilkan codebox/attach
                    tv.setText(OutputHelper.format(fullPlain, mdStyle()), TextView.BufferType.SPANNABLE);
                    appendAiExtras(extra, boxes, m.attach);
            addAiActions(extra, m);
                    scrollIfNear();
                    // animasi selesai, pastikan busy sudah false
                    if (busy) {
                        busy = false;
                        updateSendButton();
                    }
                    return;
                }
                int n = 2 + rnd.nextInt(2); // 2 atau 3
                if (rnd.nextInt(8) == 0) n = 4 + rnd.nextInt(4); // kadang 4–7
                n = Math.min(n, words.length - idx[0]);
                for (int i = 0; i < n; i++) {
                    if (acc.length() > 0) acc.append(' ');
                    acc.append(words[idx[0]++]);
                }
                // tampilkan sementara sebagai plain text (cepat)
                tv.setText(acc.toString());
                scrollIfNear();
                int delay = 28 + rnd.nextInt(45); // 28–72 ms
                handler.postDelayed(this, delay);
            }
        };
        handler.post(streamRunnable);
        return box;
    }

    private void appendAiExtras(LinearLayout extra, List<String[]> boxes, String attach) {
        extra.removeAllViews();
        for (String[] bx : boxes) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = dp(8);
            extra.addView(codeBox(bx[0], bx[1]), bp);
        }
        if (attach != null) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(8);
            extra.addView(attachCard(attach), cp);
        }
    }

    private void cancelStream() {
        streamToken++;
        if (streamRunnable != null) {
            handler.removeCallbacks(streamRunnable);
            streamRunnable = null;
        }
    }

    /** Kartu berkas seperti di Claude: ketuk untuk membuka pratinjau. Unduh tersedia di dalam pratinjau. */
    private View attachCard(final String path) {
        final File f = resolveAny(path);
        final boolean exists = f.isFile() && (isSandboxed(f) || shell.isAllowedPath(f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = Ui.rect(Theme.SURFACE, 16);
        bg.setStroke(dp(1), Theme.LINE);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE), bg, null));
        card.setClickable(true);

        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "FILE";
        if (ext.length() > 4) ext = "FILE";
        TextView badge = Ui.text(this, ext, 11, Theme.ON_VAR, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rect(Theme.SURFACE_HIGHEST, 10));
        card.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(56)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, name, 15, Theme.ON, false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView sub = Ui.text(this, exists ? ext + " · " + sizeText(f.length()) : "File tidak ditemukan",
                12, Theme.ON_VAR, false);
        texts.addView(title);
        texts.addView(sub);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(12);
        card.addView(texts, tl);

        card.setOnClickListener(v -> {
            if (!exists) { M3.toast(this, "File tidak ditemukan"); return; }
            showFileViewer(f);
        });
        return card;
    }

    private static String sizeText(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", b / 1048576.0);
    }

    /** Baris ringkas di atas jawaban. Ketuk untuk membuka daftar pikiran (bottom sheet). */
    private View thinkingRow(final List<String> steps) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(10), dp(8));
        row.setBackground(Ui.ripple(0, 12));
        row.setClickable(true);

        row.addView(new MonaOrb(this, false), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView t = Ui.text(this, "Proses berpikir", 14, Theme.ON_VAR, false);
        t.setPadding(dp(8), 0, dp(8), 0);
        row.addView(t, new LinearLayout.LayoutParams(-2, -2));
        TextView n = Ui.text(this, summaryOf(steps), 12, Theme.ON_VAR, false);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.text(this, "›", 22, Theme.ON_VAR, false));

        row.setOnClickListener(v -> showTimelineSheet(steps));
        return row;
    }

    /** Bottom sheet "Pikiran": daftar langkah proses untuk satu balasan. */
    private void showThinkingSheet(List<String> steps) {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;

        TextView title = Ui.text(this, "Proses berpikir", 20, Theme.ON, true);
        title.setPadding(dp(4), 0, dp(4), dp(2));
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = Ui.text(this, steps.size() + " langkah", 13, Theme.ON_VAR, false);
        sub.setPadding(dp(4), 0, dp(4), dp(12));
        box.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < steps.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(4), dp(10), dp(4), dp(10));

            TextView num = Ui.text(this, String.valueOf(i + 1), 12, Theme.ON_PRIM_CONT, true);
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.rect(Theme.PRIM_CONT, 12));
            row.addView(num, new LinearLayout.LayoutParams(dp(24), dp(24)));

            TextView t = Ui.text(this, steps.get(i), 14, Theme.ON, false);
            t.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = dp(12);
            row.addView(t, tl);
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1,
        java.lang.Math.min(dp(440), dp(52) * steps.size() + dp(8))));
        sheet.show();
    }

    /**
     * Langkah disimpan sebagai string. Format khusus:
     * "file|Membuat nama.txt|/path" dan "shell|perintah|keluaran". Lainnya = langkah biasa.
     */
    private String summaryOf(List<String> steps) {
        List<String> names = new ArrayList<>();
        int cmds = 0;
        for (String s : steps) {
            if (s.startsWith("file|")) {
                String[] parts = s.split("\\|", 3);
                names.add(parts.length > 1 ? parts[1].replace("Membuat ", "") : "berkas");
            } else if (s.startsWith("shell|")) {
                cmds++;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!names.isEmpty()) sb.append("Membuat ").append(TextUtils.join(", ", names));
        if (cmds > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("menjalankan ").append(cmds).append(" perintah");
        }
        if (sb.length() > 0) sb.append(" · ");
        sb.append(steps.size()).append(" langkah");
        return sb.toString();
    }

    private static String pick(String... opts) {
        return opts[new java.util.Random().nextInt(opts.length)];
    }

    /** Rencana langkah dari teks AI: baris bernomor/berbutir sebelum blok kode, dan baris "Selanjutnya: ...". */
    private List<String> planStepsFrom(String reply) {
        List<String> out = new ArrayList<>();
        String head = reply.split("```", 2)[0];
        for (String line : head.split("\\n")) {
            String t = line.trim();
            Matcher m = Pattern.compile("^(?:\\d+[.)]|[-*•])\\s+(.+)$").matcher(t);
            if (m.find()) out.add("Rencana: " + m.group(1).trim());
            else if (t.matches("(?i)^(selanjutnya|langkah berikutnya|next)\\b.*")) out.add(t);
            if (out.size() >= 8) break;
        }
        return out;
    }

    /** Lokasi berkas yang tepat, atau berkas sandbox dengan nama yang sama setelah dibersihkan (spasi jadi _, akhiran (1)). */
    private File resolveAny(String path) {
        File f = resolveFile(path);
        if (f.isFile()) return f;
        String key = sanitizeName(new File(path).getName());
        for (File g : sandboxFiles()) {
            String gn = sanitizeName(g.getName());
            if (gn.equalsIgnoreCase(key) || stripCounter(gn).equalsIgnoreCase(key)) return g;
        }
        return f;
    }

    private static String sanitizeName(String n) {
        return n.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private static String stripCounter(String n) {
        return n.replaceAll("_?\\(\\d+\\)(\\.[^.]*)?$", "$1");
    }

    /** Bottom sheet ala Claude: urutan langkah (file dan perintah shell) sebagai timeline. */
    /** Sheet "Berpikir" yang realtime: langkah bertambah otomatis selama permintaan masih berjalan. */
    private void showLiveTimeline() {
        final Sheet sheet = new Sheet(this, true);
        final LinearLayout box = sheet.panel;

        final TextView title = Ui.text(this, "Berpikir…", 18, Theme.ON, true);
        title.setPadding(dp(4), 0, dp(4), dp(4));
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        final TextView sub = Ui.text(this, "", 13, Theme.ON_VAR, false);
        sub.setPadding(dp(4), 0, dp(4), dp(8));
        box.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        final android.widget.ProgressBar bar = new android.widget.ProgressBar(this);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(ColorStateList.valueOf(Theme.PRIMARY));
        box.addView(bar, new LinearLayout.LayoutParams(-1, dp(3)));

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, 0);
        final ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, dp(460)));
        sheet.show();

        final Runnable[] tick = new Runnable[1];
        final String[] lastSig = { "" };
        tick[0] = () -> {
            if (!list.isAttachedToWindow()) return;   // sheet sudah ditutup: berhenti memperbarui
            List<String> src = liveSteps;
            List<String> snap = src == null ? new ArrayList<String>() : new ArrayList<>(src);
            boolean working = jobRunning;
            // Bangun ulang daftar hanya jika ada perubahan, dan jaga posisi gulir:
            // ikut turun hanya jika pengguna sudah di bawah; kalau sedang membaca di atas, posisinya tidak diubah.
            String sig = snap.size() + "|" + working + "|" + (working ? (System.currentTimeMillis() - jobStartedAt) / 1000 : 0);
            if (!sig.equals(lastSig[0])) {
                lastSig[0] = sig;
                final int y = sv.getScrollY();
                final boolean atBottom = y + sv.getHeight() >= list.getHeight() - dp(40);
                list.removeAllViews();
                addTimelineItems(list, snap, working);
                sv.post(() -> {
                    if (atBottom) sv.fullScroll(View.FOCUS_DOWN);
                    else sv.scrollTo(0, y);
                });
            }
            title.setText(working ? "Berpikir…" : "Selesai berpikir");
            sub.setText(snap.size() + " langkah" + (working ? " · sedang berjalan" : ""));
            bar.setVisibility(working ? View.VISIBLE : View.GONE);
            if (working) handler.postDelayed(tick[0], 300);
        };
        tick[0].run();
    }

    /** Isi daftar langkah (dipakai sheet berpikir). Saat masih berjalan, ditambah baris waktu tunggu. */
    private void addTimelineItems(LinearLayout list, List<String> steps, boolean working) {
        for (int i = 0; i < steps.size(); i++) {
            String raw = steps.get(i);
            String kind = "step", a = raw, b = "";
            if (raw.startsWith("file|") || raw.startsWith("shell|") || raw.startsWith("think|")) {
                String[] parts = raw.split("\\|", 3);
                kind = parts[0];
                a = parts.length > 1 ? parts[1] : "";
                b = parts.length > 2 ? parts[2] : "";
            }
            list.addView(timelineItem(kind, a, b, !working && i == steps.size() - 1 ? true : false),
            new LinearLayout.LayoutParams(-1, -2));
        }
        if (working) {
            long sec = (System.currentTimeMillis() - jobStartedAt) / 1000;
            list.addView(timelineItem("step", "Menunggu balasan model · " + sec + " dtk", "", true),
            new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void showTimelineSheet(List<String> steps) {
        final Sheet sheet = new Sheet(this, true);
        LinearLayout box = sheet.panel;

        TextView title = Ui.text(this, summaryOf(steps), 18, Theme.ON, true);
        title.setPadding(dp(4), 0, dp(4), dp(14));
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < steps.size(); i++) {
            String raw = steps.get(i);
            String kind = "step", a = raw, b = "";
            if (raw.startsWith("file|") || raw.startsWith("shell|") || raw.startsWith("think|")) {
                String[] parts = raw.split("\\|", 3);
                kind = parts[0];
                a = parts.length > 1 ? parts[1] : "";
                b = parts.length > 2 ? parts[2] : "";
            }
            list.addView(timelineItem(kind, a, b, i == steps.size() - 1),
            new LinearLayout.LayoutParams(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1,
        java.lang.Math.min(dp(520), dp(60) * steps.size() + dp(60))));
        sheet.show();
    }

    /** Satu baris timeline: penanda di kiri (dengan garis ke langkah berikutnya), isi di kanan. */
    private View timelineItem(String kind, String a, String b, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        View dot;
        if ("shell".equals(kind)) {
            ImageView term = new ImageView(this);
            term.setImageResource(ICON_TERMINAL);
            term.setColorFilter(Theme.ON_VAR, PorterDuff.Mode.SRC_IN);
            term.setPadding(dp(6), dp(6), dp(6), dp(6));
            dot = term;
        } else {
            String glyph = "file".equals(kind) ? "▤" : "•";
            TextView t = Ui.text(this, glyph, 12, Theme.ON_VAR, true);
            t.setGravity(Gravity.CENTER);
            dot = t;
        }
        dot.setBackground(Ui.rect(Theme.SURFACE_HIGHEST, 14));
        rail.addView(dot, new LinearLayout.LayoutParams(dp(28), dp(28)));
        if (!last) {
            View line = new View(this);
            line.setBackgroundColor(M3.alpha(Theme.OUTLINE, 0.6f));
            rail.addView(line, new LinearLayout.LayoutParams(dp(2), 0, 1f));
        }
        row.addView(rail, new LinearLayout.LayoutParams(dp(28), -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(3), dp(2), dp(16));
        if ("shell".equals(kind)) {
            col.addView(Ui.text(this, "Menjalankan perintah", 15, Theme.ON, false));
            col.addView(codeBlock(a), topMargin(6));
            if (!b.isEmpty()) col.addView(codeBlock(b), topMargin(6));
        } else if ("think".equals(kind)) {
            col.addView(Ui.text(this, a, 15, Theme.ON, false));
            col.addView(Ui.text(this, b, 13, Theme.ON_VAR, false), topMargin(4));
        } else if ("file".equals(kind)) {
            col.addView(Ui.text(this, a, 15, Theme.ON, false));
            col.addView(Ui.text(this, b, 12, Theme.ON_VAR, false), topMargin(2));
        } else {
            col.addView(Ui.text(this, a, 14, Theme.ON, false));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private View codeBlock(String text) {
        TextView t = new TextView(this);
        String shown = text.length() > 800 ? text.substring(0, 800) + "…" : text;
        t.setText(shown);
        t.setTextColor(Theme.ON);
        t.setTextSize(12);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setBackground(roundedBg(Theme.SURFACE_HIGHEST, 10, 0, 0));
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        return t;
    }

    private LinearLayout.LayoutParams topMargin(int dpTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(dpTop);
        return lp;
    }

    /** Indikator saat menunggu: bintang Claude berputar + "Berpikir…" + langkah yang sedang jalan. */
    /** Langkah proses dari permintaan yang sedang berjalan (untuk sheet saat indikator diketuk). */
    private List<String> liveSteps;
    /** Permintaan AI sedang berjalan (untuk sheet berpikir yang realtime). */
    private boolean jobRunning;
    private long jobStartedAt;

    private class ThinkingLive extends LinearLayout {
        private final TextView label, step;
        private ObjectAnimator pulse;
        private final Handler th = new Handler(Looper.getMainLooper());
        private final java.util.Random rnd = new java.util.Random();
        private String phrase = "";
        private int pos = 0;
        private boolean erasing = false;
        private boolean running = false;

        ThinkingLive(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(4), dp(10), dp(4), dp(10));
            setClickable(true);
            setBackground(Ui.ripple(0, 12));
            setOnClickListener(v -> showLiveTimeline());
            addView(new MonaOrb(c, true), new LinearLayout.LayoutParams(dp(22), dp(22)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            label = Ui.text(c, "Berpikir…", 15, Theme.ON, false);
            step = Ui.text(c, "", 12, Theme.ON_VAR, false);
            col.addView(label);
            col.addView(step);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.leftMargin = dp(10);
            addView(col, cp);
        }

        /** Langkah nyata tetap dicatat di sheet proses; baris ini menampilkan efek mengetik. */
        void setStep(String s) { }

        private String nextPhrase() {
            int n = 2 + rnd.nextInt(3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(' ');
                sb.append(THINK_WORDS[rnd.nextInt(THINK_WORDS.length)]);
            }
            return sb.append('…').toString();
        }

        private final Runnable typer = new Runnable() {
            @Override public void run() {
                if (!running) return;
                if (!erasing) {
                    if (pos < phrase.length()) {
                        pos++;
                        step.setText(phrase.substring(0, pos));
                        th.postDelayed(this, 42);
                    } else {
                        erasing = true;
                        th.postDelayed(this, 1100);
                    }
                } else {
                    if (pos > 0) {
                        pos--;
                        step.setText(phrase.substring(0, pos));
                        th.postDelayed(this, 18);
                    } else {
                        erasing = false;
                        phrase = nextPhrase();
                        th.postDelayed(this, 250);
                    }
                }
            }
        };

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            pulse = ObjectAnimator.ofFloat(label, "alpha", 0.4f, 1f);
            pulse.setDuration(900);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.start();
            running = true;
            phrase = nextPhrase();
            pos = 0;
            erasing = false;
            th.post(typer);
        }

        @Override protected void onDetachedFromWindow() {
            running = false;
            th.removeCallbacks(typer);
            if (pulse != null) { pulse.cancel(); pulse = null; }
            super.onDetachedFromWindow();
        }
    }

    private View addTypingMessage() {
        typingLive = new ThinkingLive(this);
        chatContainer.addView(typingLive, new LinearLayout.LayoutParams(-1, -2));
        scrollToBottom();
        return typingLive;
    }

    private void removeTypingRow() {
        if (typingRow != null) chatContainer.removeView(typingRow);
        typingRow = null;
        typingLive = null;
    }

    private OutputHelper.Style mdStyle() {
        return new OutputHelper.Style(Theme.ON, Theme.ON_VAR, Theme.PRIMARY, Theme.SURFACE_HIGH,
        Theme.dark ? 0xFFE8A27F : 0xFF8A3B1F, getResources().getDisplayMetrics().density);
    }

    /** Scroll murni (tanpa fullScroll) supaya fokus tidak berpindah dari kolom input. */
    private void scrollToBottom() {
        scrollView.post(() -> {
            View c = scrollView.getChildAt(0);
            if (c != null) scrollView.scrollTo(0, c.getBottom());
        });
    }

    private void scrollToBottomSmooth() {
        scrollView.post(() -> {
            View c = scrollView.getChildAt(0);
            if (c != null) scrollView.smoothScrollTo(0, c.getBottom());
        });
    }

    /** True jika posisi chat berada dekat bagian paling bawah (toleransi slack px). */
    private boolean isNearBottom(int slack) {
        View c = scrollView == null ? null : scrollView.getChildAt(0);
        if (c == null) return true;
        int max = java.lang.Math.max(0, c.getHeight() - scrollView.getHeight());
        return scrollView.getScrollY() >= max - slack;
    }

    /** Ikut turun hanya bila pengguna sedang di bawah; jika sedang membaca di atas, posisinya dibiarkan. */
    private void scrollIfNear() {
        if (isNearBottom(dp(120))) scrollToBottom();
    }

    private TextView jumpDown;
    private boolean jumpShown;

    private TextView buildJumpDown() {
        TextView t = new TextView(this);
        t.setText("\u2193");
        t.setTextSize(20);
        t.setTextColor(Theme.ON);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundedBg(Theme.SURFACE_HIGH, 100, Theme.LINE, 1));
        t.setElevation(dp(3));
        t.setContentDescription("Ke pesan terbaru");
        t.setAlpha(0f);
        t.setVisibility(View.GONE);
        t.setOnClickListener(v -> scrollToBottomSmooth());
        return t;
    }

    /** Tombol turun muncul di atas input saat chat tidak sedang di bawah, lalu hilang lagi. */
    private void updateJumpButton() {
        if (jumpDown == null || scrollView == null) return;
        boolean show = settingsScreen == null && chatContainer != null && chatContainer.getChildCount() > 0
                && !isNearBottom(dp(160));
        if (show == jumpShown) return;
        jumpShown = show;
        jumpDown.animate().cancel();
        if (show) {
            jumpDown.setVisibility(View.VISIBLE);
            jumpDown.animate().alpha(1f).translationY(0f).setDuration(200)
            .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            jumpDown.animate().alpha(0f).translationY(dp(12)).setDuration(160)
            .withEndAction(() -> { if (!jumpShown) jumpDown.setVisibility(View.GONE); }).start();
        }
    }

    private void keepInputFocus() {
        imeSticky = true;
        final Runnable r = () -> {
            if (isFinishing() || inputField == null || !imeSticky) return;
            if (settingsScreen != null || (drawerLayout != null && drawerLayout.isOpen())) return;
            if (!inputField.hasFocus()) inputField.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(inputField, 0);
        };
        inputField.post(r);
        inputField.postDelayed(r, 120);   // jaga-jaga jika IME sempat menutup saat layout berubah
    }

    // =====================================================================
    //  HELPER UI
    // =====================================================================
    private ImageButton iconButton(int res, int tint, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable bg = attrDrawable(android.R.attr.selectableItemBackgroundBorderless);
        if (bg != null) b.setBackground(bg); else b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));

        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(params);

        b.setOnClickListener(l);
        return b;
    }

    /** Ikon header (menu, chat baru, more): ripple kustom inoripple10, bukan ripple Material bawaan. */
    private ImageButton headerIcon(int res, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(Theme.ON_VAR, PorterDuff.Mode.SRC_IN);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.setBackground(circleRipple());
        b.setOnClickListener(l);
        return b;
    }

    /** Ripple bulat dan lembut, dipakai tombol header (menu, chat baru, more). */
    private RippleDrawable circleRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(M3.alpha(Theme.ON_VAR, 0.16f)), null, mask);
    }

    private TextView makeAvatar(String letter, int textSize, int color) {
        TextView t = new TextView(this);
        t.setText(letter);
        t.setTextColor(Color.WHITE);
        t.setTextSize(textSize);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        t.setBackground(circle);
        return t;
    }

    private void styleField(EditText e, String hint) {
        e.setHint(hint);
        e.setHintTextColor(Theme.ON_VAR);
        e.setTextColor(Theme.ON);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setBackground(roundedBg(Theme.SURFACE, 10, Theme.LINE, 1));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
    }

    private TextView button(String text, boolean filled) {
        TextView t = Ui.text(this, text, 14, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(11), dp(18), dp(11));
        t.setClickable(true);
        if (filled) {
            t.setTextColor(Theme.ON_PRIMARY);
            t.setBackground(rippled(roundedBg(Theme.PRIMARY, 14, 0, 0)));
        } else {
            t.setTextColor(Theme.ON);
            t.setBackground(rippled(roundedBg(Theme.SURFACE, 14, Theme.LINE, 1)));
        }
        return t;
    }

    /** Bungkus latar dengan efek ripple (riak sentuh) warna Mona. */
    private Drawable rippled(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(Theme.RIPPLE), content, null);
    }

    private GradientDrawable roundedBg(int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(fill);
        bg.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) bg.setStroke(dp(strokeDp), strokeColor);
        return bg;
    }

    private Drawable attrDrawable(int attr) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true) && tv.resourceId != 0) return getDrawable(tv.resourceId);
        return null;
    }

    private void hideKeyboard() {
        if (inputField == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(inputField.getWindowToken(), 0);
    }

    private String shorten(String s, int max) {
        s = s.replace('\n', ' ').trim();
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private int dp(int value) {
        return java.lang.Math.round(getResources().getDisplayMetrics().density * value);
    }
}
