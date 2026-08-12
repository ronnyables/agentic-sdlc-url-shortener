package com.schwab.orchestrator;

import com.schwab.orchestrator.core.*;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.schwab.orchestrator.testkit.MiniTest.*;

public class WorkflowEngineTest {

    private WorkflowEngine engine(WorkflowGraph graph) {
        return new WorkflowEngine(graph, new WorkflowContext("TEST-" + System.nanoTime(), "synthetic test requirement"),
                new AuditTrail(), new GuardrailEngine(), EngineConfig.defaults(), "unit-test");
    }

    private WorkflowEngine engine(WorkflowGraph graph, EngineConfig config) {
        return new WorkflowEngine(graph, new WorkflowContext("TEST-" + System.nanoTime(), "synthetic test requirement"),
                new AuditTrail(), new GuardrailEngine(), config, "unit-test");
    }

    public void testLinearHappyPathExecutesInDependencyOrder() {
        ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("a", "a").executor((ctx, n) -> { order.add("a"); return StageResult.ok("ok"); }).build())
                .addStage(StageDefinition.builder("b", "b").dependsOn("a").executor((ctx, n) -> { order.add("b"); return StageResult.ok("ok"); }).build())
                .addStage(StageDefinition.builder("c", "c").dependsOn("b").executor((ctx, n) -> { order.add("c"); return StageResult.ok("ok"); }).build());

        WorkflowEngine eng = engine(graph);
        RunState run = eng.advance();
        eng.shutdown();

        assertEquals(RunStatus.COMPLETED_SUCCESS, run.getStatus(), "all-success linear chain should complete successfully");
        assertEquals(List.of("a", "b", "c"), List.copyOf(order), "stages must execute in dependency order");
    }

    public void testParallelStagesBothCompleteBeforeSynchronizingDependent() {
        ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("a", "a").executor((ctx, n) -> { order.add("a"); return StageResult.ok("ok"); }).build())
                .addStage(StageDefinition.builder("b1", "b1").dependsOn("a").executor((ctx, n) -> { order.add("b1"); return StageResult.ok("ok"); }).build())
                .addStage(StageDefinition.builder("b2", "b2").dependsOn("a").executor((ctx, n) -> { order.add("b2"); return StageResult.ok("ok"); }).build())
                .addStage(StageDefinition.builder("c", "c").dependsOn("b1", "b2").executor((ctx, n) -> { order.add("c"); return StageResult.ok("ok"); }).build());

        WorkflowEngine eng = engine(graph);
        RunState run = eng.advance();
        eng.shutdown();

        List<String> seq = List.copyOf(order);
        assertEquals(RunStatus.COMPLETED_SUCCESS, run.getStatus(), "diamond graph should complete successfully");
        assertTrue(seq.indexOf("a") < seq.indexOf("b1"), "a must precede b1");
        assertTrue(seq.indexOf("a") < seq.indexOf("b2"), "a must precede b2");
        assertTrue(seq.indexOf("b1") < seq.indexOf("c"), "c must wait for b1 (synchronization)");
        assertTrue(seq.indexOf("b2") < seq.indexOf("c"), "c must wait for b2 (synchronization)");
    }

    public void testTransientFailureRecoversViaRetry() {
        AtomicInteger calls = new AtomicInteger(0);
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("flaky", "flaky")
                        .maxRetries(2)
                        .retryBackoffMillis(5)
                        .executor((ctx, attempt) -> {
                            calls.incrementAndGet();
                            if (attempt == 1) return StageResult.failed("simulated transient failure");
                            return StageResult.ok("recovered");
                        })
                        .build());

        WorkflowEngine eng = engine(graph);
        RunState run = eng.advance();
        eng.shutdown();

        assertEquals(RunStatus.COMPLETED_SUCCESS, run.getStatus(), "stage should recover on its second attempt");
        assertEquals(StageStatus.SUCCEEDED, run.info("flaky").getStatus(), "flaky stage final status");
        assertEquals(2, calls.get(), "executor should have been invoked exactly twice");
        assertEquals(1, run.info("flaky").getRetryCount(), "exactly one retry should have been recorded");
    }

    public void testExhaustedRetriesFailsStageAndSkipsDependents() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("a", "a").executor((ctx, n) -> StageResult.ok("ok")).build())
                .addStage(StageDefinition.builder("b", "b").dependsOn("a").maxRetries(1).retryBackoffMillis(5)
                        .executor((ctx, n) -> StageResult.failed("always fails")).build())
                .addStage(StageDefinition.builder("c", "c").dependsOn("b").executor((ctx, n) -> StageResult.ok("should never run")).build());

        WorkflowEngine eng = engine(graph);
        RunState run = eng.advance();
        eng.shutdown();

        assertEquals(RunStatus.COMPLETED_WITH_FAILURES, run.getStatus(), "a permanently failed stage means the run did not fully succeed");
        assertEquals(StageStatus.SUCCEEDED, run.info("a").getStatus(), "a has no reason to fail");
        assertEquals(StageStatus.FAILED, run.info("b").getStatus(), "b exhausts retries and fails permanently");
        assertEquals(StageStatus.SKIPPED, run.info("c").getStatus(), "c is blocked because its dependency b failed");
    }

    public void testRollbackCascadeRunsCompensatingActionOnUpstreamSuccess() {
        AtomicBoolean compensated = new AtomicBoolean(false);
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("impl", "impl")
                        .executor((ctx, n) -> StageResult.ok("committed"))
                        .compensatingAction(ctx -> compensated.set(true))
                        .build())
                .addStage(StageDefinition.builder("test", "test").dependsOn("impl").maxRetries(0)
                        .executor((ctx, n) -> StageResult.failed("regression detected")).build());

        WorkflowEngine eng = engine(graph);
        RunState run = eng.advance();
        eng.shutdown();

        assertEquals(StageStatus.FAILED, run.info("test").getStatus(), "test should fail permanently (maxRetries=0)");
        assertEquals(StageStatus.ROLLED_BACK, run.info("impl").getStatus(), "impl should be rolled back after downstream failure");
        assertTrue(compensated.get(), "the compensating action must actually run");
    }

    public void testApprovalGateHoldsAndResumesOnApproval() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("a", "a").executor((ctx, n) -> StageResult.ok("ok")).build())
                .addStage(StageDefinition.builder("release", "release").dependsOn("a").requiresApproval(true)
                        .executor((ctx, n) -> StageResult.ok("released")).build());

        WorkflowEngine eng = engine(graph);
        RunState paused = eng.advance();
        assertEquals(RunStatus.PAUSED_FOR_APPROVAL, paused.getStatus(), "run must pause at the approval gate");
        assertEquals(StageStatus.WAITING_APPROVAL, paused.info("release").getStatus(), "release stage should be waiting");
        assertEquals(1, paused.getPendingApprovals().size(), "exactly one pending approval expected");

        eng.decideApproval("release", true, "tester", "looks fine");
        RunState done = eng.advance();
        eng.shutdown();

        assertEquals(RunStatus.COMPLETED_SUCCESS, done.getStatus(), "run should complete after approval");
        assertEquals(StageStatus.SUCCEEDED, done.info("release").getStatus(), "release should have executed after approval");
    }

    public void testApprovalRejectionFailsStageAndSkipsDependents() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("release", "release").requiresApproval(true)
                        .executor((ctx, n) -> StageResult.ok("released")).build())
                .addStage(StageDefinition.builder("post", "post").dependsOn("release")
                        .executor((ctx, n) -> StageResult.ok("post-release step")).build());

        WorkflowEngine eng = engine(graph);
        eng.advance();
        eng.decideApproval("release", false, "tester", "not ready");
        RunState done = eng.advance();
        eng.shutdown();

        assertEquals(StageStatus.FAILED, done.info("release").getStatus(), "rejected approval should fail the stage");
        assertEquals(StageStatus.SKIPPED, done.info("post").getStatus(), "dependents of a rejected stage should be skipped");
        assertEquals(RunStatus.COMPLETED_WITH_FAILURES, done.getStatus(), "run should reflect the rejection as a failure outcome");
    }

    public void testSafeStopHaltsRemainingWorkAfterFailureThreshold() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("a", "a").maxRetries(0)
                        .executor((ctx, n) -> StageResult.failed("boom")).build())
                .addStage(StageDefinition.builder("b", "b").dependsOn("a")
                        .executor((ctx, n) -> StageResult.ok("should not run")).build())
                .addStage(StageDefinition.builder("c", "c").dependsOn("b")
                        .executor((ctx, n) -> StageResult.ok("should not run")).build());

        WorkflowEngine eng = engine(graph, new EngineConfig(1, 4)); // safe-stop after just 1 failure
        RunState run = eng.advance();
        eng.shutdown();

        assertEquals(RunStatus.SAFE_STOPPED, run.getStatus(), "engine should halt after crossing the failure threshold");
        assertEquals(StageStatus.SKIPPED, run.info("b").getStatus(), "b should be skipped by the safe-stop, not executed");
        assertEquals(StageStatus.SKIPPED, run.info("c").getStatus(), "c should be skipped by the safe-stop, not executed");
    }

    public void testInvalidateReexecutesStaleStagesOnReplan() {
        AtomicInteger aRuns = new AtomicInteger(0);
        AtomicInteger bRuns = new AtomicInteger(0);
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(StageDefinition.builder("a", "a").executor((ctx, n) -> { aRuns.incrementAndGet(); return StageResult.ok("ok"); }).build())
                .addStage(StageDefinition.builder("b", "b").dependsOn("a").executor((ctx, n) -> { bRuns.incrementAndGet(); return StageResult.ok("ok"); }).build());

        WorkflowEngine eng = engine(graph);
        RunState first = eng.advance();
        assertEquals(RunStatus.COMPLETED_SUCCESS, first.getStatus(), "initial run should succeed");
        assertEquals(1, aRuns.get(), "a should have executed once");
        assertEquals(1, bRuns.get(), "b should have executed once");

        eng.invalidate("a", "upstream input changed");
        assertEquals(StageStatus.STALE, eng.getRunState().info("a").getStatus(), "a should be marked STALE after invalidate");
        assertEquals(StageStatus.STALE, eng.getRunState().info("b").getStatus(), "b (dependent of a) should also be marked STALE");

        RunState replanned = eng.advance();
        eng.shutdown();

        assertEquals(RunStatus.COMPLETED_SUCCESS, replanned.getStatus(), "re-planned run should also complete successfully");
        assertEquals(2, aRuns.get(), "a should have been re-executed after invalidate");
        assertEquals(2, bRuns.get(), "b should have been re-executed as a dependent of the invalidated stage");
    }
}
