package com.calc.app.solver;

import java.math.BigInteger;

public final class Frac implements Comparable<Frac> {
    public static final Frac ZERO = new Frac(BigInteger.ZERO, BigInteger.ONE);
    public static final Frac ONE = new Frac(BigInteger.ONE, BigInteger.ONE);

    public final BigInteger n;
    public final BigInteger d;

    private Frac(BigInteger n, BigInteger d) {
        if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
        BigInteger g = n.gcd(d);
        if (g.signum() != 0 && !g.equals(BigInteger.ONE)) { n = n.divide(g); d = d.divide(g); }
        this.n = n;
        this.d = d;
    }

    public static Frac of(long v) { return new Frac(BigInteger.valueOf(v), BigInteger.ONE); }

    public static Frac of(long a, long b) { return new Frac(BigInteger.valueOf(a), BigInteger.valueOf(b)); }

    public static Frac fromDouble(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-12) return of(Math.round(v));
        long[] r = Node.ratio(v);
        if (r == null) throw new ArithmeticException("not rational");
        return of(v < 0 ? -r[0] : r[0], r[1]);
    }

    public Frac add(Frac o) { return new Frac(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d)); }
    public Frac sub(Frac o) { return new Frac(n.multiply(o.d).subtract(o.n.multiply(d)), d.multiply(o.d)); }
    public Frac mul(Frac o) { return new Frac(n.multiply(o.n), d.multiply(o.d)); }
    public Frac div(Frac o) { return new Frac(n.multiply(o.d), d.multiply(o.n)); }
    public Frac neg() { return new Frac(n.negate(), d); }
    public boolean isZero() { return n.signum() == 0; }
    public boolean isOne() { return n.equals(BigInteger.ONE) && d.equals(BigInteger.ONE); }
    public boolean isInt() { return d.equals(BigInteger.ONE); }
    public int signum() { return n.signum(); }
    public double toDouble() { return new java.math.BigDecimal(n).divide(new java.math.BigDecimal(d), java.math.MathContext.DECIMAL64).doubleValue(); }

    @Override
    public int compareTo(Frac o) { return n.multiply(o.d).compareTo(o.n.multiply(d)); }

    @Override
    public boolean equals(Object o) { return o instanceof Frac && ((Frac) o).n.equals(n) && ((Frac) o).d.equals(d); }

    @Override
    public int hashCode() { return n.hashCode() * 31 + d.hashCode(); }

    @Override
    public String toString() { return isInt() ? n.toString() : n + "/" + d; }
}
