package com.example.bloom.registration;

public class UsernameTakenException extends RuntimeException {

    private final AvailabilityResult availability;

    public UsernameTakenException(AvailabilityResult availability) {
        super("Username already taken: " + availability.username());
        this.availability = availability;
    }

    public AvailabilityResult availability() {
        return availability;
    }
}
