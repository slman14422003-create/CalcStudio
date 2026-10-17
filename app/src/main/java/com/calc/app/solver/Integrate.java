package com.calc.app.solver;

import java.util.ArrayList;
import java.util.List;

public final class Integrate {

    private Integrate() {}

    private static final String U = "u_";

    public static Node run(Node n, String x, List<Step> st) {
        return run(n, x, st, 0);
    }

    private static boolean isE(Node n) { return n instanceof Node.Var && ((Node.Var) n).name.equals("e"); }

    private static double[] linear(Node u, String x) {
        Node s = Simplify.expand(u);
        if (!s.has(x)) return null;
        List<Node> ts = s instanceof Node.Add ? ((Node.Add) s).t : Node.list(s);
        double a = 0;
        double b = 0;
        for (Node t : ts) {
            if (!t.has(x)) {
                if (!(t instanceof Node.Num)) return null;
                b += ((Node.Num) t).v;
            } else if (t instanceof Node.Var && ((Node.Var) t).name.equals(x)) a += 1;
            else if (t instanceof Node.Mul && ((Node.Mul) t).f.size() == 2 && ((Node.Mul) t).f.get(0) instanceof Node.Num
                    && ((Node.Mul) t).f.get(1) instanceof Node.Var && ((Node.Var) ((Node.Mul) t).f.get(1)).name.equals(x)) a += ((Node.Num) ((Node.Mul) t).f.get(0)).v;
            else return null;
        }
        if (Math.abs(a) < 1e-12) return null;
        return new double[]{a, b};
    }

    private static Node divA(Node n, double a) {
        return Math.abs(a - 1) < 1e-12 ? n : Node.div(n, Node.num(a));
    }

    private static Node run(Node n, String x, List<Step> st, int depth) {
        if (depth > 12) return null;
        n = Simplify.run(n);
        if (!n.has(x)) {
            st.add(new Step("i_const", n.toString()));
            return Simplify.run(Node.mul(n, Node.var(x)));
        }
        if (n instanceof Node.Add) {
            List<Node> ts = ((Node.Add) n).t;
            st.add(new Step("i_sum", n.toString()));
            List<Node> out = new ArrayList<>();
            for (Node t : ts) {
                Node r = run(t, x, st, depth + 1);
                if (r == null) return null;
                out.add(r);
            }
            return Simplify.run(Node.add(out));
        }
        if (n instanceof Node.Mul) {
            List<Node> cons = new ArrayList<>();
            List<Node> vars = new ArrayList<>();
            for (Node f : ((Node.Mul) n).f) { if (f.has(x)) vars.add(f); else cons.add(f); }
            if (!cons.isEmpty() && !vars.isEmpty()) {
                Node c = cons.size() == 1 ? cons.get(0) : Node.mul(cons);
                Node rest = vars.size() == 1 ? vars.get(0) : Node.mul(vars);
                if (!c.isNum(1)) {
                    st.add(new Step("i_const_mult", Simplify.run(c).toString(), rest.toString()));
                    Node r = run(rest, x, st, depth + 1);
                    return r == null ? null : Simplify.run(Node.mul(c, r));
                }
            }
        }
        Node direct = direct(n, x, st);
        if (direct != null) return Simplify.run(direct);
        Node trig = trigRewrite(n, x, st, depth);
        if (trig != null) return trig;
        Node us = usub(n, x, st, depth);
        if (us != null) return us;
        Node ex = expandTry(n, x, st, depth);
        if (ex != null) return ex;
        Node parts = parts(n, x, st, depth);
        if (parts != null) return parts;
        return rational(n, x, st, depth);
    }

    private static Node expandTry(Node n, String x, List<Step> st, int depth) {
        Node e = Simplify.expand(n);
        if (e.toString().equals(n.toString()) || !(e instanceof Node.Add)) return null;
        if (!polyLike(e, x)) return null;
        st.add(new Step("i_expand", n.toString(), e.toString()));
        return run(e, x, st, depth + 1);
    }

    private static boolean polyLike(Node e, String x) {
        for (Node t : ((Node.Add) e).t) {
            Node b = t;
            if (b instanceof Node.Mul) {
                for (Node f : ((Node.Mul) b).f) {
                    if (f.has(x) && !(f instanceof Node.Var) && !(f instanceof Node.Pow && ((Node.Pow) f).b instanceof Node.Var)) return false;
                }
            } else if (b.has(x) && !(b instanceof Node.Var) && !(b instanceof Node.Pow && ((Node.Pow) b).b instanceof Node.Var)) return false;
        }
        return true;
    }

    private static Node direct(Node n, String x, List<Step> st) {
        Node xv = Node.var(x);
        if (n instanceof Node.Var) {
            st.add(new Step("i_power", n.toString()));
            return Node.div(Node.pow(n, 2), Node.num(2));
        }
        if (n instanceof Node.Pow) {
            Node.Pow p = (Node.Pow) n;
            if (!p.e.has(x) && p.e instanceof Node.Num) {
                double k = ((Node.Num) p.e).v;
                double[] lin = linear(p.b, x);
                if (lin != null) {
                    boolean plain = p.b instanceof Node.Var;
                    if (Math.abs(k + 1) < 1e-12) {
                        st.add(new Step(plain ? "i_log" : "i_linear", n.toString(), "ln|" + p.b + "|"));
                        return divA(Node.fn("ln", Node.fn("abs", p.b)), lin[0]);
                    }
                    st.add(new Step(plain ? "i_power" : "i_linear", n.toString(), Simplify.run(Node.div(Node.pow(p.b, k + 1), Node.num(k + 1))).toString()));
                    return divA(Node.div(Node.pow(p.b, k + 1), Node.num(k + 1)), lin[0]);
                }
            }
            if (isE(p.b) && !p.e.has("\u0000")) {
                double[] lin = linear(p.e, x);
                if (lin != null) {
                    st.add(new Step("i_exp", n.toString()));
                    return divA(n, lin[0]);
                }
            }
            if (!p.b.has(x) && p.e.has(x)) {
                double[] lin = linear(p.e, x);
                if (lin != null) {
                    st.add(new Step("i_exp_a", n.toString()));
                    return divA(Node.div(n, Node.fn("ln", p.b)), lin[0]);
                }
            }
            if (p.b instanceof Node.Fn && p.e.isNum(-2)) {
                Node.Fn f = (Node.Fn) p.b;
                double[] lin = linear(f.a, x);
                if (lin != null && f.name.equals("cos")) { st.add(new Step("i_sec2", n.toString())); return divA(Node.fn("tan", f.a), lin[0]); }
                if (lin != null && f.name.equals("sin")) { st.add(new Step("i_sec2", n.toString())); return divA(Node.neg(Node.fn("cot", f.a)), lin[0]); }
            }
            Node atan = atanForm(p, x, st);
            if (atan != null) return atan;
            return null;
        }
        if (n instanceof Node.Fn) {
            Node.Fn f = (Node.Fn) n;
            double[] lin = linear(f.a, x);
            if (lin == null) return null;
            Node r;
            switch (f.name) {
                case "sin": r = Node.neg(Node.fn("cos", f.a)); break;
                case "cos": r = Node.fn("sin", f.a); break;
                case "sinh": r = Node.fn("cosh", f.a); break;
                case "cosh": r = Node.fn("sinh", f.a); break;
                case "tan": r = Node.neg(Node.fn("ln", Node.fn("abs", Node.fn("cos", f.a)))); break;
                case "cot": r = Node.fn("ln", Node.fn("abs", Node.fn("sin", f.a))); break;
                case "ln": r = Node.sub(Node.mul(f.a, Node.fn("ln", f.a)), f.a); break;
                case "atan": r = Node.sub(Node.mul(f.a, Node.fn("atan", f.a)), Node.mul(Node.num(0.5), Node.fn("ln", Node.add(Node.num(1), Node.pow(f.a, 2))))); break;
                default: return null;
            }
            st.add(new Step(lin[0] == 1 && lin[1] == 0 ? "i_basic" : "i_linear", n.toString(), Simplify.run(r).toString()));
            return divA(r, lin[0]);
        }
        if (n instanceof Node.Mul) {
            Node.Mul m = (Node.Mul) n;
            if (m.f.size() == 2) {
                Node a = m.f.get(0);
                Node b = m.f.get(1);
                Node r = expSinCos(a, b, x, st);
                if (r == null) r = expSinCos(b, a, x, st);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static Node expSinCos(Node ex, Node tr, String x, List<Step> st) {
        if (!(ex instanceof Node.Pow) || !isE(((Node.Pow) ex).b) || !(tr instanceof Node.Fn)) return null;
        Node.Fn f = (Node.Fn) tr;
        if (!f.name.equals("sin") && !f.name.equals("cos")) return null;
        double[] le = linear(((Node.Pow) ex).e, x);
        double[] lt = linear(f.a, x);
        if (le == null || lt == null) return null;
        if (Math.abs(le[0]) < 1e-12) return null;
        double a = le[0];
        double b = lt[0];
        double den = a * a + b * b;
        st.add(new Step("i_exp_trig", Node.mul(ex, tr).toString()));
        Node r;
        if (f.name.equals("sin")) r = Node.mul(ex, Node.sub(Node.mul(Node.num(a), tr), Node.mul(Node.num(b), Node.fn("cos", f.a))));
        else r = Node.mul(ex, Node.add(Node.mul(Node.num(a), tr), Node.mul(Node.num(b), Node.fn("sin", f.a))));
        return Node.div(r, Node.num(den));
    }

    private static Node atanForm(Node.Pow p, String x, List<Step> st) {
        if (!p.e.isNum(-1)) return null;
        Node b = Simplify.expand(p.b);
        double[] q = quad(b, x);
        if (q == null) return null;
        double a = q[0];
        double bb = q[1];
        double c = q[2];
        if (Math.abs(a) < 1e-12) return null;
        double disc = bb * bb - 4 * a * c;
        if (disc >= -1e-12) return null;
        double k = Math.sqrt(-disc) / (2 * a);
        double sx = -bb / (2 * a);
        st.add(new Step("i_atan", p.toString()));
        Node arg = Node.div(Node.sub(Node.var(x), Node.num(sx)), Node.num(Math.abs(k)));
        double factor = 1 / (a * Math.abs(k));
        return Node.mul(Node.num(factor), Node.fn("atan", Simplify.run(arg)));
    }

    static double[] quad(Node e, String x) {
        List<Node> ts = e instanceof Node.Add ? ((Node.Add) e).t : Node.list(e);
        double[] c = new double[3];
        for (Node t : ts) {
            double k = 1;
            int deg = 0;
            if (t instanceof Node.Num) k = ((Node.Num) t).v;
            else {
                List<Node> fs = t instanceof Node.Mul ? ((Node.Mul) t).f : Node.list(t);
                for (Node f : fs) {
                    if (f instanceof Node.Num) k *= ((Node.Num) f).v;
                    else if (f instanceof Node.Var && ((Node.Var) f).name.equals(x)) deg += 1;
                    else if (f instanceof Node.Pow && ((Node.Pow) f).b instanceof Node.Var && ((Node.Var) ((Node.Pow) f).b).name.equals(x) && ((Node.Pow) f).e.isInteger()) deg += (int) ((Node.Num) ((Node.Pow) f).e).v;
                    else return null;
                }
            }
            if (deg < 0 || deg > 2) return null;
            c[deg] += k;
        }
        return new double[]{c[2], c[1], c[0]};
    }

    private static Node trigRewrite(Node n, String x, List<Step> st, int depth) {
        if (!(n instanceof Node.Pow)) return null;
        Node.Pow p = (Node.Pow) n;
        if (!(p.b instanceof Node.Fn) || !p.e.isInteger()) return null;
        Node.Fn f = (Node.Fn) p.b;
        int k = (int) ((Node.Num) p.e).v;
        if (k < 2 || k > 8) return null;
        if (!f.name.equals("sin") && !f.name.equals("cos") && !f.name.equals("tan")) return null;
        if (linear(f.a, x) == null) return null;
        Node rew;
        if (f.name.equals("tan")) {
            Node sec2 = Node.add(Node.num(1), Node.pow(Node.fn("tan", f.a), 2));
            rew = Node.mul(Node.pow(Node.fn("tan", f.a), k - 2), Node.sub(sec2, Node.num(1)));
            rew = Simplify.expand(rew);
            st.add(new Step("i_trig_identity", n.toString()));
            return rationalTan(rew, f, k, x, st, depth);
        }
        if (k % 2 == 1) {
            Node other = f.name.equals("sin") ? Node.fn("cos", f.a) : Node.fn("sin", f.a);
            Node base = Node.sub(Node.num(1), Node.pow(other, 2));
            Node r = Node.mul(Node.pow(base, (k - 1) / 2.0), f);
            rew = Simplify.expand(r);
            st.add(new Step("i_trig_odd", n.toString(), rew.toString()));
            return run(rew, x, st, depth + 1);
        }
        Node twice = Node.fn("cos", Node.mul(Node.num(2), f.a));
        Node half = f.name.equals("sin") ? Node.div(Node.sub(Node.num(1), twice), Node.num(2)) : Node.div(Node.add(Node.num(1), twice), Node.num(2));
        Node r = k == 2 ? half : Node.pow(half, k / 2.0);
        rew = Simplify.expand(r);
        st.add(new Step("i_trig_even", n.toString(), rew.toString()));
        return run(rew, x, st, depth + 1);
    }

    private static Node rationalTan(Node rew, Node.Fn f, int k, String x, List<Step> st, int depth) {
        if (k == 2) {
            double[] lin = linear(f.a, x);
            Node r = Node.sub(Node.fn("tan", f.a), Node.mul(Node.num(lin[0]), Node.var(x)));
            return Simplify.run(divA(r, lin[0]));
        }
        return null;
    }

    private static Node subst(Node n, Node g, Node u) {
        if (n.toString().equals(g.toString())) return u;
        if (n instanceof Node.Add) { List<Node> o = new ArrayList<>(); for (Node t : ((Node.Add) n).t) o.add(subst(t, g, u)); return Node.add(o); }
        if (n instanceof Node.Mul) { List<Node> o = new ArrayList<>(); for (Node t : ((Node.Mul) n).f) o.add(subst(t, g, u)); return Node.mul(o); }
        if (n instanceof Node.Pow) return Node.pow(subst(((Node.Pow) n).b, g, u), subst(((Node.Pow) n).e, g, u));
        if (n instanceof Node.Fn) return Node.fn(((Node.Fn) n).name, subst(((Node.Fn) n).a, g, u));
        return n;
    }

    private static void candidates(Node n, String x, List<Node> out) {
        if (n instanceof Node.Fn) { Node a = ((Node.Fn) n).a; if (a.has(x) && !(a instanceof Node.Var)) add(out, a); candidates(a, x, out); }
        else if (n instanceof Node.Pow) {
            Node.Pow p = (Node.Pow) n;
            if (p.b.has(x) && !(p.b instanceof Node.Var)) add(out, p.b);
            if (p.e.has(x) && !(p.e instanceof Node.Var)) add(out, p.e);
            candidates(p.b, x, out);
            candidates(p.e, x, out);
        } else if (n instanceof Node.Add) { for (Node t : ((Node.Add) n).t) candidates(t, x, out); }
        else if (n instanceof Node.Mul) { for (Node t : ((Node.Mul) n).f) candidates(t, x, out); }
        if (n instanceof Node.Fn && ((Node.Fn) n).a instanceof Node.Var) add(out, n);
    }

    private static void add(List<Node> out, Node n) {
        for (Node o : out) if (o.toString().equals(n.toString())) return;
        out.add(n);
    }

    private static Node usub(Node n, String x, List<Step> st, int depth) {
        List<Node> cands = new ArrayList<>();
        candidates(n, x, cands);
        for (Node g : cands) {
            if (linear(g, x) != null) continue;
            List<Step> tmp = new ArrayList<>();
            Node dg = Diff.d(g, x, tmp);
            if (dg.isNum(0)) continue;
            Node q = Simplify.run(Node.div(n, dg));
            Node qu = Simplify.run(subst(q, g, Node.var(U)));
            if (qu.has(x)) continue;
            List<Step> inner = new ArrayList<>();
            Node F = run(qu, U, inner, depth + 1);
            if (F == null) continue;
            st.add(new Step("i_usub", g.toString(), dg.toString(), qu.toString()));
            st.addAll(inner);
            Node back = Simplify.run(subst(F, Node.var(U), g));
            st.add(new Step("i_back", g.toString(), back.toString()));
            return back;
        }
        return null;
    }

    private static int liate(Node f, String x) {
        if (f instanceof Node.Fn) {
            String nm = ((Node.Fn) f).name;
            if (nm.equals("ln") || nm.equals("asin") || nm.equals("acos") || nm.equals("atan")) return 0;
            return 3;
        }
        if (f instanceof Node.Var || (f instanceof Node.Pow && ((Node.Pow) f).b instanceof Node.Var && ((Node.Pow) f).e.isInteger())) return 1;
        if (f instanceof Node.Pow && isE(((Node.Pow) f).b)) return 4;
        return 2;
    }

    private static Node parts(Node n, String x, List<Step> st, int depth) {
        if (depth > 6 || !(n instanceof Node.Mul)) return null;
        Node.Mul m = (Node.Mul) n;
        List<Node> fs = new ArrayList<>();
        double coef = 1;
        for (Node f : m.f) { if (f instanceof Node.Num) coef *= ((Node.Num) f).v; else fs.add(f); }
        if (fs.isEmpty()) return null;
        int best = -1;
        int bestRank = 99;
        for (int i = 0; i < fs.size(); i++) {
            int r = liate(fs.get(i), x);
            if (r < bestRank) { bestRank = r; best = i; }
        }
        Node u = fs.get(best);
        List<Node> rest = new ArrayList<>();
        for (int i = 0; i < fs.size(); i++) if (i != best) rest.add(fs.get(i));
        if (rest.isEmpty()) {
            if (u instanceof Node.Fn && ((Node.Fn) u).name.equals("ln") ) return null;
            rest.add(Node.num(1));
        }
        Node dv = rest.size() == 1 ? rest.get(0) : Node.mul(rest);
        if (liate(u, x) == 1 && (liate(dv, x) == 1)) return null;
        List<Step> t1 = new ArrayList<>();
        Node v = run(dv, x, t1, depth + 2);
        if (v == null) return null;
        List<Step> t2 = new ArrayList<>();
        Node du = Diff.d(u, x, t2);
        st.add(new Step("i_parts", u.toString(), dv.toString(), du.toString(), v.toString()));
        Node vdu = Simplify.run(Node.mul(v, du));
        List<Step> t3 = new ArrayList<>();
        Node r = run(vdu, x, t3, depth + 1);
        if (r == null) return null;
        st.addAll(t3);
        Node res = Simplify.run(Node.sub(Node.mul(u, v), r));
        if (coef != 1) res = Simplify.run(Node.mul(Node.num(coef), res));
        return res;
    }

    private static Node rational(Node n, String x, List<Step> st, int depth) {
        Node top = null;
        Node bot = null;
        if (n instanceof Node.Pow && ((Node.Pow) n).e.isNum(-1)) { top = Node.num(1); bot = ((Node.Pow) n).b; }
        else if (n instanceof Node.Mul) {
            List<Node> num = new ArrayList<>();
            List<Node> den = new ArrayList<>();
            for (Node f : ((Node.Mul) n).f) {
                if (f instanceof Node.Pow && ((Node.Pow) f).e.isNum(-1)) den.add(((Node.Pow) f).b); else num.add(f);
            }
            if (den.size() != 1) return null;
            top = num.isEmpty() ? Node.num(1) : (num.size() == 1 ? num.get(0) : Node.mul(num));
            bot = den.get(0);
        }
        if (top == null) return null;
        double[] qd = quad(Simplify.expand(bot), x);
        double[] ln = quad(Simplify.expand(top), x);
        if (qd == null || ln == null || ln[0] != 0 || Math.abs(qd[0]) < 1e-12) return null;
        double a = qd[0];
        double b = qd[1];
        double c = qd[2];
        double p = ln[1];
        double q = ln[2];
        double disc = b * b - 4 * a * c;
        Node xv = Node.var(x);
        Node bn = Simplify.expand(bot);
        if (disc < -1e-12) {
            double lc = p / (2 * a);
            double rest = q - p * b / (2 * a);
            st.add(new Step("i_rational_quad", n.toString()));
            List<Node> parts = new ArrayList<>();
            if (Math.abs(lc) > 1e-12) parts.add(Node.mul(Node.num(lc), Node.fn("ln", bn)));
            if (Math.abs(rest) > 1e-12) {
                double k = Math.sqrt(-disc) / (2 * a);
                Node arg = Simplify.run(Node.div(Node.sub(xv, Node.num(-b / (2 * a))), Node.num(Math.abs(k))));
                parts.add(Node.mul(Node.num(rest / (a * Math.abs(k))), Node.fn("atan", arg)));
            }
            return Simplify.run(Node.add(parts));
        }
        double sq = Math.sqrt(Math.max(0, disc));
        double r1 = (-b + sq) / (2 * a);
        double r2 = (-b - sq) / (2 * a);
        if (Math.abs(r1 - r2) < 1e-9) {
            double A = p / a;
            double B = (q + p * r1) / a;
            st.add(new Step("i_rational_double", n.toString(), Node.fmtNum(r1)));
            Node t1 = Node.mul(Node.num(A), Node.fn("ln", Node.fn("abs", Node.sub(xv, Node.num(r1)))));
            Node t2 = Node.neg(Node.div(Node.num(B), Node.sub(xv, Node.num(r1))));
            return Simplify.run(Node.add(t1, t2));
        }
        double A = (p * r1 + q) / (a * (r1 - r2));
        double B = (p * r2 + q) / (a * (r2 - r1));
        st.add(new Step("i_rational_partial", n.toString(), Node.ratStr(r1), Node.ratStr(r2), Node.ratStr(A), Node.ratStr(B)));
        Node t1 = Node.mul(Node.num(A), Node.fn("ln", Node.fn("abs", Node.sub(xv, Node.num(r1)))));
        Node t2 = Node.mul(Node.num(B), Node.fn("ln", Node.fn("abs", Node.sub(xv, Node.num(r2)))));
        return Simplify.run(Node.add(t1, t2));
    }
}
