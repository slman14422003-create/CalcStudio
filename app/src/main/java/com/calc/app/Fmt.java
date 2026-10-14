package com.calc.app;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Fmt {

    private Fmt() {}

    public static String result(double v, boolean thousands) {
        if (Double.isNaN(v)) return "Error";
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "-∞";

        double abs = Math.abs(v);
        if (v != 0 && (abs < 1e-9 || abs >= 1e15)) {
            return sci(v);
        }

        BigDecimal bd = new BigDecimal(v, new MathContext(12, RoundingMode.HALF_UP));
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
