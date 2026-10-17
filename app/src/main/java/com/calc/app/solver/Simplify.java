package com.calc.app.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Simplify {

    private Simplify() {}

    public static Node run(Node n) {
        Node prev = n;
        for (int i = 0; i < 8; i++) {
            Node cur = step(prev);
            if (cur.toString().equals(prev.toString())) return cur;
            prev = cur;
        }
        return prev;
    }

    private static boolean isE(Node n) { return n instanceof Node.Var && ((Node.Var) n).name.equals("e"); }

    private static Node step(Node n) {
        if (n instanceof Node.Num || n instanceof Node.Var) return n;
        if (n instanceof Node.Fn) return fn((Node.Fn) n);
        if (n instanceof Node.Pow) return pow((Node.Pow) n);
        if (n instanceof Node.Add) return add((Node.Add) n);
        return mul((Node.Mul) n);
    }

    private static Node fn(Node.Fn f) {
        Node a = step(f.a);
        if (f.name.equals("ln")) {
            if (a.isNum(1)) return Node.num(0);
            if (isE(a)) return Node.num(1);
            if (a instanceof Node.Pow && isE(((Node.Pow) a).b)) return ((Node.Pow) a).e;
        }
        if (a instanceof Node.Num) {
            double v = ((Node.Num) a).v;
            double r;
            try { r = Node.fn(f.name, a).eval(null); } catch (RuntimeException e) { return Node.fn(f.name, a); }
            if (!Double.isNaN(r) && !Double.isInfinite(r) && Math.abs(r - Math.rint(r)) < 1e-12 && (v == 0 || !f.name.equals("ln"))) return Node.num(Math.rint(r));
        }
        if (a instanceof Node.Var && ((Node.Var) a).name.equals("pi")) {
            switch (f.name) {
                case "sin": case "tan": case "sinh": return Node.fn(f.name, a).eval(null) == 0 ? Node.num(0) : Node.fn(f.name, a);
                case "cos": return Node.num(-1);
                default: break;
            }
        }
        if (f.name.equals("abs") && a instanceof Node.Num) return Node.num(Math.abs(((Node.Num) a).v));
        return Node.fn(f.name, a);
    }

    private static Node pow(Node.Pow p) {
        Node b = step(p.b);
        Node e = step(p.e);
        if (e.isNum(0)) return Node.num(1);
        if (e.isNum(1)) return b;
        if (b.isNum(1)) return Node.num(1);
        if (b.isNum(0) && e instanceof Node.Num && ((Node.Num) e).v > 0) return Node.num(0);
        if (b instanceof Node.Num && e instanceof Node.Num) {
            double r = Math.pow(((Node.Num) b).v, ((Node.Num) e).v);
            if (!Double.isNaN(r) && !Double.isInfinite(r)) {
                boolean exact = Math.abs(r - Math.rint(r)) < 1e-12 || e.isInteger();
                if (exact) return Node.num(r);
            }
        }
        if (isE(b) && e instanceof Node.Fn && ((Node.Fn) e).name.equals("ln")) return ((Node.Fn) e).a;
        if (b instanceof Node.Pow && e.isInteger()) {
            Node.Pow bp = (Node.Pow) b;
            return step(Node.pow(bp.b, Node.mul(bp.e, e)));
        }
        if (b instanceof Node.Pow && bp(b).e.isInteger() ) {
            Node.Pow q = (Node.Pow) b;
            return step(Node.pow(q.b, Node.mul(q.e, e)));
        }
        if (b instanceof Node.Mul && e.isInteger()) {
            List<Node> fs = new ArrayList<>();
            for (Node f : ((Node.Mul) b).f) fs.add(Node.pow(f, e));
            return step(Node.mul(fs));
        }
        return Node.pow(b, e);
    }

    private static Node.Pow bp(Node n) { return (Node.Pow) n; }

    private static void flattenAdd(Node n, List<Node> out) {
        if (n instanceof Node.Add) for (Node t : ((Node.Add) n).t) flattenAdd(t, out);
        else out.add(n);
    }

    private static void flattenMul(Node n, List<Node> out) {
        if (n instanceof Node.Mul) for (Node t : ((Node.Mul) n).f) flattenMul(t, out);
        else out.add(n);
    }

    private static Node add(Node.Add a) {
        List<Node> flat = new ArrayList<>();
        for (Node t : a.t) flattenAdd(step(t), flat);
        double c = 0;
        Map<String, double[]> coefs = new LinkedHashMap<>();
        Map<String, Node> rests = new LinkedHashMap<>();
        for (Node t : flat) {
            if (t instanceof Node.Num) { c += ((Node.Num) t).v; continue; }
            double k = 1;
            Node rest = t;
            if (t instanceof Node.Mul) {
                List<Node> fs = new ArrayList<>();
                for (Node f : ((Node.Mul) t).f) {
                    if (f instanceof Node.Num) k *= ((Node.Num) f).v; else fs.add(f);
                }
                rest = fs.size() == 1 ? fs.get(0) : Node.mul(fs);
            }
            String key = rest.toString();
            double[] cur = coefs.get(key);
            if (cur == null) { coefs.put(key, new double[]{k}); rests.put(key, rest); }
            else cur[0] += k;
        }
        for (String key : new ArrayList<>(rests.keySet())) {
            Node r = rests.get(key);
            if (!(r instanceof Node.Pow) || !coefs.containsKey(key)) continue;
            Node.Pow pw = (Node.Pow) r;
            if (!(pw.b instanceof Node.Fn) || !pw.e.isNum(2) || !((Node.Fn) pw.b).name.equals("sin")) continue;
            String other = Node.pow(Node.fn("cos", ((Node.Fn) pw.b).a), 2).toString();
            if (!coefs.containsKey(other)) continue;
            double k1 = coefs.get(key)[0];
            double k2 = coefs.get(other)[0];
            if (Math.abs(k1 - k2) < 1e-12) {
                c += k1;
                coefs.get(key)[0] = 0;
                coefs.get(other)[0] = 0;
            }
        }
        List<Node> out = new ArrayList<>();
        for (Map.Entry<String, double[]> en : coefs.entrySet()) {
            double k = en.getValue()[0];
            if (Math.abs(k) < 1e-12) continue;
            Node rest = rests.get(en.getKey());
            if (Math.abs(k - 1) < 1e-12) out.add(rest);
            else if (rest instanceof Node.Mul) {
                List<Node> fs = new ArrayList<>();
                fs.add(Node.num(k));
                fs.addAll(((Node.Mul) rest).f);
                out.add(new Node.Mul(fs));
            } else out.add(Node.mul(Node.num(k), rest));
        }
        if (Math.abs(c) > 1e-12 || out.isEmpty()) out.add(Node.num(c));
        if (out.size() == 1) return out.get(0);
        return Node.add(out);
    }

    private static Node mul(Node.Mul m) {
        List<Node> flat = new ArrayList<>();
        for (Node t : m.f) flattenMul(step(t), flat);
        double c = 1;
        Map<String, Node> bases = new LinkedHashMap<>();
        Map<String, List<Node>> exps = new LinkedHashMap<>();
        for (Node t : flat) {
            if (t instanceof Node.Num) { c *= ((Node.Num) t).v; continue; }
            Node b = t;
            Node e = Node.num(1);
            if (t instanceof Node.Pow) { b = ((Node.Pow) t).b; e = ((Node.Pow) t).e; }
            String key = b.toString();
            if (!bases.containsKey(key)) { bases.put(key, b); exps.put(key, new ArrayList<Node>()); }
            exps.get(key).add(e);
        }
        if (c == 0) return Node.num(0);
        List<Node> out = new ArrayList<>();
        for (Map.Entry<String, Node> en : bases.entrySet()) {
            List<Node> es = exps.get(en.getKey());
            Node e = es.size() == 1 ? es.get(0) : step(Node.add(es));
            Node r = step(Node.pow(en.getValue(), e));
            if (r instanceof Node.Num) c *= ((Node.Num) r).v;
            else if (r instanceof Node.Mul) {
                for (Node q : ((Node.Mul) r).f) { if (q instanceof Node.Num) c *= ((Node.Num) q).v; else out.add(q); }
            } else out.add(r);
        }
        if (c == 0) return Node.num(0);
        if (out.isEmpty()) return Node.num(c);
        if (out.size() == 1 && Math.abs(c - 1) < 1e-12) return out.get(0);
        java.util.Collections.sort(out, (x, y) -> {
            int rx = rank(x);
            int ry = rank(y);
            if (rx != ry) return Integer.compare(rx, ry);
            return x.toString().compareTo(y.toString());
        });
        List<Node> res = new ArrayList<>();
        if (Math.abs(c - 1) > 1e-12) res.add(Node.num(c));
        res.addAll(out);
        return res.size() == 1 ? res.get(0) : new Node.Mul(res);
    }

    private static int rank(Node n) {
        Node b = n instanceof Node.Pow ? ((Node.Pow) n).b : n;
        if (b instanceof Node.Var) return 0;
        if (b instanceof Node.Fn) return 1;
        return 2;
    }

    public static Node expand(Node n) {
        Node s = run(n);
        for (int i = 0; i < 6; i++) {
            Node e = run(exp1(s));
            if (e.toString().equals(s.toString())) return e;
            s = e;
        }
        return s;
    }

    private static Node exp1(Node n) {
        if (n instanceof Node.Add) {
            List<Node> ts = new ArrayList<>();
            for (Node t : ((Node.Add) n).t) ts.add(exp1(t));
            return Node.add(ts);
        }
        if (n instanceof Node.Pow) {
            Node.Pow p = (Node.Pow) n;
            Node b = exp1(p.b);
            if (p.e.isInteger() && ((Node.Num) p.e).v >= 2 && ((Node.Num) p.e).v <= 12 && b instanceof Node.Add) {
                int k = (int) ((Node.Num) p.e).v;
                Node r = b;
                for (int i = 1; i < k; i++) r = distribute(r, b);
                return r;
            }
            return Node.pow(b, p.e);
        }
        if (n instanceof Node.Mul) {
            Node r = null;
            for (Node f : ((Node.Mul) n).f) {
                Node x = exp1(f);
                r = r == null ? x : distribute(r, x);
            }
            return r;
        }
        if (n instanceof Node.Fn) return Node.fn(((Node.Fn) n).name, exp1(((Node.Fn) n).a));
        return n;
    }

    private static Node distribute(Node a, Node b) {
        List<Node> as = a instanceof Node.Add ? ((Node.Add) a).t : Node.list(a);
        List<Node> bs = b instanceof Node.Add ? ((Node.Add) b).t : Node.list(b);
        List<Node> out = new ArrayList<>();
        for (Node x : as) for (Node y : bs) out.add(Node.mul(x, y));
        return out.size() == 1 ? out.get(0) : Node.add(out);
    }
}
