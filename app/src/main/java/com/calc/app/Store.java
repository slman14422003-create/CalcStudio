package com.calc.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Store {

    private static final String PREFS = "calc_prefs";
    private static final String K_MEMORY = "memory";
    private static final String K_ANGLE = "angle_mode";
    private static final String K_HAPTIC = "haptic";
    private static final String K_THOUSANDS = "thousands";
    private static final String K_HISTORY = "history";
    private static final String K_LAST_TAB = "last_tab";
    private static final String K_LAST_BASE = "last_base";
    private static final String K_DIGITS = "digits";
    private static final String K_FORMAT = "format_mode";
    private static final String K_DECIMALS = "decimals";
    private static final String K_HP = "high_precision";
    private static final String K_HP_DIGITS = "hp_digits";
    private static final String K_AUTO_PREVIEW = "auto_preview";
    private static final int MAX_HISTORY = 200;

    private final SharedPreferences prefs;

    public Store(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public double getMemory() { return Double.longBitsToDouble(prefs.getLong(K_MEMORY, 0L)); }
    public void setMemory(double v) { prefs.edit().putLong(K_MEMORY, Double.doubleToLongBits(v)).apply(); }

    public int getAngleMode() { return prefs.getInt(K_ANGLE, Expr.DEG); }
    public void setAngleMode(int m) { prefs.edit().putInt(K_ANGLE, m).apply(); }

    public boolean getHaptic() { return prefs.getBoolean(K_HAPTIC, true); }
    public void setHaptic(boolean b) { prefs.edit().putBoolean(K_HAPTIC, b).apply(); }

    public boolean getThousands() { return prefs.getBoolean(K_THOUSANDS, true); }
    public void setThousands(boolean b) { prefs.edit().putBoolean(K_THOUSANDS, b).apply(); }

    public int getDigits() { return prefs.getInt(K_DIGITS, 12); }
    public void setDigits(int d) { prefs.edit().putInt(K_DIGITS, d).apply(); }

    public int getFormatMode() { return prefs.getInt(K_FORMAT, Fmt.MODE_AUTO); }
    public void setFormatMode(int m) { prefs.edit().putInt(K_FORMAT, m).apply(); }

    public int getDecimals() { return prefs.getInt(K_DECIMALS, 6); }
    public void setDecimals(int d) { prefs.edit().putInt(K_DECIMALS, d).apply(); }

    public boolean getHighPrecision() { return prefs.getBoolean(K_HP, false); }
    public void setHighPrecision(boolean b) { prefs.edit().putBoolean(K_HP, b).apply(); }

    public int getHpDigits() { return prefs.getInt(K_HP_DIGITS, 50); }
    public void setHpDigits(int d) { prefs.edit().putInt(K_HP_DIGITS, d).apply(); }

    public boolean getAutoPreview() { return prefs.getBoolean(K_AUTO_PREVIEW, true); }
    public void setAutoPreview(boolean b) { prefs.edit().putBoolean(K_AUTO_PREVIEW, b).apply(); }

    public int getLastTab() { return prefs.getInt(K_LAST_TAB, 1); }
    public void setLastTab(int t) { prefs.edit().putInt(K_LAST_TAB, t).apply(); }

    public int getLastBase() { return prefs.getInt(K_LAST_BASE, ProgrammerEngine.DEC); }
    public void setLastBase(int b) { prefs.edit().putInt(K_LAST_BASE, b).apply(); }

    public void addHistory(String expression, String result) {
        List<HistoryItem> items = getHistory();
        items.add(0, new HistoryItem(expression, result, System.currentTimeMillis()));
        while (items.size() > MAX_HISTORY) items.remove(items.size() - 1);
        saveHistory(items);
    }

    public void clearHistory() {
        prefs.edit().remove(K_HISTORY).apply();
    }

    public List<HistoryItem> getHistory() {
        List<HistoryItem> out = new ArrayList<>();
        String raw = prefs.getString(K_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new HistoryItem(o.getString("e"), o.getString("r"), o.getLong("t")));
            }
        } catch (JSONException ignored) {}
        return out;
    }

    private void saveHistory(List<HistoryItem> items) {
        JSONArray arr = new JSONArray();
        for (HistoryItem it : items) {
            JSONObject o = new JSONObject();
            try {
                o.put("e", it.expression);
                o.put("r", it.result);
                o.put("t", it.time);
                arr.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(K_HISTORY, arr.toString()).apply();
    }
}
