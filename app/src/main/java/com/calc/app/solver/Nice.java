package com.calc.app.solver;

public final class Nice {
    private Nice() {}

    public static String dec(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "\u221e" : "-\u221e";
        return new java.math.BigDecimal(v).round(new java.math.MathContext(10)).stripTrailingZeros().toPlainString();
    }

    public static String of(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "\u221e" : "-\u221e";
        if (Math.abs(v - Math.rint(v)) < 1e-9) return Node.fmtNum(Math.rint(v));
        long[] r = Node.ratio(v);
        if (r != null && r[1] <= 1000) return (v < 0 ? "-" : "") + r[0] + "/" + r[1];
        if (Math.abs(v) > 1e-9) {
            long[] q = Node.ratio(v / Math.PI);
            if (q != null && q[1] <= 12) {
                String top = q[0] == 1 ? "pi" : q[0] + "pi";
                return (v < 0 ? "-" : "") + top + "/" + q[1] + " \u2248 " + dec(v);
            }
            double w = v / Math.PI;
            if (Math.abs(w - Math.rint(w)) < 1e-9) return (Math.rint(w) == 1 ? "" : Node.fmtNum(Math.rint(w))) + "pi \u2248 " + dec(v);
            double ev = Math.log(Math.abs(v));
            if (Math.abs(ev - Math.rint(ev)) < 1e-9 && Math.abs(ev) < 20) return (v < 0 ? "-" : "") + "e^" + Node.fmtNum(Math.rint(ev)) + " \u2248 " + dec(v);
        }
        return dec(v);
    }

    public static String approx(double v) {
        String n = of(v);
        if (n.contains("\u2248")) return n;
        if (Math.abs(v - Math.rint(v)) < 1e-9) return n;
        return n.contains("/") ? n + " \u2248 " + dec(v) : n;
    }
}
