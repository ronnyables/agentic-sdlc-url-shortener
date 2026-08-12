package com.schwab.orchestrator.guardrails;

import com.schwab.orchestrator.core.*;

/** Belt-and-suspenders check: independently confirms a recorded human approval exists for this stage. */
public final class ChangeControlGuardrail implements Guardrail {

    @Override
    public String name() { return "change-control"; }

    @Override
    public GuardrailResult check(WorkflowContext context, StageDefinition stage, StageResult result) {
        if (!stage.isRequiresApproval()) {
            return GuardrailResult.pass(name());
        }
        boolean approved = context.decisionLineage().stream()
                .anyMatch(d -> d.stageId.equals(stage.getId()) && d.decision.startsWith("APPROVED#"));
        return approved
                ? GuardrailResult.pass(name())
                : GuardrailResult.block(name(), "high-impact stage has no recorded human approval decision");
    }
}
