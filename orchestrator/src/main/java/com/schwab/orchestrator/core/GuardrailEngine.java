package com.schwab.orchestrator.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Registry of named guardrails; stages opt in to specific guardrails by name via StageDefinition.guardrails(...). */
public final class GuardrailEngine {

    private final Map<String, Guardrail> registry = new LinkedHashMap<>();

    public GuardrailEngine register(Guardrail guardrail) {
        registry.put(guardrail.name(), guardrail);
        return this;
    }

    /** Runs every guardrail the stage opted into; returns all results (both pass and block) for full audit visibility. */
    public List<GuardrailResult> evaluate(WorkflowContext context, StageDefinition stage, StageResult result) {
        List<GuardrailResult> results = new ArrayList<>();
        for (String name : stage.getGuardrailNames()) {
            Guardrail guardrail = registry.get(name);
            if (guardrail == null) {
                results.add(GuardrailResult.block(name, "Guardrail '" + name + "' is not registered"));
                continue;
            }
            results.add(guardrail.check(context, stage, result));
        }
        return results;
    }
}
