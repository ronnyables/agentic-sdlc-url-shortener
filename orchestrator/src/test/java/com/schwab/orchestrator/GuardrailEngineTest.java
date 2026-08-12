package com.schwab.orchestrator;

import com.schwab.orchestrator.core.*;
import com.schwab.orchestrator.guardrails.*;

import java.util.List;
import java.util.Map;

import static com.schwab.orchestrator.testkit.MiniTest.*;

public class GuardrailEngineTest {

    public void testSecurityGuardrailBlocksHardcodedSecret() {
        SecurityGuardrail guardrail = new SecurityGuardrail();
        StageDefinition stage = StageDefinition.builder("s", "s").executor((ctx, n) -> null).build();
        StageResult result = StageResult.ok("draft", Map.of("snippet", "api_key = \"sk_live_12345\""));
        GuardrailResult check = guardrail.check(new WorkflowContext("r", "req"), stage, result);
        assertFalse(check.isPassed(), "a hardcoded api_key should be blocked");
    }

    public void testSecurityGuardrailPassesCleanOutput() {
        SecurityGuardrail guardrail = new SecurityGuardrail();
        StageDefinition stage = StageDefinition.builder("s", "s").executor((ctx, n) -> null).build();
        StageResult result = StageResult.ok("draft", Map.of("snippet", "config values are sourced from environment variables"));
        GuardrailResult check = guardrail.check(new WorkflowContext("r", "req"), stage, result);
        assertTrue(check.isPassed(), "clean output should not be flagged");
    }

    public void testComplianceGuardrailRequiresArtifactsPresent() {
        ComplianceGuardrail guardrail = new ComplianceGuardrail("testing.allTestsPassed", "documentation.readmePresent");
        StageDefinition stage = StageDefinition.builder("s", "s").executor((ctx, n) -> null).build();
        WorkflowContext ctxMissing = new WorkflowContext("r", "req");
        assertFalse(guardrail.check(ctxMissing, stage, StageResult.ok("x")).isPassed(), "missing artifacts should block");

        WorkflowContext ctxComplete = new WorkflowContext("r", "req");
        ctxComplete.putArtifact("testing.allTestsPassed", true);
        ctxComplete.putArtifact("documentation.readmePresent", true);
        assertTrue(guardrail.check(ctxComplete, stage, StageResult.ok("x")).isPassed(), "present + true artifacts should pass");
    }

    public void testChangeControlGuardrailRequiresApprovalDecision() {
        ChangeControlGuardrail guardrail = new ChangeControlGuardrail();
        StageDefinition stage = StageDefinition.builder("release", "release").requiresApproval(true).executor((ctx, n) -> null).build();
        WorkflowContext ctx = new WorkflowContext("r", "req");
        assertFalse(guardrail.check(ctx, stage, StageResult.ok("x")).isPassed(), "no approval decision recorded yet");

        ctx.recordDecision("release", "human:tester", "APPROVED#0", "fine");
        assertTrue(guardrail.check(ctx, stage, StageResult.ok("x")).isPassed(), "approval decision now present");
    }

    public void testGuardrailEngineOnlyRunsGuardrailsTheStageOptedInto() {
        GuardrailEngine engine = new GuardrailEngine().register(new SecurityGuardrail());
        StageDefinition stageWithGuardrail = StageDefinition.builder("s1", "s1").guardrails("security").executor((ctx, n) -> null).build();
        StageDefinition stageWithoutGuardrail = StageDefinition.builder("s2", "s2").executor((ctx, n) -> null).build();
        StageResult leaky = StageResult.ok("x", Map.of("v", "password=hunter2"));

        List<GuardrailResult> withCheck = engine.evaluate(new WorkflowContext("r", "req"), stageWithGuardrail, leaky);
        List<GuardrailResult> withoutCheck = engine.evaluate(new WorkflowContext("r", "req"), stageWithoutGuardrail, leaky);

        assertEquals(1, withCheck.size(), "opted-in stage should have exactly one guardrail evaluated");
        assertFalse(withCheck.get(0).isPassed(), "the security guardrail should catch the leaked password");
        assertTrue(withoutCheck.isEmpty(), "a stage that didn't opt in should have zero guardrails run");
    }
}
