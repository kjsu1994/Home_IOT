package com.homeiot.device.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_states")
public class DeviceState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id_fk", nullable = false, unique = true)
    private Device device;

    @Column(name = "led_on")
    private Boolean ledOn;

    @Column(name = "wifi_connected")
    private Boolean wifiConnected;

    @Column(name = "ota_ready")
    private Boolean otaReady;

    @Column(name = "last_reported_at", nullable = false)
    private OffsetDateTime lastReportedAt;

    @Lob
    @Column(name = "raw_state_json")
    private String rawStateJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DeviceState() {
    }

    public DeviceState(Device device) {
        this.device = device;
        this.lastReportedAt = OffsetDateTime.now();
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.lastReportedAt == null) {
            this.lastReportedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void update(Boolean ledOn, Boolean wifiConnected, Boolean otaReady, String rawStateJson) {
        this.ledOn = ledOn;
        this.wifiConnected = wifiConnected;
        this.otaReady = otaReady;
        this.rawStateJson = rawStateJson;
        this.lastReportedAt = OffsetDateTime.now();
    }

    public Device getDevice() {
        return device;
    }

    public Boolean getLedOn() {
        return ledOn;
    }

    public Boolean getWifiConnected() {
        return wifiConnected;
    }

    public Boolean getOtaReady() {
        return otaReady;
    }

    public OffsetDateTime getLastReportedAt() {
        return lastReportedAt;
    }

    public String getRawStateJson() {
        return rawStateJson;
    }
}
