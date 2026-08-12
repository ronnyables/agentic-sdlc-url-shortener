package com.schwab.orchestrator.core;

/** Exit gate: evaluated after a stage's executor produces a successful StageResult. */
@FunctionalInterface
public interface ExitGate {
    GateResult evaluate(WorkflowContext context, StageResult result);

    ExitGate ALWAYS_PASS = (ctx, result) -> GateResult.pass("no exit criteria");
}
