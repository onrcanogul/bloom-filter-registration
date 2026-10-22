package com.example.bloom.registration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return Map.of("message", "Form geçersiz", "fieldErrors", fields);
    }

    @ExceptionHandler(UsernameTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> usernameTaken(UsernameTakenException e) {
        return Map.of(
                "message", "Bu kullanıcı adı zaten alınmış",
                "fieldErrors", Map.of("username", "Bu kullanıcı adı zaten alınmış"),
                "availabilityCheck", e.availability());
    }
}
