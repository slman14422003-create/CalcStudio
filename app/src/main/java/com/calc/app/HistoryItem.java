package com.calc.app;

public final class HistoryItem {
    public final String expression;
    public final String result;
    public final long time;

    public HistoryItem(String expression, String result, long time) {
        this.expression = expression;
        this.result = result;
        this.time = time;
    }
}
