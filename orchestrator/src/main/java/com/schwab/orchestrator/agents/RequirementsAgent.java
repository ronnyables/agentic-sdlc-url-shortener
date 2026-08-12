package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Interprets the raw requirement text, flags ambiguity using explicit heuristics
 * (not just a feeling), and normalizes it into a structured engineering spec.
 * When the text is ambiguous, the agent does not silently guess: it records the
 * specific clarifying questions it would ask a human, applies the most
 * conservative reasonable default for each, and logs the assumption - so a
 * reviewer can see exactly where interpretation happened and why.
 */
public final class RequirementsAgent implements StageExecutor {

    private static final List<String> VAGUE_TERMS = List.of(
            "better", "improve", "nicer", "some", "etc", "various", "robust", "modern", "as needed");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[.\\n]+");

    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) {
        // Dynamic re-planning hook: if a human has since clarified the original ambiguous
        // ask (recorded as this artifact by a scenario/caller), re-interpret using that text
        // instead of the original raw requirement. The original input is preserved untouched.
        String humanClarification = context.getArtifact("requirements.humanClarification");
        String raw = humanClarification != null ? humanClarification : context.getRawRequirement();
        List<String> clarifyingQuestions = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();

        boolean tooShort = raw.trim().split("\\s+").length < 6;
        boolean hasVagueTerm = VAGUE_TERMS.stream().anyMatch(t -> raw.toLowerCase().contains(t));
        boolean missingScope = !raw.toLowerCase().matches(".*\\b(api|endpoint|service|analytics|redirect|shorten|url)\\b.*");

        boolean ambiguous = tooShort || hasVagueTerm || missingScope;

        if (tooShort) {
            clarifyingQuestions.add("The request is very brief - which specific capability should change (API surface, storage, analytics, reliability)?");
            assumptions.add("Treated as a request to extend the existing URL-shortener analytics capability, the smallest scope consistent with the wording.");
        }
        if (hasVagueTerm) {
            clarifyingQuestions.add("Terms like 'better'/'improve' are subjective - what measurable outcome defines success?");
            assumptions.add("Interpreted 'better/improve' as: add concrete, testable functionality (not a vague quality pass) with a stated acceptance criterion.");
        }
        if (missingScope) {
            clarifyingQuestions.add("No concrete noun (API, analytics, redirect, storage) was mentioned - which subsystem is in scope?");
            assumptions.add("Defaulted scope to the analytics subsystem, since that's the most common ask for an already-working shortener.");
        }

        List<String> functional = new ArrayList<>();
        List<String> nonFunctional = new ArrayList<>();
        for (String sentence : SENTENCE_SPLIT.split(raw)) {
            String s = sentence.trim();
            if (s.isEmpty()) continue;
            String lower = s.toLowerCase();
            if (lower.contains("latency") || lower.contains("scale") || lower.contains("reliab")
                    || lower.contains("secur") || lower.contains("available") || lower.contains("performance")) {
                nonFunctional.add(s);
            } else {
                functional.add(s);
            }
        }
        if (functional.isEmpty()) {
            functional.add(raw.trim());
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("normalizedProblem", ambiguous
                ? "Ambiguous request normalized with " + assumptions.size() + " explicit assumption(s) - see decision lineage."
                : "Well-specified request normalized directly from stated requirements.");
        artifacts.put("functionalRequirements", functional);
        artifacts.put("nonFunctionalRequirements", nonFunctional);
        artifacts.put("ambiguityDetected", ambiguous);
        artifacts.put("clarifyingQuestions", clarifyingQuestions);
        artifacts.put("assumptions", assumptions);
        artifacts.put("usedHumanClarification", humanClarification != null);

        context.recordDecision("requirements", "agent:RequirementsAgent",
                humanClarification != null ? "RE_NORMALIZED_FROM_CLARIFICATION"
                        : (ambiguous ? "NORMALIZED_WITH_ASSUMPTIONS" : "NORMALIZED"),
                humanClarification != null ? "re-interpreted using human-provided clarification: " + humanClarification
                        : (ambiguous ? String.join(" | ", assumptions) : "requirement was specific enough to normalize directly"));

        return StageResult.ok("Requirement interpreted (" + functional.size() + " functional, "
                + nonFunctional.size() + " non-functional); ambiguityDetected=" + ambiguous, artifacts);
    }
}
