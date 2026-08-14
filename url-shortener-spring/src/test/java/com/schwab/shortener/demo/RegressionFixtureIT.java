package com.schwab.shortener.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOT part of the product's real test suite, and deliberately named *IT
 * (not *Test) so Maven Surefire's default include pattern ({@code **}/*Test.java)
 * does not pick it up on a plain {@code mvn test} run - it only runs when
 * explicitly targeted via {@code -Dtest=RegressionFixtureIT}, which is
 * exactly what the orchestrator's brownfield scenario does.
 *
 * It exists purely as a fault-injection fixture so the brownfield
 * orchestration scenario can drive a real failing `mvn test` run through
 * TestingAgent and observe the engine's rollback + skip cascade react to a
 * genuine (simulated) regression, rather than a hard-coded "pretend this
 * failed" flag.
 */
public class RegressionFixtureIT {

    @Test
    void simulatedRegressionInAnalyticsRollup() {
        // Stands in for "the change we just shipped broke something."
        assertThat(false).as("simulated regression: daily rollup count did not match raw click count").isTrue();
    }
}
