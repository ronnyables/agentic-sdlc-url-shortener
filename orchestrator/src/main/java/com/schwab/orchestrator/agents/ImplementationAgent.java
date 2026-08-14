package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces an implementation summary/change-plan. This prototype does not
 * auto-write production code (see docs/04-testing-and-tradeoffs.md for why);
 * instead it emits a structured, file-level change plan that a human engineer
 * (or a follow-up code-writing agent) would execute.
 *
 * When {@code simulateSecretLeakOnFirstAttempt} is true, the first attempt
 * deliberately emits a change plan that contains what looks like a hardcoded
 * credential, so the SecurityGuardrail has something real to catch - this is
 * used by the ambiguous-requirement scenario to demonstrate guardrail-driven
 * retry-and-recover behavior end-to-end, not just declare that guardrails exist.
 */
public final class ImplementationAgent implements StageExecutor {

    private final boolean simulateSecretLeakOnFirstAttempt;

    public ImplementationAgent(boolean simulateSecretLeakOnFirstAttempt) {
        this.simulateSecretLeakOnFirstAttempt = simulateSecretLeakOnFirstAttempt;
    }

    public ImplementationAgent() {
        this(false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) {
        List<String> apiChanges = context.getArtifact("design.apiChanges");
        if (apiChanges == null) apiChanges = List.of();
        List<String> impactedFiles = context.getArtifact("codebase-reasoning.impactedFiles");

        StringBuilder changePlan = new StringBuilder();
        if (simulateSecretLeakOnFirstAttempt && attemptNumber == 1) {
            // Deliberately non-compliant draft - a real agent might paste a sample config
            // that includes a placeholder credential without noticing. The SecurityGuardrail
            // is expected to catch exactly this on the exit gate.
            changePlan.append("// draft config snippet for new integration\n")
                    .append("api_key = \"sk_live_EXAMPLE_1234567890\"\n")
                    .append("Add caching layer for analytics rollups.");
        } else {
            changePlan.append("Config values are sourced from environment variables (no literals). ")
                    .append("Add caching layer for analytics rollups using Spring's @Cacheable/@CacheEvict abstraction already in place.");
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("changePlanSummary", changePlan.toString());
        artifacts.put("filesToChange", impactedFiles != null ? impactedFiles : apiChanges);
        artifacts.put("estimatedRiskLevel", (impactedFiles != null && impactedFiles.size() > 5) ? "medium" : "low");

        context.recordDecision("implementation", "agent:ImplementationAgent", "CHANGE_PLAN_DRAFTED",
                "attempt " + attemptNumber + "; risk=" + artifacts.get("estimatedRiskLevel"));

        return StageResult.ok("Change plan drafted (attempt " + attemptNumber + ")", artifacts);
    }
}
