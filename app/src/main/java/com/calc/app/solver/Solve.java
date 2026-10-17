package com.calc.app.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Solve {

    private Solve() {}

    public static double[] poly(Node e, String x) {
        Node s = Simplify.expand(e);
        List<Node> ts = s instanceof Node.Add ? ((Node.Add) s).t : Node.list(s);
        double[] c = new double[14];
        int max = 0;
        for (Node t : ts) {
            double k = 1;
            int deg = 0;
            List<Node> fs = t instanceof Node.Mul ? ((Node.Mul) t).f : Node.list(t);
            for (Node f : fs) {
                if (f instanceof Node.Num) k *= ((Node.Num) f).v;
                else if (f instanceof Node.Var && ((Node.Var) f).name.equals(x)) deg += 1;
                else if (f instanceof Node.Pow && ((Node.Pow) f).b instanceof Node.Var && ((Node.Var) ((Node.Pow) f).b).name.equals(x)
                        && ((Node.Pow) f).e.isInteger() && ((Node.Num) ((Node.Pow) f).e).v >= 0) deg += (int) ((Node.Num) ((Node.Pow) f).e).v;
                else return null;
            }
            if (deg > 12) return null;
            c[deg] += k;
            max = Math.max(max, deg);
        }
        double[] out = new double[max + 1];
        System.arraycopy(c, 0, out, 0, max + 1);
        while (out.length > 1 && Math.abs(out[out.length - 1]) < 1e-12) {
            double[] t = new double[out.length - 1];
            System.arraycopy(out, 0, t, 0, t.length);
            out = t;
        }
        return out;
    }

    public static Node polyNode(double[] c, String x) {
        List<Node> ts = new ArrayList<>();
        for (int k = c.length - 1; k >= 0; k--) {
            if (Math.abs(c[k]) < 1e-12) continue;
            ts.add(Node.mul(Node.num(c[k]), Node.pow(Node.var(x), k)));
        }
        if (ts.isEmpty()) return Node.num(0);
        return Simplify.run(Node.add(ts));
    }

    public static String chooseVar(Node f, String hint) {
        List<String> vs = new ArrayList<>();
        f.vars(vs);
        if (hint != null && vs.contains(hint)) return hint;
        if (vs.contains("x")) return "x";
        if (vs.isEmpty()) return null;
        return vs.get(0);
    }

    private static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return Node.fmtNum(Math.rint(v));
        long[] r = Node.ratio(v);
        if (r != null && r[1] <= 100) return (v < 0 ? "-" : "") + r[0] + "/" + r[1];
        return Node.fmtNum(v);
    }

    private static String dec(double v) {
        return new java.math.BigDecimal(v).round(new java.math.MathContext(8)).stripTrailingZeros().toPlainString();
    }

    private static String exactOrDec(Node n) {
        String s = n.toString();
        double v;
        try { v = n.eval(null); } catch (RuntimeException e) { return s; }
        if (n.isNum()) return num(v);
        if (Math.abs(v - Math.rint(v)) < 1e-9) return Node.fmtNum(Math.rint(v));
        if (Math.abs(v) > 1e-9) {
            long[] r = Node.ratio(v / Math.PI);
            if (r != null && r[1] <= 12) {
                String sg = v < 0 ? "-" : "";
                String top = r[0] == 1 ? "pi" : r[0] + "pi";
                return sg + top + "/" + r[1] + " \u2248 " + dec(v);
            }
        }
        return s + " \u2248 " + dec(v);
    }

    private static boolean intLike(double[] c) {
        for (double v : c) if (Math.abs(v - Math.rint(v)) > 1e-9) return false;
        return true;
    }

    public static Solution equation(String text, String hint) {
        String[] sides = text.split("=");
        if (sides.length > 2) return Solution.fail("equation", text, "err_syntax");
        Node lhs;
        Node rhs;
        try {
            lhs = Parser.parse(sides[0]);
            rhs = sides.length == 2 ? Parser.parse(sides[1]) : Node.num(0);
        } catch (RuntimeException e) {
            return Solution.fail("equation", text, "err_syntax");
        }
        Node f = Simplify.run(Node.sub(lhs, rhs));
        String x = chooseVar(f, hint);
        Solution s = new Solution("equation", lhs + " = " + rhs);
        if (x == null) return Solution.fail("equation", text, "err_novar");
        boolean sameEq = sides.length == 2;
        double[] c = poly(f, x);
        if (c != null && c.length > 1) {
            if (rhs.isNum(0) == false || !lhs.toString().equals(Simplify.expand(lhs).toString())) {
                s.step("s_standard", Simplify.expand(f).toString());
            }
            return polySolve(s, f, c, x);
        }
        if (c != null) {
            if (Math.abs(c[0]) < 1e-12) { s.answer("ans_identity"); s.verify(true, "v_identity"); }
            else { s.answer("ans_no_solution"); s.verify(true, "v_nosol"); }
            return s;
        }
        return general(s, lhs, rhs, f, x);
    }

    static Solution polySolve(Solution s, Node f, double[] c, String x) {
        int deg = c.length - 1;
        List<String> ans = new ArrayList<>();
        List<double[]> roots = new ArrayList<>();
        if (deg == 1) {
            double a = c[1];
            double b = c[0];
            s.step("s_linear", polyNode(c, x).toString(), num(a), num(b));
            double r = -b / a;
            s.step("s_linear_result", x, num(-b), num(a), num(r));
            roots.add(new double[]{r, 0});
            ans.add(x + " = " + num(r));
        } else {
            double[] cur = c;
            double[] rem = c.clone();
            if (deg >= 3) {
                s.step("s_degree", Integer.toString(deg), polyNode(c, x).toString());
                List<Frac> work = null;
                if (intLike(c) || scaleable(c)) {
                    work = new ArrayList<>();
                    double sc = scaleFactor(c);
                    for (double v : c) work.add(Frac.fromDouble(v * sc));
                    int guard = 0;
                    while (work.size() - 1 >= 3 && guard++ < 10) {
                        Frac root = rationalRoot(work);
                        if (root == null) break;
                        s.step("s_rational_root", root.toString(), x);
                        roots.add(new double[]{root.toDouble(), 0});
                        ans.add(x + " = " + root);
                        work = deflate(work, root);
                        double[] wd = new double[work.size()];
                        for (int i = 0; i < wd.length; i++) wd[i] = work.get(i).toDouble();
                        s.step("s_deflate", polyNode(wd, x).toString());
                    }
                    rem = new double[work.size()];
                    for (int i = 0; i < rem.length; i++) rem[i] = work.get(i).toDouble();
                }
                if (rem.length - 1 >= 3) {
                    s.step("s_numeric_roots");
                    for (double[] r : durandKerner(rem)) {
                        addRoot(roots, ans, r, x);
                    }
                    rem = new double[]{1};
                }
            }
            if (rem.length == 3) {
                double a = rem[2];
                double b = rem[1];
                double cc = rem[0];
                s.step("s_quad_formula", num(a), num(b), num(cc));
                double disc = b * b - 4 * a * cc;
                s.step("s_disc", "b^2 - 4ac", num(disc));
                if (Math.abs(disc) < 1e-9) {
                    double r = -b / (2 * a);
                    s.step("s_disc_zero");
                    roots.add(new double[]{r, 0});
                    ans.add(x + " = " + num(r));
                } else if (disc > 0) {
                    s.step("s_disc_pos");
                    double sq = Math.sqrt(disc);
                    boolean exact = Math.abs(sq - Math.rint(sq)) < 1e-9 && intLike(rem);
                    for (int sign = 1; sign >= -1; sign -= 2) {
                        double r = (-b + sign * sq) / (2 * a);
                        roots.add(new double[]{r, 0});
                        if (exact || !intLike(rem)) ans.add(x + " = " + num(r));
                        else {
                            Node ex = quadExact(a, b, (long) Math.rint(disc), sign);
                            ans.add(x + " = " + ex + " \u2248 " + dec(r));
                        }
                    }
                } else {
                    s.step("s_disc_neg");
                    double re = -b / (2 * a);
                    double im = Math.sqrt(-disc) / (2 * Math.abs(a));
                    roots.add(new double[]{re, im});
                    roots.add(new double[]{re, -im});
                    ans.add(x + " = " + cplx(re, im));
                    ans.add(x + " = " + cplx(re, -im));
                }
            } else if (rem.length == 2) {
                double r = -rem[0] / rem[1];
                roots.add(new double[]{r, 0});
                ans.add(x + " = " + num(r));
            }
        }
        Integer[] order = new Integer[roots.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final List<double[]> rr = roots;
        java.util.Arrays.sort(order, (p, q) -> {
            int cmp = Double.compare(rr.get(p)[0], rr.get(q)[0]);
            return cmp != 0 ? cmp : Double.compare(rr.get(q)[1], rr.get(p)[1]);
        });
        if (order.length == ans.size()) {
            List<String> sorted = new ArrayList<>();
            for (int i : order) sorted.add(ans.get(i));
            ans = sorted;
        }
        s.answers.addAll(ans);
        s.answer(ans.isEmpty() ? "ans_no_solution" : "ans_solutions");
        boolean ok = true;
        for (double[] r : roots) if (!checkPoly(c, r)) ok = false;
        s.verify(ok && !roots.isEmpty(), "v_substitute");
        return s;
    }

    private static Node quadExact(double a, double b, long disc, int sign) {
        long k = 1;
        long m = disc;
        for (long i = 2; i * i <= m; i++) {
            while (m % (i * i) == 0) { m /= i * i; k *= i; }
        }
        long bb = Math.round(b);
        long den = Math.round(2 * a);
        long g = java.math.BigInteger.valueOf(Math.abs(bb)).gcd(java.math.BigInteger.valueOf(k)).gcd(java.math.BigInteger.valueOf(Math.abs(den))).longValue();
        if (g == 0) g = 1;
        Node rad = Node.mul(Node.num((double) sign * k / g), Node.pow(Node.num(m), 0.5));
        Node top = bb == 0 ? rad : Node.add(Node.num((double) -bb / g), rad);
        return Simplify.run(Node.div(top, Node.num((double) den / g)));
    }

    private static String cplx(double re, double im) {
        double ai = Math.abs(im);
        String imS = Math.abs(ai - 1) < 1e-9 ? "i" : num(ai) + "i";
        if (Math.abs(re) < 1e-9) return (im < 0 ? "-" : "") + imS;
        return num(re) + (im < 0 ? " - " : " + ") + imS;
    }

    private static void addRoot(List<double[]> roots, List<String> ans, double[] r, String x) {
        for (double[] q : roots) if (Math.abs(q[0] - r[0]) < 1e-7 && Math.abs(q[1] - r[1]) < 1e-7) return;
        roots.add(r);
        if (Math.abs(r[1]) < 1e-9) ans.add(x + " \u2248 " + dec(r[0]));
        else ans.add(x + " \u2248 " + dec(r[0]) + (r[1] < 0 ? " - " : " + ") + dec(Math.abs(r[1])) + "i");
    }

    static boolean scaleable(double[] c) { return scaleFactor(c) > 0 && scaleFactor(c) < 1e6; }

    static double scaleFactor(double[] c) {
        double sc = 1;
        for (double v : c) {
            if (Math.abs(v - Math.rint(v)) < 1e-9) continue;
            long[] r = Node.ratio(v);
            if (r == null) return -1;
            sc = lcm(sc, r[1]);
        }
        return sc;
    }

    private static double lcm(double a, double b) {
        long x = (long) a;
        long y = (long) b;
        long g = java.math.BigInteger.valueOf(x).gcd(java.math.BigInteger.valueOf(y)).longValue();
        return (double) (x / g * y);
    }

    private static boolean checkPoly(double[] c, double[] r) {
        double re = 0;
        double im = 0;
        double scale = 0;
        for (int k = c.length - 1; k >= 0; k--) {
            double nre = re * r[0] - im * r[1] + c[k];
            double nim = re * r[1] + im * r[0];
            re = nre;
            im = nim;
            scale += Math.abs(c[k]) * Math.pow(Math.hypot(r[0], r[1]) + 1, k);
        }
        return Math.hypot(re, im) <= 1e-6 * (1 + scale);
    }

    static Frac rationalRoot(List<Frac> p) {
        int n = p.size() - 1;
        java.math.BigInteger a0 = p.get(0).n.abs();
        java.math.BigInteger an = p.get(n).n.abs();
        if (p.get(0).isZero()) return Frac.ZERO;
        List<Long> ps = divisors(a0);
        List<Long> qs = divisors(an);
        if (ps == null || qs == null) return null;
        for (long q : qs) for (long pv : ps) for (int sg = 1; sg >= -1; sg -= 2) {
            Frac cand = Frac.of(sg * pv, q);
            Frac val = Frac.ZERO;
            for (int k = n; k >= 0; k--) val = val.mul(cand).add(p.get(k));
            if (val.isZero()) return cand;
        }
        return null;
    }

    private static List<Long> divisors(java.math.BigInteger v) {
        if (v.bitLength() > 40) return null;
        long n = v.longValue();
        List<Long> out = new ArrayList<>();
        for (long i = 1; i * i <= n; i++) if (n % i == 0) { out.add(i); if (i != n / i) out.add(n / i); }
        java.util.Collections.sort(out);
        return out;
    }

    static List<Frac> deflate(List<Frac> p, Frac root) {
        int n = p.size() - 1;
        Frac[] q = new Frac[n];
        Frac carry = Frac.ZERO;
        for (int k = n; k >= 1; k--) {
            carry = carry.mul(root).add(p.get(k));
            q[k - 1] = carry;
        }
        List<Frac> out = new ArrayList<>();
        for (Frac f : q) out.add(f);
        return out;
    }

    public static List<double[]> durandKerner(double[] c) {
        int n = c.length - 1;
        double[] re = new double[n];
        double[] im = new double[n];
        double lead = c[n];
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n + 0.4;
            re[i] = 0.9 * Math.cos(ang) * (1 + Math.abs(c[0] / lead));
            im[i] = 0.9 * Math.sin(ang) * (1 + Math.abs(c[0] / lead));
        }
        for (int it = 0; it < 500; it++) {
            double delta = 0;
            for (int i = 0; i < n; i++) {
                double pr = 0;
                double pi = 0;
                for (int k = n; k >= 0; k--) {
                    double nr = pr * re[i] - pi * im[i] + c[k] / lead;
                    double ni = pr * im[i] + pi * re[i];
                    pr = nr;
                    pi = ni;
                }
                double dr = 1;
                double di = 0;
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    double ar = re[i] - re[j];
                    double ai = im[i] - im[j];
                    double nr = dr * ar - di * ai;
                    double ni = dr * ai + di * ar;
                    dr = nr;
                    di = ni;
                }
                double den = dr * dr + di * di;
                if (den == 0) continue;
                double qr = (pr * dr + pi * di) / den;
                double qi = (pi * dr - pr * di) / den;
                re[i] -= qr;
                im[i] -= qi;
                delta = Math.max(delta, Math.hypot(qr, qi));
            }
            if (delta < 1e-13) break;
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new double[]{re[i], Math.abs(im[i]) < 1e-9 ? 0 : im[i]});
        return out;
    }

    private static Solution general(Solution s, Node lhs, Node rhs, Node f, String x) {
        Node left;
        Node right;
        if (lhs.has(x) && !rhs.has(x)) { left = lhs; right = rhs; }
        else if (rhs.has(x) && !lhs.has(x)) { left = rhs; right = lhs; }
        else { left = Simplify.run(f); right = Node.num(0); }
        List<Step> tmp = new ArrayList<>();
        List<Node> sols = isolate(Simplify.run(left), Simplify.run(right), x, tmp);
        boolean periodic = false;
        if (sols != null && !sols.isEmpty()) {
            s.steps.addAll(tmp);
            for (Step st : tmp) if (st.key.equals("s_inv_trig")) periodic = true;
            List<Node> good = new ArrayList<>();
            List<String> others = new ArrayList<>();
            for (Node sol : sols) {
                Node ss = Simplify.run(sol);
                if (ss.toString().contains("NaN")) continue;
                if (checkSolution(f, x, ss)) good.add(ss);
            }
            for (Node g : good) {
                boolean dup = false;
                for (String o : others) if (o.startsWith(x + " = " + g.toString() + " ") || o.equals(x + " = " + g)) dup = true;
                if (!dup) s.answers.add(x + " = " + exactOrDec(g));
            }
            if (good.isEmpty()) { s.answer("ans_no_solution"); s.verify(true, "v_nosol"); return s; }
            s.answer("ans_solutions");
            s.verify(true, "v_substitute");
            if (periodic) s.noteKey = "n_periodic";
            return s;
        }
        s.step("s_numeric_scan");
        List<Double> roots = numericRoots(f, x);
        if (roots.isEmpty()) { s.answer("ans_no_solution"); s.verify(false, "v_numeric"); s.noteKey = "n_numeric"; return s; }
        for (double r : roots) s.answers.add(x + " \u2248 " + dec(r));
        s.answer("ans_solutions");
        s.verify(true, "v_substitute");
        s.noteKey = "n_numeric";
        return s;
    }

    private static boolean checkSolution(Node f, String x, Node sol) {
        List<String> vs = new ArrayList<>();
        f.vars(vs);
        Map<String, Double> env = new HashMap<>();
        Random rd = new Random(7);
        for (String v : vs) if (!v.equals(x)) env.put(v, 1.5 + rd.nextDouble());
        try {
            double xv = sol.eval(env);
            if (Double.isNaN(xv) || Double.isInfinite(xv)) return false;
            env.put(x, xv);
            double fv = f.eval(env);
            if (Double.isNaN(fv)) return false;
            return Math.abs(fv) <= 1e-7 * (1 + Math.abs(xv));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<Node> isolate(Node e, Node rhs, String x, List<Step> st) {
        e = Simplify.run(e);
        rhs = Simplify.run(rhs);
        if (e instanceof Node.Var && ((Node.Var) e).name.equals(x)) return Node.list(rhs).isEmpty() ? null : new ArrayList<>(java.util.Arrays.asList(rhs));
        if (e instanceof Node.Add) {
            List<Node> with = new ArrayList<>();
            List<Node> without = new ArrayList<>();
            for (Node t : ((Node.Add) e).t) { if (t.has(x)) with.add(t); else without.add(t); }
            if (with.size() != 1) return null;
            Node moved = without.isEmpty() ? Node.num(0) : Node.add(without);
            Node nr = Simplify.run(Node.sub(rhs, moved));
            st.add(new Step("s_isolate", with.get(0).toString(), nr.toString()));
            return isolate(with.get(0), nr, x, st);
        }
        if (e instanceof Node.Mul) {
            List<Node> with = new ArrayList<>();
            List<Node> without = new ArrayList<>();
            for (Node t : ((Node.Mul) e).f) { if (t.has(x)) with.add(t); else without.add(t); }
            if (with.size() != 1) return null;
            Node k = without.size() == 1 ? without.get(0) : Node.mul(without);
            Node nr = Simplify.run(Node.div(rhs, k));
            st.add(new Step("s_isolate", with.get(0).toString(), nr.toString()));
            return isolate(with.get(0), nr, x, st);
        }
        if (e instanceof Node.Pow) {
            Node.Pow p = (Node.Pow) e;
            if (p.b.has(x) && !p.e.has(x)) {
                Node inv = Simplify.run(Node.div(Node.num(1), p.e));
                double rv;
                try { rv = rhs.eval(null); } catch (RuntimeException ex) { rv = Double.NaN; }
                boolean even = p.e.isInteger() && ((int) Math.round(((Node.Num) p.e).v)) % 2 == 0;
                if (even) {
                    if (!Double.isNaN(rv) && rv < 0) return new ArrayList<>();
                    Node root = Simplify.run(Node.pow(rhs, inv));
                    st.add(new Step("s_root_even", p.b.toString(), root.toString()));
                    List<Node> out = new ArrayList<>();
                    List<Node> a = isolate(p.b, root, x, st);
                    List<Node> b = isolate(p.b, Simplify.run(Node.neg(root)), x, new ArrayList<Step>());
                    if (a != null) out.addAll(a);
                    if (b != null) out.addAll(b);
                    return out;
                }
                Node root;
                if (!Double.isNaN(rv) && rv < 0 && p.e.isInteger()) root = Simplify.run(Node.neg(Node.pow(Node.neg(rhs), inv)));
                else root = Simplify.run(Node.pow(rhs, inv));
                st.add(new Step("s_root", p.b.toString(), root.toString()));
                return isolate(p.b, root, x, st);
            }
            if (!p.b.has(x) && p.e.has(x)) {
                Node nr;
                boolean isE = p.b instanceof Node.Var && ((Node.Var) p.b).name.equals("e");
                nr = isE ? Node.fn("ln", rhs) : Node.div(Node.fn("ln", rhs), Node.fn("ln", p.b));
                nr = Simplify.run(nr);
                st.add(new Step("s_log", p.e.toString(), nr.toString()));
                return isolate(p.e, nr, x, st);
            }
            return null;
        }
        if (e instanceof Node.Fn) {
            Node.Fn fn = (Node.Fn) e;
            Node a = fn.a;
            List<Node> targets = new ArrayList<>();
            switch (fn.name) {
                case "sin": {
                    Node as = Node.fn("asin", rhs);
                    targets.add(as);
                    targets.add(Node.sub(Node.var("pi"), as));
                    st.add(new Step("s_inv_trig", fn.toString(), as.toString()));
                    break;
                }
                case "cos": {
                    Node ac = Node.fn("acos", rhs);
                    targets.add(ac);
                    targets.add(Node.neg(ac));
                    st.add(new Step("s_inv_trig", fn.toString(), ac.toString()));
                    break;
                }
                case "tan": {
                    Node at = Node.fn("atan", rhs);
                    targets.add(at);
                    st.add(new Step("s_inv_trig", fn.toString(), at.toString()));
                    break;
                }
                case "ln": {
                    Node ex = Node.exp(rhs);
                    targets.add(ex);
                    st.add(new Step("s_exp", fn.toString(), Simplify.run(ex).toString()));
                    break;
                }
                case "abs": {
                    targets.add(rhs);
                    targets.add(Node.neg(rhs));
                    st.add(new Step("s_abs", rhs.toString()));
                    break;
                }
                default: return null;
            }
            List<Node> out = new ArrayList<>();
            boolean first = true;
            for (Node t : targets) {
                List<Node> r = isolate(a, Simplify.run(t), x, first ? st : new ArrayList<Step>());
                first = false;
                if (r != null) out.addAll(r);
            }
            return out;
        }
        return null;
    }

    public static List<Double> numericRoots(Node f, String x) {
        List<String> vs = new ArrayList<>();
        f.vars(vs);
        Map<String, Double> env = new HashMap<>();
        for (String v : vs) if (!v.equals(x)) env.put(v, 1.0);
        List<Double> roots = new ArrayList<>();
        double prevX = -100;
        double prevV = value(f, x, env, prevX);
        for (double xv = -99.9; xv <= 100; xv += 0.05) {
            double v = value(f, x, env, xv);
            if (!Double.isNaN(v) && !Double.isNaN(prevV) && prevV * v <= 0 && Math.abs(v - prevV) < 1e6) {
                double lo = prevX;
                double hi = xv;
                double flo = prevV;
                for (int i = 0; i < 80; i++) {
                    double mid = (lo + hi) / 2;
                    double fm = value(f, x, env, mid);
                    if (Double.isNaN(fm)) break;
                    if (flo * fm <= 0) hi = mid; else { lo = mid; flo = fm; }
                }
                double r = (lo + hi) / 2;
                double fr = value(f, x, env, r);
                if (!Double.isNaN(fr) && Math.abs(fr) < 1e-6) {
                    boolean dup = false;
                    for (double q : roots) if (Math.abs(q - r) < 1e-6) dup = true;
                    if (!dup) roots.add(r);
                }
            }
            prevX = xv;
            prevV = v;
            if (roots.size() >= 12) break;
        }
        return roots;
    }

    private static double value(Node f, String x, Map<String, Double> env, double xv) {
        env.put(x, xv);
        try { return f.eval(env); } catch (RuntimeException e) { return Double.NaN; }
    }
}
