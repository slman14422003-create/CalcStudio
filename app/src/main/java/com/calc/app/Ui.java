package com.calc.app;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        l.setLayoutParams(lp);
        return l;
    }

    public static Button key(Context c, String text, int styleResId, float weight) {
        Button b = new Button(c, null, 0, styleResId);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        int m = dp(c, 4);
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

    public static TextView small(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(attrColor(c, R.attr.cTextSecondary));
        return t;
    }

}
