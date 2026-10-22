package com.example.bloom.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.LockSupport;

/**
 * Adds an artificial network round trip to lookups so an in-memory database behaves
 * like a remote one. Adjustable at runtime from the benchmark panel.
 */
@Component
public class DatabaseLatencySimulator {

    private volatile long latencyMicros;

    public DatabaseLatencySimulator(@Value("${app.db.simulated-latency-micros:0}") long latencyMicros) {
        setLatencyMicros(latencyMicros);
    }

    public void pause() {
        if (latencyMicros > 0) {
            LockSupport.parkNanos(latencyMicros * 1_000);
        }
    }

    public long getLatencyMicros() {
        return latencyMicros;
    }

    public void setLatencyMicros(long latencyMicros) {
        if (latencyMicros < 0) {
            throw new IllegalArgumentException("latency must not be negative");
        }
        this.latencyMicros = latencyMicros;
    }
}
