# Testing Approach, Limitations, and Trade-offs

## Build environment note (read this first)

This project was built and verified inside a sandboxed environment with **no route to Maven Central, npm's CDN mirrors, Adoptium, apt, or any other JVM package/binary source** — only `pypi.org`, `registry.npmjs.org`, and `github.com`'s HTML pages were reachable, all confirmed by direct testing (`curl` against ~15 candidate hosts, all but those three timing out at a proxy allowlist). The sandbox also ships a JRE with no standalone `javac` binary on `PATH`.

Rather than write ~9,000 lines of Spring Boot / JUnit code I could not compile and simply assert it probably works, I made a deliberate call: **build the entire system with zero external dependencies**, using only the JDK standard library (`com.sun.net.httpserver` for HTTP, hand-rolled JSON, a small reflection-based test runner in place of JUnit). This let every line of this repository actually be compiled and executed during development — not just written.

The one wrinkle (compiling without a `javac` binary) was solved by invoking the compiler through `javax.tools.ToolProvider.getSystemJavaCompiler()`, which the JVM here exposes even without a `javac` executable on `PATH` — `scripts/Compile.java` is that ~20-line bootstrap, and `scripts/compile.sh` uses real `javac` first and only falls back to it if `javac` is unavailable. On a normal developer machine with a full JDK, `javac` will be found and used directly; the fallback exists for parity with how this was actually verified.

**This is disclosed, not hidden**, because "I ran this and it passed" is a materially different (and more defensible) claim than "I wrote this and it should pass," and the assignment explicitly asks for validation rigor and defensible decisions.

## What was actually run, not just written

- **49 automated tests**, all passing: 27 in `url-shortener` (unit + full-stack HTTP integration), 22 in `orchestrator` (graph validation, parallel synchronization, retries, rollback, approval gating, safe-stop, dynamic re-plan, guardrails, metrics — all against synthetic stages so the engine is tested independent of the SDLC agents).
- **All 3 required scenarios**, end to end, including the REST API path for scenario 1.
- **Live HTTP exercises** of every url-shortener endpoint with `curl`, including deliberately triggering the rate limiter (21st rapid request returns 429), TTL expiry (410 Gone after the TTL elapses), alias conflicts (409), and malformed URLs (400).
- **A real regression caught by a real test failure**, not a mocked one, in the brownfield scenario — see `03-scenarios.md`.

## Testing strategy per module

**`url-shortener`** — hexagonal split (see `01-architecture.md`) means the core business logic (`UrlShortenerService`, `Base62Encoder`, `RateLimiter`) is unit tested with zero HTTP involved, and a separate `HttpApiIntegrationTest` boots a real `HttpServerApp` on an ephemeral port and drives it with `java.net.http.HttpClient` for true end-to-end coverage of the routing/JSON/status-code layer.

**`orchestrator`** — the engine is tested with hand-built synthetic `StageDefinition`s (lambda executors with counters/flags) rather than the real SDLC agents, so a test failure points unambiguously at scheduling logic, not agent heuristics. The agents themselves are exercised (and their real output captured) by the 3 end-to-end scenarios instead — which is a form of acceptance testing layered on top of the engine's unit tests.

## Notable bug the test suite actually caught

`HttpServerApp`'s worker thread pool was originally created with `Executors.newFixedThreadPool(16)` — plain, non-daemon threads. The first full test run hung indefinitely after printing `PASS` for all three `HttpApiIntegrationTest` cases, because the JVM would not exit while those worker threads were still alive. Fixed by supplying a daemon-thread factory and calling `executor.shutdownNow()` from `stop()`. This is exactly the kind of bug that only surfaces by actually running the code — left in as an example of why the "no verified compile, no ship" policy above mattered in practice.

## Limitations and honest trade-offs

| Decision | Why | What it costs |
|---|---|---|
| In-memory persistence (`InMemoryUrlRepository`, `InMemoryClickEventStore`) | Zero-dependency constraint; no H2/Postgres reachable | Data doesn't survive a restart. Swap-in point is clean: both are behind interfaces (`UrlRepository`, `ClickEventStore`) with a single implementing class each. |
| `com.sun.net.httpserver` instead of Spring Boot | Could not fetch/verify Spring Boot in this sandbox | No `@RestController` conveniences, manual routing/JSON. The core service layer has zero web-framework imports, so this is a drop-in swap later, not a rewrite. |
| Hand-rolled JSON (`JsonUtil`/`MiniJson`) instead of Jackson | Same reason | Sufficient for this project's flat/shallow DTOs; would not scale to deeply polymorphic payloads without more work. |
| Custom `MiniTest`/`TestRunner` instead of JUnit 5 | Same reason | Test methods are written in a JUnit-like style (one `public void testX()` per case) specifically so porting to real JUnit 5 later is close to a search-and-replace, not a rewrite. |
| Rollback = a declared `CompensatingAction`, not a git revert | No real VCS integration in scope for a prototype | Faithfully demonstrates the orchestration *pattern* (undo a committed upstream effect on downstream failure) without implying this system can revert arbitrary code changes — it can't, and doesn't claim to. |
| `ImplementationAgent` produces a structured change plan, not a literal code diff | Auto-generating correct, compilable patches is a much larger scope than an orchestration-layer prototype | This is the most significant scope cut. A production version of this system would hand the change plan to a code-generation agent (or a human) as the next stage; wiring that in is additive, not a redesign — `ImplementationAgent`'s output artifact (`filesToChange`, `changePlanSummary`) is already shaped for that. |
| Rate limiting / caching are single-instance, in-memory | No Redis reachable; out of scope for a single-node prototype | Documented explicitly in code comments; the swap to a distributed limiter is a `RateLimiter` interface extraction away. |
| Guardrails are regex/heuristic-based | A prototype scanning tool, not a real secret-scanner (e.g., TruffleHog) or SAST engine | Sufficient to prove the *governance pattern* (guardrail blocks, agent retries, guardrail passes) end to end; a production system would plug in a real scanner behind the same `Guardrail` interface. |
| `RequirementsAgent`'s ambiguity detection is keyword/heuristic-based | No LLM call in this prototype (see below) | Deliberately conservative and explainable — every ambiguity flag and every assumption is logged with a concrete reason, which is arguably more auditable than an opaque LLM judgment call, even if less linguistically sophisticated. |
| No real LLM in the loop | The assignment's core evaluation is the orchestration layer's engineering (dependency graph, gates, retries, rollback, governance), not prompt engineering; and this sandbox's network restrictions would have made an LLM-backed agent unverifiable anyway | Each "agent" is a deterministic, rule-based `StageExecutor`. The `StageExecutor` interface is intentionally the same shape an LLM-backed implementation would need (`WorkflowContext` in, `StageResult` out), so swapping in real model calls per agent is additive. |

## Risks and failure scenarios considered

- **Retry storms**: bounded by `maxRetries` per stage (never unbounded) and a run-level safe-stop breaker independent of any single stage's retry budget.
- **A guardrail silently rubber-stamping bad output**: guardrails are opt-in per stage and explicitly listed in `StageDefinition`, so "which stages are protected by which policy" is visible in one file (`WorkflowGraphs.java`) rather than buried in a global filter.
- **Approval bypass after a re-plan**: solved via versioned approvals (see `01-architecture.md` decision 3) — this was caught and fixed during development, not assumed correct from the start.
- **A stuck run with no visibility**: every state transition is audited; `RunState.statusSnapshot()` and the REST API's `/runs/{id}` give a live view at any point, including mid-pause.
- **Cascading failure with no way to stop the system**: safe-stop; independent of and in addition to per-stage retry/rollback.
- **Analytics data leaking raw client IPs**: `ClickEvent` stores a truncated SHA-256 hash of the client address, never the raw IP.
