package com.calc.app.solver;

import java.util.ArrayList;
import java.util.List;

public final class Diff {

    private Diff() {}

    private static boolean negPow(Node f) {
        return f instanceof Node.Pow && ((Node.Pow) f).e instanceof Node.Num && ((Node.Num) ((Node.Pow) f).e).v < 0;
    }

    public static Node d(Node n, String x, List<Step> st) {
        Node r = Simplify.run(raw(n, x, st));
        return r;
    }

    private static Node raw(Node n, String x, List<Step> st) {
        if (!n.has(x)) return Node.num(0);
        if (n instanceof Node.Var) return Node.num(1);
        if (n instanceof Node.Add) {
            List<Node> ts = ((Node.Add) n).t;
            if (ts.size() > 1) st.add(new Step("d_sum", n.toString()));
            List<Node> out = new ArrayList<>();
            for (Node t : ts) out.add(d(t, x, st));
            return Node.add(out);
        }
        if (n instanceof Node.Mul) return mul((Node.Mul) n, x, st);
        if (n instanceof Node.Pow) return pow((Node.Pow) n, x, st);
        return fn((Node.Fn) n, x, st);
    }

    private static Node mul(Node.Mul m, String x, List<Step> st) {
        List<Node> cons = new ArrayList<>();
        List<Node> vars = new ArrayList<>();
        for (Node f : m.f) { if (f.has(x)) vars.add(f); else cons.add(f); }
        if (!cons.isEmpty()) {
            Node c = cons.size() == 1 ? cons.get(0) : Node.mul(cons);
            Node rest = vars.size() == 1 ? vars.get(0) : Node.mul(vars);
            boolean trivial = c.isNum(1);
            if (!trivial) st.add(new Step("d_const", Simplify.run(c).toString(), rest.toString()));
            Node dr = d(rest, x, st);
            return Node.mul(c, dr);
        }
        if (vars.size() == 1) return d(vars.get(0), x, st);
        List<Node> top = new ArrayList<>();
        List<Node> bot = new ArrayList<>();
        for (Node f : vars) {
            if (negPow(f)) {
                Node.Pow p = (Node.Pow) f;
                bot.add(Simplify.run(Node.pow(p.b, -((Node.Num) p.e).v)));
            } else top.add(f);
        }
        if (!bot.isEmpty() && !top.isEmpty()) {
            Node u = top.size() == 1 ? top.get(0) : Node.mul(top);
            Node v = bot.size() == 1 ? bot.get(0) : Node.mul(bot);
            st.add(new Step("d_quotient", u.toString(), v.toString()));
            Node du = d(u, x, st);
            st.add(new Step("d_part", "u", u.toString(), du.toString()));
            Node dv = d(v, x, st);
            st.add(new Step("d_part", "v", v.toString(), dv.toString()));
            return Node.div(Node.sub(Node.mul(du, v), Node.mul(u, dv)), Node.pow(v, 2));
        }
        st.add(new Step("d_product", m.toString()));
        List<Node> terms = new ArrayList<>();
        for (int i = 0; i < vars.size(); i++) {
            List<Node> fs = new ArrayList<>();
            for (int j = 0; j < vars.size(); j++) fs.add(j == i ? d(vars.get(j), x, st) : vars.get(j));
            terms.add(Node.mul(fs));
        }
        return Node.add(terms);
    }

    private static Node pow(Node.Pow p, String x, List<Step> st) {
        boolean bx = p.b.has(x);
        boolean ex = p.e.has(x);
        if (bx && !ex) {
            Node nm1 = Simplify.run(Node.sub(p.e, Node.num(1)));
            if (p.b instanceof Node.Var) {
                st.add(new Step("d_power", p.toString()));
                return Node.mul(p.e, Node.pow(p.b, nm1));
            }
            st.add(new Step("d_chain_power", p.toString(), p.b.toString()));
            Node db = d(p.b, x, st);
            st.add(new Step("d_inner", p.b.toString(), db.toString()));
            return Node.mul(p.e, Node.pow(p.b, nm1), db);
        }
        if (!bx && ex) {
            boolean isE = p.b instanceof Node.Var && ((Node.Var) p.b).name.equals("e");
            if (isE) st.add(new Step("d_exp", p.toString()));
            else st.add(new Step("d_exp_a", p.toString()));
            Node de = d(p.e, x, st);
            if (!(p.e instanceof Node.Var)) st.add(new Step("d_inner", p.e.toString(), de.toString()));
            if (isE) return Node.mul(p, de);
            return Node.mul(p, Node.fn("ln", p.b), de);
        }
        st.add(new Step("d_logdiff", p.toString()));
        Node de = d(p.e, x, st);
        Node db = d(p.b, x, st);
        return Node.mul(p, Node.add(Node.mul(de, Node.fn("ln", p.b)), Node.div(Node.mul(p.e, db), p.b)));
    }

    private static Node outer(String name, Node u) {
        switch (name) {
            case "sin": return Node.fn("cos", u);
            case "cos": return Node.neg(Node.fn("sin", u));
            case "tan": return Node.pow(Node.fn("cos", u), -2);
            case "cot": return Node.neg(Node.pow(Node.fn("sin", u), -2));
            case "sec": return Node.mul(Node.fn("sec", u), Node.fn("tan", u));
            case "csc": return Node.neg(Node.mul(Node.fn("csc", u), Node.fn("cot", u)));
            case "asin": return Node.pow(Node.sub(Node.num(1), Node.pow(u, 2)), -0.5);
            case "acos": return Node.neg(Node.pow(Node.sub(Node.num(1), Node.pow(u, 2)), -0.5));
            case "atan": return Node.pow(Node.add(Node.num(1), Node.pow(u, 2)), -1);
            case "sinh": return Node.fn("cosh", u);
            case "cosh": return Node.fn("sinh", u);
            case "tanh": return Node.pow(Node.fn("cosh", u), -2);
            case "ln": return Node.pow(u, -1);
            case "abs": return Node.div(u, Node.fn("abs", u));
            default: throw new IllegalStateException(name);
        }
    }

    private static Node fn(Node.Fn f, String x, List<Step> st) {
        Node u = f.a;
        Node o = outer(f.name, u);
        Node generic = Simplify.run(outer(f.name, Node.var("u")));
        st.add(new Step("d_fn", f.name + "(u)", generic.toString()));
        if (u instanceof Node.Var) return o;
        Node du = d(u, x, st);
        st.add(new Step("d_inner", u.toString(), du.toString()));
        return Node.mul(o, du);
    }
}
