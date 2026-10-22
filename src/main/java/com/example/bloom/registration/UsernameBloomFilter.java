package com.example.bloom.registration;

import com.example.bloom.seed.MockDataSeeder;
import com.example.bloom.user.UserRepository;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory index of every taken username, backed by Guava's {@link BloomFilter}.
 * Warmed from the database at startup and kept in sync on every registration.
 */
@Component
public class UsernameBloomFilter {

    private static final Logger log = LoggerFactory.getLogger(UsernameBloomFilter.class);

    private final BloomFilter<String> filter;
    private final long expectedInsertions;
    private final double targetFalsePositiveRate;

    // MockDataSeeder is injected only to guarantee the database is populated before warm-up.
    public UsernameBloomFilter(UserRepository repository,
                               MockDataSeeder seeder,
                               @Value("${app.bloom.expected-insertions}") long expectedInsertions,
                               @Value("${app.bloom.false-positive-rate}") double falsePositiveRate) {
        this.expectedInsertions = expectedInsertions;
        this.targetFalsePositiveRate = falsePositiveRate;
        this.filter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), expectedInsertions, falsePositiveRate);

        long start = System.nanoTime();
        AtomicLong loaded = new AtomicLong();
        repository.forEachUsername(username -> {
            filter.put(username);
            loaded.incrementAndGet();
        });
        log.info("Bloom filter warmed with {} usernames in {} ms ({} KB, current FPP {})",
                loaded.get(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start),
                sizeInBytes() / 1024, String.format("%.6f", filter.expectedFpp()));
    }

    /** {@code false} = definitely not taken; {@code true} = maybe taken, ask the database. */
    public boolean mightContain(String username) {
        return filter.mightContain(username);
    }

    public void put(String username) {
        filter.put(username);
    }

    public Stats stats() {
        return new Stats(expectedInsertions, targetFalsePositiveRate, filter.approximateElementCount(),
                filter.expectedFpp(), sizeInBytes());
    }

    private long sizeInBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            filter.writeTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.size();
    }

    public record Stats(long expectedInsertions,
                        double targetFalsePositiveRate,
                        long approximateElementCount,
                        double currentFalsePositiveRate,
                        long sizeInBytes) {
    }
}
