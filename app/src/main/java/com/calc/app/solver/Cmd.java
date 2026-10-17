package com.calc.app.solver;

import java.util.ArrayList;
import java.util.List;

public final class Cmd {
    public String op;
    public String expr = "";
    public String var;
    public String a;
    public String b;
    public int side;
    public final List<String> list = new ArrayList<>();
    public final List<String> nums = new ArrayList<>();
    public String raw = "";

    public Cmd(String op) { this.op = op; }

    @Override
    public String toString() { return op + " | " + expr + " | " + var + " | " + a + " | " + b + " | " + list + " | " + nums; }
}
