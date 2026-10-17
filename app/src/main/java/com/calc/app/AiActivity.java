package com.calc.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.calc.app.solver.Cmd;
import com.calc.app.solver.Msgs;
import com.calc.app.solver.Renderer;
import com.calc.app.solver.Router;
import com.calc.app.solver.Solution;
import com.calc.app.solver.SolverEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AiActivity extends AppCompatActivity {

    public static final String EXTRA_PROBLEM = "ai_problem";

    private static boolean hasArabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '\u0600' && ch <= '\u06ff') return true;
        }
        return false;
    }

    private static boolean hasLatin(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) return true;
        }
        return false;
    }

    private boolean arabicReply(String problem) {
        if (hasArabic(problem)) return true;
        if (hasLatin(problem)) return false;
        return "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    private Store store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean downloadCancelled = false;
    private volatile boolean working = false;

    private LinearLayout setupCard;
    private ScrollView setupScroll;
    private LinearLayout chatArea;
    private LinearLayout chatList;
    private LinearLayout emptyState;
    private LinearLayout composer;
    private TextView tvStatus;
    private ProgressBar progress;
    private EditText etUrl;
    private EditText etProblem;
    private TextView btnSend;
    private TextView btnNew;
    private TextView btnMenu;
    private TextView tvModelInfo;
    private ScrollView scroll;
    private final StringBuffer output = new StringBuffer();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean waiting = false;
    private int dotStep = 0;
    private final Runnable dotsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!waiting || activeBody == null) return;
            dotStep = (dotStep + 1) % 4;
            activeBody.setText(getString(R.string.ai_thinking).replace("\u2026", "") + "...".substring(0, dotStep));
            ui.postDelayed(this, 500);
        }
    };
    private final AtomicBoolean flushPending = new AtomicBoolean(false);
    private final Runnable flushRunnable = new Runnable() {
        @Override
        public void run() {
            flushPending.set(false);
            flushOutput();
        }
    };
    private TextView activeBody;
    private LinearLayout activeBubble;

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> saveLauncher;
    private final List<TextView> modelRows = new ArrayList<>();
    private List<ModelConf> catalog;
    private ModelConf selectedConf;
    private ModelConf conf = ModelConf.fallback();
    private boolean forceLoad = false;
    private boolean userStopped = false;
    private boolean toolPending = false;
    private String pendingExpr = null;
    private int toolCalls = 0;
    private boolean replyArabic = false;
    private final StringBuilder segment = new StringBuilder();
    private static final long FOREGROUND_IDLE_MS = 5L * 60L * 1000L;

    private void selectModel(ModelConf c) {
        selectedConf = c;
        etUrl.setText(c.url);
        for (int i = 0; i < modelRows.size(); i++) {
            Ui.setChipState(modelRows.get(i), catalog.get(i) == c);
        }
    }

    private String rowText(ModelConf c, boolean recommended) {
        String ram = String.format(Locale.US, "%.1f", c.ramMb / 1024f);
        String tier = "small".equals(c.tier) ? getString(R.string.ai_tier_small)
                : "big".equals(c.tier) ? getString(R.string.ai_tier_big)
                : getString(R.string.ai_tier_balanced);
        String head = recommended ? c.label + "  \u2605 " + getString(R.string.ai_recommended) : c.label;
        return head + "\n" + getString(R.string.ai_model_row_info, c.sizeMb, ram) + "\n" + tier;
    }

    private File modelFile() {
        File dir = new File(getFilesDir(), "models");
        if (!dir.exists()) dir.mkdirs();
        String name = store.getAiModelName();
        if (name.isEmpty()) name = "model.litertlm";
        return new File(dir, name);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importModel(uri);
        });

        saveLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            if (uri != null) exportModel(uri);
        });

        buildUi();
        String seed = getIntent().getStringExtra(EXTRA_PROBLEM);
        if (seed != null) etProblem.setText(seed);
        refreshState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalLlm.get().cancelIdleRelease();
    }

    @Override
    protected void onStop() {
        super.onStop();
        long delay = isFinishing() ? 3000L : Math.max(10, conf.idleReleaseSec) * 1000L;
        LocalLlm.get().scheduleIdleRelease(delay);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) LocalLlm.get().scheduleIdleRelease(3000L);
        waiting = false;
        ui.removeCallbacks(dotsRunnable);
        ui.removeCallbacks(flushRunnable);
        downloadCancelled = true;
        io.shutdown();
    }

    private int c(int attr) { return Ui.attrColor(this, attr); }
    private int dp(int v) { return Ui.dp(this, v); }

    // ---------- UI ----------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(R.attr.cBg));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(6));
        ImageButton back = new ImageButton(this, null, 0, R.style.IconButton);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription(getString(R.string.action_back));
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(10), 0, dp(10), 0);
        TextView title = new TextView(this);
        title.setText(R.string.ai_title);
        title.setTextSize(20);
        title.setSingleLine(true);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        title.setTextColor(c(R.attr.cTextPrimary));
        titleBox.addView(title);
        tvModelInfo = new TextView(this);
        tvModelInfo.setTextSize(12);
        tvModelInfo.setSingleLine(true);
        tvModelInfo.setTextColor(c(R.attr.cTextSecondary));
        titleBox.addView(tvModelInfo);
        bar.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnNew = headerAction(getString(R.string.ai_new_chat));
        btnNew.setOnClickListener(v -> newChat());
        bar.addView(btnNew);
        btnMenu = headerAction("⋮");
        btnMenu.setTextSize(20);
        btnMenu.setContentDescription(getString(R.string.ai_model_menu));
        btnMenu.setOnClickListener(this::showModelMenu);
        bar.addView(btnMenu);
        root.addView(bar);

        setupScroll = new ScrollView(this);
        setupScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout setupHost = new LinearLayout(this);
        setupHost.setOrientation(LinearLayout.VERTICAL);
        setupHost.setPadding(dp(16), dp(8), dp(16), dp(24));
        setupScroll.addView(setupHost, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(setupScroll);

        setupCard = Ui.card(this);
        setupCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView setupTitle = new TextView(this);
        setupTitle.setText(R.string.ai_setup_title);
        setupTitle.setTextSize(18);
        setupTitle.setTextColor(c(R.attr.cTextPrimary));
        setupTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        setupCard.addView(setupTitle);
        TextView setupDesc = new TextView(this);
        setupDesc.setText(R.string.ai_setup_desc);
        setupDesc.setTextSize(14);
        setupDesc.setTextColor(c(R.attr.cTextSecondary));
        setupDesc.setPadding(0, dp(6), 0, dp(12));
        setupCard.addView(setupDesc);

        catalog = ModelConf.catalog(this);
        ModelConf rec = ModelConf.recommended(this);
        for (ModelConf mc : catalog) {
            final ModelConf item = mc;
            TextView row = Ui.chip(this, rowText(mc, mc == rec));
            row.setSingleLine(false);
            row.setMinHeight(dp(72));
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setOnClickListener(v -> selectModel(item));
            modelRows.add(row);
            Ui.setChipState(row, false);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = dp(8);
            setupCard.addView(row, rLp);
        }

        etUrl = new EditText(this);
        etUrl.setHint(R.string.ai_url_hint);
        etUrl.setSingleLine(true);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etUrl.setTextSize(14);
        etUrl.setTextColor(c(R.attr.cTextPrimary));
        etUrl.setHintTextColor(c(R.attr.cTextHint));
        etUrl.setBackground(Ui.round(getColor(R.color.field), dp(14), 0, 0));
        etUrl.setPadding(dp(14), dp(12), dp(14), dp(12));
        etUrl.setText(store.getAiUrl());
        ModelConf initial = rec;
        for (ModelConf mc : catalog) {
            if (mc.url.contentEquals(etUrl.getText())) initial = mc;
        }
        if (initial != null) {
            if (etUrl.getText().length() == 0 || initial.url.contentEquals(etUrl.getText())) selectModel(initial);
        }
        setupCard.addView(etUrl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        brLp.topMargin = dp(12);
        TextView btnDownload = pill(getString(R.string.ai_download), true);
        btnDownload.setOnClickListener(v -> startDownload());
        TextView btnImport = pill(getString(R.string.ai_import), false);
        btnImport.setOnClickListener(v -> importLauncher.launch(new String[]{"*/*"}));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        b1.setMarginEnd(dp(8));
        btnRow.addView(btnDownload, b1);
        btnRow.addView(btnImport, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        setupCard.addView(btnRow, brLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        pLp.topMargin = dp(14);
        setupCard.addView(progress, pLp);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(13);
        tvStatus.setTextColor(c(R.attr.cTextSecondary));
        tvStatus.setPadding(0, dp(8), 0, 0);
        tvStatus.setVisibility(View.GONE);
        setupCard.addView(tvStatus);
        setupHost.addView(setupCard, Ui.matchWrap(this, 0, 12, 0));

        chatArea = new LinearLayout(this);
        chatArea.setOrientation(LinearLayout.VERTICAL);
        chatArea.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setPadding(dp(14), dp(8), dp(14), dp(12));
        scroll.addView(chatList, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chatArea.addView(scroll);

        emptyState = buildEmptyState();
        chatList.addView(emptyState);

        composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(dp(12), dp(6), dp(12), dp(10));

        etProblem = new EditText(this);
        etProblem.setHint(R.string.ai_problem_hint);
        etProblem.setMinHeight(dp(48));
        etProblem.setMaxLines(5);
        etProblem.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        etProblem.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etProblem.setTextSize(16);
        etProblem.setTextColor(c(R.attr.cTextPrimary));
        etProblem.setHintTextColor(c(R.attr.cTextHint));
        etProblem.setBackground(Ui.round(c(R.attr.cSurface), dp(24), c(R.attr.cStrokeSoft), dp(1)));
        etProblem.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        epLp.setMarginEnd(dp(8));
        composer.addView(etProblem, epLp);

        btnSend = new TextView(this);
        btnSend.setGravity(Gravity.CENTER);
        btnSend.setTextSize(22);
        btnSend.setTextColor(getColor(R.color.on_accent));
        btnSend.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        btnSend.setBackgroundResource(R.drawable.bg_pill_accent);
        btnSend.setClickable(true);
        btnSend.setFocusable(true);
        btnSend.setContentDescription(getString(R.string.ai_solve));
        btnSend.setOnClickListener(v -> onSendClicked());
        composer.addView(btnSend, new LinearLayout.LayoutParams(dp(48), dp(48)));
        setSendState(false);
        chatArea.addView(composer);
        root.addView(chatArea);

        setContentView(root);
    }

    private LinearLayout buildEmptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(8), dp(40), dp(8), dp(8));

        TextView badge = new TextView(this);
        badge.setText("∑");
        badge.setTextSize(30);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(c(R.attr.cAccentText));
        badge.setBackground(Ui.round(c(R.attr.cAccentSoft), dp(32), 0, 0));
        box.addView(badge, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView hello = new TextView(this);
        hello.setText(R.string.ai_welcome_title);
        hello.setTextSize(22);
        hello.setGravity(Gravity.CENTER);
        hello.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        hello.setTextColor(c(R.attr.cTextPrimary));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dp(16);
        box.addView(hello, hLp);

        TextView desc = new TextView(this);
        desc.setText(R.string.ai_subtitle);
        desc.setTextSize(14);
        desc.setGravity(Gravity.CENTER);
        desc.setTextColor(c(R.attr.cTextSecondary));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(6);
        dLp.bottomMargin = dp(22);
        box.addView(desc, dLp);

        String[] samples = {"(125 + 75) × 3 − 40", getString(R.string.ai_sample_equation), getString(R.string.ai_sample_calculus)};
        for (String s : samples) {
            TextView chip = new TextView(this);
            chip.setText(s);
            chip.setTextSize(15);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setTextColor(c(R.attr.cTextPrimary));
            chip.setBackground(Ui.round(c(R.attr.cSurface), dp(20), c(R.attr.cStrokeSoft), dp(1)));
            chip.setPadding(dp(18), dp(12), dp(18), dp(12));
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> sendText(s));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.topMargin = dp(8);
            box.addView(chip, cLp);
        }
        return box;
    }

    private TextView headerAction(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setTextColor(c(R.attr.cAccentText));
        t.setMinWidth(dp(40));
        t.setMinHeight(dp(40));
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void showModelMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.ai_share_model);
        menu.getMenu().add(0, 2, 1, R.string.ai_save_model);
        menu.getMenu().add(0, 3, 2, R.string.ai_remove_model);
        android.view.MenuItem power = menu.getMenu().add(0, 4, 3, R.string.ai_low_power);
        power.setCheckable(true);
        power.setChecked(store.getAiLowPower());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) shareModel();
            else if (id == 2) saveLauncher.launch(modelFile().getName());
            else if (id == 3) removeModel();
            else if (id == 4) {
                store.setAiLowPower(!store.getAiLowPower());
                newChat();
                LocalLlm.get().release();
                Toast.makeText(this, R.string.ai_power_changed, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        menu.show();
    }

    private TextView pill(String text, boolean accent) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setClickable(true);
        t.setFocusable(true);
        if (accent) {
            t.setBackgroundResource(R.drawable.bg_pill_accent);
            t.setTextColor(getColor(R.color.on_accent));
        } else {
            t.setBackgroundResource(R.drawable.bg_pill);
            t.setTextColor(c(R.attr.cTextPrimary));
        }
        return t;
    }

    private void setSendState(boolean stop) {
        btnSend.setText(stop ? "■" : "↑");
        btnSend.setTextSize(stop ? 16 : 24);
    }

    // ---------- chat bubbles ----------

    private int maxBubbleWidth() {
        return (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
    }

    private void addUserBubble(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(getColor(R.color.on_accent));
        t.setTextIsSelectable(true);
        t.setMaxWidth(maxBubbleWidth());
        t.setBackground(Ui.round(c(R.attr.cAccent), dp(20), 0, 0));
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.topMargin = dp(10);
        chatList.addView(t, lp);
    }

    private void addAiBubble(String exact, boolean deferBody) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackground(Ui.round(c(R.attr.cSurface), dp(20), 0, 0));
        bubble.setPadding(dp(16), dp(12), dp(16), dp(10));

        if (exact != null) {
            TextView chip = new TextView(this);
            chip.setText(getString(R.string.ai_exact_result) + "  " + exact);
            chip.setTextSize(15);
            chip.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            chip.setTextColor(c(R.attr.cAccentText));
            chip.setTextIsSelectable(true);
            chip.setBackground(Ui.round(c(R.attr.cAccentSoft), dp(12), 0, 0));
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.bottomMargin = dp(8);
            bubble.addView(chip, cLp);
        }

        TextView body = new TextView(this);
        body.setText(R.string.ai_thinking);
        body.setTextSize(16);
        body.setLineSpacing(0, 1.15f);
        body.setTextColor(c(R.attr.cTextSecondary));
        if (deferBody) body.setVisibility(View.GONE);
        bubble.addView(body);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(28));
        lp.topMargin = dp(10);
        chatList.addView(bubble, lp);
        activeBubble = bubble;
        activeBody = body;
    }

    private void addCopyAction(final LinearLayout bubble, final TextView body) {
        TextView copy = new TextView(this);
        copy.setText(R.string.ai_copy);
        copy.setTextSize(12);
        copy.setTextColor(c(R.attr.cAccentText));
        copy.setPadding(0, dp(8), dp(12), dp(2));
        copy.setClickable(true);
        copy.setFocusable(true);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("ai", body.getText()));
                Toast.makeText(this, R.string.ai_copied, Toast.LENGTH_SHORT).show();
            }
        });
        bubble.addView(copy);
    }

    private void flushOutput() {
        if (activeBody == null) return;
        String shown = clean(output.toString());
        if (shown.isEmpty()) return;
        waiting = false;
        ui.removeCallbacks(dotsRunnable);
        activeBody.setTextColor(c(R.attr.cTextPrimary));
        activeBody.setText(shown);
        scrollToEnd();
    }

    private void scheduleFlush() {
        if (flushPending.compareAndSet(false, true)) ui.postDelayed(flushRunnable, 90);
    }

    private void scrollToEnd() {
        scroll.post(() -> scroll.scrollTo(0, chatList.getHeight()));
    }

    private static String clean(String s) {
        return s.replaceAll("(?s)<think>.*?</think>", "").replaceAll("(?s)<think>.*$", "")
                .replaceAll("\\[\\[\\s*calc\\s*:[^\\]]*\\]\\]", "").replaceAll("\\[\\[\\s*calc[^\\]]*$", "").replace("**", "").replace("__", "").replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("\\n\\s*\\n+", "\n").trim();
    }

    private void newChat() {
        LocalLlm llm = LocalLlm.get();
        userStopped = true;
        toolPending = false;
        segment.setLength(0);
        if (llm.isBusy()) llm.cancel();
        llm.reset();
        chatList.removeAllViews();
        chatList.addView(emptyState);
        waiting = false;
        ui.removeCallbacks(dotsRunnable);
        activeBody = null;
        activeBubble = null;
        output.setLength(0);
        setSendState(false);
    }

    // ---------- state ----------

    private boolean hasModel() {
        File f = modelFile();
        return f.exists() && f.length() > 1024 * 1024;
    }

    private void refreshState() {
        boolean has = hasModel();
        setupScroll.setVisibility(has ? View.GONE : View.VISIBLE);
        chatArea.setVisibility(has ? View.VISIBLE : View.GONE);
        btnNew.setVisibility(has ? View.VISIBLE : View.GONE);
        btnMenu.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            File f = modelFile();
            conf = ModelConf.forFile(this, f.getName(), f.length());
            long mb = f.length() / (1024 * 1024);
            LocalLlm llm = LocalLlm.get();
            String info = getString(R.string.ai_model_ready) + " \u00b7 " + conf.label + " \u00b7 " + mb + " MB";
            if (llm.isLoaded()) info += " \u00b7 " + llm.getBackendName();
            tvModelInfo.setText(info);
        } else {
            tvModelInfo.setText(R.string.ai_subtitle);
        }
    }

    private void setStatus(String text, boolean showProgress) {
        tvStatus.setText(text);
        tvStatus.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
        progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
    }

    private void loadModelAsync(final Runnable after) {
        final LocalLlm llm = LocalLlm.get();
        if (llm.isLoaded()) {
            if (after != null) after.run();
            return;
        }
        if (working) return;
        if (!forceLoad && !LocalLlm.hasEnoughMemory(this, conf.ramMb)) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.ai_low_memory)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        forceLoad = true;
                        loadModelAsync(after);
                    })
                    .setNegativeButton(android.R.string.cancel, (d, w) -> resetAskUi(null))
                    .setOnCancelListener(d -> resetAskUi(null))
                    .show();
            return;
        }
        forceLoad = false;
        btnSend.setAlpha(0.5f);
        working = true;
        tvModelInfo.setText(R.string.ai_loading);
        final String path = modelFile().getAbsolutePath();
        final String cache = LocalLlm.cacheDirFor(this);
        final ModelConf loadConf = conf;
        final boolean lowPower = store.getAiLowPower();
        io.execute(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            String error = null;
            try {
                llm.load(path, cache, loadConf, lowPower);
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                working = false;
                btnSend.setAlpha(1f);
                if (err != null) {
                    tvModelInfo.setText(getString(R.string.ai_error) + ": " + err);
                    resetAskUi(getString(R.string.ai_error) + ": " + err);
                } else {
                    refreshState();
                    if (after != null) after.run();
                }
            });
        });
    }

    private void removeModel() {
        newChat();
        LocalLlm.get().release();
        modelFile().delete();
        store.setAiModelName("");
        Toast.makeText(this, R.string.ai_removed, Toast.LENGTH_SHORT).show();
        refreshState();
    }

    // ---------- sharing ----------

    private void shareModel() {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", modelFile());
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.ai_share_model)));
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.ai_error) + ": " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportModel(final Uri uri) {
        Toast.makeText(this, R.string.ai_saving, Toast.LENGTH_SHORT).show();
        final File src = modelFile();
        io.execute(() -> {
            String error = null;
            try (InputStream in = new java.io.FileInputStream(src);
                 OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("Cannot open destination");
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> Toast.makeText(this,
                    err == null ? getString(R.string.ai_saved) : getString(R.string.ai_error) + ": " + err,
                    Toast.LENGTH_LONG).show());
        });
    }

    // ---------- model acquisition ----------

    private String displayName(Uri uri) {
        try (Cursor cur = getContentResolver().query(uri, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cur.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String safeName(String raw) {
        return ModelConf.fileNameFromUrl(raw);
    }

    private void importModel(final Uri uri) {
        final String name = safeName(displayName(uri));
        setStatus(getString(R.string.ai_importing), true);
        progress.setIndeterminate(true);
        io.execute(() -> {
            File tmp = new File(modelFile().getParentFile(), "model.tmp");
            String error = null;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) throw new IllegalStateException("Cannot open file");
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }
            finishAcquire(tmp, error, name);
        });
    }

    private void startDownload() {
        final String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) return;
        store.setAiUrl(url);
        final String name = safeName(url);
        if (selectedConf != null && url.equals(selectedConf.url)
                && selectedConf.ramMb > LocalLlm.totalMemoryMb(this) * 0.5) {
            Toast.makeText(this, R.string.ai_heavy_warning, Toast.LENGTH_LONG).show();
        }
        downloadCancelled = false;
        setStatus(getString(R.string.ai_downloading), true);
        progress.setIndeterminate(false);
        progress.setProgress(0);
        io.execute(() -> {
            File tmp = new File(modelFile().getParentFile(), "model.tmp");
            String error = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                int code = conn.getResponseCode();
                if (code / 100 != 2) throw new IllegalStateException("HTTP " + code);
                final long total = conn.getContentLengthLong();
                File target = modelFile().getParentFile();
                if (total > 0 && target != null && target.getUsableSpace() < total + (200L << 20)) {
                    throw new IllegalStateException(getString(R.string.ai_no_space));
                }
                long done = 0;
                long lastUi = 0;
                try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (downloadCancelled) throw new IllegalStateException("Cancelled");
                        out.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastUi > 250) {
                            lastUi = now;
                            final long d = done;
                            runOnUiThread(() -> {
                                if (total > 0) progress.setProgress((int) (d * 1000 / total));
                                tvStatus.setText(String.format(Locale.US, "%s %d MB", getString(R.string.ai_downloading), d / (1024 * 1024)));
                            });
                        }
                    }
                }
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }
            finishAcquire(tmp, error, name);
        });
    }

    private void finishAcquire(final File tmp, final String error, final String name) {
        String err = error;
        if (err == null) {
            if (tmp.length() < 1024 * 1024) {
                err = getString(R.string.ai_invalid_file);
            } else {
                LocalLlm.get().release();
                File dir = tmp.getParentFile();
                File[] existing = dir == null ? null : dir.listFiles();
                if (existing != null) {
                    for (File f : existing) {
                        if (f.getName().endsWith(".litertlm")) f.delete();
                    }
                }
                store.setAiModelName(name);
                File dest = new File(dir, name);
                if (!tmp.renameTo(dest)) err = getString(R.string.ai_error);
            }
        }
        if (err != null) tmp.delete();
        final String shown = err;
        runOnUiThread(() -> {
            if (shown != null) {
                progress.setIndeterminate(false);
                setStatus(getString(R.string.ai_error) + ": " + shown, false);
            } else {
                setStatus("", false);
                refreshState();
            }
        });
    }

    // ---------- solving ----------

    private void onSendClicked() {
        final LocalLlm llm = LocalLlm.get();
        if (llm.isBusy()) {
            userStopped = true;
            llm.cancel();
            return;
        }
        sendText(etProblem.getText().toString().trim());
    }

    private String exactResult(String problem) {
        try {
            String cleaned = CalcTool.normalizeDigits(problem).replaceAll("[=?\\s\\u061f]+$", "");
            double v = Expr.eval(cleaned, store.getAngleMode(), () -> 0.0);
            if (!Double.isNaN(v) && !Double.isInfinite(v)) return Fmt.result(v, store.getThousands());
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String ps(Locale loc, int res, Object... args) {
        Configuration cfg = new Configuration(getResources().getConfiguration());
        cfg.setLocale(loc);
        return createConfigurationContext(cfg).getResources().getString(res, args);
    }

    private String buildPrompt(String problem, String exact) {
        Locale loc = replyArabic ? new Locale("ar") : Locale.ENGLISH;
        StringBuilder p = new StringBuilder();
        p.append(ps(loc, R.string.ai_prompt_question, problem));
        if (exact != null) p.append(ps(loc, R.string.ai_prompt_verified, exact));
        p.append(ps(loc, R.string.ai_prompt_tail, exact == null ? "" : exact));
        return p.toString();
    }

    private void resetAskUi(String message) {
        waiting = false;
        ui.removeCallbacks(dotsRunnable);
        ui.removeCallbacks(flushRunnable);
        flushPending.set(false);
        setSendState(false);
        btnSend.setAlpha(1f);
        if (message != null && activeBody != null) {
            activeBody.setTextColor(getColor(R.color.bad));
            activeBody.setText(message);
        }
    }

    private void sendText(final String problem) {
        final LocalLlm llm = LocalLlm.get();
        if (llm.isBusy() || working) return;
        if (TextUtils.isEmpty(problem)) {
            Toast.makeText(this, R.string.ai_empty_problem, Toast.LENGTH_SHORT).show();
            return;
        }
        final String exact = exactResult(problem);
        etProblem.setText("");
        if (emptyState.getParent() != null) chatList.removeView(emptyState);
        addUserBubble(problem);
        if (exact != null) {
            addAiBubble(exact, true);
            addExplainAction(activeBubble, activeBody, problem, exact);
            scrollToEnd();
            return;
        }
        addAiBubble(null, false);
        solveRequest(problem);
    }

    private final Msgs solverMsgs = new Msgs() {
        @Override
        public String get(String key, Object... args) {
            Locale loc = replyArabic ? new Locale("ar") : Locale.ENGLISH;
            int id = getResources().getIdentifier("sv_" + key, "string", getPackageName());
            if (id == 0) return key;
            Configuration cfg = new Configuration(getResources().getConfiguration());
            cfg.setLocale(loc);
            String fmt = createConfigurationContext(cfg).getResources().getString(id);
            try {
                return String.format(Locale.ROOT, fmt, args);
            } catch (RuntimeException e) {
                return fmt;
            }
        }
    };

    private void beginSolveUi() {
        output.setLength(0);
        segment.setLength(0);
        userStopped = false;
        waiting = true;
        dotStep = 0;
        ui.removeCallbacks(dotsRunnable);
        ui.postDelayed(dotsRunnable, 500);
        scrollToEnd();
    }

    private void solveRequest(final String problem) {
        final LocalLlm llm = LocalLlm.get();
        replyArabic = arabicReply(problem);
        beginSolveUi();
        final Cmd cmd = Router.understand(problem);
        if (cmd != null) {
            runSolver(cmd);
            return;
        }
        Runnable go = () -> translateWithModel(problem);
        if (llm.isLoaded()) go.run();
        else loadModelAsync(go);
    }

    private void runSolver(final Cmd cmd) {
        working = true;
        final boolean arabic = replyArabic;
        io.execute(() -> {
            String text;
            try {
                Solution sol = SolverEngine.run(cmd);
                text = Renderer.render(sol, solverMsgs, arabic);
            } catch (Throwable t) {
                text = solverMsgs.get("err_unsupported");
            }
            final String shown = text;
            runOnUiThread(() -> showSolution(shown));
        });
    }

    private void translateWithModel(final String problem) {
        final LocalLlm llm = LocalLlm.get();
        llm.reset();
        output.setLength(0);
        setSendState(true);
        llm.ask(getString(R.string.sv_translate_system), problem, new LocalLlm.Listener() {
            @Override
            public void onToken(String text) {
                output.append(text);
            }

            @Override
            public void onDone() {
                if (userStopped) {
                    runOnUiThread(() -> showSolution(getString(R.string.ai_empty_reply)));
                    return;
                }
                Cmd c = Router.fromModelLine(clean(output.toString()));
                String text;
                if (c == null) {
                    text = solverMsgs.get("cant_understand");
                } else {
                    try {
                        text = Renderer.render(SolverEngine.run(c), solverMsgs, replyArabic);
                    } catch (Throwable t) {
                        text = solverMsgs.get("err_unsupported");
                    }
                }
                final String shown = text;
                runOnUiThread(() -> showSolution(shown));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> failAnswer(activeBody, message));
            }
        });
    }

    private void showSolution(String text) {
        working = false;
        waiting = false;
        ui.removeCallbacks(dotsRunnable);
        ui.removeCallbacks(flushRunnable);
        flushPending.set(false);
        setSendState(false);
        if (activeBody == null) return;
        activeBody.setTextColor(c(R.attr.cTextPrimary));
        activeBody.setText(text);
        activeBody.setTextIsSelectable(true);
        addCopyAction(activeBubble, activeBody);
        scrollToEnd();
        LocalLlm.get().scheduleIdleRelease(FOREGROUND_IDLE_MS);
    }

    private void addExplainAction(final LinearLayout bubble, final TextView body, final String problem, final String exact) {
        TextView explain = new TextView(this);
        explain.setText(R.string.ai_explain_steps);
        explain.setTextSize(14);
        explain.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        explain.setTextColor(c(R.attr.cAccentText));
        explain.setPadding(0, dp(4), dp(12), dp(4));
        explain.setClickable(true);
        explain.setFocusable(true);
        explain.setOnClickListener(v -> {
            LocalLlm llm = LocalLlm.get();
            if (llm.isBusy() || working) return;
            bubble.removeView(v);
            body.setVisibility(View.VISIBLE);
            activeBubble = bubble;
            activeBody = body;
            startAsk(problem, exact);
        });
        bubble.addView(explain);
    }

    private void startAsk(final String problem, final String exact) {
        final LocalLlm llm = LocalLlm.get();
        if (llm.isBusy() || working) return;
        replyArabic = arabicReply(problem);
        output.setLength(0);
        segment.setLength(0);
        toolCalls = 0;
        toolPending = false;
        pendingExpr = null;
        userStopped = false;
        setSendState(true);
        waiting = true;
        dotStep = 0;
        ui.removeCallbacks(dotsRunnable);
        ui.postDelayed(dotsRunnable, 500);
        scrollToEnd();

        final boolean tools = conf.tools && exact == null;
        final String prompt = buildPrompt(problem, exact);
        final TextView body = activeBody;
        final LinearLayout bubble = activeBubble;
        Runnable go = () -> runGeneration(prompt, tools, body, bubble);
        if (llm.isLoaded()) go.run();
        else loadModelAsync(go);
    }

    private void handleToken(String text, boolean tools) {
        if (!tools || toolCalls >= conf.maxToolCalls) {
            output.append(text);
            scheduleFlush();
            return;
        }
        if (toolPending) return;
        segment.append(text);
        Matcher m = CalcTool.TAG.matcher(segment);
        if (m.find()) {
            output.append(segment, 0, m.start());
            pendingExpr = m.group(1).trim();
            toolPending = true;
            segment.setLength(0);
            LocalLlm.get().cancel();
            scheduleFlush();
            return;
        }
        int k = segment.indexOf("[[");
        int hold;
        if (k >= 0) hold = k;
        else if (segment.length() > 0 && segment.charAt(segment.length() - 1) == '[') hold = segment.length() - 1;
        else hold = segment.length();
        if (k >= 0 && segment.length() - k > 300) hold = segment.length();
        if (hold > 0) {
            output.append(segment, 0, hold);
            segment.delete(0, hold);
        }
        scheduleFlush();
    }

    private void runGeneration(final String prompt, final boolean tools, final TextView body, final LinearLayout bubble) {
        final LocalLlm llm = LocalLlm.get();
        llm.ask(conf.systemPrompt(tools), prompt, new LocalLlm.Listener() {
            @Override
            public void onToken(String text) {
                handleToken(text, tools);
            }

            @Override
            public void onDone() {
                if (toolPending && !userStopped) {
                    final String expr = pendingExpr;
                    toolPending = false;
                    pendingExpr = null;
                    String res = CalcTool.run(store, expr);
                    toolCalls++;
                    Locale loc = replyArabic ? new Locale("ar") : Locale.ENGLISH;
                    output.append("\n").append(expr).append(" = ").append(res == null ? "?" : res).append("\n");
                    String follow = res != null
                            ? ps(loc, R.string.ai_tool_result, expr, res)
                            : ps(loc, R.string.ai_tool_failed, expr);
                    scheduleFlush();
                    runGeneration(follow, tools, body, bubble);
                    return;
                }
                toolPending = false;
                if (segment.length() > 0) {
                    output.append(segment);
                    segment.setLength(0);
                }
                runOnUiThread(() -> finishAnswer(body, bubble));
            }

            @Override
            public void onError(String message) {
                toolPending = false;
                runOnUiThread(() -> failAnswer(body, message));
            }
        });
    }

    private void finishAnswer(TextView body, LinearLayout bubble) {
        waiting = false;
        ui.removeCallbacks(dotsRunnable);
        ui.removeCallbacks(flushRunnable);
        flushPending.set(false);
        setSendState(false);
        String shown = clean(output.toString());
        if (shown.isEmpty()) {
            body.setTextColor(c(R.attr.cTextSecondary));
            body.setText(R.string.ai_empty_reply);
        } else {
            body.setTextColor(c(R.attr.cTextPrimary));
            body.setText(shown);
            body.setTextIsSelectable(true);
            addCopyAction(bubble, body);
        }
        scrollToEnd();
        LocalLlm.get().scheduleIdleRelease(FOREGROUND_IDLE_MS);
    }

    private void failAnswer(TextView body, String message) {
        waiting = false;
        ui.removeCallbacks(dotsRunnable);
        ui.removeCallbacks(flushRunnable);
        flushPending.set(false);
        setSendState(false);
        String shown = clean(output.toString());
        if (!shown.isEmpty()) {
            body.setTextColor(c(R.attr.cTextPrimary));
            body.setText(shown);
        } else {
            body.setTextColor(getColor(R.color.bad));
            body.setText(getString(R.string.ai_error) + ": " + message);
        }
        scrollToEnd();
        LocalLlm.get().scheduleIdleRelease(FOREGROUND_IDLE_MS);
    }
}
