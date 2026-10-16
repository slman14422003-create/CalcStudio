package com.calc.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private static final String[][][] ADV_CATS = {
            {
                    {"sum", "avg", "median", "min", "max"},
                    {"stdev", "stdevp", "var", "varp", "prod"},
                    {"gcd", "lcm", "mod", "rand", "randint"}
            },
            {
                    {"floor", "ceil", "round", "trunc", "frac"},
                    {"sign", "abs", "isprime", "nextprime", "fact"},
                    {"sqrt", "cbrt", "lg", "acot", "coth"}
            },
            {
                    {"pow", "hypot", "logb", "log2", "exp"},
                    {"atan2", "cot", "sec", "csc", "gamma"},
                    {"erf", "deg", "rad", "\u03c6", "\u03c4"}
            }
    };

    private interface KeyHandler { void onKey(String key, Button b); }

    private Store store;
    private int currentTab = TAB_SCI;
    private int angleMode = Expr.DEG;
    private boolean shiftOn = false;
    private boolean hypOn = false;
    private boolean landscape = false;
    private double lastAnswer = 0;
    private boolean lastWasResult = false;
    private boolean resultIsError = false;

    private String currentExpr = "";

    private TextView tvSubtitle;
    private TextView tvExpr;
    private TextView tvResult;
    private TextView tvAngle;
    private TextView tvShiftDot;
    private TextView tvHypDot;
    private TextView tvMemDot;
    private HorizontalScrollView exprScroll;
    private View displayBox;

    private LinearLayout navBar;
    private final View[] navItems = new View[5];
    private final ImageView[] navIcons = new ImageView[5];
    private final TextView[] navLabels = new TextView[5];
    private String[] tabLabels;

    private FrameLayout panelContainer;
    private View basicKeypad, sciKeypad, progPanel, convPanel, advKeypad;
    private BigDecimal lastBig = null;
    private BigDecimal pendingBig = null;
    private double pendingAns = 0;

    private final Map<String, List<Button>> opButtons = new HashMap<>();
    private final Map<String, Button> sciKeys = new HashMap<>();

    private LinearLayout advGrid;
    private final TextView[] advChips = new TextView[3];
    private int advCategory = 0;

    private int progBase = ProgrammerEngine.DEC;
    private int progBits = 64;
    private String progEntry = "0";
    private long progAcc = 0;
    private String progPendingOp = null;
    private boolean progReplace = false;
    private String progLastExpr = "";
    private final View[] progBaseRows = new View[4];
    private final TextView[] progBaseLabels = new TextView[4];
    private final TextView[] progBaseValues = new TextView[4];
    private final TextView[] progBitChips = new TextView[4];
    private final Button[] progDigitBtns = new Button[16];
    private TextView tvProgExpr;

    private TextView tvConvIn;
    private TextView tvConvOut;
    private TextView tvConvRate;
    private TextView tvFromUnit;
    private TextView tvToUnit;
    private TextView[] convChips;
    private int convCategory = 0;
    private String convText = "";
    private String convFrom = "";
    private String convTo = "";

    private ActivityResultLauncher<Intent> historyLauncher;

    // ---------- lifecycle ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        store = new Store(this);
        Fmt.apply(store);
        angleMode = store.getAngleMode();
        currentTab = Math.max(0, Math.min(TAB_ADV, store.getLastTab()));
        progBase = store.getLastBase();
        progBits = store.getProgBits();
        convCategory = Math.max(0, Math.min(Converter.CATEGORIES.length - 1, store.getConvCategory()));
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        historyLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getData() != null) {
                String restore = result.getData().getStringExtra(EXTRA_RESTORE);
                if (restore != null) {
                    if (currentTab == TAB_PROG || currentTab == TAB_CONV) switchTab(TAB_SCI);
                    currentExpr = restore;
                    lastWasResult = false;
                    showExpr(currentExpr);
                    livePreview();
                }
            }
        });

        buildUi();
        if (savedInstanceState != null) restoreState(savedInstanceState);
        switchTab(currentTab);
        updateAngleLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Fmt.apply(store);
        angleMode = store.getAngleMode();
        updateAngleLabel();
        if (store.getKeepOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showExpr(currentExpr);
        if (!lastWasResult) livePreview();
        doConvert();
        updateProgDisplay();
        refreshIndicators();
    }

    @Override
    protected void onPause() {
        super.onPause();
        store.setAngleMode(angleMode);
        store.setLastTab(currentTab);
        store.setLastBase(progBase);
        store.setProgBits(progBits);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("expr", currentExpr);
        out.putBoolean("isResult", lastWasResult);
        out.putBoolean("isError", resultIsError);
        out.putString("result", tvResult.getText().toString());
        out.putDouble("ans", lastAnswer);
        out.putBoolean("shift", shiftOn);
        out.putBoolean("hyp", hypOn);
        out.putString("convText", convText);
        out.putString("progEntry", progEntry);
        out.putLong("progAcc", progAcc);
        out.putString("progOp", progPendingOp);
        out.putBoolean("progReplace", progReplace);
    }

    private void restoreState(Bundle in) {
        currentExpr = in.getString("expr", "");
        lastWasResult = in.getBoolean("isResult", false);
        resultIsError = in.getBoolean("isError", false);
        lastAnswer = in.getDouble("ans", 0);
        shiftOn = in.getBoolean("shift", false);
        hypOn = in.getBoolean("hyp", false);
        convText = in.getString("convText", "");
        progEntry = in.getString("progEntry", "0");
        progAcc = in.getLong("progAcc", 0);
        progPendingOp = in.getString("progOp", null);
        progReplace = in.getBoolean("progReplace", false);
        showExpr(currentExpr);
        String res = in.getString("result", "0");
        setResult(res, resultIsError);
        doConvert();
        updateProgDisplay();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        applyNavLayout();
        applyWeights(currentTab);
    }

    private int c(int attr) { return Ui.attrColor(this, attr); }
    private int dp(int v) { return Ui.dp(this, v); }

    // ---------- root layout ----------

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

        root.addView(buildNavBar());
        setContentView(root);
    }

    private FrameLayout.LayoutParams panelLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(12), dp(2), dp(12), dp(4));
        return lp;
    }

    // ---------- top bar ----------

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(2));
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageButton btnHistory = iconButton(R.drawable.ic_history);
        btnHistory.setContentDescription(getString(R.string.action_history));
        btnHistory.setOnClickListener(v -> historyLauncher.launch(new Intent(this, HistoryActivity.class)));
        bar.addView(btnHistory);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        titleBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(c(R.attr.cTextPrimary));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        titleBox.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvSubtitle = new TextView(this);
        tvSubtitle.setTextSize(12);
        tvSubtitle.setLetterSpacing(0.04f);
        tvSubtitle.setTextColor(c(R.attr.cAccentText));
        tvSubtitle.setGravity(Gravity.CENTER);
        tvSubtitle.setSingleLine(true);
        titleBox.addView(tvSubtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.addView(titleBox);

        ImageButton btnSettings = iconButton(R.drawable.ic_settings);
        btnSettings.setContentDescription(getString(R.string.action_settings));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(btnSettings);
        return bar;
    }

    private ImageButton iconButton(int drawableRes) {
        ImageButton b = new ImageButton(this, null, 0, R.style.IconButton);
        b.setImageResource(drawableRes);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return b;
    }

    // ---------- bottom navigation ----------

    private View buildNavBar() {
        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackgroundResource(R.drawable.bg_nav);
        navBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        blp.setMargins(dp(12), dp(4), dp(12), dp(10));
        navBar.setLayoutParams(blp);

        tabLabels = new String[]{
                getString(R.string.tab_basic), getString(R.string.tab_sci), getString(R.string.tab_prog),
                getString(R.string.tab_conv), getString(R.string.tab_adv)
        };
        int[] icons = {R.drawable.ic_nav_basic, R.drawable.ic_nav_sci, R.drawable.ic_nav_prog, R.drawable.ic_nav_conv, R.drawable.ic_nav_adv};

        for (int i = 0; i < 5; i++) {
            final int tabId = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(tabLabels[i]);
            item.setOnClickListener(v -> switchTab(tabId));

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView label = new TextView(this);
            label.setText(tabLabels[i]);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(11);
            Ui.autoFit(label, 8, 11);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
            llp.topMargin = dp(2);
            llp.setMarginStart(dp(2));
            llp.setMarginEnd(dp(2));
            item.addView(label, llp);

            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            ilp.setMargins(dp(2), 0, dp(2), 0);
            navBar.addView(item, ilp);
            navItems[i] = item;
            navIcons[i] = icon;
            navLabels[i] = label;
        }
        applyNavLayout();
        return navBar;
    }

    private void applyNavLayout() {
        if (navBar == null) return;
        ViewGroup.LayoutParams lp = navBar.getLayoutParams();
        lp.height = dp(landscape ? 46 : 64);
        navBar.setLayoutParams(lp);
        for (TextView t : navLabels) t.setVisibility(landscape ? View.GONE : View.VISIBLE);
    }

    private void refreshNav() {
        for (int i = 0; i < 5; i++) {
            boolean sel = i == currentTab;
            navItems[i].setBackgroundResource(sel ? R.drawable.bg_nav_item_selected : R.drawable.bg_nav_item_idle);
            int color = sel ? c(R.attr.cAccentText) : c(R.attr.cTextSecondary);
            navIcons[i].setImageTintList(ColorStateList.valueOf(color));
            navLabels[i].setTextColor(color);
            navLabels[i].setTypeface(Typeface.create(sel ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
    }

    // ---------- display ----------

    private View buildDisplay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_display);
        box.setPadding(dp(18), dp(12), dp(18), dp(14));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.setMargins(dp(12), dp(6), dp(12), dp(8));
        box.setLayoutParams(blp);

        LinearLayout indicators = new LinearLayout(this);
        indicators.setOrientation(LinearLayout.HORIZONTAL);
        indicators.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        indicators.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvAngle = indicatorText(getString(R.string.deg));
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

        View spacer = new View(this);
        indicators.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ImageButton copy = new ImageButton(this, null, 0, R.style.IconButton);
        copy.setImageResource(R.drawable.ic_copy);
        copy.setImageTintList(ColorStateList.valueOf(c(R.attr.cTextSecondary)));
        copy.setContentDescription(getString(R.string.action_copy));
        copy.setOnClickListener(v -> copyResult());
        indicators.addView(copy, new LinearLayout.LayoutParams(dp(34), dp(34)));
        box.addView(indicators);

        exprScroll = new HorizontalScrollView(this);
        exprScroll.setHorizontalScrollBarEnabled(false);
        exprScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        exprScroll.setFillViewport(true);
        LinearLayout.LayoutParams esLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        esLp.topMargin = dp(4);
        exprScroll.setLayoutParams(esLp);
        tvExpr = new TextView(this);
        tvExpr.setTextSize(24);
        tvExpr.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvExpr.setMinHeight(dp(34));
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
        tvResult.setMinHeight(dp(44));
        Ui.autoFit(tvResult, 28, 72);
        tvResult.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tvResult.setOnLongClickListener(v -> {
            copyResult();
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
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        Ui.setTagState(t, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(6));
        t.setLayoutParams(lp);
        return t;
    }

    private void showExpr(String raw) {
        tvExpr.setText(Fmt.prettyExpr(raw, store.getThousands()));
        exprScroll.post(() -> exprScroll.fullScroll(View.FOCUS_RIGHT));
        refreshOperatorHighlight();
    }

    private void setResult(String text, boolean error) {
        resultIsError = error;
        tvResult.setText(text);
        tvResult.setTextColor(error ? getColor(R.color.bad) : c(R.attr.cTextPrimary));
    }

    private void copyResult() {
        if (resultIsError) return;
        copyToClipboard(tvResult.getText().toString());
    }

    private void copyToClipboard(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("result", s));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    // ---------- shared key helpers ----------

    private static boolean isOperatorKey(String k) {
        return k.equals("\u00f7") || k.equals("\u00d7") || k.equals("\u2212") || k.equals("+");
    }

    private static boolean isDigitKey(String k) {
        return (k.length() == 1 && Character.isDigit(k.charAt(0))) || k.equals(".");
    }

    private int styleFor(String k) {
        if (k.equals("AC")) return R.style.KeyDanger;
        if (k.equals("=")) return R.style.KeyEquals;
        if (isDigitKey(k)) return R.style.KeyDigit;
        if (isOperatorKey(k)) return R.style.KeyOperator;
        return R.style.KeyFunction;
    }

    private LinearLayout addKeys(LinearLayout parent, String[] keys, float[] weights, float rowWeight,
                                 boolean compact, int gapDp, Map<String, Button> registry, KeyHandler handler) {
        LinearLayout row = Ui.row(this, rowWeight);
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            float w = weights == null ? 1f : weights[i];
            if (k == null) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w));
                continue;
            }
            int style = styleFor(k);
            final Button b = Ui.key(this, k, style, w, gapDp);
            Ui.styleKey(b, style, compact);
            b.setOnClickListener(v -> { vibrate(b); handler.onKey(k, b); });
            if (k.equals("DEL")) {
                b.setOnLongClickListener(v -> { vibrate(b); handler.onKey("DEL_LONG", b); return true; });
            }
            if (isOperatorKey(k)) {
                List<Button> list = opButtons.get(k);
                if (list == null) { list = new ArrayList<>(); opButtons.put(k, list); }
                list.add(b);
            }
            if (registry != null) registry.put(k, b);
            row.addView(b);
        }
        parent.addView(row);
        return row;
    }

    private void refreshOperatorHighlight() {
        String last = "";
        if (!lastWasResult && !currentExpr.isEmpty()) last = currentExpr.substring(currentExpr.length() - 1);
        for (Map.Entry<String, List<Button>> e : opButtons.entrySet()) {
            boolean on = e.getKey().equals(last);
            for (Button b : e.getValue()) {
                b.setBackgroundResource(on ? R.drawable.key_operator_active_bg : R.drawable.key_operator_bg);
                b.setTextColor(on ? getColor(R.color.on_accent) : getColor(R.color.key_op_soft_text));
            }
        }
    }

    private void vibrate(View v) {
        if (store.getHaptic()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    // ---------- basic keypad ----------

    private View buildBasicKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

        String[][] rows = {
                {"AC", "\u00b1", "%", "\u00f7"},
                {"7", "8", "9", "\u00d7"},
                {"4", "5", "6", "\u2212"},
                {"1", "2", "3", "+"},
                {"0", ".", "DEL", "="},
        };
        KeyHandler h = (k, b) -> handleBasicKey(k);
        for (String[] r : rows) addKeys(col, r, null, 1f, false, 4, null, h);
        return col;
    }

    private void handleBasicKey(String key) {
        switch (key) {
            case "AC":
            case "DEL_LONG": clearAll(); return;
            case "DEL": backspace(); return;
            case "=": onEquals(); return;
            case "\u00b1": negate(); return;
            default: insert(key);
        }
    }

    private void negate() {
        String e = lastWasResult ? lastAnswerPlain() : currentExpr;
        lastWasResult = false;
        if (e.isEmpty()) {
            currentExpr = "\u2212";
        } else if (e.startsWith("\u2212(") && e.endsWith(")")) {
            currentExpr = e.substring(2, e.length() - 1);
        } else {
            currentExpr = "\u2212(" + e + ")";
        }
        showExpr(currentExpr);
        livePreview();
    }

    // ---------- scientific keypad ----------

    private View buildSciKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

        KeyHandler h = (k, b) -> handleSciKey(k);
        addKeys(col, new String[]{"sin", "cos", "tan", "log", "ln"}, null, 0.85f, true, 3, sciKeys, h);
        addKeys(col, new String[]{"x\u00b2", "\u221a", "x\u02b8", "\u02e3\u221ay", "n!"}, null, 0.85f, true, 3, sciKeys, h);
        addKeys(col, new String[]{"(", ")", "\u03c0", "e", "1/x"}, null, 0.85f, true, 3, sciKeys, h);
        addKeys(col, new String[]{"\u221b", "%", "nPr", "nCr", "\u00b1"}, null, 0.85f, true, 3, sciKeys, h);
        addKeys(col, new String[]{"MC", "MR", "M+", "M\u2212", "Ans"}, null, 0.85f, true, 3, sciKeys, h);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        col.addView(gap);

        addKeys(col, new String[]{"7", "8", "9", "DEL", "AC"}, null, 1f, true, 3, null, h);
        addKeys(col, new String[]{"4", "5", "6", "\u00d7", "\u00f7"}, null, 1f, true, 3, null, h);
        addKeys(col, new String[]{"1", "2", "3", "+", "\u2212"}, null, 1f, true, 3, null, h);
        addKeys(col, new String[]{"0", ".", "EXP", "e\u02e3", "="}, null, 1f, true, 3, sciKeys, h);
        return col;
    }

    private void handleSciKey(String key) {
        switch (key) {
            case "AC":
            case "DEL_LONG": clearAll(); return;
            case "DEL": backspace(); return;
            case "=": onEquals(); return;
            case "\u00b1": negate(); return;
            case "EXP": insert("E"); return;
            case "e\u02e3": insert("e^("); return;
            case "n!": insert("!"); return;
            case "x\u00b2": insert(shiftOn ? "\u00b3" : "\u00b2"); consumeShift(false); return;
            case "1/x": insert("\u207b\u00b9"); return;
            case "\u221a": insert(shiftOn ? "\u221b(" : "\u221a("); consumeShift(false); return;
            case "\u221b": insert("\u221b("); return;
            case "x\u02b8": insert("^"); return;
            case "\u02e3\u221ay": insert("root("); return;
            case "nPr": insert("nPr("); return;
            case "nCr": insert("nCr("); return;
            case "MC": store.setMemory(0); refreshIndicators(); toast(R.string.memory_cleared); return;
            case "MR": insert(plainNum(store.getMemory())); return;
            case "M+": store.setMemory(store.getMemory() + currentNumericValue()); refreshIndicators(); toast(R.string.memory_saved); return;
            case "M\u2212": store.setMemory(store.getMemory() - currentNumericValue()); refreshIndicators(); toast(R.string.memory_saved); return;
            case "sin": insertTrig("sin", "asin", "sinh", "asinh"); return;
            case "cos": insertTrig("cos", "acos", "cosh", "acosh"); return;
            case "tan": insertTrig("tan", "atan", "tanh", "atanh"); return;
            case "log": insert(shiftOn ? "10^(" : "log("); consumeShift(false); return;
            case "ln": insert(shiftOn ? "e^(" : "ln("); consumeShift(false); return;
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
        consumeShift(true);
    }

    private void consumeShift(boolean alsoHyp) {
        if (!shiftOn && !(alsoHyp && hypOn)) return;
        shiftOn = false;
        if (alsoHyp) hypOn = false;
        refreshIndicators();
    }

    private void refreshShiftLabels() {
        setSciLabel("sin", hypOn ? (shiftOn ? "asinh" : "sinh") : (shiftOn ? "sin\u207b\u00b9" : "sin"));
        setSciLabel("cos", hypOn ? (shiftOn ? "acosh" : "cosh") : (shiftOn ? "cos\u207b\u00b9" : "cos"));
        setSciLabel("tan", hypOn ? (shiftOn ? "atanh" : "tanh") : (shiftOn ? "tan\u207b\u00b9" : "tan"));
        setSciLabel("log", shiftOn ? "10\u02e3" : "log");
        setSciLabel("ln", shiftOn ? "e\u02e3" : "ln");
        setSciLabel("x\u00b2", shiftOn ? "x\u00b3" : "x\u00b2");
        setSciLabel("\u221a", shiftOn ? "\u221b" : "\u221a");
    }

    private void setSciLabel(String key, String text) {
        Button b = sciKeys.get(key);
        if (b != null) b.setText(text);
    }

    private double currentNumericValue() {
        try {
            String clean = currentExpr.isEmpty() ? tvResult.getText().toString() : autoClose(currentExpr);
            return Expr.eval(clean, angleMode, () -> lastAnswer);
        } catch (Exception e) {
            try { return Double.parseDouble(tvResult.getText().toString().replace(",", "")); } catch (Exception e2) { return 0; }
        }
    }

    private void refreshIndicators() {
        Ui.setTagState(tvShiftDot, shiftOn);
        Ui.setTagState(tvHypDot, hypOn);
        boolean hasMem = store.getMemory() != 0;
        Ui.setTagState(tvMemDot, hasMem);
        Ui.setTagState(tvAngle, false);
        tvAngle.setTextColor(c(R.attr.cAccentText));
        boolean sci = currentTab == TAB_SCI;
        tvAngle.setVisibility(currentTab == TAB_BASIC ? View.GONE : View.VISIBLE);
        tvShiftDot.setVisibility(sci ? View.VISIBLE : View.GONE);
        tvHypDot.setVisibility(sci ? View.VISIBLE : View.GONE);
        tvMemDot.setVisibility(sci || hasMem ? View.VISIBLE : View.GONE);
        refreshShiftLabels();
    }

    // ---------- advanced keypad ----------

    private View buildAdvKeypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = {getString(R.string.adv_stats), getString(R.string.adv_number), getString(R.string.adv_math)};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = Ui.chip(this, names[i]);
            chip.setOnClickListener(v -> showAdvCategory(idx));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(34), 1f);
            clp.setMargins(dp(3), 0, dp(3), 0);
            chips.addView(chip, clp);
            advChips[i] = chip;
        }
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        chipsLp.setMargins(0, dp(2), 0, dp(8));
        col.addView(chips, chipsLp);

        advGrid = new LinearLayout(this);
        advGrid.setOrientation(LinearLayout.VERTICAL);
        col.addView(advGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f));

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        col.addView(gap);

        KeyHandler h = (k, b) -> handleAdvKey(k);
        addKeys(col, new String[]{"(", ")", ",", "DEL", "AC"}, null, 1f, true, 3, null, h);
        addKeys(col, new String[]{"7", "8", "9", "\u00f7", "\u00d7"}, null, 1f, true, 3, null, h);
        addKeys(col, new String[]{"4", "5", "6", "\u2212", "+"}, null, 1f, true, 3, null, h);
        addKeys(col, new String[]{"1", "2", "3", "\u03c0", "e"}, null, 1f, true, 3, null, h);
        addKeys(col, new String[]{"0", ".", "Ans", "="}, new float[]{1f, 1f, 1f, 2f}, 1f, true, 3, null, h);

        showAdvCategory(advCategory);
        return col;
    }

    private void showAdvCategory(int idx) {
        advCategory = idx;
        advGrid.removeAllViews();
        KeyHandler h = (k, b) -> handleAdvKey(k);
        for (String[] row : ADV_CATS[idx]) addKeys(advGrid, row, null, 1f, true, 3, null, h);
        for (int i = 0; i < advChips.length; i++) Ui.setChipState(advChips[i], i == idx);
    }

    private void handleAdvKey(String key) {
        switch (key) {
            case "AC":
            case "DEL_LONG": clearAll(); return;
            case "DEL": backspace(); return;
            case "=": onEquals(); return;
            case "(": case ")": case ",": case "\u03c0": case "e": case "Ans": case ".":
            case "\u00f7": case "\u00d7": case "\u2212": case "+":
                insert(key); return;
            case "\u03c6": insert("phi"); return;
            case "\u03c4": insert("tau"); return;
            default:
                if (isDigitKey(key)) insert(key);
                else insert(key + "(");
        }
    }

    // ---------- expression handling ----------

    private static boolean isBinOp(char ch) {
        return ch == '+' || ch == '\u2212' || ch == '\u00d7' || ch == '\u00f7';
    }

    private static boolean isChainToken(String t) {
        return t.equals("+") || t.equals("\u2212") || t.equals("\u00d7") || t.equals("\u00f7") || t.equals("^")
                || t.equals("\u00b2") || t.equals("\u00b3") || t.equals("!") || t.equals("%") || t.equals("\u207b\u00b9");
    }

    private void insert(String token) {
        if (lastWasResult) {
            currentExpr = isChainToken(token) ? lastAnswerPlain() : "";
            lastWasResult = false;
        }
        if (token.equals(".")) {
            int i = currentExpr.length() - 1;
            boolean hasDot = false;
            while (i >= 0) {
                char ch = currentExpr.charAt(i);
                if (ch == '.') { hasDot = true; break; }
                if (!Character.isDigit(ch)) break;
                i--;
            }
            if (hasDot) return;
            boolean prevDigit = !currentExpr.isEmpty() && Character.isDigit(currentExpr.charAt(currentExpr.length() - 1));
            if (!prevDigit) token = "0.";
        }
        if (token.length() == 1 && isBinOp(token.charAt(0))) {
            if (currentExpr.isEmpty() && (token.equals("\u00d7") || token.equals("\u00f7"))) return;
            if (!currentExpr.isEmpty()) {
                char last = currentExpr.charAt(currentExpr.length() - 1);
                if (isBinOp(last)) {
                    boolean allowUnary = token.equals("\u2212") && (last == '\u00d7' || last == '\u00f7');
                    if (!allowUnary) {
                        currentExpr = currentExpr.substring(0, currentExpr.length() - 1) + token;
                        showExpr(currentExpr);
                        livePreview();
                        return;
                    }
                }
            }
        }
        currentExpr += token;
        showExpr(currentExpr);
        livePreview();
    }

    private void backspace() {
        if (lastWasResult) { clearAll(); return; }
        if (currentExpr.isEmpty()) return;
        String[] words = {"asinh(", "acosh(", "atanh(", "sinh(", "cosh(", "tanh(", "asin(", "acos(", "atan(",
                "sin(", "cos(", "tan(", "log(", "ln(", "root(", "nPr(", "nCr(", "10^(", "e^(", "\u207b\u00b9"};
        for (String w : words) {
            if (currentExpr.endsWith(w)) {
                currentExpr = currentExpr.substring(0, currentExpr.length() - w.length());
                showExpr(currentExpr);
                livePreview();
                return;
            }
        }
        currentExpr = currentExpr.substring(0, currentExpr.length() - 1);
        showExpr(currentExpr);
        livePreview();
    }

    private void clearAll() {
        currentExpr = "";
        lastWasResult = false;
        showExpr("");
        setResult("0", false);
    }

    private String lastAnswerPlain() {
        if (store.getHighPrecision() && lastBig != null) return lastBig.stripTrailingZeros().toPlainString();
        return plainNum(lastAnswer);
    }

    private static String plainNum(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static String autoClose(String s) {
        int open = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '(') open++;
            else if (ch == ')' && open > 0) open--;
        }
        if (open == 0) return s;
        StringBuilder b = new StringBuilder(s);
        for (int i = 0; i < open; i++) b.append(')');
        return b.toString();
    }

    private static String previewForm(String s) {
        int end = s.length();
        while (end > 0 && "+\u2212\u00d7\u00f7^,".indexOf(s.charAt(end - 1)) >= 0) end--;
        return s.substring(0, end);
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
        double v = Expr.eval(expr, angleMode, () -> lastAnswer);
        pendingAns = v;
        return Fmt.result(v, store.getThousands());
    }

    private void livePreview() {
        if (currentExpr.trim().isEmpty()) { setResult("0", false); return; }
        if (!store.getAutoPreview()) return;
        String e = previewForm(currentExpr);
        if (e.isEmpty()) return;
        try {
            setResult(compute(autoClose(e)), false);
        } catch (Exception ignored) {
        }
    }

    private void onEquals() {
        if (currentExpr.trim().isEmpty()) return;
        String expr = autoClose(currentExpr.trim());
        try {
            String formatted = compute(expr);
            store.addHistory(expr, formatted);
            currentExpr = expr;
            lastAnswer = pendingAns;
            lastBig = pendingBig;
            lastWasResult = true;
            showExpr(currentExpr);
            setResult(formatted, false);
        } catch (Expr.EvalException ex) {
            setResult(Fmt.errorMessage(ex, this), true);
        } catch (Exception ex) {
            setResult(getString(R.string.error_math), true);
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
        tvSubtitle.setText(tabLabels[tab]);
        refreshNav();
        if (tab == TAB_PROG) updateProgDisplay();
        refreshIndicators();
    }

    private void applyWeights(int tab) {
        float displayWeight;
        float panelWeight;
        if (landscape) {
            displayWeight = (tab == TAB_PROG || tab == TAB_CONV) ? 0f : 0.6f;
            panelWeight = 3f;
        } else if (tab == TAB_BASIC) {
            displayWeight = 1.0f;
            panelWeight = 2.3f;
        } else if (tab == TAB_SCI || tab == TAB_ADV) {
            displayWeight = 1.0f;
            panelWeight = 3.1f;
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

    // ---------- programmer mode ----------

    private View buildProgPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        String[] bitNames = {"QWORD", "DWORD", "WORD", "BYTE"};
        for (int i = 0; i < 4; i++) {
            final int bits = ProgrammerEngine.WORD_BITS[i];
            TextView chip = Ui.chip(this, bitNames[i]);
            chip.setTextSize(11);
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setOnClickListener(v -> setProgBits(bits));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(32), 1f);
            clp.setMargins(dp(3), 0, dp(3), 0);
            head.addView(chip, clp);
            progBitChips[i] = chip;
        }
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        headLp.setMargins(0, dp(4), 0, dp(6));
        col.addView(head, headLp);

        LinearLayout baseCard = Ui.card(this);
        baseCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        col.addView(baseCard, Ui.matchWrap(this, 0, 4, 0));

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
            r.setOnLongClickListener(v -> {
                copyToClipboard(rawBaseValue(base));
                return true;
            });

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
            if (base == ProgrammerEngine.BIN) Ui.autoFitLines(value, 9, 18, 2);
            else Ui.autoFit(value, 11, 22);
            r.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            baseCard.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(base == ProgrammerEngine.BIN ? 64 : 44)));
            progBaseRows[i] = r;
            progBaseLabels[i] = label;
            progBaseValues[i] = value;
        }

        tvProgExpr = new TextView(this);
        tvProgExpr.setTextSize(14);
        tvProgExpr.setTypeface(Typeface.MONOSPACE);
        tvProgExpr.setTextColor(c(R.attr.cTextSecondary));
        tvProgExpr.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        tvProgExpr.setSingleLine(true);
        tvProgExpr.setPadding(dp(8), 0, dp(8), 0);
        col.addView(tvProgExpr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        col.addView(keys, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addProgRow(keys, new String[]{"A", "B", "C", "D", "E", "F"}, null, 0.8f);
        addProgRow(keys, new String[]{"AND", "OR", "XOR", "NOT", "<<"}, null, 0.85f);
        addProgRow(keys, new String[]{"NAND", "NOR", ">>", "MOD", "\u00b1"}, null, 0.85f);
        addProgRow(keys, new String[]{"7", "8", "9", "DEL", "AC"}, null, 1f);
        addProgRow(keys, new String[]{"4", "5", "6", "\u00d7", "\u00f7"}, null, 1f);
        addProgRow(keys, new String[]{"1", "2", "3", "+", "\u2212"}, null, 1f);
        addProgRow(keys, new String[]{"0", "="}, new float[]{2f, 3f}, 1f);
        return col;
    }

    private int progStyle(String k) {
        if (k.equals("AC")) return R.style.KeyDanger;
        if (k.equals("=")) return R.style.KeyEquals;
        if (isOperatorKey(k)) return R.style.KeyOperator;
        if (k.matches("[0-9A-F]")) return R.style.KeyDigit;
        return R.style.KeyFunction;
    }

    private void addProgRow(LinearLayout parent, String[] keys, float[] weights, float rowWeight) {
        LinearLayout row = Ui.row(this, rowWeight);
        for (int i = 0; i < keys.length; i++) {
            final String k = keys[i];
            int style = progStyle(k);
            float w = weights == null ? 1f : weights[i];
            final Button b = Ui.key(this, k, style, w, 3);
            Ui.styleKey(b, style, true);
            if (style == R.style.KeyDigit && !k.matches("[0-9]")) b.setTextSize(20);
            b.setOnClickListener(v -> { vibrate(b); handleProgKey(k); });
            if (k.equals("DEL")) {
                b.setOnLongClickListener(v -> { vibrate(b); progClear(); return true; });
            }
            row.addView(b);
            if (k.matches("[0-9A-F]")) progDigitBtns[Character.digit(k.charAt(0), 16)] = b;
        }
        parent.addView(row);
    }

    private long progValue() {
        try {
            return ProgrammerEngine.parse(progEntry, progBase, progBits);
        } catch (Exception e) {
            return 0;
        }
    }

    private String progFmt(long v, int base) {
        return ProgrammerEngine.format(v, base, progBits);
    }

    private void progSetValue(long v) {
        progEntry = progFmt(v, progBase);
        updateProgDisplay();
    }

    private String rawBaseValue(int base) {
        return progFmt(progValue(), base);
    }

    private void switchBase(int base) {
        long cur = progValue();
        progBase = base;
        progEntry = progFmt(cur, base);
        updateProgDisplay();
    }

    private void setProgBits(int bits) {
        long cur = ProgrammerEngine.wrap(progValue(), bits);
        progBits = bits;
        progAcc = ProgrammerEngine.wrap(progAcc, bits);
        progEntry = progFmt(cur, progBase);
        store.setProgBits(bits);
        updateProgDisplay();
    }

    private void handleProgKey(String key) {
        if (key.matches("[0-9A-F]")) { progDigit(key); return; }
        switch (key) {
            case "AC": progClear(); return;
            case "DEL": progBackspace(); return;
            case "\u00b1": progSetValue(ProgrammerEngine.wrap(-progValue(), progBits)); return;
            case "NOT": progSetValue(ProgrammerEngine.not(progValue(), progBits)); progReplace = true; return;
            case "=": progEquals(); return;
            default: progOperator(key);
        }
    }

    private void progClear() {
        progAcc = 0;
        progPendingOp = null;
        progEntry = "0";
        progReplace = false;
        updateProgDisplay();
    }

    private void progBackspace() {
        if (progEntry.length() <= 1 || (progEntry.length() == 2 && progEntry.startsWith("-"))) progEntry = "0";
        else progEntry = progEntry.substring(0, progEntry.length() - 1);
        updateProgDisplay();
    }

    private void progDigit(String d) {
        if (!ProgrammerEngine.validDigit(d.charAt(0), progBase)) return;
        String start = progReplace ? "0" : progEntry;
        String next = start.equals("0") ? d : start + d;
        if (!ProgrammerEngine.fits(next, progBase, progBits)) {
            toast(R.string.error_word_limit);
            return;
        }
        progReplace = false;
        progEntry = next;
        updateProgDisplay();
    }

    private void progOperator(String op) {
        if (progPendingOp != null && !progReplace) {
            if (!progCompute()) return;
        } else if (progPendingOp == null) {
            progAcc = progValue();
        }
        progPendingOp = op;
        progReplace = true;
        updateProgDisplay();
    }

    private void progEquals() {
        if (progPendingOp == null) return;
        if (!progCompute()) return;
        store.addHistory(progLastExpr, progEntry + " (" + baseLabel(progBase) + ")", HistoryItem.KIND_PROG);
        progPendingOp = null;
        progReplace = true;
        updateProgDisplay();
    }

    private boolean progCompute() {
        long a = progAcc;
        long b = progValue();
        int bits = progBits;
        progLastExpr = progFmt(a, progBase) + " " + progPendingOp + " " + progFmt(b, progBase);
        long r;
        switch (progPendingOp) {
            case "AND": r = ProgrammerEngine.and(a, b); break;
            case "OR": r = ProgrammerEngine.or(a, b); break;
            case "XOR": r = ProgrammerEngine.xor(a, b); break;
            case "NAND": r = ProgrammerEngine.nand(a, b, bits); break;
            case "NOR": r = ProgrammerEngine.nor(a, b, bits); break;
            case "<<": r = ProgrammerEngine.shl(a, b, bits); break;
            case ">>": r = ProgrammerEngine.shr(a, b, bits); break;
            case "+": r = ProgrammerEngine.wrap(a + b, bits); break;
            case "\u2212": r = ProgrammerEngine.wrap(a - b, bits); break;
            case "\u00d7": r = ProgrammerEngine.wrap(a * b, bits); break;
            case "\u00f7":
                if (b == 0) { progFail(); return false; }
                r = ProgrammerEngine.wrap(a / b, bits);
                break;
            case "MOD":
                if (b == 0) { progFail(); return false; }
                r = ProgrammerEngine.wrap(a % b, bits);
                break;
            default: r = b;
        }
        progAcc = r;
        progEntry = progFmt(r, progBase);
        return true;
    }

    private void progFail() {
        toast(R.string.error_div_zero);
        progAcc = 0;
        progPendingOp = null;
        progEntry = "0";
        progReplace = true;
        updateProgDisplay();
    }

    private String baseLabel(int base) {
        if (base == ProgrammerEngine.HEX) return getString(R.string.base_hex);
        if (base == ProgrammerEngine.OCT) return getString(R.string.base_oct);
        if (base == ProgrammerEngine.BIN) return getString(R.string.base_bin);
        return getString(R.string.base_dec);
    }

    private void updateProgDisplay() {
        long v = progValue();
        progBaseValues[0].setText(ProgrammerEngine.grouped(progFmt(v, ProgrammerEngine.DEC), ProgrammerEngine.DEC));
        progBaseValues[1].setText(ProgrammerEngine.grouped(progFmt(v, ProgrammerEngine.HEX), ProgrammerEngine.HEX));
        progBaseValues[2].setText(ProgrammerEngine.grouped(progFmt(v, ProgrammerEngine.OCT), ProgrammerEngine.OCT));
        progBaseValues[3].setText(ProgrammerEngine.grouped(progFmt(v, ProgrammerEngine.BIN), ProgrammerEngine.BIN));
        tvProgExpr.setText(progPendingOp == null ? "" : progFmt(progAcc, progBase) + " " + progPendingOp);
        for (int i = 0; i < 4; i++) {
            boolean active = progBase == PROG_BASES[i];
            progBaseRows[i].setBackgroundResource(active ? R.drawable.bg_base_row_selected : R.drawable.bg_base_row_idle);
            progBaseLabels[i].setTextColor(active ? c(R.attr.cAccentText) : c(R.attr.cTextSecondary));
            progBaseValues[i].setTextColor(c(R.attr.cTextPrimary));
            Ui.setChipState(progBitChips[i], progBits == ProgrammerEngine.WORD_BITS[i]);
        }
        for (int i = 0; i < 16; i++) {
            if (progDigitBtns[i] == null) continue;
            boolean en = i < progBase;
            progDigitBtns[i].setEnabled(en);
            progDigitBtns[i].setAlpha(en ? 1f : 0.3f);
        }
    }

    // ---------- converter ----------

    private View buildConvPanel() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(panelLp());
        col.setVisibility(View.GONE);

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
        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        catLp.setMargins(0, dp(4), 0, dp(10));
        col.addView(catScroll, catLp);

        LinearLayout inCard = new LinearLayout(this);
        inCard.setOrientation(LinearLayout.HORIZONTAL);
        inCard.setGravity(Gravity.CENTER_VERTICAL);
        inCard.setBackgroundResource(R.drawable.bg_card_active);
        inCard.setPadding(dp(18), dp(12), dp(14), dp(12));
        LinearLayout inText = new LinearLayout(this);
        inText.setOrientation(LinearLayout.VERTICAL);
        inText.addView(Ui.small(this, getString(R.string.conv_from)));
        tvConvIn = new TextView(this);
        tvConvIn.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvConvIn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        Ui.autoFit(tvConvIn, 22, 44);
        inText.addView(tvConvIn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        inCard.addView(inText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvFromUnit = unitPill(true);
        inCard.addView(tvFromUnit, new LinearLayout.LayoutParams(dp(112), dp(48)));
        col.addView(inCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageButton swap = new ImageButton(this);
        swap.setImageResource(R.drawable.ic_swap);
        swap.setBackgroundResource(R.drawable.bg_swap_btn);
        swap.setScaleType(ImageView.ScaleType.CENTER);
        swap.setElevation(dp(4));
        swap.setContentDescription(getString(R.string.conv_swap));
        swap.setOnClickListener(v -> swapUnits());
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        swLp.gravity = Gravity.CENTER_HORIZONTAL;
        swLp.topMargin = -dp(16);
        swLp.bottomMargin = -dp(16);
        col.addView(swap, swLp);

        LinearLayout outCard = new LinearLayout(this);
        outCard.setOrientation(LinearLayout.HORIZONTAL);
        outCard.setGravity(Gravity.CENTER_VERTICAL);
        outCard.setBackgroundResource(R.drawable.bg_result_card);
        outCard.setPadding(dp(18), dp(12), dp(14), dp(12));
        LinearLayout outText = new LinearLayout(this);
        outText.setOrientation(LinearLayout.VERTICAL);
        outText.addView(Ui.small(this, getString(R.string.conv_to)));
        tvConvOut = new TextView(this);
        tvConvOut.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tvConvOut.setTextColor(c(R.attr.cAccentText));
        tvConvOut.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        Ui.autoFit(tvConvOut, 18, 44);
        tvConvOut.setOnLongClickListener(v -> { copyToClipboard(tvConvOut.getText().toString()); return true; });
        outText.addView(tvConvOut, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        outCard.addView(outText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvToUnit = unitPill(false);
        outCard.addView(tvToUnit, new LinearLayout.LayoutParams(dp(112), dp(48)));
        col.addView(outCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvConvRate = new TextView(this);
        tvConvRate.setTextSize(13);
        tvConvRate.setGravity(Gravity.CENTER);
        tvConvRate.setSingleLine(true);
        tvConvRate.setTextColor(c(R.attr.cTextSecondary));
        LinearLayout.LayoutParams rateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        rateLp.setMargins(0, dp(4), 0, dp(4));
        col.addView(tvConvRate, rateLp);

        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        col.addView(keys, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        KeyHandler h = (k, b) -> handleConvKey(k);
        addKeys(keys, new String[]{"7", "8", "9", "AC"}, null, 1f, true, 3, null, h);
        addKeys(keys, new String[]{"4", "5", "6", "DEL"}, null, 1f, true, 3, null, h);
        addKeys(keys, new String[]{"1", "2", "3", "\u00b1"}, null, 1f, true, 3, null, h);
        addKeys(keys, new String[]{"0", ".", "\u21c5"}, new float[]{2f, 1f, 1f}, 1f, true, 3, null, h);

        selectCategory(convCategory);
        return col;
    }

    private TextView unitPill(final boolean from) {
        TextView t = new TextView(this);
        t.setTextSize(17);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(c(R.attr.cAccentText));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        t.setBackgroundResource(R.drawable.bg_unit_pill);
        t.setPadding(dp(14), 0, dp(8), 0);
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_updown, 0);
        t.setCompoundDrawablePadding(dp(4));
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(v -> pickUnit(from));
        return t;
    }

    private void handleConvKey(String k) {
        switch (k) {
            case "AC":
            case "DEL_LONG":
                convText = "";
                break;
            case "DEL":
                if (!convText.isEmpty()) convText = convText.substring(0, convText.length() - 1);
                if (convText.equals("-")) convText = "";
                break;
            case "\u00b1":
                convText = convText.startsWith("-") ? convText.substring(1) : "-" + convText;
                break;
            case "\u21c5":
                swapUnits();
                return;
            case ".":
                if (!convText.contains(".")) {
                    convText = (convText.isEmpty() || convText.equals("-")) ? convText + "0." : convText + ".";
                }
                break;
            default:
                if (convText.length() >= 15) return;
                if (convText.equals("0")) convText = k;
                else if (convText.equals("-0")) convText = "-" + k;
                else convText += k;
        }
        doConvert();
    }

    private void selectCategory(int index) {
        convCategory = index;
        for (int i = 0; i < convChips.length; i++) Ui.setChipState(convChips[i], i == index);
        String category = Converter.CATEGORIES[index];
        String[] names = unitNames();
        String f = store.getConvUnit(category, true);
        String t = store.getConvUnit(category, false);
        convFrom = indexOfName(names, f) >= 0 ? f : names[0];
        convTo = indexOfName(names, t) >= 0 ? t : (names.length > 1 ? names[1] : names[0]);
        store.setConvCategory(index);
        updateUnitLabels();
        doConvert();
    }

    private String[] unitNames() {
        return Converter.units(Converter.CATEGORIES[convCategory]).keySet().toArray(new String[0]);
    }

    private static int indexOfName(String[] names, String name) {
        if (name == null) return -1;
        for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
        return -1;
    }

    private void pickUnit(final boolean from) {
        final String[] names = unitNames();
        int checked = indexOfName(names, from ? convFrom : convTo);
        new AlertDialog.Builder(this)
                .setTitle(from ? R.string.conv_from : R.string.conv_to)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    if (from) convFrom = names[which];
                    else convTo = names[which];
                    saveUnits();
                    updateUnitLabels();
                    doConvert();
                    d.dismiss();
                })
                .show();
    }

    private void saveUnits() {
        String category = Converter.CATEGORIES[convCategory];
        store.setConvUnit(category, true, convFrom);
        store.setConvUnit(category, false, convTo);
    }

    private void swapUnits() {
        String tmp = convFrom;
        convFrom = convTo;
        convTo = tmp;
        saveUnits();
        updateUnitLabels();
        doConvert();
    }

    private void updateUnitLabels() {
        tvFromUnit.setText(convFrom);
        tvToUnit.setText(convTo);
    }

    private String categoryLabel(String key) {
        switch (key) {
            case Converter.LENGTH: return getString(R.string.conv_length);
            case Converter.WEIGHT: return getString(R.string.conv_weight);
            case Converter.TEMP: return getString(R.string.conv_temp);
            case Converter.AREA: return getString(R.string.conv_area);
            case Converter.VOLUME: return getString(R.string.conv_volume);
            case Converter.SPEED: return getString(R.string.conv_speed);
            case Converter.DATA: return getString(R.string.conv_data);
            case Converter.TIME: return getString(R.string.conv_time);
            case Converter.ENERGY: return getString(R.string.conv_energy);
            case Converter.PRESSURE: return getString(R.string.conv_pressure);
            case Converter.POWER: return getString(R.string.conv_power);
            case Converter.ANGLE: return getString(R.string.conv_angle);
            default: return key;
        }
    }

    private void doConvert() {
        if (tvConvIn == null || tvConvOut == null || convFrom.isEmpty() || convTo.isEmpty()) return;
        String category = Converter.CATEGORIES[convCategory];
        boolean empty = convText.isEmpty() || convText.equals("-");
        tvConvIn.setText(empty ? "0" : Fmt.prettyExpr(convText, store.getThousands()));
        tvConvIn.setTextColor(empty ? c(R.attr.cTextHint) : c(R.attr.cTextPrimary));
        double input;
        try {
            input = empty ? 0 : Double.parseDouble(convText);
        } catch (NumberFormatException e) {
            input = 0;
        }
        double out = Converter.convert(category, convFrom, convTo, input);
        tvConvOut.setText(Fmt.result(out, store.getThousands()));
        double one = Converter.convert(category, convFrom, convTo, 1);
        tvConvRate.setText("1 " + convFrom + " = " + Fmt.result(one, store.getThousands()) + " " + convTo);
    }
}
