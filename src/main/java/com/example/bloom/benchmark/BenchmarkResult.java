package com.example.bloom.benchmark;

public record BenchmarkResult(int lookups,
                              long takenProbes,
                              long latencyMicros,
                              ModeResult withoutBloom,
                              ModeResult withBloom,
                              long falsePositives,
                              double observedFalsePositiveRate,
                              long mismatchedAnswers,
                              double speedup,
                              double databaseLookupsSavedPercent) {

    public record ModeResult(long databaseLookups, double elapsedMillis, double averageMicrosPerCheck) {
    }
}
