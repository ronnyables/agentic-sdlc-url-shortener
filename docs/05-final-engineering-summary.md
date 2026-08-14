# Final Engineering Summary

## Plan and rationale

The assignment asks for two things in one prototype: a URL shortener (the product), and an agentic orchestration layer that coordinates the SDLC to build/enhance it (the differentiator). I treated the orchestration layer as the primary deliverable and the URL shortener as both a real product *and* the orchestrator's test subject — every scenario actually builds, tests, and documents the real service, so the two halves validate each other instead of being demoed independently.

Sequence followed:

1. Interpreted the assignment itself as a requirement (see "Requirement Understanding" below) and normalized it into a concrete scope before writing code.
2. Discovered this sandbox has no reachable JVM package/binary source (Maven Central, Adoptium, apt all blocked by a proxy allowlist) — see `04-testing-and-tradeoffs.md`. Rather than write unverifiable framework code, re-scoped to a zero-dependency Java build, which turned out to still support a genuine compiler via `javax.tools`.
3. Built `url-shortener`'s core domain and web layer, with tests, verified live via `curl` (rate limiting, TTL expiry, dedup, alias conflicts all actually triggered).
4. Built the orchestration engine (`WorkflowGraph`/`WorkflowEngine`) as a generic, agent-agnostic scheduler, unit tested against synthetic stages before any SDLC agent existed — so the engine's correctness doesn't depend on the agents being "convincing."
5. Built 8 SDLC-stage agents, 2 of which do real work rather than simulate it (`TestingAgent` shells out to the real test suite; `CodebaseReasoningAgent` statically scans the real source tree).
6. Wired the 3 required scenarios (greenfield/brownfield/ambiguous) against the shared graph, ran all of them, and used the *real* captured output (not hand-written prose) for the scenario documentation.
7. Added a REST API as a second, interactive way to drive the same engine.
8. Wrote this documentation set last, from the actual test/scenario output already on disk.

## Requirement understanding applied to the assignment itself

Reading the assignment closely: "workflow orchestration" is explicitly called out as the *critical differentiator* and is graded on nine specific capabilities (dependency graph with gates, parallel sync, decision lineage, approval checkpoints, retries/fallback/rollback/safe-stop, guardrails, observability, metrics, dynamic re-planning) — not on how sophisticated the SDLC agents' text generation is. That reading drove the biggest scope decision in this project: spend the engineering budget on making the orchestration engine's nine capabilities *genuinely, verifiably work* (with unit tests for each one in isolation), rather than on making the agents produce more elaborate prose. `docs/02-orchestration-model.md` maps each of the nine capabilities to its implementation and test evidence directly.

## Artifacts produced

- `url-shortener/` — working HTTP service, 24 source files, 27 tests (all passing).
- `orchestrator/` — working orchestration engine + 8 agents + 3 guardrails + REST API, 44 source files, 22 tests (all passing).
- 3 executed scenarios with captured audit trails (`docs/generated/*-trace.txt`) and agent-written documentation (`docs/generated/*-summary.md`).
- `scripts/` — build/test/run automation requiring nothing but a JDK.
- This documentation set (`docs/01` through `docs/05`).

## Risks, trade-offs, and validation — see `docs/04-testing-and-tradeoffs.md` for the full table

Headline items: in-memory persistence (documented, behind swappable interfaces), no real web framework (documented, core is framework-agnostic so this is a thin-layer swap), no LLM in the loop (documented — agents are deterministic and rule-based, but shaped so an LLM-backed `StageExecutor` is a drop-in replacement), and `ImplementationAgent` produces a change plan rather than a literal code diff (the single biggest scope cut, explained and justified). Validation was not "I wrote tests" — it was "I ran 49 tests and 3 end-to-end scenarios and I'm citing their actual output," which is the standard this document tries to hold itself to throughout.

## Assumptions

- "Build a URL shortener from scratch" (the greenfield scenario's requirement, taken near-verbatim from the assignment's own scenario description) was treated as well-specified and normalized directly.
- Where the assignment was itself silent on a detail (e.g., exact rate-limit thresholds, exact TTL semantics, exact alias validation rules), I chose conservative, documented defaults and stated them in code comments and `01-architecture.md` rather than leaving them implicit.
- "Human approval" is simulated by a scripted decision in the scenario drivers, clearly labeled as such in both the code and the console output (`>>> Simulating reviewer decision: APPROVE`) — the engine itself has no knowledge of who or what supplies the decision; the REST API demonstrates a real external caller supplying it over HTTP instead.

## Limitations

Everything in this section is also in `04-testing-and-tradeoffs.md` with more detail; the short list: single-node, in-memory state throughout; no real code-diff generation; heuristic (not LLM-based) requirement interpretation and guardrails; no distributed rate limiting/caching; rollback is a compensating action, not a VCS-level revert. None of these were hidden or discovered late — they were made as explicit, reasoned trade-offs during design, each with a stated reason and a stated swap-in path.
