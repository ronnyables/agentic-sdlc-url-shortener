package com.schwab.orchestrator.core;

public final class GuardrailResult {
    private final boolean passed;
    private final String guardrailName;
    private final String message;

    private GuardrailResult(boolean passed, String guardrailName, String message) {
        this.passed = passed;
        this.guardrailName = guardrailName;
        this.message = message;
    }

    public static GuardrailResult pass(String guardrailName) {
        return new GuardrailResult(true, guardrailName, "ok");
    }

    public static GuardrailResult block(String guardrailName, String message) {
        return new GuardrailResult(false, guardrailName, message);
    }

    public boolean isPassed() { return passed; }
    public String getGuardrailName() { return guardrailName; }
    public String getMessage() { return message; }
}
