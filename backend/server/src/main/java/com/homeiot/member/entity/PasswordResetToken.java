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
@Table(name = "password_reset_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_password_reset_tokens_email", columnNames = "email")
})
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "reset_code_hash", nullable = false, length = 255)
    private String resetCodeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

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

    public void issue(String email, String resetCodeHash, OffsetDateTime expiresAt) {
        this.email = email;
        this.resetCodeHash = resetCodeHash;
        this.expiresAt = expiresAt;
    }

    public String getEmail() {
        return email;
    }

    public String getResetCodeHash() {
        return resetCodeHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
