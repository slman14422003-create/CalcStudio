package com.calc.app.solver;

import java.util.ArrayList;
import java.util.List;

public final class SolverEngine {

    private SolverEngine() {}

    public static Solution run(Cmd c) {
        try {
            return dispatch(c);
        } catch (Parser.ParseException e) {
            return Solution.fail(c.op, c.raw, "err_syntax");
        } catch (ArithmeticException e) {
            return Solution.fail(c.op, c.raw, "err_math");
        } catch (RuntimeException e) {
            return Solution.fail(c.op, c.raw, "err_unsupported");
        }
    }

    private static int toInt(String s) {
        Node n = Simplify.run(Parser.parse(CalcToolBridge.digits(s)));
        if (!n.isInteger()) throw new ArithmeticException("int");
        return (int) Math.round(n.numVal());
    }

    private static double toDouble(String s) {
        return Simplify.run(Parser.parse(CalcToolBridge.digits(s))).eval(null);
    }

    private static Solution dispatch(Cmd c) {
        switch (c.op) {
            case "DIFF": return diff(c);
            case "INT": return integral(c);
            case "DINT": return Algebra.definite(c.expr, nz(c.var), c.a, c.b);
            case "SOLVE": return Solve.equation(c.expr, c.var);
            case "SYSTEM": return Algebra.system(c.list);
            case "LIMIT": return Limit.run(c.expr, nz(c.var), c.a == null ? "0" : c.a, c.side);
            case "DET": return Algebra.matrix("det", Mat.parse(c.list.get(0)), null, c.list.get(0));
            case "INV": return Algebra.matrix("inverse", Mat.parse(c.list.get(0)), null, c.list.get(0));
            case "TRANS": return Algebra.matrix("transpose", Mat.parse(c.list.get(0)), null, c.list.get(0));
            case "RANK": return Algebra.matrix("rank", Mat.parse(c.list.get(0)), null, c.list.get(0));
            case "EIGEN": return Algebra.matrix("eigen", Mat.parse(c.list.get(0)), null, c.list.get(0));
            case "MMUL": return Algebra.matrix("mul", Mat.parse(c.list.get(0)), Mat.parse(c.list.get(1)), c.raw);
            case "MADD": return Algebra.matrix("add", Mat.parse(c.list.get(0)), Mat.parse(c.list.get(1)), c.raw);
            case "MSUB": return Algebra.matrix("sub", Mat.parse(c.list.get(0)), Mat.parse(c.list.get(1)), c.raw);
            case "STATS": return Stats.describe(Stats.parseList(c.list.get(0)), c.list.get(0));
            case "REG": return Stats.regression(Stats.parseList(c.list.get(0)), Stats.parseList(c.list.get(1)), c.raw);
            case "NCR": return Stats.comb("ncr", toInt(c.nums.get(0)), toInt(c.nums.get(1)), c.raw);
            case "NPR": return Stats.comb("npr", toInt(c.nums.get(0)), toInt(c.nums.get(1)), c.raw);
            case "FACT": return Stats.comb("fact", toInt(c.nums.get(0)), 0, c.raw);
            case "NORMAL": return Stats.normal(toDouble(c.nums.get(0)), toDouble(c.nums.get(1)), toDouble(c.nums.get(2)), c.raw);
            case "BINOM": return Stats.binomial(toInt(c.nums.get(0)), toDouble(c.nums.get(1)), toInt(c.nums.get(2)), c.raw);
            case "ODE2": return Ode.secondOrder(toDouble(c.nums.get(0)), toDouble(c.nums.get(1)), toDouble(c.nums.get(2)), c.raw);
            case "ODE": return ode(c);
            case "SIMPLIFY": return simplify(c, false);
            case "EXPAND": return simplify(c, true);
            case "FACTOR": return Factor.run(c.expr);
            default: return Solution.fail(c.op, c.raw, "err_unsupported");
        }
    }

    private static String nz(String v) { return v == null || v.isEmpty() ? "x" : v; }

    private static Solution diff(Cmd c) {
        String x = nz(c.var);
        Node f = Simplify.run(Parser.parse(c.expr));
        int order = c.a == null ? 1 : Integer.parseInt(c.a);
        Solution s = new Solution("diff", "d/d" + x + " [" + f + "]");
        Node cur = f;
        for (int k = 1; k <= order; k++) {
            if (order > 1) s.step("d_order", Integer.toString(k), cur.toString());
            Node nxt = Diff.d(cur, x, s.steps);
            cur = nxt;
        }
        s.answers.add((order == 1 ? "f'(" + x + ")" : "f^(" + order + ")(" + x + ")") + " = " + cur);
        s.answer("ans_expr");
        Node prev = f;
        boolean ok = true;
        Node walk = f;
        for (int k = 1; k <= order; k++) {
            Node next = Diff.d(walk, x, new ArrayList<Step>());
            if (!Verify.derivativeOf(walk, next, x)) ok = false;
            walk = next;
        }
        s.verify(ok, "v_numeric_derivative");
        return s;
    }

    private static Solution integral(Cmd c) {
        String x = nz(c.var);
        Node f = Simplify.run(Parser.parse(c.expr));
        Solution s = new Solution("int", "\u222b " + f + " d" + x);
        List<Step> st = new ArrayList<>();
        Node F = Integrate.run(f, x, st);
        if (F == null) return Solution.fail("int", s.problem, "err_unsupported_integral");
        s.steps.addAll(st);
        s.answers.add(F + " + C");
        s.answer("ans_expr");
        s.verify(Verify.derivativeOf(F, f, x), "v_derivative_back");
        return s;
    }

    private static Solution simplify(Cmd c, boolean expandOnly) {
        Node n = Simplify.run(Parser.parse(c.expr));
        Solution s = new Solution("simplify", n.toString());
        List<String> vs = new ArrayList<>();
        n.vars(vs);
        if (vs.isEmpty()) {
            double v = n.eval(null);
            s.answers.add(Nice.of(v));
            s.answer("ans_value", Nice.of(v));
            s.verify(true, "v_equal_numeric");
            return s;
        }
        String x = vs.contains("x") ? "x" : vs.get(0);
        Node cancelled = Factor.cancel(n, x, s.steps);
        if (cancelled != null) {
            s.answers.add(cancelled.toString());
            s.answer("ans_expr");
            s.verify(Verify.equalNumeric(n, cancelled, x), "v_equal_numeric");
            return s;
        }
        Node e = Simplify.expand(n);
        if (!n.toString().equals(e.toString())) s.step("f_expand", e.toString());
        s.answers.add(e.toString());
        if (!expandOnly) {
            Solution f = Factor.run(c.expr);
            if (!f.failed && !f.answers.isEmpty() && !f.answers.get(0).equals(e.toString())) {
                s.step("f_factored", f.answers.get(0));
                s.answers.add(f.answers.get(0));
            }
        }
        s.answer("ans_expr");
        s.verify(Verify.equalNumeric(n, e, x), "v_equal_numeric");
        return s;
    }

    private static Solution ode(Cmd c) {
        String[] sd = c.expr.split("=");
        if (sd.length != 2) return Solution.fail("ode", c.expr, "err_syntax");
        Node f = Simplify.expand(Node.sub(Parser.parse(sd[0]), Parser.parse(sd[1])));
        double aP = coef(f, "P");
        double aQ = coef(f, "Q");
        double aY = coef(f, "y");
        Node g = Simplify.expand(Node.sub(f, Node.add(Node.mul(Node.num(aP), Node.var("P")), Node.mul(Node.num(aQ), Node.var("Q")), Node.mul(Node.num(aY), Node.var("y")))));
        String pretty = c.expr.replace("P", "y''").replace("Q", "y'");
        if (aP != 0) {
            if (!g.isNum(0)) return Solution.fail("ode", pretty, "err_unsupported");
            return Ode.secondOrder(aP, aQ, aY, pretty);
        }
        if (aQ != 0) {
            Node q = Simplify.run(Node.div(Node.neg(g), Node.num(aQ)));
            return Ode.firstOrderLinear(aY / aQ, q.toString(), pretty);
        }
        return Solution.fail("ode", pretty, "err_unsupported");
    }

    private static double coef(Node f, String v) {
        Node d = Simplify.run(Diff.d(f, v, new ArrayList<Step>()));
        if (!(d instanceof Node.Num)) throw new IllegalStateException("nonconst");
        return ((Node.Num) d).v;
    }
}
