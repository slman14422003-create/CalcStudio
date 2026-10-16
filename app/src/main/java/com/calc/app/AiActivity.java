package com.calc.app;

import android.content.Intent;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private String languageInstruction(String problem) {
        if (hasArabic(problem)) return "Write the whole answer in Arabic. ";
        if (hasLatin(problem)) return "Write the answer in the same language as the problem. ";
        String lang = getResources().getConfiguration().getLocales().get(0).getLanguage();
        return "ar".equals(lang) ? "Write the whole answer in Arabic. " : "Write the answer in English. ";
    }

    private Store store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean downloadCancelled = false;
    private volatile boolean working = false;

    private LinearLayout setupCard;
    private LinearLayout solveCard;
    private LinearLayout resultCard;
    private TextView tvStatus;
    private ProgressBar progress;
    private EditText etUrl;
    private EditText etProblem;
    private TextView btnSolve;
    private TextView tvModelInfo;
    private TextView tvExact;
    private TextView tvOutput;
    private ScrollView scroll;
    private final StringBuilder output = new StringBuilder();

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> saveLauncher;
    private TextView chipSmall;
    private TextView chipBalanced;

    private static final String BASE_URL = "https://huggingface.co/leuconoe/litert-lm-unity-quantized/resolve/main/";
    private static final String URL_SMALL = BASE_URL + "qwen2.5-0.5b-instruct/Qwen2.5-0.5B-Instruct_wi4b64_ekv1280.litertlm";
    private static final String URL_BALANCED = BASE_URL + "qwen2.5-1.5b-instruct/Qwen2.5-1.5B-Instruct_wi4b64_ekv4096.litertlm";

    private void selectPreset(boolean small) {
        etUrl.setText(small ? URL_SMALL : URL_BALANCED);
        Ui.setChipState(chipSmall, small);
        Ui.setChipState(chipBalanced, !small);
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
        root.setFitsSystemWindows(true);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(4));
        ImageButton back = new ImageButton(this, null, 0, R.style.IconButton);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription(getString(R.string.action_back));
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        root.addView(bar);
        root.addView(Ui.largeTitle(this, R.string.ai_title));

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        TextView sub = new TextView(this);
        sub.setText(R.string.ai_subtitle);
        sub.setTextSize(14);
        sub.setTextColor(c(R.attr.cTextSecondary));
        sub.setPadding(dp(6), 0, dp(6), dp(12));
        content.addView(sub);

        // setup card
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
        chipSmall.setOnClickListener(v -> selectPreset(true));
        chipBalanced.setOnClickListener(v -> selectPreset(false));
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, dp(36), 1f);
        c1.setMarginEnd(dp(8));
        presetRow.addView(chipSmall, c1);
        presetRow.addView(chipBalanced, new LinearLayout.LayoutParams(0, dp(36), 1f));
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        prLp.bottomMargin = dp(10);
        setupCard.addView(presetRow, prLp);

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
        if (etUrl.getText().length() == 0 || URL_BALANCED.contentEquals(etUrl.getText())) selectPreset(false);
        else if (URL_SMALL.contentEquals(etUrl.getText())) selectPreset(true);
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
        content.addView(setupCard, Ui.matchWrap(this, 0, 12, 0));

        // solve card
        solveCard = Ui.card(this);
        solveCard.setPadding(dp(18), dp(14), dp(18), dp(16));

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        tvModelInfo = new TextView(this);
        tvModelInfo.setTextSize(13);
        tvModelInfo.setTextColor(c(R.attr.cAccentText));
        infoRow.addView(tvModelInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        solveCard.addView(infoRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView share = actionText(getString(R.string.ai_share_model), c(R.attr.cAccentText));
        share.setOnClickListener(v -> shareModel());
        TextView save = actionText(getString(R.string.ai_save_model), c(R.attr.cAccentText));
        save.setOnClickListener(v -> saveLauncher.launch(modelFile().getName()));
        TextView remove = actionText(getString(R.string.ai_remove_model), getColor(R.color.bad));
        remove.setOnClickListener(v -> removeModel());
        actions.addView(share);
        actions.addView(save);
        actions.addView(remove);
        solveCard.addView(actions);

        etProblem = new EditText(this);
        etProblem.setHint(R.string.ai_problem_hint);
        etProblem.setMinLines(3);
        etProblem.setGravity(Gravity.TOP | Gravity.START);
        etProblem.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etProblem.setTextSize(18);
        etProblem.setTextColor(c(R.attr.cTextPrimary));
        etProblem.setHintTextColor(c(R.attr.cTextHint));
        etProblem.setBackground(Ui.round(getColor(R.color.field), dp(16), 0, 0));
        etProblem.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        epLp.topMargin = dp(10);
        solveCard.addView(etProblem, epLp);

        btnSolve = pill(getString(R.string.ai_solve), true);
        btnSolve.setOnClickListener(v -> onSolveClicked());
        LinearLayout.LayoutParams bsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        bsLp.topMargin = dp(12);
        solveCard.addView(btnSolve, bsLp);
        content.addView(solveCard, Ui.matchWrap(this, 0, 12, 0));

        // result card
        resultCard = Ui.card(this);
        resultCard.setPadding(dp(18), dp(14), dp(18), dp(16));
        tvExact = new TextView(this);
        tvExact.setTextSize(22);
        tvExact.setTextColor(c(R.attr.cAccentText));
        tvExact.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        tvExact.setTextIsSelectable(true);
        tvExact.setVisibility(View.GONE);
        resultCard.addView(tvExact);
        tvOutput = new TextView(this);
        tvOutput.setTextSize(16);
        tvOutput.setLineSpacing(0, 1.15f);
        tvOutput.setTextColor(c(R.attr.cTextPrimary));
        tvOutput.setTextIsSelectable(true);
        LinearLayout.LayoutParams toLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toLp.topMargin = dp(8);
        resultCard.addView(tvOutput, toLp);
        TextView disclaimer = new TextView(this);
        disclaimer.setText(R.string.ai_disclaimer);
        disclaimer.setTextSize(12);
        disclaimer.setTextColor(c(R.attr.cTextHint));
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(12);
        resultCard.addView(disclaimer, dLp);
        resultCard.setVisibility(View.GONE);
        content.addView(resultCard, Ui.matchWrap(this, 0, 0, 0));

        setContentView(root);
    }

    private TextView actionText(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(color);
        t.setPadding(0, dp(8), dp(16), dp(6));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
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

    // ---------- state ----------

    private boolean hasModel() {
        File f = modelFile();
        return f.exists() && f.length() > 1024 * 1024;
    }

    private void refreshState() {
        boolean has = hasModel();
        setupCard.setVisibility(has ? View.GONE : View.VISIBLE);
        solveCard.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            long mb = modelFile().length() / (1024 * 1024);
            tvModelInfo.setText(getString(R.string.ai_model_ready) + " \u00b7 " + mb + " MB");
            loadModelAsync();
        } else {
            resultCard.setVisibility(View.GONE);
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
            btnSolve.setAlpha(1f);
            return;
        }
        btnSolve.setAlpha(0.5f);
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
                btnSolve.setAlpha(1f);
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

    private void onSolveClicked() {
        final LocalLlm llm = LocalLlm.get();
        if (llm.isBusy()) {
            llm.cancel();
            return;
        }
        if (working || !llm.isLoaded()) return;
        String problem = etProblem.getText().toString().trim();
        if (TextUtils.isEmpty(problem)) {
            Toast.makeText(this, R.string.ai_empty_problem, Toast.LENGTH_SHORT).show();
            return;
        }

        String exact = null;
        try {
            String cleaned = normalizeDigits(problem).replaceAll("[=?\\s\\u061f]+$", "");
            double v = Expr.eval(cleaned, store.getAngleMode(), () -> 0.0);
            exact = Fmt.result(v, store.getThousands());
        } catch (Throwable ignored) {
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a patient math tutor. ").append(languageInstruction(problem))
                .append("Explain with short numbered steps in plain text, no LaTeX. Be concise and accurate.\n\n");
        prompt.append("Problem: ").append(problem).append("\n");
        if (exact != null) {
            prompt.append("The exact result is ").append(exact)
                    .append(". Explain step by step how to reach it. Do not change the result.");
        } else {
            prompt.append("Solve it step by step and finish with a line that starts with Answer:");
        }

        output.setLength(0);
        tvOutput.setText(getString(R.string.ai_thinking));
        resultCard.setVisibility(View.VISIBLE);
        if (exact != null) {
            tvExact.setText(getString(R.string.ai_exact_result) + ": " + exact);
            tvExact.setVisibility(View.VISIBLE);
        } else {
            tvExact.setVisibility(View.GONE);
        }
        btnSolve.setText(R.string.ai_stop);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));

        llm.ask("", prompt.toString(), new LocalLlm.Listener() {
            @Override
            public void onToken(String text) {
                runOnUiThread(() -> {
                    output.append(text);
                    tvOutput.setText(output.toString().trim());
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                });
            }

            @Override
            public void onDone() {
                runOnUiThread(() -> btnSolve.setText(R.string.ai_solve));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnSolve.setText(R.string.ai_solve);
                    if (output.length() == 0) tvOutput.setText(getString(R.string.ai_error) + ": " + message);
                });
            }
        });
    }
}
