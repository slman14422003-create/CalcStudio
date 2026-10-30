package com.calc.app;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public final class IconPickerActivity extends AppCompatActivity {

    private static final int COLUMNS = 3;

    private LinearLayout grid;
    private int selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selected = IconSwitcher.current(this);

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
        root.addView(Ui.largeTitle(this, R.string.settings_app_icon));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 28));

        grid = Ui.card(this);
        grid.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 8));
        content.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText(R.string.app_icon_hint);
        hint.setTextSize(15);
        hint.setTextColor(Ui.attrColor(this, R.attr.cTextSecondary));
        hint.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);
        content.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        render();
    }

    private void render() {
        grid.removeAllViews();
        int count = IconSwitcher.ALL.length;
        for (int start = 0; start < count; start += COLUMNS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = start; i < start + COLUMNS; i++) {
                if (i < count) row.addView(cell(i));
                else row.addView(spacer());
            }
            grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    private View cell(final int index) {
        IconSwitcher.Variant variant = IconSwitcher.ALL[index];
        boolean on = index == selected;

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setContentDescription(getString(variant.nameRes));

        FrameLayout ring = new FrameLayout(this);
        int pad = Ui.dp(this, 7);
        ring.setPadding(pad, pad, pad, pad);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setColor(0);
        stroke.setCornerRadius(Ui.dp(this, 30));
        if (on) stroke.setStroke(Ui.dp(this, 3), Ui.attrColor(this, R.attr.cAccent));
        ring.setBackground(stroke);

        ImageView icon = new ImageView(this);
        icon.setImageResource(variant.iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ring.addView(icon, new FrameLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        cell.addView(ring, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText(variant.nameRes);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", on ? Typeface.BOLD : Typeface.NORMAL));
        label.setTextColor(Ui.attrColor(this, on ? R.attr.cAccentText : R.attr.cTextSecondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        cell.addView(label, lp);

        cell.setOnClickListener(v -> {
            if (index == selected) return;
            IconSwitcher.apply(this, index);
            selected = index;
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            render();
        });
        return cell;
    }
}
