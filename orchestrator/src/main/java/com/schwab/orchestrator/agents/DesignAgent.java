package com.schwab.orchestrator.agents;

import com.schwab.orchestrator.core.StageExecutor;
import com.schwab.orchestrator.core.StageResult;
import com.schwab.orchestrator.core.WorkflowContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the normalized requirement into concrete architecture decisions:
 * API surface, data model changes, and technology choices - justified against
 * the actual existing url-shortener design rather than invented from scratch.
 */
public final class DesignAgent implements StageExecutor {

    @SuppressWarnings("unchecked")
    @Override
    public StageResult execute(WorkflowContext context, int attemptNumber) {
        List<String> functional = context.getArtifact("requirements.functionalRequirements");
        if (functional == null) functional = List.of();

        List<String> apiChanges = new ArrayList<>();
        List<String> dataModelChanges = new ArrayList<>();
        List<String> techChoices = new ArrayList<>();
        StringBuilder rationale = new StringBuilder();

        String joined = String.join(" ", functional).toLowerCase();

        if (joined.contains("analytic")) {
            apiChanges.add("Extend GET /api/urls/{code}/analytics to accept ?since= and ?groupBy=day query params");
            dataModelChanges.add("Add a derived daily-rollup view over the existing ClickEvent store (no schema break)");
            rationale.append("Analytics already has a click-event store backed by ClickEventRepository (JPA/H2); this is additive, not a rewrite. ");
        }
        if (joined.contains("qr") || joined.contains("code")) {
            apiChanges.add("Add GET /api/urls/{code}/qrcode returning a PNG");
            techChoices.add("Generate QR codes with a small self-contained matrix encoder to avoid adding an external dependency");
        }
        if (joined.contains("auth") || joined.contains("owner")) {
            apiChanges.add("Add optional Authorization header support; associate ShortUrl.owner with the authenticated principal");
            dataModelChanges.add("Add nullable 'owner' field to ShortUrl (backward compatible - existing links have owner=null)");
        }
        if (apiChanges.isEmpty()) {
            apiChanges.add("No new endpoints required; change is internal to the service layer");
        }
        techChoices.add("Keep the zero-external-dependency approach (see docs/04-testing-and-tradeoffs.md) unless the feature strictly requires a new capability");

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("apiChanges", apiChanges);
        artifacts.put("dataModelChanges", dataModelChanges);
        artifacts.put("techChoices", techChoices);
        artifacts.put("designRationale", rationale.length() == 0 ? "Direct extension of existing service layer; no architectural change needed." : rationale.toString());

        context.recordDecision("design", "agent:DesignAgent", "DESIGN_PROPOSED",
                apiChanges.size() + " API change(s), " + dataModelChanges.size() + " data model change(s)");

        return StageResult.ok("Design produced: " + apiChanges.size() + " API change(s)", artifacts);
    }
}
