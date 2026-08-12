# Agentic SDLC System — URL Shortener

A working prototype for the "Build an Agentic Software Engineering System" assignment. It has two parts:

1. **`url-shortener/`** — the product being built: a URL shortener service with core APIs, analytics, and reliability features (rate limiting, TTL expiry, deduplication, caching).
2. **`orchestrator/`** — the agentic orchestration layer (the assignment's "critical differentiator"): a dependency-graph workflow engine that coordinates requirements → design → task decomposition → implementation → testing → documentation → release readiness, with entry/exit gates, parallel synchronization, human approval checkpoints, bounded retries, rollback, safe-stop, policy guardrails, audit-grade observability, reliability metrics, and dynamic re-planning.

Both modules are **pure Java 11+ with zero external dependencies** — no Maven, no Spring, no internet access required to build or run. See [`docs/04-testing-and-tradeoffs.md`](docs/04-testing-and-tradeoffs.md) for why, including the unusual build environment this was developed and verified in.

## Quick start

```bash
# From the repo root:
scripts/build.sh              # compiles both modules
scripts/test-all.sh           # runs all 49 automated tests (27 + 22)

scripts/run-url-shortener.sh  # starts the product on http://localhost:8080
scripts/run-scenario.sh greenfield   # runs one of the three orchestration scenarios
scripts/run-scenario.sh brownfield
scripts/run-scenario.sh ambiguous
scripts/run-all-scenarios.sh         # runs all three back to back

scripts/run-orchestrator-api.sh      # starts an interactive REST API on http://localhost:8090
```

Requires only a JDK 11+ on `PATH` (or `JAVA_HOME` set). `scripts/compile.sh` uses `javac` if present and otherwise falls back to invoking the compiler through `javax.tools` (see `scripts/Compile.java`) — both paths were exercised during development.

## Project layout

```
agentic-sdlc-url-shortener/
├── url-shortener/                  the product
│   ├── src/main/java/com/schwab/shortener/
│   │   ├── core/                   framework-agnostic domain + business logic (unit tested in isolation)
│   │   └── web/                    thin HTTP adapter (com.sun.net.httpserver) + handlers
│   └── src/test/java/              27 tests: unit + full-stack HTTP integration
├── orchestrator/                   the agentic orchestration layer
│   ├── src/main/java/com/schwab/orchestrator/
│   │   ├── core/                   WorkflowEngine, WorkflowGraph, gates, retries, rollback, audit, metrics
│   │   ├── agents/                 8 SDLC-stage agents (Requirements, Design, Testing, ...)
│   │   ├── guardrails/             Security / Compliance / Change-control policy checks
│   │   ├── scenarios/              graph wiring + the 3 demo scenarios
│   │   └── web/                    REST API for interactive runs
│   └── src/test/java/              22 tests against the engine using synthetic stages
├── docs/
│   ├── 01-architecture.md          components, control flow, key decisions
│   ├── 02-orchestration-model.md   how the engine satisfies each orchestration requirement
│   ├── 03-scenarios.md             the 3 required scenarios, with real captured output
│   ├── 04-testing-and-tradeoffs.md testing approach, limitations, trade-offs
│   ├── 05-final-engineering-summary.md  plan, rationale, risks, assumptions
│   └── generated/                  real execution traces + doc artifacts written by the agents themselves
└── scripts/                        build/test/run scripts (see Quick start)
```

## What's real vs. simulated

Everything in this repository actually runs — there is no scripted fake output. Specifically:

- The url-shortener is a real HTTP server; every endpoint, the rate limiter, TTL expiry, and deduplication were exercised live with `curl` during development.
- `TestingAgent` (inside the orchestrator) genuinely shells out to `java ... com.schwab.shortener.testkit.TestRunner` and parses the real pass/fail output — it does not fabricate a result.
- `CodebaseReasoningAgent` performs a real keyword-matching static scan of the actual `url-shortener/src` tree, not a hand-authored file list.
- The brownfield scenario's regression is a real failing test (`RegressionFixtureTest`) that the engine's rollback logic reacts to, not a hard-coded "pretend this failed" flag.
- `DocumentationAgent` writes real Markdown files to `docs/generated/`.
- All 49 tests and all 3 scenarios were run to completion and their output captured before this document was written.

## Documentation index

- [Architecture](docs/01-architecture.md)
- [Orchestration model](docs/02-orchestration-model.md)
- [Scenarios: greenfield, brownfield, ambiguous](docs/03-scenarios.md)
- [Testing approach, limitations, trade-offs](docs/04-testing-and-tradeoffs.md)
- [Final engineering summary](docs/05-final-engineering-summary.md)
