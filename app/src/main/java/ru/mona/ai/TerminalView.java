package ru.mona.ai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Layar terminal ala Termux. Output bisa dipilih lalu disalin (tahan lalu geser, atau tombol SALIN).
 * Prompt dan input ada tepat di bawah output, jadi perintah ditulis di tempat yang sama dengan hasilnya.
 */
final class TerminalView extends ScrollView {

    /** Dipanggil saat pengguna menekan kirim pada baris input. */
    interface Listener {
        void onSubmit(String cmd);
    }

    private final Activity host;
    private final Listener listener;
    private final TextView screen;
    private final TextView prompt;
    private final EditText input;

    TerminalView(Activity a, Listener l) {
        super(a);
        host = a;
        listener = l;
        setBackgroundColor(Theme.BG);
        setFillViewport(true);

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);

        screen = new TextView(a);
        screen.setTypeface(Typeface.MONOSPACE);
        screen.setTextSize(13);
        screen.setTextColor(Theme.ON);
        screen.setPadding(dp(16), dp(12), dp(16), dp(4));
        screen.setTextIsSelectable(true);
        body.addView(screen, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(2), dp(16), dp(16));

        prompt = new TextView(a);
        prompt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        prompt.setTextSize(13);
        prompt.setTextColor(Theme.PRIMARY);
        prompt.setSingleLine(true);
        row.addView(prompt, new LinearLayout.LayoutParams(-2, -2));

        input = new EditText(a);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(13);
        input.setTextColor(Theme.ON);
        input.setHintTextColor(Theme.ON_VAR);
        input.setBackground(null);
        input.setPadding(dp(4), 0, 0, 0);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        Ui.tintCursor(input);   // kursor oranye Mona
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND || id == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));

        addView(body, new FrameLayout.LayoutParams(-1, -2));

        // Ketuk di mana saja (layar, baris prompt, area kosong): langsung fokus dan memunculkan keyboard.
        // TextView yang bisa dipilih menelan sentuhan, jadi setiap lapisan diberi listener sendiri.
        View.OnClickListener focus = v -> focusInput();
        setOnClickListener(focus);
        body.setOnClickListener(focus);
        row.setOnClickListener(focus);
        screen.setOnClickListener(focus);
        prompt.setOnClickListener(focus);
    }

    private int dp(float v) {
        return Ui.dp(v);
    }

    /** Tambah output di bawah, lalu turun ke baris terakhir. */
    void append(String t) {
        screen.append(t);
        scrollEnd();
    }

    void setOutput(String t) {
        screen.setText(t);
        scrollEnd();
    }

    void clear() {
        screen.setText("");
    }

    String output() {
        return screen.getText().toString();
    }

    void setPrompt(String p) {
        prompt.setText(p);
    }

    /** Sisipkan teks (misalnya TAB, ~, /) di posisi kursor pada baris input. */
    void insert(String t) {
        int pos = input.getSelectionStart();
        if (pos < 0) pos = input.length();
        Editable e = input.getText();
        e.insert(pos, t);
        input.setSelection(pos + t.length());
        input.requestFocus();
    }

    void focusInput() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    /** Salin seluruh output ke papan klip. */
    void copyOutput() {
        ClipboardManager cm = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("terminal", output()));
        M3.toast(host, "Output disalin");
    }

    private void submit() {
        String cmd = input.getText().toString();
        input.setText("");
        listener.onSubmit(cmd);
    }

    private void scrollEnd() {
        post(() -> fullScroll(View.FOCUS_DOWN));
    }
}
