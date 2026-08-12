package com.schwab.shortener.demo;

import static com.schwab.shortener.testkit.MiniTest.*;

/**
 * NOT part of the product's real test suite (it is never included in the
 * class list used by scripts/test-url-shortener.sh or the CI-equivalent run
 * in docs/04-testing-and-tradeoffs.md). It exists purely as a fault-injection
 * fixture so the brownfield orchestration scenario can drive a *real* failing
 * `mvn test`-equivalent run through TestingAgent and observe the engine's
 * rollback + skip cascade react to a genuine (simulated) regression, rather
 * than a hard-coded "pretend this failed" flag.
 */
public class RegressionFixtureTest {
    public void testSimulatedRegressionInAnalyticsRollup() {
        // Stands in for "the change we just shipped broke something."
        assertTrue(false, "simulated regression: daily rollup count did not match raw click count");
    }
}
