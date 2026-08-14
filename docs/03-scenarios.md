# Scenarios: Greenfield, Brownfield, Ambiguous

All three were actually executed (`scripts/run-all-scenarios.sh`); the excerpts below are copied from the real captured traces in `docs/generated/*-trace.txt`, not written by hand. Run them yourself with `scripts/run-scenario.sh <name>` — the run IDs will differ but the behavior will match.

Each scenario shares the same graph shape (`orchestrator/scenarios/WorkflowGraphs.java`) and differs only in the requirement text and options passed in, which is itself a demonstration that the orchestration layer is generic and not hand-tuned per scenario.

## Scenario 1 — Greenfield: build the URL shortener from scratch

**Requirement:** "Build a URL shortener service from scratch with core APIs (create, redirect, delete), click analytics, and reliability features (rate limiting, TTL expiry, deduplication)."

**What it demonstrates:** requirement understanding on a well-specified ask (no ambiguity flagged), the full 7-stage pipeline, a real parallel synchronization point, and a human approval checkpoint at the highest-impact step.

Real timestamps from the captured trace show `testing` and `documentation` both entering within microseconds of each other and running concurrently — `documentation` (fast, just writes a file) finishes at `19:29:08.843`, while `testing` (slow — it shells out to a real JVM subprocess running 27 tests) doesn't finish until `19:29:10.153`, roughly 1.3 seconds later. `release-readiness` only enters after both:

```
19:29:08.786714  STAGE_ENTERED   testing        entering stage
19:29:08.786800  STAGE_ENTERED   documentation  entering stage
19:29:08.843826  STAGE_SUCCEEDED documentation  Documentation written to docs/generated/GF-...-summary.md
19:29:10.153369  STAGE_SUCCEEDED testing        All 27 tests passed
19:29:10.153737  STAGE_ENTERED   release-readiness  entering stage
19:29:10.158361  STAGE_WAITING_APPROVAL  release-readiness  paused for human approval
19:29:10.182252  STAGE_APPROVED  release-readiness  human:ronny.ables  Verified test summary and generated docs; approved for release.
19:29:10.185510  RUN_COMPLETED   -              final status=COMPLETED_SUCCESS
```

**Result:** `COMPLETED_SUCCESS`, all 7 stages `SUCCEEDED`, `successRate=100.0%`, zero retries, zero rollbacks.

## Scenario 2 — Brownfield: enhance the existing service (with a real regression)

**Requirement:** "Add per-referrer daily analytics rollups and tune the rate limiter for the existing URL shortener service without breaking current API contracts."

**What it demonstrates:** codebase reasoning against the real source tree, and — deliberately — a genuine regression caught by actually running tests, so the rollback/skip machinery has something real to react to instead of a scripted failure.

`CodebaseReasoningAgent` keyword-matched the requirement ("rollups", "rate limiter") against the actual `url-shortener/src` tree and flagged 8 real files as impacted, including `RateLimiter.java`, `ClickEventStore.java`, and `AnalyticsSummary.java` — a genuine (if simple) static-analysis result, not a hard-coded list.

The test class list for this scenario intentionally includes `com.schwab.shortener.demo.RegressionFixtureTest`, a fixture that always fails (clearly commented in its source as "not part of the product's real test suite"), simulating a regression introduced by the change. `TestingAgent` ran it for real:

```
STAGE_SUCCEEDED  codebase-reasoning  8 file(s) flagged as impacted using a targeted scan
STAGE_ATTEMPT_FAILED  testing  test run reported failures or produced no parseable summary(...)  [16 passed, 1 failed]
STAGE_RETRY_SCHEDULED  testing  retrying in 20ms (attempt 2)
STAGE_ATTEMPT_FAILED  testing  [16 passed, 1 failed again — deterministic fixture]
STAGE_FAILED     testing   exhausted retries and fallback
STAGE_ROLLED_BACK implementation  compensating for downstream failure at 'testing': ...
STAGE_SKIPPED    release-readiness  one or more dependencies failed or were rolled back
```

**Result:** `COMPLETED_WITH_FAILURES`. Final stage statuses: `requirements/codebase-reasoning/design/task-decomposition/documentation = SUCCEEDED`, `testing = FAILED`, `implementation = ROLLED_BACK`, `release-readiness = SKIPPED`. Metrics: `successRate=62.5% retries=1 rollbackFreq=0.13`. Note `documentation` stayed `SUCCEEDED` — it's a *sibling* of `testing` (both depend only on `implementation`), not a dependent, so it correctly isn't rolled back or skipped. This is the governance path working as designed: a real regression stopped the release without a human having to notice it manually.

## Scenario 3 — Ambiguous: "Make the analytics better."

**Requirement (verbatim):** "Make the analytics better."

**What it demonstrates three things end to end, all real:**

**(a) Ambiguity detection with logged assumptions, not silent guessing.** `RequirementsAgent` flagged this as ambiguous (too short, contains the vague term "better") and recorded exactly which clarifying questions it would ask a human and which assumption it applied instead of blocking:

> clarifying questions: *"The request is very brief - which specific capability should change...?"*, *"Terms like 'better'/'improve' are subjective - what measurable outcome defines success?"*
> assumptions applied: *"Treated as a request to extend the existing URL-shortener analytics capability..."*, *"Interpreted 'better/improve' as: add concrete, testable functionality..."*

**(b) A real guardrail block-then-recover cycle.** This scenario's `ImplementationAgent` is configured to draft a first attempt that looks like it leaks an API key (`api_key = "sk_live_..."`). `SecurityGuardrail` caught it and blocked the exit gate; the stage retried; the second attempt was clean and passed. This is not a hypothetical — the captured metrics show `implementation`'s attempt count go to 2 and `mttr=433ms` (on the re-plan run) is entirely attributable to this retry:

```
STAGE_ATTEMPT_STARTED  implementation  attempt 1/3
GUARDRAIL_BLOCKED      implementation  guardrail:security  possible hardcoded secret detected...
STAGE_RETRY_SCHEDULED  implementation  retrying in 20ms (attempt 2)
STAGE_ATTEMPT_STARTED  implementation  attempt 2/3
STAGE_SUCCEEDED        implementation  Change plan drafted (attempt 2)
```

**(c) Dynamic re-planning after a human clarifies.** After the first full run completes and is approved (`APPROVED#0`), a human reviewer clarifies what they actually meant: *"Add per-referrer breakdown and daily rollups to the analytics endpoint."* Calling `invalidate("requirements", ...)` marks `requirements` and all six downstream stages `STALE`; the engine re-executes the whole pipeline against the clarified text with no special "replan mode" — it's the same scheduler loop. `design.apiChanges` changes from a generic rollup mention to the specific `GET /api/urls/{code}/analytics ?since=&groupBy=day` change. `release-readiness` requires a **fresh** approval — the engine will not let the old `APPROVED#0` decision satisfy the gate for the new plan, so a second approval (`APPROVED#1`) is required and recorded.

**Result:** `COMPLETED_SUCCESS` (both before and after re-plan). First-pass metrics: `retries=2 retryFreq=0.29 mttr=433ms`. Full decision lineage shows both interpretations side by side, each with its own agent decisions and its own approval — the audit trail literally documents "we shipped once under an assumption, then re-shipped correctly once clarified," which is the point of the exercise.

## Interactive alternative: the REST API

All three scenarios are also runnable through the orchestrator's REST API (`scripts/run-orchestrator-api.sh`) instead of the console-driven main classes, for anyone who'd rather poke at it with `curl`/Postman:

```bash
curl -s -X POST localhost:8090/runs -d '{"scenario":"greenfield"}'
curl -s localhost:8090/runs/<runId>
curl -s -X POST localhost:8090/runs/<runId>/approve/release-readiness \
     -d '{"approved":true,"approver":"you","comment":"looks good"}'
curl -s localhost:8090/runs/<runId>/metrics
curl -s localhost:8090/runs/<runId>/audit
```

This was exercised live during development (greenfield run started, inspected mid-pause, approved, and completed via HTTP) — see `02-orchestration-model.md` for what each endpoint proves.
