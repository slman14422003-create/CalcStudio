package com.calc.app.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Limit {

    private Limit() {}

    private static double at(Node f, String x, double v) {
        Map<String, Double> env = new HashMap<>();
        env.put(x, v);
        try { return f.eval(env); } catch (RuntimeException e) { return Double.NaN; }
    }

    private static String show(double v) { return Nice.of(v); }

    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    public static Solution run(String expr, String x, String pointText, int side) {
        String problem = "lim " + x + "\u2192" + pointText + " " + expr;
        Node f;
        Double a = null;
        boolean inf = false;
        int infSign = 1;
        String pt = pointText.trim().toLowerCase().replace("\u221e", "inf").replace("infinity", "inf");
        try {
            f = Simplify.run(Parser.parse(expr));
            if (pt.equals("inf") || pt.equals("+inf")) { inf = true; infSign = 1; }
            else if (pt.equals("-inf")) { inf = true; infSign = -1; }
            else a = Simplify.run(Parser.parse(pointText)).eval(null);
        } catch (RuntimeException e) {
            return Solution.fail("limit", problem, "err_syntax");
        }
        Solution s = new Solution("limit", problem);
        if (inf) return atInfinity(s, f, x, infSign);
        double av = a;
        double direct = at(f, x, av);
        if (finite(direct) && side == 0) {
            s.step("l_direct", show(av), show(direct));
            s.answers.add(show(direct));
            s.answer("ans_value", show(direct));
            s.verify(numericAgrees(f, x, av, direct), "v_numeric_limit");
            return s;
        }
        if (Double.isNaN(direct) && f instanceof Node.Mul) {
            Node num = null;
            Node den = null;
            List<Node> top = new ArrayList<>();
            List<Node> bot = new ArrayList<>();
            for (Node q : ((Node.Mul) f).f) {
                if (q instanceof Node.Pow && ((Node.Pow) q).e instanceof Node.Num && ((Node.Num) ((Node.Pow) q).e).v < 0) {
                    bot.add(Simplify.run(Node.pow(((Node.Pow) q).b, -((Node.Num) ((Node.Pow) q).e).v)));
                } else top.add(q);
            }
            if (!bot.isEmpty() && !top.isEmpty()) {
                num = top.size() == 1 ? top.get(0) : Node.mul(top);
                den = bot.size() == 1 ? bot.get(0) : Node.mul(bot);
            }
            if (num != null) {
                double nv = at(num, x, av);
                double dv = at(den, x, av);
                s.step("l_indeterminate", show(nv), show(dv));
                Node n1 = num;
                Node d1 = den;
                for (int round = 0; round < 5; round++) {
                    nv = at(n1, x, av);
                    dv = at(d1, x, av);
                    boolean zz = Math.abs(nv) < 1e-12 && Math.abs(dv) < 1e-12;
                    if (!zz) {
                        double r = nv / dv;
                        if (finite(r)) {
                            s.step("l_direct", show(av), show(r));
                            s.answers.add(show(r));
                            s.answer("ans_value", show(r));
                            s.verify(numericAgrees(f, x, av, r), "v_numeric_limit");
                            return s;
                        }
                        break;
                    }
                    Node n2 = Diff.d(n1, x, new ArrayList<Step>());
                    Node d2 = Diff.d(d1, x, new ArrayList<Step>());
                    s.step("l_lhopital", n1.toString(), d1.toString(), n2.toString(), d2.toString());
                    n1 = n2;
                    d1 = d2;
                }
            }
        }
        return oneSided(s, f, x, av, side);
    }

    private static boolean numericAgrees(Node f, String x, double a, double L) {
        double[] hs = {1e-4, 1e-6};
        int good = 0;
        int tot = 0;
        for (double h : hs) {
            for (int sg = -1; sg <= 1; sg += 2) {
                double v = at(f, x, a + sg * h);
                if (!finite(v)) continue;
                tot++;
                if (Math.abs(v - L) <= 1e-2 * (1 + Math.abs(L))) good++;
            }
        }
        return tot > 0 && good == tot;
    }

    private static Solution oneSided(Solution s, Node f, String x, double a, int side) {
        double[] hs = {1e-3, 1e-5, 1e-7};
        double[] left = new double[3];
        double[] right = new double[3];
        for (int i = 0; i < 3; i++) { left[i] = at(f, x, a - hs[i]); right[i] = at(f, x, a + hs[i]); }
        s.step("l_numeric_sides", show(left[2]), show(right[2]));
        String lv = classify(left);
        String rv = classify(right);
        if (lv == null && rv == null) return Solution.fail("limit", s.problem, "err_unsupported");
        if (side < 0) { s.answers.add(lv == null ? "?" : lv); s.answer("ans_value", lv == null ? "?" : lv); s.verify(lv != null, "v_numeric_limit"); return s; }
        if (side > 0) { s.answers.add(rv == null ? "?" : rv); s.answer("ans_value", rv == null ? "?" : rv); s.verify(rv != null, "v_numeric_limit"); return s; }
        if (lv != null && lv.equals(rv)) {
            s.answers.add(lv);
            s.answer("ans_value", lv);
            s.verify(true, "v_numeric_limit");
            return s;
        }
        s.answers.add("left = " + lv + ", right = " + rv);
        s.answer("ans_dne");
        s.verify(true, "v_numeric_limit");
        return s;
    }

    private static String classify(double[] v) {
        if (!finite(v[2]) && !Double.isInfinite(v[2])) return null;
        if (Double.isInfinite(v[2])) return v[2] > 0 ? "\u221e" : "-\u221e";
        if (Math.abs(v[2]) > 1e5 && Math.abs(v[2]) > 10 * Math.abs(v[1]) / 10 && Math.abs(v[2]) > Math.abs(v[1]) * 5) return v[2] > 0 ? "\u221e" : "-\u221e";
        if (Math.abs(v[2] - v[1]) <= 1e-3 * (1 + Math.abs(v[2])) && Math.abs(v[1] - v[0]) <= 1e-1 * (1 + Math.abs(v[1]))) {
            double r = v[2];
            double ri = Math.rint(r);
            if (Math.abs(r - ri) < 1e-4) r = ri;
            return show(round(r));
        }
        return null;
    }

    private static double round(double r) {
        long[] q = Node.ratio(r);
        if (q != null && q[1] <= 100 && Math.abs(q[0] / (double) q[1] - Math.abs(r)) < 1e-5) return (r < 0 ? -1 : 1) * q[0] / (double) q[1];
        return r;
    }

    private static Solution atInfinity(Solution s, Node f, String x, int sign) {
        double[] xs = {1e3, 1e5, 1e7};
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) v[i] = at(f, x, sign * xs[i]);
        double[] pn = null;
        double[] pd = null;
        if (f instanceof Node.Mul) {
            List<Node> top = new ArrayList<>();
            List<Node> bot = new ArrayList<>();
            for (Node q : ((Node.Mul) f).f) {
                if (q instanceof Node.Pow && ((Node.Pow) q).e.isNum(-1)) bot.add(((Node.Pow) q).b); else top.add(q);
            }
            if (bot.size() == 1 && !top.isEmpty()) {
                pn = Solve.poly(top.size() == 1 ? top.get(0) : Node.mul(top), x);
                pd = Solve.poly(bot.get(0), x);
            }
        }
        if (pn != null && pd != null) {
            int dn = pn.length - 1;
            int dd = pd.length - 1;
            s.step("l_inf_rational", Integer.toString(dn), Integer.toString(dd));
        }
        String res = classifyInf(v);
        if (res == null) return Solution.fail("limit", s.problem, "err_unsupported");
        s.answers.add(res);
        s.answer("ans_value", res);
        s.step("l_numeric_inf", show(v[2]));
        s.verify(true, "v_numeric_limit");
        return s;
    }

    private static String classifyInf(double[] v) {
        if (Double.isNaN(v[2])) return null;
        if (Double.isInfinite(v[2])) return v[2] > 0 ? "\u221e" : "-\u221e";
        if (Math.abs(v[2]) > 1e6 || (Math.abs(v[2]) > 1e3 && Math.abs(v[2]) > 20 * Math.abs(v[0]))) return v[2] > 0 ? "\u221e" : "-\u221e";
        if (Math.abs(v[2] - v[1]) <= 1e-3 * (1 + Math.abs(v[2]))) {
            if (Math.abs(v[2] - Math.E) < 1e-5) return "e \u2248 2.718281828";
            double r = round(v[2]);
            if (Math.abs(v[2] - r) < 1e-3) return show(r);
            return Nice.dec(v[2]);
        }
        return null;
    }
}
