package com.calc.app;

public final class HistoryItem {
    public static final int KIND_CALC = 0;
    public static final int KIND_PROG = 1;
    public static final int KIND_CONV = 2;

    public final String expression;
    public final String result;
    public final long time;
    public final int kind;

    public HistoryItem(String expression, String result, long time) {
        this(expression, result, time, KIND_CALC);
    }

    public HistoryItem(String expression, String result, long time, int kind) {
        this.expression = expression;
        this.result = result;
        this.time = time;
        this.kind = kind;
    }
}
