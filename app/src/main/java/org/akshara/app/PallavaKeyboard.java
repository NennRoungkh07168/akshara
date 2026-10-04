package org.akshara.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Akshara Pallava keyboard: a system-wide Android keyboard.
 * Types standard Grantha Unicode (U+11300 block), shown on the keys
 * in the Akshara Pallava font. Vowel keys add a vowel sign after a
 * consonant and an independent vowel elsewhere.
 */
public class PallavaKeyboard extends InputMethodService {

    private static final int BG = Color.rgb(17, 22, 31);
    private static final int KEY = Color.rgb(36, 45, 66);
    private static final int KEY_PRESSED = Color.rgb(62, 75, 105);
    private static final int KEY_CTRL = Color.rgb(26, 33, 48);
    private static final int FG = Color.rgb(230, 233, 240);
    private static final int MUTED = Color.rgb(154, 163, 184);
    private static final int GOLD = Color.rgb(216, 174, 85);

    // Characters in the Akshara Pallava font (traced from the Aksharamukha Pallava alphabet).
    // Everything else falls back to a Grantha font and is shown in gold.
    private static final int[] IN_FONT = {0x11315, 0x11316, 0x11317, 0x11318, 0x11319, 0x1131A, 0x1131B,
            0x1131C, 0x1131D, 0x1131E, 0x1131F, 0x11320, 0x11321, 0x11322, 0x11323, 0x11324, 0x11325,
            0x11326, 0x11327, 0x11328, 0x1132A, 0x1132B, 0x1132C, 0x1132D, 0x1132E, 0x1132F, 0x11330,
            0x11332, 0x11335, 0x11336, 0x11337, 0x11338, 0x11339};

    private static final String[][] PAGE1_LAT = {
            {"ka", "kha", "ga", "gha", "ṅa"},
            {"ca", "cha", "ja", "jha", "ña"},
            {"ṭa", "ṭha", "ḍa", "ḍha", "ṇa"},
            {"ta", "tha", "da", "dha", "na"},
            {"pa", "pha", "ba", "bha", "ma"}};
    private static final int[][] PAGE1_CP = {
            {0x11315, 0x11316, 0x11317, 0x11318, 0x11319},
            {0x1131A, 0x1131B, 0x1131C, 0x1131D, 0x1131E},
            {0x1131F, 0x11320, 0x11321, 0x11322, 0x11323},
            {0x11324, 0x11325, 0x11326, 0x11327, 0x11328},
            {0x1132A, 0x1132B, 0x1132C, 0x1132D, 0x1132E}};

    private static final String[] VOWEL_LAT = {"a", "ā", "i", "ī", "u", "ū", "e", "ai", "o", "au"};
    private static final int[] VOWEL_IND = {0x11305, 0x11306, 0x11307, 0x11308, 0x11309, 0x1130A,
            0x1130F, 0x11310, 0x11313, 0x11314};
    private static final int[] VOWEL_SIGN = {0, 0x1133E, 0x1133F, 0x11340, 0x11341, 0x11342,
            0x11347, 0x11348, 0x1134B, 0x1134C};

    private static final int VIRAMA = 0x1134D, ANUSVARA = 0x11302, VISARGA = 0x11303;

    private Typeface pallava;
    private LinearLayout root;
    private boolean page2 = false;
    private EditorInfo editorInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable repeater;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            pallava = Typeface.createFromAsset(getAssets(), "www/AksharaPallava.ttf");
        } catch (Exception e) {
            pallava = Typeface.DEFAULT;
        }
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(3), dp(4), dp(3), dp(6));
        build();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        editorInfo = info;
        if (!restarting) page2 = false;
        if (root != null) build();
    }

    // ---------- layout ----------

    private void build() {
        root.removeAllViews();
        if (!page2) {
            for (int r = 0; r < PAGE1_CP.length; r++) {
                LinearLayout row = newRow();
                for (int c = 0; c < PAGE1_CP[r].length; c++) {
                    addCharKey(row, PAGE1_CP[r][c], PAGE1_LAT[r][c], 1f);
                }
                root.addView(row);
            }
        } else {
            LinearLayout r1 = newRow();
            addCharKey(r1, 0x1132F, "ya", 1f);
            addCharKey(r1, 0x11330, "ra", 1f);
            addCharKey(r1, 0x11332, "la", 1f);
            addCharKey(r1, 0x11335, "va", 1f);
            addCharKey(r1, 0x11336, "śa", 1f);
            root.addView(r1);

            LinearLayout r2 = newRow();
            addCharKey(r2, 0x11337, "ṣa", 1f);
            addCharKey(r2, 0x11338, "sa", 1f);
            addCharKey(r2, 0x11339, "ha", 1f);
            addCharKey(r2, 0x11333, "ḷa", 1f);
            addTextKey(r2, "।", "daṇḍa", "\u0964", 1f);
            root.addView(r2);

            for (int half = 0; half < 2; half++) {
                LinearLayout rv = newRow();
                for (int i = half * 5; i < half * 5 + 5; i++) {
                    final int idx = i;
                    addKey(rv, cp(VOWEL_IND[i]), VOWEL_LAT[i], inFont(VOWEL_IND[i]) ? FG : GOLD, 1f,
                            v -> typeVowel(idx));
                }
                root.addView(rv);
            }

            LinearLayout r5 = newRow();
            addTextKey(r5, "◌" + cp(VIRAMA), "virāma", cp(VIRAMA), 1f);
            addTextKey(r5, "◌" + cp(ANUSVARA), "ṃ", cp(ANUSVARA), 1f);
            addTextKey(r5, "◌" + cp(VISARGA), "ḥ", cp(VISARGA), 1f);
            addTextKey(r5, "॥", "double daṇḍa", "\u0965", 1f);
            addTextKey(r5, "ZW", "no join", "\u200C", 1f);
            root.addView(r5);
        }

        LinearLayout ctrl = newRow();
        addKey(ctrl, page2 ? cp(0x11315) + "…" : cp(0x1132F) + "…", page2 ? "ka–ma" : "ya–ha, vowels", FG, 1.4f, v -> {
            page2 = !page2;
            build();
        });
        addKey(ctrl, "🌐", "keyboards", FG, 1f, v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        addKey(ctrl, "", "space", FG, 3f, v -> commit(" "));
        View bk = addKey(ctrl, "⌫", "", FG, 1.3f, null);
        setupRepeat(bk);
        addKey(ctrl, "⏎", "", FG, 1.3f, v -> enter());
        root.addView(ctrl);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addCharKey(LinearLayout row, final int codepoint, String latin, float weight) {
        addKey(row, cp(codepoint), latin, inFont(codepoint) ? FG : GOLD, weight, v -> typeConsonant(codepoint));
    }

    private void addTextKey(LinearLayout row, String label, String latin, final String text, float weight) {
        addKey(row, label, latin, GOLD, weight, v -> commit(text));
    }

    private View addKey(LinearLayout row, String label, String latin, int color, float weight,
                        View.OnClickListener click) {
        LinearLayout key = new LinearLayout(this);
        key.setOrientation(LinearLayout.VERTICAL);
        key.setGravity(Gravity.CENTER);
        key.setBackground(keyBackground(latin.isEmpty() || "space".equals(latin) || "keyboards".equals(latin)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), weight);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        key.setLayoutParams(lp);

        TextView main = new TextView(this);
        main.setText(label);
        main.setTypeface(pallava);
        main.setTextColor(color);
        main.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() > 3 ? 15 : 22);
        main.setGravity(Gravity.CENTER);
        main.setIncludeFontPadding(false);
        key.addView(main);

        if (!latin.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(latin);
            sub.setTextColor(MUTED);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            sub.setGravity(Gravity.CENTER);
            sub.setSingleLine(true);
            key.addView(sub);
        }
        if (click != null) {
            key.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                click.onClick(v);
            });
        }
        key.setContentDescription(latin.isEmpty() ? label : latin);
        row.addView(key);
        return key;
    }

    private StateListDrawable keyBackground(boolean control) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(control ? KEY_CTRL : KEY);
        normal.setCornerRadius(dp(8));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(KEY_PRESSED);
        pressed.setCornerRadius(dp(8));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    private void setupRepeat(View key) {
        key.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    backspace();
                    repeater = new Runnable() {
                        @Override
                        public void run() {
                            backspace();
                            handler.postDelayed(this, 60);
                        }
                    };
                    handler.postDelayed(repeater, 400);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (repeater != null) handler.removeCallbacks(repeater);
                    repeater = null;
                    return true;
                default:
                    return true;
            }
        });
    }

    // ---------- typing ----------

    private void typeConsonant(int codepoint) {
        commit(cp(codepoint));
    }

    private void typeVowel(int idx) {
        if (consonantBefore()) {
            if (idx == 0) return; // inherent a: nothing to add
            commit(cp(VOWEL_SIGN[idx]));
        } else {
            commit(cp(VOWEL_IND[idx]));
        }
    }

    private boolean consonantBefore() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (before == null || before.length() == 0) return false;
        int last = Character.codePointBefore(before, before.length());
        return last >= 0x11315 && last <= 0x11339;
    }

    private void commit(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    private void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            ic.commitText("", 1);
        } else {
            ic.deleteSurroundingTextInCodePoints(1, 0);
        }
    }

    private void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        EditorInfo ei = editorInfo != null ? editorInfo : getCurrentInputEditorInfo();
        if (ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            boolean multiLine = (ei.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            if (!noEnterAction && !multiLine && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    // ---------- helpers ----------

    private static String cp(int codepoint) {
        return new String(Character.toChars(codepoint));
    }

    private static boolean inFont(int codepoint) {
        for (int c : IN_FONT) if (c == codepoint) return true;
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
