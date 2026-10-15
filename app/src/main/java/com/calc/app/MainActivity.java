package com.calc.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import java.math.BigDecimal;
import java.util.Map;

public final class MainActivity extends AppCompatActivity {

    public static final String EXTRA_RESTORE = "restore_expr";

    private static final int TAB_BASIC = 0;
    private static final int TAB_SCI = 1;
    private static final int TAB_PROG = 2;
    private static final int TAB_CONV = 3;
    private static final int TAB_ADV = 4;

    private static final int[] PROG_BASES = {
            ProgrammerEngine.DEC, ProgrammerEngine.HEX, ProgrammerEngine.OCT, ProgrammerEngine.BIN
    };

    private Store store;
    private int currentTab = TAB_SCI;
    private int angleMode = Expr.DEG;
    private boolean shiftOn = false;
    private boolean hypOn = false;
    private double lastAnswer = 0;
    private boolean lastWasResult = false;

    private String currentExpr = "";

    private TextView tvExpr;
    private TextView tvResult;
    private TextView tvAngle;
    private TextView tvShiftDot;
    private TextView tvHypDot;
    private TextView tvMemDot;
    private HorizontalScrollView exprScroll;
    private View displayBox;

    private HorizontalScrollView tabScroll;
    private final TextView[] tabChips = new TextView[5];

    private FrameLayout panelContainer;
    private View basicKeypad, sciKeypad, progPanel, convPanel, advKeypad;
    private Button shiftKeyRef, hypKeyRef, degKeyRef;
    private BigDecimal lastBig = null;
    private BigDecimal pendingBig = null;
    private double pendingAns = 0;

    private int progBase = ProgrammerEngine.DEC;
    private String progEntry = "0";
    private long progAcc = 0;
    private String progPendingOp = null;
    private final View[] progBaseRows = new View[4];
    private final TextView[] progBaseLabels = new TextView[4];
    private final TextView[] progBaseValues = new TextView[4];
    private Button[] hexDigitButtons;

    private EditText etConvInput;
    private TextView tvConvResult;
    private Spinner spFrom, spTo;
    private TextView[] convChips;
    private int convCategory = 0;

    private ActivityResultLauncher<Intent> historyLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        store = new Store(this);
        Fmt.apply(store);
        angleMode = store.getAngleMode();
        currentTab = Math.max(0, Math.min(TAB_ADV, store.getLastTab()));
        progBase = store.getLastBase();

        historyLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getData() != null) {
                String restore = result.getData().getStringExtra(EXTRA_RESTORE);
                if (restore != null) {
                    currentExpr = restore;
                    showExpr(currentExpr);
                    livePreview();
                }
            }
        });

        buildUi();
        switchTab(currentTab);
        updateAngleLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Fmt.apply(store);
        angleMode = store.getAngleMode();
        updateAngleLabel();
        if (!lastWasResult) livePreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        store.setAngleMode(angleMode);
        store.setLastTab(currentTab);
        store.setLastBase(progBase);
    }

    private int c(int attr) { return Ui.attrColor(this, attr); }
    private int dp(int v) { return Ui.dp(this, v); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(R.attr.cBg));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setFitsSystemWindows(true);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        root.addView(buildTopBar());

        displayBox = buildDisplay();
        root.addView(displayBox);

        panelContainer = new FrameLayout(this);
        panelContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        basicKeypad = buildBasicKeypad();
        sciKeypad = buildSciKeypad();
        progPanel = buildProgPanel();
        convPanel = buildConvPanel();
        advKeypad = buildAdvKeypad();
        panelContainer.addView(basicKeypad);
        panelContainer.addView(sciKeypad);
        panelContainer.addView(progPanel);
        panelContainer.addView(convPanel);
        panelContainer.addView(advKeypad);

        root.addView(panelContainer);
        setContentView(root);
    }

    private FrameLayout.LayoutParams panelLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(12), dp(4), dp(12), dp(12));
        return lp;
    }

    // ---------- top bar ----------

    private View buildTopBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(16), dp(8), dp(16), dp(4));
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton btnHistory = iconButton(R.drawable.ic_history);
        btnHistory.setContentDescription(getString(R.string.action_history));
        btnHistory.setOnClickListener(v -> historyLauncher.launch(new Intent(this, HistoryActivity.class)));
        bar.addView(btnHistory);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(c(R.attr.cTextPrimary));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(title);

        ImageButton btnSettings = iconButton(R.drawable.ic_settings);
        btnSettings.setContentDescription(getString(R.string.action_settings));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(btnSettings);
        wrap.addView(bar);

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tabScroll.setFillViewport(true);
        LinearLayout.LayoutParams tsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        tsLp.topMargin = dp(10);
        tabScroll.setLayoutParams(tsLp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);

        String[] labels = {
                getString(R.string.tab_basic), getString(R.string.tab_sci), getString(R.string.tab_prog),
                getString(R.string.tab_conv), getString(R.string.tab_adv)
        };
        int[] ids = {TAB_BASIC, TAB_SCI, TAB_PROG, TAB_CONV, TAB_ADV};
        for (int i = 0; i < labels.length; i++) {
            final int tabId = ids[i];
            TextView chip = Ui.chip(this, labels[i]);
            chip.setOnClickListener(v -> switchTab(tabId));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            clp.setMargins(dp(3), 0, dp(3), 0);
            tabs.addView(chip, clp);
            tabChips[tabId] = chip;
        }
        tabScroll.addView(tabs, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wrap.addView(tabScroll);

        return wrap;
    }

    private ImageButton iconButton(int drawableRes) {
        ImageButton b = new ImageButton(this, null, 0, R.style.IconButton);
        b.setImageResource(drawableRes);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return b;
    }

    private void refreshTabPills() {
        for (int i = 0; i < tabChips.length; i++) {
            Ui.setChipState(tabChips[i], currentTab == i);
        }
        final TextView sel = tabChips[currentTab];
        tabScroll.post(() -> {
            int target = sel.getLeft() - (tabScroll.getWidth() - sel.getWidth()) / 2;
            tabScroll.smoothScrollTo(Math.max(0, target), 0);
        });
    }

    // ---------- display ----------

    private View buildDisplay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_display);
        box.setPadding(dp(24), dp(6), dp(24), dp(8));
        box.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout indicators = new LinearLayout(this);
        indicators.setOrientation(LinearLayout.HORIZONTAL);
        indicators.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        indicators.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvAngle = indicatorText(getString(R.string.deg));
        tvAngle.setTextColor(c(R.attr.cAccentText));
        tvAngle.setOnClickListener(v -> cycleAngle());
        tvShiftDot = indicatorText(getString(R.string.shift));
        tvShiftDot.setOnClickListener(v -> { shiftOn = !shiftOn; refreshIndicators(); });
        tvHypDot = indicatorText(getString(R.string.hyp));
        tvHypDot.setOnClickListener(v -> { hypOn = !hypOn; refreshIndicators(); });
        tvMemDot = indicatorText("M");
        tvMemDot.setClickable(false);

        indicators.addView(tvAngle);
        indicators.addView(tvShiftDot);
        indicators.addView(tvHypDot);
        indicators.addView(tvMemDot);
        box.addView(indicators);

        exprScroll = new HorizontalScrollView(this);
        exprScroll.setHorizontalScrollBarEnabled(false);
        exprScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        exprScroll.setFillViewport(true);
        LinearLayout.LayoutParams esLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        esLp.topMargin = dp(6);
        exprScroll.setLayoutParams(esLp);
        tvExpr = new TextView(this);
        tvExpr.setTextSize(22);
        tvExpr.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvExpr.setMinHeight(dp(30));
        tvExpr.setTextColor(c(R.attr.cTextSecondary));
        tvExpr.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvExpr.setSingleLine(true);
        tvExpr.setText("");
        exprScroll.addView(tvExpr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(exprScroll);

        tvResult = new TextView(this);
        tvResult.setTextColor(c(R.attr.cTextPrimary));
        tvResult.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvResult.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvResult.setText("0");
        tvResult.setMinHeight(dp(48));
        Ui.autoFit(tvResult, 28, 72);
        tvResult.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tvResult.setOnLongClickListener(v -> {
            copyToClipboard(tvResult.getText().toString());
            return true;
        });
        box.addView(tvResult);

        return box;
    }

    private TextView indicatorText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11);
        t.setLetterSpacing(0.06f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        Ui.setTagState(t, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(6));
        t.setLayoutParams(lp);
        return t;
    }

    private void showExpr(String s) {
        tvExpr.setText(s);
        exprScroll.post(() -> exprScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private void copyToClipboard(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("result", s));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    // ---------- basic keypad ----------

    private View buildBasicKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundResource(R.drawable.bg_keypad_panel);
        col.setPadding(dp(10), dp(12), dp(10), dp(10));
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

        String[][] rows = {
                {"AC", "±", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"0", ".", "DEL", "="},
        };
        for (String[] r : rows) {
            LinearLayout row = Ui.row(this);
            for (String k : r) {
                int style = keyStyle(k);
                if (k.equals("±") || k.equals("%") || k.equals("DEL")) style = R.style.KeyFunction;
                Button b = Ui.key(this, k, style, 1f, 4);
                if (style == R.style.KeyFunction) b.setTextSize(20);
                b.setOnClickListener(v -> handleBasicKey(k, b));
                row.addView(b);
            }
            col.addView(row);
        }
        return col;
    }

    private void handleBasicKey(String key, Button source) {
        vibrate(source);
        switch (key) {
            case "AC": clearAll(); return;
            case "DEL": backspace(); return;
            case "=": onEquals(); return;
            case "±": negate(); return;
            default: insert(key);
        }
    }

    private void negate() {
        String e = lastWasResult ? lastAnswerPlain() : currentExpr;
        lastWasResult = false;
        if (e.isEmpty()) {
            currentExpr = "−";
        } else if (e.startsWith("−(") && e.endsWith(")")) {
            currentExpr = e.substring(2, e.length() - 1);
        } else {
            currentExpr = "−(" + e + ")";
        }
        showExpr(currentExpr);
        livePreview();
    }

    // ---------- scientific keypad ----------

    private View buildSciKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundResource(R.drawable.bg_keypad_panel);
        col.setPadding(dp(8), dp(10), dp(8), dp(8));
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

        addSciRow(col, new String[]{"SHIFT", "HYP", "DEG", "(", ")"}, 0.8f);
        addSciRow(col, new String[]{"sin", "cos", "tan", "log", "ln"}, 0.8f);
        addSciRow(col, new String[]{"x²", "√", "xʸ", "ˣ√y", "n!"}, 0.8f);
        addSciRow(col, new String[]{"π", "e", "1/x", "∛", "%"}, 0.8f);
        addSciRow(col, new String[]{"MC", "MR", "M+", "M−", "DEL"}, 0.8f);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        col.addView(gap);

        addSciRow(col, new String[]{"7", "8", "9", "AC", "÷"}, 1f);
        addSciRow(col, new String[]{"4", "5", "6", "nPr", "×"}, 1f);
        addSciRow(col, new String[]{"1", "2", "3", "nCr", "−"}, 1f);
        addSciRow(col, new String[]{"0", ".", "Ans", "EXP", "+"}, 1f);

        LinearLayout eqRow = Ui.row(this, 0.9f);
        Button eq = Ui.key(this, "=", R.style.KeyEquals, 1f, 2);
        eq.setTextSize(26);
        eq.setOnClickListener(v -> onEquals());
        eqRow.addView(eq);
        col.addView(eqRow);

        return col;
    }

    private void addSciRow(LinearLayout parent, String[] keys, float rowWeight) {
        LinearLayout row = Ui.row(this, rowWeight);
        for (String k : keys) {
            int style = keyStyle(k);
            Button b = Ui.key(this, k, style, 1f, 2);
            if (style == R.style.KeyFunction) {
                Ui.autoFit(b, 10, 16);
            } else if (style == R.style.KeyDigit || style == R.style.KeyOperator) {
                b.setTextSize(24);
            }
            if (k.equals("DEG")) degKeyRef = b;
            if (k.equals("SHIFT")) shiftKeyRef = b;
            if (k.equals("HYP")) hypKeyRef = b;
            b.setOnClickListener(v -> handleSciKey(k, b));
            row.addView(b);
        }
        parent.addView(row);
    }

    private boolean isDigitKey(String k) {
        return k.length() == 1 && Character.isDigit(k.charAt(0)) || k.equals(".");
    }

    private int keyStyle(String k) {
        if (k.equals("AC")) return R.style.KeyDanger;
        if (k.equals("=")) return R.style.KeyEquals;
        if (isDigitKey(k)) return R.style.KeyDigit;
        if (k.equals("÷") || k.equals("×") || k.equals("−") || k.equals("+")) return R.style.KeyOperator;
        return R.style.KeyFunction;
    }

    private void handleSciKey(String key, Button source) {
        vibrate(source);
        switch (key) {
            case "SHIFT": shiftOn = !shiftOn; refreshIndicators(); return;
            case "HYP": hypOn = !hypOn; refreshIndicators(); return;
            case "DEG": cycleAngle(); return;
            case "AC": clearAll(); return;
            case "DEL": backspace(); return;
            case "(": insert("("); return;
            case ")": insert(")"); return;
            case "÷": insert("÷"); return;
            case "×": insert("×"); return;
            case "−": insert("−"); return;
            case "+": insert("+"); return;
            case ".": insert("."); return;
            case "%": insert("%"); return;
            case "π": insert("π"); return;
            case "e": insert("e"); return;
            case "Ans": insert("Ans"); return;
            case "EXP": insert("e"); return;
            case "n!": insert("!"); return;
            case "x²": insert(shiftOn ? "³" : "²"); return;
            case "1/x": insert("⁻¹"); return;
            case "√": insert(shiftOn ? "∛(" : "√("); return;
            case "∛": insert("∛("); return;
            case "xʸ": insert("^"); return;
            case "ˣ√y": insert("root("); return;
            case "nPr": insert("nPr("); return;
            case "nCr": insert("nCr("); return;
            case "MC": store.setMemory(0); refreshIndicators(); toast(R.string.memory_cleared); return;
            case "MR": insert(plainNum(store.getMemory())); return;
            case "M+": store.setMemory(store.getMemory() + currentNumericValue()); refreshIndicators(); toast(R.string.memory_saved); return;
            case "M−": store.setMemory(store.getMemory() - currentNumericValue()); refreshIndicators(); toast(R.string.memory_saved); return;
            case "sin": insertTrig("sin", "asin", "sinh", "asinh"); return;
            case "cos": insertTrig("cos", "acos", "cosh", "acosh"); return;
            case "tan": insertTrig("tan", "atan", "tanh", "atanh"); return;
            case "log": insert(shiftOn ? "10^(" : "log("); return;
            case "ln": insert(shiftOn ? "e^(" : "ln("); return;
            default:
                insert(key);
        }
    }

    private void insertTrig(String normal, String inv, String hyp, String invHyp) {
        String fn;
        if (shiftOn && hypOn) fn = invHyp;
        else if (shiftOn) fn = inv;
        else if (hypOn) fn = hyp;
        else fn = normal;
        insert(fn + "(");
    }

    private double currentNumericValue() {
        try {
            String clean = currentExpr.isEmpty() ? tvResult.getText().toString() : currentExpr;
            return Expr.eval(toEvalString(clean), angleMode, () -> lastAnswer);
        } catch (Exception e) {
            try { return Double.parseDouble(tvResult.getText().toString().replace(",", "")); } catch (Exception e2) { return 0; }
        }
    }

    private void refreshIndicators() {
        styleToggleKey(shiftKeyRef, shiftOn);
        styleToggleKey(hypKeyRef, hypOn);
        btnDot(tvShiftDot, shiftOn);
        btnDot(tvHypDot, hypOn);
        boolean hasMem = store.getMemory() != 0;
        btnDot(tvMemDot, hasMem);
        boolean sci = currentTab == TAB_SCI;
        tvAngle.setVisibility(currentTab == TAB_BASIC ? View.GONE : View.VISIBLE);
        tvShiftDot.setVisibility(sci ? View.VISIBLE : View.GONE);
        tvHypDot.setVisibility(sci ? View.VISIBLE : View.GONE);
        tvMemDot.setVisibility(sci || hasMem ? View.VISIBLE : View.GONE);
    }

    private void styleToggleKey(Button b, boolean on) {
        if (b == null) return;
        b.setBackgroundResource(on ? R.drawable.key_equals_bg : R.drawable.key_function_bg);
        b.setTextColor(on ? getColor(R.color.on_accent) : c(R.attr.cTextPrimary));
    }

    private void btnDot(TextView t, boolean active) {
        Ui.setTagState(t, active);
    }

    // ---------- advanced keypad ----------

    private View buildAdvKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundResource(R.drawable.bg_keypad_panel);
        col.setPadding(dp(8), dp(10), dp(8), dp(8));
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

        String[][] rows = {
                {"gcd", "lcm", "min", "max", "sum"},
                {"avg", "median", "stdev", "var", "prod"},
                {"floor", "ceil", "round", "trunc", "sign"},
                {"mod", "hypot", "atan2", "logb", "pow"},
                {"isprime", "nextprime", "cot", "sec", "csc"},
                {"erf", "gamma", "deg", "rad", "randint"},
                {"φ", "τ", "π", "e", "Ans"},
                {",", "(", ")", "DEL", "AC"},
        };
        for (String[] r : rows) {
            LinearLayout row = Ui.row(this);
            for (String k : r) {
                int style = k.equals("AC") ? R.style.KeyDanger : R.style.KeyFunction;
                Button b = Ui.key(this, k, style, 1f, 2);
                if (style == R.style.KeyFunction) Ui.autoFit(b, 9, 15);
                b.setOnClickListener(v -> handleAdvKey(k, b));
                row.addView(b);
            }
            col.addView(row);
        }

        LinearLayout eqRow = Ui.row(this);
        Button eq = Ui.key(this, "=", R.style.KeyEquals, 1f, 2);
        eq.setTextSize(26);
        eq.setOnClickListener(v -> onEquals());
        eqRow.addView(eq);
        col.addView(eqRow);
        return col;
    }

    private void handleAdvKey(String key, Button source) {
        vibrate(source);
        switch (key) {
            case "AC": clearAll(); return;
            case "DEL": backspace(); return;
            case ",": case "(": case ")": case "π": case "e": case "Ans": insert(key); return;
            case "φ": insert("phi"); return;
            case "τ": insert("tau"); return;
            default: insert(key + "(");
        }
    }

    // ---------- expression handling ----------

    private boolean isChainOperator(String t) {
        return t.equals("+") || t.equals("−") || t.equals("×") || t.equals("÷") || t.equals("^");
    }

    private void insert(String token) {
        if (lastWasResult) {
            currentExpr = isChainOperator(token) ? lastAnswerPlain() : "";
            lastWasResult = false;
        }
        currentExpr += token;
        showExpr(currentExpr);
        livePreview();
    }

    private void backspace() {
        if (lastWasResult) { clearAll(); return; }
        if (currentExpr.isEmpty()) return;
        currentExpr = currentExpr.substring(0, currentExpr.length() - 1);
        showExpr(currentExpr);
        livePreview();
    }

    private void clearAll() {
        currentExpr = "";
        showExpr("");
        tvResult.setText("0");
        lastWasResult = false;
    }

    private String toEvalString(String s) {
        return s;
    }

    private String lastAnswerPlain() {
        if (store.getHighPrecision() && lastBig != null) return lastBig.stripTrailingZeros().toPlainString();
        return plainNum(lastAnswer);
    }

    private static String plainNum(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private String compute(String expr) {
        pendingBig = null;
        if (store.getHighPrecision()) {
            try {
                BigDecimal ans = lastBig != null ? lastBig : BigDecimal.valueOf(lastAnswer);
                BigDecimal b = BigExpr.eval(expr, angleMode, store.getHpDigits(), ans);
                pendingBig = b;
                pendingAns = b.doubleValue();
                return Fmt.big(b, store.getThousands());
            } catch (BigExpr.Unsupported | ArithmeticException | NumberFormatException ignored) {
                pendingBig = null;
            }
        }
        double v = Expr.eval(toEvalString(expr), angleMode, () -> lastAnswer);
        pendingAns = v;
        return Fmt.result(v, store.getThousands());
    }

    private void livePreview() {
        if (currentExpr.trim().isEmpty()) { tvResult.setText("0"); return; }
        if (!store.getAutoPreview()) return;
        try {
            tvResult.setText(compute(currentExpr));
        } catch (Exception ignored) {
        }
    }

    private void onEquals() {
        if (currentExpr.trim().isEmpty()) return;
        try {
            String formatted = compute(currentExpr);
            store.addHistory(currentExpr, formatted);
            tvResult.setText(formatted);
            lastAnswer = pendingAns;
            lastBig = pendingBig;
            lastWasResult = true;
        } catch (Expr.EvalException ex) {
            tvResult.setText(Fmt.errorMessage(ex, this));
        } catch (Exception ex) {
            tvResult.setText(getString(R.string.error_math));
        }
    }

    private void cycleAngle() {
        angleMode = (angleMode + 1) % 3;
        updateAngleLabel();
        livePreview();
    }

    private void updateAngleLabel() {
        String label = angleMode == Expr.DEG ? getString(R.string.deg) : angleMode == Expr.RAD ? getString(R.string.rad) : getString(R.string.grad);
        tvAngle.setText(label);
        if (degKeyRef != null) degKeyRef.setText(label);
    }

    private void vibrate(View v) {
        if (store.getHaptic()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    // ---------- tabs ----------

    private void switchTab(int tab) {
        currentTab = tab;
        basicKeypad.setVisibility(tab == TAB_BASIC ? View.VISIBLE : View.GONE);
        sciKeypad.setVisibility(tab == TAB_SCI ? View.VISIBLE : View.GONE);
        progPanel.setVisibility(tab == TAB_PROG ? View.VISIBLE : View.GONE);
        convPanel.setVisibility(tab == TAB_CONV ? View.VISIBLE : View.GONE);
        advKeypad.setVisibility(tab == TAB_ADV ? View.VISIBLE : View.GONE);
        boolean showDisplay = tab != TAB_PROG && tab != TAB_CONV;
        displayBox.setVisibility(showDisplay ? View.VISIBLE : View.GONE);
        applyWeights(tab);
        if (tab != TAB_CONV) hideKeyboard();
        refreshTabPills();
        if (tab == TAB_PROG) updateProgDisplay();
        refreshIndicators();
    }

    private void applyWeights(int tab) {
        float displayWeight;
        float panelWeight;
        if (tab == TAB_BASIC) {
            displayWeight = 1.15f;
            panelWeight = 2.2f;
        } else if (tab == TAB_SCI || tab == TAB_ADV) {
            displayWeight = 0.95f;
            panelWeight = 3.0f;
        } else {
            displayWeight = 0f;
            panelWeight = 1f;
        }
        LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) displayBox.getLayoutParams();
        dlp.weight = displayWeight;
        displayBox.setLayoutParams(dlp);
        LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) panelContainer.getLayoutParams();
        plp.weight = panelWeight;
        panelContainer.setLayoutParams(plp);
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    // ---------- programmer mode ----------

    private View buildProgPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.setVisibility(View.GONE);

        LinearLayout baseCard = Ui.card(this);
        baseCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bcLp.setMargins(dp(12), dp(8), dp(12), dp(10));
        col.addView(baseCard, bcLp);

        String[] baseNames = {
                getString(R.string.base_dec), getString(R.string.base_hex),
                getString(R.string.base_oct), getString(R.string.base_bin)
        };
        for (int i = 0; i < 4; i++) {
            final int base = PROG_BASES[i];
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(12), 0, dp(12), 0);
            r.setClickable(true);
            r.setFocusable(true);
            r.setOnClickListener(v -> switchBase(base));

            TextView label = new TextView(this);
            label.setText(baseNames[i]);
            label.setTextSize(12);
            label.setLetterSpacing(0.06f);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            r.addView(label, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView value = new TextView(this);
            value.setTypeface(Typeface.MONOSPACE);
            value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            value.setText("0");
            Ui.autoFit(value, 11, 22);
            r.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            baseCard.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            progBaseRows[i] = r;
            progBaseLabels[i] = label;
            progBaseValues[i] = value;
        }

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        keys.setBackgroundResource(R.drawable.bg_keypad_panel);
        keys.setPadding(dp(8), dp(10), dp(8), dp(8));
        LinearLayout.LayoutParams kLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        kLp.setMargins(dp(12), 0, dp(12), dp(12));
        col.addView(keys, kLp);

        hexDigitButtons = new Button[6];
        addProgRow(keys, new String[]{"A", "B", "C", "D", "E", "F"}, null, 0.8f);
        addProgRow(keys, new String[]{"AND", "OR", "XOR", "NOT", "<<"}, null, 0.8f);
        addProgRow(keys, new String[]{"NAND", "NOR", ">>", "±", "%"}, null, 0.8f);
        addProgRow(keys, new String[]{"7", "8", "9", "DEL", "AC"}, null, 1f);
        addProgRow(keys, new String[]{"4", "5", "6", "×", "÷"}, null, 1f);
        addProgRow(keys, new String[]{"1", "2", "3", "+", "−"}, null, 1f);
        addProgRow(keys, new String[]{"0", "="}, new float[]{2f, 3f}, 1f);

        return col;
    }

    private void addProgRow(LinearLayout parent, String[] keys, float[] weights, float rowWeight) {
        LinearLayout row = Ui.row(this, rowWeight);
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            int style;
            if (k.equals("AC")) style = R.style.KeyDanger;
            else if (k.equals("=")) style = R.style.KeyEquals;
            else if (k.equals("÷") || k.equals("×") || k.equals("−") || k.equals("+")) style = R.style.KeyOperator;
            else if (k.matches("[0-9A-F]")) style = R.style.KeyDigit;
            else style = R.style.KeyFunction;
            float w = weights == null ? 1f : weights[i];
            Button b = Ui.key(this, k, style, w, 2);
            if (style == R.style.KeyFunction) {
                Ui.autoFit(b, 9, 15);
            } else if (style == R.style.KeyDigit) {
                b.setTextSize(k.matches("[0-9]") ? 24 : 20);
            } else if (style == R.style.KeyOperator) {
                b.setTextSize(24);
            } else if (style == R.style.KeyEquals) {
                b.setTextSize(26);
            }
            b.setOnClickListener(v -> handleProgKey(k, b));
            row.addView(b);
            if (k.matches("[A-F]")) {
                int idx = k.charAt(0) - 'A';
                hexDigitButtons[idx] = b;
            }
        }
        parent.addView(row);
    }

    private void switchBase(int base) {
        long cur = currentProgValue();
        progBase = base;
        progEntry = ProgrammerEngine.format(cur, base);
        updateDigitEnablement();
        updateProgDisplay();
    }

    private void updateDigitEnablement() {
        for (int i = 0; i < 4; i++) {
            boolean active = progBase == PROG_BASES[i];
            progBaseRows[i].setBackgroundResource(active ? R.drawable.bg_base_row_selected : R.drawable.bg_base_row_idle);
            progBaseLabels[i].setTextColor(active ? c(R.attr.cAccentText) : c(R.attr.cTextSecondary));
            progBaseValues[i].setTextColor(c(R.attr.cTextPrimary));
        }
        for (int i = 0; i < 6; i++) {
            if (hexDigitButtons[i] == null) continue;
            boolean en = progBase == ProgrammerEngine.HEX;
            hexDigitButtons[i].setEnabled(en);
            hexDigitButtons[i].setAlpha(en ? 1f : 0.3f);
        }
    }

    private long currentProgValue() {
        try {
            return ProgrammerEngine.parse(progEntry, progBase);
        } catch (Exception e) {
            return 0;
        }
    }

    private void handleProgKey(String key, Button source) {
        vibrate(source);
        if (key.matches("[0-9A-F]")) { progDigit(key); return; }
        switch (key) {
            case "AC": progAcc = 0; progPendingOp = null; progEntry = "0"; updateProgDisplay(); return;
            case "DEL":
                if (progEntry.length() > 1) progEntry = progEntry.substring(0, progEntry.length() - 1);
                else progEntry = "0";
                updateProgDisplay();
                return;
            case "±":
                progEntry = progEntry.startsWith("-") ? progEntry.substring(1) : "-" + progEntry;
                updateProgDisplay();
                return;
            case "NOT": {
                long v = ProgrammerEngine.not(currentProgValue());
                progEntry = ProgrammerEngine.format(v, progBase);
                updateProgDisplay();
                return;
            }
            case "%": {
                long v = currentProgValue() / 100;
                progEntry = ProgrammerEngine.format(v, progBase);
                updateProgDisplay();
                return;
            }
            case "=":
                if (progPendingOp == null) {
                    store.addHistory(progEntry, progEntry + " (" + baseLabel(progBase) + ")");
                    return;
                }
                progCompute(null);
                return;
            default: progOperator(key);
        }
    }

    private void progDigit(String d) {
        if (!ProgrammerEngine.validDigit(d.charAt(0), progBase)) return;
        if (progEntry.equals("0")) progEntry = d;
        else progEntry += d;
        updateProgDisplay();
    }

    private void progOperator(String op) {
        if (progPendingOp != null) {
            progCompute(op);
        } else {
            progAcc = currentProgValue();
            progPendingOp = op;
            progEntry = "0";
            updateProgDisplay();
        }
    }

    private void progCompute(String nextOp) {
        if (progPendingOp == null) { progPendingOp = nextOp; return; }
        long b = currentProgValue();
        long r;
        switch (progPendingOp) {
            case "AND": r = ProgrammerEngine.and(progAcc, b); break;
            case "OR": r = ProgrammerEngine.or(progAcc, b); break;
            case "XOR": r = ProgrammerEngine.xor(progAcc, b); break;
            case "NAND": r = ProgrammerEngine.nand(progAcc, b); break;
            case "NOR": r = ProgrammerEngine.nor(progAcc, b); break;
            case "<<": r = ProgrammerEngine.shl(progAcc, b); break;
            case ">>": r = ProgrammerEngine.shr(progAcc, b); break;
            case "+": r = progAcc + b; break;
            case "−": r = progAcc - b; break;
            case "×": r = progAcc * b; break;
            case "÷": r = b == 0 ? 0 : progAcc / b; break;
            default: r = b;
        }
        progAcc = r;
        progEntry = ProgrammerEngine.format(r, progBase);
        progPendingOp = nextOp;
        updateProgDisplay();
        if (nextOp == null) {
            store.addHistory(describeProgOp(), progEntry + " (" + baseLabel(progBase) + ")");
        }
    }

    private String describeProgOp() {
        return progEntry;
    }

    private String baseLabel(int base) {
        if (base == ProgrammerEngine.HEX) return getString(R.string.base_hex);
        if (base == ProgrammerEngine.OCT) return getString(R.string.base_oct);
        if (base == ProgrammerEngine.BIN) return getString(R.string.base_bin);
        return getString(R.string.base_dec);
    }

    private void updateProgDisplay() {
        long v = currentProgValue();
        progBaseValues[0].setText(ProgrammerEngine.format(v, ProgrammerEngine.DEC));
        progBaseValues[1].setText(ProgrammerEngine.grouped(ProgrammerEngine.format(v, ProgrammerEngine.HEX), ProgrammerEngine.HEX));
        progBaseValues[2].setText(ProgrammerEngine.format(v, ProgrammerEngine.OCT));
        progBaseValues[3].setText(ProgrammerEngine.grouped(ProgrammerEngine.format(v, ProgrammerEngine.BIN), ProgrammerEngine.BIN));
        updateDigitEnablement();
    }

    // ---------- converter ----------

    private View buildConvPanel() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVisibility(View.GONE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(24));

        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        catScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        convChips = new TextView[Converter.CATEGORIES.length];
        for (int i = 0; i < Converter.CATEGORIES.length; i++) {
            final int idx = i;
            TextView chip = Ui.chip(this, categoryLabel(Converter.CATEGORIES[i]));
            chip.setOnClickListener(v -> selectCategory(idx));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            clp.setMarginEnd(dp(8));
            catRow.addView(chip, clp);
            convChips[i] = chip;
        }
        catScroll.addView(catRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(catScroll, Ui.matchWrap(this, 4, 14, 0));

        etConvInput = new EditText(this);
        etConvInput.setHint("0");
        etConvInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etConvInput.setTextSize(30);
        etConvInput.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        etConvInput.setSingleLine(true);
        etConvInput.setBackground(null);
        etConvInput.setPadding(0, dp(6), dp(8), dp(6));
        etConvInput.setTextColor(c(R.attr.cTextPrimary));
        etConvInput.setHintTextColor(c(R.attr.cTextHint));

        spFrom = new Spinner(this);
        spTo = new Spinner(this);
        styleSpinner(spFrom);
        styleSpinner(spTo);

        LinearLayout inCard = Ui.card(this);
        inCard.setPadding(dp(18), dp(14), dp(14), dp(14));
        inCard.addView(Ui.small(this, getString(R.string.conv_from)));
        LinearLayout inRow = new LinearLayout(this);
        inRow.setOrientation(LinearLayout.HORIZONTAL);
        inRow.setGravity(Gravity.CENTER_VERTICAL);
        inRow.addView(etConvInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inRow.addView(spFrom, new LinearLayout.LayoutParams(dp(136), dp(44)));
        inCard.addView(inRow);
        col.addView(inCard, Ui.matchWrap(this, 0, 0, 0));

        ImageButton swap = new ImageButton(this);
        swap.setImageResource(R.drawable.ic_swap);
        swap.setBackgroundResource(R.drawable.bg_swap_btn);
        swap.setScaleType(ImageView.ScaleType.CENTER);
        swap.setContentDescription(getString(R.string.conv_swap));
        swap.setOnClickListener(v -> swapUnits());
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        swLp.gravity = Gravity.CENTER_HORIZONTAL;
        swLp.topMargin = dp(10);
        swLp.bottomMargin = dp(10);
        col.addView(swap, swLp);

        tvConvResult = new TextView(this);
        tvConvResult.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvConvResult.setTextColor(c(R.attr.cAccentText));
        tvConvResult.setPadding(0, dp(6), dp(8), dp(6));
        tvConvResult.setText("0");
        tvConvResult.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        Ui.autoFit(tvConvResult, 16, 36);
        tvConvResult.setOnLongClickListener(v -> {
            copyToClipboard(tvConvResult.getText().toString());
            return true;
        });

        LinearLayout outCard = new LinearLayout(this);
        outCard.setOrientation(LinearLayout.VERTICAL);
        outCard.setBackgroundResource(R.drawable.bg_result_card);
        outCard.setPadding(dp(18), dp(14), dp(14), dp(14));
        outCard.addView(Ui.small(this, getString(R.string.conv_to)));
        LinearLayout outRow = new LinearLayout(this);
        outRow.setOrientation(LinearLayout.HORIZONTAL);
        outRow.setGravity(Gravity.CENTER_VERTICAL);
        outRow.addView(tvConvResult, new LinearLayout.LayoutParams(0, dp(48), 1f));
        outRow.addView(spTo, new LinearLayout.LayoutParams(dp(136), dp(44)));
        outCard.addView(outRow);
        col.addView(outCard, Ui.matchWrap(this, 0, 0, 0));

        scroll.addView(col);

        AdapterView.OnItemSelectedListener recompute = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { doConvert(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        spFrom.setOnItemSelectedListener(recompute);
        spTo.setOnItemSelectedListener(recompute);

        etConvInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c2) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c2) {}
            @Override public void afterTextChanged(android.text.Editable s) { doConvert(); }
        });

        selectCategory(0);
        return scroll;
    }

    private void selectCategory(int index) {
        convCategory = index;
        for (int i = 0; i < convChips.length; i++) {
            Ui.setChipState(convChips[i], i == index);
        }
        setupUnitSpinners(Converter.CATEGORIES[index]);
    }

    private void swapUnits() {
        int a = spFrom.getSelectedItemPosition();
        int b = spTo.getSelectedItemPosition();
        if (a < 0 || b < 0) return;
        spFrom.setSelection(b);
        spTo.setSelection(a);
    }

    private void styleSpinner(Spinner sp) {
        sp.setBackgroundResource(R.drawable.bg_unit_pill);
        sp.setPopupBackgroundDrawable(Ui.round(c(R.attr.cSurface), dp(14), c(R.attr.cStrokeSoft), dp(1)));
        sp.setPadding(dp(12), 0, dp(8), 0);
    }

    private String categoryLabel(String key) {
        switch (key) {
            case Converter.LENGTH: return getString(R.string.conv_length);
            case Converter.WEIGHT: return getString(R.string.conv_weight);
            case Converter.TEMP: return getString(R.string.conv_temp);
            case Converter.AREA: return getString(R.string.conv_area);
            case Converter.SPEED: return getString(R.string.conv_speed);
            case Converter.DATA: return getString(R.string.conv_data);
            case Converter.TIME: return getString(R.string.conv_time);
            default: return key;
        }
    }

    private void setupUnitSpinners(String category) {
        Map<String, Double> units = Converter.units(category);
        String[] names = units.keySet().toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner, names);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spFrom.setAdapter(adapter);
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, R.layout.item_spinner, names);
        adapter2.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spTo.setAdapter(adapter2);
        if (names.length > 1) spTo.setSelection(1);
        doConvert();
    }

    private void doConvert() {
        if (spFrom.getAdapter() == null || spTo.getAdapter() == null) return;
        if (spFrom.getSelectedItem() == null || spTo.getSelectedItem() == null) return;
        String category = Converter.CATEGORIES[convCategory];
        String from = (String) spFrom.getSelectedItem();
        String to = (String) spTo.getSelectedItem();
        double input;
        try {
            input = Double.parseDouble(etConvInput.getText().toString());
        } catch (Exception e) {
            tvConvResult.setText("0");
            return;
        }
        double out = Converter.convert(category, from, to, input);
        tvConvResult.setText(Fmt.result(out, store.getThousands()));
    }
}
