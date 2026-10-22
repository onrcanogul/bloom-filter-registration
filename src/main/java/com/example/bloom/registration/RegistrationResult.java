package com.example.bloom.registration;

public record RegistrationResult(String username, String email, AvailabilityResult availabilityCheck) {
}
