package com.calc.app;

import ch.obermuhlner.math.big.BigDecimalMath;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

public final class BigExpr {

    public static final class Unsupported extends RuntimeException {
        public Unsupported() { super("unsupported"); }
    }

    private final String src;
    private final int angleMode;
    private final MathContext mc;
    private final BigDecimal ans;
    private int pos;
    private boolean lastTermSimple;

    private BigExpr(String src, int angleMode, MathContext mc, BigDecimal ans) {
        this.src = src;
        this.angleMode = angleMode;
        this.mc = mc;
        this.ans = ans;
    }

    public static BigDecimal eval(String input, int angleMode, int digits, BigDecimal ans) {
        String clean = input.replace('×', '*').replace('÷', '/').replace('−', '-');
        BigExpr e = new BigExpr(clean, angleMode, new MathContext(digits + 5, RoundingMode.HALF_EVEN), ans == null ? BigDecimal.ZERO : ans);
        BigDecimal v = e.expr();
        e.ws();
        if (e.pos != e.src.length()) throw new Expr.EvalException("syntax");
        return v.round(new MathContext(digits, RoundingMode.HALF_EVEN));
    }

    private void ws() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private boolean match(char c) {
        ws();
        if (pos < src.length() && src.charAt(pos) == c) { pos++; return true; }
        return false;
    }

    private boolean word(String w) {
        ws();
        int n = w.length();
        if (pos + n <= src.length() && src.regionMatches(true, pos, w, 0, n)) {
            if (pos + n < src.length() && Character.isLetter(src.charAt(pos + n))) return false;
            pos += n;
            return true;
        }
        return false;
    }

    private BigDecimal expr() {
        BigDecimal v = term();
        while (true) {
            if (match('+')) {
                int from = pos;
                BigDecimal t = term();
                v = v.add((lastTermSimple && endsWithPercent(from)) ? v.multiply(t, mc) : t, mc);
            } else if (match('-')) {
                int from = pos;
                BigDecimal t = term();
                v = v.subtract((lastTermSimple && endsWithPercent(from)) ? v.multiply(t, mc) : t, mc);
            } else break;
        }
        return v;
    }

    private boolean endsWithPercent(int from) {
        int k = pos - 1;
        while (k >= from && Character.isWhitespace(src.charAt(k))) k--;
        return k >= from && src.charAt(k) == '%';
    }

    private BigDecimal term() {
        boolean hadOp = false;
        BigDecimal v = unary();
        while (true) {
            ws();
            if (match('*')) {
                hadOp = true;
                v = v.multiply(unary(), mc);
            } else if (match('/')) {
                hadOp = true;
                BigDecimal d = unary();
                if (d.signum() == 0) throw new Expr.EvalException("div0");
                v = v.divide(d, mc);
            } else if (pos < src.length() && (src.charAt(pos) == '(' || Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || Character.isLetter(src.charAt(pos)) || src.charAt(pos) == '\u03c0' || src.charAt(pos) == '\u221a' || src.charAt(pos) == '\u221b')) {
                if (src.regionMatches(true, pos, "mod", 0, 3)) throw new Unsupported();
                hadOp = true;
                v = v.multiply(unary(), mc);
            } else break;
        }
        lastTermSimple = !hadOp;
        return v;
    }

    private BigDecimal power() {
        BigDecimal b = postfix();
        if (match('^')) {
            BigDecimal x = unary();
            return pow(b, x);
        }
        return b;
    }

    private BigDecimal pow(BigDecimal b, BigDecimal x) {
        if (x.signum() == 0) return BigDecimal.ONE;
        if (b.signum() == 0) {
            if (x.signum() < 0) throw new Expr.EvalException("div0");
            return BigDecimal.ZERO;
        }
        try {
            BigInteger xi = x.toBigIntegerExact();
            if (xi.abs().compareTo(BigInteger.valueOf(100000)) <= 0) {
                int n = xi.intValue();
                BigDecimal r = b.pow(Math.abs(n), mc);
                return n < 0 ? BigDecimal.ONE.divide(r, mc) : r;
            }
        } catch (ArithmeticException ignored) {}
        if (b.signum() < 0) throw new Expr.EvalException("math");
        return BigDecimalMath.pow(b, x, mc);
    }

    private BigDecimal unary() {
        if (match('-')) return unary().negate();
        if (match('+')) return unary();
        return power();
    }

    private BigDecimal postfix() {
        BigDecimal v = primary();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '!') { pos++; v = fact(v); }
            else if (c == '%') { pos++; v = v.divide(BigDecimal.valueOf(100), mc); }
            else if (c == '²') { pos++; v = v.multiply(v, mc); }
            else if (c == '³') { pos++; v = v.multiply(v, mc).multiply(v, mc); }
            else if (src.startsWith("⁻¹", pos)) {
                pos += 2;
                if (v.signum() == 0) throw new Expr.EvalException("div0");
                v = BigDecimal.ONE.divide(v, mc);
            } else break;
        }
        return v;
    }

    private BigDecimal fact(BigDecimal v) {
        if (v.signum() < 0) throw new Expr.EvalException("math");
        BigInteger n;
        try { n = v.toBigIntegerExact(); } catch (ArithmeticException ex) { throw new Unsupported(); }
        if (n.compareTo(BigInteger.valueOf(3000)) > 0) throw new Expr.EvalException("overflow");
        BigInteger r = BigInteger.ONE;
        for (int i = 2; i <= n.intValue(); i++) r = r.multiply(BigInteger.valueOf(i));
        return new BigDecimal(r);
    }

    private BigDecimal arg() {
        if (match('(')) {
            BigDecimal v = expr();
            if (!match(')')) throw new Expr.EvalException("syntax");
            return v;
        }
        return postfix();
    }

    private BigDecimal pi() { return BigDecimalMath.pi(mc); }

    private BigDecimal toRad(BigDecimal v) {
        if (angleMode == Expr.DEG) return v.multiply(pi(), mc).divide(BigDecimal.valueOf(180), mc);
        if (angleMode == Expr.GRAD) return v.multiply(pi(), mc).divide(BigDecimal.valueOf(200), mc);
        return v;
    }

    private BigDecimal fromRad(BigDecimal v) {
        if (angleMode == Expr.DEG) return v.multiply(BigDecimal.valueOf(180), mc).divide(pi(), mc);
        if (angleMode == Expr.GRAD) return v.multiply(BigDecimal.valueOf(200), mc).divide(pi(), mc);
        return v;
    }

    private BigDecimal primary() {
        ws();
        if (pos >= src.length()) throw new Expr.EvalException("syntax");
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            BigDecimal v = expr();
            if (!match(')')) throw new Expr.EvalException("syntax");
            return v;
        }
        if (c == '√') { pos++; return sqrt(arg()); }
        if (c == '∛') { pos++; return cbrt(arg()); }
        if (c == 'π') { pos++; return pi(); }
        if (Character.isDigit(c) || c == '.') return number();

        if (word("sqrt")) return sqrt(arg());
        if (word("cbrt")) return cbrt(arg());
        if (word("exp")) return BigDecimalMath.exp(arg(), mc);
        if (word("ln")) return ln(arg());
        if (word("log2")) return BigDecimalMath.log2(positive(arg()), mc);
        if (word("log")) return BigDecimalMath.log10(positive(arg()), mc);
        if (word("sin")) return snapZero(BigDecimalMath.sin(toRad(arg()), mc));
        if (word("cos")) return snapZero(BigDecimalMath.cos(toRad(arg()), mc));
        if (word("tan")) {
            BigDecimal x = toRad(arg());
            BigDecimal cs = snapZero(BigDecimalMath.cos(x, mc));
            if (cs.signum() == 0) throw new Expr.EvalException("math");
            return snapZero(snapZero(BigDecimalMath.sin(x, mc)).divide(cs, mc));
        }
        if (word("asin")) return fromRad(BigDecimalMath.asin(arg(), mc));
        if (word("acos")) return fromRad(BigDecimalMath.acos(arg(), mc));
        if (word("atan")) return fromRad(BigDecimalMath.atan(arg(), mc));
        if (word("abs")) return arg().abs();
        if (word("fact")) return fact(arg());
        if (word("pi")) return pi();
        if (word("ans")) return ans;
        if (c == 'e' && !(pos + 1 < src.length() && Character.isLetter(src.charAt(pos + 1)))) {
            pos++;
            return BigDecimalMath.e(mc);
        }
        throw new Unsupported();
    }

    private BigDecimal snapZero(BigDecimal v) {
        int digits = mc.getPrecision() - 5;
        if (v.signum() != 0 && v.abs().compareTo(BigDecimal.ONE.movePointLeft(digits + 2)) < 0) return BigDecimal.ZERO;
        return v;
    }

    private BigDecimal positive(BigDecimal v) {
        if (v.signum() <= 0) throw new Expr.EvalException("math");
        return v;
    }

    private BigDecimal ln(BigDecimal v) {
        return BigDecimalMath.log(positive(v), mc);
    }

    private BigDecimal sqrt(BigDecimal v) {
        if (v.signum() < 0) throw new Expr.EvalException("math");
        if (v.signum() == 0) return BigDecimal.ZERO;
        return BigDecimalMath.sqrt(v, mc);
    }

    private BigDecimal cbrt(BigDecimal v) {
        if (v.signum() == 0) return BigDecimal.ZERO;
        BigDecimal r = BigDecimalMath.root(BigDecimal.valueOf(3), v.abs(), mc);
        return v.signum() < 0 ? r.negate() : r;
    }

    private BigDecimal number() {
        int start = pos;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'E' || src.charAt(pos) == 'e')) {
            int save = pos;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            } else pos = save;
        }
        try {
            return new BigDecimal(src.substring(start, pos));
        } catch (NumberFormatException ex) {
            throw new Expr.EvalException("syntax");
        }
    }
}
