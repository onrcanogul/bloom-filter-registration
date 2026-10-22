package com.example.bloom.registration;

import com.example.bloom.user.NewUser;
import com.example.bloom.user.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RegistrationService {

    private final UserRepository repository;
    private final UsernameBloomFilter bloomFilter;
    private final PasswordHasher passwordHasher;

    private final AtomicLong availabilityChecks = new AtomicLong();
    private final AtomicLong databaseQueriesSkipped = new AtomicLong();
    private final AtomicLong falsePositives = new AtomicLong();

    public RegistrationService(UserRepository repository, UsernameBloomFilter bloomFilter, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.bloomFilter = bloomFilter;
        this.passwordHasher = passwordHasher;
    }

    public AvailabilityResult checkAvailability(String rawUsername, boolean useBloom) {
        String username = normalize(rawUsername);
        availabilityChecks.incrementAndGet();
        long start = System.nanoTime();

        if (!useBloom) {
            boolean taken = repository.existsByUsername(username);
            return new AvailabilityResult(username, !taken, BloomVerdict.NOT_USED, true, false, micros(start));
        }

        if (!bloomFilter.mightContain(username)) {
            databaseQueriesSkipped.incrementAndGet();
            return new AvailabilityResult(username, true, BloomVerdict.DEFINITELY_ABSENT, false, false, micros(start));
        }

        boolean taken = repository.existsByUsername(username);
        if (!taken) {
            falsePositives.incrementAndGet();
        }
        return new AvailabilityResult(username, !taken, BloomVerdict.MIGHT_EXIST, true, !taken, micros(start));
    }

    public RegistrationResult register(RegistrationRequest request, boolean useBloom) {
        AvailabilityResult availability = checkAvailability(request.username(), useBloom);
        if (!availability.available()) {
            throw new UsernameTakenException(availability);
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        try {
            // The unique constraint is the source of truth; the check above only avoids useless work.
            repository.insert(new NewUser(availability.username(), email, passwordHasher.hash(request.password())));
        } catch (DuplicateKeyException e) {
            throw new UsernameTakenException(new AvailabilityResult(availability.username(), false,
                    availability.bloomVerdict(), true, false, availability.elapsedMicros()));
        }
        bloomFilter.put(availability.username());
        return new RegistrationResult(availability.username(), email, availability);
    }

    public Stats stats() {
        return new Stats(repository.count(), availabilityChecks.get(), repository.usernameLookupCount(),
                databaseQueriesSkipped.get(), falsePositives.get(), bloomFilter.stats());
    }

    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static long micros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000;
    }

    public record Stats(long registeredUsers,
                        long availabilityChecks,
                        long databaseLookups,
                        long databaseLookupsSkipped,
                        long falsePositives,
                        UsernameBloomFilter.Stats bloomFilter) {
    }
}
