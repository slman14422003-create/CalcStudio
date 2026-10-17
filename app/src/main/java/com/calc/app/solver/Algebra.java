package com.calc.app.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Algebra {

    private Algebra() {}

    public static Solution system(List<String> eqs) {
        String problem = String.join(" ; ", eqs);
        List<Node> fs = new ArrayList<>();
        try {
            for (String e : eqs) {
                String[] sd = e.split("=");
                if (sd.length != 2) return Solution.fail("system", problem, "err_syntax");
                fs.add(Simplify.expand(Node.sub(Parser.parse(sd[0]), Parser.parse(sd[1]))));
            }
        } catch (RuntimeException ex) {
            return Solution.fail("system", problem, "err_syntax");
        }
        List<String> vars = new ArrayList<>();
        for (Node f : fs) f.vars(vars);
        java.util.Collections.sort(vars);
        if (vars.isEmpty()) return Solution.fail("system", problem, "err_novar");
        int n = vars.size();
        Frac[][] m = new Frac[fs.size()][n + 1];
        try {
            for (int i = 0; i < fs.size(); i++) {
                Node f = fs.get(i);
                Map<String, Double> zero = new HashMap<>();
                for (String v : vars) zero.put(v, 0.0);
                double k0 = f.eval(zero);
                for (int j = 0; j < n; j++) {
                    Node d = Simplify.run(Diff.d(f, vars.get(j), new ArrayList<Step>()));
                    if (!(d instanceof Node.Num)) return Solution.fail("system", problem, "err_nonlinear");
                    m[i][j] = Frac.fromDouble(((Node.Num) d).v);
                }
                m[i][n] = Frac.fromDouble(-k0);
                Node chk = Simplify.expand(Node.sub(f, Node.num(k0)));
                for (int j = 0; j < n; j++) {
                    Node d = Simplify.run(Diff.d(f, vars.get(j), new ArrayList<Step>()));
                    chk = Simplify.run(Node.sub(chk, Node.mul(d, Node.var(vars.get(j)))));
                }
                if (!chk.isNum(0)) return Solution.fail("system", problem, "err_nonlinear");
            }
        } catch (RuntimeException ex) {
            return Solution.fail("system", problem, "err_nonlinear");
        }
        Solution s = new Solution("system", problem);
        Mat aug = new Mat(m);
        s.step("m_aug", String.join(", ", vars), aug.toString());
        List<Step> st = new ArrayList<>();
        Mat work = aug.copy();
        int rank = work.rref(st, true, n);
        s.steps.addAll(st);
        for (int i = rank; i < work.r; i++) {
            if (!work.a[i][n].isZero()) {
                s.step("m_inconsistent");
                s.answer("ans_no_solution");
                s.verify(true, "v_nosol");
                return s;
            }
        }
        if (rank < n) {
            s.step("m_free");
            List<Integer> pivots = new ArrayList<>();
            for (int i = 0; i < rank; i++) for (int j = 0; j < n; j++) if (!work.a[i][j].isZero()) { pivots.add(j); break; }
            for (int j = 0; j < n; j++) {
                if (pivots.contains(j)) continue;
                s.answers.add(vars.get(j) + " = " + vars.get(j) + " (free)");
            }
            for (int i = 0; i < rank; i++) {
                int pj = pivots.get(i);
                StringBuilder sb = new StringBuilder(vars.get(pj) + " = " + work.a[i][n]);
                for (int j = 0; j < n; j++) {
                    if (j == pj || work.a[i][j].isZero()) continue;
                    Frac c = work.a[i][j].neg();
                    sb.append(c.signum() < 0 ? " - " : " + ").append(c.signum() < 0 ? c.neg() : c).append("*").append(vars.get(j));
                }
                s.answers.add(sb.toString());
            }
            s.answer("ans_infinite");
            s.verify(true, "v_rank");
            return s;
        }
        Map<String, Double> sol = new HashMap<>();
        for (int j = 0; j < n; j++) {
            s.answers.add(vars.get(j) + " = " + work.a[j][n]);
            sol.put(vars.get(j), work.a[j][n].toDouble());
        }
        boolean ok = true;
        for (Node f : fs) if (Math.abs(f.eval(sol)) > 1e-9) ok = false;
        s.answer("ans_solutions");
        s.verify(ok, "v_substitute");
        return s;
    }

    public static Solution matrix(String op, Mat a, Mat b, String problem) {
        Solution s = new Solution("matrix_" + op, problem);
        switch (op) {
            case "det": return det(s, a);
            case "inverse": return inverse(s, a);
            case "transpose": {
                s.step("m_show", "A", a.toString());
                Mat t = a.transpose();
                s.step("m_transpose");
                s.answers.add(t.toString());
                s.answer("ans_matrix");
                s.verify(t.transpose().inline().equals(a.inline()), "v_transpose");
                return s;
            }
            case "mul": {
                if (b == null || a.c != b.r) return Solution.fail("matrix", problem, "err_dims");
                s.step("m_show", "A", a.toString());
                s.step("m_show", "B", b.toString());
                s.step("m_mul_rule", Integer.toString(a.r), Integer.toString(b.c));
                Mat p = a.mul(b);
                s.step("m_mul_entry", "1,1", entryExpr(a, b, 0, 0));
                s.answers.add(p.toString());
                s.answer("ans_matrix");
                s.verify(p.transpose().inline().equals(b.transpose().mul(a.transpose()).inline()), "v_mul_transpose");
                return s;
            }
            case "add": case "sub": {
                if (b == null || a.r != b.r || a.c != b.c) return Solution.fail("matrix", problem, "err_dims");
                s.step("m_show", "A", a.toString());
                s.step("m_show", "B", b.toString());
                Mat r = a.add(b, op.equals("sub"));
                s.step(op.equals("sub") ? "m_sub_rule" : "m_add_rule");
                s.answers.add(r.toString());
                s.answer("ans_matrix");
                s.verify(r.add(b, !op.equals("sub")).inline().equals(a.inline()), "v_undo");
                return s;
            }
            case "rank": {
                s.step("m_show", "A", a.toString());
                Mat w = a.copy();
                List<Step> st = new ArrayList<>();
                int rk = w.rref(st, true, 0);
                s.steps.addAll(st);
                s.answers.add(Integer.toString(rk));
                s.answer("ans_rank", Integer.toString(rk));
                Mat t = a.transpose();
                int rk2 = t.rref(new ArrayList<Step>(), false, 0);
                s.verify(rk == rk2, "v_rank_transpose");
                return s;
            }
            case "eigen": return eigen(s, a);
            default: return Solution.fail("matrix", problem, "err_unsupported");
        }
    }

    private static String entryExpr(Mat a, Mat b, int i, int j) {
        StringBuilder sb = new StringBuilder();
        Frac sum = Frac.ZERO;
        for (int k = 0; k < a.c; k++) {
            if (k > 0) sb.append(" + ");
            sb.append("(").append(a.a[i][k]).append(")(").append(b.a[k][j]).append(")");
            sum = sum.add(a.a[i][k].mul(b.a[k][j]));
        }
        return sb.append(" = ").append(sum).toString();
    }

    private static Solution det(Solution s, Mat a) {
        if (a.r != a.c) return Solution.fail("matrix", s.problem, "err_square");
        s.step("m_show", "A", a.toString());
        Frac d;
        if (a.r == 1) {
            d = a.a[0][0];
        } else if (a.r == 2) {
            s.step("m_det2", a.a[0][0] + "*" + a.a[1][1], a.a[0][1] + "*" + a.a[1][0]);
            d = a.a[0][0].mul(a.a[1][1]).sub(a.a[0][1].mul(a.a[1][0]));
        } else if (a.r == 3) {
            s.step("m_det3");
            d = Frac.ZERO;
            for (int j = 0; j < 3; j++) {
                Frac cof = a.a[0][j];
                Mat mi = a.minor(0, j);
                Frac md = mi.a[0][0].mul(mi.a[1][1]).sub(mi.a[0][1].mul(mi.a[1][0]));
                Frac term = cof.mul(md);
                if (j % 2 == 1) term = term.neg();
                s.step("m_cofactor", (j % 2 == 0 ? "+" : "-") + a.a[0][j], md.toString(), term.toString());
                d = d.add(term);
            }
        } else {
            s.step("m_det_elim");
            d = a.det();
        }
        s.answers.add(d.toString());
        s.answer("ans_det", d.toString());
        boolean ok = d.equals(a.det()) && d.equals(a.transpose().det());
        s.verify(ok, "v_det_transpose");
        return s;
    }

    private static Solution inverse(Solution s, Mat a) {
        if (a.r != a.c) return Solution.fail("matrix", s.problem, "err_square");
        s.step("m_show", "A", a.toString());
        Frac d = a.det();
        s.step("m_det_value", d.toString());
        if (d.isZero()) {
            s.answer("ans_singular");
            s.verify(true, "v_det_zero");
            return s;
        }
        int n = a.r;
        Mat aug = Mat.zero(n, 2 * n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) aug.a[i][j] = a.a[i][j];
            aug.a[i][n + i] = Frac.ONE;
        }
        s.step("m_aug_identity", aug.toString());
        List<Step> st = new ArrayList<>();
        aug.rref(st, true, n);
        s.steps.addAll(st);
        Mat inv = Mat.zero(n, n);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) inv.a[i][j] = aug.a[i][n + j];
        s.answers.add(inv.toString());
        s.answer("ans_matrix");
        s.verify(a.mul(inv).inline().equals(Mat.identity(n).inline()) && inv.mul(a).inline().equals(Mat.identity(n).inline()), "v_inverse_product");
        return s;
    }

    private static Solution eigen(Solution s, Mat a) {
        if (a.r != a.c || a.r < 2 || a.r > 3) return Solution.fail("matrix", s.problem, "err_eigen_size");
        s.step("m_show", "A", a.toString());
        int n = a.r;
        double[] cp;
        Frac tr = Frac.ZERO;
        for (int i = 0; i < n; i++) tr = tr.add(a.a[i][i]);
        Frac det = a.det();
        if (n == 2) cp = new double[]{det.toDouble(), -tr.toDouble(), 1};
        else {
            Frac c2 = Frac.ZERO;
            for (int i = 0; i < 3; i++) {
                Mat mi = a.minor(i, i);
                c2 = c2.add(mi.a[0][0].mul(mi.a[1][1]).sub(mi.a[0][1].mul(mi.a[1][0])));
            }
            cp = new double[]{-det.toDouble(), c2.toDouble(), -tr.toDouble(), 1};
        }
        String lam = "\u03bb";
        s.step("m_charpoly", Solve.polyNode(cp, lam).toString());
        Solution ps = new Solution("poly", "");
        Solve.polySolve(ps, Solve.polyNode(cp, lam), cp, lam);
        s.steps.addAll(ps.steps);
        List<Double> reals = new ArrayList<>();
        for (String ans : ps.answers) {
            s.answers.add(ans);
        }
        for (String ans : ps.answers) {
            String rhs = ans.substring(ans.indexOf('=') + 1).trim();
            if (ans.contains("\u2248") || rhs.contains("i") || rhs.contains("sqrt")) continue;
            try {
                double v = rhs.contains("/") ? Double.parseDouble(rhs.split("/")[0]) / Double.parseDouble(rhs.split("/")[1]) : Double.parseDouble(rhs);
                if (!reals.contains(v)) reals.add(v);
            } catch (NumberFormatException ignored) {
            }
        }
        for (double v : reals) {
            Frac fv;
            try { fv = Frac.fromDouble(v); } catch (ArithmeticException e) { continue; }
            Mat sh = a.copy();
            for (int i = 0; i < n; i++) sh.a[i][i] = sh.a[i][i].sub(fv);
            Mat w = sh.copy();
            int rk = w.rref(new ArrayList<Step>(), false, 0);
            List<Integer> piv = new ArrayList<>();
            for (int i = 0; i < rk; i++) for (int j = 0; j < n; j++) if (!w.a[i][j].isZero()) { piv.add(j); break; }
            for (int free = 0; free < n; free++) {
                if (piv.contains(free)) continue;
                Frac[] vec = new Frac[n];
                for (int j = 0; j < n; j++) vec[j] = Frac.ZERO;
                vec[free] = Frac.ONE;
                for (int i = 0; i < rk; i++) vec[piv.get(i)] = w.a[i][free].neg();
                StringBuilder sb = new StringBuilder("v(" + fv + ") = [");
                for (int j = 0; j < n; j++) { sb.append(vec[j]); if (j < n - 1) sb.append(", "); }
                s.answers.add(sb.append("]").toString());
                boolean ok = true;
                for (int i = 0; i < n; i++) {
                    Frac lhs = Frac.ZERO;
                    for (int j = 0; j < n; j++) lhs = lhs.add(a.a[i][j].mul(vec[j]));
                    if (!lhs.equals(fv.mul(vec[i]))) ok = false;
                }
                if (!ok) { s.verify(false, "v_eigen"); }
            }
        }
        s.answer("ans_eigen");
        if (s.verifyKey == null) s.verify(ps.verified, "v_eigen");
        return s;
    }

    public static Solution definite(String expr, String x, String lo, String hi) {
        String problem = expr + " [" + lo + ", " + hi + "]";
        Node f;
        double a;
        double b;
        try {
            f = Simplify.run(Parser.parse(expr));
            a = Simplify.run(Parser.parse(lo)).eval(null);
            b = Simplify.run(Parser.parse(hi)).eval(null);
        } catch (RuntimeException e) {
            return Solution.fail("definite", problem, "err_syntax");
        }
        Solution s = new Solution("definite", "\u222b[" + lo + "," + hi + "] " + f + " d" + x);
        List<Step> st = new ArrayList<>();
        Node F = Integrate.run(f, x, st);
        double val;
        if (F != null) {
            s.steps.addAll(st);
            s.step("i_definite", F.toString(), hi, lo);
            Map<String, Double> eb = new HashMap<>();
            Map<String, Double> ea = new HashMap<>();
            eb.put(x, b);
            ea.put(x, a);
            val = F.eval(eb) - F.eval(ea);
            double num = simpson(f, x, a, b);
            boolean ok = !Double.isNaN(num) && Math.abs(num - val) <= 1e-6 * (1 + Math.abs(val));
            s.answers.add(Nice.of(val));
            s.answer("ans_value", Nice.of(val));
            s.verify(ok, "v_numeric_integral");
            return s;
        }
        s.step("i_numeric");
        val = simpson(f, x, a, b);
        if (Double.isNaN(val)) return Solution.fail("definite", problem, "err_unsupported");
        s.answers.add(Nice.dec(val));
        s.answer("ans_value", Nice.dec(val));
        s.noteKey = "n_numeric";
        s.verify(true, "v_numeric_integral");
        return s;
    }

    static double simpson(Node f, String x, double a, double b) {
        int n = 2000;
        double h = (b - a) / n;
        Map<String, Double> env = new HashMap<>();
        double sum = 0;
        try {
            for (int i = 0; i <= n; i++) {
                env.put(x, a + i * h);
                double v = f.eval(env);
                if (Double.isNaN(v) || Double.isInfinite(v)) return Double.NaN;
                sum += v * (i == 0 || i == n ? 1 : (i % 2 == 1 ? 4 : 2));
            }
        } catch (RuntimeException e) {
            return Double.NaN;
        }
        return sum * h / 3;
    }
}
