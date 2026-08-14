# The Spring Boot Rebuild (`url-shortener-spring`)

## Why a second module exists

The original `url-shortener` module (docs `01`-`05`) is a zero-dependency, JDK-only implementation, built that way specifically because this sandbox has no route to Maven Central. It is fully compiled, run, and tested by me, live, with captured evidence — that's still true and that module is untouched.

`url-shortener-spring/` is a from-scratch rebuild of the same product on **Spring Boot 3.2 + Maven + Spring Data JPA + H2 (with a Postgres profile)** — the stack most engineering teams would actually expect for a production URL shortener, requested explicitly as a follow-up. Same API surface, same business rules (validation, dedup, TTL expiry, rate limiting, loop prevention), same reliability features, re-architected onto a real framework and a real relational database instead of hand-rolled HTTP handling and in-memory maps.

The orchestrator (`orchestrator/`) is unchanged in design and now targets this module by default — see "What changed in the orchestrator" below.

## Update: a real build failure, and what it changed

The first version of this module used Lombok (`@Getter`/`@Setter`/`@Builder`) on the two JPA entities. On the first actual `mvn compile` run (on a real machine, not this sandbox), it failed with ~27 "cannot find symbol" errors for methods like `builder()`, `getCode()`, `isActive()` — the classic signature of Lombok's annotation processor not running: the class compiles (it's found by name) but none of its generated members exist.

Two changes came out of that:

1. **`pom.xml` now explicitly registers Lombok as an `annotationProcessorPath`** on `maven-compiler-plugin` rather than relying on Maven auto-detecting it from the compile classpath - the auto-detection is what didn't happen, on whatever combination of Maven/JDK/OS produced the failure.
2. **More importantly, Lombok was removed from the two entity classes entirely.** `ShortUrl` and `ClickEvent` now have hand-written getters/setters/a builder (a few dozen extra lines, entirely mechanical). This isn't a workaround layered on top of the first fix - it's the more defensible one, because it removes an entire category of "did the annotation processor actually run on your machine" risk rather than just patching the one config that triggered it. Given I can't compile-test this module in my own sandbox (see below), minimizing build-time unknowns matters more here than it would on a project I could verify directly.

Everything else in this document was written before that fix and remains accurate; the affected files were re-syntax-checked afterward (see the verification list below) and no other files reference Lombok.

## Honest disclosure: what could and couldn't be verified here

This is the same sandbox as before: still no `mvn` on `PATH`, still no route to Maven Central (confirmed again — `mvn -version` returns "command not found", and there is no way to install it or fetch dependencies here). That means **I could not run `mvn test` or `mvn spring-boot:run` against this module myself**, unlike the zero-dependency build where every line was actually executed.

What I *did* do instead, in order of rigor:

1. **Syntax-validated all 29 source files** with a real Java grammar parser (`tree-sitter-java`, which — unlike the `javalang` library used for the first module — correctly handles Java 17 syntax including records, so this check is meaningful for this codebase). Zero syntax errors.
2. **Validated `pom.xml` as well-formed XML** and **`application.yml`/`docker-compose.yml` as well-formed YAML** (both parsed cleanly, including the multi-document Spring profile syntax in `application.yml`).
3. **Unit-tested the one piece of new orchestrator logic in isolation**: `TestingAgent` now parses Maven Surefire's summary line via regex instead of the old TestRunner subprocess output. I extracted that regex and ran it in Python against two realistic, hand-verified Maven Surefire output samples (one all-green, one with a failure mixed into per-class output plus a final aggregate) to confirm the "take the last match = aggregate" strategy is correct. Both matched exactly. This doesn't prove the Spring code compiles, but it does prove the piece of new orchestration logic that depends on Maven's output format is correct.
4. **Actually ran the greenfield scenario against this module.** Everything through `implementation` executed for real (`RequirementsAgent`, `DesignAgent`, `TaskDecompositionAgent`, `ImplementationAgent` all ran as plain Java, no Maven needed). At the `testing` stage, `TestingAgent` correctly detected that `mvn` isn't on `PATH`, failed with a clear message (`could not launch Maven ('mvn' on PATH is required...)`), retried once per its configured policy, then failed permanently — at which point the engine's **rollback cascade actually fired**: `implementation` was rolled back via its compensating action, and `release-readiness` was skipped because a dependency failed. Final metrics: `successRate=57.1% retries=1 rollbackFreq=0.14`.

That last point is worth being direct about: **this is not a scenario I'm claiming succeeded.** It's evidence that (a) the new Spring module's package structure and the orchestrator's updated file-scanning/wiring are correct up to the point Maven is needed, and (b) the orchestration engine's failure-handling machinery (retry → permanent failure → rollback → skip cascade), which was already unit-tested with synthetic stages, also behaves correctly against a *real* infrastructure failure, not just a synthetic one. The full trace is in `docs/generated/greenfield-GF-*-trace.txt`.

**What this means for you:** run `mvn -f url-shortener-spring/pom.xml test` (or `scripts/run-scenario-spring.sh greenfield`, which does the same thing) on a machine with Maven and internet access, and I expect it to compile and pass — I wrote every line carefully and know these APIs well — but I am not claiming to have verified it the way I verified the zero-dependency module, and you should treat "builds and passes on your machine" as the actual first test of this code, not a formality.

## What changed in the orchestrator

Only two agents and the wiring around them:

- **`TestingAgent`** — constructor changed from `(testClasspath, testClassNames)` to `(mavenProjectDir, explicitTestClasses)`. It now runs `mvn -q -B -f <projectDir>/pom.xml [-Dtest=Class1,Class2] test` instead of the old `java -cp ... TestRunner` subprocess, and parses Surefire's `Tests run: X, Failures: Y, Errors: Z, Skipped: W` summary instead of the old custom format.
- **`CodebaseReasoningAgent`** — its keyword→hint map was updated for the new package layout (`@Cacheable`/`@CacheEvict` instead of `LruCache`, `RedirectController` instead of `RedirectHandler`; most hints — `AnalyticsSummary`, `RateLimiter`, `AliasConflictException`, `ttlSeconds`, etc. — were unchanged because I kept the same class/field names on purpose when porting).
- **`SdlcOptions`** defaults now point at `url-shortener-spring` instead of `url-shortener`.
- **The fault-injection fixture** for the brownfield scenario was ported from `RegressionFixtureTest.java` (old module, plain method-name convention) to `RegressionFixtureIT.java` (new module) — renamed specifically so Maven Surefire's default `**/*Test.java` inclusion pattern does **not** pick it up on a plain `mvn test` run; it only executes when explicitly targeted via `-Dtest=RegressionFixtureIT`, which is exactly what the brownfield scenario does. This was a deliberate design fix, not an oversight — see the class's own Javadoc.

The workflow engine itself (`WorkflowGraph`, `WorkflowEngine`, gates, retries, rollback, safe-stop, guardrails, audit, metrics) is **byte-for-byte unchanged**, and its 22 unit tests (which use synthetic stages, not the real agents) still pass — re-run and re-verified after every change above, exactly as before.

## Setup (on a machine with Maven + internet access)

```bash
cd url-shortener-spring

# Run with the default embedded H2 database - no external services needed:
mvn spring-boot:run
mvn test

# Or with Postgres:
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres

# Run the orchestrator against this module (from the repo root):
java -cp "orchestrator/bin:orchestrator/src/main/java" com.schwab.orchestrator.scenarios.GreenfieldScenario
# (build the orchestrator first with scripts/build.sh, or add a scripts/*-spring.sh
#  variant that skips building the old url-shortener module - see scripts/README notes)
```

API surface is identical to the reference build (`docs/01-architecture.md`), plus Spring Boot Actuator's `/actuator/health` and the H2 console at `/h2-console` (enabled for local development only).

## Architecture differences at a glance

| | `url-shortener` (reference) | `url-shortener-spring` (rebuild) |
|---|---|---|
| Build | none (plain `javac`) | Maven |
| Web | `com.sun.net.httpserver` | Spring MVC (`@RestController`) |
| Persistence | in-memory `ConcurrentHashMap` | Spring Data JPA + H2 (default) / Postgres (profile) |
| Caching | hand-rolled `LruCache` | Spring Cache abstraction (`@Cacheable`/`@CacheEvict`) |
| Validation | manual | Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Positive`) |
| DTOs | POJOs | Java 17 `record`s |
| JSON | hand-rolled `JsonUtil` | Jackson (Spring Boot default) |
| Tests | custom `MiniTest`/`TestRunner` | JUnit 5 + Mockito + AssertJ + `@DataJpaTest`/`@WebMvcTest`/`@SpringBootTest` |
| Verified how | actually compiled and run in this sandbox | syntax-checked (Java 17-aware parser) + config-validated + logic-unit-tested in isolation; **not** compiled here (see above) |

Both are legitimate engineering artifacts; which one is "correct" depends entirely on what you're optimizing for — see `docs/04-testing-and-tradeoffs.md` for that discussion, now applicable to both.
