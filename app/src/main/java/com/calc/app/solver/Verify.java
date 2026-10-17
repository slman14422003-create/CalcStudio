package com.calc.app.solver;

import java.util.HashMap;
import java.util.Map;

public final class Verify {

    private Verify() {}

    private static final double[] POINTS = {0.37, 0.91, 1.43, 2.12, 2.7, -0.52, -1.3, 3.4, 0.15, 1.9};

    public static boolean derivativeOf(Node F, Node f, String x) {
        int ok = 0;
        int bad = 0;
        Map<String, Double> env = new HashMap<>();
        java.util.List<String> vs = new java.util.ArrayList<>();
        F.vars(vs);
        f.vars(vs);
        for (String v : vs) if (!v.equals(x)) env.put(v, 1.7);
        double h = 1e-5;
        for (double p : POINTS) {
            try {
                env.put(x, p + h);
                double a = F.eval(env);
                env.put(x, p - h);
                double b = F.eval(env);
                env.put(x, p);
                double fv = f.eval(env);
                double d = (a - b) / (2 * h);
                if (Double.isNaN(d) || Double.isInfinite(d) || Double.isNaN(fv) || Double.isInfinite(fv)) continue;
                if (Math.abs(d - fv) <= 1e-4 * (1 + Math.abs(fv))) ok++; else bad++;
            } catch (RuntimeException e) {
            }
        }
        return ok >= 3 && bad == 0;
    }

    public static boolean equalNumeric(Node a, Node b, String x) {
        int ok = 0;
        int bad = 0;
        Map<String, Double> env = new HashMap<>();
        java.util.List<String> vs = new java.util.ArrayList<>();
        a.vars(vs);
        b.vars(vs);
        for (String v : vs) if (!v.equals(x)) env.put(v, 1.7);
        for (double p : POINTS) {
            try {
                env.put(x, p);
                double u = a.eval(env);
                double w = b.eval(env);
                if (Double.isNaN(u) || Double.isNaN(w) || Double.isInfinite(u) || Double.isInfinite(w)) continue;
                if (Math.abs(u - w) <= 1e-8 * (1 + Math.abs(u))) ok++; else bad++;
            } catch (RuntimeException e) {
            }
        }
        return ok >= 3 && bad == 0;
    }
}
