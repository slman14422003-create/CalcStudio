package com.calc.app;

public final class ProgrammerEngine {

    public static final int DEC = 10;
    public static final int HEX = 16;
    public static final int OCT = 8;
    public static final int BIN = 2;

    private ProgrammerEngine() {}

    public static long parse(String s, int base) {
        if (s == null || s.isEmpty()) return 0;
        boolean neg = s.startsWith("-");
        if (neg) s = s.substring(1);
        if (s.isEmpty()) return 0;
        long v = Long.parseLong(s, base);
        return neg ? -v : v;
    }

    public static String format(long v, int base) {
        if (base == DEC) return Long.toString(v);
        boolean neg = v < 0;
        String s = Long.toString(Math.abs(v), base).toUpperCase();
        return neg ? "-" + s : s;
    }

    public static String grouped(String digits, int base) {
        int groupSize = base == BIN ? 4 : (base == HEX ? 4 : 3);
        boolean neg = digits.startsWith("-");
        if (neg) digits = digits.substring(1);
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            out.append(digits.charAt(i));
            count++;
            if (count % groupSize == 0 && i != 0) out.append(' ');
        }
        out.reverse();
        return (neg ? "-" : "") + out;
    }

    public static long and(long a, long b) { return a & b; }
    public static long or(long a, long b) { return a | b; }
    public static long xor(long a, long b) { return a ^ b; }
    public static long not(long a) { return ~a; }
    public static long shl(long a, long n) { return a << n; }
    public static long shr(long a, long n) { return a >> n; }
    public static long nand(long a, long b) { return ~(a & b); }
    public static long nor(long a, long b) { return ~(a | b); }

    public static boolean validDigit(char c, int base) {
        int d;
        if (Character.isDigit(c)) d = c - '0';
        else if (c >= 'A' && c <= 'F') d = c - 'A' + 10;
        else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;
        else return false;
        return d < base;
    }
}
