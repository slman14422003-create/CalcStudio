package com.calc.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

public final class Ui {

    private Ui() {}

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static int sp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    public static int attrColor(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    public static LinearLayout row(Context c) {
        return row(c, 1f);
    }

    public static LinearLayout row(Context c, float weight) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, weight);
        l.setLayoutParams(lp);
        return l;
    }

    public static Button key(Context c, String text, int styleResId, float weight) {
        return key(c, text, styleResId, weight, 3);
    }

    public static Button key(Context c, String text, int styleResId, float weight, int marginDp) {
        Button b = new Button(c, null, 0, styleResId);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        int m = dp(c, marginDp);
        lp.setMargins(m, m, m, m);
        b.setLayoutParams(lp);
        b.setPadding(0, 0, 0, 0);
        b.setIncludeFontPadding(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        return b;
    }

    public static void autoFit(TextView t, int minSp, int maxSp) {
        t.setSingleLine(true);
        t.setMaxLines(1);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(t, minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
    }

    public static GradientDrawable round(int color, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }

    public static TextView largeTitle(Context c, int res) {
        TextView t = new TextView(c);
        t.setText(res);
        t.setTextSize(32);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setLetterSpacing(-0.01f);
        t.setTextColor(attrColor(c, R.attr.cTextPrimary));
        t.setPadding(dp(c, 22), dp(c, 2), dp(c, 22), dp(c, 8));
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.bg_card);
        l.setClipToOutline(true);
        return l;
    }

    public static LinearLayout.LayoutParams matchWrap(Context c, int top, int bottom, int side) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, side), dp(c, top), dp(c, side), dp(c, bottom));
        return lp;
    }

    public static TextView small(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(attrColor(c, R.attr.cTextSecondary));
        return t;
    }

    public static TextView chip(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);
        setChipState(t, false);
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        return t;
    }

    public static void setChipState(TextView t, boolean selected) {
        Context c = t.getContext();
        int left = t.getPaddingLeft();
        int right = t.getPaddingRight();
        t.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_idle);
        t.setPadding(left, 0, right, 0);
        t.setTextColor(selected ? c.getColor(R.color.on_accent) : attrColor(c, R.attr.cTextPrimary));
        t.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    }

    public static void setTagState(TextView t, boolean active) {
        Context c = t.getContext();
        int left = t.getPaddingLeft();
        int top = t.getPaddingTop();
        int right = t.getPaddingRight();
        int bottom = t.getPaddingBottom();
        t.setBackgroundResource(active ? R.drawable.bg_tag_active : R.drawable.bg_tag_idle);
        t.setPadding(left, top, right, bottom);
        t.setTextColor(active ? c.getColor(R.color.on_accent) : attrColor(c, R.attr.cTextSecondary));
    }

    public static ImageView tile(Context c, int iconRes, int colorRes) {
        ImageView v = new ImageView(c);
        v.setImageResource(iconRes);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = dp(c, 5);
        v.setPadding(p, p, p, p);
        v.setBackground(round(c.getColor(colorRes), dp(c, 8), 0, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30));
        lp.setMarginEnd(dp(c, 14));
        v.setLayoutParams(lp);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    public static View divider(Context c, int startMarginDp) {
        View d = new View(c);
        d.setBackgroundColor(attrColor(c, R.attr.cStrokeSoft));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMarginStart(dp(c, startMarginDp));
        d.setLayoutParams(lp);
        return d;
    }

}
