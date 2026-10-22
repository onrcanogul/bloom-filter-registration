package com.example.bloom.seed;

import com.example.bloom.user.NewUser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Deterministic generator of realistic-looking usernames ("ayse.yilmaz", "mehmetkaya42",
 * "e_demir1994"...). The same seed always yields the same users.
 */
public class MockUserGenerator {

    private static final String[] FIRST_NAMES = {
            "ahmet", "mehmet", "mustafa", "ali", "huseyin", "hasan", "ibrahim", "ismail", "yusuf", "omer",
            "murat", "emre", "burak", "can", "onur", "kerem", "baris", "serkan", "volkan", "tolga",
            "ayse", "fatma", "zeynep", "elif", "merve", "esra", "ozge", "selin", "deniz", "ece",
            "irem", "busra", "gizem", "ceren", "derya", "melis", "pinar", "sibel", "tugba", "yasemin",
            "john", "james", "michael", "david", "daniel", "alex", "chris", "sam", "max", "leo",
            "emma", "olivia", "sophia", "mia", "anna", "laura", "sarah", "julia", "nina", "lara"
    };

    private static final String[] LAST_NAMES = {
            "yilmaz", "kaya", "demir", "sahin", "celik", "yildiz", "yildirim", "ozturk", "aydin", "ozdemir",
            "arslan", "dogan", "kilic", "aslan", "cetin", "kara", "koc", "kurt", "ozkan", "simsek",
            "polat", "erdogan", "korkmaz", "ozgur", "aksoy", "tekin", "unal", "gunes", "bulut", "acar",
            "smith", "johnson", "brown", "miller", "wilson", "moore", "taylor", "clark", "lewis", "walker",
            "hall", "young", "king", "wright", "scott", "green", "baker", "adams", "nelson", "carter"
    };

    private static final String[] EMAIL_DOMAINS = {"example.com", "mail.test", "posta.test", "inbox.test"};

    public static final String SEED_PASSWORD_HASH = "mock-user:no-login";

    private final Random random;

    public MockUserGenerator(long seed) {
        this.random = new Random(seed);
    }

    public List<NewUser> generateUsers(int count) {
        List<NewUser> users = new ArrayList<>(count);
        for (String username : generateUsernames(count)) {
            String domain = EMAIL_DOMAINS[random.nextInt(EMAIL_DOMAINS.length)];
            users.add(new NewUser(username, username + "@" + domain, SEED_PASSWORD_HASH));
        }
        return users;
    }

    public Set<String> generateUsernames(int count) {
        Set<String> usernames = new LinkedHashSet<>(count * 2);
        while (usernames.size() < count) {
            usernames.add(nextUsername());
        }
        return usernames;
    }

    public String nextUsername() {
        String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        return switch (random.nextInt(7)) {
            case 0 -> first + "." + last;
            case 1 -> first + last + random.nextInt(100);
            case 2 -> first + "_" + last + (1965 + random.nextInt(46));
            case 3 -> first.charAt(0) + last + random.nextInt(1_000);
            case 4 -> first + random.nextInt(10_000);
            case 5 -> last + "." + first + random.nextInt(10);
            default -> first.charAt(0) + "_" + last + (1965 + random.nextInt(46));
        };
    }
}
