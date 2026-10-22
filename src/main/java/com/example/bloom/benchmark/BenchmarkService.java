package com.example.bloom.benchmark;

import com.example.bloom.registration.AvailabilityResult;
import com.example.bloom.registration.RegistrationService;
import com.example.bloom.seed.MockUserGenerator;
import com.example.bloom.user.DatabaseLatencySimulator;
import com.example.bloom.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Runs the same set of availability checks with and without the Bloom filter and compares
 * database round trips, wall-clock time and correctness.
 */
@Service
public class BenchmarkService {

    private static final int WARM_UP_PROBES = 500;

    private final RegistrationService registrationService;
    private final UserRepository repository;
    private final DatabaseLatencySimulator latency;

    public BenchmarkService(RegistrationService registrationService, UserRepository repository,
                            DatabaseLatencySimulator latency) {
        this.registrationService = registrationService;
        this.repository = repository;
        this.latency = latency;
    }

    public synchronized BenchmarkResult run(BenchmarkRequest request) {
        List<String> probes = buildProbes(request.lookups(), request.takenRatio());
        long previousLatency = latency.getLatencyMicros();
        try {
            latency.setLatencyMicros(0);
            warmUp(probes);

            latency.setLatencyMicros(request.latencyMicros());
            Run without = execute(probes, false);
            Run with = execute(probes, true);
            return compare(request, without, with);
        } finally {
            latency.setLatencyMicros(previousLatency);
        }
    }

    private List<String> buildProbes(int lookups, double takenRatio) {
        int taken = (int) Math.round(lookups * takenRatio);
        List<String> probes = new ArrayList<>(repository.randomUsernames(taken));

        // Realistic new names often collide with the 200k existing ones ("ayse1234"), so drop
        // collisions up front to keep the taken/new split exactly as requested.
        MockUserGenerator candidates = new MockUserGenerator(System.nanoTime());
        Set<String> fresh = new LinkedHashSet<>();
        while (fresh.size() < lookups - taken) {
            List<String> batch = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                batch.add(candidates.nextUsername());
            }
            batch.removeAll(repository.findExistingUsernames(batch));
            batch.stream().limit(lookups - taken - fresh.size()).forEach(fresh::add);
        }
        probes.addAll(fresh);
        Collections.shuffle(probes, new Random());
        return probes;
    }

    private void warmUp(List<String> probes) {
        for (String probe : probes.subList(0, Math.min(WARM_UP_PROBES, probes.size()))) {
            registrationService.checkAvailability(probe, false);
            registrationService.checkAvailability(probe, true);
        }
    }

    private Run execute(List<String> probes, boolean useBloom) {
        List<AvailabilityResult> results = new ArrayList<>(probes.size());
        long lookupsBefore = repository.usernameLookupCount();
        long start = System.nanoTime();
        for (String probe : probes) {
            results.add(registrationService.checkAvailability(probe, useBloom));
        }
        long elapsedNanos = System.nanoTime() - start;
        return new Run(results, repository.usernameLookupCount() - lookupsBefore, elapsedNanos);
    }

    private static BenchmarkResult compare(BenchmarkRequest request, Run without, Run with) {
        long taken = 0;
        long absent = 0;
        long falsePositives = 0;
        long mismatches = 0;
        for (int i = 0; i < without.results.size(); i++) {
            AvailabilityResult truth = without.results.get(i);
            AvailabilityResult filtered = with.results.get(i);
            if (truth.available()) {
                absent++;
            } else {
                taken++;
            }
            if (filtered.falsePositive()) {
                falsePositives++;
            }
            if (truth.available() != filtered.available()) {
                mismatches++;
            }
        }

        return new BenchmarkResult(
                request.lookups(),
                taken,
                request.latencyMicros(),
                without.toModeResult(),
                with.toModeResult(),
                falsePositives,
                absent == 0 ? 0 : (double) falsePositives / absent,
                mismatches,
                with.elapsedNanos == 0 ? 0 : (double) without.elapsedNanos / with.elapsedNanos,
                without.databaseLookups == 0 ? 0
                        : 100.0 * (without.databaseLookups - with.databaseLookups) / without.databaseLookups);
    }

    private record Run(List<AvailabilityResult> results, long databaseLookups, long elapsedNanos) {

        BenchmarkResult.ModeResult toModeResult() {
            return new BenchmarkResult.ModeResult(databaseLookups, elapsedNanos / 1e6,
                    elapsedNanos / 1e3 / results.size());
        }
    }
}
