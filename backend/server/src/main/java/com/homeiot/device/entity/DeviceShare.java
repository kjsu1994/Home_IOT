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
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_shares", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_shares_device_member", columnNames = {"device_id_fk", "member_id"})
})
public class DeviceShare {
    public enum Source {
        QR,
        ADMIN,
        FAMILY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id_fk", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 30)
    private String role = "USER";

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Source source = Source.QR;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected DeviceShare() {
    }

    public DeviceShare(Device device, Member member, String role) {
        this(device, member, role, Source.QR);
    }

    public DeviceShare(Device device, Member member, String role, Source source) {
        this.device = device;
        this.member = member;
        this.role = role == null || role.isBlank() ? "USER" : role;
        this.source = source == null ? Source.QR : source;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Device getDevice() {
        return device;
    }

    public Member getMember() {
        return member;
    }

    public String getRole() {
        return role;
    }

    public Source getSource() {
        return source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
