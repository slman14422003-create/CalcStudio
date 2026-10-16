package com.calc.app;

import java.math.BigInteger;
import java.util.Locale;

public final class ProgrammerEngine {

    public static final int DEC = 10;
    public static final int HEX = 16;
    public static final int OCT = 8;
    public static final int BIN = 2;

    public static final int[] WORD_BITS = {64, 32, 16, 8};

    private ProgrammerEngine() {}

    public static long wrap(long v, int bits) {
        if (bits >= 64) return v;
        int sh = 64 - bits;
        return (v << sh) >> sh;
    }

    public static long mask(long v, int bits) {
        return bits >= 64 ? v : v & ((1L << bits) - 1L);
    }

    public static String format(long v, int base, int bits) {
        long w = wrap(v, bits);
        if (base == DEC) return Long.toString(w);
        return Long.toUnsignedString(mask(w, bits), base).toUpperCase(Locale.ROOT);
    }

    public static long parse(String s, int base, int bits) {
        if (s == null || s.isEmpty() || s.equals("-")) return 0;
        boolean neg = s.startsWith("-");
        String d = neg ? s.substring(1) : s;
        if (d.isEmpty()) return 0;
        long v = Long.parseUnsignedLong(d, base);
        return wrap(neg ? -v : v, bits);
    }

    public static boolean fits(String entry, int base, int bits) {
        if (entry == null || entry.isEmpty() || entry.equals("-")) return true;
        try {
            BigInteger v = new BigInteger(entry, base);
            BigInteger one = BigInteger.ONE;
            if (base == DEC) {
                BigInteger lim = one.shiftLeft(bits - 1);
                return v.compareTo(lim.negate()) >= 0 && v.compareTo(lim.subtract(one)) <= 0;
            }
            return v.signum() >= 0 && v.compareTo(one.shiftLeft(bits).subtract(one)) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String grouped(String digits, int base) {
        int groupSize = (base == BIN || base == HEX) ? 4 : 3;
        char sep = base == DEC ? ',' : ' ';
        boolean neg = digits.startsWith("-");
        if (neg) digits = digits.substring(1);
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            out.append(digits.charAt(i));
            count++;
            if (count % groupSize == 0 && i != 0) out.append(sep);
        }
        out.reverse();
        return (neg ? "-" : "") + out;
    }

    public static long and(long a, long b) { return a & b; }
    public static long or(long a, long b) { return a | b; }
    public static long xor(long a, long b) { return a ^ b; }
    public static long not(long a, int bits) { return wrap(~a, bits); }
    public static long nand(long a, long b, int bits) { return wrap(~(a & b), bits); }
    public static long nor(long a, long b, int bits) { return wrap(~(a | b), bits); }

    public static long shl(long a, long n, int bits) {
        if (n < 0 || n >= bits) return 0;
        return wrap(a << n, bits);
    }

    public static long shr(long a, long n, int bits) {
        if (n < 0 || n >= bits) return a < 0 ? -1L : 0L;
        return wrap(a, bits) >> n;
    }

    public static boolean validDigit(char c, int base) {
        int d;
        if (c >= '0' && c <= '9') d = c - '0';
        else if (c >= 'A' && c <= 'F') d = c - 'A' + 10;
        else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;
        else return false;
        return d < base;
    }
}
