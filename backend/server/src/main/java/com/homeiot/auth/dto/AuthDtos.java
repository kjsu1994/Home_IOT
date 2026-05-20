package com.homeiot.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType) {
    }

    public record SessionResponse(Long memberId, String email, String name, List<String> roles) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String code,
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9!@#\\$%\\^&*()_+\\-=]{8,30}$") String newPassword
    ) {
    }

    public record FindEmailResponse(String email) {
    }
}
