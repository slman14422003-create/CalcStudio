package com.calc.app.solver;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class Arith {

    private Arith() {}

    private static final int MAX_STEPS = 40;
    private static final int MAX_DIGITS = 400;

    private static abstract class N {}

    private static final class Num extends N {
        final BigDecimal v;
        Num(BigDecimal v) { this.v = v; }
    }

    private static final class Bin extends N {
        final char op;
        final N l;
        final N r;
        Bin(char op, N l, N r) { this.op = op; this.l = l; this.r = r; }
    }

    private static final class Neg extends N {
        final N x;
        Neg(N x) { this.x = x; }
    }

    private static final class Pct extends N {
        final N x;
        Pct(N x) { this.x = x; }
    }

    private static final class Fn extends N {
        final String name;
        final N x;
        Fn(String name, N x) { this.name = name; this.x = x; }
    }

    private static final class Par extends N {
        final N x;
        Par(N x) { this.x = x; }
    }

    private static final class Fail extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class P {
        final String s;
        int i = 0;

        P(String s) { this.s = s; }

        boolean more() { return i < s.length(); }

        char peek() { return s.charAt(i); }

        N parse() {
            N n = expr();
            if (more()) throw new Fail();
            return n;
        }

        N expr() {
            N n = term();
            while (more() && (peek() == '+' || peek() == '-')) {
                char op = s.charAt(i++);
                n = new Bin(op, n, term());
            }
            return n;
        }

        N term() {
            N n = unary();
            while (more()) {
                char c = peek();
                if (c == '*' || c == '/') {
                    i++;
                    n = new Bin(c, n, unary());
                } else if (c == '(' || c == '\u221a' || (c >= 'a' && c <= 'z')) {
                    n = new Bin('*', n, unary());
                } else {
                    break;
                }
            }
            return n;
        }

        N unary() {
            if (more() && (peek() == '-' || peek() == '+')) {
                char c = s.charAt(i++);
                N x = unary();
                return c == '-' ? new Neg(x) : x;
            }
            return power();
        }

        N power() {
            N b = postfix();
            if (more() && peek() == '^') {
                i++;
                return new Bin('^', b, unary());
            }
            return b;
        }

        N postfix() {
            N n = primary();
            while (more() && peek() == '%') {
                i++;
                n = new Pct(n);
            }
            return n;
        }

        N primary() {
            if (!more()) throw new Fail();
            char c = peek();
            if (c == '(') {
                i++;
                N x = expr();
                if (!more() || peek() != ')') throw new Fail();
                i++;
                return new Par(x);
            }
            if (c == '\u221a') {
                i++;
                return new Fn("sqrt", primary());
            }
            if (c >= 'a' && c <= 'z') {
                int st = i;
                while (more() && peek() >= 'a' && peek() <= 'z') i++;
                String name = s.substring(st, i);
                if (!name.equals("sqrt") && !name.equals("abs")) throw new Fail();
                if (!more() || peek() != '(') throw new Fail();
                return new Fn(name, primary());
            }
            if ((c >= '0' && c <= '9') || c == '.') {
                int st = i;
                boolean dot = false;
                while (more() && ((peek() >= '0' && peek() <= '9') || peek() == '.')) {
                    if (peek() == '.') {
                        if (dot) throw new Fail();
                        dot = true;
                    }
                    i++;
                }
                String t = s.substring(st, i);
                if (t.equals(".")) throw new Fail();
                return new Num(new BigDecimal(t));
            }
            throw new Fail();
        }
    }

    private static String normalize(String in) {
        String t = CalcToolBridge.digits(in);
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            if (Character.isWhitespace(c) || c == ',') continue;
            if (c == '\u2212' || c == '\u2013' || c == '\u2014') b.append('-');
            else if (c == '\u00d7' || c == '\u00b7' || c == '\u22c5') b.append('*');
            else if (c == '\u00f7') b.append('/');
            else b.append(Character.toLowerCase(c));
        }
        String r = b.toString().replace("**", "^");
        while (!r.isEmpty()) {
            char last = r.charAt(r.length() - 1);
            if (last == '=' || last == '?' || last == '\u061f') r = r.substring(0, r.length() - 1);
            else break;
        }
        return r;
    }

    private static N fold(N n) {
        if (n instanceof Bin) {
            Bin b = (Bin) n;
            return new Bin(b.op, fold(b.l), fold(b.r));
        }
        if (n instanceof Neg) {
            N x = fold(((Neg) n).x);
            if (x instanceof Num) return new Num(((Num) x).v.negate());
            return new Neg(x);
        }
        if (n instanceof Pct) return new Pct(fold(((Pct) n).x));
        if (n instanceof Fn) {
            Fn f = (Fn) n;
            return new Fn(f.name, fold(f.x));
        }
        if (n instanceof Par) {
            N x = fold(((Par) n).x);
            if (x instanceof Num || x instanceof Par) return x;
            return new Par(x);
        }
        return n;
    }

    private static N replace(N n, N target, N with) {
        if (n == target) return with;
        if (n instanceof Bin) {
            Bin b = (Bin) n;
            N l = replace(b.l, target, with);
            N r = replace(b.r, target, with);
            return (l == b.l && r == b.r) ? n : new Bin(b.op, l, r);
        }
        if (n instanceof Neg) {
            N x = replace(((Neg) n).x, target, with);
            return x == ((Neg) n).x ? n : new Neg(x);
        }
        if (n instanceof Pct) {
            N x = replace(((Pct) n).x, target, with);
            return x == ((Pct) n).x ? n : new Pct(x);
        }
        if (n instanceof Fn) {
            Fn f = (Fn) n;
            N x = replace(f.x, target, with);
            return x == f.x ? n : new Fn(f.name, x);
        }
        if (n instanceof Par) {
            N x = replace(((Par) n).x, target, with);
            return x == ((Par) n).x ? n : new Par(x);
        }
        return n;
    }

    private static String sym(char op) {
        switch (op) {
            case '*': return "\u00d7";
            case '/': return "\u00f7";
            case '-': return "\u2212";
            default: return String.valueOf(op);
        }
    }

    private static boolean inexact(BigDecimal v) {
        return v.stripTrailingZeros().scale() > 10;
    }

    private static String fmt(BigDecimal v) {
        if (v.signum() == 0) return "0";
        BigDecimal w = inexact(v) ? round(v) : v;
        if (w.signum() == 0) return "0";
        String s = w.stripTrailingZeros().toPlainString();
        if (s.startsWith("-")) s = "\u2212" + s.substring(1);
        return s;
    }

    private static String render(N n, boolean parenNeg) {
        if (n instanceof Num) {
            BigDecimal v = ((Num) n).v;
            String s = fmt(v);
            return (parenNeg && v.signum() < 0) ? "(" + s + ")" : s;
        }
        if (n instanceof Bin) {
            Bin b = (Bin) n;
            String l = render(b.l, b.op == '^');
            String r = render(b.r, true);
            if (b.op == '^') return l + "^" + r;
            return l + " " + sym(b.op) + " " + r;
        }
        if (n instanceof Neg) {
            N x = ((Neg) n).x;
            if (x instanceof Par || x instanceof Fn || x instanceof Num) return "\u2212" + render(x, false);
            return "\u2212(" + render(x, false) + ")";
        }
        if (n instanceof Pct) return render(((Pct) n).x, true) + "%";
        if (n instanceof Par) return "(" + render(((Par) n).x, false) + ")";
        Fn f = (Fn) n;
        boolean par = f.x instanceof Par;
        if (f.name.equals("sqrt")) {
            return "\u221a" + (par ? render(f.x, false) : render(f.x, true));
        }
        return par ? f.name + render(f.x, false) : f.name + "(" + render(f.x, false) + ")";
    }

    private static String render(N n) {
        return render(n, false);
    }

    private static final class Cand {
        N node;
        int depth;
        int prec;
    }

    private static boolean ready(N n) {
        if (n instanceof Bin) return ((Bin) n).l instanceof Num && ((Bin) n).r instanceof Num;
        if (n instanceof Pct) return ((Pct) n).x instanceof Num;
        if (n instanceof Fn) return ((Fn) n).x instanceof Num;
        return false;
    }

    private static int prec(N n) {
        if (n instanceof Fn) return 5;
        if (n instanceof Pct) return 4;
        char op = ((Bin) n).op;
        if (op == '^') return 3;
        if (op == '*' || op == '/') return 2;
        return 1;
    }

    private static void collect(N n, int depth, List<Cand> out) {
        if (n instanceof Bin) {
            collect(((Bin) n).l, depth, out);
            if (ready(n)) add(n, depth, out);
            collect(((Bin) n).r, depth, out);
        } else if (n instanceof Neg) {
            collect(((Neg) n).x, depth, out);
        } else if (n instanceof Pct) {
            collect(((Pct) n).x, depth, out);
            if (ready(n)) add(n, depth, out);
        } else if (n instanceof Fn) {
            collect(((Fn) n).x, depth, out);
            if (ready(n)) add(n, depth, out);
        } else if (n instanceof Par) {
            collect(((Par) n).x, depth + 1, out);
        }
    }

    private static void add(N n, int depth, List<Cand> out) {
        Cand c = new Cand();
        c.node = n;
        c.depth = depth;
        c.prec = prec(n);
        out.add(c);
    }

    private static N pick(N root) {
        List<Cand> all = new ArrayList<>();
        collect(root, 0, all);
        Cand best = null;
        for (Cand c : all) {
            if (best == null || c.depth > best.depth || (c.depth == best.depth && c.prec > best.prec)) best = c;
        }
        return best == null ? null : best.node;
    }

    private static BigDecimal round(BigDecimal v) {
        BigDecimal r = v.setScale(10, RoundingMode.HALF_UP);
        return r.signum() == 0 ? BigDecimal.ZERO : r.stripTrailingZeros();
    }

    private static BigDecimal div(BigDecimal x, BigDecimal y, boolean[] approx) {
        if (y.signum() == 0) throw new ArithmeticException();
        try {
            return x.divide(y);
        } catch (ArithmeticException e) {
            approx[0] = true;
            return x.divide(y, MathContext.DECIMAL128).stripTrailingZeros();
        }
    }

    private static BigDecimal pow(BigDecimal x, BigDecimal y, boolean[] approx) {
        BigDecimal ys = y.stripTrailingZeros();
        if (ys.signum() == 0) {
            if (x.signum() == 0) throw new ArithmeticException();
            return BigDecimal.ONE;
        }
        if (ys.scale() <= 0 && ys.abs().compareTo(BigDecimal.valueOf(1000)) <= 0) {
            int e = ys.intValue();
            int a = Math.abs(e);
            if (x.signum() == 0) {
                if (e < 0) throw new ArithmeticException();
                return BigDecimal.ZERO;
            }
            double digits = a * Math.abs(Math.log10(x.abs().doubleValue()));
            if (digits > MAX_DIGITS || (long) x.scale() * a > 2000) throw new ArithmeticException();
            BigDecimal p = x.pow(a);
            return e >= 0 ? p : div(BigDecimal.ONE, p, approx);
        }
        double r = Math.pow(x.doubleValue(), y.doubleValue());
        if (Double.isNaN(r) || Double.isInfinite(r)) throw new ArithmeticException();
        approx[0] = true;
        return BigDecimal.valueOf(r).stripTrailingZeros();
    }

    private static BigDecimal compute(N n, boolean[] approx) {
        BigDecimal v;
        if (n instanceof Bin) {
            Bin b = (Bin) n;
            BigDecimal x = ((Num) b.l).v;
            BigDecimal y = ((Num) b.r).v;
            switch (b.op) {
                case '+': v = x.add(y); break;
                case '-': v = x.subtract(y); break;
                case '*': v = x.multiply(y); break;
                case '/': v = div(x, y, approx); break;
                default: v = pow(x, y, approx); break;
            }
        } else if (n instanceof Pct) {
            v = ((Num) ((Pct) n).x).v.movePointLeft(2);
        } else {
            Fn f = (Fn) n;
            BigDecimal x = ((Num) f.x).v;
            if (f.name.equals("abs")) {
                v = x.abs();
            } else {
                if (x.signum() < 0) throw new ArithmeticException();
                BigDecimal r = BigDecimal.valueOf(Math.sqrt(x.doubleValue())).stripTrailingZeros();
                if (r.multiply(r).compareTo(x) != 0) approx[0] = true;
                v = r;
            }
        }
        if (v.precision() > MAX_DIGITS) throw new ArithmeticException();
        return v;
    }

    private static double evalD(N n) {
        if (n instanceof Num) return ((Num) n).v.doubleValue();
        if (n instanceof Neg) return -evalD(((Neg) n).x);
        if (n instanceof Par) return evalD(((Par) n).x);
        if (n instanceof Pct) return evalD(((Pct) n).x) / 100.0;
        if (n instanceof Fn) {
            Fn f = (Fn) n;
            double x = evalD(f.x);
            return f.name.equals("abs") ? Math.abs(x) : Math.sqrt(x);
        }
        Bin b = (Bin) n;
        double x = evalD(b.l);
        double y = evalD(b.r);
        switch (b.op) {
            case '+': return x + y;
            case '-': return x - y;
            case '*': return x * y;
            case '/': return x / y;
            default: return Math.pow(x, y);
        }
    }

    private static boolean close(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b) || Double.isInfinite(a) || Double.isInfinite(b)) return true;
        double tol = 1e-7 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return Math.abs(a - b) <= tol;
    }

    public static Solution run(String exprRaw, String raw) {
        String s = normalize(exprRaw == null ? "" : exprRaw);
        if (s.isEmpty()) return Solution.fail("arith", raw, "err_unsupported");
        N tree;
        try {
            tree = new P(s).parse();
        } catch (Fail e) {
            return Solution.fail("arith", raw, "err_unsupported");
        } catch (StackOverflowError e) {
            return Solution.fail("arith", raw, "err_unsupported");
        }
        Solution sol = new Solution("arith", render(tree));
        double check = evalD(tree);
        N cur = fold(tree);
        sol.step("a_start", render(tree));
        boolean[] approx = {false};
        int count = 0;
        try {
            while (!(cur instanceof Num)) {
                if (++count > MAX_STEPS) return Solution.fail("arith", raw, "err_unsupported");
                N next = pick(cur);
                if (next == null) return Solution.fail("arith", raw, "err_unsupported");
                String opText = render(next);
                boolean[] local = {false};
                BigDecimal v = compute(next, local);
                if (local[0]) approx[0] = true;
                boolean x = local[0] || inexact(v);
                String res = fmt(v);
                cur = fold(replace(cur, next, new Num(v)));
                if (cur instanceof Num) sol.step(x ? "a_lastx" : "a_last", opText, res);
                else sol.step(x ? "a_stepx" : "a_step", opText, res, render(cur));
            }
        } catch (ArithmeticException e) {
            return Solution.fail("arith", raw, "err_math");
        }
        BigDecimal fin = ((Num) cur).v;
        if (inexact(fin)) approx[0] = true;
        String shown = fmt(fin);
        sol.answers.add(shown);
        sol.answer("ans_value", shown);
        sol.verify(close(fin.doubleValue(), check), "v_arith");
        if (approx[0]) sol.noteKey = "a_rounded";
        return sol;
    }
}
