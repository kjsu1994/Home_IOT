package com.homeiot.device.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.homeiot.member.entity.Member;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_claim_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_claim_tokens_hash", columnNames = "token_hash")
})
public class DeviceClaimToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id_fk", nullable = false)
    private Device device;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_member_id")
    private Member createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_member_id")
    private Member usedBy;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected DeviceClaimToken() {
    }

    public DeviceClaimToken(Device device, String tokenHash, OffsetDateTime expiresAt) {
        this(device, tokenHash, expiresAt, null);
    }

    public DeviceClaimToken(Device device, String tokenHash, OffsetDateTime expiresAt, Member createdBy) {
        this.device = device;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public boolean isUsable(OffsetDateTime now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed() {
        markUsed(null);
    }

    public void markUsed(Member member) {
        this.usedAt = OffsetDateTime.now();
        this.usedBy = member;
    }

    public void revoke() {
        if (this.revokedAt == null && this.usedAt == null) {
            this.revokedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Device getDevice() {
        return device;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public Member getCreatedBy() {
        return createdBy;
    }

    public Member getUsedBy() {
        return usedBy;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
