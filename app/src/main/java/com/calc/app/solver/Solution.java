package com.calc.app.solver;

import java.util.ArrayList;
import java.util.List;

public final class Solution {
    public final String kind;
    public final String problem;
    public final List<Step> steps = new ArrayList<>();
    public final List<String> answers = new ArrayList<>();
    public String answerKey;
    public String[] answerArgs = new String[0];
    public String verifyKey;
    public String[] verifyArgs = new String[0];
    public boolean verified;
    public boolean failed;
    public String noteKey;

    public Solution(String kind, String problem) {
        this.kind = kind;
        this.problem = problem;
    }

    public static Solution fail(String kind, String problem, String noteKey) {
        Solution s = new Solution(kind, problem);
        s.failed = true;
        s.noteKey = noteKey;
        return s;
    }

    public Solution step(String key, String... args) {
        steps.add(new Step(key, args));
        return this;
    }

    public Solution answer(String key, String... args) {
        answerKey = key;
        answerArgs = args;
        return this;
    }

    public Solution verify(boolean ok, String key, String... args) {
        verified = ok;
        verifyKey = key;
        verifyArgs = args;
        return this;
    }
}
