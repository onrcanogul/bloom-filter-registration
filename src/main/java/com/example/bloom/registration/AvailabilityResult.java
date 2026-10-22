package com.example.bloom.registration;

public record AvailabilityResult(String username,
                                 boolean available,
                                 BloomVerdict bloomVerdict,
                                 boolean databaseQueried,
                                 boolean falsePositive,
                                 long elapsedMicros) {
}
