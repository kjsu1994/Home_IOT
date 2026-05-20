package com.homeiot.member.dto;

import com.homeiot.member.entity.Member;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.List;

public final class MemberDtos {
    private MemberDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9!@#\\$%\\^&*()_+\\-=]{8,30}$") String password
    ) {
    }

    public record EmailCheckResponse(String email, boolean available, boolean duplicated) {
    }

    public record EmailVerificationSendRequest(@NotBlank @Email String email) {
    }

    public record EmailVerificationConfirmRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String code
    ) {
    }

    public record EmailVerificationSendResponse(String email, boolean verificationSent, OffsetDateTime expiresAt) {
    }

    public record EmailVerificationConfirmResponse(String email, boolean verified, OffsetDateTime verifiedAt) {
    }

    public record AccountUpdateRequest(@NotBlank String name) {
    }

    public record PasswordChangeRequest(
            String currentPassword,
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9!@#\\$%\\^&*()_+\\-=]{8,30}$") String newPassword
    ) {
    }

    public record MemberResponse(
            Long memberId,
            String email,
            String name,
            Member.AuthProvider authProvider,
            List<String> roles
    ) {
    }
}
