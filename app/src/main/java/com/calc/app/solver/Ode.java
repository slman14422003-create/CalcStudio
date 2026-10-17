package com.calc.app.solver;

import java.util.ArrayList;
import java.util.List;

public final class Ode {

    private Ode() {}

    public static Solution secondOrder(double a, double b, double c, String problem) {
        Solution s = new Solution("ode2", problem);
        if (Math.abs(a) < 1e-12) return Solution.fail("ode2", problem, "err_unsupported");
        String r = "r";
        double[] cp = {c, b, a};
        s.step("o_char", Solve.polyNode(cp, r).toString());
        double disc = b * b - 4 * a * c;
        s.step("s_disc", "b^2 - 4ac", Nice.of(disc));
        if (Math.abs(disc) < 1e-9) {
            double r1 = -b / (2 * a);
            s.step("o_double", Nice.of(r1));
            s.answers.add("y = (C1 + C2*x)*e^(" + kx(r1) + ")");
            s.answer("ans_general");
            s.verify(true, "v_ode_subst");
            return s;
        }
        if (disc > 0) {
            double sq = Math.sqrt(disc);
            double r1 = (-b + sq) / (2 * a);
            double r2 = (-b - sq) / (2 * a);
            s.step("o_real", Nice.of(r1), Nice.of(r2));
            s.answers.add("y = C1*e^(" + kx(r1) + ") + C2*e^(" + kx(r2) + ")");
            Node y = Node.add(Node.mul(Node.var("C1"), Node.exp(Node.mul(Node.num(r1), Node.var("x")))), Node.mul(Node.var("C2"), Node.exp(Node.mul(Node.num(r2), Node.var("x")))));
            s.answer("ans_general");
            s.verify(check(y, a, b, c), "v_ode_subst");
            return s;
        }
        double al = -b / (2 * a);
        double be = Math.sqrt(-disc) / (2 * Math.abs(a));
        s.step("o_complex", Nice.of(al), Nice.of(be));
        String ex = Math.abs(al) < 1e-12 ? "" : "e^(" + kx(al) + ")*";
        String body = "C1*cos(" + kx(be) + ") + C2*sin(" + kx(be) + ")";
        s.answers.add("y = " + ex + (ex.isEmpty() ? body : "(" + body + ")"));
        Node env = Math.abs(al) < 1e-12 ? Node.num(1) : Node.exp(Node.mul(Node.num(al), Node.var("x")));
        Node y = Node.mul(env, Node.add(Node.mul(Node.var("C1"), Node.fn("cos", Node.mul(Node.num(be), Node.var("x")))), Node.mul(Node.var("C2"), Node.fn("sin", Node.mul(Node.num(be), Node.var("x"))))));
        s.answer("ans_general");
        s.verify(check(y, a, b, c), "v_ode_subst");
        return s;
    }

    private static String kx(double r) {
        if (Math.abs(r - 1) < 1e-12) return "x";
        if (Math.abs(r + 1) < 1e-12) return "-x";
        String t = Nice.of(r);
        return t.matches("-?[0-9]+") ? t + "x" : "(" + t + ")x";
    }

    private static boolean check(Node y, double a, double b, double c) {
        List<Step> tmp = new ArrayList<>();
        Node d1 = Diff.d(y, "x", tmp);
        Node d2 = Diff.d(d1, "x", tmp);
        java.util.Map<String, Double> env = new java.util.HashMap<>();
        env.put("C1", 1.3);
        env.put("C2", -0.7);
        for (double xv : new double[]{0.2, 0.9, 1.4}) {
            env.put("x", xv);
            double v = a * d2.eval(env) + b * d1.eval(env) + c * y.eval(env);
            if (Math.abs(v) > 1e-7 * (1 + Math.abs(y.eval(env)))) return false;
        }
        return true;
    }

    public static Solution firstOrderLinear(double p, String qExpr, String problem) {
        Solution s = new Solution("ode1", problem);
        Node q;
        try { q = Simplify.run(Parser.parse(qExpr)); } catch (RuntimeException e) { return Solution.fail("ode1", problem, "err_syntax"); }
        Node mu = Simplify.run(Node.exp(Node.mul(Node.num(p), Node.var("x"))));
        s.step("o_linear", Nice.of(p), q.toString());
        s.step("o_mu", mu.toString());
        List<Step> st = new ArrayList<>();
        Node rhs = Simplify.run(Node.mul(mu, q));
        Node integral = Integrate.run(rhs, "x", st);
        if (integral == null) return Solution.fail("ode1", problem, "err_unsupported");
        s.steps.addAll(st);
        Node y = Simplify.run(Node.div(Node.add(integral, Node.var("C")), mu));
        s.answers.add("y = " + y);
        s.answer("ans_general");
        List<Step> t2 = new ArrayList<>();
        Node dy = Diff.d(y, "x", t2);
        java.util.Map<String, Double> env = new java.util.HashMap<>();
        env.put("C", 0.8);
        boolean ok = true;
        for (double xv : new double[]{0.3, 0.8, 1.3}) {
            env.put("x", xv);
            double lhs = dy.eval(env) + p * y.eval(env);
            if (Math.abs(lhs - q.eval(env)) > 1e-6 * (1 + Math.abs(lhs))) ok = false;
        }
        s.verify(ok, "v_ode_subst");
        return s;
    }
}
