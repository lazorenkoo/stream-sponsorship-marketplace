package com.streambid.auth;

import com.streambid.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull UserRole role) {

    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
