package com.schwab.orchestrator.core;

/** A named policy check (security / compliance / change-control) evaluated at a stage's exit gate. */
public interface Guardrail {
    String name();
    GuardrailResult check(WorkflowContext context, StageDefinition stage, StageResult result);
}
