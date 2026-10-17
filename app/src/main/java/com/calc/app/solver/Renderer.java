package com.calc.app.solver;

public final class Renderer {

    private Renderer() {}

    private static String iso(String s, boolean rtl) {
        if (!rtl || s == null || s.isEmpty()) return s;
        return "\u2066" + s + "\u2069";
    }

    private static String flat(String s) {
        return s.replace("\n", "  ;  ");
    }

    private static String line(String s, boolean rtl) {
        return rtl ? "\u200f" + s : s;
    }

    private static Object[] wrap(String[] args, boolean rtl) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = iso(flat(args[i]), rtl);
        return out;
    }

    public static String render(Solution s, Msgs m, boolean rtl) {
        StringBuilder sb = new StringBuilder();
        if (s.failed) {
            return m.get(s.noteKey == null ? "err_unsupported" : s.noteKey);
        }
        if (!s.steps.isEmpty()) {
            sb.append(m.get("lbl_steps")).append("\n");
            int i = 1;
            for (Step st : s.steps) {
                sb.append(line(i + ". " + m.get(st.key, wrap(st.args, rtl)), rtl)).append("\n");
                i++;
            }
            sb.append("\n");
        }
        sb.append(m.get(s.answerKey == null ? "ans_solutions" : s.answerKey, wrap(s.answerArgs, rtl)));
        sb.append("\n");
        for (String a : s.answers) sb.append(line("  " + (a.contains("\n") ? "\n" + a : iso(a, rtl)), rtl)).append("\n");
        if (s.verifyKey != null) {
            sb.append("\n");
            sb.append(s.verified ? "\u2713 " : "\u26a0 ");
            sb.append(m.get(s.verified ? "lbl_verified" : "lbl_unverified")).append(": ");
            sb.append(m.get(s.verifyKey, wrap(s.verifyArgs, rtl)));
            sb.append("\n");
        }
        if (s.noteKey != null) sb.append("\n").append(m.get(s.noteKey)).append("\n");
        return sb.toString().trim();
    }
}
