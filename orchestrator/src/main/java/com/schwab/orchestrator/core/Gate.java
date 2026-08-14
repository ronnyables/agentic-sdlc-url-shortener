package com.schwab.orchestrator.core;

/** Entry gate: evaluated before a stage's executor runs. */
@FunctionalInterface
public interface Gate {
    GateResult evaluate(WorkflowContext context);

    Gate ALWAYS_PASS = ctx -> GateResult.pass("no entry precondition");
}
