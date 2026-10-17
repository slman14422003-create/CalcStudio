package com.calc.app.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Router {

    private Router() {}

    private static final String[] DIFF = {"derivative", "differentiate", "d/dx", "diff(", "\u0645\u0634\u062a\u0642\u0647", "\u0627\u0634\u062a\u0642", "\u062a\u0641\u0627\u0636\u0644", "\u0627\u0644\u0645\u0634\u062a\u0642\u0647"};
    private static final String[] INT = {"integral", "integrate", "\u222b", "\u062a\u0643\u0627\u0645\u0644", "\u0643\u0627\u0645\u0644"};
    private static final String[] LIM = {"limit", "lim ", "lim(", "\u0646\u0647\u0627\u064a\u0647"};
    private static final String[] SOLVE = {"solve", "find x", "root", "\u062d\u0644", "\u0627\u0648\u062c\u062f \u0642\u064a\u0645\u0647", "\u062c\u0630\u0648\u0631", "\u0627\u0644\u062c\u0630\u0631"};
    private static final String[] SIMPL = {"simplify", "\u0628\u0633\u0637", "\u0627\u062e\u062a\u0635\u0631"};
    private static final String[] EXPAND = {"expand", "\u0627\u0646\u0634\u0631", "\u0641\u0643"};
    private static final String[] FACTOR = {"factor", "\u062d\u0644\u0644", "\u062a\u062d\u0644\u064a\u0644"};
    private static final String[] DET = {"determinant", "det", "\u0645\u062d\u062f\u062f"};
    private static final String[] INV = {"inverse", "\u0645\u0639\u0643\u0648\u0633"};
    private static final String[] TRANS = {"transpose", "\u0645\u0646\u0642\u0648\u0644"};
    private static final String[] RANK = {"rank", "\u0631\u062a\u0628\u0647"};
    private static final String[] EIGEN = {"eigen", "\u0630\u0627\u062a\u064a", "\u0627\u0644\u0630\u0627\u062a\u064a\u0647"};
    private static final String[] MEAN = {"mean", "average", "median", "std", "variance", "deviation", "statistics", "stats", "mode", "\u0645\u062a\u0648\u0633\u0637", "\u0648\u0633\u064a\u0637", "\u0627\u0646\u062d\u0631\u0627\u0641", "\u062a\u0628\u0627\u064a\u0646", "\u0627\u062d\u0635\u0627\u0621", "\u0645\u0646\u0648\u0627\u0644"};
    private static final String[] REG = {"regression", "\u0627\u0646\u062d\u062f\u0627\u0631", "correlation", "\u0627\u0631\u062a\u0628\u0627\u0637"};
    private static final String[] NCR = {"ncr", "combination", "choose", "\u062a\u0648\u0627\u0641\u064a\u0642"};
    private static final String[] NPR = {"npr", "permutation", "\u062a\u0628\u0627\u062f\u064a\u0644"};
    private static final String[] FACT = {"factorial", "\u0639\u0627\u0645\u0644\u064a"};
    private static final String[] ODE = {"differential equation", "ode", "\u0645\u0639\u0627\u062f\u0644\u0647 \u062a\u0641\u0627\u0636\u0644\u064a\u0647", "\u062a\u0641\u0627\u0636\u0644\u064a\u0647"};

    private static final Set<String> FILLER = new HashSet<>(Arrays.asList(
            "of", "the", "for", "find", "calculate", "compute", "solve", "what", "is", "evaluate", "function", "expression",
            "equation", "derivative", "differentiate", "integral", "integrate", "limit", "lim", "simplify", "expand", "factor",
            "please", "value", "to", "with", "respect", "wrt", "d/dx", "determinant", "det", "inverse", "transpose", "rank",
            "eigenvalues", "eigen", "mean", "average", "median", "of:", "a", "an", "system", "equations", "x", "and",
            "\u0627\u0648\u062c\u062f", "\u062c\u062f", "\u0627\u062d\u0633\u0628", "\u062d\u0644", "\u0645\u0634\u062a\u0642\u0647", "\u0627\u0634\u062a\u0642", "\u062a\u0643\u0627\u0645\u0644", "\u0643\u0627\u0645\u0644",
            "\u0646\u0647\u0627\u064a\u0647", "\u0627\u0644\u062f\u0627\u0644\u0647", "\u0644\u0644\u062f\u0627\u0644\u0647", "\u0644\u062f\u0627\u0644\u0647", "\u0644", "\u0644\u0644", "\u0645\u0627", "\u0647\u064a", "\u0647\u0648", "\u0642\u064a\u0645\u0647",
            "\u0628\u0633\u0637", "\u0627\u0646\u0634\u0631", "\u062d\u0644\u0644", "\u0627\u0644\u0645\u0642\u062f\u0627\u0631", "\u0627\u0644\u062a\u0639\u0628\u064a\u0631", "\u0627\u0644\u0645\u0639\u0627\u062f\u0644\u0647", "\u0645\u0639\u0627\u062f\u0644\u0647", "\u0646\u0638\u0627\u0645",
            "\u0627\u0644\u0645\u0634\u062a\u0642\u0647", "\u0627\u0644\u0645\u062d\u062f\u062f", "\u0645\u062d\u062f\u062f", "\u0645\u0639\u0643\u0648\u0633", "\u0645\u0646\u0642\u0648\u0644", "\u0631\u062a\u0628\u0647", "\u0627\u0644\u0645\u0635\u0641\u0648\u0641\u0647", "\u0645\u0635\u0641\u0648\u0641\u0647",
            "\u0627\u0644\u062a\u0643\u0627\u0645\u0644", "\u0627\u0644\u0646\u0647\u0627\u064a\u0647", "\u0644\u0644\u0645\u0639\u0627\u062f\u0644\u0647", "\u0644\u0644\u0645\u0635\u0641\u0648\u0641\u0647", "\u0644\u0644\u0645\u0642\u062f\u0627\u0631", "\u0627\u0644\u0627\u062a\u064a\u0647", "\u0627\u0644\u062a\u0627\u0644\u064a\u0647", "\u062a\u0627\u0644\u064a\u0647"));

    private static String strip(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u064b' && c <= '\u065f' || c == '\u0640' || c == '\u0670') continue;
            b.append(c);
        }
        return b.toString();
    }

    private static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u0623': case '\u0625': case '\u0622': b.append('\u0627'); break;
                case '\u0649': b.append('\u064a'); break;
                case '\u0629': b.append('\u0647'); break;
                case '\u061f': b.append('?'); break;
                default: b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }

    private static boolean has(String n, String[] kws) {
        for (String k : kws) if (n.contains(k)) return true;
        return false;
    }

    private static int lastIndex(String n, String[] kws) {
        int best = -1;
        int len = 0;
        for (String k : kws) {
            int i = n.lastIndexOf(k);
            if (i >= 0 && i + k.length() > best + len) { best = i; len = k.length(); }
        }
        return best < 0 ? -1 : best + len;
    }

    private static String stripFiller(String t) {
        String s = t.trim();
        while (true) {
            String[] parts = s.split("\\s+", 2);
            if (parts.length == 0 || parts[0].isEmpty()) return s.trim();
            String w = norm(parts[0]).replaceAll("[:?\\u060c,]+$", "");
            if (w.equals("x") && parts.length > 1 && !parts[1].trim().startsWith("=")) { }
            if (FILLER.contains(w) && !w.equals("x") && !w.equals("a")) {
                s = parts.length > 1 ? parts[1].trim() : "";
                continue;
            }
            if (w.length() > 2 && w.startsWith("\u0627\u0644") && FILLER.contains(w)) { s = parts.length > 1 ? parts[1].trim() : ""; continue; }
            return s;
        }
    }

    private static String cleanExpr(String e) {
        String s = e.trim().replaceAll("^[:\\s]+", "").replaceAll("[?\\u061f.,\\u060c:]+$", "").trim();
        s = s.replaceFirst("^(?i)[a-z]\\s*\\(\\s*[a-z]\\s*\\)\\s*=\\s*", "");
        s = s.replaceFirst("^(?i)y\\s*=\\s*", "");
        return s.trim();
    }

    private static List<String> groups(String t) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '[') { if (depth == 0) start = i; depth++; }
            else if (c == ']') { depth--; if (depth == 0 && start >= 0) { out.add(t.substring(start, i + 1)); start = -1; } }
        }
        return out;
    }

    private static List<String> numbersIn(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:/\\d+)?").matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    private static boolean looksLikeMathLine(String s) {
        return s.matches(".*[0-9a-zA-Z].*") && !s.matches(".*[\\u0600-\\u06ff]{3,}.*");
    }

    public static Cmd understand(String raw) {
        if (raw == null) return null;
        String t = strip(CalcToolBridge.digits(raw)).trim();
        if (t.isEmpty()) return null;
        t = t.replace("\u2192", "->").replace("\u221e", "inf").replace("\u2264", "<=").replace("\u2265", ">=");
        String n = norm(t);
        Cmd c = ode(t, n);
        if (c == null) c = matrix(t, n);
        if (c == null) c = stats(t, n);
        if (c == null) c = comb(t, n);
        if (c == null) c = limit(t, n);
        if (c == null) c = integral(t, n);
        if (c == null) c = derivative(t, n);
        if (c == null) c = algebraic(t, n);
        if (c == null) c = equations(t, n);
        if (c != null && wordy(c)) return null;
        if (c != null) c.raw = raw;
        return c;
    }

    private static boolean wordy(Cmd c) {
        if (c.op.equals("ODE")) return false;
        List<String> all = new ArrayList<>();
        all.add(c.expr);
        if (c.op.equals("SYSTEM")) all.addAll(c.list);
        for (String s : all) {
            Matcher m = Pattern.compile("[A-Za-z]{3,}").matcher(s == null ? "" : s);
            while (m.find()) if (!Parser.knownWord(m.group())) return true;
        }
        return false;
    }

    private static Cmd ode(String t, String n) {
        if (!t.contains("'") && !has(n, ODE)) return null;
        if (!t.contains("'")) return null;
        String e = t.replace("y''", "P").replace("y'", "Q").replace("\u2032\u2032", "").replaceAll("(?i)y\\s*''", "P");
        int eq = e.indexOf('=');
        if (eq < 0) return null;
        Cmd c = new Cmd("ODE");
        c.expr = cleanOde(e);
        return c;
    }

    private static String cleanOde(String e) {
        String s = e;
        for (String k : new String[]{"solve", "find", "differential equation"}) s = s.replaceAll("(?i)" + k, " ");
        s = s.replaceAll("[\\u0600-\\u06ff]+", " ").trim();
        return s.replaceAll("[?.]+$", "").trim();
    }

    private static Cmd matrix(String t, String n) {
        List<String> g = groups(t);
        boolean kw = has(n, DET) || has(n, INV) || has(n, TRANS) || has(n, RANK) || has(n, EIGEN) || n.contains("\u0645\u0635\u0641\u0648\u0641\u0647") || n.contains("matrix");
        if (g.isEmpty() && !(kw && t.contains(";"))) return null;
        Cmd c;
        if (has(n, DET) && !n.contains("\u0645\u062d\u062f\u062f\u0627\u062a")) c = new Cmd("DET");
        else if (has(n, INV)) c = new Cmd("INV");
        else if (has(n, TRANS)) c = new Cmd("TRANS");
        else if (has(n, EIGEN)) c = new Cmd("EIGEN");
        else if (has(n, RANK)) c = new Cmd("RANK");
        else if (g.size() >= 2) {
            String between = t.substring(t.indexOf(g.get(0)) + g.get(0).length(), t.indexOf(g.get(1), t.indexOf(g.get(0)) + g.get(0).length()));
            if (between.contains("+")) c = new Cmd("MADD");
            else if (between.contains("-")) c = new Cmd("MSUB");
            else c = new Cmd("MMUL");
        } else c = new Cmd("DET");
        if (!g.isEmpty()) {
            c.list.addAll(g);
            return c;
        }
        int k = Math.max(lastIndex(n, DET), Math.max(lastIndex(n, INV), Math.max(lastIndex(n, TRANS), Math.max(lastIndex(n, RANK), lastIndex(n, EIGEN)))));
        String body = k >= 0 ? t.substring(k) : t;
        body = stripFiller(body);
        c.list.add(body);
        return c;
    }

    private static Cmd stats(String t, String n) {
        boolean reg = has(n, REG);
        if (!reg && !has(n, MEAN)) return null;
        if (n.contains("normal") || n.contains("binom")) return null;
        Cmd c = new Cmd(reg ? "REG" : "STATS");
        if (reg) {
            String[] parts = t.split("[;|]|\\band\\b|\\s\\u0648\\s");
            for (String p : parts) { List<String> nums = numbersIn(p); if (nums.size() >= 2) c.list.add(String.join(",", nums)); }
            if (c.list.size() < 2) return null;
            return c;
        }
        List<String> nums = numbersIn(t);
        if (nums.size() < 2) return null;
        c.list.add(String.join(",", nums));
        return c;
    }

    private static Cmd comb(String t, String n) {
        Matcher m = Pattern.compile("(?i)(?:n?c)\\s*\\(\\s*(\\d+)\\s*[,;]\\s*(\\d+)\\s*\\)").matcher(t);
        if (m.find() && !n.contains("lim")) { Cmd c = new Cmd("NCR"); c.nums.add(m.group(1)); c.nums.add(m.group(2)); return c; }
        m = Pattern.compile("(?i)(?:n?p)\\s*\\(\\s*(\\d+)\\s*[,;]\\s*(\\d+)\\s*\\)").matcher(t);
        if (m.find() && has(n, NPR)) { Cmd c = new Cmd("NPR"); c.nums.add(m.group(1)); c.nums.add(m.group(2)); return c; }
        m = Pattern.compile("(?i)^\\s*(\\d+)\\s*([cp])\\s*(\\d+)\\s*$").matcher(t);
        if (m.find()) { Cmd c = new Cmd(m.group(2).equalsIgnoreCase("c") ? "NCR" : "NPR"); c.nums.add(m.group(1)); c.nums.add(m.group(3)); return c; }
        if (has(n, NCR) || has(n, NPR)) {
            List<String> nums = numbersIn(t);
            if (nums.size() >= 2) { Cmd c = new Cmd(has(n, NCR) ? "NCR" : "NPR"); c.nums.add(nums.get(0)); c.nums.add(nums.get(1)); return c; }
        }
        m = Pattern.compile("^\\s*(\\d+)\\s*!\\s*$").matcher(t);
        if (m.find()) { Cmd c = new Cmd("FACT"); c.nums.add(m.group(1)); return c; }
        if (has(n, FACT)) {
            List<String> nums = numbersIn(t);
            if (!nums.isEmpty()) { Cmd c = new Cmd("FACT"); c.nums.add(nums.get(0)); return c; }
        }
        if (n.contains("normal") || n.contains("\u0637\u0628\u064a\u0639\u064a")) {
            String mu = kv(t, "mean|mu|\u0645\u062a\u0648\u0633\u0637");
            String sd = kv(t, "sd|std|sigma|\u0627\u0646\u062d\u0631\u0627\u0641");
            String x = kv(t, "x");
            if (mu != null && sd != null && x != null) { Cmd c = new Cmd("NORMAL"); c.nums.add(mu); c.nums.add(sd); c.nums.add(x); return c; }
        }
        if (n.contains("binom") || n.contains("\u062b\u0646\u0627\u0626\u064a")) {
            String nn = kv(t, "n");
            String p = kv(t, "p");
            String k = kv(t, "k|x");
            if (nn != null && p != null && k != null) { Cmd c = new Cmd("BINOM"); c.nums.add(nn); c.nums.add(p); c.nums.add(k); return c; }
        }
        return null;
    }

    private static String kv(String t, String keys) {
        Matcher m = Pattern.compile("(?i)(?:" + keys + ")\\s*[=:]\\s*(-?\\d+(?:\\.\\d+)?)").matcher(t);
        return m.find() ? m.group(1) : null;
    }

    private static Cmd limit(String t, String n) {
        boolean kw = has(n, LIM) || n.startsWith("lim") || n.contains("limit");
        Matcher m = Pattern.compile("(?i)([a-z])\\s*(?:->|=>|\\bto\\b|tends to|approaches|\\u062a\u0624\u0648\u0644 \u0627\u0644\u0649|\\u062a\\u0624\\u0648\\u0644 \\u0625\\u0644\\u0649|\\u062a\\u0642\\u062a\\u0631\\u0628 \\u0645\\u0646|\\u062a\\u0624\\u0648\\u0644 \\u0627\\u0644\\u0649)\\s*([-+]?\\s*[a-z0-9.()/]+(?:\\s*\\^\\s*[-+]?[0-9.]+)?)\\s*([+-])?").matcher(t);
        if (!kw && !m.find()) return null;
        if (kw) { m = Pattern.compile("(?i)([a-z])\\s*(?:->|=>|\\bto\\b|tends to|approaches|\\u062a\\u0624\\u0648\\u0644 \\u0627\\u0644\\u0649|\\u062a\\u0624\\u0648\\u0644 \\u0625\\u0644\\u0649|\\u062a\\u0642\\u062a\\u0631\\u0628 \\u0645\\u0646|\\u062a\\u0624\\u0648\\u0644 \\u0627\\u0644\\u0649)\\s*([-+]?\\s*[a-z0-9.()/]+)\\s*([+-])?").matcher(t); if (!m.find()) return null; }
        else if (!kw) { m = Pattern.compile("(?i)([a-z])\\s*(?:->|=>)\\s*([-+]?\\s*[a-z0-9.()/]+)\\s*([+-])?").matcher(t); if (!m.find()) return null; }
        Cmd c = new Cmd("LIMIT");
        c.var = m.group(1).toLowerCase();
        c.a = m.group(2).replaceAll("\\s+", "");
        if (m.groupCount() >= 3 && m.group(3) != null) c.side = m.group(3).equals("+") ? 1 : -1;
        String rest = t.substring(0, m.start()) + " " + t.substring(m.end());
        String n2 = norm(rest);
        int k = lastIndex(n2, new String[]{"limit", "lim", "\u0646\u0647\u0627\u064a\u0647"});
        if (k >= 0) rest = rest.substring(k);
        rest = rest.replaceAll("(?i)\\b(as|when|where|at)\\b", " ").replaceAll("\u0639\u0646\u062f\u0645\u0627", " ").replaceAll("\u0639\u0646\u062f", " ");
        c.expr = cleanExpr(stripFiller(rest));
        if (c.expr.isEmpty()) return null;
        return c;
    }

    private static Cmd integral(String t, String n) {
        if (!has(n, INT)) return null;
        Cmd c = new Cmd("INT");
        c.var = "x";
        String rest = t;
        int k = Math.max(lastIndex(n, INT), 0);
        rest = t.substring(k);
        Matcher mb = Pattern.compile("(?i)(?:from|\\u0645\\u0646)\\s+(.+?)\\s+(?:to|\\u0627\\u0644\\u0649|\\u0625\\u0644\\u0649|\\u0644\\u063a\\u0627\\u064a\\u0647|\\u062d\\u062a\\u0649)\\s+([-+]?[a-z0-9.()/^]+)\\s*$").matcher(rest);
        Matcher mb2 = Pattern.compile("(?i)(.+?)\\s+(?:from|\\u0645\\u0646)\\s+([-+]?[a-z0-9.()/^]+)\\s+(?:to|\\u0627\\u0644\\u0649|\\u0625\\u0644\\u0649|\\u062d\\u062a\\u0649)\\s+([-+]?[a-z0-9.()/^]+)\\s*$").matcher(rest);
        Matcher mb3 = Pattern.compile("(?i)(.+?)\\s+(?:between|\\u0628\\u064a\\u0646)\\s+([-+]?[a-z0-9.()/^]+)\\s+(?:and|\\u0648)\\s+([-+]?[a-z0-9.()/^]+)\\s*$").matcher(rest);
        Matcher mb4 = Pattern.compile("^\\s*[_\\[(]?\\s*([-+]?[0-9a-z.()/^]+)\\s*(?:,|\\^|to)\\s*([-+]?[0-9a-z.()/^]+)\\s*[\\])]?\\s+(.+)$").matcher(rest.trim());
        if (mb2.find()) { c.expr = mb2.group(1); c.a = mb2.group(2); c.b = mb2.group(3); c.op = "DINT"; }
        else if (mb3.find()) { c.expr = mb3.group(1); c.a = mb3.group(2); c.b = mb3.group(3); c.op = "DINT"; }
        else c.expr = rest;
        c.expr = cleanExpr(stripFiller(c.expr)).replaceAll("(?i)\\s*d([a-z])\\s*$", "").trim();
        Matcher dv = Pattern.compile("(?i)\\bd([a-z])\\s*$").matcher(rest.trim());
        if (dv.find()) c.var = dv.group(1).toLowerCase();
        else {
            List<String> vs = new ArrayList<>();
            try { Parser.parse(c.expr).vars(vs); } catch (RuntimeException ignored) { }
            if (!vs.isEmpty() && !vs.contains("x")) c.var = vs.get(0);
        }
        if (c.expr.isEmpty()) return null;
        return c;
    }

    private static Cmd derivative(String t, String n) {
        if (!has(n, DIFF)) return null;
        Cmd c = new Cmd("DIFF");
        c.var = "x";
        int k = Math.max(lastIndex(n, DIFF), 0);
        String rest = t.substring(k);
        Matcher w = Pattern.compile("(?i)(?:with respect to|wrt|\\u0628\\u0627\\u0644\\u0646\\u0633\\u0628\\u0647 \\u0644|\\u0628\\u0627\\u0644\\u0646\\u0633\\u0628\\u0629 \\u0644)\\s*([a-z])\\b").matcher(rest);
        if (w.find()) { c.var = w.group(1).toLowerCase(); rest = rest.substring(0, w.start()) + rest.substring(w.end()); }
        Matcher order = Pattern.compile("(?i)(?:second|2nd|\\u0627\\u0644\\u062b\\u0627\\u0646\\u064a\\u0647|\\u062b\\u0627\\u0646\\u064a\\u0647)").matcher(n);
        if (order.find()) c.a = "2";
        Matcher o3 = Pattern.compile("(?i)(?:third|3rd|\\u0627\\u0644\\u062b\\u0627\\u0644\\u062b\\u0647|\\u062b\\u0627\\u0644\\u062b\\u0647)").matcher(n);
        if (o3.find()) c.a = "3";
        rest = rest.replaceAll("(?i)\\b(second|third|2nd|3rd)\\b", " ").replaceAll("\u0627\u0644\u062b\u0627\u0646\u064a\u0629|\u062b\u0627\u0646\u064a\u0629|\u0627\u0644\u062b\u0627\u0646\u064a\u0647|\u0627\u0644\u062b\u0627\u0644\u062b\u0629|\u062b\u0627\u0644\u062b\u0629", " ");
        c.expr = cleanExpr(stripFiller(rest.replaceFirst("(?i)^\\s*d/d[a-z]\\s*", "")));
        if (c.expr.isEmpty()) return null;
        if (w.hitEnd() && false) return null;
        return c;
    }

    private static Cmd algebraic(String t, String n) {
        String op = null;
        String[] kws = null;
        if (has(n, FACTOR)) { op = "FACTOR"; kws = FACTOR; }
        else if (has(n, EXPAND)) { op = "EXPAND"; kws = EXPAND; }
        else if (has(n, SIMPL)) { op = "SIMPLIFY"; kws = SIMPL; }
        if (op == null) return null;
        if (t.contains("=") && !op.equals("FACTOR")) return null;
        Cmd c = new Cmd(op);
        int k = Math.max(lastIndex(n, kws), 0);
        c.expr = cleanExpr(stripFiller(t.substring(k)));
        if (c.expr.isEmpty()) return null;
        return c;
    }

    private static Cmd equations(String t, String n) {
        boolean kw = has(n, SOLVE);
        if (!t.contains("=") && !kw) {
            if (!n.matches(".*[a-z].*")) return null;
            if (n.matches(".*[\\u0600-\\u06ff]{2,}.*")) return null;
            try { Parser.parse(t); } catch (RuntimeException e) { return null; }
            Cmd c = new Cmd("SIMPLIFY");
            c.expr = t;
            return c;
        }
        int k = Math.max(lastIndex(n, SOLVE), 0);
        String rest = t.substring(k);
        rest = rest.replaceAll("(?i)\\b(for [a-z])\\b", " ");
        int eqCount = 0;
        for (char ch : rest.toCharArray()) if (ch == '=') eqCount++;
        List<String> parts = new ArrayList<>();
        if (eqCount >= 2) {
            for (String p : rest.split("[;\\n]|\\band\\b|\\s\\u0648\\s|(?<=[0-9a-z)])\\s*,\\s*(?=[^=,]*=)")) {
                String q = cleanExpr(stripFiller(p));
                if (q.contains("=")) parts.add(q);
            }
        }
        if (parts.size() >= 2) {
            Cmd c = new Cmd("SYSTEM");
            c.list.addAll(parts);
            return c;
        }
        Cmd c = new Cmd("SOLVE");
        String e = cleanExpr(stripFiller(rest));
        Matcher fv = Pattern.compile("(?i)\\bfor\\s+([a-z])\\b").matcher(t);
        if (fv.find()) c.var = fv.group(1).toLowerCase();
        if (e.isEmpty()) return null;
        c.expr = e;
        return c;
    }

    public static Cmd fromModelLine(String line) {
        if (line == null) return null;
        Pattern opPat = Pattern.compile("(?i)\\b(DIFF|INT|DINT|SOLVE|SYSTEM|LIMIT|DET|INV|TRANS|RANK|EIGEN|MMUL|MADD|MSUB|STATS|REG|NCR|NPR|FACT|NORMAL|BINOM|SIMPLIFY|EXPAND|FACTOR|ODE2)\\s*\\|");
        String first = null;
        for (String l : line.split("\\n")) {
            Matcher m = opPat.matcher(l.replace("`", ""));
            if (m.find()) { first = l.replace("`", "").substring(m.start()).trim(); break; }
        }
        if (first == null) return null;
        String[] p = first.split("\\|", -1);
        for (int i = 0; i < p.length; i++) p[i] = p[i].trim();
        String op = p[0].toUpperCase();
        Cmd c = new Cmd(op);
        switch (op) {
            case "DIFF": case "INT": c.expr = at(p, 1); c.var = nz(at(p, 2), "x"); break;
            case "DINT": c.expr = at(p, 1); c.var = nz(at(p, 2), "x"); c.a = at(p, 3); c.b = at(p, 4); break;
            case "SOLVE": c.expr = at(p, 1); c.var = at(p, 2).isEmpty() ? null : at(p, 2); break;
            case "SYSTEM": for (String e : at(p, 1).split(";")) if (!e.trim().isEmpty()) c.list.add(e.trim()); break;
            case "LIMIT": c.expr = at(p, 1); c.var = nz(at(p, 2), "x"); c.a = at(p, 3); c.side = at(p, 4).equals("+") ? 1 : at(p, 4).equals("-") ? -1 : 0; break;
            case "DET": case "INV": case "TRANS": case "RANK": case "EIGEN": c.list.add(at(p, 1)); break;
            case "MMUL": case "MADD": case "MSUB": c.list.add(at(p, 1)); c.list.add(at(p, 2)); break;
            case "STATS": c.list.add(at(p, 1)); break;
            case "REG": c.list.add(at(p, 1)); c.list.add(at(p, 2)); break;
            case "NCR": case "NPR": case "FACT": case "ODE2": case "NORMAL": case "BINOM": for (int i = 1; i < p.length; i++) c.nums.add(p[i]); break;
            case "SIMPLIFY": case "EXPAND": case "FACTOR": c.expr = at(p, 1); break;
            default: return null;
        }
        c.raw = line;
        return c;
    }

    private static String at(String[] p, int i) { return i < p.length ? p[i] : ""; }

    private static String nz(String s, String d) { return s == null || s.isEmpty() ? d : s; }
}
