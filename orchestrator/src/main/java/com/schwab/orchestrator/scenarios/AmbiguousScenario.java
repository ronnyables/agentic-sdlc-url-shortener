package com.schwab.orchestrator.scenarios;

import com.schwab.orchestrator.core.*;

import java.util.List;

/**
 * Scenario 3 - Ambiguous: a vague, underspecified requirement.
 * Demonstrates: RequirementsAgent's ambiguity heuristics + documented
 * assumptions, a real guardrail block-then-retry-recover cycle (the
 * SecurityGuardrail catches a simulated leaked credential on attempt 1;
 * attempt 2 fixes it), and dynamic re-planning after a human later clarifies
 * what they actually meant.
 */
public final class AmbiguousScenario {

    public static void main(String[] args) throws Exception {
        String requirement = "Make the analytics better.";

        SdlcOptions opts = new SdlcOptions();
        opts.brownfield = false;
        opts.simulateSecretLeakOnFirstAttempt = true;
        opts.testClassNames = List.of("Base62EncoderTest", "UrlShortenerServiceTest");

        ScenarioRunner.printHeader("SCENARIO 3: AMBIGUOUS - \"Make the analytics better.\"");
        System.out.println("Requirement (verbatim): \"" + requirement + "\"");

        WorkflowGraph graph = WorkflowGraphs.buildSdlcGraph(opts);
        GuardrailEngine guardrails = WorkflowGraphs.buildGuardrails();
        AuditTrail audit = new AuditTrail();
        WorkflowContext context = new WorkflowContext("AMB-" + System.currentTimeMillis(), requirement);

        WorkflowEngine engine = new WorkflowEngine(graph, context, audit, guardrails, EngineConfig.defaults(), "ambiguous");
        try {
            RunState run = engine.advance();
            ScenarioRunner.printStatus(run);

            System.out.println();
            System.out.println(">>> ambiguityDetected=" + context.getArtifact("requirements.ambiguityDetected"));
            System.out.println(">>> clarifying questions the agent would ask a human: "
                    + context.getArtifact("requirements.clarifyingQuestions"));
            System.out.println(">>> assumptions applied instead of blocking: "
                    + context.getArtifact("requirements.assumptions"));
            System.out.println(">>> implementation attempts recorded: "
                    + run.info("implementation").getAttempts() + " (expect 2: attempt 1 blocked by SecurityGuardrail, attempt 2 recovered)");

            if (run.getStatus() == RunStatus.PAUSED_FOR_APPROVAL) {
                System.out.println();
                System.out.println(">>> Engine paused for human approval on release-readiness.");
                System.out.println(">>> Simulating reviewer decision: APPROVE");
                engine.decideApproval("release-readiness", true, "ronny.ables", "Approved initial (assumption-based) release.");
                run = engine.advance();
                ScenarioRunner.printStatus(run);
            }

            ScenarioRunner.printMetrics(run);

            // --- Dynamic re-planning: a human now clarifies what they actually meant ---
            System.out.println();
            System.out.println("-".repeat(90));
            System.out.println("A human reviewer now clarifies the original ambiguous request:");
            String clarification = "Add per-referrer breakdown and daily rollups to the analytics endpoint.";
            System.out.println("  \"" + clarification + "\"");
            context.putArtifact("requirements.humanClarification", clarification);
            engine.invalidate("requirements", "human clarified the originally ambiguous requirement");
            System.out.println(">>> invalidate('requirements') marks requirements + all transitive dependents STALE");

            RunState replanRun = engine.advance();
            ScenarioRunner.printStatus(replanRun);
            if (replanRun.getStatus() == RunStatus.PAUSED_FOR_APPROVAL) {
                System.out.println();
                System.out.println(">>> release-readiness requires a FRESH approval after re-plan (old approval no longer counts)");
                engine.decideApproval("release-readiness", true, "ronny.ables", "Reviewed re-planned design against clarified requirement; approved.");
                replanRun = engine.advance();
                ScenarioRunner.printStatus(replanRun);
            }
            System.out.println();
            System.out.println(">>> design.apiChanges after re-plan: " + context.getArtifact("design.apiChanges"));
            ScenarioRunner.printMetrics(replanRun);

            System.out.println();
            System.out.println("-- full decision lineage (shows both interpretations) --");
            context.decisionLineage().forEach(d -> System.out.println("  " + d));

            ScenarioRunner.writeReport("docs/generated", "ambiguous", replanRun, audit);
        } finally {
            engine.shutdown();
        }
    }
}
