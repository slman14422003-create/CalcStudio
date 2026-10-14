package com.calc.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public final class SettingsActivity extends AppCompatActivity {

    private Store store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.attrColor(this, R.attr.cBg));
        root.setFitsSystemWindows(true);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 14);
        bar.setPadding(Ui.dp(this, 16), pad, Ui.dp(this, 16), Ui.dp(this, 10));

        Button back = new Button(this, null, 0, R.style.IconButton);
        back.setText("←");
        back.setAllCaps(false);
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setTextSize(20);
        back.setPadding(0, 0, 0, 0);
        back.setMinWidth(0);
        back.setMinHeight(0);
        back.setMinimumWidth(0);
        back.setMinimumHeight(0);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(Ui.attrColor(this, R.attr.cTextPrimary));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(Ui.dp(this, 14));
        title.setLayoutParams(tLp);
        bar.addView(title);

        root.addView(bar);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 24));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout g1 = group(list, R.string.group_general);
        addRow(g1, settingRow(R.string.settings_haptic, store.getHaptic(), (sw, checked) -> store.setHaptic(checked)));
        addRow(g1, settingRow(R.string.settings_thousands, store.getThousands(), (sw, checked) -> store.setThousands(checked)));
        addRow(g1, settingRow(R.string.settings_auto_preview, store.getAutoPreview(), (sw, checked) -> store.setAutoPreview(checked)));
        final String[] angles = {getString(R.string.deg), getString(R.string.rad), getString(R.string.grad)};
        addRow(g1, choiceRow(R.string.settings_angle_default, angles, () -> store.getAngleMode(), i -> store.setAngleMode(i)));

        LinearLayout g2 = group(list, R.string.group_format);
        final String[] modes = {getString(R.string.fmt_auto), getString(R.string.fmt_fixed), getString(R.string.fmt_sci), getString(R.string.fmt_eng)};
        addRow(g2, choiceRow(R.string.settings_format, modes, () -> store.getFormatMode(), i -> store.setFormatMode(i)));
        final String[] digitOpts = {"6", "8", "10", "12", "15"};
        final int[] digitVals = {6, 8, 10, 12, 15};
        addRow(g2, choiceRow(R.string.settings_digits, digitOpts, () -> indexOf(digitVals, store.getDigits()), i -> store.setDigits(digitVals[i])));
        final String[] decOpts = {"0", "1", "2", "3", "4", "6", "8", "10", "12"};
        final int[] decVals = {0, 1, 2, 3, 4, 6, 8, 10, 12};
        addRow(g2, choiceRow(R.string.settings_decimals, decOpts, () -> indexOf(decVals, store.getDecimals()), i -> store.setDecimals(decVals[i])));

        LinearLayout g3 = group(list, R.string.group_precision);
        addRow(g3, settingRow(R.string.settings_high_precision, store.getHighPrecision(), (sw, checked) -> store.setHighPrecision(checked)));
        final String[] hpOpts = {"20", "30", "50", "100", "200"};
        final int[] hpVals = {20, 30, 50, 100, 200};
        addRow(g3, choiceRow(R.string.settings_hp_digits, hpOpts, () -> indexOf(hpVals, store.getHpDigits()), i -> store.setHpDigits(hpVals[i])));

        setContentView(root);
    }

    private LinearLayout group(LinearLayout parent, int titleRes) {
        TextView t = new TextView(this);
        t.setText(titleRes);
        t.setTextSize(12);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(Ui.attrColor(this, R.attr.cTextSecondary));
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 16), 0, Ui.dp(this, 8));
        parent.addView(t);
        LinearLayout g = Ui.card(this);
        parent.addView(g, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    private void addRow(LinearLayout group, View row) {
        if (group.getChildCount() > 0) {
            View d = new View(this);
            d.setBackgroundColor(Ui.attrColor(this, R.attr.cStrokeSoft));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMarginStart(Ui.dp(this, 16));
            group.addView(d, lp);
        }
        group.addView(row);
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return 0;
    }

    private interface IntGetter { int get(); }
    private interface IntSetter { void set(int i); }

    private LinearLayout choiceRow(int labelRes, String[] options, IntGetter getter, IntSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 60));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(16);
        label.setTextColor(Ui.attrColor(this, R.attr.cTextPrimary));
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        TextView value = new TextView(this);
        value.setTextSize(16);
        value.setTextColor(Ui.attrColor(this, R.attr.cAccentText));
        value.setText(options[Math.max(0, Math.min(options.length - 1, getter.get()))]);
        row.addView(value);
        TextView chev = new TextView(this);
        chev.setText("›");
        chev.setTextSize(22);
        chev.setTextColor(Ui.attrColor(this, R.attr.cTextHint));
        chev.setPadding(Ui.dp(this, 8), 0, 0, 0);
        row.addView(chev);
        row.setBackgroundResource(R.drawable.bg_row_ripple);

        row.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(labelRes)
                .setSingleChoiceItems(options, getter.get(), (d, which) -> {
                    setter.set(which);
                    value.setText(options[which]);
                    d.dismiss();
                })
                .show());
        return row;
    }

    private interface OnToggle { void onChange(Switch sw, boolean checked); }

    private LinearLayout settingRow(int labelRes, boolean initial, OnToggle onToggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 60));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(16);
        label.setTextColor(Ui.attrColor(this, R.attr.cTextPrimary));
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(lLp);
        row.addView(label);

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((btn, checked) -> onToggle.onChange(sw, checked));
        row.addView(sw);

        return row;
    }
}
