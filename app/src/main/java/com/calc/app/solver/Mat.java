package com.calc.app.solver;

import java.util.ArrayList;
import java.util.List;

public final class Mat {
    public final Frac[][] a;
    public final int r;
    public final int c;

    public Mat(Frac[][] a) {
        this.a = a;
        this.r = a.length;
        this.c = a.length == 0 ? 0 : a[0].length;
    }

    public static Mat zero(int r, int c) {
        Frac[][] m = new Frac[r][c];
        for (int i = 0; i < r; i++) for (int j = 0; j < c; j++) m[i][j] = Frac.ZERO;
        return new Mat(m);
    }

    public static Mat identity(int n) {
        Mat m = zero(n, n);
        for (int i = 0; i < n; i++) m.a[i][i] = Frac.ONE;
        return m;
    }

    public Mat copy() {
        Mat m = zero(r, c);
        for (int i = 0; i < r; i++) System.arraycopy(a[i], 0, m.a[i], 0, c);
        return m;
    }

    public static Mat parse(String s) {
        String t = CalcToolBridge.digits(s).trim();
        List<List<Frac>> rows = new ArrayList<>();
        String body = t.replaceAll("\\s*[\\]\\)]\\s*,?\\s*[\\[\\(]\\s*", ";").replaceAll("[\\[\\]\\(\\)\\{\\}]", "");
        for (String row : body.split("[;|\\n]")) {
            row = row.trim();
            if (row.isEmpty()) continue;
            List<Frac> cells = new ArrayList<>();
            for (String cell : row.split("[,\\s]+")) {
                if (cell.isEmpty()) continue;
                Node n = Simplify.run(Parser.parse(cell));
                if (!(n instanceof Node.Num)) throw new Parser.ParseException("cell");
                cells.add(Frac.fromDouble(((Node.Num) n).v));
            }
            rows.add(cells);
        }
        if (rows.isEmpty()) throw new Parser.ParseException("matrix");
        int w = rows.get(0).size();
        Frac[][] m = new Frac[rows.size()][w];
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).size() != w) throw new Parser.ParseException("ragged");
            for (int j = 0; j < w; j++) m[i][j] = rows.get(i).get(j);
        }
        return new Mat(m);
    }

    @Override
    public String toString() {
        String[][] s = new String[r][c];
        int[] wd = new int[c];
        for (int i = 0; i < r; i++) for (int j = 0; j < c; j++) { s[i][j] = a[i][j].toString(); wd[j] = Math.max(wd[j], s[i][j].length()); }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r; i++) {
            sb.append("[ ");
            for (int j = 0; j < c; j++) {
                for (int k = s[i][j].length(); k < wd[j]; k++) sb.append(' ');
                sb.append(s[i][j]);
                if (j < c - 1) sb.append("  ");
            }
            sb.append(" ]");
            if (i < r - 1) sb.append("\n");
        }
        return sb.toString();
    }

    public String inline() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < r; i++) {
            sb.append("[");
            for (int j = 0; j < c; j++) { sb.append(a[i][j]); if (j < c - 1) sb.append(", "); }
            sb.append("]");
            if (i < r - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    public Mat transpose() {
        Mat m = zero(c, r);
        for (int i = 0; i < r; i++) for (int j = 0; j < c; j++) m.a[j][i] = a[i][j];
        return m;
    }

    public Mat mul(Mat o) {
        Mat m = zero(r, o.c);
        for (int i = 0; i < r; i++) for (int j = 0; j < o.c; j++) {
            Frac s = Frac.ZERO;
            for (int k = 0; k < c; k++) s = s.add(a[i][k].mul(o.a[k][j]));
            m.a[i][j] = s;
        }
        return m;
    }

    public Mat add(Mat o, boolean minus) {
        Mat m = zero(r, c);
        for (int i = 0; i < r; i++) for (int j = 0; j < c; j++) m.a[i][j] = minus ? a[i][j].sub(o.a[i][j]) : a[i][j].add(o.a[i][j]);
        return m;
    }

    public Mat scale(Frac k) {
        Mat m = zero(r, c);
        for (int i = 0; i < r; i++) for (int j = 0; j < c; j++) m.a[i][j] = a[i][j].mul(k);
        return m;
    }

    public Frac det() {
        Mat m = copy();
        Frac det = Frac.ONE;
        for (int col = 0; col < r; col++) {
            int p = -1;
            for (int i = col; i < r; i++) if (!m.a[i][col].isZero()) { p = i; break; }
            if (p < 0) return Frac.ZERO;
            if (p != col) { Frac[] t = m.a[p]; m.a[p] = m.a[col]; m.a[col] = t; det = det.neg(); }
            det = det.mul(m.a[col][col]);
            for (int i = col + 1; i < r; i++) {
                Frac f = m.a[i][col].div(m.a[col][col]);
                if (f.isZero()) continue;
                for (int j = col; j < r; j++) m.a[i][j] = m.a[i][j].sub(f.mul(m.a[col][j]));
            }
        }
        return det;
    }

    public Mat minor(int ri, int ci) {
        Mat m = zero(r - 1, c - 1);
        for (int i = 0, ii = 0; i < r; i++) {
            if (i == ri) continue;
            for (int j = 0, jj = 0; j < c; j++) { if (j == ci) continue; m.a[ii][jj++] = a[i][j]; }
            ii++;
        }
        return m;
    }

    public static String rowOp(int target, int src, Frac f) {
        String sgn = f.signum() < 0 ? " + " : " - ";
        Frac af = f.signum() < 0 ? f.neg() : f;
        return "R" + (target + 1) + " \u2190 R" + (target + 1) + sgn + (af.isOne() ? "" : af + "*") + "R" + (src + 1);
    }

    public int rref(List<Step> st, boolean narrate, int stopCols) {
        int row = 0;
        int cols = stopCols <= 0 ? c : stopCols;
        for (int col = 0; col < cols && row < r; col++) {
            int p = -1;
            for (int i = row; i < r; i++) if (!a[i][col].isZero()) { p = i; break; }
            if (p < 0) continue;
            if (p != row) {
                Frac[] t = a[p]; a[p] = a[row]; a[row] = t;
                if (narrate) st.add(new Step("m_swap", "R" + (row + 1), "R" + (p + 1), toString()));
            }
            Frac pv = a[row][col];
            if (!pv.isOne()) {
                for (int j = 0; j < c; j++) a[row][j] = a[row][j].div(pv);
                if (narrate) st.add(new Step("m_scale", "R" + (row + 1) + " \u2190 R" + (row + 1) + " / " + pv, toString()));
            }
            for (int i = 0; i < r; i++) {
                if (i == row || a[i][col].isZero()) continue;
                Frac f = a[i][col];
                for (int j = 0; j < c; j++) a[i][j] = a[i][j].sub(f.mul(a[row][j]));
                if (narrate) st.add(new Step("m_elim", rowOp(i, row, f), toString()));
            }
            row++;
        }
        return row;
    }
}
