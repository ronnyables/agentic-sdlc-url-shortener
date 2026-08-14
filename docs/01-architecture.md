# Architecture Overview

## 1. Two independent modules, one repo

`url-shortener` and `orchestrator` are deliberately separate Maven-style module trees with **no compile-time dependency on each other**. The orchestrator only touches the url-shortener at *runtime*, and only in one place: `TestingAgent` shells out to a subprocess (`java -cp url-shortener/bin com.schwab.shortener.testkit.TestRunner ...`) to run the product's real test suite and parse its output. Everything else the orchestrator does — codebase reasoning, design, documentation — reads the url-shortener's *source tree* as data (via `java.nio.file`), not as a library dependency.

This separation mirrors a real organization: the orchestration/CI system and the product it builds are not the same deployable artifact, and coupling them at compile time would be an architectural smell.

## 2. `url-shortener` — the product

Framework-agnostic core, thin HTTP adapter. This is a standard hexagonal-architecture split, chosen specifically so the business logic could be unit tested with zero mocking of a web framework:

```
com.schwab.shortener.core         <- pure Java, no HTTP types anywhere
  Base62Encoder                     sequence -> short code
  ShortUrl, ClickEvent              domain models
  UrlRepository (+ InMemory impl)   persistence port
  ClickEventStore (+ InMemory impl) analytics event log
  RateLimiter                       per-client token bucket
  LruCache                          hot-path cache for redirects
  UrlShortenerService               all business rules live here

com.schwab.shortener.web          <- thin adapter over core, using com.sun.net.httpserver
  HttpServerApp                     wires routes
  handlers/                         Create/Redirect/Metadata/Analytics/Delete/Health
  JsonUtil, HttpUtil                zero-dependency JSON + request/response helpers
```

**Why `com.sun.net.httpserver` instead of Spring Boot.** See [`04-testing-and-tradeoffs.md`](04-testing-and-tradeoffs.md) for the full trade-off — in short, this sandbox has no route to Maven Central, so a Spring Boot build could not be dependency-resolved or verified here. Rather than ship unverified framework code, the web layer uses the JDK's built-in HTTP server. Because the core is framework-agnostic already, swapping in Spring Boot later is a matter of replacing `web/` with `@RestController` adapters that call the exact same `UrlShortenerService` — zero business-logic changes required.

**API surface:**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/urls` | create a short URL (`url`, optional `customAlias`, optional `ttlSeconds`) |
| `GET` | `/{code}` | 302 redirect + records a click event |
| `GET` | `/api/urls/{code}` | metadata |
| `GET` | `/api/urls/{code}/analytics` | click count, referrer breakdown, recent events |
| `DELETE` | `/api/urls/{code}` | soft-delete (deactivate) |
| `GET` | `/health` | liveness |

**Reliability features implemented:** per-client token-bucket rate limiting (429 on exhaustion), TTL-based expiry (410 Gone once expired, plus a background sweeper thread), long-URL deduplication (reissuing the same code for a repeat long URL), an LRU cache in front of the repository for the hot redirect path, and loop prevention (refuses to shorten a URL that points back at the service's own host).

## 3. `orchestrator` — the agentic orchestration layer

This is the assignment's "critical differentiator." Full design rationale is in [`02-orchestration-model.md`](02-orchestration-model.md); the short version:

```
com.schwab.orchestrator.core
  WorkflowGraph        explicit DAG of StageDefinitions, cycle-checked, topologically layered
  StageDefinition       id, dependencies, entry/exit gates, retries, approval flag, guardrails, compensating action
  WorkflowEngine        the scheduler/executor (see below)
  WorkflowContext       shared artifact bag + append-only decision lineage (cross-stage state)
  AuditTrail            append-only, timestamped event log (audit-grade observability)
  GuardrailEngine        registry of named policy checks, opt-in per stage
  MetricsCollector       success rate, retry/rollback frequency, MTTR, end-to-end latency

com.schwab.orchestrator.agents     8 SDLC-stage "agents" (heuristic/rule-based + 2 that do real work)
com.schwab.orchestrator.guardrails Security / Compliance / Change-control policy checks
com.schwab.orchestrator.scenarios  graph wiring shared by all 3 scenarios + the 3 scenario drivers
com.schwab.orchestrator.web        REST API for interactive runs (start / status / approve / invalidate)
```

### Control flow

`WorkflowEngine.advance()` does **not** walk a precomputed static schedule. On every call it recomputes the set of "ready" stages from current stage statuses and the DAG's dependency edges, submits all of them to a bounded thread pool concurrently, waits for that round to finish, then loops. This one loop is what produces, without special-casing any of them:

- **Sequential execution** — a stage only becomes ready once every dependency is `SUCCEEDED`.
- **Parallel execution with synchronization** — independent stages in the same round run concurrently; a stage with multiple dependencies naturally "joins" once all of them land.
- **Pausing for human approval** — a stage requiring approval flips to `WAITING_APPROVAL` instead of executing; the ready-set empties out around it and `advance()` returns control with `RunStatus.PAUSED_FOR_APPROVAL`.
- **Rollback / skip cascades** — a permanently failed stage triggers a backward walk that runs compensating actions on already-succeeded, rollback-eligible dependencies, and a forward walk (via the next `computeReadyStages()` pass) that marks blocked descendants `SKIPPED`.
- **Dynamic re-planning** — `invalidate(stageId, reason)` marks a stage and all of its transitive dependents `STALE`; `STALE` is treated exactly like `PENDING` by the readiness check, so the same loop re-executes them on the next `advance()`.

See the sequence of screenshots-in-text (real captured runs) in [`03-scenarios.md`](03-scenarios.md).

### Key design decisions

1. **One shared `WorkflowContext` per run**, not per stage. Cross-stage context and decision lineage (`DecisionRecord`) are the actual audit requirement in the assignment ("preserve cross-stage context and decision lineage") — a per-stage context would have hidden this.
2. **Guardrails are opt-in per stage, not global.** A stage declares which named guardrails apply to it (`StageDefinition.guardrails("security")`). This keeps the policy surface explicit and inspectable instead of a hidden global interceptor.
3. **Approval is versioned.** Every time `invalidate()` re-plans a stage, its "plan version" is bumped, and a prior `APPROVED` decision no longer satisfies the approval gate. Re-approval is required after re-planning — see the ambiguous scenario, where `release-readiness` is approved twice (`APPROVED#0`, then `APPROVED#1`) because the underlying design changed between the two approvals.
4. **Rollback is a compensating action, not a version-control revert.** Because this prototype does not commit code to a real repository, "rollback" means running a declared `CompensatingAction` (e.g., marking a change plan reverted) rather than a git revert. See trade-offs doc.
5. **Safe-stop is a run-level circuit breaker**, separate from per-stage retries. A stage retries its own transient failures; safe-stop watches the *aggregate* failure count across the whole run and halts everything once a configurable threshold is crossed — modeling "stop and get a human" rather than retrying forever.
