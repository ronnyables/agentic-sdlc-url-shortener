package com.schwab.orchestrator.scenarios;

import com.schwab.orchestrator.core.*;

import java.util.List;

/**
 * Scenario 1 - Greenfield: build the URL shortener from scratch.
 * Demonstrates: requirement understanding on a well-specified ask, a mostly
 * linear happy path with a real parallel synchronization point (testing +
 * documentation run concurrently, release-readiness joins on both), a human
 * approval checkpoint at the highest-impact step, and clean audit/metrics.
 */
public final class GreenfieldScenario {

    public static void main(String[] args) throws Exception {
        String requirement = "Build a URL shortener service from scratch with core APIs "
                + "(create, redirect, delete), click analytics, and reliability features "
                + "(rate limiting, TTL expiry, deduplication).";

        SdlcOptions opts = new SdlcOptions();
        opts.brownfield = false;
        opts.simulateSecretLeakOnFirstAttempt = false;
        // Empty = TestingAgent runs Maven's default full test suite (unit + web-slice + full-stack
        // integration tests), which naturally excludes RegressionFixtureIT (see its javadoc).
        opts.testClassNames = List.of();

        ScenarioRunner.printHeader("SCENARIO 1: GREENFIELD - Build URL Shortener From Scratch");
        System.out.println("Requirement: " + requirement);

        WorkflowGraph graph = WorkflowGraphs.buildSdlcGraph(opts);
        GuardrailEngine guardrails = WorkflowGraphs.buildGuardrails();
        AuditTrail audit = new AuditTrail();
        WorkflowContext context = new WorkflowContext("GF-" + System.currentTimeMillis(), requirement);

        WorkflowEngine engine = new WorkflowEngine(graph, context, audit, guardrails, EngineConfig.defaults(), "greenfield");
        try {
            RunState run = engine.advance();
            ScenarioRunner.printStatus(run);

            if (run.getStatus() == RunStatus.PAUSED_FOR_APPROVAL) {
                System.out.println();
                System.out.println(">>> Engine paused for human approval on: " +
                        run.getPendingApprovals().stream().map(p -> p.displayName).collect(java.util.stream.Collectors.toList()));
                System.out.println(">>> Simulating reviewer decision: APPROVE (tests green, docs present, low risk)");
                engine.decideApproval("release-readiness", true, "ronny.ables",
                        "Verified test summary and generated docs; approved for release.");
                run = engine.advance();
                ScenarioRunner.printStatus(run);
            }

            ScenarioRunner.printMetrics(run);
            System.out.println();
            System.out.println("-- decision lineage --");
            context.decisionLineage().forEach(d -> System.out.println("  " + d));
            ScenarioRunner.writeReport("docs/generated", "greenfield", run, audit);
        } finally {
            engine.shutdown();
        }
    }
}
