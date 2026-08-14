package com.schwab.orchestrator;

import com.schwab.orchestrator.core.StageDefinition;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowGraph;

import java.util.List;

import static com.schwab.orchestrator.testkit.MiniTest.*;

public class WorkflowGraphTest {

    private StageDefinition stage(String id, String... deps) {
        return StageDefinition.builder(id, id)
                .dependsOn(deps)
                .executor((ctx, attempt) -> StageResult.ok("ok"))
                .build();
    }

    public void testValidateAcceptsValidDag() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(stage("a"))
                .addStage(stage("b", "a"))
                .addStage(stage("c", "a"));
        assertNoThrow(graph::validate, "a simple DAG should validate cleanly");
    }

    public void testValidateRejectsUnknownDependency() {
        WorkflowGraph graph = new WorkflowGraph().addStage(stage("a", "ghost"));
        assertThrows(IllegalStateException.class, graph::validate, "dependency on a non-existent stage must fail validation");
    }

    public void testValidateRejectsCycle() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(stage("a", "c"))
                .addStage(stage("b", "a"))
                .addStage(stage("c", "b"));
        assertThrows(IllegalStateException.class, graph::validate, "a -> depends on c -> depends on b -> depends on a is a cycle");
    }

    public void testTopologicalLayersGroupsIndependentStagesTogether() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(stage("requirements"))
                .addStage(stage("design", "requirements"))
                .addStage(stage("testing", "design"))
                .addStage(stage("documentation", "design"))
                .addStage(stage("release", "testing", "documentation"));

        List<List<StageDefinition>> layers = graph.topologicalLayers();
        assertEquals(4, layers.size(), "expected 4 sequential layers: [requirements] [design] [testing,documentation] [release]");
        assertEquals(1, layers.get(0).size(), "layer 0 = requirements only");
        assertEquals(1, layers.get(1).size(), "layer 1 = design only");
        assertEquals(2, layers.get(2).size(), "layer 2 = testing + documentation running in parallel");
        assertEquals(1, layers.get(3).size(), "layer 3 = release, synchronizing on both parallel branches");
    }

    public void testTransitiveDependentsIncludesIndirectDescendants() {
        WorkflowGraph graph = new WorkflowGraph()
                .addStage(stage("a"))
                .addStage(stage("b", "a"))
                .addStage(stage("c", "b"))
                .addStage(stage("d")); // unrelated

        var dependents = graph.transitiveDependents("a");
        assertTrue(dependents.contains("b"), "b directly depends on a");
        assertTrue(dependents.contains("c"), "c transitively depends on a via b");
        assertFalse(dependents.contains("d"), "d is unrelated to a");
    }

    public void testDuplicateStageIdIsRejected() {
        WorkflowGraph graph = new WorkflowGraph().addStage(stage("a"));
        assertThrows(IllegalArgumentException.class, () -> graph.addStage(stage("a")), "duplicate stage ids must be rejected");
    }
}
