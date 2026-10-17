package com.calc.app.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class Node {

    public static final class Num extends Node {
        public final double v;
        Num(double v) { this.v = v; }
    }

    public static final class Var extends Node {
        public final String name;
        Var(String name) { this.name = name; }
    }

    public static final class Add extends Node {
        public final List<Node> t;
        Add(List<Node> t) { this.t = t; }
    }

    public static final class Mul extends Node {
        public final List<Node> f;
        Mul(List<Node> f) { this.f = f; }
    }

    public static final class Pow extends Node {
        public final Node b;
        public final Node e;
        Pow(Node b, Node e) { this.b = b; this.e = e; }
    }

    public static final class Fn extends Node {
        public final String name;
        public final Node a;
        Fn(String name, Node a) { this.name = name; this.a = a; }
    }

    private String cache;

    public static Node num(double v) { return new Num(v); }
    public static Node var(String n) { return new Var(n); }
    public static Node add(Node... t) { return new Add(new ArrayList<>(Arrays.asList(t))); }
    public static Node add(List<Node> t) { return new Add(new ArrayList<>(t)); }
    public static Node mul(Node... f) { return new Mul(new ArrayList<>(Arrays.asList(f))); }
    public static Node mul(List<Node> f) { return new Mul(new ArrayList<>(f)); }
    public static Node pow(Node b, Node e) { return new Pow(b, e); }
    public static Node pow(Node b, double e) { return new Pow(b, num(e)); }
    public static Node fn(String n, Node a) { return new Fn(n, a); }
    public static Node neg(Node a) { return mul(num(-1), a); }
    public static Node sub(Node a, Node b) { return add(a, neg(b)); }
    public static Node div(Node a, Node b) { return mul(a, pow(b, -1)); }
    public static Node exp(Node a) { return pow(var("e"), a); }

    public boolean isNum() { return this instanceof Num; }
    public boolean isNum(double v) { return this instanceof Num && Math.abs(((Num) this).v - v) < 1e-12; }
    public double numVal() { return ((Num) this).v; }

    public boolean isInteger() {
        if (!(this instanceof Num)) return false;
        double v = ((Num) this).v;
        return Math.abs(v - Math.rint(v)) < 1e-12 && Math.abs(v) < 1e15;
    }

    public boolean has(String variable) {
        if (this instanceof Var) return ((Var) this).name.equals(variable);
        if (this instanceof Add) { for (Node n : ((Add) this).t) if (n.has(variable)) return true; return false; }
        if (this instanceof Mul) { for (Node n : ((Mul) this).f) if (n.has(variable)) return true; return false; }
        if (this instanceof Pow) return ((Pow) this).b.has(variable) || ((Pow) this).e.has(variable);
        if (this instanceof Fn) return ((Fn) this).a.has(variable);
        return false;
    }

    public void vars(List<String> out) {
        if (this instanceof Var) {
            String n = ((Var) this).name;
            if (!n.equals("e") && !n.equals("pi") && !out.contains(n)) out.add(n);
        } else if (this instanceof Add) { for (Node n : ((Add) this).t) n.vars(out); }
        else if (this instanceof Mul) { for (Node n : ((Mul) this).f) n.vars(out); }
        else if (this instanceof Pow) { ((Pow) this).b.vars(out); ((Pow) this).e.vars(out); }
        else if (this instanceof Fn) { ((Fn) this).a.vars(out); }
    }

    public double eval(Map<String, Double> env) {
        if (this instanceof Num) return ((Num) this).v;
        if (this instanceof Var) {
            String n = ((Var) this).name;
            Double d = env == null ? null : env.get(n);
            if (d != null) return d;
            if (n.equals("e")) return Math.E;
            if (n.equals("pi")) return Math.PI;
            throw new IllegalStateException("unbound " + n);
        }
        if (this instanceof Add) { double s = 0; for (Node n : ((Add) this).t) s += n.eval(env); return s; }
        if (this instanceof Mul) { double s = 1; for (Node n : ((Mul) this).f) s *= n.eval(env); return s; }
        if (this instanceof Pow) return Math.pow(((Pow) this).b.eval(env), ((Pow) this).e.eval(env));
        Fn f = (Fn) this;
        double x = f.a.eval(env);
        switch (f.name) {
            case "sin": return Math.sin(x);
            case "cos": return Math.cos(x);
            case "tan": return Math.tan(x);
            case "cot": return 1 / Math.tan(x);
            case "sec": return 1 / Math.cos(x);
            case "csc": return 1 / Math.sin(x);
            case "asin": return Math.asin(x);
            case "acos": return Math.acos(x);
            case "atan": return Math.atan(x);
            case "sinh": return Math.sinh(x);
            case "cosh": return Math.cosh(x);
            case "tanh": return Math.tanh(x);
            case "ln": return Math.log(x);
            case "abs": return Math.abs(x);
            default: throw new IllegalStateException("fn " + f.name);
        }
    }

    public static String fmtNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-12 && Math.abs(v) < 1e15) return Long.toString((long) Math.rint(v));
        String s = new java.math.BigDecimal(v).round(new java.math.MathContext(10)).stripTrailingZeros().toPlainString();
        return s;
    }

    private static boolean negLead(Node n) {
        if (n instanceof Num) return ((Num) n).v < 0;
        if (n instanceof Mul) {
            Node f = ((Mul) n).f.get(0);
            return f instanceof Num && ((Num) f).v < 0;
        }
        return false;
    }

    private static Node negated(Node n) {
        if (n instanceof Num) return num(-((Num) n).v);
        Mul m = (Mul) n;
        List<Node> fs = new ArrayList<>(m.f);
        double c = -((Num) fs.get(0)).v;
        if (Math.abs(c - 1) < 1e-12) fs.remove(0);
        else fs.set(0, num(c));
        if (fs.isEmpty()) return num(1);
        if (fs.size() == 1) return fs.get(0);
        return new Mul(fs);
    }

    private static String paren(Node n, boolean always) {
        String s = n.toString();
        if (always) return "(" + s + ")";
        return s;
    }

    private static boolean atom(Node n) {
        if (n instanceof Num) { double v = ((Num) n).v; return v >= 0 && Math.abs(v - Math.rint(v)) < 1e-12; }
        return n instanceof Var || n instanceof Fn || (n instanceof Pow && ((Pow) n).e.isNum(0.5));
    }

    @Override
    public String toString() {
        if (cache != null) return cache;
        cache = render();
        return cache;
    }

    private String render() {
        if (this instanceof Num) return fmtNum(((Num) this).v);
        if (this instanceof Var) return ((Var) this).name;
        if (this instanceof Fn) {
            Fn fz = (Fn) this;
            if (fz.name.equals("abs")) return "|" + fz.a + "|";
            return fz.name + "(" + fz.a + ")";
        }
        if (this instanceof Add) {
            StringBuilder sb = new StringBuilder();
            List<Node> ts = ((Add) this).t;
            for (int i = 0; i < ts.size(); i++) {
                Node t = ts.get(i);
                if (i == 0) sb.append(t);
                else if (negLead(t)) sb.append(" - ").append(negated(t));
                else sb.append(" + ").append(t);
            }
            return sb.toString();
        }
        if (this instanceof Pow) {
            Pow p = (Pow) this;
            if (p.e.isNum(0.5)) return "sqrt(" + p.b + ")";
            if (p.e instanceof Num && ((Num) p.e).v < 0) {
                Node pos = new Pow(p.b, num(-((Num) p.e).v));
                if (p.e.isNum(-1)) pos = p.b;
                String d = pos.toString();
                return "1/" + (atom(pos) || (pos instanceof Pow && !(pos.toString().contains(" "))) ? d : "(" + d + ")");
            }
            String bs = atom(p.b) ? p.b.toString() : "(" + p.b + ")";
            String es;
            if ((p.e.isInteger() && ((Num) p.e).v >= 0) || p.e instanceof Var) es = p.e.toString();
            else if (p.e instanceof Num) es = "(" + ratStr(((Num) p.e).v) + ")";
            else es = "(" + p.e + ")";
            return bs + "^" + es;
        }
        Mul m = (Mul) this;
        List<Node> top = new ArrayList<>();
        List<Node> dens = new ArrayList<>();
        double coef = 1;
        for (Node f : m.f) {
            if (f instanceof Num) coef *= ((Num) f).v;
            else if (f instanceof Pow && ((Pow) f).e instanceof Num && ((Num) ((Pow) f).e).v < 0) {
                Pow p = (Pow) f;
                double ev = -((Num) p.e).v;
                dens.add(Math.abs(ev - 1) < 1e-12 ? p.b : new Pow(p.b, num(ev)));
            } else top.add(f);
        }
        double ac = Math.abs(coef);
        long cn = 1;
        long cd = 1;
        boolean useDec = false;
        if (Math.abs(ac - Math.rint(ac)) < 1e-12) cn = Math.round(ac);
        else {
            long[] r = ratio(ac);
            if (r != null) { cn = r[0]; cd = r[1]; } else useDec = true;
        }
        if (cd > 1) dens.add(num(cd));
        boolean needCoef = cn != 1 || useDec || top.isEmpty();
        StringBuilder sb = new StringBuilder();
        if (coef < 0) sb.append("-");
        boolean intCoef = !useDec && cd == 1 || cd > 1;
        if (needCoef) sb.append(useDec ? fmtNum(ac) : Long.toString(cn));
        for (int i = 0; i < top.size(); i++) {
            Node f = top.get(i);
            String s = f instanceof Add ? "(" + f + ")" : f.toString();
            if (!(sb.length() == 0 || (sb.length() == 1 && sb.charAt(0) == '-'))) {
                boolean implicit = needCoef && i == 0 && !useDec && (f instanceof Var || f instanceof Fn || f instanceof Add);
                if (!implicit) sb.append("*");
            }
            sb.append(s);
        }
        if (!dens.isEmpty()) {
            if (sb.length() == 0 || (sb.length() == 1 && sb.charAt(0) == '-')) sb.append("1");
            String d;
            if (dens.size() == 1) {
                Node q = dens.get(0);
                d = q instanceof Add || q instanceof Mul ? "(" + q + ")" : q.toString();
            } else {
                List<String> parts = new ArrayList<>();
                for (Node q : dens) parts.add(q instanceof Add ? "(" + q + ")" : q.toString());
                d = "(" + String.join("*", parts) + ")";
            }
            sb.append("/").append(d);
        }
        return sb.toString();
    }

    public static long[] ratio(double v) {
        double a = Math.abs(v);
        for (long q = 2; q <= 1000; q++) {
            double p = a * q;
            if (Math.abs(p - Math.rint(p)) < 1e-9 * Math.max(1, q)) return new long[]{Math.round(p), q};
        }
        return null;
    }

    public static String ratStr(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-12) return fmtNum(v);
        long[] r = ratio(v);
        if (r == null) return fmtNum(v);
        return (v < 0 ? "-" : "") + r[0] + "/" + r[1];
    }

    @Override
    public boolean equals(Object o) { return o instanceof Node && toString().equals(o.toString()); }

    @Override
    public int hashCode() { return toString().hashCode(); }

    public static List<Node> list(Node... n) { return new ArrayList<>(Arrays.asList(n)); }

    public static <T> List<T> sorted(List<T> l) { List<T> c = new ArrayList<>(l); Collections.sort((List) c); return c; }
}
