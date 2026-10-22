package com.example.bloom.seed;

import com.example.bloom.user.NewUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MockUserGeneratorTest {

    @Test
    void generatesRequestedNumberOfUniqueUsers() {
        List<NewUser> users = new MockUserGenerator(1).generateUsers(20_000);

        Set<String> usernames = users.stream().map(NewUser::username).collect(Collectors.toSet());
        assertThat(users).hasSize(20_000);
        assertThat(usernames).hasSize(20_000);
    }

    @Test
    void isDeterministicForTheSameSeed() {
        assertThat(new MockUserGenerator(7).generateUsers(500))
                .isEqualTo(new MockUserGenerator(7).generateUsers(500));
    }

    @Test
    void producesValidUsernamesAndEmails() {
        assertThat(new MockUserGenerator(3).generateUsers(2_000)).allSatisfy(user -> {
            assertThat(user.username()).matches("[a-z0-9._]{3,32}");
            assertThat(user.email()).startsWith(user.username() + "@");
        });
    }
}
