package com.homeiot.device.entity;

import com.homeiot.member.entity.DeviceOwnerMarker;
import com.homeiot.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "devices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_devices_device_id", columnNames = "device_id")
})
public class Device implements DeviceOwnerMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 120)
    private String name;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "room", length = 80)
    private String room;

    @Column(name = "device_api_key_hash", length = 128)
    private String deviceApiKeyHash;

    @Column(name = "device_api_key_issued_at")
    private OffsetDateTime deviceApiKeyIssuedAt;

    @ManyToOne
    @JoinColumn(name = "owner_member_id")
    private Member owner;

    @Column(name = "device_type", nullable = false, length = 80)
    private String deviceType;

    @Column(name = "fw_version", nullable = false, length = 80)
    private String fwVersion;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "rssi")
    private Integer rssi;

    @Column(name = "online_yn", nullable = false)
    private boolean online;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Device() {
    }

    public Device(String deviceId, String name, String deviceType, String fwVersion, String ip, Integer rssi) {
        this.deviceId = deviceId;
        this.name = name;
        this.deviceType = deviceType;
        this.fwVersion = fwVersion;
        this.ip = ip;
        this.rssi = rssi;
        this.online = true;
        this.lastSeenAt = OffsetDateTime.now();
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.lastSeenAt == null) {
            this.lastSeenAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void refreshFrom(String name, String deviceType, String fwVersion, String ip, Integer rssi) {
        this.name = name;
        this.deviceType = deviceType;
        this.fwVersion = fwVersion;
        this.ip = ip;
        this.rssi = rssi;
        this.online = true;
        this.lastSeenAt = OffsetDateTime.now();
    }

    public void updateMetadata(String displayName, String room) {
        this.displayName = normalizeBlank(displayName);
        this.room = normalizeBlank(room);
    }

    public void assignOwner(Member owner) {
        if (this.owner == null) {
            this.owner = owner;
        }
    }

    public void unassignOwnerIf(Member member) {
        if (this.owner != null && member != null && this.owner.getId().equals(member.getId())) {
            this.owner = null;
        }
    }

    public void updateDeviceApiKeyHash(String deviceApiKeyHash) {
        this.deviceApiKeyHash = deviceApiKeyHash;
        this.deviceApiKeyIssuedAt = OffsetDateTime.now();
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public void updateHealth(Boolean online, String ip, Integer rssi) {
        if (online != null) {
            this.online = online;
        }
        if (ip != null && !ip.isBlank()) {
            this.ip = ip;
        }
        if (rssi != null) {
            this.rssi = rssi;
        }
        this.lastSeenAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEffectiveName() {
        return displayName == null ? name : displayName;
    }

    public String getRoom() {
        return room;
    }

    public String getDeviceApiKeyHash() {
        return deviceApiKeyHash;
    }

    public OffsetDateTime getDeviceApiKeyIssuedAt() {
        return deviceApiKeyIssuedAt;
    }

    public Member getOwner() {
        return owner;
    }

    @Override
    public Long ownerId() {
        return owner == null ? null : owner.getId();
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getFwVersion() {
        return fwVersion;
    }

    public String getIp() {
        return ip;
    }

    public Integer getRssi() {
        return rssi;
    }

    public boolean isOnline() {
        return online;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
