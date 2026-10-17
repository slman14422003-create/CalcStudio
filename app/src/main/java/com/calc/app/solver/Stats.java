package com.calc.app.solver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

public final class Stats {

    private Stats() {}

    public static double[] parseList(String s) {
        String t = CalcToolBridge.digits(s).replaceAll("[\\[\\]\\(\\)\\{\\}]", " ").trim();
        String[] parts = t.split("[,;\\s]+");
        List<Double> out = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            Node n = Simplify.run(Parser.parse(p));
            if (!(n instanceof Node.Num)) throw new Parser.ParseException("list");
            out.add(((Node.Num) n).v);
        }
        if (out.isEmpty()) throw new Parser.ParseException("empty");
        double[] r = new double[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static String dn(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return Node.fmtNum(Math.rint(v));
        return Nice.dec(v);
    }

    private static String join(double[] d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) { if (i > 0) sb.append(", "); sb.append(Node.fmtNum(d[i])); }
        return sb.toString();
    }

    private static double median(double[] sorted, int lo, int hi) {
        int n = hi - lo;
        if (n % 2 == 1) return sorted[lo + n / 2];
        return (sorted[lo + n / 2 - 1] + sorted[lo + n / 2]) / 2;
    }

    public static Solution describe(double[] data, String problem) {
        Solution s = new Solution("stats", problem);
        int n = data.length;
        double[] sorted = data.clone();
        Arrays.sort(sorted);
        double sum = 0;
        for (double v : data) sum += v;
        double mean = sum / n;
        s.step("st_data", Integer.toString(n), join(data));
        s.step("st_mean", Node.fmtNum(sum), Integer.toString(n), dn(mean));
        s.answers.add("n = " + n);
        s.answers.add("sum = " + dn(sum));
        s.answers.add("mean = " + dn(mean));
        s.step("st_sorted", join(sorted));
        double med = median(sorted, 0, n);
        s.step("st_median", dn(med));
        s.answers.add("median = " + dn(med));
        TreeMap<Double, Integer> freq = new TreeMap<>();
        for (double v : data) freq.merge(v, 1, Integer::sum);
        int best = 0;
        for (int c : freq.values()) best = Math.max(best, c);
        List<String> modes = new ArrayList<>();
        if (best > 1) for (java.util.Map.Entry<Double, Integer> e : freq.entrySet()) if (e.getValue() == best) modes.add(Node.fmtNum(e.getKey()));
        s.answers.add("mode = " + (modes.isEmpty() ? "-" : String.join(", ", modes)));
        s.answers.add("min = " + Node.fmtNum(sorted[0]) + ", max = " + Node.fmtNum(sorted[n - 1]) + ", range = " + dn(sorted[n - 1] - sorted[0]));
        if (n >= 4) {
            int half = n / 2;
            double q1 = median(sorted, 0, half);
            double q3 = median(sorted, n % 2 == 0 ? half : half + 1, n);
            s.answers.add("Q1 = " + dn(q1) + ", Q3 = " + dn(q3) + ", IQR = " + dn(q3 - q1));
        }
        double ss = 0;
        for (double v : data) ss += (v - mean) * (v - mean);
        s.step("st_ss", dn(ss));
        double varP = ss / n;
        s.answers.add("variance (population) = " + dn(varP));
        s.answers.add("std dev (population) = " + Nice.dec(Math.sqrt(varP)));
        boolean ok;
        if (n > 1) {
            double varS = ss / (n - 1);
            s.step("st_var", dn(ss), Integer.toString(n - 1), dn(varS));
            s.answers.add("variance (sample) = " + dn(varS));
            s.answers.add("std dev (sample) = " + Nice.dec(Math.sqrt(varS)));
            double sq = 0;
            for (double v : data) sq += v * v;
            double alt = (sq - n * mean * mean) / (n - 1);
            ok = Math.abs(alt - varS) <= 1e-8 * (1 + Math.abs(varS));
        } else ok = true;
        s.answer("ans_stats");
        s.verify(ok, "v_variance_formula");
        return s;
    }

    public static Solution regression(double[] x, double[] y, String problem) {
        if (x.length != y.length || x.length < 2) return Solution.fail("regression", problem, "err_dims");
        Solution s = new Solution("regression", problem);
        int n = x.length;
        double sx = 0, sy = 0, sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) { sx += x[i]; sy += y[i]; sxy += x[i] * y[i]; sxx += x[i] * x[i]; syy += y[i] * y[i]; }
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-12) return Solution.fail("regression", problem, "err_unsupported");
        double slope = (n * sxy - sx * sy) / den;
        double icpt = (sy - slope * sx) / n;
        double r = (n * sxy - sx * sy) / Math.sqrt(den * (n * syy - sy * sy));
        s.step("st_reg_sums", dn(sx), dn(sy), dn(sxy), dn(sxx));
        s.step("st_reg_slope", dn(slope));
        s.step("st_reg_icpt", dn(icpt));
        s.answers.add("y = " + Nice.dec(slope) + "x + " + Nice.dec(icpt));
        s.answers.add("r = " + Nice.dec(r) + ", r^2 = " + Nice.dec(r * r));
        double xm = sx / n;
        double ym = sy / n;
        double num = 0, d2 = 0;
        for (int i = 0; i < n; i++) { num += (x[i] - xm) * (y[i] - ym); d2 += (x[i] - xm) * (x[i] - xm); }
        s.answer("ans_stats");
        s.verify(Math.abs(num / d2 - slope) < 1e-8 * (1 + Math.abs(slope)), "v_regression_centered");
        return s;
    }

    public static BigInteger fact(int n) {
        BigInteger r = BigInteger.ONE;
        for (int i = 2; i <= n; i++) r = r.multiply(BigInteger.valueOf(i));
        return r;
    }

    public static BigInteger ncr(int n, int k) {
        if (k < 0 || k > n) return BigInteger.ZERO;
        k = Math.min(k, n - k);
        BigInteger r = BigInteger.ONE;
        for (int i = 1; i <= k; i++) r = r.multiply(BigInteger.valueOf(n - k + i)).divide(BigInteger.valueOf(i));
        return r;
    }

    public static Solution comb(String kind, int n, int k, String problem) {
        Solution s = new Solution("comb", problem);
        if (n < 0 || n > 400 || k < 0 || k > n) return Solution.fail("comb", problem, "err_range");
        BigInteger val;
        if (kind.equals("ncr")) {
            s.step("c_ncr", Integer.toString(n), Integer.toString(k), Integer.toString(n - k));
            val = ncr(n, k);
            boolean ok = val.equals(fact(n).divide(fact(k).multiply(fact(n - k))));
            s.verify(ok, "v_factorial_formula");
        } else if (kind.equals("npr")) {
            s.step("c_npr", Integer.toString(n), Integer.toString(k), Integer.toString(n - k));
            val = fact(n).divide(fact(n - k));
            BigInteger alt = BigInteger.ONE;
            for (int i = 0; i < k; i++) alt = alt.multiply(BigInteger.valueOf(n - i));
            s.verify(val.equals(alt), "v_product_formula");
        } else {
            val = fact(n);
            s.step("c_fact", Integer.toString(n));
            BigInteger alt = BigInteger.ONE;
            for (int i = n; i > 1; i--) alt = alt.multiply(BigInteger.valueOf(i));
            s.verify(val.equals(alt), "v_product_formula");
        }
        s.answers.add(val.toString());
        s.answer("ans_value", val.toString());
        return s;
    }

    public static double erf(double x) {
        double t = 1.0 / (1.0 + 0.5 * Math.abs(x));
        double y = 1 - t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806
                + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? y : -y;
    }

    public static double normCdf(double z) { return 0.5 * (1 + erf(z / Math.sqrt(2))); }

    public static Solution normal(double mean, double sd, double x, String problem) {
        if (sd <= 0) return Solution.fail("normal", problem, "err_range");
        Solution s = new Solution("normal", problem);
        double z = (x - mean) / sd;
        s.step("n_z", dn(x), dn(mean), dn(sd), dn(z));
        double p = normCdf(z);
        s.step("n_cdf", Nice.dec(z), Nice.dec(p));
        s.answers.add("P(X < " + Node.fmtNum(x) + ") = " + Nice.dec(p));
        s.answers.add("P(X > " + Node.fmtNum(x) + ") = " + Nice.dec(1 - p));
        s.answer("ans_stats");
        double sym = normCdf(-z);
        s.verify(Math.abs(sym - (1 - p)) < 1e-6, "v_symmetry");
        return s;
    }

    public static Solution binomial(int n, double p, int k, String problem) {
        if (n < 0 || n > 1000 || k < 0 || k > n || p < 0 || p > 1) return Solution.fail("binomial", problem, "err_range");
        Solution s = new Solution("binomial", problem);
        double pmf = ncr(n, k).doubleValue() * Math.pow(p, k) * Math.pow(1 - p, n - k);
        s.step("b_pmf", Integer.toString(n), Integer.toString(k), dn(p), ncr(n, k).toString(), Nice.dec(pmf));
        double cdf = 0;
        for (int i = 0; i <= k; i++) cdf += ncr(n, i).doubleValue() * Math.pow(p, i) * Math.pow(1 - p, n - i);
        double total = 0;
        for (int i = 0; i <= n; i++) total += ncr(n, i).doubleValue() * Math.pow(p, i) * Math.pow(1 - p, n - i);
        s.answers.add("P(X = " + k + ") = " + Nice.dec(pmf));
        s.answers.add("P(X \u2264 " + k + ") = " + Nice.dec(cdf));
        s.answers.add("mean = " + dn(n * p) + ", variance = " + dn(n * p * (1 - p)));
        s.answer("ans_stats");
        s.verify(Math.abs(total - 1) < 1e-9, "v_sum_one");
        return s;
    }
}
