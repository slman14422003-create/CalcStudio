package com.calc.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.os.LocaleListCompat;

public final class SettingsActivity extends AppCompatActivity {

    private static final int ROW_HEIGHT_DP = 56;
    private static final int DIVIDER_INSET_DP = 60;

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
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));

        ImageButton back = new ImageButton(this, null, 0, R.style.IconButton);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription(getString(R.string.action_back));
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        root.addView(bar);
        root.addView(Ui.largeTitle(this, R.string.settings_title));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 0), Ui.dp(this, 16), Ui.dp(this, 28));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout g0 = group(list, R.string.group_language);
        final String[] langs = {getString(R.string.lang_system), getString(R.string.lang_arabic), getString(R.string.lang_english)};
        addRow(g0, choiceRow(R.drawable.ic_set_globe, R.color.info, R.string.settings_language, langs, () -> currentLang(), i -> setLang(i)));

        LinearLayout g1 = group(list, R.string.group_general);
        addRow(g1, settingRow(R.drawable.ic_set_haptic, R.color.violet, R.string.settings_haptic, store.getHaptic(), (sw, checked) -> store.setHaptic(checked)));
        addRow(g1, settingRow(R.drawable.ic_set_number, R.color.ok, R.string.settings_thousands, store.getThousands(), (sw, checked) -> store.setThousands(checked)));
        addRow(g1, settingRow(R.drawable.ic_set_eye, R.color.warn, R.string.settings_auto_preview, store.getAutoPreview(), (sw, checked) -> store.setAutoPreview(checked)));
        final String[] angles = {getString(R.string.deg), getString(R.string.rad), getString(R.string.grad)};
        addRow(g1, choiceRow(R.drawable.ic_set_angle, R.color.indigo, R.string.settings_angle_default, angles, () -> store.getAngleMode(), i -> store.setAngleMode(i)));

        LinearLayout g2 = group(list, R.string.group_format);
        final String[] modes = {getString(R.string.fmt_auto), getString(R.string.fmt_fixed), getString(R.string.fmt_sci), getString(R.string.fmt_eng)};
        addRow(g2, choiceRow(R.drawable.ic_set_format, R.color.pink, R.string.settings_format, modes, () -> store.getFormatMode(), i -> store.setFormatMode(i)));
        final String[] digitOpts = {"6", "8", "10", "12", "15"};
        final int[] digitVals = {6, 8, 10, 12, 15};
        addRow(g2, choiceRow(R.drawable.ic_set_digits, R.color.teal, R.string.settings_digits, digitOpts, () -> indexOf(digitVals, store.getDigits()), i -> store.setDigits(digitVals[i])));
        final String[] decOpts = {"0", "1", "2", "3", "4", "6", "8", "10", "12"};
        final int[] decVals = {0, 1, 2, 3, 4, 6, 8, 10, 12};
        addRow(g2, choiceRow(R.drawable.ic_set_decimals, R.color.bad, R.string.settings_decimals, decOpts, () -> indexOf(decVals, store.getDecimals()), i -> store.setDecimals(decVals[i])));

        LinearLayout g3 = group(list, R.string.group_precision);
        addRow(g3, settingRow(R.drawable.ic_set_precision, R.color.info, R.string.settings_high_precision, store.getHighPrecision(), (sw, checked) -> store.setHighPrecision(checked)));
        final String[] hpOpts = {"20", "30", "50", "100", "200"};
        final int[] hpVals = {20, 30, 50, 100, 200};
        addRow(g3, choiceRow(R.drawable.ic_set_digits, R.color.teal, R.string.settings_hp_digits, hpOpts, () -> indexOf(hpVals, store.getHpDigits()), i -> store.setHpDigits(hpVals[i])));

        setContentView(root);
    }

    private LinearLayout group(LinearLayout parent, int titleRes) {
        TextView t = new TextView(this);
        t.setText(titleRes);
        t.setTextSize(13);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setTextColor(Ui.attrColor(this, R.attr.cTextSecondary));
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 8));
        parent.addView(t);
        LinearLayout g = Ui.card(this);
        parent.addView(g, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return g;
    }

    private void addRow(LinearLayout group, View row) {
        if (group.getChildCount() > 0) {
            group.addView(Ui.divider(this, DIVIDER_INSET_DP));
        }
        group.addView(row);
    }

    private int currentLang() {
        LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
        if (l.isEmpty()) return 0;
        String tag = l.toLanguageTags();
        return tag.startsWith("ar") ? 1 : 2;
    }

    private void setLang(int i) {
        String tag = i == 1 ? "ar" : (i == 2 ? "en" : "");
        AppCompatDelegate.setApplicationLocales(tag.isEmpty() ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(tag));
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return 0;
    }

    private interface IntGetter { int get(); }
    private interface IntSetter { void set(int i); }

    private LinearLayout baseRow(int iconRes, int colorRes, int labelRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, ROW_HEIGHT_DP));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));

        row.addView(Ui.tile(this, iconRes, colorRes));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(16);
        label.setTextColor(Ui.attrColor(this, R.attr.cTextPrimary));
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);
        return row;
    }

    private LinearLayout choiceRow(int iconRes, int colorRes, int labelRes, String[] options, IntGetter getter, IntSetter setter) {
        LinearLayout row = baseRow(iconRes, colorRes, labelRes);

        TextView value = new TextView(this);
        value.setTextSize(16);
        value.setTextColor(Ui.attrColor(this, R.attr.cTextSecondary));
        value.setText(options[Math.max(0, Math.min(options.length - 1, getter.get()))]);
        row.addView(value);

        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.ic_chevron_end);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
        clp.setMarginStart(Ui.dp(this, 4));
        chev.setLayoutParams(clp);
        chev.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chev);

        row.setBackgroundResource(R.drawable.bg_row_flat);
        row.setClickable(true);
        row.setFocusable(true);
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

    private interface OnToggle { void onChange(SwitchCompat sw, boolean checked); }

    private LinearLayout settingRow(int iconRes, int colorRes, int labelRes, boolean initial, OnToggle onToggle) {
        LinearLayout row = baseRow(iconRes, colorRes, labelRes);

        SwitchCompat sw = new SwitchCompat(this);
        sw.setTrackDrawable(getDrawable(R.drawable.switch_track));
        sw.setThumbDrawable(getDrawable(R.drawable.switch_thumb));
        sw.setShowText(false);
        sw.setSwitchMinWidth(Ui.dp(this, 51));
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((btn, checked) -> onToggle.onChange(sw, checked));
        row.addView(sw);

        row.setBackgroundResource(R.drawable.bg_row_flat);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }
}
