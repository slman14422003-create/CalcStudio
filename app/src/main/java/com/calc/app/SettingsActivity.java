package com.calc.app;

import android.os.Bundle;
import android.view.Gravity;
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
        bar.setPadding(pad, pad, pad, pad);

        Button back = new Button(this);
        back.setText("←");
        back.setMinWidth(0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(18);
        title.setTextColor(Ui.attrColor(this, R.attr.cTextPrimary));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(Ui.dp(this, 10));
        title.setLayoutParams(tLp);
        bar.addView(title);

        root.addView(bar);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list.addView(settingRow(R.string.settings_haptic, store.getHaptic(), (sw, checked) -> store.setHaptic(checked)));
        list.addView(settingRow(R.string.settings_thousands, store.getThousands(), (sw, checked) -> store.setThousands(checked)));
        list.addView(settingRow(R.string.settings_auto_preview, store.getAutoPreview(), (sw, checked) -> store.setAutoPreview(checked)));

        final String[] angles = {getString(R.string.deg), getString(R.string.rad), getString(R.string.grad)};
        list.addView(choiceRow(R.string.settings_angle_default, angles, () -> store.getAngleMode(), i -> store.setAngleMode(i)));

        final String[] modes = {getString(R.string.fmt_auto), getString(R.string.fmt_fixed), getString(R.string.fmt_sci), getString(R.string.fmt_eng)};
        list.addView(choiceRow(R.string.settings_format, modes, () -> store.getFormatMode(), i -> store.setFormatMode(i)));

        final String[] digitOpts = {"6", "8", "10", "12", "15"};
        final int[] digitVals = {6, 8, 10, 12, 15};
        list.addView(choiceRow(R.string.settings_digits, digitOpts, () -> indexOf(digitVals, store.getDigits()), i -> store.setDigits(digitVals[i])));

        final String[] decOpts = {"0", "1", "2", "3", "4", "6", "8", "10", "12"};
        final int[] decVals = {0, 1, 2, 3, 4, 6, 8, 10, 12};
        list.addView(choiceRow(R.string.settings_decimals, decOpts, () -> indexOf(decVals, store.getDecimals()), i -> store.setDecimals(decVals[i])));

        list.addView(settingRow(R.string.settings_high_precision, store.getHighPrecision(), (sw, checked) -> store.setHighPrecision(checked)));

        final String[] hpOpts = {"20", "30", "50", "100", "200"};
        final int[] hpVals = {20, 30, 50, 100, 200};
        list.addView(choiceRow(R.string.settings_hp_digits, hpOpts, () -> indexOf(hpVals, store.getHpDigits()), i -> store.setHpDigits(hpVals[i])));

        setContentView(root);
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
        int pad = Ui.dp(this, 16);
        row.setPadding(pad, pad, pad, pad);

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
        int pad = Ui.dp(this, 16);
        row.setPadding(pad, pad, pad, pad);

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
