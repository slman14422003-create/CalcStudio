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

import java.util.Map;

public final class MainActivity extends AppCompatActivity {

    public static final String EXTRA_RESTORE = "restore_expr";

    private static final int TAB_SCI = 0;
    private static final int TAB_PROG = 1;
    private static final int TAB_CONV = 2;

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
    private View sciKeypad, progPanel, convPanel;
    private Button tabSci, tabProg, tabConv;

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
        angleMode = store.getAngleMode();
        currentTab = store.getLastTab();
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

        root.addView(buildTopBar());
        root.addView(buildDisplay());

        panelContainer = new FrameLayout(this);
        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        panelContainer.setLayoutParams(pcLp);

        sciKeypad = buildSciKeypad();
        progPanel = buildProgPanel();
        convPanel = buildConvPanel();
        panelContainer.addView(sciKeypad);
        panelContainer.addView(progPanel);
        panelContainer.addView(convPanel);

        root.addView(panelContainer);
        setContentView(root);
    }

    // ---------- top bar ----------

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(4));
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnHistory = iconButton("🕘");
        btnHistory.setOnClickListener(v -> {
            Intent i = new Intent(this, HistoryActivity.class);
            historyLauncher.launch(i);
        });
        bar.addView(btnHistory);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(c(R.attr.cField));
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable tabsBg = new GradientDrawable();
        tabsBg.setColor(c(R.attr.cField));
        tabsBg.setCornerRadius(dp(100));
        tabs.setBackground(tabsBg);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        tabsLp.setMarginStart(dp(8));
        tabsLp.setMarginEnd(dp(8));
        tabs.setLayoutParams(tabsLp);

        tabSci = tabPill(getString(R.string.tab_sci));
        tabProg = tabPill(getString(R.string.tab_prog));
        tabConv = tabPill(getString(R.string.tab_conv));
        tabSci.setOnClickListener(v -> switchTab(TAB_SCI));
        tabProg.setOnClickListener(v -> switchTab(TAB_PROG));
        tabConv.setOnClickListener(v -> switchTab(TAB_CONV));
        tabs.addView(tabSci);
        tabs.addView(tabProg);
        tabs.addView(tabConv);
        bar.addView(tabs);

        Button btnSettings = iconButton("⚙");
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(btnSettings);

        return bar;
    }

    private Button iconButton(String emoji) {
        Button b = new Button(this, null, 0, R.style.IconButton);
        b.setText(emoji);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        b.setLayoutParams(lp);
        return b;
    }

    private Button tabPill(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshTabPills() {
        setPillState(tabSci, currentTab == TAB_SCI);
        setPillState(tabProg, currentTab == TAB_PROG);
        setPillState(tabConv, currentTab == TAB_CONV);
    }

    private void setPillState(Button b, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(100));
        if (active) {
            bg.setColor(c(R.attr.cSurface));
            b.setTextColor(c(R.attr.cAccentText));
        } else {
            bg.setColor(Color.TRANSPARENT);
            b.setTextColor(c(R.attr.cTextSecondary));
        }
        b.setBackground(bg);
    }

    // ---------- display ----------

    private View buildDisplay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(c(R.attr.cGroupBg));
        box.setPadding(dp(18), dp(10), dp(18), dp(14));
        box.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        tvExpr.setTextColor(c(R.attr.cTextSecondary));
        tvExpr.setGravity(Gravity.END);
        tvExpr.setText("");
        tvExpr.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        exprScroll.addView(tvExpr);
        box.addView(exprScroll);

        tvResult = new TextView(this);
        tvResult.setTextSize(42);
        tvResult.setTextColor(c(R.attr.cTextPrimary));
        tvResult.setTypeface(tvResult.getTypeface(), android.graphics.Typeface.BOLD);
        tvResult.setGravity(Gravity.END);
        tvResult.setText("0");
        tvResult.setSingleLine(true);
        tvResult.setAutoSizeTextTypeUniformWithConfiguration(16, 42, 1, TypedValue.COMPLEX_UNIT_SP);
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
        t.setTextSize(11);
        t.setTextColor(c(R.attr.cAccentText));
        t.setPadding(dp(6), 0, dp(6), 0);
        return t;
    }

    private void copyToClipboard(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("result", s));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    // ---------- scientific keypad ----------

    private View buildSciKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(6), dp(6), dp(6), dp(6));
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
        Button eq = Ui.key(this, "=", R.style.KeyOperator, 1f);
        eq.setTextSize(22);
        eq.setOnClickListener(v -> onEquals());
        eqRow.addView(eq);
        col.addView(eqRow);

        return col;
    }

    private void addSciRow(LinearLayout parent, String[] keys) {
        LinearLayout row = Ui.row(this);
        for (String k : keys) {
            int style = isDigitKey(k) ? R.style.KeyDigit : (isOperatorKey(k) ? R.style.KeyOperator : R.style.KeyFunction);
            Button b = Ui.key(this, k, style, 1f);
            if (style == R.style.KeyFunction) b.setTextSize(14);
            if (k.equals("DEG")) degKeyRef = b;
            b.setOnClickListener(v -> handleSciKey(k, b));
            row.addView(b);
        }
        parent.addView(row);
    }

    private Button degKeyRef;

    private boolean isDigitKey(String k) {
        return k.length() == 1 && Character.isDigit(k.charAt(0)) || k.equals(".");
    }

    private boolean isOperatorKey(String k) {
        return k.equals("÷") || k.equals("×") || k.equals("−") || k.equals("+") || k.equals("AC") || k.equals("=");
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
            case "MR": insert(Fmt.result(store.getMemory(), false)); return;
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
        btnDot(tvShiftDot, shiftOn);
        btnDot(tvHypDot, hypOn);
        btnDot(tvMemDot, store.getMemory() != 0);
    }

    private void btnDot(TextView t, boolean active) {
        t.setAlpha(active ? 1f : 0.25f);
    }

    // ---------- expression handling ----------

    private boolean isChainOperator(String t) {
        return t.equals("+") || t.equals("−") || t.equals("×") || t.equals("÷") || t.equals("^");
    }

    private void insert(String token) {
        if (lastWasResult) {
            currentExpr = isChainOperator(token) ? Fmt.result(lastAnswer, false) : "";
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

    private void livePreview() {
        if (currentExpr.trim().isEmpty()) { tvResult.setText("0"); return; }
        try {
            double v = Expr.eval(toEvalString(currentExpr), angleMode, () -> lastAnswer);
            tvResult.setText(Fmt.result(v, store.getThousands()));
        } catch (Exception ignored) {
        }
    }

    private void onEquals() {
        if (currentExpr.trim().isEmpty()) return;
        try {
            double v = Expr.eval(toEvalString(currentExpr), angleMode, () -> lastAnswer);
            String formatted = Fmt.result(v, store.getThousands());
            store.addHistory(currentExpr, formatted);
            tvResult.setText(formatted);
            lastAnswer = v;
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
        sciKeypad.setVisibility(tab == TAB_SCI ? View.VISIBLE : View.GONE);
        progPanel.setVisibility(tab == TAB_PROG ? View.VISIBLE : View.GONE);
        convPanel.setVisibility(tab == TAB_CONV ? View.VISIBLE : View.GONE);
        refreshTabPills();
        if (tab == TAB_PROG) updateProgDisplay();
        refreshIndicators();
    }

    // ---------- programmer mode ----------

    private View buildProgPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(6), dp(6), dp(6), dp(6));
        col.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.setVisibility(View.GONE);

        LinearLayout baseRow = new LinearLayout(this);
        baseRow.setOrientation(LinearLayout.HORIZONTAL);
        baseRow.setPadding(0, 0, 0, dp(6));
        baseDec = basePill(getString(R.string.base_dec), ProgrammerEngine.DEC);
        baseHex = basePill(getString(R.string.base_hex), ProgrammerEngine.HEX);
        baseOct = basePill(getString(R.string.base_oct), ProgrammerEngine.OCT);
        baseBin = basePill(getString(R.string.base_bin), ProgrammerEngine.BIN);
        baseRow.addView(baseDec);
        baseRow.addView(baseHex);
        baseRow.addView(baseOct);
        baseRow.addView(baseBin);
        col.addView(baseRow);

        LinearLayout convLines = new LinearLayout(this);
        convLines.setOrientation(LinearLayout.VERTICAL);
        convLines.setPadding(dp(4), dp(2), dp(4), dp(8));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> switchBase(base));
        return b;
    }

    private TextView progLine(String label) {
        TextView t = new TextView(this);
        t.setTextSize(13);
        t.setTextColor(c(R.attr.cTextSecondary));
        t.setGravity(Gravity.END);
        t.setSingleLine(true);
        return t;
    }

    private void addProgRow(LinearLayout parent, String[] keys, boolean hexRow) {
        LinearLayout row = Ui.row(this);
        for (String k : keys) {
            int style;
            if (k.equals("AC") || k.equals("DEL")) style = R.style.KeyOperator;
            else if (k.equals("=") || k.equals("÷") || k.equals("×") || k.equals("−") || k.equals("+")) style = R.style.KeyOperator;
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
        bg.setCornerRadius(dp(100));
        bg.setColor(active ? c(R.attr.cAccent) : c(R.attr.cField));
        b.setBackground(bg);
        b.setTextColor(active ? Color.WHITE : c(R.attr.cTextPrimary));
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
        col.setPadding(dp(18), dp(12), dp(18), dp(18));

        spCategory = new Spinner(this);
        String[] catLabels = new String[Converter.CATEGORIES.length];
        for (int i = 0; i < catLabels.length; i++) catLabels[i] = categoryLabel(Converter.CATEGORIES[i]);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catLabels);
        spCategory.setAdapter(catAdapter);
        col.addView(spCategory, matchWrap());

        addSpace(col, 16);

        etConvInput = new EditText(this);
        etConvInput.setHint("0");
        etConvInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etConvInput.setTextSize(20);
        col.addView(etConvInput, matchWrap());

        spFrom = new Spinner(this);
        col.addView(spFrom, matchWrap());

        addSpace(col, 10);
        TextView arrow = new TextView(this);
        arrow.setText("↓");
        arrow.setTextSize(20);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(c(R.attr.cAccentText));
        col.addView(arrow, matchWrap());
        addSpace(col, 10);

        tvConvResult = new TextView(this);
        tvConvResult.setTextSize(28);
        tvConvResult.setTypeface(tvConvResult.getTypeface(), android.graphics.Typeface.BOLD);
        tvConvResult.setTextColor(c(R.attr.cAccentText));
        col.addView(tvConvResult, matchWrap());

        spTo = new Spinner(this);
        col.addView(spTo, matchWrap());

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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        spFrom.setAdapter(adapter);
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
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
        tvConvResult.setText(Fmt.result(out, store.getThousands()) + " " + to);
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
