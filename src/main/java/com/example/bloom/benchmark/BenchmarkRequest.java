package com.example.bloom.benchmark;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param lookups        how many availability checks to run in each mode
 * @param takenRatio     share of probes drawn from existing usernames (the rest are realistic new names)
 * @param latencyMicros  simulated database round trip for this run
 */
public record BenchmarkRequest(
        @Min(1) @Max(20_000) int lookups,
        @DecimalMin("0.0") @DecimalMax("1.0") double takenRatio,
        @Min(0) @Max(20_000) long latencyMicros) {
}
