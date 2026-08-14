package com.schwab.orchestrator.guardrails;

import com.schwab.orchestrator.core.*;

import java.util.List;

/** Verifies required upstream artifacts exist before a compliance-sensitive stage (e.g. release readiness) can pass. */
public final class ComplianceGuardrail implements Guardrail {

    private final List<String> requiredArtifactKeys;

    public ComplianceGuardrail(String... requiredArtifactKeys) {
        this.requiredArtifactKeys = List.of(requiredArtifactKeys);
    }

    @Override
    public String name() { return "compliance"; }

    @Override
    public GuardrailResult check(WorkflowContext context, StageDefinition stage, StageResult result) {
        for (String key : requiredArtifactKeys) {
            Object value = context.getArtifact(key);
            if (value == null || Boolean.FALSE.equals(value)) {
                return GuardrailResult.block(name(), "required compliance artifact missing or false: " + key);
            }
        }
        return GuardrailResult.pass(name());
    }
}
