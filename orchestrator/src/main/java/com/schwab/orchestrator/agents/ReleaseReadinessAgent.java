package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Final go/no-go checklist. This stage is deliberately gated behind a human
 * approval checkpoint (configured by the scenario, not by this agent) and the
 * ComplianceGuardrail, so passing this agent's own checklist is necessary but
 * not sufficient - governance still has the final say.
 */
public final class ReleaseReadinessAgent implements StageExecutor {

    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) {
        Boolean testsPassed = context.getArtifact("testing.allTestsPassed", Boolean.FALSE);
        Boolean docsPresent = context.getArtifact("documentation.readmePresent", Boolean.FALSE);

        Map<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("testsPassed", testsPassed);
        checklist.put("docsPresent", docsPresent);
        checklist.put("decisionLineageRecorded", !context.decisionLineage().isEmpty());

        boolean ready = testsPassed && docsPresent;

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("checklist", checklist);
        artifacts.put("readyForRelease", ready);

        context.recordDecision("release-readiness", "agent:ReleaseReadinessAgent",
                ready ? "GO" : "NO_GO", checklist.toString());

        if (!ready) {
            return StageResult.failed("release checklist failed: " + checklist);
        }
        return StageResult.ok("Release checklist passed: " + checklist, artifacts);
    }
}
