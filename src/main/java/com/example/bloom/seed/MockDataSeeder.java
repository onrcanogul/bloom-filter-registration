package com.example.bloom.seed;

import com.example.bloom.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Fills the database with mock users before the application starts serving requests.
 * Runs as a bean initializer (not an ApplicationRunner) so anything that loads the
 * usernames afterwards — like the Bloom filter warm-up — sees the full data set.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MockDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(MockDataSeeder.class);

    private final long seededUsers;

    public MockDataSeeder(UserRepository repository,
                          @Value("${app.seed.user-count:200000}") int userCount,
                          @Value("${app.seed.random-seed:42}") long randomSeed) {
        if (userCount > 0 && repository.count() == 0) {
            long start = System.nanoTime();
            repository.insertAll(new MockUserGenerator(randomSeed).generateUsers(userCount));
            log.info("Seeded {} mock users in {} ms", userCount,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
        this.seededUsers = repository.count();
    }

    public long seededUsers() {
        return seededUsers;
    }
}
