package com.calc.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
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
    private Button btnShift;
    private Button btnHyp;

    private FrameLayout panelContainer;
    private View basicKeypad, sciKeypad, progPanel, convPanel, advKeypad;
    private Button tabBasic, tabSci, tabProg, tabConv, tabAdv;
    private Button shiftKeyRef, hypKeyRef;
    private BigDecimal lastBig = null;
    private BigDecimal pendingBig = null;
    private double pendingAns = 0;

    private int progBase = ProgrammerEngine.DEC;
    private String progEntry = "0";
    private long progAcc = 0;
    private String progPendingOp = null;
    private TextView tvProgMain;
    private TextView tvProgDec, tvProgHex, tvProgOct, tvProgBin;
    private Button[] hexDigitButtons;
    private Button baseDec, baseHex, baseOct, baseBin;

    private EditText etConvInput;
    private TextView tvConvResult;
    private Spinner spCategory, spFrom, spTo;

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
                    tvExpr.setText(currentExpr);
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
        root.addView(buildDisplay());

        panelContainer = new FrameLayout(this);
        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        panelContainer.setLayoutParams(pcLp);

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

    // ---------- top bar ----------

    private View buildTopBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(12), dp(6), dp(12), dp(2));
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
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
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

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(2), dp(2), dp(2), dp(2));
        tabs.setBackground(Ui.round(getColor(R.color.seg_bg), dp(10), 0, 0));
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        tabsLp.topMargin = dp(8);
        tabs.setLayoutParams(tabsLp);

        tabBasic = tabPill(getString(R.string.tab_basic));
        tabSci = tabPill(getString(R.string.tab_sci));
        tabProg = tabPill(getString(R.string.tab_prog));
        tabConv = tabPill(getString(R.string.tab_conv));
        tabAdv = tabPill(getString(R.string.tab_adv));
        tabBasic.setOnClickListener(v -> switchTab(TAB_BASIC));
        tabSci.setOnClickListener(v -> switchTab(TAB_SCI));
        tabProg.setOnClickListener(v -> switchTab(TAB_PROG));
        tabConv.setOnClickListener(v -> switchTab(TAB_CONV));
        tabAdv.setOnClickListener(v -> switchTab(TAB_ADV));
        tabs.addView(tabBasic);
        tabs.addView(tabSci);
        tabs.addView(tabProg);
        tabs.addView(tabConv);
        tabs.addView(tabAdv);
        wrap.addView(tabs);

        return wrap;
    }

    private ImageButton iconButton(int drawableRes) {
        ImageButton b = new ImageButton(this, null, 0, R.style.IconButton);
        b.setImageResource(drawableRes);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return b;
    }

    private Button tabPill(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMaxLines(1);
        b.setIncludeFontPadding(false);
        b.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshTabPills() {
        setPillState(tabBasic, currentTab == TAB_BASIC);
        setPillState(tabSci, currentTab == TAB_SCI);
        setPillState(tabProg, currentTab == TAB_PROG);
        setPillState(tabConv, currentTab == TAB_CONV);
        setPillState(tabAdv, currentTab == TAB_ADV);
    }

    private void setPillState(Button b, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (active) {
            bg.setColor(getColor(R.color.seg_selected));
            b.setTextColor(c(R.attr.cTextPrimary));
            b.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            b.setElevation(dp(1));
        } else {
            bg.setColor(Color.TRANSPARENT);
            b.setTextColor(c(R.attr.cTextPrimary));
            b.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            b.setElevation(0);
        }
        b.setBackground(bg);
    }

    // ---------- display ----------

    private View buildDisplay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_display);
        box.setPadding(dp(22), dp(6), dp(22), dp(10));
        box.setLayoutParams(Ui.matchWrap(this, 4, 4, 0));

        LinearLayout indicators = new LinearLayout(this);
        indicators.setOrientation(LinearLayout.HORIZONTAL);
        indicators.setGravity(Gravity.END);
        indicators.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvMemDot = indicatorText("M");
        tvShiftDot = indicatorText("SHIFT");
        tvHypDot = indicatorText("HYP");
        tvAngle = indicatorText(getString(R.string.deg));
        tvAngle.setOnClickListener(v -> cycleAngle());
        tvMemDot.setAlpha(0.25f);
        tvShiftDot.setAlpha(0.25f);
        tvHypDot.setAlpha(0.25f);

        indicators.addView(tvMemDot);
        indicators.addView(tvShiftDot);
        indicators.addView(tvHypDot);
        indicators.addView(tvAngle);
        box.addView(indicators);

        HorizontalScrollView exprScroll = new HorizontalScrollView(this);
        exprScroll.setHorizontalScrollBarEnabled(false);
        exprScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvExpr = new TextView(this);
        tvExpr.setTextSize(20);
        tvExpr.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        tvExpr.setMinHeight(dp(26));
        tvExpr.setTextColor(c(R.attr.cTextSecondary));
        tvExpr.setGravity(Gravity.END);
        tvExpr.setText("");
        tvExpr.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        exprScroll.addView(tvExpr);
        box.addView(exprScroll);

        tvResult = new TextView(this);
        tvResult.setTextSize(64);
        tvResult.setTextColor(c(R.attr.cTextPrimary));
        tvResult.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        tvResult.setGravity(Gravity.END);
        tvResult.setText("0");
        tvResult.setSingleLine(true);
        tvResult.setAutoSizeTextTypeUniformWithConfiguration(24, 64, 1, TypedValue.COMPLEX_UNIT_SP);
        tvResult.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        t.setTextSize(10);
        t.setLetterSpacing(0.08f);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(c(R.attr.cAccentText));
        t.setPadding(dp(6), 0, dp(6), 0);
        return t;
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
        col.setPadding(dp(10), dp(6), dp(10), dp(10));
        col.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
                Button b = Ui.key(this, k, style, 1f);
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
        tvExpr.setText(currentExpr);
        livePreview();
    }

    // ---------- scientific keypad ----------

    private View buildSciKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(4), dp(10), dp(8));
        col.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addSciRow(col, new String[]{"SHIFT", "HYP", "DEG", "(", ")"});
        addSciRow(col, new String[]{"sin", "cos", "tan", "log", "ln"});
        addSciRow(col, new String[]{"x²", "√", "xʸ", "ˣ√y", "n!"});
        addSciRow(col, new String[]{"π", "e", "1/x", "∛", "%"});
        addSciRow(col, new String[]{"MC", "MR", "M+", "M−", "DEL"});
        addSciRow(col, new String[]{"7", "8", "9", "AC", "÷"});
        addSciRow(col, new String[]{"4", "5", "6", "nPr", "×"});
        addSciRow(col, new String[]{"1", "2", "3", "nCr", "−"});
        addSciRow(col, new String[]{"0", ".", "Ans", "EXP", "+"});

        LinearLayout eqRow = Ui.row(this);
        Button eq = Ui.key(this, "=", R.style.KeyEquals, 1f);
        eq.setOnClickListener(v -> onEquals());
        eqRow.addView(eq);
        col.addView(eqRow);

        return col;
    }

    private void addSciRow(LinearLayout parent, String[] keys) {
        LinearLayout row = Ui.row(this);
        for (String k : keys) {
            int style = keyStyle(k);
            Button b = Ui.key(this, k, style, 1f);
            if (style == R.style.KeyFunction) b.setTextSize(14);
            if (k.equals("DEG")) degKeyRef = b;
            if (k.equals("SHIFT")) shiftKeyRef = b;
            if (k.equals("HYP")) hypKeyRef = b;
            b.setOnClickListener(v -> handleSciKey(k, b));
            row.addView(b);
        }
        parent.addView(row);
    }

    private Button degKeyRef;

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
        if (shiftKeyRef != null) shiftKeyRef.setTextColor(shiftOn ? c(R.attr.cAccentText) : c(R.attr.cTextPrimary));
        if (hypKeyRef != null) hypKeyRef.setTextColor(hypOn ? c(R.attr.cAccentText) : c(R.attr.cTextPrimary));
        btnDot(tvShiftDot, shiftOn);
        btnDot(tvHypDot, hypOn);
        btnDot(tvMemDot, store.getMemory() != 0);
    }

    private void btnDot(TextView t, boolean active) {
        t.setAlpha(active ? 1f : 0.25f);
    }

    // ---------- advanced keypad ----------

    private View buildAdvKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(4), dp(10), dp(8));
        col.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
                Button b = Ui.key(this, k, style, 1f);
                b.setTextSize(k.length() > 5 ? 11 : 13);
                b.setOnClickListener(v -> handleAdvKey(k, b));
                row.addView(b);
            }
            col.addView(row);
        }

        LinearLayout eqRow = Ui.row(this);
        Button eq = Ui.key(this, "=", R.style.KeyEquals, 1f);
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
        tvExpr.setText(currentExpr);
        livePreview();
    }

    private void backspace() {
        if (lastWasResult) { clearAll(); return; }
        if (currentExpr.isEmpty()) return;
        currentExpr = currentExpr.substring(0, currentExpr.length() - 1);
        tvExpr.setText(currentExpr);
        livePreview();
    }

    private void clearAll() {
        currentExpr = "";
        tvExpr.setText("");
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
        refreshTabPills();
        if (tab == TAB_PROG) updateProgDisplay();
        refreshIndicators();
    }

    // ---------- programmer mode ----------

    private View buildProgPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(4), dp(10), dp(8));
        col.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.setVisibility(View.GONE);

        LinearLayout baseRow = new LinearLayout(this);
        baseRow.setOrientation(LinearLayout.HORIZONTAL);
        baseRow.setPadding(dp(2), dp(2), dp(2), dp(2));
        baseRow.setBackground(Ui.round(getColor(R.color.seg_bg), dp(10), 0, 0));
        LinearLayout.LayoutParams baseLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        baseLp.bottomMargin = dp(10);
        baseLp.topMargin = dp(4);
        baseRow.setLayoutParams(baseLp);
        baseDec = basePill(getString(R.string.base_dec), ProgrammerEngine.DEC);
        baseHex = basePill(getString(R.string.base_hex), ProgrammerEngine.HEX);
        baseOct = basePill(getString(R.string.base_oct), ProgrammerEngine.OCT);
        baseBin = basePill(getString(R.string.base_bin), ProgrammerEngine.BIN);
        baseRow.addView(baseDec);
        baseRow.addView(baseHex);
        baseRow.addView(baseOct);
        baseRow.addView(baseBin);
        col.addView(baseRow);

        LinearLayout convLines = Ui.card(this);
        convLines.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        convLines.setLayoutParams(clp);
        tvProgDec = progLine("DEC");
        tvProgHex = progLine("HEX");
        tvProgOct = progLine("OCT");
        tvProgBin = progLine("BIN");
        convLines.addView(tvProgDec);
        convLines.addView(tvProgHex);
        convLines.addView(tvProgOct);
        convLines.addView(tvProgBin);
        col.addView(convLines);

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        keys.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        hexDigitButtons = new Button[6];
        addProgRow(keys, new String[]{"A", "B", "C", "D", "E"}, true);
        addProgRow(keys, new String[]{"F", "DEL", "AC"}, true);
        addProgRow(keys, new String[]{"AND", "OR", "XOR", "NOT", "<<"}, false);
        addProgRow(keys, new String[]{"NAND", "NOR", ">>", "±", "%"}, false);
        addProgRow(keys, new String[]{"7", "8", "9", "÷", "×"}, false);
        addProgRow(keys, new String[]{"4", "5", "6", "−", "+"}, false);
        addProgRow(keys, new String[]{"1", "2", "3", "0", "="}, false);

        col.addView(keys);
        return col;
    }

    private Button basePill(String label, int base) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> switchBase(base));
        return b;
    }

    private TextView progLine(String label) {
        TextView t = new TextView(this);
        t.setTextSize(15);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextColor(c(R.attr.cTextPrimary));
        t.setGravity(Gravity.END);
        t.setSingleLine(true);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private void addProgRow(LinearLayout parent, String[] keys, boolean hexRow) {
        LinearLayout row = Ui.row(this);
        for (String k : keys) {
            int style;
            if (k.equals("AC")) style = R.style.KeyDanger;
            else if (k.equals("=")) style = R.style.KeyEquals;
            else if (k.equals("÷") || k.equals("×") || k.equals("−") || k.equals("+")) style = R.style.KeyOperator;
            else if (k.matches("[0-9A-F]")) style = R.style.KeyDigit;
            else style = R.style.KeyFunction;
            Button b = Ui.key(this, k, style, 1f);
            if (style == R.style.KeyFunction) b.setTextSize(13);
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
        setBasePillState(baseDec, progBase == ProgrammerEngine.DEC);
        setBasePillState(baseHex, progBase == ProgrammerEngine.HEX);
        setBasePillState(baseOct, progBase == ProgrammerEngine.OCT);
        setBasePillState(baseBin, progBase == ProgrammerEngine.BIN);
        for (int i = 0; i < 6; i++) {
            if (hexDigitButtons[i] == null) continue;
            boolean en = progBase == ProgrammerEngine.HEX;
            hexDigitButtons[i].setEnabled(en);
            hexDigitButtons[i].setAlpha(en ? 1f : 0.3f);
        }
    }

    private void setBasePillState(Button b, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(active ? getColor(R.color.seg_selected) : Color.TRANSPARENT);
        b.setBackground(bg);
        b.setTextColor(c(R.attr.cTextPrimary));
        b.setElevation(active ? dp(1) : 0);
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
        tvProgDec.setText("DEC  " + ProgrammerEngine.format(v, ProgrammerEngine.DEC));
        tvProgHex.setText("HEX  " + ProgrammerEngine.grouped(ProgrammerEngine.format(v, ProgrammerEngine.HEX), ProgrammerEngine.HEX));
        tvProgOct.setText("OCT  " + ProgrammerEngine.format(v, ProgrammerEngine.OCT));
        tvProgBin.setText("BIN  " + ProgrammerEngine.grouped(ProgrammerEngine.format(v, ProgrammerEngine.BIN), ProgrammerEngine.BIN));
        updateDigitEnablement();
    }

    // ---------- converter ----------

    private View buildConvPanel() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setVisibility(View.GONE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(8), dp(14), dp(18));

        spCategory = new Spinner(this);
        String[] catLabels = new String[Converter.CATEGORIES.length];
        for (int i = 0; i < catLabels.length; i++) catLabels[i] = categoryLabel(Converter.CATEGORIES[i]);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, catLabels);
        catAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        styleSpinner(spCategory);
        spCategory.setAdapter(catAdapter);
        LinearLayout catCard = Ui.card(this);
        catCard.setPadding(dp(8), dp(2), dp(8), dp(2));
        catCard.addView(spCategory, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        col.addView(catCard, Ui.matchWrap(this, 6, 10, 0));

        etConvInput = new EditText(this);
        etConvInput.setHint("0");
        etConvInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etConvInput.setTextSize(26);
        etConvInput.setSingleLine(true);
        etConvInput.setBackground(null);
        etConvInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        etConvInput.setTextColor(c(R.attr.cTextPrimary));
        etConvInput.setHintTextColor(c(R.attr.cTextHint));

        spFrom = new Spinner(this);
        spTo = new Spinner(this);

        LinearLayout inCard = Ui.card(this);
        inCard.setPadding(dp(14), dp(10), dp(10), dp(10));
        TextView fromLabel = Ui.small(this, getString(R.string.conv_from));
        inCard.addView(fromLabel);
        LinearLayout inRow = new LinearLayout(this);
        inRow.setOrientation(LinearLayout.HORIZONTAL);
        inRow.setGravity(Gravity.CENTER_VERTICAL);
        inRow.addView(etConvInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inRow.addView(spFrom, new LinearLayout.LayoutParams(dp(140), dp(48)));
        inCard.addView(inRow);
        col.addView(inCard, Ui.matchWrap(this, 0, 0, 0));

        TextView arrow = new TextView(this);
        arrow.setText("↓");
        arrow.setTextSize(20);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(c(R.attr.cAccentText));
        arrow.setBackground(Ui.round(getColor(R.color.seg_bg), dp(10), 0, 0));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(44), dp(36));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(8);
        alp.bottomMargin = dp(8);
        col.addView(arrow, alp);

        tvConvResult = new TextView(this);
        tvConvResult.setTextSize(34);
        tvConvResult.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        tvConvResult.setTextColor(c(R.attr.cAccentText));
        tvConvResult.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvConvResult.setSingleLine(true);
        tvConvResult.setAutoSizeTextTypeUniformWithConfiguration(14, 34, 1, TypedValue.COMPLEX_UNIT_SP);
        tvConvResult.setOnLongClickListener(v -> {
            copyToClipboard(tvConvResult.getText().toString());
            return true;
        });

        LinearLayout outCard = new LinearLayout(this);
        outCard.setOrientation(LinearLayout.VERTICAL);
        outCard.setBackgroundResource(R.drawable.bg_result_card);
        outCard.setPadding(dp(14), dp(10), dp(10), dp(10));
        outCard.addView(Ui.small(this, getString(R.string.conv_to)));
        LinearLayout outRow = new LinearLayout(this);
        outRow.setOrientation(LinearLayout.HORIZONTAL);
        outRow.setGravity(Gravity.CENTER_VERTICAL);
        outRow.addView(tvConvResult, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        outRow.addView(spTo, new LinearLayout.LayoutParams(dp(140), dp(48)));
        outCard.addView(outRow);
        col.addView(outCard, Ui.matchWrap(this, 0, 0, 0));

        scroll.addView(col);

        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { setupUnitSpinners(Converter.CATEGORIES[pos]); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

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

        setupUnitSpinners(Converter.CATEGORIES[0]);
        return scroll;
    }

    private void styleSpinner(Spinner sp) {
        sp.setBackground(null);
        sp.setPopupBackgroundDrawable(Ui.round(c(R.attr.cSurface), dp(14), c(R.attr.cStrokeSoft), dp(1)));
        sp.setPadding(0, 0, 0, 0);
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
        styleSpinner(spFrom);
        styleSpinner(spTo);
        spTo.setAdapter(adapter2);
        if (names.length > 1) spTo.setSelection(1);
        doConvert();
    }

    private void doConvert() {
        if (spFrom.getAdapter() == null || spTo.getAdapter() == null) return;
        if (spFrom.getSelectedItem() == null || spTo.getSelectedItem() == null) return;
        String category = Converter.CATEGORIES[spCategory.getSelectedItemPosition()];
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

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private void addSpace(LinearLayout parent, int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h)));
        parent.addView(v);
    }
}
