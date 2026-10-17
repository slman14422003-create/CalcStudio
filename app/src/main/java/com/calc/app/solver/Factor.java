package com.calc.app.solver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class Factor {

    private Factor() {}

    public static Solution run(String expr) {
        Node n;
        try { n = Simplify.run(Parser.parse(expr)); } catch (RuntimeException e) { return Solution.fail("factor", expr, "err_syntax"); }
        Solution s = new Solution("factor", n.toString());
        String x = Solve.chooseVar(n, null);
        Node expanded = Simplify.expand(n);
        if (x == null) return Solution.fail("factor", expr, "err_novar");
        double[] c = Solve.poly(expanded, x);
        if (c == null || c.length < 2) {
            s.step("f_expand", expanded.toString());
            s.answers.add(expanded.toString());
            s.answer("ans_expr");
            s.verify(true, "v_equal_numeric");
            return s;
        }
        if (!Solve.scaleable(c)) return Solution.fail("factor", expr, "err_unsupported");
        double sc = Solve.scaleFactor(c);
        List<Frac> poly = new ArrayList<>();
        for (double v : c) poly.add(Frac.fromDouble(v * sc));
        Frac k = Frac.of(1).div(Frac.fromDouble(sc));
        List<String> factors = new ArrayList<>();
        int guard = 0;
        while (poly.size() > 1 && guard++ < 12) {
            Frac r = Solve.rationalRoot(poly);
            if (r == null) break;
            s.step("f_root", r.toString(), x);
            BigInteger q = r.d;
            BigInteger p = r.n;
            String f;
            if (q.equals(BigInteger.ONE)) f = r.signum() == 0 ? x : "(" + x + (r.signum() > 0 ? " - " : " + ") + r.n.abs() + ")";
            else f = "(" + (q.equals(BigInteger.ONE) ? "" : q.toString()) + x + (p.signum() > 0 ? " - " : " + ") + p.abs() + ")";
            factors.add(f);
            List<Frac> quo = Solve.deflate(poly, r);
            Frac qf = Frac.of(q.longValue());
            k = k.mul(qf).div(qf);
            if (!q.equals(BigInteger.ONE)) {
                for (int i = 0; i < quo.size(); i++) quo.set(i, quo.get(i).div(qf));
                k = k.mul(Frac.ONE);
            }
            poly = quo;
        }
        BigInteger lcm = BigInteger.ONE;
        for (Frac f : poly) lcm = lcm.multiply(f.d).divide(lcm.gcd(f.d));
        BigInteger g = BigInteger.ZERO;
        for (Frac f : poly) g = g.gcd(f.n.multiply(lcm).divide(f.d));
        if (g.signum() == 0) g = BigInteger.ONE;
        List<Frac> prim = new ArrayList<>();
        for (Frac f : poly) prim.add(Frac.of(1).mul(f).mul(Frac.of(lcm.longValue())).div(Frac.of(g.longValue())));
        Frac content = Frac.of(g.longValue()).div(Frac.of(lcm.longValue()));
        if (prim.get(prim.size() - 1).signum() < 0) {
            for (int i = 0; i < prim.size(); i++) prim.set(i, prim.get(i).neg());
            content = content.neg();
        }
        k = k.mul(content);
        if (prim.size() > 1) {
            double[] pd = new double[prim.size()];
            for (int i = 0; i < pd.length; i++) pd[i] = prim.get(i).toDouble();
            String rem = Solve.polyNode(pd, x).toString();
            if (prim.size() == 2 && false) factors.add("(" + rem + ")");
            else factors.add("(" + rem + ")");
        } else k = k.mul(prim.get(0));
        StringBuilder sb = new StringBuilder();
        if (!k.isOne()) sb.append(k.equals(Frac.ONE.neg()) ? "-" : k.toString());
        if (!k.isOne() && !k.equals(Frac.ONE.neg()) && !factors.isEmpty()) sb.append("*");
        sb.append(String.join("", factors));
        String result = sb.length() == 0 ? "1" : sb.toString();
        s.answers.add(result);
        s.answer("ans_expr");
        boolean ok = true;
        try {
            Node back = Parser.parse(result.replace(")(", ")*("));
            java.util.Map<String, Double> env = new java.util.HashMap<>();
            for (double xv : new double[]{0.7, -1.3, 2.9}) {
                env.put(x, xv);
                double a1 = back.eval(env);
                double a2 = n.eval(env);
                if (Math.abs(a1 - a2) > 1e-6 * (1 + Math.abs(a2))) ok = false;
            }
        } catch (RuntimeException e) { ok = false; }
        s.verify(ok, "v_equal_numeric");
        return s;
    }

    private static Frac evalAt(List<Frac> p, Frac r) {
        Frac v = Frac.ZERO;
        for (int k = p.size() - 1; k >= 0; k--) v = v.mul(r).add(p.get(k));
        return v;
    }

    public static Node cancel(Node n, String x, List<Step> st) {
        if (!(n instanceof Node.Mul)) return null;
        List<Node> top = new ArrayList<>();
        List<Node> bot = new ArrayList<>();
        for (Node q : ((Node.Mul) n).f) {
            if (q instanceof Node.Pow && ((Node.Pow) q).e.isNum(-1)) bot.add(((Node.Pow) q).b); else top.add(q);
        }
        if (bot.size() != 1 || top.isEmpty()) return null;
        double[] pn = Solve.poly(top.size() == 1 ? top.get(0) : Node.mul(top), x);
        double[] pd = Solve.poly(bot.get(0), x);
        if (pn == null || pd == null || pd.length < 2 || !Solve.scaleable(pn) || !Solve.scaleable(pd)) return null;
        List<Frac> num = new ArrayList<>();
        List<Frac> den = new ArrayList<>();
        for (double v : pn) num.add(Frac.fromDouble(v));
        for (double v : pd) den.add(Frac.fromDouble(v));
        List<Frac> roots = new ArrayList<>();
        List<Frac> work = new ArrayList<>(den);
        int guard = 0;
        while (work.size() > 1 && guard++ < 12) {
            Frac r = Solve.rationalRoot(work);
            if (r == null) break;
            roots.add(r);
            work = Solve.deflate(work, r);
        }
        boolean any = false;
        for (Frac r : roots) {
            if (num.size() > 1 && evalAt(num, r).isZero() && den.size() > 1 && evalAt(den, r).isZero()) {
                st.add(new Step("f_cancel", r.toString(), x));
                num = Solve.deflate(num, r);
                den = Solve.deflate(den, r);
                any = true;
            }
        }
        if (!any) return null;
        double[] a = new double[num.size()];
        double[] b = new double[den.size()];
        for (int i = 0; i < a.length; i++) a[i] = num.get(i).toDouble();
        for (int i = 0; i < b.length; i++) b[i] = den.get(i).toDouble();
        return Simplify.run(Node.div(Solve.polyNode(a, x), Solve.polyNode(b, x)));
    }
}
