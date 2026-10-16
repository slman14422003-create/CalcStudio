package com.calc.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AiActivity extends AppCompatActivity {

    public static final String EXTRA_PROBLEM = "ai_problem";

    private static String normalizeDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') b.append((char) ('0' + (ch - '\u0660')));
            else if (ch >= '\u06f0' && ch <= '\u06f9') b.append((char) ('0' + (ch - '\u06f0')));
            else if (ch == '\u066b') b.append('.');
            else if (ch == '\u066c') continue;
            else if (ch == '\u066a') b.append('%');
            else b.append(ch);
        }
        return b.toString();
    }

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
    private TextView chipSmall;
    private TextView chipBalanced;
    private TextView chipBig;

    private static final String SYSTEM_PROMPT = "You are a math tutor in a calculator app. "
            + "Be brief: at most 5 short numbered steps, plain text, no LaTeX, no markdown. "
            + "A verified result, if given, is correct and is your final answer. "
            + "For an expression with variables and no equals sign, simplify it only. "
            + "Always reply in the language of the user message; if it is Arabic, reply only in Arabic. "
            + "End with one line: Answer: <result>, or الجواب: <result> in Arabic.";

    private static final String BASE_URL = "https://huggingface.co/leuconoe/litert-lm-unity-quantized/resolve/main/";
    private static final String URL_SMALL = BASE_URL + "qwen2.5-0.5b-instruct/Qwen2.5-0.5B-Instruct_wi4b64_ekv1280.litertlm";
    private static final String URL_BIG = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3_1.7B.litertlm";
    private static final String URL_BALANCED = BASE_URL + "qwen2.5-1.5b-instruct/Qwen2.5-1.5B-Instruct_wi4b64_ekv4096.litertlm";

    private void selectPreset(int which) {
        etUrl.setText(which == 0 ? URL_SMALL : which == 1 ? URL_BALANCED : URL_BIG);
        Ui.setChipState(chipSmall, which == 0);
        Ui.setChipState(chipBalanced, which == 1);
        Ui.setChipState(chipBig, which == 2);
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
    protected void onDestroy() {
        super.onDestroy();
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

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        chipSmall = Ui.chip(this, getString(R.string.ai_preset_small));
        chipBalanced = Ui.chip(this, getString(R.string.ai_preset_balanced));
        chipBig = Ui.chip(this, getString(R.string.ai_preset_big));
        chipSmall.setOnClickListener(v -> selectPreset(0));
        chipBalanced.setOnClickListener(v -> selectPreset(1));
        chipBig.setOnClickListener(v -> selectPreset(2));
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, dp(36), 1f);
        c1.setMarginEnd(dp(8));
        presetRow.addView(chipSmall, c1);
        presetRow.addView(chipBalanced, new LinearLayout.LayoutParams(0, dp(36), 1f));
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        prLp.bottomMargin = dp(8);
        setupCard.addView(presetRow, prLp);
        LinearLayout.LayoutParams bigLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        bigLp.bottomMargin = dp(10);
        setupCard.addView(chipBig, bigLp);

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
        if (etUrl.getText().length() == 0 || URL_BALANCED.contentEquals(etUrl.getText())) selectPreset(1);
        else if (URL_SMALL.contentEquals(etUrl.getText())) selectPreset(0);
        else if (URL_BIG.contentEquals(etUrl.getText())) selectPreset(2);
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

        String[] samples = {"(125 + 75) × 3 − 40", "sqrt(144) + 2^5", getString(R.string.ai_sample_equation)};
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
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) shareModel();
            else if (id == 2) saveLauncher.launch(modelFile().getName());
            else if (id == 3) removeModel();
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

    private void addAiBubble(String exact) {
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
        activeBody.setTextColor(c(R.attr.cTextPrimary));
        activeBody.setText(clean(output.toString()));
        scrollToEnd();
    }

    private void scheduleFlush() {
        if (flushPending.compareAndSet(false, true)) ui.postDelayed(flushRunnable, 90);
    }

    private void scrollToEnd() {
        scroll.post(() -> scroll.scrollTo(0, chatList.getHeight()));
    }

    private static String clean(String s) {
        return s.replaceAll("(?s)<think>.*?</think>", "").replaceAll("(?s)<think>.*$", "").replace("**", "").replace("__", "").replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("\\n\\s*\\n+", "\n").trim();
    }

    private void newChat() {
        LocalLlm llm = LocalLlm.get();
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
            long mb = modelFile().length() / (1024 * 1024);
            tvModelInfo.setText(getString(R.string.ai_model_ready) + " \u00b7 " + mb + " MB");
            loadModelAsync();
        } else {
            tvModelInfo.setText(R.string.ai_subtitle);
        }
    }

    private void setStatus(String text, boolean showProgress) {
        tvStatus.setText(text);
        tvStatus.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
        progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
    }

    private void loadModelAsync() {
        final LocalLlm llm = LocalLlm.get();
        if (llm.isLoaded()) {
            btnSend.setAlpha(1f);
            return;
        }
        btnSend.setAlpha(0.5f);
        working = true;
        tvModelInfo.setText(R.string.ai_loading);
        final String path = modelFile().getAbsolutePath();
        final String cache = getCacheDir().getAbsolutePath();
        io.execute(() -> {
            String error = null;
            try {
                llm.load(path, cache);
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                working = false;
                btnSend.setAlpha(1f);
                if (err != null) {
                    tvModelInfo.setText(getString(R.string.ai_error) + ": " + err);
                } else {
                    long mb = modelFile().length() / (1024 * 1024);
                    tvModelInfo.setText(getString(R.string.ai_model_ready) + " \u00b7 " + mb + " MB");
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
        String n = raw == null ? "" : raw;
        int q = n.indexOf('?');
        if (q >= 0) n = n.substring(0, q);
        int s = n.lastIndexOf('/');
        if (s >= 0) n = n.substring(s + 1);
        n = n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.isEmpty()) n = "model";
        if (n.length() > 100) n = n.substring(n.length() - 100);
        if (!n.toLowerCase(Locale.US).endsWith(".litertlm")) n = n + ".litertlm";
        return n;
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
            llm.cancel();
            return;
        }
        sendText(etProblem.getText().toString().trim());
    }

    private void sendText(String problem) {
        final LocalLlm llm = LocalLlm.get();
        if (llm.isBusy() || working || !llm.isLoaded()) return;
        if (TextUtils.isEmpty(problem)) {
            Toast.makeText(this, R.string.ai_empty_problem, Toast.LENGTH_SHORT).show();
            return;
        }

        String exact = null;
        try {
            String cleaned = normalizeDigits(problem).replaceAll("[=?\\s\\u061f]+$", "");
            double v = Expr.eval(cleaned, store.getAngleMode(), () -> 0.0);
            if (!Double.isNaN(v) && !Double.isInfinite(v)) exact = Fmt.result(v, store.getThousands());
        } catch (Throwable ignored) {
        }

        StringBuilder prompt = new StringBuilder();
        if (arabicReply(problem)) {
            prompt.append("أجب بالعربية فقط ولا تكتب أي كلمة إنجليزية.\nالسؤال: ").append(problem);
            if (exact != null) prompt.append("\nالنتيجة المؤكدة: ").append(exact);
            prompt.append("\nاشرح بخطوات قصيرة بالعربية، واختم بسطر: الجواب: ");
            if (exact != null) prompt.append(exact);
        } else {
            prompt.append(problem).append("\nReply in the same language as the question.");
            if (exact != null) prompt.append("\nVerified result: ").append(exact).append(".");
        }

        etProblem.setText("");
        if (emptyState.getParent() != null) chatList.removeView(emptyState);
        addUserBubble(problem);
        addAiBubble(exact);
        output.setLength(0);
        setSendState(true);
        waiting = true;
        dotStep = 0;
        ui.postDelayed(dotsRunnable, 500);
        scrollToEnd();

        final TextView body = activeBody;
        final LinearLayout bubble = activeBubble;
        llm.ask(SYSTEM_PROMPT, prompt.toString(), new LocalLlm.Listener() {
            @Override
            public void onToken(String text) {
                output.append(text);
            }

            @Override
            public void onDone() {
                runOnUiThread(() -> {
                    waiting = false;
                    ui.removeCallbacks(dotsRunnable);
                    ui.removeCallbacks(flushRunnable);
                    flushPending.set(false);
                    setSendState(false);
                    if (output.length() == 0) {
                        body.setText(R.string.ai_empty_reply);
                    } else {
                        flushOutput();
                        body.setTextIsSelectable(true);
                        addCopyAction(bubble, body);
                    }
                    scrollToEnd();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    waiting = false;
                    ui.removeCallbacks(dotsRunnable);
                    ui.removeCallbacks(flushRunnable);
                    flushPending.set(false);
                    setSendState(false);
                    if (output.length() > 0) flushOutput();
                    if (output.length() == 0) {
                        body.setTextColor(getColor(R.color.bad));
                        body.setText(getString(R.string.ai_error) + ": " + message);
                    }
                    scrollToEnd();
                });
            }
        });
    }
}
