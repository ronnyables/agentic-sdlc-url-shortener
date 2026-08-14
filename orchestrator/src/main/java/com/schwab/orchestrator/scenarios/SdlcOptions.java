package com.schwab.orchestrator.scenarios;

import java.util.List;

/**
 * Wiring parameters shared by all three scenarios; differences between
 * scenarios are expressed via these fields.
 *
 * Points at {@code url-shortener-spring} (the Spring Boot / Maven rebuild)
 * by default. The original zero-dependency {@code url-shortener} module is
 * still present in the repo as a previously-verified reference
 * implementation - see docs/04-testing-and-tradeoffs.md for why both exist.
 */
public final class SdlcOptions {
    public boolean brownfield = false;
    public boolean simulateSecretLeakOnFirstAttempt = false;
    public String repoSrcRoot = "url-shortener-spring/src/main/java";
    public String mavenProjectDir = "url-shortener-spring";
    /** Passed to `mvn -Dtest=`; empty/null means "run Maven's default full test suite". */
    public List<String> testClassNames = List.of();
    public String docOutputDir = "docs/generated";
    public boolean requireApprovalForRelease = true;
}
