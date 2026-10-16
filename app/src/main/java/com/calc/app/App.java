package com.calc.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public final class App extends Application {
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_COMPLETE) LocalLlm.get().release();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Store.nightModeFor(new Store(this).getTheme()));
        LocalLlm.preload(this);
    }
}
