package com.calc.app.solver;

public final class CalcToolBridge {
    private CalcToolBridge() {}

    public static String digits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') b.append((char) ('0' + (ch - '\u0660')));
            else if (ch >= '\u06f0' && ch <= '\u06f9') b.append((char) ('0' + (ch - '\u06f0')));
            else if (ch == '\u066b') b.append('.');
            else if (ch == '\u066c') continue;
            else if (ch == '\u066a') b.append('%');
            else if (ch == '\u060c' || ch == '\u061b') b.append(ch == '\u060c' ? ',' : ';');
            else b.append(ch);
        }
        return b.toString();
    }
}
