package com.example.bloom.registration;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @GetMapping("/users/availability")
    public AvailabilityResult availability(@RequestParam String username,
                                           @RequestParam(defaultValue = "true") boolean useBloom) {
        return service.checkAvailability(username, useBloom);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResult register(@Valid @RequestBody RegistrationRequest request,
                                       @RequestParam(defaultValue = "true") boolean useBloom) {
        return service.register(request, useBloom);
    }

    @GetMapping("/stats")
    public RegistrationService.Stats stats() {
        return service.stats();
    }
}
