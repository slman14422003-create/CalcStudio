package com.calc.app;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Fmt {

    public static final int MODE_AUTO = 0;
    public static final int MODE_FIXED = 1;
    public static final int MODE_SCI = 2;
    public static final int MODE_ENG = 3;

    private static volatile int digits = 12;
    private static volatile int mode = MODE_AUTO;
    private static volatile int decimals = 6;

    private Fmt() {}

    public static void apply(Store st) {
        digits = st.getDigits();
        mode = st.getFormatMode();
        decimals = st.getDecimals();
    }

    public static String big(BigDecimal v, boolean thousands) {
        BigDecimal bd = v.stripTrailingZeros();
        if (bd.signum() == 0) return "0";
        int exp = bd.precision() - bd.scale() - 1;
        if (mode == MODE_SCI || mode == MODE_ENG || exp < -9 || exp >= 15 + Math.max(0, digits - 15)) {
            String u = bd.unscaledValue().abs().toString();
            String m = u.length() > 1 ? u.charAt(0) + "." + u.substring(1) : u;
            return (bd.signum() < 0 ? "-" : "") + m + "×10^" + exp;
        }
        if (mode == MODE_FIXED) bd = bd.setScale(decimals, RoundingMode.HALF_UP);
        String plain = bd.toPlainString();
        if (thousands) plain = addThousands(plain);
        return plain;
    }

    public static String result(double v, boolean thousands) {
        if (Double.isNaN(v)) return "Error";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "-∞";

        double abs = Math.abs(v);
        if (v != 0 && mode == MODE_SCI) return sci(v);
        if (v != 0 && mode == MODE_ENG) return eng(v);
        if (v != 0 && mode == MODE_AUTO && (abs < 1e-9 || abs >= 1e15)) {
            return sci(v);
        }
        if (mode == MODE_FIXED) {
            String f = new BigDecimal(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
            if (f.startsWith("-") && f.replace("-", "").replace("0", "").replace(".", "").isEmpty()) f = f.substring(1);
            return thousands ? addThousands(f) : f;
        }

        BigDecimal bd = new BigDecimal(v, new MathContext(Math.min(digits, 15), RoundingMode.HALF_UP));
        bd = bd.stripTrailingZeros();
        String plain = bd.toPlainString();

        if (plain.contains(".")) {
            while (plain.endsWith("0")) plain = plain.substring(0, plain.length() - 1);
            if (plain.endsWith(".")) plain = plain.substring(0, plain.length() - 1);
        }
        if (plain.equals("-0")) plain = "0";

        if (thousands) plain = addThousands(plain);
        return plain;
    }

    private static String eng(double v) {
        int exp = (int) Math.floor(Math.log10(Math.abs(v)));
        int e3 = Math.floorDiv(exp, 3) * 3;
        BigDecimal m = new BigDecimal(v / Math.pow(10, e3), new MathContext(Math.min(digits, 15), RoundingMode.HALF_UP)).stripTrailingZeros();
        return m.toPlainString() + "×10^" + e3;
    }

    private static String sci(double v) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.######E0", sym);
        String s = df.format(v);
        s = s.replace("E", "×10^");
        return s;
    }

    private static String addThousands(String plain) {
        boolean neg = plain.startsWith("-");
        if (neg) plain = plain.substring(1);
        String intPart = plain;
        String fracPart = "";
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            intPart = plain.substring(0, dot);
            fracPart = plain.substring(dot);
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            out.append(intPart.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) out.append(',');
        }
        out.reverse();
        return (neg ? "-" : "") + out + fracPart;
    }

    public static String prettyExpr(String s, boolean thousands) {
        StringBuilder out = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) {
                int j = i;
                while (j < n && Character.isDigit(s.charAt(j))) j++;
                String run = s.substring(i, j);
                char prev = i > 0 ? s.charAt(i - 1) : ' ';
                boolean skip = prev == '.' || Character.isLetter(prev);
                if (!skip && i >= 2 && (prev == '+' || prev == '-' || prev == '\u2212') && s.charAt(i - 2) == 'E') skip = true;
                out.append(thousands && !skip && run.length() > 3 ? addThousands(run) : run);
                i = j;
                continue;
            }
            if (ch == '\u00d7' || ch == '\u00f7') {
                out.append(' ').append(ch).append(' ');
            } else if (ch == '+' || ch == '-' || ch == '\u2212') {
                if (isUnaryAt(s, i)) out.append(ch);
                else out.append(' ').append(ch).append(' ');
            } else if (ch == ',') {
                out.append(", ");
            } else {
                out.append(ch);
            }
            i++;
        }
        return out.toString();
    }

    private static boolean isUnaryAt(String s, int i) {
        int k = i - 1;
        while (k >= 0 && s.charAt(k) == ' ') k--;
        if (k < 0) return true;
        return "(+-\u2212\u00d7\u00f7^,E".indexOf(s.charAt(k)) >= 0;
    }

    public static String errorMessage(Expr.EvalException ex, android.content.Context ctx) {
        String m = ex.getMessage();
        if (m == null) return ctx.getString(R.string.error_math);
        switch (m) {
            case "div0": return ctx.getString(R.string.error_div_zero);
            case "syntax":
            case "empty": return ctx.getString(R.string.error_syntax);
            default: return ctx.getString(R.string.error_math);
        }
    }
}
