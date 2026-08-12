package com.schwab.orchestrator.scenarios;

import java.util.List;

/** Wiring parameters shared by all three scenarios; differences between scenarios are expressed via these fields. */
public final class SdlcOptions {
    public boolean brownfield = false;
    public boolean simulateSecretLeakOnFirstAttempt = false;
    public String repoSrcRoot = "url-shortener/src/main/java";
    public String testClasspath = "url-shortener/bin";
    public List<String> testClassNames = List.of("com.schwab.shortener.UrlShortenerServiceTest");
    public String docOutputDir = "docs/generated";
    public boolean requireApprovalForRelease = true;
}
