package com.calc.app;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class CalcTool {

    public static final Pattern TAG = Pattern.compile("\\[\\[\\s*calc\\s*:([^\\]]{1,200})\\]\\]", Pattern.CASE_INSENSITIVE);

    private CalcTool() {}

    public static String normalizeDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') b.append((char) ('0' + (ch - '\u0660')));
            else if (ch >= '\u06f0' && ch <= '\u06f9') b.append((char) ('0' + (ch - '\u06f0')));
            else if (ch == '\u066b') b.append('.');
            else if (ch == '\u066c') continue;
            else if (ch == '\u066a') b.append('%');
            else b.append(ch);
        }
        return b.toString();
    }

    public static String run(Store store, String raw) {
        if (raw == null) return null;
        String e = normalizeDigits(raw).trim().replaceAll("[=?\\s\\u061f]+$", "");
        if (e.isEmpty()) return null;
        int angle = store.getAngleMode();
        try {
            if (store.getHighPrecision()) {
                try {
                    BigDecimal b = BigExpr.eval(e, angle, store.getHpDigits(), BigDecimal.ZERO, true);
                    return Fmt.big(b, false);
                } catch (BigExpr.Unsupported | ArithmeticException | NumberFormatException ignored) {
                }
            }
            double v = Expr.eval(e, angle, () -> 0.0);
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return Fmt.result(v, false);
        } catch (Throwable t) {
            return null;
        }
    }
}
