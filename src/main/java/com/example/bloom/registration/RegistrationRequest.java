package com.example.bloom.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Kullanıcı adı zorunludur")
        @Size(min = 3, max = 32, message = "Kullanıcı adı 3-32 karakter olmalıdır")
        @Pattern(regexp = "^[A-Za-z0-9._]+$", message = "Sadece harf, rakam, nokta ve alt çizgi kullanılabilir")
        String username,

        @NotBlank(message = "E-posta zorunludur")
        @Email(message = "Geçerli bir e-posta adresi girin")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Şifre zorunludur")
        @Size(min = 8, max = 72, message = "Şifre 8-72 karakter olmalıdır")
        String password) {
}
