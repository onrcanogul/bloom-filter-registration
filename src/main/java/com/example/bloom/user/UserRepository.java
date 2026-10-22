package com.example.bloom.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;
    private final DatabaseLatencySimulator latency;
    private final AtomicLong usernameLookups = new AtomicLong();

    public UserRepository(JdbcTemplate jdbc, DatabaseLatencySimulator latency) {
        this.jdbc = jdbc;
        this.latency = latency;
    }

    /** The expensive call the Bloom filter tries to avoid. Counted and delayed. */
    public boolean existsByUsername(String username) {
        usernameLookups.incrementAndGet();
        latency.pause();
        return !jdbc.queryForList("SELECT 1 FROM users WHERE username = ? LIMIT 1", Integer.class, username).isEmpty();
    }

    /**
     * @throws org.springframework.dao.DuplicateKeyException if the username is already taken
     */
    public void insert(NewUser user) {
        jdbc.update("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)",
                user.username(), user.email(), user.passwordHash());
    }

    public void insertAll(List<NewUser> users) {
        jdbc.batchUpdate("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)", users, 5_000,
                (ps, user) -> {
                    ps.setString(1, user.username());
                    ps.setString(2, user.email());
                    ps.setString(3, user.passwordHash());
                });
    }

    public void forEachUsername(Consumer<String> consumer) {
        jdbc.query("SELECT username FROM users", rs -> {
            consumer.accept(rs.getString(1));
        });
    }

    public List<String> randomUsernames(int limit) {
        return jdbc.queryForList("SELECT username FROM users ORDER BY RAND() LIMIT ?", String.class, limit);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return count == null ? 0 : count;
    }

    public long usernameLookupCount() {
        return usernameLookups.get();
    }
}
