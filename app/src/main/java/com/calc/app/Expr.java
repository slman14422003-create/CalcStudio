package com.calc.app;

import org.apache.commons.math3.primes.Primes;
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.ArithmeticUtils;

import java.util.ArrayList;
import java.util.List;

public final class Expr {

    public static final int DEG = 0;
    public static final int RAD = 1;
    public static final int GRAD = 2;

    public static class EvalException extends RuntimeException {
        public EvalException(String msg) { super(msg); }
    }

    public interface AnsProvider {
        double ans();
    }

    private final String src;
    private final int angleMode;
    private final AnsProvider ansProvider;
    private int pos;
    private boolean lastTermSimple;

    private Expr(String src, int angleMode, AnsProvider ansProvider) {
        this.src = src;
        this.angleMode = angleMode;
        this.ansProvider = ansProvider;
    }

    public static double eval(String input, int angleMode, AnsProvider ansProvider) {
        String clean = normalize(input);
        if (clean.isEmpty()) throw new EvalException("empty");
        Expr e = new Expr(clean, angleMode, ansProvider);
        double v = e.parseExpr();
        e.skipSpaces();
        if (e.pos != e.src.length()) throw new EvalException("syntax");
        if (Double.isNaN(v)) throw new EvalException("math");
        if (Double.isInfinite(v)) throw new EvalException("overflow");
        return v;
    }

    private static String normalize(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '×': b.append('*'); break;
                case '÷': b.append('/'); break;
                case '−': b.append('-'); break;
                case '，': b.append(','); break;
                case '٫':
                case '،': b.append(','); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    private void skipSpaces() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private char peek() {
        skipSpaces();
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private boolean match(char c) {
        skipSpaces();
        if (pos < src.length() && src.charAt(pos) == c) { pos++; return true; }
        return false;
    }

    private boolean matchWord(String w) {
        skipSpaces();
        int len = w.length();
        if (pos + len <= src.length() && src.regionMatches(true, pos, w, 0, len)) {
            boolean boundaryOk = true;
            if (pos + len < src.length()) {
                char after = src.charAt(pos + len);
                if (Character.isLetter(after) && Character.isLetter(w.charAt(len - 1))) boundaryOk = false;
            }
            if (boundaryOk) { pos += len; return true; }
        }
        return false;
    }

    private double parseExpr() {
        double v = parseTerm();
        while (true) {
            skipSpaces();
            if (match('+')) {
                int from = pos;
                double t = parseTerm();
                v += (lastTermSimple && endsWithPercent(from)) ? v * t : t;
            } else if (match('-')) {
                int from = pos;
                double t = parseTerm();
                v -= (lastTermSimple && endsWithPercent(from)) ? v * t : t;
            } else break;
        }
        return v;
    }

    private boolean endsWithPercent(int from) {
        int k = pos - 1;
        while (k >= from && Character.isWhitespace(src.charAt(k))) k--;
        return k >= from && src.charAt(k) == '%';
    }

    private double parseTerm() {
        boolean hadOp = false;
        double v = parseUnary();
        while (true) {
            skipSpaces();
            if (match('*')) {
                hadOp = true;
                v *= parseUnary();
            } else if (matchWord("mod")) {
                hadOp = true;
                double d = parseUnary();
                if (d == 0) throw new EvalException("div0");
                v = v - d * Math.floor(v / d);
            } else if (match('/')) {
                hadOp = true;
                double d = parseUnary();
                if (d == 0) throw new EvalException("div0");
                v /= d;
            } else if (startsImplicitFactor()) {
                hadOp = true;
                v *= parseUnary();
            } else break;
        }
        lastTermSimple = !hadOp;
        return v;
    }

    private boolean startsImplicitFactor() {
        skipSpaces();
        if (pos >= src.length()) return false;
        char c = src.charAt(pos);
        if (Character.isDigit(c) || c == '.' ) return true;
        if (c == '(' ) return true;
        if (isIdentStart(c)) return true;
        return false;
    }

    private double parsePower() {
        double base = parsePostfix();
        skipSpaces();
        if (match('^')) {
            double exp = parseUnary();
            return Math.pow(base, exp);
        }
        return base;
    }

    private double parseUnary() {
        skipSpaces();
        if (match('-')) return -parseUnary();
        if (match('+')) return parseUnary();
        return parsePower();
    }

    private double parsePostfix() {
        double v = parsePrimary();
        while (true) {
            skipSpaces();
            if (pos >= src.length()) break;
            char c = src.charAt(pos);
            if (c == '!') { pos++; v = factorial(v); }
            else if (c == '%') { pos++; v = v / 100.0; }
            else if (c == '²') { pos++; v = v * v; }
            else if (c == '³') { pos++; v = v * v * v; }
            else if (src.startsWith("⁻¹", pos)) { pos += 2; if (v == 0) throw new EvalException("div0"); v = 1.0 / v; }
            else break;
        }
        return v;
    }

    private boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == 'π' || c == '√' || c == '∛';
    }

    private double parsePrimary() {
        skipSpaces();
        if (pos >= src.length()) throw new EvalException("syntax");
        char c = src.charAt(pos);

        if (match('(')) {
            double v = parseExpr();
            if (!match(')')) throw new EvalException("syntax");
            return v;
        }
        if (c == '√') { pos++; return rootArg(2); }
        if (c == '∛') { pos++; return rootArg(3); }
        if (Character.isDigit(c) || c == '.') return parseNumber();

        if (matchWord("atan2")) { double[] a = args(2, 2); return fromRad(Math.atan2(a[0], a[1])); }
        if (matchWord("asinh")) return asinh(argParen());
        if (matchWord("acosh")) return acosh(argParen());
        if (matchWord("atanh")) return atanh(argParen());
        if (matchWord("asin")) return fromRad(Math.asin(argParen()));
        if (matchWord("acos")) return fromRad(Math.acos(argParen()));
        if (matchWord("atan")) return fromRad(Math.atan(argParen()));
        if (matchWord("sinh")) return Math.sinh(argParen());
        if (matchWord("cosh")) return Math.cosh(argParen());
        if (matchWord("tanh")) return Math.tanh(argParen());
        if (matchWord("sin")) return trig(0, argParen());
        if (matchWord("cos")) return trig(1, argParen());
        if (matchWord("tan")) return trig(2, argParen());
        if (matchWord("log2")) return ln(argParen()) / Math.log(2);
        if (matchWord("log")) return logFn();
        if (matchWord("ln")) return ln(argParen());
        if (matchWord("sqrt")) return rootArg(2);
        if (matchWord("cbrt")) return rootArg(3);
        if (matchWord("root")) return rootFn();
        if (matchWord("abs")) return Math.abs(argParen());
        if (matchWord("fact")) return factorial(argParen());
        if (matchWord("exp")) return Math.exp(argParen());
        if (matchWord("rand")) { consumeEmptyParen(); return Math.random(); }
        if (matchWord("ncr")) return nCrFn();
        if (matchWord("npr")) return nPrFn();
        if (matchWord("acot")) return fromRad(Math.atan(1.0 / argParen()));
        if (matchWord("coth")) return 1.0 / Math.tanh(argParen());
        if (matchWord("cot")) return trig(3, argParen());
        if (matchWord("sec")) return trig(4, argParen());
        if (matchWord("csc")) return trig(5, argParen());
        if (matchWord("deg")) return Math.toDegrees(argParen());
        if (matchWord("rad")) return Math.toRadians(argParen());
        if (matchWord("logb")) { double[] a = args(2, 2); double d = ln(a[0]); if (d == 0) throw new EvalException("math"); return ln(a[1]) / d; }
        if (matchWord("lg")) return log10(argParen());
        if (matchWord("pow")) { double[] a = args(2, 2); return Math.pow(a[0], a[1]); }
        if (matchWord("mod")) { double[] a = args(2, 2); if (a[1] == 0) throw new EvalException("div0"); return a[0] - a[1] * Math.floor(a[0] / a[1]); }
        if (matchWord("hypot")) { double[] a = args(2, 2); return Math.hypot(a[0], a[1]); }
        if (matchWord("gcd")) return gcdFn(args(2, 64));
        if (matchWord("lcm")) return lcmFn(args(2, 64));
        if (matchWord("floor")) return Math.floor(argParen());
        if (matchWord("ceil")) return Math.ceil(argParen());
        if (matchWord("trunc")) { double x = argParen(); return x < 0 ? Math.ceil(x) : Math.floor(x); }
        if (matchWord("frac")) { double x = argParen(); return x - (x < 0 ? Math.ceil(x) : Math.floor(x)); }
        if (matchWord("round")) return roundFn(args(1, 2));
        if (matchWord("sign")) return Math.signum(argParen());
        if (matchWord("min")) return StatUtils.min(args(1, 256));
        if (matchWord("max")) return StatUtils.max(args(1, 256));
        if (matchWord("sum")) return StatUtils.sum(args(1, 256));
        if (matchWord("prod")) { double p = 1; for (double d : args(1, 256)) p *= d; return p; }
        if (matchWord("avg") || matchWord("mean")) return StatUtils.mean(args(1, 256));
        if (matchWord("median")) return StatUtils.percentile(args(1, 256), 50);
        if (matchWord("stdevp")) return Math.sqrt(StatUtils.populationVariance(args(1, 256)));
        if (matchWord("stdev")) { double[] a = args(2, 256); return Math.sqrt(StatUtils.variance(a)); }
        if (matchWord("varp")) return StatUtils.populationVariance(args(1, 256));
        if (matchWord("var")) { double[] a = args(2, 256); return StatUtils.variance(a); }
        if (matchWord("isprime")) { double x = argParen(); return isInt(x) && x >= 2 && x < Integer.MAX_VALUE && Primes.isPrime((int) x) ? 1 : 0; }
        if (matchWord("nextprime")) { double x = argParen(); if (!isInt(x) || x < 0 || x >= 2000000000) throw new EvalException("math"); return Primes.nextPrime((int) Math.max(2, x + 1)); }
        if (matchWord("erf")) return Erf.erf(argParen());
        if (matchWord("gamma")) return Gamma.gamma(argParen());
        if (matchWord("randint")) { double[] a = args(2, 2); double lo = Math.ceil(Math.min(a[0], a[1])), hi = Math.floor(Math.max(a[0], a[1])); return lo + Math.floor(Math.random() * (hi - lo + 1)); }
        if (matchWord("phi")) return (1 + Math.sqrt(5)) / 2;
        if (matchWord("tau")) return 2 * Math.PI;
        if (matchWord("pi")) return Math.PI;
        if (matchWord("ans")) return ansProvider != null ? ansProvider.ans() : 0;
        if (c == 'π') { pos++; return Math.PI; }
        if (c == 'e') {
            if (pos + 1 < src.length() && Character.isLetter(src.charAt(pos + 1))) {
                // not a bare 'e', fall through to error below
            } else {
                pos++;
                return Math.E;
            }
        }
        throw new EvalException("syntax");
    }

    private double trig(int kind, double x) {
        double r = toRad(x);
        double s = snap(Math.sin(r));
        double c = snap(Math.cos(r));
        switch (kind) {
            case 0: return s;
            case 1: return c;
            case 2:
                if (c == 0) throw new EvalException("math");
                return snap(s / c);
            case 3:
                if (s == 0) throw new EvalException("math");
                return snap(c / s);
            case 4:
                if (c == 0) throw new EvalException("math");
                return 1.0 / c;
            default:
                if (s == 0) throw new EvalException("math");
                return 1.0 / s;
        }
    }

    private static double snap(double v) {
        if (Math.abs(v) < 1e-15) return 0;
        double r = Math.rint(v);
        if (Math.abs(v - r) < 1e-14) return r;
        return v;
    }

    private static double ln(double x) {
        if (!(x > 0)) throw new EvalException("math");
        return Math.log(x);
    }

    private static double log10(double x) {
        if (!(x > 0)) throw new EvalException("math");
        return Math.log10(x);
    }

    private double rootArg(int n) {
        double x;
        if (match('(')) { x = parseExpr(); if (!match(')')) throw new EvalException("syntax"); }
        else x = parsePostfix();
        if (n == 2) {
            if (x < 0) throw new EvalException("math");
            return Math.sqrt(x);
        }
        return Math.cbrt(x);
    }

    private double rootFn() {
        if (!match('(')) throw new EvalException("syntax");
        double n = parseExpr();
        if (!match(',')) throw new EvalException("syntax");
        double x = parseExpr();
        if (!match(')')) throw new EvalException("syntax");
        if (n == 0) throw new EvalException("div0");
        if (x < 0 && (n % 2 == 0)) throw new EvalException("math");
        double sign = x < 0 ? -1 : 1;
        return sign * Math.pow(Math.abs(x), 1.0 / n);
    }

    private double nCrFn() {
        if (!match('(')) throw new EvalException("syntax");
        double n = parseExpr();
        if (!match(',')) throw new EvalException("syntax");
        double r = parseExpr();
        if (!match(')')) throw new EvalException("syntax");
        return nCr(n, r);
    }

    private double nPrFn() {
        if (!match('(')) throw new EvalException("syntax");
        double n = parseExpr();
        if (!match(',')) throw new EvalException("syntax");
        double r = parseExpr();
        if (!match(')')) throw new EvalException("syntax");
        return nPr(n, r);
    }

    public static double nCr(double n, double r) {
        if (n < 0 || r < 0 || r > n) throw new EvalException("math");
        return Math.round(factorial(n) / (factorial(r) * factorial(n - r)));
    }

    public static double nPr(double n, double r) {
        if (n < 0 || r < 0 || r > n) throw new EvalException("math");
        return Math.round(factorial(n) / factorial(n - r));
    }

    private static double asinh(double x) {
        return Double.isInfinite(x) ? x : Math.log(x + Math.sqrt(x * x + 1));
    }

    private static double acosh(double x) {
        if (x < 1) throw new EvalException("math");
        return Math.log(x + Math.sqrt(x * x - 1));
    }

    private static double atanh(double x) {
        if (x <= -1 || x >= 1) throw new EvalException("math");
        return 0.5 * Math.log((1 + x) / (1 - x));
    }

    private static boolean isInt(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x) && x == Math.floor(x);
    }

    private double[] args(int min, int max) {
        if (!match('(')) throw new EvalException("syntax");
        List<Double> list = new ArrayList<>();
        list.add(parseExpr());
        while (match(',')) list.add(parseExpr());
        if (!match(')')) throw new EvalException("syntax");
        if (list.size() < min || list.size() > max) throw new EvalException("syntax");
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private double logFn() {
        double[] a = args(1, 2);
        if (a.length == 1) return log10(a[0]);
        double d = ln(a[1]);
        if (d == 0) throw new EvalException("math");
        return ln(a[0]) / d;
    }

    private static double gcdFn(double[] a) {
        long g = 0;
        for (double d : a) {
            if (!isInt(d) || Math.abs(d) > 9e15) throw new EvalException("math");
            g = ArithmeticUtils.gcd(g, (long) Math.abs(d));
        }
        return g;
    }

    private static double lcmFn(double[] a) {
        long l = 1;
        for (double d : a) {
            if (!isInt(d) || Math.abs(d) > 9e15) throw new EvalException("math");
            long n = (long) Math.abs(d);
            if (n == 0) return 0;
            try { l = ArithmeticUtils.lcm(l, n); } catch (ArithmeticException ex) { throw new EvalException("overflow"); }
        }
        return l;
    }

    private static double roundFn(double[] a) {
        if (a.length == 1) return Math.floor(a[0] + 0.5);
        double m = Math.pow(10, a[1]);
        return Math.floor(a[0] * m + 0.5) / m;
    }

    private void consumeEmptyParen() {
        if (match('(')) match(')');
    }

    private double argParen() {
        if (match('(')) {
            double v = parseExpr();
            if (!match(')')) throw new EvalException("syntax");
            return v;
        }
        return parsePostfix();
    }

    private double parseNumber() {
        int start = pos;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            int save = pos;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            } else {
                pos = save;
            }
        }
        String numStr = src.substring(start, pos);
        try {
            return Double.parseDouble(numStr);
        } catch (NumberFormatException ex) {
            throw new EvalException("syntax");
        }
    }

    private double toRad(double v) {
        if (angleMode == DEG) return Math.toRadians(v);
        if (angleMode == GRAD) return v * Math.PI / 200.0;
        return v;
    }

    private double fromRad(double v) {
        if (angleMode == DEG) return Math.toDegrees(v);
        if (angleMode == GRAD) return v * 200.0 / Math.PI;
        return v;
    }

    public static double factorial(double n) {
        if (n < 0) throw new EvalException("math");
        if (Math.floor(n) != n) return gamma(n + 1);
        if (n > 170) throw new EvalException("overflow");
        double r = 1;
        for (int i = 2; i <= (int) n; i++) r *= i;
        return r;
    }

    private static double gamma(double x) {
        double[] g = {
                676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7
        };
        if (x < 0.5) return Math.PI / (Math.sin(Math.PI * x) * gamma(1 - x));
        x -= 1;
        double a = 0.99999999999980993;
        double t = x + 7.5;
        for (int i = 0; i < g.length; i++) a += g[i] / (x + i + 1);
        return Math.sqrt(2 * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
    }

    public static List<String> tokenizeForHighlight(String s) {
        List<String> out = new ArrayList<>();
        out.add(s);
        return out;
    }
}
