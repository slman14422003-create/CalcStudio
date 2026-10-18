package com.calc.app;

import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public final class Motion {

    public static final int SHORT = 120;
    public static final int MEDIUM = 220;
    public static final int LONG = 320;

    public static final Interpolator STANDARD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private Motion() {}

    public static void enter(View v, int riseDp) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(Ui.dp(v.getContext(), riseDp));
        v.animate().alpha(1f).translationY(0f).setDuration(MEDIUM).setInterpolator(STANDARD).start();
    }

    public static void enter(View v) {
        enter(v, 10);
    }
}
