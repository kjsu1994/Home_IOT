package com.homeiot.member.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_members_email", columnNames = "email")
})
public class Member {
    public enum Status {
        ACTIVE,
        WITHDRAWN
    }

    public enum AuthProvider {
        LOCAL,
        GOOGLE,
        NAVER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role", nullable = false, length = 40)
    private List<String> roles = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Member() {
    }

    public Member(String email, String name, String passwordHash, AuthProvider authProvider, List<String> roles) {
        this.email = normalizeEmail(email);
        this.name = name;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider;
        this.roles = new ArrayList<>(roles);
        this.status = Status.ACTIVE;
    }

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

    public void reactivateLocal(String name, String passwordHash, List<String> roles) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.authProvider = AuthProvider.LOCAL;
        this.roles = new ArrayList<>(roles);
        this.status = Status.ACTIVE;
    }

    public void linkOauth(String name, AuthProvider provider, List<String> roles) {
        this.name = name == null || name.isBlank() ? this.name : name.trim();
        this.authProvider = provider;
        this.status = Status.ACTIVE;
        if (this.roles == null || this.roles.isEmpty()) {
            this.roles = new ArrayList<>(roles);
        } else {
            roles.forEach(role -> {
                if (!this.roles.contains(role)) {
                    this.roles.add(role);
                }
            });
        }
    }

    public void updateProfile(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.authProvider = AuthProvider.LOCAL;
        this.status = Status.ACTIVE;
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public Status getStatus() {
        return status;
    }

    public List<String> getRoles() {
        return roles;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}

