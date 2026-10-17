package com.calc.app.solver;

public final class Step {
    public final String key;
    public final String[] args;

    public Step(String key, String... args) {
        this.key = key;
        this.args = args;
    }
}
