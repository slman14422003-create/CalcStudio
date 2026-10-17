package com.calc.app.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Parser {

    public static final class ParseException extends RuntimeException {
        public ParseException(String m) { super(m); }
    }

    private static final Set<String> FUNCS = new HashSet<>(Arrays.asList(
            "sin", "cos", "tan", "cot", "sec", "csc", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh", "ln", "log", "log10", "log2", "exp", "sqrt", "abs", "cbrt"));

    private final List<String> tok = new ArrayList<>();
    private int p;

    private Parser(String s) { lex(s); }

    public static Node parse(String s) {
        Parser ps = new Parser(s);
        if (ps.tok.isEmpty()) throw new ParseException("empty");
        Node n = ps.expr();
        if (ps.p != ps.tok.size()) throw new ParseException("trailing " + ps.tok.get(ps.p));
        return n;
    }

    private static String prep(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u00d7': case '\u00b7': case '\u22c5': b.append('*'); break;
                case '\u00f7': b.append('/'); break;
                case '\u2212': case '\u2013': b.append('-'); break;
                case '\u00b2': b.append("^2"); break;
                case '\u00b3': b.append("^3"); break;
                case '\u221a': b.append("sqrt"); break;
                case '\u03c0': b.append("pi"); break;
                case '[': case '{': b.append('('); break;
                case ']': case '}': b.append(')'); break;
                case '\u060c': b.append(','); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    private void lex(String raw) {
        String s = prep(raw);
        int i = 0;
        boolean bar = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int j = i;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                tok.add(s.substring(i, j));
                i = j;
            } else if (Character.isLetter(c) && c < 128) {
                int j = i;
                while (j < s.length() && Character.isLetter(s.charAt(j)) && s.charAt(j) < 128) j++;
                splitWord(s.substring(i, j));
                i = j;
            } else if (c == '|') {
                if (!bar) { tok.add("abs"); tok.add("("); } else tok.add(")");
                bar = !bar;
                i++;
            } else if ("+-*/^()=,".indexOf(c) >= 0) {
                tok.add(String.valueOf(c));
                i++;
            } else throw new ParseException("char " + c);
        }
    }

    private void splitWord(String w) {
        String lw = w.toLowerCase();
        if (FUNCS.contains(lw) || lw.equals("pi")) { tok.add(lw); return; }
        int i = 0;
        while (i < w.length()) {
            boolean matched = false;
            for (int len = Math.min(6, w.length() - i); len >= 2; len--) {
                String sub = w.substring(i, i + len).toLowerCase();
                if (FUNCS.contains(sub) || sub.equals("pi")) {
                    tok.add(sub);
                    i += len;
                    matched = true;
                    break;
                }
            }
            if (!matched) { tok.add(String.valueOf(w.charAt(i))); i++; }
        }
    }

    private String peek() { return p < tok.size() ? tok.get(p) : null; }

    private boolean isFunc(String t) { return t != null && FUNCS.contains(t); }

    public static boolean knownWord(String w) {
        String l = w.toLowerCase();
        return FUNCS.contains(l) || l.equals("pi");
    }

    private static boolean isNumTok(String t) {
        return t != null && (Character.isDigit(t.charAt(0)) || t.charAt(0) == '.');
    }

    private static boolean isIdent(String t) {
        return t != null && Character.isLetter(t.charAt(0));
    }

    private Node expr() {
        Node left = term();
        while (true) {
            String t = peek();
            if ("+".equals(t)) { p++; left = Node.add(left, term()); }
            else if ("-".equals(t)) { p++; left = Node.sub(left, term()); }
            else return left;
        }
    }

    private Node term() {
        Node left = unary();
        while (true) {
            String t = peek();
            if ("*".equals(t)) { p++; left = Node.mul(left, unary()); }
            else if ("/".equals(t)) { p++; left = Node.div(left, unary()); }
            else if ("(".equals(t) || isIdent(t)) { left = Node.mul(left, power()); }
            else return left;
        }
    }

    private Node unary() {
        String t = peek();
        if ("-".equals(t)) { p++; return Node.neg(unary()); }
        if ("+".equals(t)) { p++; return unary(); }
        return power();
    }

    private Node power() {
        Node base = primary();
        if ("^".equals(peek())) {
            p++;
            Node ex;
            if ("-".equals(peek())) { p++; ex = Node.neg(power()); }
            else if ("+".equals(peek())) { p++; ex = power(); }
            else ex = power();
            return Node.pow(base, ex);
        }
        return base;
    }

    private Node primary() {
        String t = peek();
        if (t == null) throw new ParseException("eof");
        if (isNumTok(t)) {
            p++;
            try { return Node.num(Double.parseDouble(t)); } catch (NumberFormatException e) { throw new ParseException("num"); }
        }
        if ("(".equals(t)) {
            p++;
            Node n = expr();
            if (!")".equals(peek())) throw new ParseException("paren");
            p++;
            return n;
        }
        if (isFunc(t)) {
            p++;
            Node pw = null;
            if ("^".equals(peek())) {
                p++;
                pw = "-".equals(peek()) ? signedPow() : power();
            }
            Node arg;
            if ("(".equals(peek())) {
                p++;
                arg = expr();
                if (!")".equals(peek())) throw new ParseException("paren");
                p++;
            } else arg = power();
            Node f = makeFn(t, arg);
            return pw == null ? f : Node.pow(f, pw);
        }
        if (isIdent(t)) {
            p++;
            if (t.equals("pi")) return Node.var("pi");
            return Node.var(t);
        }
        throw new ParseException("unexpected " + t);
    }

    private Node signedPow() {
        p++;
        return Node.neg(power());
    }

    private static Node makeFn(String name, Node a) {
        switch (name) {
            case "sqrt": return Node.pow(a, 0.5);
            case "cbrt": return Node.pow(a, Node.div(Node.num(1), Node.num(3)));
            case "exp": return Node.exp(a);
            case "log": case "log10": return Node.div(Node.fn("ln", a), Node.fn("ln", Node.num(10)));
            case "log2": return Node.div(Node.fn("ln", a), Node.fn("ln", Node.num(2)));
            case "arcsin": return Node.fn("asin", a);
            case "arccos": return Node.fn("acos", a);
            case "arctan": return Node.fn("atan", a);
            default: return Node.fn(name, a);
        }
    }

    public static List<Node> parseList(String s) {
        List<Node> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if ((c == ',' || c == ';') && depth == 0) {
                out.add(parse(s.substring(start, i)));
                start = i + 1;
            }
        }
        out.add(parse(s.substring(start)));
        return out;
    }
}
