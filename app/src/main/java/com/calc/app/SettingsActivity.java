package com.calc.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
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

        root.addView(settingRow(R.string.settings_haptic, store.getHaptic(), (sw, checked) -> store.setHaptic(checked)));
        root.addView(settingRow(R.string.settings_thousands, store.getThousands(), (sw, checked) -> store.setThousands(checked)));

        setContentView(root);
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
