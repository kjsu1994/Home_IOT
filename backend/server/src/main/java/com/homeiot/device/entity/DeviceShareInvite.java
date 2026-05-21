package com.homeiot.device.entity;

import com.homeiot.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_share_invites")
public class DeviceShareInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id_fk", nullable = false)
    private Device device;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 30)
    private String role = DeviceShare.ROLE_CONTROLLER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceShare.Source source = DeviceShare.Source.FAMILY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_member_id")
    private Member invitedBy;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected DeviceShareInvite() {
    }

    public DeviceShareInvite(Device device, String email, String role, DeviceShare.Source source, Member invitedBy, OffsetDateTime expiresAt) {
        this.device = device;
        this.email = email;
        this.role = DeviceShare.normalizeRole(role);
        this.source = source == null ? DeviceShare.Source.FAMILY : source;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public boolean isAcceptable(OffsetDateTime now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void markAccepted() {
        this.acceptedAt = OffsetDateTime.now();
    }

    public void revoke() {
        if (acceptedAt == null && revokedAt == null) {
            this.revokedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Device getDevice() {
        return device;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return DeviceShare.normalizeRole(role);
    }

    public DeviceShare.Source getSource() {
        return source;
    }

    public Member getInvitedBy() {
        return invitedBy;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
