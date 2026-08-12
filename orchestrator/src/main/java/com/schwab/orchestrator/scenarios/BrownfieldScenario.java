package com.schwab.orchestrator.scenarios;

import com.schwab.orchestrator.core.*;

import java.util.List;

/**
 * Scenario 2 - Brownfield: enhance the already-built URL shortener.
 * Demonstrates: codebase reasoning against the real source tree, a genuine
 * regression caught by actually running tests (the RegressionFixtureTest
 * fixture), and the resulting rollback + skip cascade + safe-stop path -
 * governance reacting to a real failure, not a scripted one.
 */
public final class BrownfieldScenario {

    public static void main(String[] args) throws Exception {
        String requirement = "Add per-referrer daily analytics rollups and tune the rate limiter "
                + "for the existing URL shortener service without breaking current API contracts.";

        SdlcOptions opts = new SdlcOptions();
        opts.brownfield = true;
        opts.simulateSecretLeakOnFirstAttempt = false;
        // Deliberately include the fault-injection fixture alongside a real, passing suite so the
        // scenario exercises a genuine (simulated) regression rather than an always-green demo.
        opts.testClassNames = List.of(
                "com.schwab.shortener.UrlShortenerServiceTest",
                "com.schwab.shortener.demo.RegressionFixtureTest");

        ScenarioRunner.printHeader("SCENARIO 2: BROWNFIELD - Enhance Existing URL Shortener (with a real regression)");
        System.out.println("Requirement: " + requirement);

        WorkflowGraph graph = WorkflowGraphs.buildSdlcGraph(opts);
        GuardrailEngine guardrails = WorkflowGraphs.buildGuardrails();
        AuditTrail audit = new AuditTrail();
        WorkflowContext context = new WorkflowContext("BF-" + System.currentTimeMillis(), requirement);
        context.putArtifact("repo.srcRoot", opts.repoSrcRoot);

        WorkflowEngine engine = new WorkflowEngine(graph, context, audit, guardrails, EngineConfig.defaults(), "brownfield");
        try {
            RunState run = engine.advance();
            ScenarioRunner.printStatus(run);

            System.out.println();
            System.out.println(">>> codebase-reasoning impacted files: "
                    + context.getArtifact("codebase-reasoning.impactedFiles"));
            System.out.println(">>> testing result: allTestsPassed="
                    + context.getArtifact("testing.allTestsPassed") + " failedTests="
                    + context.getArtifact("testing.failedTests"));
            System.out.println(">>> implementation rolled back (compensating action ran)? "
                    + context.getArtifact("implementation.reverted"));

            ScenarioRunner.printMetrics(run);
            System.out.println();
            System.out.println("-- decision lineage --");
            context.decisionLineage().forEach(d -> System.out.println("  " + d));
            ScenarioRunner.writeReport("docs/generated", "brownfield", run, audit);

            System.out.println();
            System.out.println("Expected outcome: 'testing' FAILED (real regression caught), 'implementation'");
            System.out.println("ROLLED_BACK (compensating action executed), 'documentation'/'release-readiness' SKIPPED.");
            System.out.println("This is the governance path working as designed, not a bug in the demo.");
        } finally {
            engine.shutdown();
        }
    }
}
