package com.schwab.orchestrator;

import com.schwab.orchestrator.core.*;

import static com.schwab.orchestrator.testkit.MiniTest.*;

public class MetricsCollectorTest {

    public void testMetricsReflectSuccessFailureAndRollbackCounts() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("ok1", "ok1").executor((ctx, n) -> StageResult.ok("ok")).build())
                .addStage(StageDefinition.builder("ok2", "ok2").executor((ctx, n) -> StageResult.ok("ok")).build())
                .addStage(StageDefinition.builder("impl", "impl").executor((ctx, n) -> StageResult.ok("ok"))
                        .compensatingAction(ctx -> { }).build())
                .addStage(StageDefinition.builder("broken", "broken").dependsOn("impl").maxRetries(0)
                        .executor((ctx, n) -> StageResult.failed("fail")).build())
                .addStage(StageDefinition.builder("blocked", "blocked").dependsOn("broken")
                        .executor((ctx, n) -> StageResult.ok("never")).build());

        WorkflowEngine engine = new WorkflowEngine(graph, new WorkflowContext("m", "req"), new AuditTrail(),
                new GuardrailEngine(), EngineConfig.defaults(), "metrics-test");
        RunState run = engine.advance();
        engine.shutdown();

        RunMetrics metrics = new MetricsCollector().collect(run);
        assertEquals(5, metrics.totalStages, "5 stages total");
        assertEquals(2L, metrics.succeeded, "ok1 and ok2 succeeded");
        assertEquals(1L, metrics.failed, "broken failed permanently");
        assertEquals(1L, metrics.rolledBack, "impl was rolled back");
        assertEquals(1L, metrics.skipped, "blocked was skipped");
        assertTrue(metrics.successRate > 0.0 && metrics.successRate < 1.0, "success rate should be a fraction between 0 and 1");
        assertNotNull(metrics.endToEndLatency, "a completed run should have a measurable end-to-end latency");
    }

    public void testRetryFrequencyCountsRetriesAcrossStages() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("flaky", "flaky").maxRetries(2).retryBackoffMillis(2)
                        .executor((ctx, attempt) -> attempt < 2 ? StageResult.failed("transient") : StageResult.ok("recovered"))
                        .build());
        WorkflowEngine engine = new WorkflowEngine(graph, new WorkflowContext("m2", "req"), new AuditTrail(),
                new GuardrailEngine(), EngineConfig.defaults(), "metrics-test-2");
        RunState run = engine.advance();
        engine.shutdown();

        RunMetrics metrics = new MetricsCollector().collect(run);
        assertEquals(1, metrics.totalRetries, "exactly one retry occurred");
        assertTrue(metrics.retryFrequency > 0, "retry frequency should be non-zero when retries occurred");
    }
}
