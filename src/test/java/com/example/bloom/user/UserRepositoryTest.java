package com.example.bloom.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest(properties = "app.db.simulated-latency-micros=0")
@Import({UserRepository.class, DatabaseLatencySimulator.class})
class UserRepositoryTest {

    @Autowired
    UserRepository repository;

    @Test
    void insertsAndFindsUsersByUsername() {
        repository.insert(new NewUser("alice", "alice@example.com", "hash"));

        assertThat(repository.existsByUsername("alice")).isTrue();
        assertThat(repository.existsByUsername("bob")).isFalse();
    }

    @Test
    void countsEveryUsernameLookup() {
        long before = repository.usernameLookupCount();

        repository.existsByUsername("a");
        repository.existsByUsername("b");

        assertThat(repository.usernameLookupCount() - before).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateUsernames() {
        repository.insert(new NewUser("carol", "c1@example.com", "hash"));

        assertThatThrownBy(() -> repository.insert(new NewUser("carol", "c2@example.com", "hash")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void batchInsertsAndStreamsUsernames() {
        repository.insertAll(List.of(
                new NewUser("u1", "u1@example.com", "h"),
                new NewUser("u2", "u2@example.com", "h"),
                new NewUser("u3", "u3@example.com", "h")));

        List<String> seen = new ArrayList<>();
        repository.forEachUsername(seen::add);

        assertThat(repository.count()).isEqualTo(3);
        assertThat(seen).containsExactlyInAnyOrder("u1", "u2", "u3");
        assertThat(repository.randomUsernames(2)).hasSize(2).isSubsetOf(seen);
        assertThat(repository.findExistingUsernames(List.of("u1", "nope", "u3"))).containsExactlyInAnyOrder("u1", "u3");
    }
}
