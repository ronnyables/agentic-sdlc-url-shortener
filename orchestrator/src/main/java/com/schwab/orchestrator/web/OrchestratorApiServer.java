package com.schwab.orchestrator.web;

import com.schwab.orchestrator.core.*;
import com.schwab.orchestrator.scenarios.SdlcOptions;
import com.schwab.orchestrator.scenarios.WorkflowGraphs;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal, dependency-free REST wrapper around the orchestration engine so a
 * reviewer can drive runs interactively (start a scenario, inspect status,
 * approve a gate, pull the audit trail/metrics) instead of only reading
 * captured console output from the scenario mains.
 *
 * Routes:
 *   GET  /health
 *   POST /runs                          {"scenario":"greenfield|brownfield|ambiguous"}
 *   GET  /runs/{runId}
 *   GET  /runs/{runId}/audit
 *   GET  /runs/{runId}/metrics
 *   POST /runs/{runId}/approve/{stageId} {"approved":true,"approver":"...","comment":"..."}
 *   POST /runs/{runId}/invalidate/{stageId} {"reason":"...","humanClarification":"..."}   (re-plan demo)
 */
public final class OrchestratorApiServer {

    private static final class RunHandle {
        final WorkflowEngine engine;
        final WorkflowContext context;
        final AuditTrail audit;
        RunHandle(WorkflowEngine engine, WorkflowContext context, AuditTrail audit) {
            this.engine = engine; this.context = context; this.audit = audit;
        }
    }

    private final Map<String, RunHandle> runs = new ConcurrentHashMap<>();
    private final AtomicInteger runCounter = new AtomicInteger(1);
    private final HttpServer server;

    public OrchestratorApiServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> respond(ex, 200, MiniJson.object("status", "UP")));
        server.createContext("/runs", this::handleRuns);
        server.setExecutor(Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "orchestrator-http");
            t.setDaemon(true);
            return t;
        }));
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    public int getPort() { return server.getAddress().getPort(); }

    private void handleRuns(HttpExchange exchange) throws IOException {
        try {
            String[] seg = segments(exchange);
            String method = exchange.getRequestMethod();

            if (seg.length == 1 && "POST".equalsIgnoreCase(method)) {
                startRun(exchange);
            } else if (seg.length == 2 && "GET".equalsIgnoreCase(method)) {
                statusOf(exchange, seg[1]);
            } else if (seg.length == 3 && "GET".equalsIgnoreCase(method) && "audit".equals(seg[2])) {
                auditOf(exchange, seg[1]);
            } else if (seg.length == 3 && "GET".equalsIgnoreCase(method) && "metrics".equals(seg[2])) {
                metricsOf(exchange, seg[1]);
            } else if (seg.length == 4 && "POST".equalsIgnoreCase(method) && "approve".equals(seg[2])) {
                approve(exchange, seg[1], seg[3]);
            } else if (seg.length == 4 && "POST".equalsIgnoreCase(method) && "invalidate".equals(seg[2])) {
                invalidate(exchange, seg[1], seg[3]);
            } else {
                respond(exchange, 404, MiniJson.object("error", "NOT_FOUND", "message", "no such route"));
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            respond(exchange, 400, MiniJson.object("error", "BAD_REQUEST", "message", e.getMessage()));
        } catch (Exception e) {
            respond(exchange, 500, MiniJson.object("error", "INTERNAL_ERROR", "message", String.valueOf(e.getMessage())));
        }
    }

    private void startRun(HttpExchange exchange) throws Exception {
        Map<String, Object> body = MiniJson.parseObject(readBody(exchange));
        String scenario = String.valueOf(body.getOrDefault("scenario", "greenfield"));
        String requirementOverride = (String) body.get("requirement");

        SdlcOptions opts = new SdlcOptions();
        String requirement;
        switch (scenario) {
            case "brownfield":
                opts.brownfield = true;
                opts.testClassNames = List.of("com.schwab.shortener.UrlShortenerServiceTest",
                        "com.schwab.shortener.demo.RegressionFixtureTest");
                requirement = requirementOverride != null ? requirementOverride
                        : "Add per-referrer daily analytics rollups and tune the rate limiter for the existing URL shortener service.";
                break;
            case "ambiguous":
                opts.simulateSecretLeakOnFirstAttempt = true;
                opts.testClassNames = List.of("com.schwab.shortener.Base62EncoderTest", "com.schwab.shortener.UrlShortenerServiceTest");
                requirement = requirementOverride != null ? requirementOverride : "Make the analytics better.";
                break;
            default:
                opts.testClassNames = List.of("com.schwab.shortener.Base62EncoderTest", "com.schwab.shortener.RateLimiterTest",
                        "com.schwab.shortener.UrlShortenerServiceTest", "com.schwab.shortener.HttpApiIntegrationTest");
                requirement = requirementOverride != null ? requirementOverride
                        : "Build a URL shortener service from scratch with core APIs, analytics, and reliability features.";
        }

        String runId = scenario.toUpperCase().charAt(0) + "-" + runCounter.getAndIncrement() + "-" + System.currentTimeMillis();
        WorkflowGraph graph = WorkflowGraphs.buildSdlcGraph(opts);
        GuardrailEngine guardrails = WorkflowGraphs.buildGuardrails();
        AuditTrail audit = new AuditTrail();
        WorkflowContext context = new WorkflowContext(runId, requirement);
        WorkflowEngine engine = new WorkflowEngine(graph, context, audit, guardrails, EngineConfig.defaults(), scenario);
        runs.put(runId, new RunHandle(engine, context, audit));

        RunState state = engine.advance();
        respond(exchange, 201, runSummary(runId, state, context));
    }

    private void statusOf(HttpExchange exchange, String runId) throws IOException {
        RunHandle handle = requireRun(runId);
        respond(exchange, 200, runSummary(runId, handle.engine.getRunState(), handle.context));
    }

    private void auditOf(HttpExchange exchange, String runId) throws IOException {
        RunHandle handle = requireRun(runId);
        List<String> lines = handle.audit.events().stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
        respond(exchange, 200, MiniJson.object("runId", runId, "events", lines));
    }

    private void metricsOf(HttpExchange exchange, String runId) throws IOException {
        RunHandle handle = requireRun(runId);
        RunMetrics m = new MetricsCollector().collect(handle.engine.getRunState());
        respond(exchange, 200, MiniJson.object("runId", runId, "metrics", m.toString()));
    }

    private void approve(HttpExchange exchange, String runId, String stageId) throws Exception {
        RunHandle handle = requireRun(runId);
        Map<String, Object> body = MiniJson.parseObject(readBody(exchange));
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String approver = String.valueOf(body.getOrDefault("approver", "reviewer"));
        String comment = String.valueOf(body.getOrDefault("comment", ""));
        handle.engine.decideApproval(stageId, approved, approver, comment);
        RunState state = handle.engine.advance();
        respond(exchange, 200, runSummary(runId, state, handle.context));
    }

    private void invalidate(HttpExchange exchange, String runId, String stageId) throws Exception {
        RunHandle handle = requireRun(runId);
        Map<String, Object> body = MiniJson.parseObject(readBody(exchange));
        String reason = String.valueOf(body.getOrDefault("reason", "external re-plan trigger"));
        if (body.get("humanClarification") != null) {
            handle.context.putArtifact("requirements.humanClarification", body.get("humanClarification"));
        }
        handle.engine.invalidate(stageId, reason);
        RunState state = handle.engine.advance();
        respond(exchange, 200, runSummary(runId, state, handle.context));
    }

    private Map<String, Object> runSummary(String runId, RunState state, WorkflowContext context) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", runId);
        summary.put("scenario", state.getScenarioName());
        summary.put("status", state.getStatus().name());
        summary.put("stages", state.statusSnapshot());
        summary.put("pendingApprovals", state.getPendingApprovals().stream()
                .map(p -> p.stageId).collect(java.util.stream.Collectors.toList()));
        return summary;
    }

    private RunHandle requireRun(String runId) {
        RunHandle handle = runs.get(runId);
        if (handle == null) throw new IllegalArgumentException("unknown runId: " + runId);
        return handle;
    }

    // ---------------------------------------------------------------- http plumbing

    private static String[] segments(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath().replaceAll("^/+", "").replaceAll("/+$", "");
        return path.isEmpty() ? new String[0] : path.split("/");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = MiniJson.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("server.port", System.getenv().getOrDefault("PORT", "8090")));
        OrchestratorApiServer api = new OrchestratorApiServer(port);
        api.start();
        System.out.println("Orchestrator API listening on http://localhost:" + port);
        System.out.println("  POST /runs                              {\"scenario\":\"greenfield|brownfield|ambiguous\"}");
        System.out.println("  GET  /runs/{runId}");
        System.out.println("  GET  /runs/{runId}/audit");
        System.out.println("  GET  /runs/{runId}/metrics");
        System.out.println("  POST /runs/{runId}/approve/{stageId}    {\"approved\":true,\"approver\":\"...\",\"comment\":\"...\"}");
        System.out.println("  POST /runs/{runId}/invalidate/{stageId} {\"reason\":\"...\",\"humanClarification\":\"...\"}");
        Runtime.getRuntime().addShutdownHook(new Thread(api::stop));
    }
}
