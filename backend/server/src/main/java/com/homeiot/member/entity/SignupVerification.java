package com.homeiot.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "signup_verifications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_signup_verifications_email", columnNames = "email")
})
public class SignupVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "verification_code_hash", nullable = false, length = 255)
    private String verificationCodeHash;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void issueNewCode(String email, String verificationCodeHash, OffsetDateTime expiresAt) {
        this.email = email;
        this.verificationCodeHash = verificationCodeHash;
        this.expiresAt = expiresAt;
        this.verified = false;
        this.verifiedAt = null;
    }

    public void markVerified(OffsetDateTime verifiedAt) {
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }

    public String getEmail() {
        return email;
    }

    public String getVerificationCodeHash() {
        return verificationCodeHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
