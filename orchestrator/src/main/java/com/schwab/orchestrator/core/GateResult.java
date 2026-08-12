package com.schwab.orchestrator.core;

public final class GateResult {
    private final boolean passed;
    private final String reason;

    private GateResult(boolean passed, String reason) {
        this.passed = passed;
        this.reason = reason;
    }

    public static GateResult pass() { return new GateResult(true, "ok"); }
    public static GateResult pass(String reason) { return new GateResult(true, reason); }
    public static GateResult block(String reason) { return new GateResult(false, reason); }

    public boolean isPassed() { return passed; }
    public String getReason() { return reason; }
}
