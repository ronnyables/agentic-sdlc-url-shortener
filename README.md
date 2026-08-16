# Agentic SDLC System — URL Shortener

A working prototype for the "Build an Agentic Software Engineering System" assignment. It has three parts:

1. **`url-shortener-spring/`** — the product, on **Spring Boot 3 / Maven / Spring Data JPA / H2 (or Postgres)**. This is the primary, production-style implementation.
2. **`url-shortener/`** — the same product as a **zero-dependency, pure-JDK reference build** (no Maven, no framework). Kept because it's the one implementation I could fully compile, run, and test *inside this build sandbox*, which has no route to Maven Central — see "Two implementations, one honest reason" below.
3. **`orchestrator/`** — the agentic orchestration layer (the assignment's "critical differentiator"): a dependency-graph workflow engine coordinating requirements → design → task decomposition → (codebase reasoning, brownfield) → implementation → testing → documentation → release readiness, with entry/exit gates, parallel synchronization, human approval checkpoints, bounded retries, rollback, safe-stop, policy guardrails, audit-grade observability, reliability metrics, and dynamic re-planning. Unchanged in design between both product builds; targets `url-shortener-spring` by default.

## Two implementations, one honest reason

This was built and verified in a sandboxed environment with no route to Maven Central, npm's CDN, Adoptium, or apt — confirmed by directly testing connectivity to about 15 candidate hosts. That ruled out compiling a Spring Boot build here. Rather than either (a) skip Spring Boot entirely, or (b) write it and quietly hope it compiles, I did both: a zero-dependency build I could fully verify by actually running it, and a Spring Boot rebuild written to the same spec, verified as rigorously as this sandbox allows (syntax-parsed with a Java-17-aware grammar, config files validated, the one piece of new orchestration logic unit-tested in isolation, and one scenario actually run against it end-to-end up to the point Maven is required). Full details, including exactly what that last point means and real captured evidence, are in [`docs/06-spring-boot-rebuild.md`](docs/06-spring-boot-rebuild.md).

**If you have Maven and a normal internet connection**, `url-shortener-spring/` is the one to run — start there.

## Quick start

### Spring Boot build (recommended — requires Maven + internet access)

**Prerequisites:** JDK **17 or 21** on `PATH`/`JAVA_HOME` (Spring Boot 3.2.5's bundled Mockito/Byte Buddy version doesn't support very new JDKs — e.g. Java 26 fails `mvn test` with a Byte Buddy instrumentation error; if `java -version` shows something newer than 21 and you hit that, install 21 and point `JAVA_HOME` at it: `export JAVA_HOME=$(/usr/libexec/java_home -v21)`).

```bash
scripts/test-spring.sh                     # mvn test — unit + web-slice + repository + full-stack integration tests
scripts/run-url-shortener-spring.sh        # starts on http://localhost:8080 (embedded H2)
scripts/run-scenario-spring.sh greenfield  # orchestrator drives a real `mvn test` run against this module
scripts/run-scenario-spring.sh brownfield
scripts/run-scenario-spring.sh ambiguous
```

Once it's running, try it:

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"hhttps://en.wikipedia.org/wiki/URL_shortening"}'
# -> {"code":"aB3xY9","shortUrl":"http://localhost:8080/aB3xY9",...}

curl -iL http://localhost:8080/aB3xY9   # swap in the code you got back; follows the redirect
```

Note the request field is `url`, not `longUrl`. There's no page at bare `/` — this is an API-only service, so hitting `http://localhost:8080/` directly returns a 404 by design.

### Zero-dependency reference build (requires only a JDK — what I verified directly in this sandbox)

```bash
scripts/build.sh              # compiles both the reference product and the orchestrator
scripts/test-all.sh           # runs all 49 automated tests (27 + 22)
scripts/run-url-shortener.sh  # starts the reference product on http://localhost:8080
scripts/run-scenario.sh greenfield   # runs a scenario against the reference build
scripts/run-scenario.sh brownfield
scripts/run-scenario.sh ambiguous
scripts/run-all-scenarios.sh
scripts/run-orchestrator-api.sh      # interactive REST API on http://localhost:8090
```

`scripts/compile.sh` (used by the reference-build scripts) uses `javac` if present and otherwise falls back to invoking the compiler through `javax.tools` — both paths were exercised during development, see `docs/04-testing-and-tradeoffs.md`.

## Project layout

```
agentic-sdlc-url-shortener/
├── url-shortener-spring/           the product (Spring Boot / Maven / JPA)
│   ├── src/main/java/com/schwab/shortener/
│   │   ├── model/, repository/       JPA entities + Spring Data repositories
│   │   ├── service/                  business logic (framework-light, Mockito-testable)
│   │   ├── web/, web/dto/            @RestController endpoints + request/response records
│   │   └── config/                   scheduled expiry sweeper
│   └── src/test/java/                unit (Mockito) + @DataJpaTest + @WebMvcTest + @SpringBootTest
├── url-shortener/                  the product (zero-dependency reference build)
│   ├── src/main/java/com/schwab/shortener/
│   │   ├── core/                     framework-agnostic domain + business logic
│   │   └── web/                      thin HTTP adapter (com.sun.net.httpserver)
│   └── src/test/java/                27 tests: unit + full-stack HTTP integration
├── orchestrator/                   the agentic orchestration layer
│   ├── src/main/java/com/schwab/orchestrator/
│   │   ├── core/                     WorkflowEngine, WorkflowGraph, gates, retries, rollback, audit, metrics
│   │   ├── agents/                   8 SDLC-stage agents (Requirements, Design, Testing, ...)
│   │   ├── guardrails/                Security / Compliance / Change-control policy checks
│   │   ├── scenarios/                graph wiring + the 3 demo scenarios
│   │   └── web/                      REST API for interactive runs
│   └── src/test/java/                22 tests against the engine using synthetic stages
├── docs/
│   ├── 01-architecture.md            reference-build architecture, control flow, key decisions
│   ├── 02-orchestration-model.md     how the engine satisfies each orchestration requirement
│   ├── 03-scenarios.md               the 3 required scenarios against the reference build, real output
│   ├── 04-testing-and-tradeoffs.md   testing approach, limitations, trade-offs
│   ├── 05-final-engineering-summary.md  plan, rationale, risks, assumptions
│   ├── 06-spring-boot-rebuild.md     the Spring Boot rebuild: what changed, what's verified, setup
│   └── generated/                    real execution traces + doc artifacts written by the agents themselves
└── scripts/                        build/test/run scripts for both builds (see Quick start)
```

## What's real vs. simulated

Everything actually runs — no scripted fake output — with the one clearly-disclosed exception of Maven-dependent steps against `url-shortener-spring`, explained above and in `docs/06-spring-boot-rebuild.md`. Specifically:

- The reference-build url-shortener is a real HTTP server; every endpoint, the rate limiter, TTL expiry, and deduplication were exercised live with `curl`.
- `TestingAgent` genuinely shells out — to the reference build's `TestRunner` subprocess, or to `mvn test` for the Spring build — and parses the real pass/fail output; it does not fabricate a result.
- `CodebaseReasoningAgent` performs a real keyword-matching static scan of whichever product source tree it's pointed at, not a hand-authored file list.
- The brownfield scenario's regression is a real failing test (`RegressionFixtureTest` / `RegressionFixtureIT`) that the engine's rollback logic reacts to.
- `DocumentationAgent` writes real Markdown files to `docs/generated/`.
- All 49 reference-build tests, all 22 orchestrator engine tests, and all 3 scenarios (against the reference build) were run to completion with captured output. The greenfield scenario was also run against the Spring build, and its real (expected, and explained) partial-failure trace — everything through `implementation` succeeds, `testing` fails because `mvn` isn't available in this sandbox, and the engine's rollback/skip cascade correctly reacts to that real failure — is in `docs/generated/`.

## Documentation index

- [Architecture (reference build)](docs/01-architecture.md)
- [Orchestration model](docs/02-orchestration-model.md)
- [Scenarios: greenfield, brownfield, ambiguous (reference build)](docs/03-scenarios.md)
- [Testing approach, limitations, trade-offs](docs/04-testing-and-tradeoffs.md)
- [Final engineering summary](docs/05-final-engineering-summary.md)
- [The Spring Boot rebuild: what changed, what's verified, setup](docs/06-spring-boot-rebuild.md)
