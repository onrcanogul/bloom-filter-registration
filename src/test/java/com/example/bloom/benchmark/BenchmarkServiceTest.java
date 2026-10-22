package com.example.bloom.benchmark;

import com.example.bloom.user.DatabaseLatencySimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.seed.user-count=5000", "app.db.simulated-latency-micros=0"})
class BenchmarkServiceTest {

    @Autowired
    BenchmarkService benchmark;

    @Autowired
    DatabaseLatencySimulator latency;

    @Test
    void filterSkipsMostDatabaseLookupsWithoutChangingAnyAnswer() {
        BenchmarkResult result = benchmark.run(new BenchmarkRequest(1_000, 0.2, 200));

        assertThat(result.mismatchedAnswers()).isZero();
        assertThat(result.withoutBloom().databaseLookups()).isEqualTo(1_000);
        // ~200 taken probes must hit the DB, plus ~1% false positives among the rest
        assertThat(result.withBloom().databaseLookups()).isBetween(result.takenProbes(), result.takenProbes() + 40);
        assertThat(result.withBloom().databaseLookups()).isEqualTo(result.takenProbes() + result.falsePositives());
        assertThat(result.speedup()).isGreaterThan(2);
    }

    @Test
    void honoursTheRequestedTakenRatioExactly() {
        BenchmarkResult result = benchmark.run(new BenchmarkRequest(1_000, 0.3, 0));

        assertThat(result.takenProbes()).isEqualTo(300);
    }

    @Test
    void restoresConfiguredLatencyAfterTheRun() {
        benchmark.run(new BenchmarkRequest(50, 0.5, 300));

        assertThat(latency.getLatencyMicros()).isZero();
    }
}
