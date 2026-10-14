package com.calc.app;

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
            if (match('+')) v += parseTerm();
            else if (match('-')) v -= parseTerm();
            else break;
        }
        return v;
    }

    private double parseTerm() {
        double v = parsePower();
        while (true) {
            skipSpaces();
            if (match('*')) v *= parsePower();
            else if (matchWord("mod")) v = v % parsePower();
            else if (match('/')) {
                double d = parsePower();
                if (d == 0) throw new EvalException("div0");
                v /= d;
            } else if (startsImplicitFactor()) {
                v *= parsePower();
            } else break;
        }
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
        double base = parseUnary();
        skipSpaces();
        if (match('^')) {
            double exp = parsePower();
            return Math.pow(base, exp);
        }
        return base;
    }

    private double parseUnary() {
        skipSpaces();
        if (match('-')) return -parseUnary();
        if (match('+')) return parseUnary();
        return parsePostfix();
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

        if (matchWord("asinh")) return Math.asinh(argParen());
        if (matchWord("acosh")) return Math.acosh(argParen());
        if (matchWord("atanh")) return Math.atanh(argParen());
        if (matchWord("asin")) return fromRad(Math.asin(argParen()));
        if (matchWord("acos")) return fromRad(Math.acos(argParen()));
        if (matchWord("atan")) return fromRad(Math.atan(argParen()));
        if (matchWord("sinh")) return Math.sinh(argParen());
        if (matchWord("cosh")) return Math.cosh(argParen());
        if (matchWord("tanh")) return Math.tanh(argParen());
        if (matchWord("sin")) return Math.sin(toRad(argParen()));
        if (matchWord("cos")) return Math.cos(toRad(argParen()));
        if (matchWord("tan")) return Math.tan(toRad(argParen()));
        if (matchWord("log2")) return Math.log(argParen()) / Math.log(2);
        if (matchWord("log")) return Math.log10(argParen());
        if (matchWord("ln")) return Math.log(argParen());
        if (matchWord("sqrt")) return rootArg(2);
        if (matchWord("cbrt")) return rootArg(3);
        if (matchWord("root")) return rootFn();
        if (matchWord("abs")) return Math.abs(argParen());
        if (matchWord("fact")) return factorial(argParen());
        if (matchWord("exp")) return Math.exp(argParen());
        if (matchWord("rand")) { consumeEmptyParen(); return Math.random(); }
        if (matchWord("ncr")) return nCrFn();
        if (matchWord("npr")) return nPrFn();
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
