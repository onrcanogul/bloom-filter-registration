package com.example.bloom.registration;

import com.example.bloom.seed.MockUserGenerator;
import com.example.bloom.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.seed.user-count=2000",
        "app.seed.random-seed=42",
        "app.db.simulated-latency-micros=0"
})
class RegistrationServiceTest {

    @Autowired
    RegistrationService service;

    @Autowired
    UserRepository repository;

    private final String seededUsername = new MockUserGenerator(42).generateUsers(1).getFirst().username();

    @Test
    void takenUsernamePassesTheFilterAndIsConfirmedByTheDatabase() {
        AvailabilityResult result = service.checkAvailability(seededUsername, true);

        assertThat(result.available()).isFalse();
        assertThat(result.bloomVerdict()).isEqualTo(BloomVerdict.MIGHT_EXIST);
        assertThat(result.databaseQueried()).isTrue();
        assertThat(result.falsePositive()).isFalse();
    }

    @Test
    void unknownUsernameIsAnsweredByTheFilterWithoutTouchingTheDatabase() {
        long lookupsBefore = repository.usernameLookupCount();
        int skipped = 0;
        for (int i = 0; i < 200; i++) {
            AvailabilityResult result = service.checkAvailability("fresh_" + UUID.randomUUID().toString().substring(0, 8), true);
            assertThat(result.available()).isTrue();
            if (!result.databaseQueried()) {
                assertThat(result.bloomVerdict()).isEqualTo(BloomVerdict.DEFINITELY_ABSENT);
                skipped++;
            }
        }

        // ~1% FPP: virtually all of the 200 probes must skip the database
        assertThat(skipped).isGreaterThanOrEqualTo(190);
        assertThat(repository.usernameLookupCount() - lookupsBefore).isEqualTo(200 - skipped);
    }

    @Test
    void withoutTheFilterEveryCheckHitsTheDatabase() {
        long lookupsBefore = repository.usernameLookupCount();

        AvailabilityResult result = service.checkAvailability("nobody_" + UUID.randomUUID(), false);

        assertThat(result.available()).isTrue();
        assertThat(result.bloomVerdict()).isEqualTo(BloomVerdict.NOT_USED);
        assertThat(repository.usernameLookupCount() - lookupsBefore).isEqualTo(1);
    }

    @Test
    void registeredUsernameIsImmediatelyVisibleToTheFilterAndRejectedAgain() {
        String username = "New.User_" + System.nanoTime() % 100_000;

        RegistrationResult result = service.register(new RegistrationRequest(username, "New@Example.com", "s3cret-pass"), true);

        assertThat(result.username()).isEqualTo(username.toLowerCase());
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(service.checkAvailability(username, true).bloomVerdict()).isEqualTo(BloomVerdict.MIGHT_EXIST);
        assertThatThrownBy(() -> service.register(new RegistrationRequest(username, "x@example.com", "s3cret-pass"), true))
                .isInstanceOf(UsernameTakenException.class);
    }

    @Test
    void bothModesAlwaysAgreeOnAvailability() {
        MockUserGenerator probes = new MockUserGenerator(99);
        for (int i = 0; i < 500; i++) {
            String candidate = probes.nextUsername();
            assertThat(service.checkAvailability(candidate, true).available())
                    .as(candidate)
                    .isEqualTo(service.checkAvailability(candidate, false).available());
        }
    }
}
